package app.mongle

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import app.mongle.auth.AuthSession
import app.mongle.auth.LocalAuthService
import app.mongle.data.MemoryGameStore
import app.mongle.platform.AndroidHooks
import app.mongle.service.GameService
import app.mongle.ui.*
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** One check per settings row: change it, then assert the visible effect / persisted value. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "ko-rKR-w360dp-h740dp-hdpi")
class SettingsRowsTest {
    @get:Rule val rule = createComposeRule()
    private fun settle() = repeat(4) { rule.mainClock.advanceTimeBy(250); rule.waitForIdle() }
    private var pinned = 0
    private lateinit var auth: LocalAuthService
    private var saved: AuthSession? = AuthSession("google", "u1", "민형", "m@example.com", "Pixel 8 · Android 15", 1L)

    private fun open(lang: String = "ko"): GameService {
        primeComposeResources()
        LocationBridge.request = null; BackupBridge.export = null; BackupBridge.pickRestore = null
        AndroidHooks.sync = {}; AndroidHooks.notifyNow = { _, _ -> }; AndroidHooks.reloadWidgets = {}
        val s = GameService(MemoryGameStore(), MutableClock(System.currentTimeMillis()), TimeZone.of("Asia/Seoul")).also { it.giftChance = { false } }
        runBlocking { s.completeOnboarding("몽이"); s.updateSettings(s.state.value.settings.copy(language = lang, newScheduleConfirm = false)) }
        auth = LocalAuthService(load = { saved }, persist = { saved = it }).also { it.restore() }
        rule.mainClock.autoAdvance = false
        rule.setContent {
            CompositionLocalProvider(LocalPetMotion provides (0.35f to 0.2f)) {
                MongleTheme { AuthGate(auth, splashMillis = 0) { MongleAppScreen(s, PermissionSnapshot(true, true, true, true), {}, {}, {}, onPinWidget = { pinned++ }) } }
            }
        }
        settle(); rule.mainClock.advanceTimeBy(1_000); settle()
        rule.onNodeWithTag("tab-My").performClick(); settle()
        rule.onNodeWithTag("open-settings").performClick(); settle()
        return s
    }

