package app.mongle

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.semantics.getOrNull
import app.mongle.data.MemoryGameStore
import app.mongle.platform.AndroidHooks
import app.mongle.service.GameService
import app.mongle.ui.LocalPetMotion
import app.mongle.ui.MongleAppScreen
import app.mongle.ui.MongleTheme
import app.mongle.ui.PermissionSnapshot
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** "왜 save가 안되냐": every save path, tapped like a user (real touch injection, no semantic shortcuts). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "ko-rKR-w360dp-h740dp-hdpi")
class SaveFlowsTest {
    @get:Rule val rule = createComposeRule()
    private fun settle() = repeat(4) { rule.mainClock.advanceTimeBy(250); rule.waitForIdle() }
    private fun exists(t: String) = rule.onAllNodesWithTag(t, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun app(lang: String = "ko", block: suspend GameService.() -> Unit = {}): GameService {
        primeComposeResources()
        app.mongle.ui.NotifExplain.show = false
        AndroidHooks.sync = {}; AndroidHooks.notifyNow = { _, _ -> }; AndroidHooks.reloadWidgets = {}
        val s = GameService(MemoryGameStore(), MutableClock(System.currentTimeMillis()), TimeZone.of("Asia/Seoul")).also { it.giftChance = { false } }
        runBlocking { s.completeOnboarding("몽이"); s.updateSettings(s.state.value.settings.copy(language = lang)); s.debugAddCoins(3000); s.block(); s.ackLevelUp(s.state.value.level) }
        rule.mainClock.autoAdvance = false
        rule.setContent { CompositionLocalProvider(LocalPetMotion provides (0.35f to 0.2f)) { MongleTheme { MongleAppScreen(s, PermissionSnapshot(true, true, true, true), {}, {}, {}) } } }
        settle()
        return s
    }
    private fun tap(t: String) {
        rule.mainClock.autoAdvance = true
        runCatching { rule.onNodeWithTag(t).performScrollTo() }
        rule.onNodeWithTag(t).performClick()
        rule.mainClock.autoAdvance = false
        settle()
    }
    private fun shot(name: String) = rule.onRoot().captureRoboImage(artifact(name))

    private fun openDecorate(lang: String = "ko", owned: List<String> = listOf("mug", "beanie")): GameService {
        val s = app(lang) { buyAll(owned) }
        tap("tab-Room"); tap("open-decorate")
        assertTrue(exists("decorate-screen"))
        return s
    }

    @Test fun decorateSaveWithoutChangesExplainsWhy() {
        val s = openDecorate()
        tap("deco-save")
        shot("save-deco-nochange.png")
        assertTrue("tapping save with no changes must say why", exists("deco-save-hint"))
        assertTrue(exists("decorate-screen"))
        assertEquals(emptyList<Any>(), s.state.value.placements.filter { it.defId == "mug" })
    }

    @Test fun decoratePlaceDragSave() {
        val s = openDecorate()
        tap("deco-item-mug")
        val before = rule.onNodeWithTag("placed-mug").fetchSemanticsNode().boundsInRoot
        rule.mainClock.autoAdvance = true
        rule.onNodeWithTag("placed-mug").performTouchInput { down(center); repeat(10) { moveBy(androidx.compose.ui.geometry.Offset(-12f, -10f)) }; up() }
        rule.mainClock.autoAdvance = false; settle()
        val after = rule.onNodeWithTag("placed-mug").fetchSemanticsNode().boundsInRoot
        assertTrue("dragged $before -> $after", after.left < before.left)
        tap("deco-save")
        val p = s.state.value.placements.single { it.defId == "mug" }
        assertTrue("saved x moved left: $p", p.x < 50)
        assertFalse(exists("decorate-screen"))
        assertTrue(exists("status-toast"))
        shot("save-deco-saved.png")
    }

    @Test fun decorateUndoRevertThenSave() {
        val s = openDecorate()
        tap("deco-item-mug"); tap("deco-rotate"); tap("deco-undo"); tap("deco-revert")
        tap("deco-item-mug"); tap("deco-tab-outfit"); tap("deco-item-beanie")
        tap("deco-save")
        assertTrue(s.state.value.placements.any { it.defId == "mug" })
        assertEquals("beanie", s.state.value.outfitId)
    }

    @Test fun decorateSaveEnglish() {
        val s = openDecorate("en")
        tap("deco-save"); assertTrue(exists("deco-save-hint"))
        tap("deco-item-mug"); tap("deco-save")
        assertTrue(s.state.value.placements.any { it.defId == "mug" })
    }

    @Test fun scheduleSaveWithEmptyTitleExplainsWhy() {
        val s = app()
        tap("tab-Schedule")   // empty list opens the form
        tap("save-bottom")
        shot("save-schedule-empty.png")
        assertTrue("tapping save with an empty title must show the error", exists("err-title"))
        assertTrue(exists("save-blocked"))
        assertTrue(s.state.value.schedules.isEmpty())
    }

    @Test fun scheduleSaveWithKeyboardOpenTopAndBottom() {
        val s = app()
        tap("tab-Schedule")
        rule.onNodeWithTag("field-title").performClick()
        rule.onNodeWithTag("field-title").performTextInput("운동 가기"); settle()
        tap("save-top")
        assertEquals("운동 가기", s.state.value.schedules.single().title)
        shot("save-schedule-saved.png")
    }

    @Test fun scheduleSaveEnglishBottomAndImeDone() {
        val s = app("en")
        tap("tab-Schedule")
        rule.onNodeWithTag("field-title").performClick()
        rule.onNodeWithTag("field-title").performTextInput("Gym"); settle()
        tap("save-bottom")
        assertEquals("Gym", s.state.value.schedules.single().title)
    }

    @Test fun scheduleEditSave() {
        val s = app { addSchedule("회의", System.currentTimeMillis() + 3_600_000, app.mongle.model.RepeatRule.NONE) }
        tap("tab-Schedule")
        val row = rule.onAllNodes(androidx.compose.ui.test.SemanticsMatcher("row") { n -> n.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.TestTag)?.let { it.startsWith("row-") } == true })
            .fetchSemanticsNodes().first().config[androidx.compose.ui.semantics.SemanticsProperties.TestTag]
        tap(row)
        tap("edit-" + row.removePrefix("row-"))
        // The edit form opens filled in with the schedule (it used to open blank).
        assertTrue(rule.onAllNodes(androidx.compose.ui.test.hasText("회의")).fetchSemanticsNodes().isNotEmpty())
        rule.onNodeWithTag("field-place").performTextInput("강남역"); settle()
        shot("save-schedule-edit.png")
        tap("save-bottom")
        if (exists("scope-all")) tap("scope-all")
        assertEquals("강남역", s.state.value.schedules.single().place)
        assertEquals("회의", s.state.value.schedules.single().title)
    }
}
