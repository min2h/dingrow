package app.mongle.model

private fun ItemDef.withShopPrice(): ItemDef = copy(price = EconomyConfig.shopPrice(id))

object Catalog {
    val strawberry_cake = ItemDef(
        id = "strawberry_cake",
        name = "딸기 케이크",
        kind = ItemKind.FOOD,
        rarity = Rarity.UNCOMMON,
        price = 200,
        hunger = 44,
        happiness = 8,
        xp = 10,
        stackable = true,
        blurb = "딸기 케이크.",
    ).withShopPrice()
    val rice_ball = ItemDef(
        id = "rice_ball",
        name = "주먹밥",
        kind = ItemKind.FOOD,
        rarity = Rarity.COMMON,
        price = 100,
        hunger = 34,
        happiness = 4,
        xp = 6,
        stackable = true,
        blurb = "주먹밥.",
    ).withShopPrice()
    val milk_bottle = ItemDef(
        id = "milk_bottle",
        name = "우유병",
        kind = ItemKind.FOOD,
        rarity = Rarity.COMMON,
        price = 100,
        hunger = 22,
        happiness = 2,
        xp = 3,
        stackable = true,
        blurb = "우유병.",
    ).withShopPrice()
    val cookie = ItemDef(
        id = "cookie",
        name = "쿠키",
        kind = ItemKind.FOOD,
        rarity = Rarity.COMMON,
        price = 80,
        hunger = 26,
        happiness = 3,
        xp = 4,
        stackable = true,
        blurb = "쿠키.",
    ).withShopPrice()
    val foodBasic = cookie
    val foodCandy = strawberry_cake

    val outfits = listOf(
        ItemDef("ribbon", "리본", ItemKind.OUTFIT, Rarity.COMMON, price = 150, blurb = "리본."),
        ItemDef("beret", "베레모", ItemKind.OUTFIT, Rarity.COMMON, price = 200, blurb = "베레모."),
        ItemDef("knit_scarf", "니트 목도리", ItemKind.OUTFIT, Rarity.COMMON, price = 250, blurb = "니트 목도리."),
        ItemDef("raincoat", "우비", ItemKind.OUTFIT, Rarity.COMMON, price = 300, blurb = "우비."),
        ItemDef("crown", "왕관", ItemKind.OUTFIT, Rarity.UNCOMMON, price = 500, blurb = "왕관."),
        ItemDef("sunglasses", "선글라스", ItemKind.OUTFIT, Rarity.COMMON, price = 200, blurb = "선글라스."),
        ItemDef("sprout_hat", "새싹 모자", ItemKind.OUTFIT, Rarity.COMMON, price = 350, blurb = "새싹 모자."),
        ItemDef("hoodie", "후드티", ItemKind.OUTFIT, Rarity.UNCOMMON, price = 400, blurb = "후드티.", fresh = true),
        ItemDef("straw_hat", "밀짚모자", ItemKind.OUTFIT, Rarity.COMMON, price = 250, blurb = "밀짚모자."),
        ItemDef("bunny_ears", "토끼 귀 머리띠", ItemKind.OUTFIT, Rarity.COMMON, price = 300, blurb = "토끼 귀 머리띠."),
        ItemDef("flower_crown", "꽃 화관", ItemKind.OUTFIT, Rarity.COMMON, price = 350, blurb = "꽃 화관."),
        ItemDef("bow_tie", "나비넥타이", ItemKind.OUTFIT, Rarity.COMMON, price = 150, blurb = "나비넥타이."),
        ItemDef("rain_boots", "노란 장화", ItemKind.OUTFIT, Rarity.COMMON, price = 250, blurb = "노란 장화."),
    ).map { it.withShopPrice() }

