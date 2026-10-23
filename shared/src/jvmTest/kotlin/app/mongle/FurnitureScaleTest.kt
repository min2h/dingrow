package app.mongle

import app.mongle.logic.FurnitureScale
import app.mongle.logic.RoomFloor
import app.mongle.model.Placement
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class FurnitureScaleTest {
    private val pet = FurnitureScale.PET_ART_W
    private fun visW(id: String) = FurnitureScale.boxFactor(id) * FurnitureScale.spec(id).artW
    private fun visH(id: String) = FurnitureScale.boxFactor(id) * FurnitureScale.spec(id).artH
    private val petH = FurnitureScale.PET_ART_H

    @Test fun sizesAreRealisticRelativeToPet() {
        assertTrue(visW("wood_bed") / pet in 1.6f..2.0f)
        assertTrue(visW("cushion_bed") / pet in 1.6f..2.0f)
        assertTrue(visH("bookshelf") > petH * 1.2f)
        assertTrue(visH("round_table") / petH in 0.85f..1.15f)
        assertTrue(visH("floor_lamp") / petH in 1.05f..1.4f)
        assertTrue(visW("mint_rug") > pet * 2f)
    }

    @Test fun everyCatalogFurnitureHasASize() {
        val ids = (app.mongle.model.Catalog.all).filter { it.kind in setOf(app.mongle.model.ItemKind.BED, app.mongle.model.ItemKind.RUG, app.mongle.model.ItemKind.LAMP, app.mongle.model.ItemKind.DECOR) }.map { it.id }
        assertTrue(ids.isNotEmpty())
        ids.forEach { assertTrue(it in FurnitureScale.table, "missing size for $it") }
    }

    @Test fun backOfRoomIsSmaller() {
        assertTrue(FurnitureScale.perspective(RoomFloor.Y.first) < FurnitureScale.perspective(80))
    }

    @Test fun placementAvoidsOverlap() {
        val bed = Placement("wood_bed", 22, 66)
        assertTrue(RoomFloor.collides("bookshelf", 30, 66, listOf(bed)))
        val (x, y) = RoomFloor.defaultSpot(listOf(bed), "bookshelf")
        assertFalse(RoomFloor.collides("bookshelf", x, y, listOf(bed)))
    }
}
