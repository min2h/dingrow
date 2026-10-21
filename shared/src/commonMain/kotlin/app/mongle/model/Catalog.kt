package app.mongle.model

object Catalog {
    val foodBasic = ItemDef(
        id = "food_basic",
        name = "풀잎 쿠키",
        kind = ItemKind.FOOD,
        price = 12,
        hunger = 28,
        happiness = 2,
        xp = 6,
        stackable = true,
        blurb = "바삭하고 달콤해요. 매일 먹어도 질리지 않아요.",
    )
    val foodCandy = ItemDef(
        id = "food_candy",
        name = "별사탕",
        kind = ItemKind.FOOD,
        rarity = Rarity.UNCOMMON,
        price = 28,
        hunger = 42,
        happiness = 8,
        xp = 10,
        stackable = true,
        blurb = "먹으면 눈이 반짝여요.",
    )

    val outfits = listOf(
        ItemDef("hat_sprout", "새싹 모자", ItemKind.OUTFIT, price = 40, blurb = "머리 위에 작은 잎이 자라요."),
        ItemDef("scarf_cloud", "구름 목도리", ItemKind.OUTFIT, rarity = Rarity.UNCOMMON, price = 55, blurb = "포근한 목도리."),
        ItemDef("ribbon_berry", "베리 리본", ItemKind.OUTFIT, rarity = Rarity.RARE, price = 70, blurb = "특별한 날에 매는 리본."),
    )

    val furniture = listOf(
        ItemDef("wall_peach", "복숭아 벽", ItemKind.WALL, price = 20, blurb = "따뜻한 오후 같은 벽."),
        ItemDef("wall_mint", "민트 벽", ItemKind.WALL, price = 20, blurb = "시원한 민트색."),
        ItemDef("wall_night", "밤하늘 벽", ItemKind.WALL, rarity = Rarity.UNCOMMON, price = 48, blurb = "작은 별이 떠 있어요."),
        ItemDef("floor_wood", "나무 바닥", ItemKind.FLOOR, price = 16, blurb = "맨발로 걷기 좋아요."),
        ItemDef("floor_tile", "타일 바닥", ItemKind.FLOOR, price = 16, blurb = "반듯한 민트 타일."),
        ItemDef("bed_cloud", "구름 침대", ItemKind.BED, rarity = Rarity.UNCOMMON, price = 80, blurb = "눕으면 폭신해요."),
        ItemDef("bed_wood", "나무 침대", ItemKind.BED, price = 60, blurb = "단순한 나무 침대."),
        ItemDef("rug_star", "별 러그", ItemKind.RUG, price = 36, blurb = "별 모양이 수놓여 있어요."),
        ItemDef("rug_leaf", "잎사귀 러그", ItemKind.RUG, price = 30, blurb = "초록 잎 한 장."),
        ItemDef("lamp_moon", "달 조명", ItemKind.LAMP, rarity = Rarity.UNCOMMON, price = 46, blurb = "밤에도 은은해요."),
        ItemDef("lamp_sun", "해 조명", ItemKind.LAMP, price = 46, blurb = "방 안이 환해져요."),
    )

    val gifts = listOf(
        ItemDef("gift_shell", "조개껍데기", ItemKind.GIFT, Rarity.COMMON, sellPrice = 8, blurb = "파도 소리를 담고 있어요."),
        ItemDef("gift_clover", "네잎클로버", ItemKind.GIFT, Rarity.COMMON, sellPrice = 8, blurb = "오늘은 운이 좋아요."),
        ItemDef("gift_pebble", "반짝 조약돌", ItemKind.GIFT, Rarity.UNCOMMON, sellPrice = 18, blurb = "강가에서 주워 왔어요."),
        ItemDef("gift_letter", "작은 쪽지", ItemKind.GIFT, Rarity.UNCOMMON, sellPrice = 15, blurb = "누군가 남긴 응원."),
        ItemDef("gift_acorn", "황금 도토리", ItemKind.GIFT, Rarity.RARE, sellPrice = 40, blurb = "무게가 묵직해요."),
        ItemDef("gift_star", "작은 별조각", ItemKind.GIFT, Rarity.EPIC, sellPrice = 90, blurb = "손바닥 위에서 빛나요."),
    )

    val all: List<ItemDef> = listOf(foodBasic, foodCandy) + outfits + furniture + gifts

    fun byId(id: String?): ItemDef? = all.find { it.id == id }

    fun foods(): List<ItemDef> = listOf(foodBasic, foodCandy)

    /** 위젯 '오늘의 상점'에 올라가는 로테이션. 먹이 기본템은 항상 따로 팔아요. */
    fun rotating(): List<ItemDef> = listOf(foodCandy) + outfits + furniture.filter { it.price > 0 }

    fun slotOf(kind: ItemKind): DecorSlot? = when (kind) {
        ItemKind.WALL -> DecorSlot.WALL
        ItemKind.FLOOR -> DecorSlot.FLOOR
        ItemKind.BED -> DecorSlot.BED
        ItemKind.RUG -> DecorSlot.RUG
        ItemKind.LAMP -> DecorSlot.LAMP
        else -> null
    }
}

fun wallHex(id: String?): String = when (id) {
    "wall_mint" -> "#D7F3EC"
    "wall_night" -> "#2C3A5A"
    "wall_peach" -> "#FFE3D6"
    else -> "#FFF6EE"
}

fun floorHex(id: String?): String = when (id) {
    "floor_tile" -> "#C9E7E0"
    "floor_wood" -> "#E7C8A4"
    else -> "#F3D7B5"
}

fun isDarkWall(id: String?): Boolean = id == "wall_night"
