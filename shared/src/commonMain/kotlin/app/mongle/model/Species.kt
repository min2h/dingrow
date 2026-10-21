package app.mongle.model

import kotlin.random.Random

data class SpeciesDef(
    val id: String,
    val name: String,
    val rarity: Rarity,
    val price: Int,
    val blurb: String,
)

object SpeciesCatalog {
    const val BASE = "base"
    val all: List<SpeciesDef> = listOf(
        SpeciesDef("cat", "고양이", Rarity.COMMON, 240, "고양이 몽글."),
        SpeciesDef("rabbit", "토끼", Rarity.COMMON, 240, "토끼 몽글."),
        SpeciesDef("bear", "곰", Rarity.COMMON, 240, "곰 몽글."),
        SpeciesDef("frog", "개구리", Rarity.UNCOMMON, 480, "개구리 몽글."),
        SpeciesDef("chick", "병아리", Rarity.COMMON, 240, "병아리 몽글."),
        SpeciesDef("mandu", "만두", Rarity.COMMON, 240, "만두 몽글."),
        SpeciesDef("strawberry", "딸기", Rarity.UNCOMMON, 480, "딸기 몽글."),
        SpeciesDef("greentea", "녹차", Rarity.COMMON, 240, "녹차 몽글."),
        SpeciesDef("peach", "복숭아", Rarity.COMMON, 0, "복숭아 몽글."),
        SpeciesDef("cloud", "구름", Rarity.UNCOMMON, 480, "구름 몽글."),
        SpeciesDef("star", "별", Rarity.RARE, 920, "별 몽글."),
        SpeciesDef("ghost", "유령", Rarity.UNCOMMON, 480, "유령 몽글."),
        SpeciesDef("sprout", "새싹", Rarity.COMMON, 240, "새싹 몽글."),
        SpeciesDef("penguin", "펭귄", Rarity.RARE, 920, "펭귄 몽글."),
        SpeciesDef("hamster", "햄스터", Rarity.COMMON, 240, "햄스터 몽글."),
        SpeciesDef("fox", "여우", Rarity.RARE, 920, "여우 몽글."),
        SpeciesDef("seal", "물범", Rarity.UNCOMMON, 480, "물범 몽글."),
        SpeciesDef("panda", "판다", Rarity.EPIC, 1600, "판다 몽글."),
        SpeciesDef("babydragon", "아기용", Rarity.EPIC, 1600, "아기용 몽글."),
        SpeciesDef("egg", "달걀", Rarity.COMMON, 0, "달걀 몽글."),
        SpeciesDef("milk", "우유", Rarity.COMMON, 240, "우유 몽글."),
        SpeciesDef("injeolmi", "인절미", Rarity.UNCOMMON, 480, "인절미 몽글."),
        SpeciesDef("lion", "사자", Rarity.EPIC, 1600, "사자 몽글."),
        SpeciesDef("chestnut", "밤", Rarity.UNCOMMON, 480, "밤 몽글."),
        SpeciesDef("tiger", "호랑이", Rarity.EPIC, 1600, "호랑이 몽글."),
        SpeciesDef("squirrel", "다람쥐", Rarity.COMMON, 240, "다람쥐 몽글."),
        SpeciesDef("duck", "오리", Rarity.COMMON, 240, "오리 몽글."),
        SpeciesDef("sheep", "양", Rarity.COMMON, 240, "양 몽글."),
        SpeciesDef("whale", "고래", Rarity.RARE, 920, "고래 몽글."),
        SpeciesDef("octopus", "문어", Rarity.RARE, 920, "문어 몽글."),
        SpeciesDef("mushroom", "버섯", Rarity.UNCOMMON, 480, "버섯 몽글."),
        SpeciesDef("sunflower", "해바라기", Rarity.UNCOMMON, 480, "해바라기 몽글."),
        SpeciesDef("lemon", "레몬", Rarity.COMMON, 240, "레몬 몽글."),
        SpeciesDef("pudding", "푸딩", Rarity.UNCOMMON, 480, "푸딩 몽글."),
        SpeciesDef("macaron", "마카롱", Rarity.UNCOMMON, 480, "마카롱 몽글."),
        SpeciesDef("donut", "도넛", Rarity.UNCOMMON, 480, "도넛 몽글."),
        SpeciesDef("sweetpotato", "고구마", Rarity.COMMON, 240, "고구마 몽글."),
        SpeciesDef("snowman", "눈사람", Rarity.RARE, 920, "눈사람 몽글."),
        SpeciesDef("rainbow", "무지개", Rarity.EPIC, 1600, "무지개 몽글."),
        SpeciesDef("moon", "달", Rarity.UNCOMMON, 480, "달 몽글."),
        SpeciesDef("owl", "부엉이", Rarity.RARE, 920, "부엉이 몽글."),
        SpeciesDef("koala", "코알라", Rarity.UNCOMMON, 480, "코알라 몽글."),
        SpeciesDef("otter", "수달", Rarity.UNCOMMON, 480, "수달 몽글."),
        SpeciesDef("hen", "암탉", Rarity.COMMON, 240, "암탉 몽글."),
        SpeciesDef("pig", "돼지", Rarity.COMMON, 240, "돼지 몽글."),
        SpeciesDef("puppy", "강아지", Rarity.COMMON, 240, "강아지 몽글."),
        SpeciesDef("shark", "상어", Rarity.RARE, 920, "상어 몽글."),
        SpeciesDef("butter", "버터", Rarity.COMMON, 240, "버터 몽글."),
        SpeciesDef("palette", "팔레트", Rarity.COMMON, 240, "팔레트 몽글."),
        SpeciesDef("barley", "보리", Rarity.COMMON, 240, "보리 몽글."),
    )

    fun byId(id: String?): SpeciesDef =
        all.find { it.id == id } ?: all.first { it.id == "peach" }

    fun unlockFor(seed: Long, owned: Set<String>): String? {
        val pick = all[Random(seed).nextInt(all.size)]
        if (pick.id in owned || pick.price == 0) return null
        val ticket = Random(seed xor 0x5A17).nextInt(100)
        val pass = when (pick.rarity) {
            Rarity.COMMON -> ticket < 75
            Rarity.UNCOMMON -> ticket < 40
            Rarity.RARE -> ticket < 16
            Rarity.EPIC -> ticket < 5
        }
        return if (pass) pick.id else null
    }
}
