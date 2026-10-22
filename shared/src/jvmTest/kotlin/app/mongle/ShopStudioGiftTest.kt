package app.mongle

import app.mongle.data.MemoryGameStore
import app.mongle.data.Seed
import app.mongle.logic.Layout
import app.mongle.logic.LayoutEditor
import app.mongle.logic.Shop
import app.mongle.model.Catalog
import app.mongle.model.Placement
import app.mongle.model.RepeatRule
import app.mongle.platform.JvmNotices
import app.mongle.service.GameService
import app.mongle.service.TimeProvider
import app.mongle.service.toSnapshot
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class Clock2(var now: Long) : TimeProvider { override fun now() = now }

class ShopStudioGiftTest {
    private fun service(coins: Int): Pair<GameService, Clock2> {
        val c = Clock2(1_792_000_000_000)
        val seed = Seed.create(c.now).copy(coins = coins, onboarded = true)
        return GameService(MemoryGameStore(seed), c, TimeZone.of("Asia/Seoul")) to c
    }

    @Test fun roomsLockedUntilBought() = runBlocking {
        val (s, _) = service(800)
        val mint = s.state.value.presets.first { it.wallpaperId == "mint" }.index
        val starry = s.state.value.presets.first { it.wallpaperId == "starry" }.index
        assertFalse(s.selectPreset(mint).ok)
        assertTrue(s.buyRoom("mint").ok)
        assertEquals(100, s.state.value.coins)
        assertTrue(s.selectPreset(mint).ok)
        assertFalse(s.buyRoom("mint").ok) // double tap: no second charge
        assertEquals(100, s.state.value.coins)
        assertFalse(s.buyRoom("starry").ok) // 1000 > 100: insufficient
        assertFalse(s.selectPreset(starry).ok)
        assertEquals(700, Shop.roomPrice("mint")); assertEquals(1000, Shop.roomPrice("starry")); assertEquals(0, Shop.roomPrice("wood"))
    }

    @Test fun shopPreviewNeverMutatesRealRoomAndCartBuys() = runBlocking {
        val (s, _) = service(2000)
        val real = s.state.value.placements
        val preview = LayoutEditor(Layout(real, null, "wood"))
        preview.place("canopy_bed", 31, 62); preview.place("mug", 70, 80); preview.wear("beanie")
        // Unowned rooms can't be applied in the preview (UI shows '방은 구매한 후에 적용할 수 있어요' + buy).
        assertFalse(Shop.canApplyRoom("mint", s.state.value.owned))
        assertEquals(real, s.state.value.placements)
        assertFalse("canopy_bed" in s.state.value.owned)
        val cart = Shop.cart(preview.current, s.state.value.owned)
        assertEquals(setOf("canopy_bed", "mug", "beanie"), cart.toSet())
        assertEquals(600 + 50 + 300, Shop.total(cart))
        assertEquals(30, preview.current.placements.first { it.defId == "canopy_bed" }.x) // snapped to 5
        preview.revert()
        assertEquals(Layout(real, null, "wood"), preview.current)
        assertTrue(s.buyAll(cart).ok)
        assertEquals(2000 - 950, s.state.value.coins)
        assertFalse(s.buyAll(cart).ok) // idempotent
        assertEquals(1050, s.state.value.coins)
    }

    @Test fun insufficientCoinsBuysNothing() = runBlocking {
        val (s, _) = service(100)
        val r = s.buyAll(listOf("mug", "canopy_bed"))
        assertFalse(r.ok)
        assertEquals(100, s.state.value.coins)
        assertFalse("mug" in s.state.value.owned)
    }

    @Test fun decorateOwnedOnlyUndoRevertSave() = runBlocking {
        val (s, _) = service(1000)
        s.buyAll(listOf("mug", "beanie"))
        val owned = s.state.value.owned
        val deco = Shop.decoratable(owned).map { it.id }
        assertTrue("mug" in deco && "beanie" in deco)
        assertFalse("canopy_bed" in deco)
        assertTrue(Catalog.shop.size > deco.size)
        val ed = LayoutEditor(Layout(s.state.value.placements, null, "wood"))
        ed.place("mug", 40, 70); ed.rotate("mug"); ed.flip("mug")
        assertTrue(ed.undo()); assertFalse("mug" in ed.current.flipped)
        ed.wear("beanie"); ed.place("canopy_bed") // not owned → dropped on save
        assertTrue(s.applyLayout(ed.current).ok)
        assertTrue(s.state.value.placements.any { it.defId == "mug" && it.rotation == 90 })
        assertFalse(s.state.value.placements.any { it.defId == "canopy_bed" })
        assertFalse("canopy_bed" in s.state.value.owned)
        ed.revert(); assertFalse(ed.dirty)
    }

