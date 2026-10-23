package app.mongle.logic

import app.mongle.model.Catalog
import app.mongle.model.EconomyConfig
import app.mongle.model.ItemKind
import app.mongle.model.Placement

/** A room layout snapshot used by both 방꾸미기 sessions and the shop's try-on room. */
data class Layout(
    val placements: List<Placement> = emptyList(),
    val outfitId: String? = null,
    val roomId: String = "wood",
    /** Mirrored items (defIds). */
    val flipped: Set<String> = emptySet(),
)

/**
 * Editable layout with undo + revert. Pure (no persistence): 방꾸미기 saves it with [GameService.applyLayout];
 * the shop preview never saves, so it can't change the real room or grant ownership.
 * Positions are percentages snapped to a [grid] step.
 */
class LayoutEditor(val start: Layout, private val grid: Int = 5) {
    var current: Layout = start; private set
    private val history = ArrayDeque<Layout>()

    private fun push(next: Layout) { history.addLast(current); if (history.size > 50) history.removeFirst(); current = next }
    private fun snap(v: Int) = ((v + grid / 2) / grid * grid).coerceIn(0, 100)

    fun place(defId: String, x: Int = 50, y: Int = 70) {
        if (RoomSurfaces.isSurface(defId)) { push(current.copy(placements = RoomSurfaces.apply(current.placements, defId))); return }
        val others = current.placements.filterNot { it.defId == defId }
        push(current.copy(placements = others + Placement(defId, snap(x), snap(y), current.placements.find { it.defId == defId }?.rotation ?: 0)))
    }
    fun move(defId: String, x: Int, y: Int) {
        if (RoomSurfaces.isSurface(defId)) return // surfaces are not draggable
        RoomFloor.clamp(x, y, defId).let { (cx, cy) -> place(defId, cx, cy) }
    }
    /** Adds an item at the first free floor spot that keeps clear of 몽이 (see [RoomFloor.defaultSpot]). */
    fun placeFree(defId: String) {
        if (RoomSurfaces.isSurface(defId)) return place(defId)
        RoomFloor.defaultSpot(RoomSurfaces.furniture(current.placements).filterNot { it.defId == defId }, defId, app.mongle.model.roomTheme(current.roomId)).let { (x, y) -> place(defId, x, y) }
    }
    fun rotate(defId: String) {
        push(current.copy(placements = current.placements.map { if (it.defId == defId) it.copy(rotation = (it.rotation + 90) % 360) else it }))
    }
    fun flip(defId: String) {
        push(current.copy(flipped = if (defId in current.flipped) current.flipped - defId else current.flipped + defId))
    }
    fun remove(defId: String) = push(current.copy(placements = current.placements.filterNot { it.defId == defId }))
    /** Sets the whole worn set (null = nothing). */
    fun wear(outfitId: String?) = push(current.copy(outfitId = Wardrobe.normalize(outfitId)))
    /** Slot-based toggle: put on (replacing the same slot) or take off. */
    fun toggleWear(id: String) = push(current.copy(outfitId = Wardrobe.toggle(current.outfitId, id)))
    fun room(roomId: String) = push(current.copy(roomId = roomId))
    fun undo(): Boolean { val prev = history.removeLastOrNull() ?: return false; current = prev; return true }
    /** '원래대로': back to the state at session start. */
    fun revert() { history.clear(); current = start }
    val dirty: Boolean get() = current != start
}

object Shop {
    /** Room unlock ids stored in `owned`. The default wood room is free. */
    fun roomItemId(roomId: String) = "room_$roomId"
    fun roomPrice(roomId: String): Int = EconomyConfig.roomPrices[roomId] ?: 0
    fun roomOwned(roomId: String, owned: Set<String>) = roomPrice(roomId) == 0 || roomItemId(roomId) in owned

    /** Unowned things used in a preview layout → what the user would need to buy. */
    fun cart(layout: Layout, owned: Set<String>): List<String> {
        val ids = layout.placements.map { it.defId } + Wardrobe.decode(layout.outfitId)
        val items = ids.distinct().filter { it !in owned && (Catalog.byId(it)?.price ?: 0) > 0 }
        return items // rooms are never previewed before purchase, so never in the cart
    }
    /** Unowned rooms can't be applied (not even in the shop preview) until bought. */
    fun canApplyRoom(roomId: String, owned: Set<String>) = roomOwned(roomId, owned)
    fun priceOf(id: String): Int = if (id.startsWith("room_")) roomPrice(id.removePrefix("room_")) else Catalog.byId(id)?.price ?: 0
    fun total(ids: List<String>): Int = ids.sumOf { priceOf(it) }

    /** 방꾸미기 shows only owned things. */
    fun decoratable(owned: Set<String>) = (Catalog.furniture + Catalog.cheap + Catalog.pretty + Catalog.outfits)
        .filter { it.id in owned || it.id in FreeDecor.ids }
    fun wearable(owned: Set<String>) = decoratable(owned).filter { it.kind == ItemKind.OUTFIT }
}

/** Flip is stored inside [Placement.rotation] (+1000) so the existing persistence keeps it. */
private const val FLIP = 1000
fun Placement.isFlipped(): Boolean = rotation >= FLIP
fun Placement.baseRotation(): Int = rotation % FLIP

/** The real room as an editable [Layout]. */
fun layoutOf(placements: List<Placement>, outfitId: String?, roomId: String): Layout = Layout(
    placements = placements.map { it.copy(rotation = it.baseRotation()) },
    outfitId = outfitId,
    roomId = roomId,
    flipped = placements.filter { it.isFlipped() }.map { it.defId }.toSet(),
)

