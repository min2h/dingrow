package app.mongle.platform

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import app.mongle.db.MongleDatabase
import app.mongle.service.GameService
import app.mongle.service.SystemTime
import app.mongle.service.createGameService
import app.mongle.service.toSnapshotJson
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import platform.Foundation.NSDateComponents
import platform.Foundation.NSFileManager
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationAction
import platform.UserNotifications.UNNotificationCategory
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter
import platform.WidgetKit.WidgetCenter

object IosAppConfig {
    var databaseDirectory: String = ""
}

object MongleSession {
    lateinit var service: GameService
}

private var iosLastNoticeIds: List<String> = emptyList()
private var iosAskedPermission = false
private var iosCategoryReady = false

actual class PlatformNotifier actual constructor() {
    actual fun sync(notices: List<ScheduledNotice>) {
        val center = UNUserNotificationCenter.currentNotificationCenter()
        ensurePermission(center)
        ensureCategory(center)
        if (iosLastNoticeIds.isNotEmpty()) {
            center.removePendingNotificationRequestsWithIdentifiers(iosLastNoticeIds)
        }
        iosLastNoticeIds = notices.map { it.id }
        notices.forEach { notice -> center.addNotificationRequest(requestFor(notice)) { _ -> } }
    }

    actual fun notifyNow(title: String, body: String) {
        val center = UNUserNotificationCenter.currentNotificationCenter()
        ensurePermission(center)
        val content = UNMutableNotificationContent()
        content.title = title
        content.body = body
        val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(1.0, false)
        val request = UNNotificationRequest.requestWithIdentifier("gift-" + title.hashCode(), content, trigger)
        center.addNotificationRequest(request) { _ -> }
    }

    actual fun reloadWidgets() {
        WidgetCenter.sharedCenter().reloadAllTimelines()
    }
}

private fun ensurePermission(center: UNUserNotificationCenter) {
    if (iosAskedPermission) return
    iosAskedPermission = true
    val options = UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge
    center.requestAuthorizationWithOptions(options) { _, _ -> }
}

private fun ensureCategory(center: UNUserNotificationCenter) {
    if (iosCategoryReady) return
    iosCategoryReady = true
    val ack = UNNotificationAction.actionWithIdentifier("ACK", "확인", 0u)
    val snooze = UNNotificationAction.actionWithIdentifier("SNOOZE", "5분 뒤", 0u)
    val complete = UNNotificationAction.actionWithIdentifier("COMPLETE", "완료", 0u)
    val category = UNNotificationCategory.categoryWithIdentifier(
        "DINGROW_SCHEDULE",
        listOf(ack, snooze, complete),
        emptyList<String>(),
        0u,
    )
    center.setNotificationCategories(setOf(category))
}

private fun requestFor(notice: ScheduledNotice): UNNotificationRequest {
    val content = UNMutableNotificationContent()
    content.title = notice.title
    content.body = notice.body
    content.categoryIdentifier = "DINGROW_SCHEDULE"
    content.userInfo = mapOf("scheduleId" to notice.scheduleId)
    val ldt = Instant.fromEpochMilliseconds(notice.triggerAtMillis).toLocalDateTime(TimeZone.currentSystemDefault())
    val comps = NSDateComponents()
    comps.year = ldt.year.toLong()
    comps.month = ldt.monthNumber.toLong()
    comps.day = ldt.dayOfMonth.toLong()
    comps.hour = ldt.hour.toLong()
    comps.minute = ldt.minute.toLong()
    comps.second = ldt.second.toLong()
    val trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(comps, false)
    return UNNotificationRequest.requestWithIdentifier(notice.id, content, trigger)
}

fun createIosDriver(directory: String): SqlDriver {
    val base = directory.removePrefix("file://").substringBefore("?").removeSuffix("/")
    copyLegacyDatabase(base)
    return NativeSqliteDriver(
        schema = MongleDatabase.Schema,
        name = "dingrow.db",
        onConfiguration = { config ->
            config.copy(extendedConfig = config.extendedConfig.copy(basePath = base))
        },
    )
}

private fun copyLegacyDatabase(base: String) {
    val files = NSFileManager.defaultManager
    val dest = "$base/dingrow.db"
    if (files.fileExistsAtPath(dest)) return
    val source = "$base/mongle.db"
    if (!files.fileExistsAtPath(source)) return
    listOf("", "-wal", "-shm", "-journal").forEach { suffix ->
        val from = source + suffix
        val to = dest + suffix
        if (files.fileExistsAtPath(from) && !files.fileExistsAtPath(to)) {
            files.copyItemAtPath(from, to, null)
        }
    }
}

/**
 * Swift 위젯과 알림 액션이 호출하는 동기 API.
 * 앱과 위젯 익스텐션이 같은 App Group 디렉터리를 [IosAppConfig.databaseDirectory]에 넣는다.
 */
object MongleBridge {
    fun start() {
        if (MongleSession::service.isInitialized) return
        val directory = IosAppConfig.databaseDirectory.ifBlank { platform.Foundation.NSTemporaryDirectory() }
        MongleSession.service = createGameService(createIosDriver(directory), SystemTime)
    }

    fun snapshotJson(): String {
        start()
        return MongleSession.service.state.value.toSnapshotJson()
    }

    fun feed(): String = call { it.feed() }
    fun completeNext(): String = call { it.completeNext() }
    fun sellLatestGift(): String = call { it.sellPendingOrLatest() }
    fun beginBuy(): String = call { it.beginBuyToday() }
    fun confirmBuy(): String = call { it.confirmBuy() }
    fun cancelBuy(): String = call { it.cancelBuy() }
    fun cyclePreset(): String = call { it.cyclePreset() }
    fun acknowledge(id: String): String = call { it.acknowledge(id) }
    fun completeSchedule(id: String): String = call { it.completeSchedule(id) }
    fun snooze(id: String): String = call { it.snooze(id) }

    private fun call(block: suspend (GameService) -> app.mongle.model.ActionResult): String {
        start()
        return runBlocking { block(MongleSession.service).message }
    }
}
