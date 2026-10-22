package app.mongle

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.mongle.data.MemoryGameStore
import app.mongle.logic.ConditionState
import app.mongle.logic.ScheduleDraft
import app.mongle.model.RepeatRule
import app.mongle.platform.AndroidHooks
import app.mongle.platform.NavigationBus
import app.mongle.service.GameService
import app.mongle.ui.LocalPetMotion
import app.mongle.ui.MongleAppScreen
import app.mongle.ui.MongleTheme
import app.mongle.ui.PermissionSnapshot
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "ko-rKR-w360dp-h740dp-hdpi")
class ScreensV4Test {
    @get:Rule val rule = createComposeRule()
    private val zone = TimeZone.of("Asia/Seoul")
    private fun at(h: Int, m: Int) = LocalDateTime(2026, 10, 22, h, m).toInstant(zone).toEpochMilliseconds()
    private fun settle() = repeat(4) { rule.mainClock.advanceTimeBy(250); rule.waitForIdle() }

    private fun app(lang: String = "ko", block: suspend GameService.() -> Unit = {}): GameService {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Seoul"))
        primeComposeResources()
        AndroidHooks.sync = {}; AndroidHooks.notifyNow = { _, _ -> }; AndroidHooks.reloadWidgets = {}
        val s = GameService(MemoryGameStore(), MutableClock(System.currentTimeMillis()), zone)
        runBlocking { s.completeOnboarding("몽이"); s.updateSettings(s.state.value.settings.copy(language = lang, newScheduleConfirm = false)); s.block() }
        rule.mainClock.autoAdvance = false
        rule.setContent { CompositionLocalProvider(LocalPetMotion provides (0.35f to 0.2f)) { MongleTheme { MongleAppScreen(s, PermissionSnapshot(true, true, true, true), {}, {}, {}) } } }
        settle()
        return s
    }

    private suspend fun GameService.seedDay(lang: String) {
        val base = System.currentTimeMillis()
        val day = kotlinx.datetime.Instant.fromEpochMilliseconds(base).let { it.toEpochMilliseconds() - it.toEpochMilliseconds() % 86_400_000L } - 9 * 3_600_000L
        val items = if (lang == "ko") listOf(Triple("출근", "회사", "work"), Triple("팀 회의", "3층 회의실", "meeting"), Triple("점심 약속", "을지로", "meeting"), Triple("운동 가기", "헬스장", "exercise"), Triple("술약속", "강남역", "social"), Triple("장보기", "", "chores"))
            else listOf(Triple("Work", "Office", "work"), Triple("Team sync", "Room 3F", "meeting"), Triple("Lunch", "Euljiro", "meeting"), Triple("Gym", "Gym", "exercise"), Triple("Drinks", "Gangnam Stn", "social"), Triple("Groceries", "", "chores"))
        val hours = listOf(9 * 60, 10 * 60 + 30, 12 * 60 + 30, 18 * 60, 19 * 60 + 30, 21 * 60)
        items.zip(hours).forEach { (it, m) -> addDraft(ScheduleDraft(title = it.first, timeMillis = day + 24 * 3_600_000L * ((base - day) / 86_400_000L) + m * 60_000L, repeat = RepeatRule.NONE, place = it.second, category = it.third)) }
    }

    @Test fun scheduleListCheckAndWidgetFocus() {
        val s = app { seedDay("ko") }
        rule.onNodeWithTag("tab-Schedule").performClick(); settle()
        rule.onRoot().captureRoboImage(artifact("schedule-list.png"))
        val first = s.state.value.schedules.minBy { it.timeMillis }
        rule.onNodeWithTag("complete-${first.id}").performClick()
        rule.mainClock.advanceTimeBy(120); rule.waitForIdle()
        rule.onRoot().captureRoboImage(artifact("schedule-check-anim.png"))
        settle()
        assertTrue(s.state.value.schedules.first { it.id == first.id }.done)
        rule.onNodeWithTag("done-${first.id}", useUnmergedTree = true).fetchSemanticsNode()
        // Deep link from the widget focuses + highlights that schedule.
        val target = s.state.value.schedules.maxBy { it.timeMillis }
        rule.onNodeWithTag("tab-Home").performClick(); settle()
        NavigationBus.request("schedule/${target.id}"); settle()
        rule.onNodeWithTag("row-highlight", useUnmergedTree = true).fetchSemanticsNode()
        rule.onRoot().captureRoboImage(artifact("schedule-focus.png"))
    }

