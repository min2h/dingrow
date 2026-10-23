package app.mongle

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextClearance
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
        assertTrue(rule.onAllNodesWithTag("shop-buy-cart").fetchSemanticsNodes().isEmpty()) // one row: buy button turns into "Need N more"
        rule.onNodeWithText("코인 더 필요해요", substring = true, useUnmergedTree = true).fetchSemanticsNode()
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
        clickNoScroll("deco-save")
        tag("deco-save-hint").fetchSemanticsNode()   // blocked save explains why
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

    @Test fun levelUpPopupOnceAndDexSilhouettes() {
        val first = app.mongle.model.Catalog.uniques.first().id
        val second = app.mongle.model.Catalog.uniques[1].id
        val s = app(coins = 0) { debugGrantItem(first); debugSetStage(app.mongle.model.Stage.BABY) }
        tag("levelup").fetchSemanticsNode()
        rule.onNodeWithText("선물이 더 좋아져요!").fetchSemanticsNode()
        rule.onNodeWithText("레벨 2이 되었어요!").fetchSemanticsNode()
        rule.onRoot().captureRoboImage(artifact("levelup.png"))
        clickNoScroll("levelup-ok")
        assertTrue(rule.onAllNodesWithTag("levelup").fetchSemanticsNodes().isEmpty())
        assertEquals(2, s.state.value.levelSeen)
        rule.onNodeWithTag("tab-My").performClick(); settle()
        rule.mainClock.autoAdvance = true; rule.onNodeWithTag("dex-locked-$second").performScrollTo(); rule.mainClock.autoAdvance = false; settle()
        rule.onRoot().captureRoboImage(artifact("dex-silhouettes.png"))
        rule.onNodeWithTag("dex-owned-$first").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick); settle()
        rule.onNodeWithText("판매하기").fetchSemanticsNode() // resell confirm sheet
    }

    @Test fun scheduleFormInlineErrorsDisableSave() {
        app(coins = 0)
        rule.onNodeWithTag("tab-Schedule").performClick(); settle()   // empty list opens the form
        clickNoScroll("save-bottom"); tag("save-blocked").fetchSemanticsNode(); tag("err-title").fetchSemanticsNode()
        rule.onNodeWithTag("field-title").performTextInput("가".repeat(41)); settle()
        tag("err-title").fetchSemanticsNode()
        rule.onNodeWithTag("field-title").performTextClearance(); settle()
        tag("err-title").fetchSemanticsNode()  // required
        rule.onNodeWithTag("field-title").performTextInput("운동"); settle()
        assertTrue(rule.onAllNodesWithTag("save-blocked", useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        rule.onNodeWithTag("field-memo").performTextInput("x".repeat(201)); settle()
        tag("err-memo").fetchSemanticsNode()
        tag("save-blocked").fetchSemanticsNode()
        rule.onRoot().captureRoboImage(artifact("form-errors.png"))
    }

    @Test fun shopPreviewStaysPinnedAndLastItemReachable() {
        app("en", coins = 5000)
        click("open-shop")
        click("try-beanie")   // cart bar visible
        val previewTop = tag("shop-preview").fetchSemanticsNode().boundsInRoot.top
        val last = app.mongle.model.Catalog.shop.last().id
        rule.mainClock.autoAdvance = true; tag("shop-list-end").performScrollTo(); rule.mainClock.autoAdvance = false; settle()
        val preview = tag("shop-preview").fetchSemanticsNode().boundsInRoot
        assertEquals(previewTop, preview.top, 0.5f)
        assertTrue(preview.height > 100f)
        rule.onRoot().captureRoboImage(artifact("shop-scrolled-end.png"))
        // The last card's button sits fully above the cart bar.
        val cartTop = tag("shop-cart").fetchSemanticsNode().boundsInRoot.top
        val btn = tag("try-$last").fetchSemanticsNode().boundsInRoot
        assertTrue("last button bottom ${btn.bottom} must be above cart $cartTop", btn.bottom <= cartTop + 0.5f)
        rule.onNodeWithTag("try-$last").performClick(); settle()   // real touch at its center
        tag("pet-outfit-beanie").fetchSemanticsNode()
    }

    @Test fun everyOutfitRendersWornInPinnedPreview() {
        app("en", coins = 0) { debugSetStage(app.mongle.model.Stage.BABY) }
        rule.onAllNodesWithTag("levelup-ok").fetchSemanticsNodes().takeIf { it.isNotEmpty() }?.let { clickNoScroll("levelup-ok") }
        click("open-shop")
        click("shop-tab-outfit")
        val outfits = app.mongle.model.Catalog.shop.filter { it.kind == app.mongle.model.ItemKind.OUTFIT }
        val all = app.mongle.model.Catalog.all.filter { it.kind == app.mongle.model.ItemKind.OUTFIT }
        all.forEach { assertTrue("anchor for ${it.id}", app.mongle.ui.OutfitAnchors.known(it.id)); assertTrue("art for ${it.id}", app.mongle.ui.Art.known(it.id)) }
        outfits.forEach { o ->
            click("try-${o.id}")
            tag("pet-outfit-${o.id}").fetchSemanticsNode()
            if (o.id == "ribbon" || o.id == "beret") tag("shop-pinned").captureRoboImage(artifact("tryon-${o.id}.png"))
        }
    }

    @Test fun shopPinnedPreviewDarkEn() {
        app("en", coins = 5000) { updateSettings(state.value.settings.copy(theme = "dark")) }
        click("open-shop")
        rule.onRoot().captureRoboImage(artifact("shop-dark-en-before.png"))
        click("try-ribbon")
        tag("pet-outfit-ribbon").fetchSemanticsNode()
        rule.onRoot().captureRoboImage(artifact("shop-dark-en-after.png"))
    }

    @Test @Config(qualifiers = "ko-rKR-w320dp-h568dp-mdpi") fun shopPinnedPreviewSmallScreen() {
        app("ko", coins = 5000)
        click("open-shop")
        click("try-beret")
        tag("pet-outfit-beret").fetchSemanticsNode()
        rule.onRoot().captureRoboImage(artifact("shop-small-ko.png"))
        rule.mainClock.autoAdvance = true; tag("shop-list-end").performScrollTo(); rule.mainClock.autoAdvance = false; settle()
        assertTrue(tag("shop-preview").fetchSemanticsNode().boundsInRoot.height > 80f)
        rule.onRoot().captureRoboImage(artifact("shop-small-ko-end.png"))
    }

    @Test @Config(qualifiers = "en-w411dp-h914dp-xxhdpi") fun shopShowsTwoFullRowsOn1080x2400() {
        app("en", coins = 5000)
        click("open-shop")
        click("try-beanie")
        val list = tag("shop-list").fetchSemanticsNode().boundsInRoot
        val cartTop = tag("shop-cart").fetchSemanticsNode().boundsInRoot.top
        assertTrue("visible list height ${cartTop - list.top}", cartTop - list.top > 2 * 245 * 2.625f * 0.9f)
        rule.onRoot().captureRoboImage(artifact("shop-1080x2400.png"))
        rule.mainClock.autoAdvance = true; tag("shop-list-end").performScrollTo(); rule.mainClock.autoAdvance = false; settle()
        rule.onRoot().captureRoboImage(artifact("shop-1080x2400-end.png"))
    }

    @Test @Config(qualifiers = "en-w360dp-h740dp-hdpi") fun decoratePinnedEn() {
        app("en", coins = 1000) { buyAll(listOf("mug", "beanie")) }
        click("open-decorate")
        rule.onRoot().captureRoboImage(artifact("decorate-en.png"))
    }

    @Test fun shopPreviewShowsFurnitureDraggableWithMultiSlotOutfit() {
        val s = app(coins = 0)
        click("open-shop")
        listOf("wood_bed", "bookshelf", "round_table", "raincoat", "bow_tie", "sunglasses", "crown").forEach { click("try-$it") }
        listOf("wood_bed", "bookshelf", "round_table").forEach { tag("placed-$it").assertIsDisplayed() }
        listOf("raincoat", "bow_tie", "sunglasses", "crown").forEach { tag("pet-outfit-$it").fetchSemanticsNode() }
        val f = java.io.File("/workspace/dingrow-combos").apply { mkdirs() }
        tag("shop-pinned").captureRoboImage(java.io.File(f, "shop-preview-placed.png").path)
        rule.mainClock.autoAdvance = true
        rule.onNodeWithTag("placed-round_table", useUnmergedTree = true).performTouchInput { down(center); repeat(10) { advanceEventTime(16); moveBy(androidx.compose.ui.geometry.Offset(-8f, 6f)) }; up() }
        rule.waitForIdle(); rule.mainClock.autoAdvance = false
        settle()
        tag("shop-pinned").captureRoboImage(java.io.File(f, "shop-preview-dragged.png").path)
        assertTrue(s.state.value.placements.none { it.defId == "round_table" }) // real room untouched
        // Tap the fully dressed 몽이 through the worn layers and furniture overlay: a live reaction plays.
        listOf("rain_boots", "tiny_backpack", "knit_scarf").forEach { click("try-$it") }
        rule.onNodeWithTag("room-pet").performClick()
        repeat(12) { i ->
            rule.mainClock.advanceTimeBy(80); rule.waitForIdle()
            tag("shop-preview").captureRoboImage(java.io.File(f, "frames/tap-%02d.png".format(i)).path)
        }
        assertEquals("heart#2", app.mongle.ui.RoomTap.reaction(2))
        rule.mainClock.advanceTimeBy(2000); rule.waitForIdle()
    }
}
