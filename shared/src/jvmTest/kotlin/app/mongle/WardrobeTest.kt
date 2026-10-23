package app.mongle

import app.mongle.data.MemoryGameStore
import app.mongle.data.Seed
import app.mongle.logic.Layout
import app.mongle.logic.LayoutEditor
import app.mongle.logic.OutfitClimate
import app.mongle.logic.Shop
import app.mongle.logic.Wardrobe
import app.mongle.logic.WearSlot
import app.mongle.model.Catalog
import app.mongle.model.ItemKind
import app.mongle.model.LegacyIds
import app.mongle.service.GameService
import app.mongle.service.TimeProvider
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WardrobeTest {
    private val t0 = 1_792_000_000_000L
    private fun service(owned: Set<String> = emptySet()): GameService {
        val seed = Seed.create(t0).let { it.copy(coins = 5000, onboarded = true, owned = it.owned + owned) }
        return GameService(MemoryGameStore(seed), object : TimeProvider { override fun now() = t0 }, TimeZone.of("Asia/Seoul"))
    }

    @Test fun everyOutfitHasASlot() {
        Catalog.all.filter { it.kind == ItemKind.OUTFIT }.forEach { assertTrue(Wardrobe.slotOf(it.id) != null, it.id) }
        assertEquals(WearSlot.FACE, Wardrobe.slotOf("sunglasses"))
        assertEquals(WearSlot.BODY, Wardrobe.slotOf("raincoat"))
    }

    @Test fun differentSlotsStack() {
        var w: String? = null
        listOf("crown", "raincoat", "sunglasses", "knit_scarf", "rain_boots", "tiny_backpack").forEach { w = Wardrobe.toggle(w, it) }
        assertEquals(listOf("rain_boots", "raincoat", "tiny_backpack", "knit_scarf", "sunglasses", "crown"), Wardrobe.decode(w))
    }

    @Test fun sameSlotReplacesAndRetapRemoves() {
        var w = Wardrobe.toggle(null, "hoodie")
        w = Wardrobe.toggle(w, "sunglasses")
        w = Wardrobe.toggle(w, "raincoat")
        assertEquals(listOf("raincoat", "sunglasses"), Wardrobe.decode(w))
        w = Wardrobe.toggle(w, "raincoat")
        assertEquals(listOf("sunglasses"), Wardrobe.decode(w))
        assertNull(Wardrobe.toggle(w, "sunglasses"))
    }

    @Test fun hoodDropsWhenAHeadItemIsWorn() {
        assertTrue(Wardrobe.hoodUp("raincoat"))
        assertTrue(Wardrobe.hoodUp("raincoat,sunglasses"))
        assertFalse(Wardrobe.hoodUp("raincoat,crown"))
        assertEquals(listOf("hoodie", "beanie"), Wardrobe.decode(Wardrobe.toggle("hoodie", "beanie")))
    }

    @Test fun migrationFromOldSingleOutfitField() {
        val seed = Seed.create(t0)
        val old = seed.copy(pet = seed.pet.copy(outfitId = "raincoat"))
        assertEquals(listOf("raincoat"), Wardrobe.decode(LegacyIds.migrate(old).pet.outfitId))
        assertEquals("cardigan,crown", Wardrobe.normalize("crown,ghost_item,hoodie,cardigan"))
        val multi = seed.copy(pet = seed.pet.copy(outfitId = "crown,knit_scarf,hoodie"))
        assertEquals("hoodie,knit_scarf,crown", LegacyIds.migrate(multi).pet.outfitId)
    }

    @Test fun equipStacksAndSavesThroughService() = runBlocking {
        val s = service(setOf("raincoat", "sunglasses", "crown", "hoodie"))
        assertTrue(s.equipOutfit("raincoat").ok)
        assertTrue(s.equipOutfit("sunglasses").ok)
        assertTrue(s.equipOutfit("crown").ok)
        assertEquals("raincoat,sunglasses,crown", s.state.value.outfitId)
        s.equipOutfit("hoodie")
        assertEquals("hoodie,sunglasses,crown", s.state.value.outfitId)
        s.equipOutfit("crown")
        assertEquals("hoodie,sunglasses", s.state.value.outfitId)
        s.equipOutfit(null)
        assertNull(s.state.value.outfitId)
        assertFalse(s.equipOutfit("beanie").ok)
    }

    @Test fun decorateSavesOnlyOwnedWornItems() = runBlocking {
        val s = service(setOf("raincoat", "sunglasses"))
        val ed = LayoutEditor(Layout(s.state.value.placements, null, "wood"))
        ed.toggleWear("raincoat"); ed.toggleWear("sunglasses"); ed.toggleWear("crown")
        s.applyLayout(ed.current)
        assertEquals("raincoat,sunglasses", s.state.value.outfitId)
    }

    @Test fun shopPreviewCartHoldsEveryTriedItem() {
        val ed = LayoutEditor(Layout(emptyList(), null, "wood"))
        listOf("raincoat", "sunglasses", "crown", "knit_scarf").forEach { ed.toggleWear(it) }
        val cart = Shop.cart(ed.current, emptySet())
        assertEquals(setOf("raincoat", "sunglasses", "crown", "knit_scarf"), cart.toSet())
        assertEquals(cart.sumOf { Catalog.byId(it)!!.price }, Shop.total(cart))
        assertTrue(Shop.total(cart) > Shop.total(listOf("raincoat")))
        ed.toggleWear("hoodie")
        assertEquals(setOf("hoodie", "sunglasses", "crown", "knit_scarf"), Shop.cart(ed.current, emptySet()).toSet())
        assertEquals(setOf("sunglasses", "crown", "knit_scarf"), Shop.cart(ed.current, setOf("hoodie")).toSet())
        ed.toggleWear("crown")
        assertEquals(setOf("hoodie", "sunglasses", "knit_scarf"), Shop.cart(ed.current, emptySet()).toSet())
    }

    @Test fun climateCountsAllWornItems() {
        assertTrue(OutfitClimate.of("hoodie,knit_scarf") == 3)
        assertTrue(OutfitClimate.rainproof("rain_boots,crown"))
        assertFalse(OutfitClimate.rainproof("crown"))
    }
}

