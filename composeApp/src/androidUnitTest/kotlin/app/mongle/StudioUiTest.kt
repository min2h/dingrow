package app.mongle

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "ko-rKR-w360dp-h740dp-hdpi")
class StudioUiTest {
    @get:Rule val rule = createComposeRule()
    private fun settle() = repeat(4) { rule.mainClock.advanceTimeBy(250); rule.waitForIdle() }

    private fun app(lang: String = "ko", coins: Int = 0, block: suspend GameService.() -> Unit = {}): GameService {
        primeComposeResources()
        app.mongle.ui.LocationBridge.request = null
        AndroidHooks.sync = {}; AndroidHooks.notifyNow = { _, _ -> }; AndroidHooks.reloadWidgets = {}
        val s = GameService(MemoryGameStore(), MutableClock(System.currentTimeMillis()), TimeZone.of("Asia/Seoul")).also { it.giftChance = { false } }
        runBlocking { s.completeOnboarding("몽이"); s.updateSettings(s.state.value.settings.copy(language = lang)); s.debugAddCoins(coins - s.state.value.coins); s.block() }
        rule.mainClock.autoAdvance = false
        rule.setContent { CompositionLocalProvider(LocalPetMotion provides (0.35f to 0.2f)) { MongleTheme { MongleAppScreen(s, PermissionSnapshot(true, true, true, true), {}, {}, {}) } } }
        settle()
        rule.onNodeWithTag("tab-Room").performClick(); settle()
        return s
    }