    private fun click(tag: String) {
        rule.mainClock.autoAdvance = true
        runCatching { rule.onNodeWithTag(tag).performScrollTo() }
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick); settle()
    }
    private fun page(row: String) { click(row) }
    private fun back() { rule.onNodeWithTag("overlay-back").performClick(); settle() }
    private fun seesText(t: String) = rule.onAllNodesWithText(t, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    @Test fun displayRows() {
        val s = open()
        // 언어 → visible title switches to English.
        click("row-language"); click("lang-en")
        assertEquals("en", s.state.value.settings.language)
        assertTrue(seesText("Settings"))
        click("row-language"); click("lang-ko"); assertTrue(seesText("설정"))
        // Theme → instant.
        click("seg-opt-다크"); assertEquals("dark", s.state.value.settings.theme); assertTrue(ThemeState.dark)
        click("seg-opt-라이트"); assertFalse(ThemeState.dark)
        // 12/24h → briefing time label format changes.
        click("seg-opt-24시간"); assertTrue(s.state.value.settings.use24h)
        page("row-alerts"); assertTrue(seesText("07:30")); back()
        click("seg-opt-12시간"); assertFalse(s.state.value.settings.use24h)
        page("row-alerts"); assertTrue(seesText("오전 7:30")); back()
        // Week start.
        click("seg-opt-월"); assertTrue(s.state.value.settings.weekStartsMonday)
        click("seg-opt-일"); assertFalse(s.state.value.settings.weekStartsMonday)
        // °C/°F.
        click("seg-opt-°F"); assertTrue(s.state.value.settings.fahrenheit)
        click("seg-opt-°C"); assertFalse(s.state.value.settings.fahrenheit)
    }

    @Test fun locationToggleRequestsPermissionAndGatesWeatherAlerts() {
        val s = open()
        var asked = 0
        LocationBridge.request = { asked++ }
        rule.onNodeWithTag("toggle-wx-alerts").assertIsNotEnabled()
        click("toggle-location")
        assertEquals(1, asked)
        assertFalse("only the OS grant turns it on", s.state.value.settings.useLocation)
        runBlocking { s.updateSettings(s.state.value.settings.copy(useLocation = true)) }; settle()
        rule.onNodeWithTag("toggle-wx-alerts").assertIsEnabled()
        val before = s.state.value.settings.weatherAlerts
        click("toggle-wx-alerts"); assertNotEquals(before, s.state.value.settings.weatherAlerts)
    }

    @Test fun notificationPageRows() {
        val s = open()
        page("row-alerts")
        fun flip(tag: String, read: () -> Boolean) { val b = read(); click(tag); assertNotEquals(tag, b, read()) }
        flip("toggle-sched") { s.state.value.settings.scheduleAlerts }
        val lead = s.state.value.settings.defaultLeadMinutes; click("row-lead"); assertNotEquals(lead, s.state.value.settings.defaultLeadMinutes)
        flip("toggle-newc") { s.state.value.settings.newScheduleConfirm }
        flip("toggle-brief") { s.state.value.settings.briefingEnabled }
        val bm = s.state.value.settings.briefingMinute; click("row-brief-time"); assertEquals(bm + 30, s.state.value.settings.briefingMinute)
        assertTrue(seesText("오전 8:00") || seesText("08:00"))
        flip("toggle-bwx") { s.state.value.settings.briefingWeather }
        flip("toggle-gift") { s.state.value.settings.giftAlerts }
        flip("toggle-tips") { s.state.value.settings.tipsEnabled }
        click("toggle-quiet"); assertTrue(s.state.value.settings.quietStartMinute >= 0); assertTrue(seesText("23:00 – 07:00"))
        rule.onRoot().captureRoboImage(artifact("settings-alerts-rows.png"))
    }

    @Test fun petSoundWidgetRows() {
        val s = open()
        page("row-pet")
        fun flip(tag: String, read: () -> Boolean) { val b = read(); click(tag); assertNotEquals(tag, b, read()) }
        flip("toggle-wander") { s.state.value.settings.wander }
        flip("toggle-reduce") { s.state.value.settings.reduceMotion }
        flip("toggle-sfx") { s.state.value.settings.sfxEnabled }
        val snd = s.state.value.settings.alarmSound; click("row-alarm-sound"); assertNotEquals(snd, s.state.value.settings.alarmSound)
        flip("toggle-haptics") { s.state.value.settings.haptics }
        flip("toggle-wlist") { s.state.value.settings.widgetList }
        flip("toggle-wcond") { s.state.value.settings.widgetCondition }
        flip("toggle-wbub") { s.state.value.settings.widgetBubble }
        click("seg-opt-자주"); assertEquals(2, s.state.value.settings.bubbleFrequency)
    }

    @Test fun widgetAddAndBackupRows() {
        val s = open()
        click("row-widget"); assertEquals(1, pinned)
        page("row-data")
        var exported: String? = null
        BackupBridge.export = { exported = it }
        click("row-export")
        assertTrue(exported!!.contains("\"dingrow\""))
        runBlocking { s.debugAddCoins(500) }
        val coinsAfter = s.state.value.coins
        BackupBridge.pickRestore = { cb -> cb(exported!!) }
        click("row-restore")
        rule.mainClock.autoAdvance = true; rule.waitUntil(5_000) { s.state.value.coins == coinsAfter - 500 }; rule.mainClock.autoAdvance = false
        BackupBridge.pickRestore = { cb -> cb("not a backup") }
        click("row-restore")
        assertEquals(coinsAfter - 500, s.state.value.coins)
    }

    @Test fun accountScreenAndSignOutReturnsToLogin() {
        open()
        click("row-profile")
        rule.onNodeWithTag("settings-account").fetchSemanticsNode()
        assertTrue(seesText("m@example.com"))
        rule.onRoot().captureRoboImage(artifact("settings-account.png"))
        click("sign-out")
        rule.onNodeWithTag("confirm-yes").performClick(); settle()
        rule.mainClock.advanceTimeBy(1_000); settle()
        rule.onNodeWithTag("login").fetchSemanticsNode()
        assertNull(saved)
    }
}
