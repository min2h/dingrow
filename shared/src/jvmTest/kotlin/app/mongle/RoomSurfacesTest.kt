package app.mongle

import app.mongle.logic.FurnitureScale
import app.mongle.logic.LayoutEditor
import app.mongle.logic.RoomFloor
import app.mongle.logic.RoomSurfaces
import app.mongle.logic.layoutOf
import app.mongle.model.Placement
import app.mongle.model.roomTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RoomSurfacesTest {
    private fun ed(theme: String = "wood") = LayoutEditor(layoutOf(emptyList(), null, theme))

    @Test fun oneWallpaperAndOneFloorReplaceEachOther() {
        val e = ed()
        listOf("wall_stripe", "floor_herringbone", "wall_starry", "floor_mint_tile").forEach { e.placeFree(it) }
        assertEquals("wall_starry", RoomSurfaces.wall(e.current.placements))
        assertEquals("floor_mint_tile", RoomSurfaces.floor(e.current.placements))
        assertEquals(2, e.current.placements.count { RoomSurfaces.isSurface(it.defId) })
    }

    @Test fun surfacesAreNotFurnitureNorMovable() {
        val e = ed()
        e.placeFree("wood_bed"); e.placeFree("wall_stripe")
        val before = e.current.placements.first { it.defId == "wall_stripe" }
        e.move("wall_stripe", 10, 80)
        assertEquals(before, e.current.placements.first { it.defId == "wall_stripe" })
        assertEquals(listOf("wood_bed"), RoomSurfaces.furniture(e.current.placements).map { it.defId })
        assertTrue(RoomFloor.drawOrder(e.current.placements).none { RoomSurfaces.isSurface(it.defId) })
        val bed = e.current.placements.first { it.defId == "wood_bed" }
        assertFalse(RoomFloor.collides("wood_bed", bed.x, bed.y, listOf(Placement("floor_herringbone", bed.x, bed.y, 0))))
    }

    @Test fun surfacesAreNotThemes() {
        RoomSurfaces.WALLS.plus(RoomSurfaces.FLOORS).forEach { assertEquals("wood", roomTheme(it)) }
        assertEquals("mint", roomTheme("wall_mint")); assertEquals("starry", roomTheme("wall_night"))
    }

    @Test fun normalizeKeepsLastOfEachKind() {
        val p = listOf(Placement("wall_stripe", 50, 50, 0), Placement("wall_starry", 50, 50, 0), Placement("bookshelf", 20, 60, 0))
        assertEquals(listOf("wall_starry", "bookshelf"), RoomSurfaces.normalize(p).map { it.defId })
    }

    @Test fun wallItemsHangOnTheWall() {
        val wallIds = listOf("window", "picture_frame", "wall_clock", "sun_poster", "ribbon_garland", "string_lights", "moon_mirror")
        wallIds.forEach { assertTrue(FurnitureScale.spec(it).wall, it) }
        listOf("wood", "mint", "starry").forEach { th ->
            val e = ed(th); wallIds.forEach { e.placeFree(it) }
            e.current.placements.forEach { assertTrue(it.y in RoomFloor.WALL_Y, "$th ${it.defId} y=${it.y}") }
            e.move("picture_frame", 30, 90)
            assertTrue(e.current.placements.first { it.defId == "picture_frame" }.y in RoomFloor.WALL_Y)
        }
        assertTrue(RoomFloor.hang(Placement("wall_clock", 40, 80, 0)).y in RoomFloor.WALL_Y)
    }
}