    val furniture = listOf(
        ItemDef("cushion_bed", "쿠션 침대", ItemKind.BED, Rarity.RARE, price = 800, blurb = "쿠션 침대."),
        ItemDef("wood_bed", "원목 침대", ItemKind.BED, Rarity.RARE, price = 800, blurb = "원목 침대."),
        ItemDef("bookshelf", "책장", ItemKind.DECOR, Rarity.UNCOMMON, price = 600, blurb = "책장."),
        ItemDef("round_table", "원형 테이블", ItemKind.DECOR, Rarity.UNCOMMON, price = 550, blurb = "원형 테이블."),
        ItemDef("floor_lamp", "스탠드 조명", ItemKind.LAMP, Rarity.UNCOMMON, price = 450, blurb = "스탠드 조명."),
        ItemDef("round_chair", "동글 의자", ItemKind.DECOR, Rarity.UNCOMMON, price = 500, blurb = "동글 의자."),
        ItemDef("mint_rug", "민트 러그", ItemKind.RUG, Rarity.COMMON, price = 300, blurb = "민트 러그."),
        ItemDef("flower_pot", "화분", ItemKind.DECOR, Rarity.COMMON, price = 300, blurb = "화분."),
        ItemDef("window", "창문", ItemKind.DECOR, Rarity.UNCOMMON, price = 500, blurb = "창문."),
        ItemDef("string_lights", "줄 조명", ItemKind.DECOR, Rarity.UNCOMMON, price = 400, blurb = "줄 조명."),
        ItemDef("fish_tank", "어항", ItemKind.DECOR, Rarity.UNCOMMON, price = 700, blurb = "어항."),
        ItemDef("piano", "피아노", ItemKind.DECOR, Rarity.RARE, price = 900, blurb = "피아노."),
        ItemDef("tv", "TV", ItemKind.DECOR, Rarity.UNCOMMON, price = 600, blurb = "TV."),
        ItemDef("desk", "책상", ItemKind.DECOR, Rarity.UNCOMMON, price = 500, blurb = "책상."),
        ItemDef("wardrobe", "옷장", ItemKind.DECOR, Rarity.UNCOMMON, price = 700, blurb = "옷장."),
        ItemDef("bathtub", "욕조", ItemKind.DECOR, Rarity.UNCOMMON, price = 600, blurb = "욕조."),
        ItemDef("cat_tower", "캣타워", ItemKind.DECOR, Rarity.RARE, price = 800, blurb = "캣타워."),
        ItemDef("food_bowl", "밥그릇", ItemKind.DECOR, Rarity.COMMON, price = 200, blurb = "밥그릇."),
        ItemDef("wall_clock", "벽시계", ItemKind.DECOR, Rarity.COMMON, price = 300, blurb = "벽시계."),
        ItemDef("blanket", "담요", ItemKind.DECOR, Rarity.COMMON, price = 250, blurb = "담요."),
        ItemDef("sofa", "미니 소파", ItemKind.DECOR, Rarity.UNCOMMON, price = 650, blurb = "미니 소파."),
        ItemDef("cloud_lamp", "구름 조명", ItemKind.LAMP, Rarity.UNCOMMON, price = 450, blurb = "구름 조명."),
        ItemDef("picture_frame", "액자", ItemKind.DECOR, Rarity.COMMON, price = 300, blurb = "액자."),
    ).map { it.withShopPrice() }

    val gifts = listOf(
        ItemDef("seashell", "조개껍데기", ItemKind.GIFT, Rarity.COMMON, sellPrice = 100, blurb = "조개껍데기."),
        ItemDef("clover", "네잎클로버", ItemKind.GIFT, Rarity.COMMON, sellPrice = 150, blurb = "네잎클로버."),
        ItemDef("acorn", "도토리", ItemKind.GIFT, Rarity.RARE, sellPrice = 100, blurb = "도토리."),
        ItemDef("pebble", "조약돌", ItemKind.GIFT, Rarity.UNCOMMON, sellPrice = 50, blurb = "조약돌."),
        ItemDef("feather", "깃털", ItemKind.GIFT, Rarity.COMMON, sellPrice = 100, blurb = "깃털."),
        ItemDef("star_piece", "별조각", ItemKind.GIFT, Rarity.EPIC, sellPrice = 300, blurb = "별조각."),
        ItemDef("mushroom_item", "버섯", ItemKind.GIFT, Rarity.UNCOMMON, sellPrice = 150, blurb = "버섯."),
        ItemDef("letter", "편지", ItemKind.GIFT, Rarity.UNCOMMON, sellPrice = 100, blurb = "편지."),
        ItemDef("maple_leaf", "단풍잎", ItemKind.GIFT, Rarity.COMMON, sellPrice = 100, blurb = "단풍잎."),
        ItemDef("wildflower", "들꽃", ItemKind.GIFT, Rarity.COMMON, sellPrice = 100, blurb = "들꽃."),
    )