    @Test fun englishScheduleList() {
        app("en") { seedDay("en") }
        rule.onNodeWithTag("tab-Schedule").performClick(); settle()
        rule.onRoot().captureRoboImage(artifact("en-schedule-list.png"))
    }

    @Test fun settingsPagesAndPersistence() {
        val s = app()
        rule.onNodeWithTag("tab-Schedule").performClick(); settle()
        // Empty tab opens the form; close it and open settings from the top bar.
        rule.onNodeWithTag("overlay-back").performClick(); settle()
        rule.onNodeWithTag("open-settings").performClick(); settle()
        rule.onRoot().captureRoboImage(artifact("settings-home.png"))
        assertFalse(s.state.value.settings.useLocation)
        rule.onNodeWithTag("toggle-location").performClick(); settle()
        assertTrue("toggle on without platform bridge", s.state.value.settings.useLocation)
        rule.onNodeWithTag("row-language").performClick(); settle()
        rule.onRoot().captureRoboImage(artifact("settings-language.png"))
        rule.onNodeWithTag("lang-en").performClick(); settle()
        assertEquals("en", s.state.value.settings.language)
        rule.onNodeWithText("Settings").fetchSemanticsNode()
        rule.onNodeWithTag("row-alerts").performScrollTo().performClick(); settle()
        rule.onRoot().captureRoboImage(artifact("en-settings-alerts.png"))
        rule.onNodeWithTag("toggle-brief").performClick(); settle()
        assertFalse(s.state.value.settings.briefingEnabled)
        rule.onNodeWithTag("overlay-back").performClick(); settle()
        rule.onNodeWithTag("row-pet").performScrollTo().performClick(); settle()
        assertFalse("sfx default off", s.state.value.settings.sfxEnabled)
        rule.onNodeWithTag("toggle-wander").performClick(); settle()
        assertFalse(s.state.value.settings.wander)
        rule.onRoot().captureRoboImage(artifact("en-settings-pet.png"))
        rule.onNodeWithTag("overlay-back").performClick(); settle()
        runBlocking { s.updateSettings(s.state.value.settings.copy(language = "ko")) }; settle()
        rule.onNodeWithTag("row-data").performScrollTo().performClick(); settle()
        rule.onRoot().captureRoboImage(artifact("settings-data.png"))
        rule.onNodeWithTag("row-reset").performClick(); settle()
        rule.onNodeWithTag("confirm-yes").performClick(); settle()
        assertFalse(s.state.value.onboarded)
    }

    @Test fun settingsKoreanPages() {
        app()
        rule.onNodeWithTag("tab-My").performClick(); settle()
        rule.onNodeWithTag("open-settings").performClick(); settle()
        rule.onNodeWithTag("row-alerts").performScrollTo().performClick(); settle()
        rule.onRoot().captureRoboImage(artifact("settings-alerts.png"))
        rule.onNodeWithTag("overlay-back").performClick(); settle()
        rule.onNodeWithTag("row-pet").performScrollTo().performClick(); settle()
        rule.onRoot().captureRoboImage(artifact("settings-pet.png"))
    }

    @Test fun medicineOnlyWhileSick() {
        val s = app { debugSetCondition(ConditionState(sick = true, recovery = 0, discomfort = 0, checkedAt = System.currentTimeMillis())) }
        rule.onNodeWithTag("tab-Room").performClick(); settle()
        rule.onNodeWithTag("medicine-card").fetchSemanticsNode()
        rule.onRoot().captureRoboImage(artifact("shop-medicine.png"))
        val coins = s.state.value.coins
        rule.onNodeWithTag("buy-medicine").performClick(); settle()
        if (coins >= 50) {
            assertEquals(coins - 50, s.state.value.coins)
            assertEquals(80, s.state.value.recovery)
        }
        runBlocking { s.debugSetCondition(ConditionState(checkedAt = System.currentTimeMillis())) }; settle()
        assertEquals(0, rule.onAllNodesWithTag("medicine-card").fetchSemanticsNodes().size)
    }
}
