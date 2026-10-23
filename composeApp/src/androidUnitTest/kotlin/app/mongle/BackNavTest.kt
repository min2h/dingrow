package app.mongle

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
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

/** System back from every screen: sheets/dialogs first, then the screen, tabs → Home, only Home root exits. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "ko-rKR-w360dp-h740dp-hdpi")
class BackNavTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private fun settle() = repeat(4) { rule.mainClock.advanceTimeBy(250); rule.waitForIdle() }

    private fun app(coins: Int = 0, block: suspend GameService.() -> Unit = {}): GameService {
        primeComposeResources()
        app.mongle.ui.LocationBridge.request = null
        AndroidHooks.sync = {}; AndroidHooks.notifyNow = { _, _ -> }; AndroidHooks.reloadWidgets = {}
        val s = GameService(MemoryGameStore(), MutableClock(System.currentTimeMillis()), TimeZone.of("Asia/Seoul")).also { it.giftChance = { false } }
        runBlocking { s.completeOnboarding("몽이"); s.debugAddCoins(coins - s.state.value.coins); s.block() }
        rule.mainClock.autoAdvance = false
        rule.setContent { CompositionLocalProvider(LocalPetMotion provides (0.35f to 0.2f)) { MongleTheme { MongleAppScreen(s, PermissionSnapshot(true, true, true, true), {}, {}, {}) } } }
        settle()
        return s
    }

    private fun back() { rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }; settle() }
    private fun shown(t: String) = rule.onAllNodesWithTag(t, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun click(t: String) {
        val n = rule.onNodeWithTag(t, useUnmergedTree = true)
        rule.mainClock.autoAdvance = true; runCatching { n.performScrollTo() }; rule.mainClock.autoAdvance = false
        n.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick); settle()
    }
    private fun tab(name: String) { rule.onNodeWithTag("tab-$name").performClick(); settle() }
    private fun onHome() = assertTrue("expected Home tab", shown("tab-Home") && !shown("shop-screen") && !shown("decorate-screen") && !shown("open-settings"))

    @Test fun tabsReturnHomeAndOnlyHomeRootExitsOnSecondPress() {
        app()
        for (t in listOf("Schedule", "Room", "My")) {
            tab(t); if (shown("overlay-back")) back()   // empty schedule tab auto-opens the form
            back(); onHome(); assertFalse(rule.activity.isFinishing)
        }
        back()
        assertTrue(shown("exit-hint")); assertFalse(rule.activity.isFinishing)
        rule.onRoot().captureRoboImage(artifact("back-exit-hint.png"))
        back()
        assertTrue(rule.activity.isFinishing)
    }

    @Test fun shopBackLeavesWhenCleanAndAsksOnceWithCart() {
        app(coins = 5000)
        tab("Room"); click("open-shop")
        back(); assertFalse(shown("shop-screen")); assertTrue(shown("open-shop"))
        click("open-shop"); click("try-beanie")
        back(); assertTrue(shown("discard-dialog")); assertTrue(shown("shop-screen"))
        rule.onRoot().captureRoboImage(artifact("back-discard-shop.png"))
        click("discard-no"); assertTrue(shown("shop-screen")); assertFalse(shown("discard-dialog"))
        click("shop-back"); assertTrue(shown("discard-dialog"))   // the ✕/‹ button asks the same way
        click("discard-yes"); assertFalse(shown("shop-screen"))
        back(); onHome()
    }

    @Test fun decorateAsksBeforeDroppingEdits() {
        app(coins = 1000) { buyAll(listOf("mug")) }
        tab("Room"); click("open-decorate")
        back(); assertFalse(shown("decorate-screen"))
        click("open-decorate"); click("deco-item-mug")
        back(); assertTrue(shown("discard-dialog"))
        click("discard-yes"); assertFalse(shown("decorate-screen"))
    }

    @Test fun scheduleFormBackAndDiscardPrompt() {
        app()
        tab("Schedule"); assertTrue(shown("field-title"))
        back(); assertFalse(shown("field-title"))
        rule.onNodeWithTag("tab-Home").performClick(); settle()
        app.mongle.platform.NavigationBus.request("add-schedule"); settle()
        rule.onNodeWithTag("field-title").performTextInput("산책"); settle()
        back(); assertTrue(shown("discard-dialog"))
        click("discard-no"); assertTrue(shown("field-title"))
        back(); click("discard-yes"); assertFalse(shown("field-title"))
        back(); onHome()
    }

    @Test fun settingsSubpageGoesToSettingsThenScreenThenHome() {
        app()
        tab("Room"); click("open-settings"); click("row-alerts")
        assertFalse(shown("row-alerts"))
        back(); assertTrue(shown("row-alerts"))
        back(); assertFalse(shown("row-alerts")); assertTrue(shown("open-settings"))
        back(); onHome()
    }
}
