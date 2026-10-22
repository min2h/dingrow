package app.mongle.model

/**
 * 상점 가격, 되팔이, 선물 확률. 숫자를 바꿀 때는 여기만 고친다.
 * 보상 굴림은 서버 없이 시드 RNG로 결정한다.
 */
object EconomyConfig {
    const val shopPriceMin = 150
    const val shopPriceMax = 200
    const val commonResellPercent = 70

    const val weightCoins = 750
    const val weightCommon = 200
    const val weightRare = 45
    const val weightLegendary = 5
    const val weightTotal = weightCoins + weightCommon + weightRare + weightLegendary

    const val coinMin = 10
    const val coinMax = 50
    /** 레벨이 1 오를 때마다 코인 범위에 곱해지는 비율(%). Lv1 10–50 → Lv4 31–155. */
    const val coinLevelPercent = 70
    /** 레벨당 희귀/전설 가중치 증가(1000 기준). 전설은 legendaryCap(1.5%)을 넘지 않는다. */
    const val rareLevelStep = 10
    const val rareCap = 85
    const val legendaryLevelStep = 3
    const val legendaryCap = 15
    const val medicinePrice = 50
    /** 방 테마 가격. 기본(wood)은 무료. */
    val roomPrices: Map<String, Int> = mapOf("mint" to 700, "starry" to 1000)

    /** 레벨별 가중치: (coins, common, rare, legendary), 합계는 항상 weightTotal. */
    fun weightsFor(level: Int): IntArray {
        val up = (level - 1).coerceAtLeast(0)
        val rare = (weightRare + up * rareLevelStep).coerceAtMost(rareCap)
        val legend = (weightLegendary + up * legendaryLevelStep).coerceAtMost(legendaryCap)
        val coins = weightTotal - weightCommon - rare - legend
        return intArrayOf(coins, weightCommon, rare, legend)
    }

    fun coinRange(level: Int): IntRange {
        val mult = 100 + (level - 1).coerceAtLeast(0) * coinLevelPercent
        return (coinMin * mult / 100)..(coinMax * mult / 100)
    }

    /** 50/100 저가 아이템. */
    val cheapPrices: Map<String, Int> = mapOf(
        "mini_plant" to 50, "candle" to 50, "sticker_sheet" to 50, "mug" to 50, "ribbon_garland" to 50, "cozy_socks" to 50,
        "floor_cushion" to 100, "round_mint_rug" to 100, "sun_poster" to 100, "snack_bowl" to 100,
    )

    /** 새 예쁜 아이템 300–600. */
    val prettyPrices: Map<String, Int> = mapOf(
        "canopy_bed" to 600, "velvet_armchair" to 500, "vanity_table" to 550, "rattan_shelf" to 450, "hanging_chair" to 600,
        "terrarium" to 400, "mushroom_lamp" to 350, "star_projector" to 450, "moon_mirror" to 400, "bunny_plush" to 300,
        "music_box" to 450, "desk_globe" to 350, "aroma_diffuser" to 300, "wall_stripe" to 500, "wall_starry" to 600,
        "floor_herringbone" to 500, "floor_mint_tile" to 500, "cardigan" to 400, "beanie" to 300, "sailor_collar" to 350,
        "overalls" to 450, "pearl_necklace" to 500, "heart_hairpin" to 300, "tiny_backpack" to 400,
        "pumpkin_lantern" to 350, "snow_globe" to 450, "xmas_tree" to 600, "cherry_branch" to 400,
    )
    const val pityDryGifts = 40
    /** 일정 완료 때 선물이 붙을 확률. 1000 기준. */
    const val completionGiftPerMille = 100

    const val legendaryReactionId = "legend_sparkle"
    const val legendaryReactionLine = "반짝, 전설이 손 안에 내려왔어요"

    val shopPrices: Map<String, Int> = mapOf(
        "cookie" to 150,
        "rice_ball" to 160,
        "milk_bottle" to 170,
        "strawberry_cake" to 200,
        "ribbon" to 150,
        "beret" to 160,
        "knit_scarf" to 170,
        "raincoat" to 180,
        "crown" to 200,
        "sunglasses" to 160,
        "sprout_hat" to 170,
        "hoodie" to 190,
        "straw_hat" to 160,
        "bunny_ears" to 180,
        "flower_crown" to 190,
        "bow_tie" to 150,
        "rain_boots" to 170,
        "cushion_bed" to 180,
        "wood_bed" to 190,
        "bookshelf" to 170,
        "round_table" to 180,
        "floor_lamp" to 160,
        "round_chair" to 170,
        "mint_rug" to 150,
        "flower_pot" to 150,
        "window" to 180,
        "string_lights" to 170,
        "fish_tank" to 200,
        "piano" to 200,
        "tv" to 190,
        "desk" to 180,
        "wardrobe" to 200,
        "bathtub" to 190,
        "cat_tower" to 200,
        "food_bowl" to 150,
        "wall_clock" to 160,
        "blanket" to 150,
        "sofa" to 190,
        "cloud_lamp" to 180,
        "picture_frame" to 160,
    )

    /** 선물 전용 희귀 아이템 고정 되팔이. 100–500. 상점에 없다. */
    val rareResell: Map<String, Int> = mapOf(
        "moon_bell" to 220, "glass_marble" to 260, "rainbow_shell" to 300, "amber_drop" to 340, "crystal_leaf" to 280,
        "music_charm" to 180, "pocket_watch" to 420, "silver_key" to 240, "cloud_puff" to 120, "lucky_dice" to 160,
        "rose_heart" to 380, "fairy_lantern" to 460, "clover_brooch" to 200, "sun_amulet" to 500, "snow_crystal" to 320,
        "unicorn_horn" to 480,
    )

    /** 선물 전용 전설 아이템 고정 되팔이. 300–800. 화려할수록 높다. */
    val legendaryResell: Map<String, Int> = mapOf(
        "dawn_feather" to 420, "comet_shard" to 760, "phoenix_plume" to 800, "aurora_jar" to 700, "golden_acorn" to 300,
        "mermaid_pearl" to 560, "star_crown" to 650, "dragon_egg" to 740, "moon_rabbit" to 600, "galaxy_marble" to 680,
    )

    fun shopPrice(id: String): Int = shopPrices[id] ?: cheapPrices[id] ?: prettyPrices[id] ?: ((shopPriceMin + shopPriceMax) / 2)

    fun resellOf(def: ItemDef): Int = when (def.id) {
        in legendaryResell -> legendaryResell.getValue(def.id)
        in rareResell -> rareResell.getValue(def.id)
        else -> if (def.price > 0) def.price * commonResellPercent / 100 else def.sellPrice
    }
}
