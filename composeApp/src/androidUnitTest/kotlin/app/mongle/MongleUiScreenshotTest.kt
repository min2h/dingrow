package app.mongle

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.printToString
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import app.mongle.alarm.AlarmScheduler
import app.mongle.alarm.ReminderReceiver
import app.mongle.data.MemoryGameStore
import app.mongle.model.RepeatRule
import app.mongle.model.Stage
import app.mongle.platform.AndroidHooks
import app.mongle.service.GameService
import app.mongle.service.TimeProvider
import app.mongle.ui.LocalPetMotion
import app.mongle.ui.MongleAppScreen
import app.mongle.ui.MongleTheme
import app.mongle.ui.PermissionSnapshot
import app.mongle.wallpaper.WallpaperPainter
import app.mongle.widget.BeginBuyAction
import app.mongle.widget.CancelBuyAction
import app.mongle.widget.CompleteAction
import app.mongle.widget.ConfirmBuyAction
import app.mongle.widget.FeedAction
import app.mongle.widget.LargeBody
import app.mongle.widget.PresetAction
import app.mongle.widget.SellAction
import app.mongle.widget.SmallBody
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import androidx.compose.ui.unit.DpSize
import android.os.Looper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h740dp-hdpi")
class MongleFlowScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun onboardingNamingAndHome() {
        val harness = Harness()
        harness.show()
        composeRule.onNode(hasSetTextAction()).performTextReplacement("보리")
        composeRule.onNodeWithText("같이 시작하기").performClick()
        harness.settle()
        assertEquals("보리", harness.service.state.value.petName)
        assertTrue(harness.service.state.value.onboarded)
    }

    @Test
    fun scheduleAddListAndComplete() {
        val harness = Harness()
        harness.show()
        composeRule.onNode(hasSetTextAction()).performTextReplacement("보리")
        composeRule.onNodeWithText("같이 시작하기").performClick()
        harness.settle()
        composeRule.onNodeWithTag("tab-Schedule").performClick()
        harness.settle()
        harness.clickText("추가")
        harness.settle()
        composeRule.onNode(hasSetTextAction()).performTextReplacement("물 마시기")
        composeRule.onNodeWithText("알림 맡기기").performClick()
        harness.settle()
        val added = harness.service.state.value.schedules.single()
        assertEquals("물 마시기", added.title)
        assertTrue(added.timeMillis > System.currentTimeMillis() - 5_000)
        harness.capture("schedule.png")
        harness.clickTag("complete-${added.id}")
        harness.settle()
        assertTrue(harness.service.state.value.schedules.single().done)
    }

    @Test
    fun feedGrowthOutingAndGift() {
        val harness = Harness()
        harness.show()
        runBlocking { harness.service.completeOnboarding("보리") }
        harness.settle()
        val beforeHunger = harness.service.state.value.hunger
        harness.clickTag("feed")
        harness.settle()
        val fed = harness.service.state.value
        assertTrue(
            "hunger ${beforeHunger} -> ${fed.hunger} (${fed.statusMessage})",
            fed.hunger > beforeHunger,
        )
        runBlocking { harness.growTo(Stage.BABY) }
        harness.settle()
        assertEquals(Stage.BABY, harness.service.state.value.stage)
        composeRule.onNodeWithTag("tab-Home").performClick()
        harness.settle()
        runBlocking { harness.growTo(Stage.CHILD) }
        harness.settle()
        assertEquals(Stage.CHILD, harness.service.state.value.stage)
        composeRule.onNodeWithTag("tab-Home").performClick()
        harness.settle()
        harness.clock.now = seoulMillis(2026, 10, 21, 15, 0)
        runBlocking { harness.service.refresh() }
        harness.settle()
        harness.capture("home.png")
        harness.clickTag("outing")
        harness.settle()
        assertNotNull(harness.service.state.value.outing)
        harness.clock.now += 21 * 60_000
        runBlocking { harness.service.refresh() }
        harness.settle()
        assertNotNull(harness.service.state.value.pendingGift)
        val coins = harness.service.state.value.coins
        harness.clickTag("sell-gift")
        harness.settle()
        assertTrue(harness.service.state.value.coins > coins)
        assertEquals(null, harness.service.state.value.pendingGift)
        harness.clock.now = seoulMillis(2026, 10, 21, 23, 0)
        runBlocking { harness.service.refresh() }
        harness.settle()
        composeRule.onNodeWithTag("tab-Home").performClick()
        harness.settle()
        harness.capture("home-night.png")
    }

    @Test
    fun shopBuyAndRoomDecor() {
        val harness = Harness()
        harness.show()
        runBlocking {
            harness.service.completeOnboarding("보리")
            harness.growTo(Stage.CHILD)
            val hatPrice = app.mongle.model.Catalog.byId("sprout_hat")!!.price
            var guard = 0
            while (harness.service.state.value.coins < harness.service.state.value.todayOffer.price + hatPrice && guard < 80) {
                if (guard > 0 && guard % 6 == 0) harness.clock.now += 26L * 60 * 60 * 1000
                val stamp = harness.clock.now
                harness.service.addSchedule("용돈 $guard", stamp, RepeatRule.NONE)
                harness.clock.now = stamp + 3 * 60_000
                val id = harness.service.state.value.schedules.last { !it.done }.id
                harness.service.completeSchedule(id)
                guard += 1
            }
        }
        harness.settle()
        composeRule.onNodeWithTag("tab-Room").performClick()
        harness.settle()
        harness.capture("shop.png")
        val before = harness.service.state.value.coins
        harness.clickTag("buy-today")
        harness.settle()
        assertTrue(
            "coins $before -> ${harness.service.state.value.coins} (${harness.service.state.value.statusMessage})",
            harness.service.state.value.coins < before,
        )
        harness.clickTag("buy-sprout_hat")
        harness.settle()
        assertTrue(
            "owned=${harness.service.state.value.owned} coins=${harness.service.state.value.coins} ${harness.service.state.value.statusMessage}",
            "sprout_hat" in harness.service.state.value.owned,
        )
        harness.clickText("밤하늘 방")
        harness.settle()
        assertEquals(2, harness.service.state.value.activePreset)
        harness.capture("room.png")
        harness.clickText("새싹 모자")
        harness.settle()
        assertEquals("sprout_hat", harness.service.state.value.outfitId)
        var opened = false
        harness.onWallpaper = { opened = true }
        harness.clickTag("wallpaper")
        harness.settle()
        assertTrue(opened)
        composeRule.onNodeWithTag("tab-My").performClick()
        harness.settle()
        harness.capture("dex.png")
    }

    private inner class Harness {
        val clock = MutableClock(System.currentTimeMillis())
        val service = GameService(MemoryGameStore(), clock, TimeZone.of("Asia/Seoul"))
        var onWallpaper: () -> Unit = {}
        var motion by mutableStateOf(0.15f to 0.05f)

        init {
            primeComposeResources()
            AndroidHooks.sync = {}
            AndroidHooks.notifyNow = { _, _ -> }
            AndroidHooks.reloadWidgets = {}
            composeRule.mainClock.autoAdvance = false
        }

        fun show() {
            val permissions = PermissionSnapshot(
                notificationsOn = true,
                exactAlarmsOn = true,
                supportsExactAlarm = true,
                supportsWallpaper = true,
            )
            composeRule.setContent {
                CompositionLocalProvider(LocalPetMotion provides motion) {
                    MongleTheme {
                        MongleAppScreen(
                            service = service,
                            permissions = permissions,
                            onRequestNotifications = {},
                            onOpenExactAlarm = {},
                            onOpenWallpaper = { onWallpaper() },
                        )
                    }
                }
            }
            settle()
        }

        fun settle() {
            composeRule.mainClock.advanceTimeBy(400)
            composeRule.waitForIdle()
        }

        fun capture(name: String) {
            settle()
            composeRule.onRoot().captureRoboImage(artifact(name))
        }

        fun clickText(text: String) {
            try {
                composeRule.onNodeWithText(text).performClick()
            } catch (error: AssertionError) {
                File("/tmp/mongle-semantics.txt").writeText(composeRule.onRoot().printToString())
                throw AssertionError("click '$text' failed: ${error.message}", error)
            }
        }

        fun clickTag(tag: String) {
            try {
                composeRule.onNodeWithTag(tag).performClick()
            } catch (error: AssertionError) {
                File("/tmp/mongle-semantics.txt").writeText(composeRule.onRoot().printToString())
                throw AssertionError("click '$tag' failed: ${error.message}", error)
            }
        }

        suspend fun growTo(stage: Stage) {
            var guard = 0
            while (service.state.value.stage != stage && guard < 12) {
                val stamp = clock.now
                service.addSchedule("성장 $guard", stamp, RepeatRule.NONE)
                clock.now = stamp + 3 * 60_000
                val id = service.state.value.schedules.last { !it.done }.id
                service.completeSchedule(id)
                guard += 1
            }
            check(service.state.value.stage == stage) { "stage=${service.state.value.stage} xp=${service.state.value.xp}" }
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-hdpi")
class MongleWidgetAlarmWallpaperTest {
    private val glanceId = object : GlanceId {}

    @Test
    fun widgetsRenderAndButtonsIncludingTwoTapBuy() = runBlocking {
        val clock = MutableClock(System.currentTimeMillis())
        val service = GameService(MemoryGameStore(), clock, TimeZone.of("Asia/Seoul"))
        service.completeOnboarding("보리")
        service.addSchedule("물 마시기", clock.now + 60_000, RepeatRule.NONE)
        AppGraph.installForTests(service)
        val context = RuntimeEnvironment.getApplication()
        saveGlance(context, "widget-small.png", 180, 180) { SmallBody(service.state.value) }
        saveGlance(context, "widget-large.png", 320, 360) { LargeBody(service.state.value) }

        FeedAction().onAction(context, glanceId, actionParametersOf())
        assertTrue(service.state.value.hunger > 80 || service.state.value.statusMessage.contains("먹"))

        grow(service, clock, Stage.CHILD)
        CompleteAction().onAction(context, glanceId, actionParametersOf())
        assertTrue(service.state.value.schedules.first().done || service.state.value.schedules.isEmpty())

        val preset = service.state.value.activePreset
        PresetAction().onAction(context, glanceId, actionParametersOf())
        assertNotEquals(preset, service.state.value.activePreset)

        service.startOuting()
        clock.now += 21 * 60_000
        service.refresh()
        assertNotNull(service.state.value.pendingGift)
        val beforeSell = service.state.value.coins
        SellAction().onAction(context, glanceId, actionParametersOf())
        assertTrue(service.state.value.coins > beforeSell)

        val price = service.state.value.todayOffer.price
        if (service.state.value.coins < price) grow(service, clock, Stage.ADULT)
        val coins = service.state.value.coins
        BeginBuyAction().onAction(context, glanceId, actionParametersOf())
        assertTrue(service.state.value.confirmText.isNotBlank())
        saveGlance(context, "widget-confirm-buy.png", 320, 360, publish = false) { LargeBody(service.state.value) }
        ConfirmBuyAction().onAction(context, glanceId, actionParametersOf())
        assertTrue(service.state.value.coins < coins)
        assertTrue(service.state.value.confirmText.isBlank())
        saveGlance(context, "widget-after-buy.png", 320, 360, publish = false) { LargeBody(service.state.value) }
        CancelBuyAction().onAction(context, glanceId, actionParametersOf())
    }

    @Test
    fun notificationFiresOneMinuteOutWithCompleteAndSnooze() {
        AppGraph.resetForTests()
        val context = RuntimeEnvironment.getApplication()
        AppGraph.ensure(context)
        await { AppGraph.ready }
        val now = System.currentTimeMillis()
        runBlocking {
            AppGraph.service.completeOnboarding("보리")
            AppGraph.service.addSchedule("Stretch", now + 60_000, RepeatRule.NONE)
        }
        val schedule = AppGraph.service.state.value.schedules.single()
        val alarms = shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms
        assertTrue(alarms.isNotEmpty())
        val trigger = alarms.maxOf { it.triggerAtTime }
        assertTrue("trigger=$trigger now=$now", trigger in (now + 50_000)..(now + 70_000))

        ReminderReceiver().onReceive(
            context,
            android.content.Intent(AlarmScheduler.ACTION_SHOW).apply {
                putExtra(AlarmScheduler.EXTRA_SCHEDULE_ID, schedule.id)
                putExtra(AlarmScheduler.EXTRA_TITLE, "보리")
                putExtra(AlarmScheduler.EXTRA_BODY, "물 마실 시간")
            },
        )
        val manager = context.getSystemService(NotificationManager::class.java)
        await { shadowOf(manager).allNotifications.isNotEmpty() }
        val notification = shadowOf(manager).allNotifications.single()
        assertEquals("보리", notification.extras.getString(android.app.Notification.EXTRA_TITLE))
        assertEquals(2, notification.actions.size)
        assertEquals("완료", notification.actions[0].title.toString())
        assertEquals("10분 뒤", notification.actions[1].title.toString())
        saveNotification(context, notification, "notification.png")

        ReminderReceiver().onReceive(
            context,
            android.content.Intent(AlarmScheduler.ACTION_SNOOZE).apply {
                putExtra(AlarmScheduler.EXTRA_SCHEDULE_ID, schedule.id)
            },
        )
        await { AppGraph.service.state.value.schedules.single().timeMillis >= now + 9 * 60_000 }
        val snoozed = AppGraph.service.state.value.schedules.single().timeMillis
        assertTrue(snoozed >= now + 9 * 60_000)

        ReminderReceiver().onReceive(
            context,
            android.content.Intent(AlarmScheduler.ACTION_DONE).apply {
                putExtra(AlarmScheduler.EXTRA_SCHEDULE_ID, schedule.id)
            },
        )
        await { AppGraph.service.state.value.schedules.single().done }
        assertTrue(AppGraph.service.state.value.schedules.single().done)
        await { shadowOf(manager).allNotifications.isEmpty() }
    }

    @Test
    fun wallpaperFramesMove() {
        val clock = MutableClock(System.currentTimeMillis())
        val service = GameService(MemoryGameStore(), clock, TimeZone.of("Asia/Seoul"))
        runBlocking {
            service.completeOnboarding("보리")
            grow(service, clock, Stage.BABY)
        }
        val state = service.state.value
        val first = drawWallpaper(state, 0)
        val second = drawWallpaper(state, 600)
        saveBitmap(first, "wallpaper-a.png", publish = false)
        saveBitmap(second, "wallpaper-b.png", publish = false)
        assertFalse(first.sameAs(second))
    }
}

private fun primeComposeResources() {
    val provider = Class.forName("org.jetbrains.compose.resources.AndroidContextProvider")
    val companion = provider.getField("Companion").get(null)
    val setter = companion.javaClass.methods.first { it.name == "setANDROID_CONTEXT" }
    setter.invoke(companion, RuntimeEnvironment.getApplication())
}

private fun seoulMillis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
    LocalDateTime(year, month, day, hour, minute).toInstant(TimeZone.of("Asia/Seoul")).toEpochMilliseconds()

private class MutableClock(var now: Long) : TimeProvider {
    override fun now(): Long = now
}

private fun artifact(name: String, publish: Boolean = true): File {
    val dir = File(if (publish) "/opt/cursor/artifacts" else "/tmp/mongle-frames")
    dir.mkdirs()
    return File(dir, name)
}

private fun saveBitmap(bitmap: Bitmap, name: String, publish: Boolean = true) {
    FileOutputStream(artifact(name, publish)).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
}

private fun drawWallpaper(state: app.mongle.model.GameState, shift: Long): Bitmap {
    val bitmap = Bitmap.createBitmap(540, 960, Bitmap.Config.ARGB_8888)
    WallpaperPainter.draw(Canvas(bitmap), state, shift, RuntimeEnvironment.getApplication())
    return bitmap
}

@OptIn(ExperimentalGlanceRemoteViewsApi::class)
private suspend fun saveGlance(
    context: Context,
    name: String,
    widthDp: Int,
    heightDp: Int,
    publish: Boolean = true,
    content: @androidx.compose.runtime.Composable () -> Unit,
) {
    val result = GlanceRemoteViews().compose(
        context = context,
        size = DpSize(widthDp.dp, heightDp.dp),
        content = content,
    )
    val density = context.resources.displayMetrics.density
    val width = (widthDp * density).toInt()
    val height = (heightDp * density).toInt()
    saveBitmap(renderRemoteViews(context, result.remoteViews, width, height), name, publish)
}

private fun renderRemoteViews(context: Context, remote: RemoteViews, width: Int, height: Int): Bitmap {
    val host = FrameLayout(context)
    val child = remote.apply(context, host)
    host.addView(child, FrameLayout.LayoutParams(width, height))
    val widthSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY)
    val heightSpec = View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
    host.measure(widthSpec, heightSpec)
    host.layout(0, 0, width, height)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    host.draw(Canvas(bitmap))
    return bitmap
}

private suspend fun grow(service: GameService, clock: MutableClock, stage: Stage) {
    var guard = 0
    while (service.state.value.stage.ordinal < stage.ordinal && guard < 16) {
        val stamp = clock.now
        service.addSchedule("성장 $guard", stamp, RepeatRule.NONE)
        clock.now = stamp + 3 * 60_000
        val id = service.state.value.schedules.last { !it.done }.id
        service.completeSchedule(id)
        guard += 1
    }
}

private fun saveNotification(context: Context, notification: android.app.Notification, name: String) {
    val remote = notification.contentView
        ?: notification.bigContentView
        ?: inflateSystemContent(context, notification)
    val view = remote?.apply(context, FrameLayout(context))
    if (view == null) {
        error("알림 RemoteViews를 만들지 못했습니다. 제목=${notification.extras.getString(android.app.Notification.EXTRA_TITLE)}")
    }
    val width = 540
    val height = 220
    view.measure(
        View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.AT_MOST),
    )
    view.layout(0, 0, width, view.measuredHeight.coerceAtLeast(120))
    val bitmap = Bitmap.createBitmap(width, view.height.coerceAtLeast(120), Bitmap.Config.ARGB_8888)
    view.draw(Canvas(bitmap))
    saveBitmap(bitmap, name, publish = false)
}

/** API 34+ leaves contentView null until SystemUI inflates it. The builder method is @hide but present in android-all. */
private fun inflateSystemContent(context: Context, notification: android.app.Notification): RemoteViews? {
    val builder = android.app.Notification.Builder.recoverBuilder(context, notification)
    val names = listOf("createContentView", "createBigContentView", "createHeadsUpContentView")
    for (name in names) {
        val method = builder.javaClass.methods.firstOrNull { it.name == name && it.parameterCount == 0 } ?: continue
        val result = method.invoke(builder)
        if (result is RemoteViews) return result
    }
    return null
}

private fun await(timeoutMs: Long = 4000, cond: () -> Boolean) {
    val start = System.currentTimeMillis()
    while (!cond()) {
        shadowOf(Looper.getMainLooper()).idle()
        if (System.currentTimeMillis() - start > timeoutMs) break
        Thread.sleep(40)
    }
    assertTrue(cond())
}