/** Placements with flips encoded, ready to persist. */
fun Layout.encodedPlacements(): List<Placement> =
    placements.map { if (it.defId in flipped) it.copy(rotation = it.baseRotation() + FLIP) else it }


/**
 * Floor geometry of the 3:4 room stage, in the same 0..100 percent space as [Placement].
 * 몽이 stands bottom-centre (roughly x 36..64, y 68..96), so default spots ring around it.
 * Depth: items are drawn by ascending y (lower on screen = in front); 몽이 sits at [PET_DEPTH].
 */
object RoomFloor {
    val X = 10..90
    val Y = 45..92
    const val PET_DEPTH = 86
    private val spots = listOf(22 to 66, 78 to 66, 28 to 52, 72 to 52, 50 to 50, 16 to 86, 84 to 86, 50 to 62, 15 to 56, 85 to 56)
    /** Wall-mounted items hang on the back wall: bottom edge in this band (above every theme's floor line). */
    /** Band (bottom-anchor y, % of stage) where wall-mounted items hang: on the back walls, above furniture. */
    val WALL_Y = 12..40
    /** Free wall spots per theme art, clear of its windows, shelf, moon lamp and corner. */
    fun wallSpots(theme: String): List<Pair<Int, Int>> = when (theme) {
        "mint" -> listOf(12 to 18, 84 to 22, 84 to 40, 34 to 14, 66 to 14, 94 to 32, 76 to 40, 50 to 12)
        "starry" -> listOf(36 to 26, 52 to 24, 30 to 40, 46 to 40, 58 to 36, 22 to 20, 8 to 22, 44 to 14)
        else -> listOf(10 to 18, 24 to 16, 38 to 14, 54 to 18, 12 to 32, 26 to 30, 40 to 28, 56 to 30)
    }
    fun clamp(x: Int, y: Int, id: String = "") =
        if (FurnitureScale.spec(id).wall) x.coerceIn(X.first, X.last) to y.coerceIn(WALL_Y.first, WALL_Y.last)
        else x.coerceIn(X.first, X.last) to y.coerceIn(Y.first, Y.last)
    /** Older saves put wall items on the floor; they are shown hanging on the wall instead. */
    fun hang(p: Placement): Placement = if (FurnitureScale.spec(p.defId).wall && p.y !in WALL_Y) p.copy(y = p.y.coerceIn(WALL_Y.first, WALL_Y.last)) else p
    fun overlapsPet(x: Int, y: Int) = x in 36..64 && y >= 66
    /** Room scale every collision check assumes (home room / 방꾸미기 몽이 box = 28% of stage width). */
    const val ROOM_PET_SCALE = 0.28f
    private fun halfW(id: String, y: Int) = FurnitureScale.widthPct(id, y, ROOM_PET_SCALE) / 2
    /** Visible height in % of the 3:4 stage height (how far up the floor an item covers what stands behind it). */
    private fun heightPct(id: String, y: Int) = FurnitureScale.boxFactor(id) * ROOM_PET_SCALE * FurnitureScale.spec(id).artH * FurnitureScale.scaleAt(id, y) * 75f
    /** True when [id] at (x, y) overlaps another placed item's floor footprint or 몽이's spot (front centre). */
    fun collides(id: String, x: Int, y: Int, taken: List<Placement>): Boolean {
        val wall = FurnitureScale.spec(id).wall
        val hw = halfW(id, y)
        val rug = FurnitureScale.isRug(id)
        if (!wall && !rug && x + hw > 38 && x - hw < 62 && y >= 74) return true
        return taken.filterNot { RoomSurfaces.isSurface(it.defId) }.map(::hang).any { o ->
            FurnitureScale.spec(o.defId).wall == wall && FurnitureScale.isRug(o.defId) == rug &&
                kotlin.math.abs(o.x - x) < hw + halfW(o.defId, o.y) - 2 &&
                kotlin.math.abs(o.y - y) < maxOf(9f, 0.6f * heightPct(if (o.y > y) o.defId else id, maxOf(o.y, y)))
        }
    }
    fun defaultSpot(taken: List<Placement>, id: String = "", theme: String = "wood"): Pair<Int, Int> =
        if (FurnitureScale.spec(id).wall) wallSpots(theme).let { ws -> ws.firstOrNull { (x, y) -> !collides(id, x, y, taken.map(::hang)) } ?: ws[taken.size % ws.size] }
        else (if (FurnitureScale.isRug(id)) listOf(50 to 92) + spots else spots).firstOrNull { (x, y) -> !collides(id, x, y, taken) }
            ?: spots.firstOrNull { (x, y) -> taken.none { kotlin.math.abs(it.x - x) < 15 && kotlin.math.abs(it.y - y) < 12 } }
            ?: spots[taken.size % spots.size]
    /** Furniture draw order: wall-mounted items, then rugs, then by depth. Surfaces are not furniture. */
    fun drawOrder(placements: List<Placement>) = RoomSurfaces.furniture(placements).map(::hang)
        .sortedWith(compareBy({ !FurnitureScale.spec(it.defId).wall }, { !FurnitureScale.isRug(it.defId) }, { it.y }))
    /** Drawn behind 몽이 regardless of depth. */
    fun alwaysBehind(id: String) = FurnitureScale.isRug(id) || FurnitureScale.spec(id).wall
    /**
     * Y-rotation (degrees) that turns a wall item onto the wall plane it hangs on, per room art:
     * wood has a left wall (x < 48) and a right wall; Night Sky a big left wall and the window wall; Mint is frontal.
     */
    fun wallTilt(theme: String, x: Int): Float = when (theme) {
        "wood" -> if (x < 48) 22f else -10f
        "starry" -> if (x < 62) 12f else -30f
        else -> 0f
    }
}