    val uniques = listOf(
        ItemDef(
            id = "moon_bell",
            name = "달종",
            kind = ItemKind.GIFT,
            rarity = Rarity.RARE,
            price = 0,
            sellPrice = EconomyConfig.rareResell.getValue("moon_bell"),
            blurb = "상점에 없는 달종.",
            artId = "clover",
        ),
        ItemDef(
            id = "glass_marble",
            name = "유리구슬",
            kind = ItemKind.GIFT,
            rarity = Rarity.RARE,
            price = 0,
            sellPrice = EconomyConfig.rareResell.getValue("glass_marble"),
            blurb = "상점에 없는 유리구슬.",
            artId = "pebble",
        ),
        ItemDef(
            id = "dawn_feather",
            name = "새벽깃",
            kind = ItemKind.GIFT,
            rarity = Rarity.EPIC,
            price = 0,
            sellPrice = EconomyConfig.legendaryResell.getValue("dawn_feather"),
            blurb = "상점에 없는 새벽깃.",
            artId = "feather",
        ),
        ItemDef(
            id = "comet_shard",
            name = "혜성조각",
            kind = ItemKind.GIFT,
            rarity = Rarity.EPIC,
            price = 0,
            sellPrice = EconomyConfig.legendaryResell.getValue("comet_shard"),
            blurb = "상점에 없는 혜성조각.",
            artId = "star_piece",
        ),
    )

    val companions: List<ItemDef> = SpeciesCatalog.all.map { species ->
        ItemDef(species.id, species.name, ItemKind.COMPANION, species.rarity, price = species.price, blurb = species.blurb)
    }

    val goods: List<ItemDef> = listOf(cookie, rice_ball, milk_bottle, strawberry_cake) + outfits + furniture + gifts
    val all: List<ItemDef> = goods + uniques + companions
    val uniqueIds: Set<String> = uniques.map { it.id }.toSet()

    fun byId(id: String?): ItemDef? = all.find { it.id == id }
    fun foods(): List<ItemDef> = listOf(cookie, rice_ball, milk_bottle, strawberry_cake)
    fun rotating(): List<ItemDef> = foods()
    fun slotOf(kind: ItemKind): DecorSlot? = when (kind) {
        ItemKind.WALL -> DecorSlot.WALL
        ItemKind.FLOOR -> DecorSlot.FLOOR
        ItemKind.BED -> DecorSlot.BED
        ItemKind.RUG -> DecorSlot.RUG
        ItemKind.LAMP -> DecorSlot.LAMP
        else -> null
    }
}

fun wallHex(id: String?): String = when (roomTheme(id)) {
    "mint" -> "#D7F3EC"
    "starry" -> "#2C3A5A"
    else -> "#F6E2C8"
}

fun floorHex(id: String?): String = when (roomTheme(id)) {
    "mint" -> "#C9E7E0"
    "starry" -> "#3A4A68"
    else -> "#E7C8A4"
}

fun roomTheme(id: String?): String = when (id) {
    "mint", "wall_mint", "floor_tile" -> "mint"
    "starry", "wall_night" -> "starry"
    else -> "wood"
}

fun isDarkWall(id: String?): Boolean = roomTheme(id) == "starry"