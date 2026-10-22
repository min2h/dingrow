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

    /** 50/100 코인 저가 아이템 (상점). */
    val cheap = listOf(
        ItemDef("mini_plant", "미니 다육이", ItemKind.DECOR, Rarity.COMMON, price = EconomyConfig.cheapPrices.getValue("mini_plant"), blurb = "미니 다육이."),
        ItemDef("candle", "캔들", ItemKind.DECOR, Rarity.COMMON, price = EconomyConfig.cheapPrices.getValue("candle"), blurb = "캔들."),
        ItemDef("sticker_sheet", "스티커", ItemKind.DECOR, Rarity.COMMON, price = EconomyConfig.cheapPrices.getValue("sticker_sheet"), blurb = "스티커."),
        ItemDef("mug", "하트 머그", ItemKind.DECOR, Rarity.COMMON, price = EconomyConfig.cheapPrices.getValue("mug"), blurb = "하트 머그."),
        ItemDef("ribbon_garland", "리본 가랜드", ItemKind.DECOR, Rarity.COMMON, price = EconomyConfig.cheapPrices.getValue("ribbon_garland"), blurb = "리본 가랜드."),
        ItemDef("cozy_socks", "줄무늬 양말", ItemKind.OUTFIT, Rarity.COMMON, price = EconomyConfig.cheapPrices.getValue("cozy_socks"), blurb = "줄무늬 양말."),
        ItemDef("floor_cushion", "방석 쿠션", ItemKind.DECOR, Rarity.COMMON, price = EconomyConfig.cheapPrices.getValue("floor_cushion"), blurb = "방석 쿠션."),
        ItemDef("round_mint_rug", "동글 러그", ItemKind.RUG, Rarity.COMMON, price = EconomyConfig.cheapPrices.getValue("round_mint_rug"), blurb = "동글 러그."),
        ItemDef("sun_poster", "해님 포스터", ItemKind.DECOR, Rarity.COMMON, price = EconomyConfig.cheapPrices.getValue("sun_poster"), blurb = "해님 포스터."),
        ItemDef("snack_bowl", "과자 그릇", ItemKind.DECOR, Rarity.COMMON, price = EconomyConfig.cheapPrices.getValue("snack_bowl"), blurb = "과자 그릇."),
    )

    /** 300–600 코인 새 예쁜 아이템 (상점, 계절 아이템 포함). */
    val pretty = listOf(
        ItemDef("canopy_bed", "캐노피 침대", ItemKind.BED, Rarity.UNCOMMON, price = EconomyConfig.prettyPrices.getValue("canopy_bed"), blurb = "캐노피 침대."),
        ItemDef("velvet_armchair", "벨벳 안락의자", ItemKind.DECOR, Rarity.UNCOMMON, price = EconomyConfig.prettyPrices.getValue("velvet_armchair"), blurb = "벨벳 안락의자."),
        ItemDef("vanity_table", "화장대", ItemKind.DECOR, Rarity.UNCOMMON, price = EconomyConfig.prettyPrices.getValue("vanity_table"), blurb = "화장대."),
        ItemDef("rattan_shelf", "라탄 선반", ItemKind.DECOR, Rarity.UNCOMMON, price = EconomyConfig.prettyPrices.getValue("rattan_shelf"), blurb = "라탄 선반."),
        ItemDef("hanging_chair", "행잉 체어", ItemKind.DECOR, Rarity.UNCOMMON, price = EconomyConfig.prettyPrices.getValue("hanging_chair"), blurb = "행잉 체어."),
        ItemDef("terrarium", "테라리움", ItemKind.DECOR, Rarity.UNCOMMON, price = EconomyConfig.prettyPrices.getValue("terrarium"), blurb = "테라리움."),
        ItemDef("mushroom_lamp", "버섯 조명", ItemKind.LAMP, Rarity.UNCOMMON, price = EconomyConfig.prettyPrices.getValue("mushroom_lamp"), blurb = "버섯 조명."),
        ItemDef("star_projector", "별빛 무드등", ItemKind.LAMP, Rarity.UNCOMMON, price = EconomyConfig.prettyPrices.getValue("star_projector"), blurb = "별빛 무드등."),
        ItemDef("moon_mirror", "달 거울", ItemKind.DECOR, Rarity.UNCOMMON, price = EconomyConfig.prettyPrices.getValue("moon_mirror"), blurb = "달 거울."),
        ItemDef("bunny_plush", "토끼 인형", ItemKind.DECOR, Rarity.UNCOMMON, price = EconomyConfig.prettyPrices.getValue("bunny_plush"), blurb = "토끼 인형."),
        ItemDef("music_box", "오르골", ItemKind.DECOR, Rarity.UNCOMMON, price = EconomyConfig.prettyPrices.getValue("music_box"), blurb = "오르골."),
        ItemDef("desk_globe", "지구본", ItemKind.DECOR, Rarity.UNCOMMON, price = EconomyConfig.prettyPrices.getValue("desk_globe"), blurb = "지구본."),
        ItemDef("aroma_diffuser", "아로마 디퓨저", ItemKind.DECOR, Rarity.UNCOMMON, price = EconomyConfig.prettyPrices.getValue("aroma_diffuser"), blurb = "아로마 디퓨저."),
        ItemDef("wall_stripe", "민트 줄무늬 벽지", ItemKind.WALL, Rarity.UNCOMMON, price = EconomyConfig.prettyPrices.getValue("wall_stripe"), blurb = "민트 줄무늬 벽지."),
        ItemDef("wall_starry", "별밤 벽지", ItemKind.WALL, Rarity.UNCOMMON, price = EconomyConfig.prettyPrices.getValue("wall_starry"), blurb = "별밤 벽지."),
        ItemDef("floor_herringbone", "헤링본 마루", ItemKind.FLOOR, Rarity.UNCOMMON, price = EconomyConfig.prettyPrices.getValue("floor_herringbone"), blurb = "헤링본 마루."),
        ItemDef("floor_mint_tile", "민트 타일 바닥", ItemKind.FLOOR, Rarity.UNCOMMON, price = EconomyConfig.prettyPrices.getValue("floor_mint_tile"), blurb = "민트 타일 바닥."),
        ItemDef("cardigan", "노랑 가디건", ItemKind.OUTFIT, Rarity.UNCOMMON, price = EconomyConfig.prettyPrices.getValue("cardigan"), blurb = "노랑 가디건."),
        ItemDef("beanie", "털모자", ItemKind.OUTFIT, Rarity.UNCOMMON, price = EconomyConfig.prettyPrices.getValue("beanie"), blurb = "털모자."),
        ItemDef("sailor_collar", "세일러 카라", ItemKind.OUTFIT, Rarity.UNCOMMON, price = EconomyConfig.prettyPrices.getValue("sailor_collar"), blurb = "세일러 카라."),
        ItemDef("overalls", "멜빵바지", ItemKind.OUTFIT, Rarity.UNCOMMON, price = EconomyConfig.prettyPrices.getValue("overalls"), blurb = "멜빵바지."),
        ItemDef("pearl_necklace", "진주 목걸이", ItemKind.OUTFIT, Rarity.UNCOMMON, price = EconomyConfig.prettyPrices.getValue("pearl_necklace"), blurb = "진주 목걸이."),
        ItemDef("heart_hairpin", "하트 머리핀", ItemKind.OUTFIT, Rarity.UNCOMMON, price = EconomyConfig.prettyPrices.getValue("heart_hairpin"), blurb = "하트 머리핀."),
        ItemDef("tiny_backpack", "꼬마 배낭", ItemKind.OUTFIT, Rarity.UNCOMMON, price = EconomyConfig.prettyPrices.getValue("tiny_backpack"), blurb = "꼬마 배낭."),
        ItemDef("pumpkin_lantern", "호박 랜턴", ItemKind.DECOR, Rarity.UNCOMMON, price = EconomyConfig.prettyPrices.getValue("pumpkin_lantern"), blurb = "호박 랜턴.", fresh = true),
        ItemDef("snow_globe", "스노우볼", ItemKind.DECOR, Rarity.UNCOMMON, price = EconomyConfig.prettyPrices.getValue("snow_globe"), blurb = "스노우볼.", fresh = true),
        ItemDef("xmas_tree", "크리스마스 트리", ItemKind.DECOR, Rarity.UNCOMMON, price = EconomyConfig.prettyPrices.getValue("xmas_tree"), blurb = "크리스마스 트리.", fresh = true),
        ItemDef("cherry_branch", "벚꽃 가지", ItemKind.DECOR, Rarity.UNCOMMON, price = EconomyConfig.prettyPrices.getValue("cherry_branch"), blurb = "벚꽃 가지.", fresh = true),
    )

    /** 선물 전용(상점 판매 없음). 도감에서는 얻기 전까지 실루엣. */
    val uniques = listOf(
        ItemDef("moon_bell", "달종", ItemKind.GIFT, Rarity.RARE, price = 0, sellPrice = EconomyConfig.rareResell.getValue("moon_bell"), blurb = "달종."),
        ItemDef("glass_marble", "유리구슬", ItemKind.GIFT, Rarity.RARE, price = 0, sellPrice = EconomyConfig.rareResell.getValue("glass_marble"), blurb = "유리구슬."),
        ItemDef("rainbow_shell", "무지개 조개", ItemKind.GIFT, Rarity.RARE, price = 0, sellPrice = EconomyConfig.rareResell.getValue("rainbow_shell"), blurb = "무지개 조개."),
        ItemDef("amber_drop", "호박 물방울", ItemKind.GIFT, Rarity.RARE, price = 0, sellPrice = EconomyConfig.rareResell.getValue("amber_drop"), blurb = "호박 물방울."),
        ItemDef("crystal_leaf", "수정 잎사귀", ItemKind.GIFT, Rarity.RARE, price = 0, sellPrice = EconomyConfig.rareResell.getValue("crystal_leaf"), blurb = "수정 잎사귀."),
        ItemDef("music_charm", "음표 참", ItemKind.GIFT, Rarity.RARE, price = 0, sellPrice = EconomyConfig.rareResell.getValue("music_charm"), blurb = "음표 참."),
        ItemDef("pocket_watch", "회중시계", ItemKind.GIFT, Rarity.RARE, price = 0, sellPrice = EconomyConfig.rareResell.getValue("pocket_watch"), blurb = "회중시계."),
        ItemDef("silver_key", "은빛 열쇠", ItemKind.GIFT, Rarity.RARE, price = 0, sellPrice = EconomyConfig.rareResell.getValue("silver_key"), blurb = "은빛 열쇠."),
        ItemDef("cloud_puff", "구름 솜", ItemKind.GIFT, Rarity.RARE, price = 0, sellPrice = EconomyConfig.rareResell.getValue("cloud_puff"), blurb = "구름 솜."),
        ItemDef("lucky_dice", "행운 주사위", ItemKind.GIFT, Rarity.RARE, price = 0, sellPrice = EconomyConfig.rareResell.getValue("lucky_dice"), blurb = "행운 주사위."),
        ItemDef("rose_heart", "장미수정 하트", ItemKind.GIFT, Rarity.RARE, price = 0, sellPrice = EconomyConfig.rareResell.getValue("rose_heart"), blurb = "장미수정 하트."),
        ItemDef("fairy_lantern", "요정 랜턴", ItemKind.GIFT, Rarity.RARE, price = 0, sellPrice = EconomyConfig.rareResell.getValue("fairy_lantern"), blurb = "요정 랜턴."),
        ItemDef("clover_brooch", "클로버 브로치", ItemKind.GIFT, Rarity.RARE, price = 0, sellPrice = EconomyConfig.rareResell.getValue("clover_brooch"), blurb = "클로버 브로치."),
        ItemDef("sun_amulet", "해님 부적", ItemKind.GIFT, Rarity.RARE, price = 0, sellPrice = EconomyConfig.rareResell.getValue("sun_amulet"), blurb = "해님 부적."),
        ItemDef("snow_crystal", "눈꽃 결정", ItemKind.GIFT, Rarity.RARE, price = 0, sellPrice = EconomyConfig.rareResell.getValue("snow_crystal"), blurb = "눈꽃 결정."),
        ItemDef("unicorn_horn", "유니콘 뿔", ItemKind.GIFT, Rarity.RARE, price = 0, sellPrice = EconomyConfig.rareResell.getValue("unicorn_horn"), blurb = "유니콘 뿔."),
        ItemDef("dawn_feather", "새벽깃", ItemKind.GIFT, Rarity.EPIC, price = 0, sellPrice = EconomyConfig.legendaryResell.getValue("dawn_feather"), blurb = "새벽깃."),
        ItemDef("comet_shard", "혜성조각", ItemKind.GIFT, Rarity.EPIC, price = 0, sellPrice = EconomyConfig.legendaryResell.getValue("comet_shard"), blurb = "혜성조각."),
        ItemDef("phoenix_plume", "불사조 깃털", ItemKind.GIFT, Rarity.EPIC, price = 0, sellPrice = EconomyConfig.legendaryResell.getValue("phoenix_plume"), blurb = "불사조 깃털."),
        ItemDef("aurora_jar", "오로라 병", ItemKind.GIFT, Rarity.EPIC, price = 0, sellPrice = EconomyConfig.legendaryResell.getValue("aurora_jar"), blurb = "오로라 병."),
        ItemDef("golden_acorn", "황금 도토리", ItemKind.GIFT, Rarity.EPIC, price = 0, sellPrice = EconomyConfig.legendaryResell.getValue("golden_acorn"), blurb = "황금 도토리."),
        ItemDef("mermaid_pearl", "인어의 진주", ItemKind.GIFT, Rarity.EPIC, price = 0, sellPrice = EconomyConfig.legendaryResell.getValue("mermaid_pearl"), blurb = "인어의 진주."),
        ItemDef("star_crown", "별 왕관", ItemKind.GIFT, Rarity.EPIC, price = 0, sellPrice = EconomyConfig.legendaryResell.getValue("star_crown"), blurb = "별 왕관."),
        ItemDef("dragon_egg", "용의 알", ItemKind.GIFT, Rarity.EPIC, price = 0, sellPrice = EconomyConfig.legendaryResell.getValue("dragon_egg"), blurb = "용의 알."),
        ItemDef("moon_rabbit", "달토끼 조각상", ItemKind.GIFT, Rarity.EPIC, price = 0, sellPrice = EconomyConfig.legendaryResell.getValue("moon_rabbit"), blurb = "달토끼 조각상."),
        ItemDef("galaxy_marble", "은하 구슬", ItemKind.GIFT, Rarity.EPIC, price = 0, sellPrice = EconomyConfig.legendaryResell.getValue("galaxy_marble"), blurb = "은하 구슬."),
    )

    val companions: List<ItemDef> = SpeciesCatalog.all.map { species ->
        ItemDef(species.id, species.name, ItemKind.COMPANION, species.rarity, price = species.price, blurb = species.blurb)
    }

    val goods: List<ItemDef> = listOf(cookie, rice_ball, milk_bottle, strawberry_cake) + outfits + furniture + cheap + pretty + gifts
    /** Everything sold in the coin shop (gift-only uniques excluded). */
    val shop: List<ItemDef> = listOf(cookie, rice_ball, milk_bottle, strawberry_cake) + outfits + furniture + cheap + pretty
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
    "mint", "wall_mint", "floor_tile", "wall_stripe", "floor_mint_tile" -> "mint"
    "starry", "wall_night", "wall_starry" -> "starry"
    else -> "wood"
}

fun isDarkWall(id: String?): Boolean = roomTheme(id) == "starry"