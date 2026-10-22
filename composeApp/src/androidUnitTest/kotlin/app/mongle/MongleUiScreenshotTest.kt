package app.mongle

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
@Config(sdk = [34], qualifiers = "ko-rKR-w360dp-h740dp-hdpi")
class MongleFlowScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun onboardingNamingAndHome() {
        val harness = Harness()
        harness.show()
        composeRule.onNodeWithText("다음").performClick()
        harness.settle()
        composeRule.onNode(hasSetTextAction()).performTextReplacement("보리")
        composeRule.onNodeWithText("시작하기").performClick()
        harness.settle()
        assertEquals("보리", harness.service.state.value.petName)
        assertTrue(harness.service.state.value.onboarded)
    }

    @Test
    fun scheduleAddListAndComplete() {
        val harness = Harness()
        harness.show()
        composeRule.onNodeWithText("다음").performClick()
        harness.settle()
        composeRule.onNode(hasSetTextAction()).performTextReplacement("보리")
        composeRule.onNodeWithText("시작하기").performClick()
        harness.settle()
        composeRule.onNodeWithTag("tab-Schedule").performClick()
        harness.settle()
        // Empty schedule tab opens the add form directly.
        composeRule.onNodeWithTag("field-title").performTextReplacement("물 마시기")
        composeRule.onNodeWithTag("field-place").performTextReplacement("집")
        composeRule.onNodeWithTag("cat-health").performClick()
        composeRule.onNodeWithText("저장").performClick()
        harness.settle()
        val added = harness.service.state.value.schedules.single()
        assertEquals("물 마시기", added.title)
        assertEquals("집", added.place)
        assertEquals("health", added.category)
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
        val reveal = harness.service.state.value
        assertTrue(reveal.pendingGift != null || reveal.pendingDropKind.isNotBlank())
        val coins = reveal.coins
        harness.clickTag("gift-carrier"); harness.settle()
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
        if ("sprout_hat" !in harness.service.state.value.owned) {
            harness.clickTag("buy-sprout_hat")
            harness.settle()
        }
        assertTrue(
            "owned=${harness.service.state.value.owned} coins=${harness.service.state.value.coins} ${harness.service.state.value.statusMessage}",
            "sprout_hat" in harness.service.state.value.owned,
        )
        val activeBefore = harness.service.state.value.activePreset
        harness.clickText("밤하늘 방", substring = true)
        harness.settle()
        assertEquals(activeBefore, harness.service.state.value.activePreset) // locked: 1000 coins
        harness.clickTag("room-buy-cancel")
        harness.settle()
        runBlocking { harness.service.debugAddCoins(1000); assertTrue(harness.service.buyRoom("starry").ok) }
        harness.clickText("밤하늘 방", substring = true)
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

    @Test
    fun languagePickerAndBothLocales() {
        val harness = Harness()
        harness.show()
        harness.capture("ko-onboarding.png")
        composeRule.onNodeWithText("다음").performClick()
        harness.settle()
        harness.capture("ko-onboarding-gift.png")
        composeRule.onNode(hasSetTextAction()).performTextReplacement("보리")
        composeRule.onNodeWithText("시작하기").performClick()
        harness.settle()
        runBlocking {
            harness.service.addSchedule("산책", System.currentTimeMillis() + 3_600_000, RepeatRule.NONE)
        }
        app.mongle.platform.NavigationBus.request("alarm")
        harness.settle()
        composeRule.onNodeWithText("5분 뒤 다시").assertExists()
        harness.capture("ko-alarm.png")
        composeRule.onNodeWithText("5분 뒤 다시").performClick()
        harness.settle()
        composeRule.onNodeWithTag("tab-Schedule").performClick()
        harness.settle()
        harness.clickTag("open-settings")
        harness.settle()
        harness.capture("ko-settings.png")
        harness.clickTag("row-language")
        harness.settle()
        harness.clickTag("lang-en")
        harness.settle()
        composeRule.onNodeWithText("Settings").assertExists()
        composeRule.onNodeWithText("Language · Display").assertExists()
        assertEquals("en", harness.service.state.value.settings.language)
        harness.capture("en-settings.png")
        harness.clickTag("overlay-back")
        harness.settle()
        File("/tmp/sem-back.txt").writeText(composeRule.onRoot().printToString())
        harness.clickTag("tab-Home")
        harness.settle()
        composeRule.onNodeWithText("Dingrow").assertExists()
        harness.capture("en-home.png")
        composeRule.onNodeWithTag("tab-Schedule").performClick()
        harness.settle()
        harness.capture("en-schedule.png")
        composeRule.onNodeWithTag("tab-Room").performClick()
        harness.settle()
        harness.capture("en-room.png")
        harness.capture("en-shop.png")
        composeRule.onNodeWithTag("tab-My").performClick()
        harness.settle()
        harness.capture("en-dex.png")
        app.mongle.platform.NavigationBus.request("alarm")
        harness.settle()
        composeRule.onNodeWithText("In 5 minutes").assertExists()
        harness.capture("en-alarm.png")
    }

    @Test
    fun scheduleFormBothLocales() {
        val harness = Harness()
        val language = mutableStateOf("ko")
        composeRule.setContent {
            app.mongle.ui.LanguageProvider(language.value) {
                MongleTheme { app.mongle.ui.ScheduleForm(editing = false, onSave = { _, _ -> }, onClose = {}) }
            }
        }
        harness.capture("ko-schedule-form.png")
        composeRule.runOnIdle { language.value = "en" }
        harness.settle()
        File("/tmp/sem-form.txt").writeText(composeRule.onRoot().printToString())
        composeRule.onNodeWithText("Add a task").assertExists()
        harness.capture("en-schedule-form.png")
    }

    @Test
    fun reactionGalleryBothLocales() {
        val harness = Harness()
        val language = mutableStateOf("ko")
        composeRule.setContent {
            app.mongle.ui.LanguageProvider(language.value) {
                MongleTheme { app.mongle.ui.ReactionGallery(previewPeak = true) }
            }
        }
        harness.capture("ko-reactions.png")
        composeRule.runOnIdle { language.value = "en" }
        harness.settle()
        composeRule.onNodeWithText("Mongi tap reactions").assertExists()
        harness.capture("en-reactions.png")
    }

    @Test
    fun resellSheetBothLocales() {
        val harness = Harness()
        val legendary = app.mongle.model.Catalog.byId("comet_shard")!!
        val language = mutableStateOf("ko")
        composeRule.setContent {
            app.mongle.ui.LanguageProvider(language.value) {
                MongleTheme {
                    Box(Modifier.fillMaxSize().background(app.mongle.ui.Cream), contentAlignment = Alignment.BottomCenter) {
                        app.mongle.ui.ResellSheet(legendary, {}, {})
                    }
                }
            }
        }
        harness.capture("ko-resell.png")
        composeRule.runOnIdle { language.value = "en" }
        harness.settle()
        composeRule.onNodeWithText("Sells for 760").assertExists()
        harness.capture("en-resell.png")
    }

    @Test
    fun coinShopBothLocales() {
        val harness = Harness()
        val language = mutableStateOf("en")
        val phase = mutableStateOf("list")
        composeRule.setContent {
            app.mongle.ui.LanguageProvider(language.value) {
                MongleTheme {
                    app.mongle.ui.CoinShopScreen(
                        coins = 2400,
                        phase = phase.value,
                        product = if (phase.value == "confirm") app.mongle.logic.StoreProducts.coins1000 else null,
                        message = "",
                        onPick = {},
                        onPay = {},
                        onRestore = {},
                        onClose = {},
                    )
                }
            }
        }
        composeRule.onNodeWithText("Coin shop").assertExists()
        harness.capture("en-coin-shop.png")
        composeRule.runOnIdle {
            language.value = "ko"
            phase.value = "confirm"
        }
        harness.settle()
        composeRule.onNodeWithText("결제하기").assertExists()
        harness.capture("ko-coin-shop.png")
    }

    private inner class Harness {
        val clock = MutableClock(System.currentTimeMillis())
        val service = GameService(MemoryGameStore(), clock, TimeZone.of("Asia/Seoul")).also { it.giftChance = { false } }
        var onWallpaper: () -> Unit = {}
        var motion by mutableStateOf(0.15f to 0.05f)

        init {
            primeComposeResources()
            app.mongle.l10n.AppLocale.preference = "system"
            app.mongle.ui.languageTick.value = "system"
            AndroidHooks.sync = {}
            AndroidHooks.notifyNow = { _, _ -> }
            AndroidHooks.reloadWidgets = {}
            composeRule.mainClock.autoAdvance = false
        }

        fun show() {
            app.mongle.ui.NotifExplain.show = false
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
            repeat(2) {
                androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
                composeRule.mainClock.advanceTimeBy(200)
                composeRule.waitForIdle()
            }
        }

        fun capture(name: String) {
            settle()
            composeRule.onRoot().captureRoboImage(artifact(name))
        }

        fun clickText(text: String, substring: Boolean = false) {
            try {
                composeRule.onNodeWithText(text, substring = substring).performClick()
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
        val service = GameService(MemoryGameStore(), clock, TimeZone.of("Asia/Seoul")).also { it.giftChance = { false } }
        service.completeOnboarding("보리")
        service.addSchedule("물 마시기", clock.now + 60_000, RepeatRule.NONE)
        AppGraph.installForTests(service)
        val context = RuntimeEnvironment.getApplication()
        saveGlance(context, "widget-small.png", 180, 180) { SmallBody(service.state.value) }
        saveGlance(context, "widget-large.png", 320, 360) { LargeBody(service.state.value) }
        app.mongle.l10n.AppLocale.preference = "ko"
        saveGlance(context, "widget-small-ko.png", 180, 180) { SmallBody(service.state.value) }
        saveGlance(context, "widget-large-ko.png", 320, 360) { LargeBody(service.state.value) }
        app.mongle.l10n.AppLocale.preference = "system"

        FeedAction().onAction(context, glanceId, actionParametersOf())
        assertTrue(
            service.state.value.hunger > 80 ||
                service.state.value.statusMessage.contains("먹") ||
                service.state.value.statusMessage.contains("Fed"),
        )

        grow(service, clock, Stage.CHILD)
        CompleteAction().onAction(context, glanceId, actionParametersOf())
        assertTrue(service.state.value.schedules.first().done || service.state.value.schedules.isEmpty())

        runBlocking { service.debugAddCoins(700); assertTrue(service.buyRoom("mint").ok) }
        val preset = service.state.value.activePreset
        PresetAction().onAction(context, glanceId, actionParametersOf())
        assertNotEquals(preset, service.state.value.activePreset)

        service.startOuting()
        clock.now += 21 * 60_000
        service.refresh()
        val reveal = service.state.value
        assertTrue(reveal.pendingGift != null || reveal.pendingDropKind.isNotBlank())
        val beforeSell = reveal.coins
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
        val trigger = alarms.map { it.triggerAtTime }.filter { it < now + 3_600_000 }.maxOrNull() ?: 0L
        assertTrue("briefing alarm scheduled too", alarms.any { it.triggerAtTime > now + 70_000 })
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
        await { shadowOf(manager).allNotifications.any { it.actions?.isNotEmpty() == true } }
        assertTrue("new-schedule confirmation posted", shadowOf(manager).allNotifications.any { it.extras.getString(android.app.Notification.EXTRA_TITLE) == "New schedule added" })
        val notification = shadowOf(manager).allNotifications.single { it.actions?.isNotEmpty() == true }
        assertEquals("보리 is calling", notification.extras.getString(android.app.Notification.EXTRA_TITLE))
        assertEquals(3, notification.actions.size)
        assertEquals("Check", notification.actions[0].title.toString())
        assertEquals("In 5 min", notification.actions[1].title.toString())
        assertEquals("Done", notification.actions[2].title.toString())
        saveNotification(context, notification, "notification-en.png")

        app.mongle.l10n.AppLocale.preference = "ko"
        ReminderReceiver().onReceive(
            context,
            android.content.Intent(AlarmScheduler.ACTION_SHOW).apply {
                putExtra(AlarmScheduler.EXTRA_SCHEDULE_ID, schedule.id)
            },
        )
        await {
            shadowOf(manager).allNotifications.singleOrNull { it.actions?.isNotEmpty() == true }?.extras?.getString(android.app.Notification.EXTRA_TITLE) == "보리가 불러요"
        }
        val korean = shadowOf(manager).allNotifications.single { it.actions?.isNotEmpty() == true }
        assertEquals("보리가 불러요", korean.extras.getString(android.app.Notification.EXTRA_TITLE))
        assertEquals("확인", korean.actions[0].title.toString())
        assertEquals("5분 뒤", korean.actions[1].title.toString())
        assertEquals("완료", korean.actions[2].title.toString())
        saveNotification(context, korean, "notification.png")
        app.mongle.l10n.AppLocale.preference = "system"

        ReminderReceiver().onReceive(
            context,
            android.content.Intent(AlarmScheduler.ACTION_SNOOZE).apply {
                putExtra(AlarmScheduler.EXTRA_SCHEDULE_ID, schedule.id)
            },
        )
        await { AppGraph.service.state.value.schedules.single().timeMillis >= System.currentTimeMillis() + 3 * 60_000 }
        val snoozed = AppGraph.service.state.value.schedules.single().timeMillis
        val delta = snoozed - System.currentTimeMillis()
        assertTrue("delta=$delta", delta in 4 * 60_000..6 * 60_000)

        ReminderReceiver().onReceive(
            context,
            android.content.Intent(AlarmScheduler.ACTION_DONE).apply {
                putExtra(AlarmScheduler.EXTRA_SCHEDULE_ID, schedule.id)
            },
        )
        await { AppGraph.service.state.value.schedules.single().done }
        assertTrue(AppGraph.service.state.value.schedules.single().done)
        await { shadowOf(manager).allNotifications.none { it.actions?.isNotEmpty() == true } }
    }

    @Test
    fun wallpaperFramesMove() {
        val clock = MutableClock(System.currentTimeMillis())
        val service = GameService(MemoryGameStore(), clock, TimeZone.of("Asia/Seoul")).also { it.giftChance = { false } }
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

internal fun primeComposeResources() {
    val provider = Class.forName("org.jetbrains.compose.resources.AndroidContextProvider")
    val companion = provider.getField("Companion").get(null)
    val setter = companion.javaClass.methods.first { it.name == "setANDROID_CONTEXT" }
    setter.invoke(companion, RuntimeEnvironment.getApplication())
}

private fun seoulMillis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
    LocalDateTime(year, month, day, hour, minute).toInstant(TimeZone.of("Asia/Seoul")).toEpochMilliseconds()

internal class MutableClock(var now: Long) : TimeProvider {
    override fun now(): Long = now
}

internal fun artifact(name: String, publish: Boolean = true): File {
    val dir = File(if (publish) System.getProperty("dingrow.shots") ?: "/workspace/dingrow-shots" else "/tmp/mongle-frames")
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
internal suspend fun saveGlance(
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

internal fun renderRemoteViews(context: Context, remote: RemoteViews, width: Int, height: Int): Bitmap {
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