    private fun tag(t: String) = rule.onNodeWithTag(t, useUnmergedTree = true)
    private fun click(t: String) { rule.mainClock.autoAdvance = true; rule.onNodeWithTag(t).performScrollTo(); rule.mainClock.autoAdvance = false; rule.onNodeWithTag(t).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick); settle() }
    private fun clickNoScroll(t: String) { rule.onNodeWithTag(t).performClick(); settle() }

    @Test fun shopListsAllWithOwnedBadgeAndPreviewDoesNotTouchRoom() {
        val s = app(coins = 2000) { buyAll(listOf("mug")) }
        click("open-shop")
        rule.onRoot().captureRoboImage(artifact("shop-studio.png"))
        // Owned → badge + 배치하기; unowned → try.
        rule.mainClock.autoAdvance = true; tag("owned-badge-mug").performScrollTo(); rule.mainClock.autoAdvance = false
        tag("use-mug").fetchSemanticsNode()
        tag("try-canopy_bed").fetchSemanticsNode()
        val roomBefore = s.state.value.placements
        click("try-canopy_bed"); click("try-beanie")
        assertEquals(roomBefore, s.state.value.placements)
        assertFalse("canopy_bed" in s.state.value.owned)
        assertNull(s.state.value.outfitId)
        rule.onNodeWithText("2개 사기 · 900코인").fetchSemanticsNode()
        rule.onRoot().captureRoboImage(artifact("shop-preview-cart.png"))
        // 원래대로 resets the preview.
        clickNoScroll("shop-revert")
        assertTrue(rule.onAllNodesWithTag("shop-buy-cart").fetchSemanticsNodes().isEmpty())
        // Purchase from preview: confirm → bought once.
        click("try-canopy_bed")
        clickNoScroll("shop-buy-cart")
        clickNoScroll("shop-confirm-yes")
        assertTrue("canopy_bed" in s.state.value.owned)
        assertEquals(2000 - 50 - 600, s.state.value.coins)
        clickNoScroll("shop-apply")
        rule.mainClock.autoAdvance = true
        rule.waitUntil(5_000) { s.state.value.placements.any { it.defId == "canopy_bed" } }
        rule.mainClock.autoAdvance = false
        assertTrue(s.state.value.placements.any { it.defId == "canopy_bed" })
        // Hide owned filter.
        click("shop-hide-owned")
        rule.onRoot().captureRoboImage(artifact("shop-hide-owned.png"))
        java.io.File("/tmp/sem.txt").writeText(rule.onRoot(useUnmergedTree = true).printToString())
        rule.mainClock.autoAdvance = true
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("shop-item-mug").fetchSemanticsNodes().isEmpty() }
        rule.mainClock.autoAdvance = false
        tag("shop-item-canopy_bed").assertDoesNotExist()
        tag("shop-item-cookie").fetchSemanticsNode() // consumables stay
    }

    @Test fun insufficientCoinsShowsShortAndEarnPath() {
        val s = app(coins = 100)
        click("open-shop")
        click("try-canopy_bed")
        tag("shop-short").fetchSemanticsNode()
        rule.onNodeWithTag("shop-buy-cart").assertIsNotEnabled()
        clickNoScroll("shop-earn")
        assertEquals(100, s.state.value.coins)
        assertTrue(rule.onAllNodesWithTag("shop-screen").fetchSemanticsNodes().isEmpty()) // left the shop to earn coins
    }

    @Test fun unownedRoomCannotBeAppliedInPreviewOnlyBought() {
        val s = app("en", coins = 800)
        click("open-shop")
        click("shop-room-mint")
        rule.onNodeWithText("Rooms can be applied after purchase.").fetchSemanticsNode()
        rule.onRoot().captureRoboImage(artifact("en-shop-room-locked.png"))
        clickNoScroll("room-buy-cancel")
        assertFalse("room_mint" in s.state.value.owned)
        click("shop-room-mint")
        clickNoScroll("room-buy-yes")
        assertTrue("room_mint" in s.state.value.owned)
        assertEquals(100, s.state.value.coins)
        click("shop-room-starry")
        tag("room-short").fetchSemanticsNode() // 1000 > 100
    }

    @Test fun foodShowsCountAndMedicineOnlyWhenSick() {
        val s = app(coins = 300)
        click("open-shop")
        val cookie = s.state.value.foods.first { it.defId == "cookie" }.quantity
        assertTrue(cookie > 0)
        rule.onNodeWithText("보유 ${cookie}개", substring = true, useUnmergedTree = true).fetchSemanticsNode()
        rule.onNodeWithTag("shop-medicine-use").assertIsNotEnabled()
    }

    @Test fun decorateShowsOwnedOnlyAndRevertSave() {
        val s = app(coins = 1000) { buyAll(listOf("mug", "beanie")) }
        click("open-decorate")
        rule.onRoot().captureRoboImage(artifact("decorate.png"))
        tag("deco-item-mug").fetchSemanticsNode()
        assertTrue(rule.onAllNodesWithTag("deco-item-canopy_bed").fetchSemanticsNodes().isEmpty())
        rule.onNodeWithText("600").let { } // no prices: nothing to assert beyond absence of buy tags
        assertTrue(rule.onAllNodesWithTag("try-mug").fetchSemanticsNodes().isEmpty())
        click("deco-item-mug")
        clickNoScroll("deco-rotate"); clickNoScroll("deco-flip")
        clickNoScroll("deco-undo")
        clickNoScroll("deco-revert")
        rule.onNodeWithTag("deco-save").assertIsNotEnabled()
        click("deco-item-mug"); clickNoScroll("deco-rotate")
        clickNoScroll("deco-save")
        assertTrue(s.state.value.placements.any { it.defId == "mug" && it.rotation % 1000 == 90 })
    }

    @Test fun giftBoxWaitsThenOpensByTapOrNotificationRoute() {
        val s = app(coins = 0) {
            giftChance = { true }
            addSchedule("물 마시기", nowMillis() - 60_000, app.mongle.model.RepeatRule.NONE)
            completeSchedule(state.value.schedules.first().id)
        }
        rule.onNodeWithTag("tab-Home").performClick(); settle()
        tag("gift-carrier").fetchSemanticsNode()
        rule.onRoot().captureRoboImage(artifact("gift-carrier.png"))
        clickNoScroll("gift-carrier")
        tag("gift-opened").fetchSemanticsNode()
        rule.onRoot().captureRoboImage(artifact("gift-opened.png"))
        // The gift itself still waits in the service until kept/sold.
        assertTrue(s.state.value.pendingGift != null || s.state.value.pendingDropKind.isNotBlank())
        // Notification route opens the next gift directly.
        app.mongle.ui.GiftOpen.opened = null
        settle()
        tag("gift-carrier").fetchSemanticsNode()
        app.mongle.platform.NavigationBus.request("gift"); settle()
        tag("gift-opened").fetchSemanticsNode()
    }
}
