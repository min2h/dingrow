package app.mongle.platform

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.mongle.db.MongleDatabase

object JvmNotices {
    var synced: List<ScheduledNotice> = emptyList()
    var syncCount: Int = 0
    val immediate = mutableListOf<Pair<String, String>>()
    var reloads: Int = 0

    fun reset() {
        synced = emptyList()
        syncCount = 0
        immediate.clear()
        reloads = 0
    }
}

actual class PlatformNotifier actual constructor() {
    actual fun sync(notices: List<ScheduledNotice>) {
        JvmNotices.synced = notices
        JvmNotices.syncCount += 1
    }

    actual fun notifyNow(title: String, body: String) {
        JvmNotices.immediate.add(title to body)
    }

    actual fun reloadWidgets() {
        JvmNotices.reloads += 1
    }
}

fun createMemoryDriver(): SqlDriver {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    MongleDatabase.Schema.create(driver)
    return driver
}
