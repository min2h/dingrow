package app.mongle.logic

import app.mongle.model.Catalog
import app.mongle.model.ItemKind
import app.mongle.model.Placement

/**
 * Wallpaper (WALL) and floor (FLOOR) items are SURFACES, not furniture:
 * - at most one of each per layout; choosing another replaces it,
 * - never dragged, never collide, never drawn as a pasted square,
 * - rendered as a perspective-warped tiled texture on the room art's back walls / floor,
 *   with the art's lighting and fixtures (window, shelf, built-in rug, string lights) kept on top.
 *
 * They are stored as ordinary placements (so saving, the shop cart and ownership stay unchanged)
 * at a fixed dummy position.
 *
 * Themes vs surfaces: a room theme (wood / Mint / Night Sky) is the room itself — its shape, window,
 * built-in decor and lighting. A surface item re-papers that room: it replaces the theme's wall or
 * floor texture but keeps the theme's light (day/night, sun shafts, lamp glow) and fixtures.
 * With no surface item the theme's own wall/floor show. So surfaces DO apply on top of any theme,
 * and can be tried in the shop on whichever (owned) theme is active.
 */
object RoomSurfaces {
    val WALLS = listOf("wall_stripe", "wall_starry")
    val FLOORS = listOf("floor_herringbone", "floor_mint_tile")

    fun kindOf(id: String): ItemKind? = Catalog.byId(id)?.kind?.takeIf { it == ItemKind.WALL || it == ItemKind.FLOOR }
    fun isSurface(id: String): Boolean = kindOf(id) != null

    fun wall(placements: List<Placement>): String? = placements.lastOrNull { kindOf(it.defId) == ItemKind.WALL }?.defId
    fun floor(placements: List<Placement>): String? = placements.lastOrNull { kindOf(it.defId) == ItemKind.FLOOR }?.defId

    /** Placements without surfaces: what is drawn / dragged / collided as furniture. */
    fun furniture(placements: List<Placement>): List<Placement> = placements.filterNot { isSurface(it.defId) }

    /** Puts [id] on, replacing any surface of the same kind (one wallpaper, one floor). */
    fun apply(placements: List<Placement>, id: String): List<Placement> {
        val kind = kindOf(id) ?: return placements
        return placements.filterNot { kindOf(it.defId) == kind } + Placement(id, 50, 50, 0)
    }

    /** Keeps only the last surface of each kind (repairs layouts saved before surfaces existed). */
    fun normalize(placements: List<Placement>): List<Placement> {
        val w = wall(placements); val f = floor(placements)
        return placements.filter { !isSurface(it.defId) || it.defId == w || it.defId == f }.distinctBy { it.defId }
    }
}