class RoomFloorTest {
    @Test fun defaultSpotsAvoidPetAndEachOther() {
        val ed = app.mongle.logic.LayoutEditor(app.mongle.logic.Layout(emptyList(), null, "wood"))
        listOf("wood_bed", "bookshelf", "round_table").forEach { ed.placeFree(it) }
        val ps = ed.current.placements
        assertEquals(3, ps.size)
        ps.forEach { assertFalse(app.mongle.logic.RoomFloor.overlapsPet(it.x, it.y), it.toString()) }
        assertEquals(3, ps.map { it.x to it.y }.toSet().size)
    }
    @Test fun dragIsClampedToFloor() {
        val ed = app.mongle.logic.LayoutEditor(app.mongle.logic.Layout(emptyList(), null, "wood"))
        ed.placeFree("canopy_bed")
        ed.move("canopy_bed", -40, 160)
        val p = ed.current.placements.single()
        assertEquals(10, p.x); assertEquals(90, p.y) // 92 snapped to grid 5 → 90
        ed.move("canopy_bed", 120, 0)
        assertEquals(90 to 45, ed.current.placements.single().let { it.x to it.y })
    }
    @Test fun depthOrderLowerIsInFront() {
        val ps = listOf(app.mongle.model.Placement("a", 20, 80, 0), app.mongle.model.Placement("b", 50, 50, 0), app.mongle.model.Placement("c", 70, 65, 0))
        assertEquals(listOf("b", "c", "a"), app.mongle.logic.RoomFloor.drawOrder(ps).map { it.defId })
    }
}
