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
        val others = current.placements.filterNot { it.defId == defId }
        push(current.copy(placements = others + Placement(defId, snap(x), snap(y), current.placements.find { it.defId == defId }?.rotation ?: 0)))
    }
    fun move(defId: String, x: Int, y: Int) = place(defId, x, y)
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
