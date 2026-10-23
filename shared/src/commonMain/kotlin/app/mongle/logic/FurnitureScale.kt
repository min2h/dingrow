package app.mongle.logic

/**
 * Real-world furniture sizes relative to 몽이, in one table used by every room renderer
 * (shop preview, 방꾸미기, home room). A spec gives the visible width ([w]) or height ([h]) in units of
 * 몽이's visible body (width 1.0, height 1.0); art fractions are the opaque bbox of each 256px icon.
 */
object FurnitureScale {
    /** 몽이 sprite: opaque body fills 0.895 of its square box wide, 0.78 tall. */
    const val PET_ART_W = 0.895f
    const val PET_ART_H = 0.78f

    class Spec(val w: Float = 0f, val h: Float = 0f, val artW: Float, val artH: Float, val wall: Boolean = false)

    val table: Map<String, Spec> = mapOf(
        "cushion_bed" to Spec(w = 1.8f, artW = 0.898f, artH = 0.777f),
        "wood_bed" to Spec(w = 1.9f, artW = 0.898f, artH = 0.875f),
        "bookshelf" to Spec(h = 1.6f, artW = 0.73f, artH = 0.898f),
        "round_table" to Spec(h = 1.0f, artW = 0.898f, artH = 0.758f),
        "floor_lamp" to Spec(h = 1.2f, artW = 0.359f, artH = 0.898f),
        "cloud_lamp" to Spec(h = 0.6f, artW = 0.898f, artH = 0.723f),
        "round_chair" to Spec(h = 0.9f, artW = 0.898f, artH = 0.898f),
        "velvet_armchair" to Spec(h = 1.15f, artW = 0.828f, artH = 0.77f),
        "sofa" to Spec(w = 2.0f, artW = 0.898f, artH = 0.617f),
        "mint_rug" to Spec(w = 2.4f, artW = 0.898f, artH = 0.531f),
        "round_mint_rug" to Spec(w = 1.9f, artW = 0.828f, artH = 0.527f),
        "flower_pot" to Spec(h = 1.0f, artW = 0.754f, artH = 0.898f),
        "window" to Spec(w = 1.3f, artW = 0.777f, artH = 0.898f, wall = true),
        "string_lights" to Spec(w = 2.0f, artW = 0.898f, artH = 0.367f, wall = true),
        "fish_tank" to Spec(w = 1.0f, artW = 0.867f, artH = 0.898f),
        "piano" to Spec(w = 1.9f, artW = 0.848f, artH = 0.898f),
        "tv" to Spec(w = 1.3f, artW = 0.805f, artH = 0.895f),
        "desk" to Spec(w = 1.7f, artW = 0.898f, artH = 0.801f),
        "wardrobe" to Spec(h = 1.9f, artW = 0.688f, artH = 0.898f),
        "bathtub" to Spec(w = 1.9f, artW = 0.898f, artH = 0.734f),
        "cat_tower" to Spec(h = 1.9f, artW = 0.555f, artH = 0.898f),
        "food_bowl" to Spec(w = 0.5f, artW = 0.898f, artH = 0.727f),
        "wall_clock" to Spec(w = 0.6f, artW = 0.883f, artH = 0.898f, wall = true),
        "blanket" to Spec(w = 1.4f, artW = 0.898f, artH = 0.664f),
        "picture_frame" to Spec(w = 0.7f, artW = 0.734f, artH = 0.898f, wall = true),
        "mini_plant" to Spec(h = 0.5f, artW = 0.617f, artH = 0.824f),
        "candle" to Spec(h = 0.4f, artW = 0.5f, artH = 0.828f),
        "sticker_sheet" to Spec(w = 0.45f, artW = 0.758f, artH = 0.828f),
        "mug" to Spec(w = 0.35f, artW = 0.828f, artH = 0.758f),
        "ribbon_garland" to Spec(w = 1.6f, artW = 0.828f, artH = 0.422f, wall = true),
        "floor_cushion" to Spec(w = 0.9f, artW = 0.828f, artH = 0.633f),
        "sun_poster" to Spec(w = 0.8f, artW = 0.824f, artH = 0.719f, wall = true),
        "snack_bowl" to Spec(w = 0.5f, artW = 0.828f, artH = 0.723f),
        "canopy_bed" to Spec(w = 1.9f, artW = 0.723f, artH = 0.836f),
        "aroma_diffuser" to Spec(h = 0.45f, artW = 0.531f, artH = 0.828f),
        "bunny_plush" to Spec(h = 0.6f, artW = 0.547f, artH = 0.828f),
        "cherry_branch" to Spec(h = 1.1f, artW = 0.703f, artH = 0.828f),
        "desk_globe" to Spec(h = 0.55f, artW = 0.582f, artH = 0.828f),
        "hanging_chair" to Spec(h = 1.7f, artW = 0.648f, artH = 0.836f),
        "moon_mirror" to Spec(w = 0.9f, artW = 0.762f, artH = 0.824f, wall = true),
        "mushroom_lamp" to Spec(h = 0.7f, artW = 0.711f, artH = 0.824f),
        "music_box" to Spec(h = 0.45f, artW = 0.602f, artH = 0.828f),
        "pumpkin_lantern" to Spec(h = 0.6f, artW = 0.789f, artH = 0.828f),
        "rattan_shelf" to Spec(h = 1.5f, artW = 0.664f, artH = 0.836f),
        "snow_globe" to Spec(h = 0.45f, artW = 0.652f, artH = 0.828f),
        "star_projector" to Spec(h = 0.5f, artW = 0.699f, artH = 0.828f),
        "terrarium" to Spec(h = 0.6f, artW = 0.719f, artH = 0.836f),
        "vanity_table" to Spec(h = 1.3f, artW = 0.734f, artH = 0.836f),
        "xmas_tree" to Spec(h = 2.0f, artW = 0.586f, artH = 0.828f),
    )
    private val fallback = Spec(w = 1.0f, artW = 0.85f, artH = 0.85f)
    fun spec(id: String) = table[id] ?: fallback
    /** Rugs lie flat: 몽이 and furniture may stand on them, so they skip collisions. */
    fun isRug(id: String) = id.endsWith("_rug")

    /** Side of the item's square art box, as a multiple of 몽이's square box side (perspective not applied). */
    fun boxFactor(id: String): Float = spec(id).let { s ->
        if (s.w > 0f) s.w * PET_ART_W / s.artW else s.h * PET_ART_H / s.artH
    }

    /** Back of the floor ([RoomFloor.Y] start) reads at 75% of the front-edge size where 몽이 stands. */
    fun perspective(y: Int): Float {
        if (y <= 0) return 0.75f
        val t = ((y - RoomFloor.Y.first).toFloat() / (FRONT_Y - RoomFloor.Y.first)).coerceIn(0f, 1f)
        return 0.75f + 0.25f * t
    }
    /** Wall-mounted items hang on the back wall, so they always read at back-wall size. */
    fun scaleAt(id: String, y: Int): Float = if (spec(id).wall) 0.75f else perspective(y)
    /** 몽이's feet line (% of stage height); 몽이 itself is drawn at scale 1 there. */
    const val FRONT_Y = 97

    /** Visible width of the item in % of stage width when 몽이's box is [petScale] of the stage width. */
    fun widthPct(id: String, y: Int, petScale: Float): Float =
        boxFactor(id) * petScale * spec(id).artW * scaleAt(id, y) * 100f
}
