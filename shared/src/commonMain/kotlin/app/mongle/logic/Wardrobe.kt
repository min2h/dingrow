package app.mongle.logic

/** Where a worn item sits on the body. One item per slot; different slots stack. */
enum class WearSlot { FEET, BODY, BACK, NECK, FACE, HEAD }

/**
 * Slot-based wearing shared by every platform (app, shop preview, 방꾸미기, widgets, wallpaper, cloud).
 *
 * Storage: the worn set lives in the existing single `outfitId` field as a comma-separated list in
 * draw order (`"raincoat,knit_scarf,sunglasses,crown"`). An old save/cloud doc with one id is simply a
 * one-item list, so the old single-outfit field migrates without a schema change; [normalize] also maps
 * legacy ids, drops unknown ids and resolves slot clashes.
 *
 * Draw order (back to front): feet, body, back(pack), neck, face, head.
 *
 * Conflict rule (hooded garments): the raincoat/hoodie hood is drawn UP only when the head slot is empty.
 * Wearing any head item puts the hood DOWN (only the coat shows), so hats never fight with the hood and
 * the pet keeps the coat. This looked more natural than making the coat occupy head+body.
 */
object Wardrobe {
    val slots: Map<String, WearSlot> = mapOf(
        "ribbon" to WearSlot.HEAD, "beret" to WearSlot.HEAD, "crown" to WearSlot.HEAD, "sprout_hat" to WearSlot.HEAD,
        "straw_hat" to WearSlot.HEAD, "bunny_ears" to WearSlot.HEAD, "flower_crown" to WearSlot.HEAD, "beanie" to WearSlot.HEAD,
        "heart_hairpin" to WearSlot.HEAD, "sunglasses" to WearSlot.FACE,
        "knit_scarf" to WearSlot.NECK, "bow_tie" to WearSlot.NECK, "sailor_collar" to WearSlot.NECK, "pearl_necklace" to WearSlot.NECK,
        "raincoat" to WearSlot.BODY, "hoodie" to WearSlot.BODY, "cardigan" to WearSlot.BODY, "overalls" to WearSlot.BODY,
        "tiny_backpack" to WearSlot.BACK, "rain_boots" to WearSlot.FEET, "cozy_socks" to WearSlot.FEET,
    )
    val hooded = setOf("raincoat", "hoodie")

    fun slotOf(id: String): WearSlot? = slots[id]

    /** Decodes the stored field into ids in draw order. */
    fun decode(raw: String?): List<String> = normalizeList(raw?.split(',')?.map { it.trim() }.orEmpty())

    fun encode(ids: List<String>): String? = normalizeList(ids).takeIf { it.isNotEmpty() }?.joinToString(",")

    /** Migration/cleanup: legacy ids, unknown ids dropped, last item per slot wins, canonical draw order. */
    fun normalize(raw: String?, mapId: (String) -> String = { it }): String? =
        encode(raw?.split(',')?.map { mapId(it.trim()) }.orEmpty())

    private fun normalizeList(ids: List<String>): List<String> {
        val bySlot = LinkedHashMap<WearSlot, String>()
        ids.filter { it in slots }.forEach { bySlot[slots.getValue(it)] = it }
        return WearSlot.entries.mapNotNull { bySlot[it] }
    }

    /** Toggle [id]: worn → taken off; otherwise put on, replacing whatever is in its slot. */
    fun toggle(raw: String?, id: String): String? {
        val cur = decode(raw)
        if (id in cur) return encode(cur - id)
        val slot = slotOf(id) ?: return raw
        return encode(cur.filter { slots[it] != slot } + id)
    }

    fun wears(raw: String?, id: String) = id in decode(raw)

    /** Hood is up only when no head item is worn. */
    fun hoodUp(raw: String?): Boolean = decode(raw).none { slots[it] == WearSlot.HEAD }
}
