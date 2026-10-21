package app.mongle.model

object Catalog {
    val foodBasic = food("food_basic", "풀잎 쿠키", 12, ItemMotif.COOKIE, hunger = 28, happiness = 2, xp = 6, blurb = "바삭하고 달콤해요. 매일 먹어도 질리지 않아요.")
    val foodCandy = food(
        "food_candy",
        "별사탕",
        28,
        ItemMotif.CANDY,
        rarity = Rarity.UNCOMMON,
        hunger = 42,
        happiness = 8,
        xp = 10,
        blurb = "먹으면 눈이 반짝여요.",
    )
    val foodPeach = food("food_peach", "복숭아 젤리", 18, ItemMotif.PEACH, hunger = 24, happiness = 4, xp = 4, blurb = "말랑한 복숭아 맛.")
    val foodDumpling = food("food_dumpling", "만두 간식", 22, ItemMotif.DUMPLING, hunger = 30, happiness = 5, xp = 6, blurb = "한 입 만두.")

    val outfits = listOf(
        wear("ribbon_berry", "리본", 150, ItemMotif.RIBBON, blurb = "분홍 리본."),
        wear("beret", "베레모", 200, ItemMotif.BERET, blurb = "동그란 베레모."),
        wear("scarf_cloud", "니트 목도리", 250, ItemMotif.SCARF, Rarity.UNCOMMON, "포근한 목도리."),
        wear("raincoat", "우비", 300, ItemMotif.COAT, blurb = "비 오는 날."),
        wear("crown", "왕관", 500, ItemMotif.CROWN, Rarity.RARE, "작은 왕관."),
        wear("sunglasses", "선글라스", 200, ItemMotif.GLASSES, blurb = "까만 선글라스."),
        wear("hat_sprout", "새싹 모자", 350, ItemMotif.SPROUT, Rarity.UNCOMMON, "머리 위에 작은 잎이 자라요."),
        wear("hood_yellow", "후드", 400, ItemMotif.HOOD, Rarity.UNCOMMON, "노란 후드.", fresh = true),
    )

    val furniture = listOf(
        place("wall_peach", "복숭아 벽", ItemKind.WALL, 20, ItemMotif.WALL, blurb = "따뜻한 오후 같은 벽."),
        place("wall_mint", "민트 벽", ItemKind.WALL, 20, ItemMotif.WALL, blurb = "시원한 민트색."),
        place("wall_night", "밤하늘 벽", ItemKind.WALL, 48, ItemMotif.WALL, Rarity.UNCOMMON, "작은 별이 떠 있어요."),
        place("wall_butter", "버터 벽", ItemKind.WALL, 24, ItemMotif.WALL, blurb = "노란 오후."),
        place("floor_wood", "나무 바닥", ItemKind.FLOOR, 16, ItemMotif.FLOOR, blurb = "맨발로 걷기 좋아요."),
        place("floor_tile", "타일 바닥", ItemKind.FLOOR, 16, ItemMotif.FLOOR, blurb = "반듯한 민트 타일."),
        place("floor_cream", "크림 바닥", ItemKind.FLOOR, 18, ItemMotif.FLOOR, blurb = "부드러운 크림색."),
        place("rug_leaf", "잎사귀 러그", ItemKind.RUG, 30, ItemMotif.RUG, blurb = "초록 잎 한 장."),
        place("rug_star", "별 러그", ItemKind.RUG, 36, ItemMotif.RUG, blurb = "별 모양이 수놓여 있어요."),
        place("lamp_moon", "달 조명", ItemKind.LAMP, 46, ItemMotif.LAMP, Rarity.UNCOMMON, "밤에도 은은해요."),
        place("bed_cloud", "쿠션 침대", ItemKind.BED, 800, ItemMotif.PILLOW, Rarity.RARE, "폭신한 쿠션 침대."),
        place("bed_wood", "나무 침대", ItemKind.BED, 800, ItemMotif.BED, Rarity.RARE, "나무 침대."),
        place("shelf_books", "책장", ItemKind.DECOR, 600, ItemMotif.SHELF, blurb = "책이 꽂혀 있어요."),
        place("table_round", "둥글 테이블", ItemKind.DECOR, 550, ItemMotif.TABLE, blurb = "낮은 원형 테이블."),
        place("lamp_sun", "스탠드 램프", ItemKind.LAMP, 450, ItemMotif.LAMP, blurb = "방 안이 환해져요."),
        place("chair_round", "둥글 의자", ItemKind.DECOR, 500, ItemMotif.CHAIR, blurb = "둥근 팔걸이 의자."),
        place("rug_mint", "민트 러그", ItemKind.RUG, 300, ItemMotif.RUG, blurb = "민트색 러그."),
        place("plant_pot", "화분", ItemKind.DECOR, 300, ItemMotif.PLANT, blurb = "작은 화분."),
        place("window_wood", "창문", ItemKind.DECOR, 500, ItemMotif.WINDOW, blurb = "나무 창문."),
        place("lights_string", "전구", ItemKind.DECOR, 400, ItemMotif.LIGHTS, blurb = "따뜻한 전구."),
        place("tank_fish", "어항", ItemKind.DECOR, 700, ItemMotif.TANK, Rarity.RARE, "작은 물고기."),
        place("piano", "피아노", ItemKind.DECOR, 900, ItemMotif.PIANO, Rarity.EPIC, "작은 피아노."),
        place("tv_retro", "TV", ItemKind.DECOR, 600, ItemMotif.TV, blurb = "레트로 TV."),
        place("desk_wood", "책상", ItemKind.DECOR, 500, ItemMotif.DESK, blurb = "나무 책상."),
        place("closet_wood", "옷장", ItemKind.DECOR, 700, ItemMotif.CLOSET, blurb = "옷장."),
        place("tub_bath", "욕조", ItemKind.DECOR, 600, ItemMotif.TUB, blurb = "하얀 욕조."),
        place("tower_cat", "캣타워", ItemKind.DECOR, 800, ItemMotif.TOWER, Rarity.RARE, "캣타워."),
        place("bowl_food", "밥그릇", ItemKind.DECOR, 200, ItemMotif.BOWL, blurb = "밥그릇."),
        place("clock_wall", "벽시계", ItemKind.DECOR, 300, ItemMotif.CLOCK, blurb = "둥근 시계."),
        place("blanket_soft", "담요", ItemKind.DECOR, 250, ItemMotif.BLANKET, blurb = "접어 둔 담요."),
    )

