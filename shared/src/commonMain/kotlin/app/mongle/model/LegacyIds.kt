package app.mongle.model

object LegacyIds {
    private val species = mapOf(
        "sp_peach" to "base",
        "sp_cat" to "cat",
        "sp_bunny" to "rabbit",
        "sp_bear" to "bear",
        "sp_frog" to "frog",
        "sp_chick" to "chick",
        "sp_dumpling" to "mandu",
        "sp_berry" to "strawberry",
        "sp_matcha" to "greentea",
        "sp_cloud" to "cloud",
        "sp_star" to "star",
        "sp_ghost" to "ghost",
        "sp_sprout" to "sprout",
        "sp_penguin" to "penguin",
        "sp_hamster" to "hamster",
        "sp_fox" to "fox",
        "sp_seal" to "seal",
        "sp_panda" to "panda",
        "sp_dragon" to "babydragon",
        "sp_egg" to "egg",
        "sp_milk" to "milk",
        "sp_injeolmi" to "injeolmi",
        "sp_lion" to "lion",
        "sp_tiger" to "tiger",
        "sp_squirrel" to "squirrel",
        "sp_sheep" to "sheep",
        "sp_pig" to "pig",
        "sp_koala" to "koala",
        "sp_lemon" to "lemon",
        "sp_pudding" to "pudding",
        "sp_donut" to "donut",
        "sp_mushroom" to "mushroom",
        "sp_moon" to "moon",
        "sp_rainbow" to "rainbow",
        "sp_acorn" to "chestnut",
    )

    private val items = mapOf(
        "hat_sprout" to "sprout_hat",
        "scarf_cloud" to "knit_scarf",
        "ribbon_berry" to "ribbon",
        "hood_yellow" to "hoodie",
        "bed_cloud" to "cushion_bed",
        "bed_wood" to "wood_bed",
        "lamp_sun" to "floor_lamp",
        "lamp_moon" to "cloud_lamp",
        "rug_mint" to "mint_rug",
        "rug_leaf" to "mint_rug",
        "plant_pot" to "flower_pot",
        "window_wood" to "window",
        "lights_string" to "string_lights",
        "tank_fish" to "fish_tank",
        "tv_retro" to "tv",
        "desk_wood" to "desk",
        "closet_wood" to "wardrobe",
        "tub_bath" to "bathtub",
        "tower_cat" to "cat_tower",
        "bowl_food" to "food_bowl",
        "clock_wall" to "wall_clock",
        "blanket_soft" to "blanket",
        "shelf_books" to "bookshelf",
        "table_round" to "round_table",
        "chair_round" to "round_chair",
        "gift_shell" to "seashell",
        "gift_clover" to "clover",
        "gift_pebble" to "pebble",
        "gift_letter" to "letter",
        "gift_acorn" to "acorn",
        "gift_star" to "star_piece",
        "gift_feather" to "feather",
        "gift_mushroom" to "mushroom_item",
        "food_basic" to "cookie",
        "food_candy" to "strawberry_cake",
        "food_peach" to "milk_bottle",
        "food_dumpling" to "rice_ball",
        "wall_peach" to "wood",
        "wall_butter" to "wood",
        "floor_wood" to "wood",
        "floor_cream" to "wood",
        "wall_mint" to "mint",
        "floor_tile" to "mint",
        "wall_night" to "starry",
    )

    fun speciesId(id: String): String = species[id] ?: id

    fun itemId(id: String?): String? = id?.let { items[it] ?: it }

    fun migrate(state: PersistedState): PersistedState {
        val pet = state.pet.copy(
            speciesId = speciesId(state.pet.speciesId),
            outfitId = itemId(state.pet.outfitId),
            outingGiftDefId = itemId(state.pet.outingGiftDefId),
            pendingGiftDefId = itemId(state.pet.pendingGiftDefId),
        )
        val foods = mutableMapOf<String, Int>()
        state.foods.forEach { (id, qty) ->
            val next = itemId(id) ?: id
            foods[next] = (foods[next] ?: 0) + qty
        }
        val owned = state.owned.map { itemId(speciesId(it)) ?: it }.toSet()
        val ownedAt = mutableMapOf<String, Long>()
        state.ownedAt.forEach { (id, at) ->
            val next = itemId(speciesId(id)) ?: id
            val previous = ownedAt[next]
            if (previous == null || at < previous) ownedAt[next] = at
        }
        return state.copy(
            pet = pet,
            foods = foods,
            gifts = state.gifts.map { it.copy(defId = itemId(it.defId) ?: it.defId) },
            owned = owned,
            ownedAt = ownedAt,
            presets = state.presets.map { preset ->
                preset.copy(
                    wallpaperId = roomTheme(itemId(preset.wallpaperId)),
                    floorId = roomTheme(itemId(preset.floorId)),
                    bedId = itemId(preset.bedId),
                    rugId = itemId(preset.rugId),
                    lampId = itemId(preset.lampId),
                )
            },
            pendingConfirmDef = itemId(state.pendingConfirmDef),
        )
    }
}
