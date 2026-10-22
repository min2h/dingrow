package app.mongle

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.font.FontWeight
import app.mongle.data.MemoryGameStore
import app.mongle.logic.ConditionState
import app.mongle.logic.HourForecast
import app.mongle.logic.WeatherSnapshot
import app.mongle.model.RepeatRule
import app.mongle.logic.ScheduleDraft
import app.mongle.platform.AndroidHooks
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
class HomeStatesTest {
    @get:Rule val rule = createComposeRule()
    private val zone = TimeZone.of("Asia/Seoul")

    private fun at(h: Int, m: Int) = LocalDateTime(2026, 10, 22, h, m).toInstant(zone).toEpochMilliseconds()

    private fun wx(now: Long, code: Int, temp: Double, wind: Double = 6.0, precip: Double = 0.0) =
        WeatherSnapshot(temp, code, wind, precip, now, 37.57, 126.98, (1..10).map { HourForecast(now + it * 3_600_000L, if (it == 4) 61 else code, temp - 1) })

    private fun setup(hour: Int, minute: Int = 0, lang: String = "ko", schedule: Boolean = true, block: suspend GameService.(Long) -> Unit = {}): GameService {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Seoul"))
        primeComposeResources()
        AndroidHooks.sync = {}; AndroidHooks.notifyNow = { _, _ -> }; AndroidHooks.reloadWidgets = {}
        val now = at(hour, minute)
        val clock = MutableClock(now)
        val s = GameService(MemoryGameStore(), clock, zone)
        runBlocking {
            s.completeOnboarding("몽이")
            s.updateSettings(s.state.value.settings.copy(language = lang, useLocation = true, newScheduleConfirm = false))
            if (schedule) s.addDraft(ScheduleDraft(title = if (lang == "ko") "술약속" else "Drinks", timeMillis = now + 95 * 60_000L, repeat = RepeatRule.NONE, place = if (lang == "ko") "강남역" else "Gangnam Stn"))
            s.block(now)
            s.refresh()
        }
        rule.mainClock.autoAdvance = false
        rule.setContent {
            CompositionLocalProvider(LocalPetMotion provides (0.35f to 0.2f)) {
                MongleTheme { MongleAppScreen(s, PermissionSnapshot(true, true, true, true), {}, {}, {}) }
            }
        }
        repeat(3) { rule.mainClock.advanceTimeBy(300); rule.waitForIdle() }
        return s
    }

    private fun shot(name: String) = rule.onRoot().captureRoboImage(artifact(name))

    private fun assertBubbleAnchored() {
        val room = rule.onNodeWithTag("room").fetchSemanticsNode().boundsInRoot
        val bubble = rule.onNodeWithTag("speech-bubble").fetchSemanticsNode()
        val b = bubble.boundsInRoot
        assertTrue("bubble inside room horizontally $b in $room", b.left >= room.left - 1 && b.right <= room.right + 1)
        assertTrue("bubble inside room vertically $b in $room", b.top >= room.top - 1 && b.bottom <= room.bottom + 1)
        // Above the pet's head: bubble bottom in the lower-middle part of the room, never on the stats below.
        assertTrue("bubble above pet", b.bottom < room.bottom - room.height * 0.25f)
    }

    @Test fun noonClearWithScheduleEmphasis() {
        setup(12, 30) { setWeather(wx(it, 0, 21.0)) }
        rule.onNodeWithTag("weather-chip").fetchSemanticsNode()
        val text = rule.onNodeWithTag("next-headline").fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)!!.first()
        assertTrue(text.text, text.text.contains("14:05") && text.text.contains("강남역") && text.text.contains("술약속"))
        assertTrue("time/place/title bold", text.spanStyles.count { it.item.fontWeight == FontWeight.ExtraBold } == 3)
        assertBubbleAnchored()
        shot("home-noon.png")
    }

    @Test fun noWeatherWithoutLocation() {
        setup(9, 0) { setWeather(wx(it, 0, 21.0)); updateSettings(state.value.settings.copy(useLocation = false)) }
        assertEquals(0, rule.onAllNodesWithTag("weather-chip").fetchSemanticsNodes().size)
        assertEquals(0, rule.onAllNodesWithTag("weather-fx").fetchSemanticsNodes().size)
        shot("home-no-weather.png")
    }

    @Test fun dawn() { setup(5, 40) { setWeather(wx(it, 0, 6.0)) }; assertBubbleAnchored(); shot("home-dawn.png") }
    @Test fun night() { setup(22, 10) { setWeather(wx(it, 0, 12.0)) }; shot("home-night.png") }
    @Test fun rain() { setup(15, 20) { setWeather(wx(it, 63, 11.0, precip = 4.0)) }; rule.onNodeWithTag("weather-fx").fetchSemanticsNode(); assertBubbleAnchored(); shot("home-rain.png") }
    @Test fun snow() { setup(10, 5) { setWeather(wx(it, 73, -3.0)) }; shot("home-snow.png") }
    @Test fun storm() { setup(17, 45) { setWeather(wx(it, 3, 9.0, wind = 52.0)) }; shot("home-storm.png") }
    @Test fun cold() { setup(10, 0) { setWeather(wx(it, 0, -4.0)) }; rule.onNodeWithTag("cond-cold").fetchSemanticsNode(); shot("home-cold.png") }
    @Test fun hot() { setup(14, 0) { setWeather(wx(it, 0, 31.0)) }; rule.onNodeWithTag("cond-hot").fetchSemanticsNode(); shot("home-hot.png") }

    @Test fun sickShowsRecoveryAndMedicine() {
        val s = setup(11, 0) { debugSetCondition(ConditionState(sick = true, recovery = 20, discomfort = 0, checkedAt = it)) }
        rule.onNodeWithTag("cond-sick").fetchSemanticsNode()
        rule.onNodeWithTag("medicine").fetchSemanticsNode()
        shot("home-sick.png")
        rule.onNodeWithTag("next-complete").performClick(); repeat(3) { rule.mainClock.advanceTimeBy(300); rule.waitForIdle() }
        assertEquals(40, s.state.value.recovery)
    }

    @Test fun emptyScheduleCardAddsDirectly() {
        setup(9, 0, schedule = false)
        rule.onNodeWithTag("next-add").performClick(); repeat(3) { rule.mainClock.advanceTimeBy(300); rule.waitForIdle() }
        shot("home-empty-add.png")
    }

    @Test fun englishRain() { setup(15, 20, lang = "en") { setWeather(wx(it, 63, 11.0, precip = 4.0)) }; assertBubbleAnchored(); shot("en-home-rain.png") }
    @Test fun englishSick() { setup(11, 0, lang = "en") { debugSetCondition(ConditionState(sick = true, recovery = 60, discomfort = 0, checkedAt = it)) }; shot("en-home-sick.png") }
}