    val gifts = listOf(
        gift("gift_shell", "조개", Rarity.COMMON, 100, ItemMotif.SHELL, "파도 소리를 담고 있어요."),
        gift("gift_clover", "클로버", Rarity.COMMON, 150, ItemMotif.CLOVER, "오늘은 운이 좋아요."),
        gift("gift_pebble", "돌멩이", Rarity.UNCOMMON, 50, ItemMotif.PEBBLE, "매끈한 돌."),
        gift("gift_letter", "편지", Rarity.UNCOMMON, 100, ItemMotif.NOTE, "누군가 남긴 응원."),
        gift("gift_acorn", "도토리", Rarity.RARE, 100, ItemMotif.ACORN, "무게가 묵직해요."),
        gift("gift_star", "별 조각", Rarity.EPIC, 300, ItemMotif.STAR, "손바닥 위에서 빛나요."),
        gift("gift_feather", "깃털", Rarity.COMMON, 100, ItemMotif.FEATHER, "가벼운 깃털."),
        gift("gift_mushroom", "버섯", Rarity.UNCOMMON, 150, ItemMotif.MUSHROOM, "빨간 버섯."),
    )

    val companions: List<ItemDef> = SpeciesCatalog.all.map { species ->
        ItemDef(
            id = species.id,
            name = species.name,
            kind = ItemKind.COMPANION,
            rarity = species.rarity,
            price = species.price,
            blurb = species.blurb,
        )
    }

    /** 먹이, 옷, 가구, 선물. 친구(종)는 따로 셉니다. */
    val goods: List<ItemDef> = listOf(foodBasic, foodCandy, foodPeach, foodDumpling) + outfits + furniture + gifts

    val all: List<ItemDef> = goods + companions

    fun byId(id: String?): ItemDef? = all.find { it.id == id }

    fun foods(): List<ItemDef> = listOf(foodBasic, foodCandy, foodPeach, foodDumpling)

    /**
     * 위젯 '오늘의 상점'. 80코인 이하만 올려서, 가진 단일 아이템을 모두 건너뛰어도
     * 겹쳐 사는 먹이가 남게 한다. 시트 가격의 가구와 옷은 방 그리드에서 산다.
     */
    fun rotating(): List<ItemDef> = listOf(foodCandy) + (outfits + furniture).filter { it.price in 1..80 }

    fun slotOf(kind: ItemKind): DecorSlot? = when (kind) {
        ItemKind.WALL -> DecorSlot.WALL
        ItemKind.FLOOR -> DecorSlot.FLOOR
        ItemKind.BED -> DecorSlot.BED
        ItemKind.RUG -> DecorSlot.RUG
        ItemKind.LAMP -> DecorSlot.LAMP
        else -> null
    }

    private fun food(
        id: String,
        name: String,
        price: Int,
        motif: ItemMotif,
        rarity: Rarity = Rarity.COMMON,
        hunger: Int,
        happiness: Int,
        xp: Int,
        blurb: String,
    ) = ItemDef(id, name, ItemKind.FOOD, rarity, price, hunger = hunger, happiness = happiness, xp = xp, stackable = true, blurb = blurb, motif = motif)

    private fun wear(
        id: String,
        name: String,
        price: Int,
        motif: ItemMotif,
        rarity: Rarity = Rarity.COMMON,
        blurb: String,
        fresh: Boolean = false,
    ) = ItemDef(id, name, ItemKind.OUTFIT, rarity, price, blurb = blurb, motif = motif, fresh = fresh)

    private fun place(
        id: String,
        name: String,
        kind: ItemKind,
        price: Int,
        motif: ItemMotif,
        rarity: Rarity = Rarity.COMMON,
        blurb: String,
    ) = ItemDef(id, name, kind, rarity, price, blurb = blurb, motif = motif)

    private fun gift(
        id: String,
        name: String,
        rarity: Rarity,
        sellPrice: Int,
        motif: ItemMotif,
        blurb: String,
    ) = ItemDef(id, name, ItemKind.GIFT, rarity, sellPrice = sellPrice, blurb = blurb, motif = motif)
}

fun wallHex(id: String?): String = when (id) {
    "wall_mint" -> "#D7F3EC"
    "wall_night" -> "#2C3A5A"
    "wall_peach" -> "#FFE3D6"
    "wall_butter" -> "#FFF1C9"
    else -> "#FFF6EE"
}

fun floorHex(id: String?): String = when (id) {
    "floor_tile" -> "#C9E7E0"
    "floor_wood" -> "#E7C8A4"
    "floor_cream" -> "#F6E6D4"
    else -> "#F3D7B5"
}

fun isDarkWall(id: String?): Boolean = id == "wall_night"