    @Test fun completionGiftOncePerOccurrenceWithToggle() = runBlocking {
        val (s, c) = service(0)
        s.giftChance = { true }
        JvmNotices.reset()
        s.addSchedule("물 마시기", c.now + 60_000, RepeatRule.NONE)
        val id = s.state.value.schedules.first().id
        c.now += 120_000
        assertTrue(s.completeSchedule(id).ok)
        assertTrue(s.state.value.pendingGift != null || s.state.value.pendingDropKind.isNotBlank())
        assertTrue(JvmNotices.immediate.any { it.second.contains("선물") || it.second.contains("gift") })
        // Gift waits until opened.
        c.now += 3_600_000; s.refresh()
        assertTrue(s.state.value.pendingGift != null || s.state.value.pendingDropKind.isNotBlank())
        // Toggle off: no notification, gift still granted.
        val (s2, c2) = service(0)
        s2.giftChance = { true }
        s2.updateSettings(s2.state.value.settings.copy(giftAlerts = false))
        JvmNotices.reset()
        s2.addSchedule("산책", c2.now + 60_000, RepeatRule.NONE)
        c2.now += 120_000
        s2.completeSchedule(s2.state.value.schedules.first().id)
        assertTrue(JvmNotices.immediate.none { it.second.contains("선물") || it.second.contains("gift") })
        assertTrue(s2.state.value.pendingGift != null || s2.state.value.pendingDropKind.isNotBlank())
    }

    @Test fun noSecondRollForSameOccurrence() = runBlocking {
        val (s, c) = service(0)
        var rolls = 0
        s.giftChance = { rolls++; false }
        s.addSchedule("운동", c.now + 60_000, RepeatRule.DAILY)
        val id = s.state.value.schedules.first().id
        c.now += 120_000
        s.completeSchedule(id)
        s.completeSchedule(id) // same occurrence again (blocked)
        assertEquals(1, rolls)
    }
}


class GiftBubbleTest {
    @Test fun waitingGiftShowsInWidgetBubble() = runBlocking {
        val c = Clock2(1_792_000_000_000)
        val s = GameService(MemoryGameStore(Seed.create(c.now).copy(onboarded = true)), c, TimeZone.of("Asia/Seoul"))
        s.giftChance = { true }
        s.addSchedule("물", c.now + 60_000, RepeatRule.NONE)
        c.now += 120_000
        s.completeSchedule(s.state.value.schedules.first().id)
        c.now += 60_000; s.refresh()
        assertTrue(s.state.value.toSnapshot().speech.contains("🎁"))
        assertTrue(s.state.value.speech.contains("🎁"), s.state.value.speech)
    }
}

class IapTest {
    @Test fun onlyTwoCoinPacksWithLocalizedPrices() = runBlocking {
        val ids = app.mongle.logic.StoreProducts.all.map { it.id }
        assertEquals(listOf("coins_100", "coins_1000"), ids)
        val p100 = app.mongle.logic.StoreProducts.byId("coins_100")!!
        val p1000 = app.mongle.logic.StoreProducts.byId("coins_1000")!!
        app.mongle.logic.StorePrices.clearForTests()
        assertEquals("$1.99", app.mongle.logic.StorePrices.label(p100))
        assertEquals("$16.99", app.mongle.logic.StorePrices.label(p1000))
        app.mongle.logic.StorePrices.update(mapOf("coins_100" to "₩2,900"))
        assertEquals("₩2,900", app.mongle.logic.StorePrices.label(p100))
        app.mongle.logic.StorePrices.clearForTests()
        val c = Clock2(1_792_000_000_000)
        val s = GameService(MemoryGameStore(Seed.create(c.now).copy(coins = 0, onboarded = true)), c, TimeZone.of("Asia/Seoul"))
        assertTrue(s.purchaseProduct("coins_100").ok)
        assertEquals(100, s.state.value.coins)
        assertTrue(s.purchaseProduct("coins_1000").ok)
        assertEquals(1100, s.state.value.coins)
        assertFalse(s.purchaseProduct("dingrow_coins_small").ok) // old products are gone
    }

    @Test fun consumeAfterGrantPendingAndRestoreAreIdempotent() = runBlocking {
        val c = Clock2(1_792_000_000_000)
        val fake = app.mongle.logic.FakeBilling()
        val s = GameService(MemoryGameStore(Seed.create(c.now).copy(coins = 0, onboarded = true)), c, TimeZone.of("Asia/Seoul"), billing = fake)
        assertTrue(s.purchaseProduct("coins_100").ok)
        assertEquals(1, fake.finished.size)              // consumed after the grant
        // A purchase that was pending and completed later (or the app died before consuming) is claimed at startup.
        fake.held += app.mongle.logic.StoreReceipt("coins_1000", "tok-late", true, "")
        assertEquals(1, s.claimPendingPurchases())
        assertEquals(1100, s.state.value.coins)
        assertTrue("tok-late" in fake.finished)
        // Running again (or restore) never double-grants.
        assertEquals(0, s.claimPendingPurchases())
        s.restorePurchases()
        assertEquals(1100, s.state.value.coins)
        // A failed / pending receipt grants nothing.
        assertEquals(0, GameService(MemoryGameStore(Seed.create(c.now).copy(coins = 0, onboarded = true)), c, TimeZone.of("Asia/Seoul"),
            billing = object : app.mongle.logic.BillingGateway {
                override suspend fun purchase(productId: String) = app.mongle.logic.StoreReceipt(productId, "", false, "pending")
                override suspend fun restore() = emptyList<app.mongle.logic.StoreReceipt>()
            }).let { it.purchaseProduct("coins_100"); it.state.value.coins })
    }
}
