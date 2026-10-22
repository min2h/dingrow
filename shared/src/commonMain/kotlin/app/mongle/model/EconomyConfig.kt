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
    /** 레벨이 1 오를 때마다 코인 보상에 더하는 비율. */
    const val coinLevelPercent = 8
    const val pityDryGifts = 40
    /** 일정 완료 때 선물이 붙을 확률. 1000 기준. */
    const val completionGiftPerMille = 200

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

    /** 희귀 고유 아이템 고정 되팔이. 300–600. */
    val rareResell: Map<String, Int> = mapOf(
        "moon_bell" to 360,
        "glass_marble" to 540,
    )

    /** 전설 고유 아이템 고정 되팔이. 화려한 물건일수록 높다. 700–3000. */
    val legendaryResell: Map<String, Int> = mapOf(
        "dawn_feather" to 860,
        "comet_shard" to 2400,
    )

    fun shopPrice(id: String): Int = shopPrices[id] ?: ((shopPriceMin + shopPriceMax) / 2)

    fun resellOf(def: ItemDef): Int = when (def.id) {
        in legendaryResell -> legendaryResell.getValue(def.id)
        in rareResell -> rareResell.getValue(def.id)
        else -> if (def.price > 0) def.price * commonResellPercent / 100 else def.sellPrice
    }
}
