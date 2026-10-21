package app.mongle.model

import kotlin.random.Random

enum class MochiEar {
    NONE, CAT, BUNNY, BEAR, ROUND, FROG, TUFT, RIBBON, STAR, GHOST,
    SPROUT, PENGUIN, HAMSTER, FOX, SEAL, PANDA, DRAGON, EGG, CLOUD, HOOD,
}

data class SpeciesLook(
    val body: Long,
    val ear: MochiEar,
    val earColor: Long = body,
    val accent: Long = 0xFFE98B84,
)

data class SpeciesDef(
    val id: String,
    val name: String,
    val rarity: Rarity,
    val price: Int,
    val blurb: String,
    val look: SpeciesLook,
)

object SpeciesCatalog {
    val all: List<SpeciesDef> = listOf(
        sp("sp_cat", "고양이", Rarity.COMMON, 220, 0xFFFFD7C2, MochiEar.CAT, blurb = "작은 귀가 먼저 인사해요."),
        sp("sp_bunny", "토끼", Rarity.COMMON, 220, 0xFFD9F3EA, MochiEar.BUNNY, blurb = "귀가 하늘로 뻗어요."),
        sp("sp_bear", "곰", Rarity.COMMON, 240, 0xFFFFE7A8, MochiEar.BEAR, blurb = "동글한 귀의 곰."),
        sp("sp_frog", "개구리", Rarity.UNCOMMON, 480, 0xFFD7F3E4, MochiEar.FROG, earTint = 0xFFB7D7A8, blurb = "두건을 눌러쓴 개구리."),
        sp("sp_chick", "병아리", Rarity.COMMON, 200, 0xFFFFE0C2, MochiEar.TUFT, accent = 0xFFF0B429, blurb = "노란 부리."),
        sp("sp_dumpling", "만두", Rarity.COMMON, 260, 0xFFFFF3D0, MochiEar.RIBBON, blurb = "주름이 귀여운 만두."),
        sp("sp_berry", "딸기", Rarity.UNCOMMON, 420, 0xFFE7D4F2, MochiEar.NONE, accent = 0xFFE25B5B, blurb = "머리 위에 딸기."),
        sp("sp_matcha", "녹차", Rarity.COMMON, 240, 0xFFD5F0E4, MochiEar.NONE, blurb = "말차색 볼."),
        sp("sp_peach", "복숭아", Rarity.COMMON, 0, 0xFFFFE3D2, MochiEar.NONE, blurb = "처음 만나는 복숭아 몽이."),
        sp("sp_cloud", "구름", Rarity.UNCOMMON, 460, 0xFFE4DDF8, MochiEar.CLOUD, blurb = "솜사탕 구름."),
        sp("sp_star", "별", Rarity.RARE, 880, 0xFFFFE7A3, MochiEar.STAR, blurb = "별 모양 몸."),
        sp("sp_ghost", "유령", Rarity.UNCOMMON, 500, 0xFFE6D9F6, MochiEar.GHOST, blurb = "밑단이 물결쳐요."),
        sp("sp_sprout", "새싹", Rarity.COMMON, 260, 0xFFD9F3D8, MochiEar.SPROUT, accent = 0xFF7DBE6A, blurb = "머리에서 잎이 나요."),
        sp("sp_penguin", "펭귄", Rarity.UNCOMMON, 520, 0xFFF7F4EF, MochiEar.PENGUIN, earTint = 0xFF3E4654, blurb = "검정 후드."),
        sp("sp_hamster", "햄스터", Rarity.COMMON, 240, 0xFFFFE6C9, MochiEar.HAMSTER, earTint = 0xFFE7B98A, blurb = "볼이 빵빵해요."),
        sp("sp_fox", "여우", Rarity.UNCOMMON, 540, 0xFFFFF1E4, MochiEar.FOX, earTint = 0xFFE9924A, blurb = "뾰족한 귀."),
        sp("sp_seal", "물개", Rarity.COMMON, 260, 0xFFE4D9F4, MochiEar.SEAL, blurb = "매끈한 물개."),
        sp("sp_panda", "판다", Rarity.RARE, 920, 0xFFFFF7EF, MochiEar.PANDA, earTint = 0xFF2C2C2C, blurb = "눈두덩이 까매요."),
        sp("sp_dragon", "아기용", Rarity.EPIC, 1600, 0xFFD5F4EE, MochiEar.DRAGON, accent = 0xFFE7C56B, blurb = "작은 날개."),
        sp("sp_egg", "알", Rarity.COMMON, 0, 0xFFFFF4EA, MochiEar.EGG, blurb = "아직 껍질 안."),
        sp("sp_milk", "우유", Rarity.COMMON, 200, 0xFFFFFBF6, MochiEar.NONE, blurb = "하얀 우유빛."),
        sp("sp_blueberry", "블루베리", Rarity.UNCOMMON, 440, 0xFFD9DDF8, MochiEar.NONE, accent = 0xFF6E78C9, blurb = "보라빛 볼."),
        sp("sp_lemon", "레몬", Rarity.COMMON, 220, 0xFFFFF3B0, MochiEar.TUFT, accent = 0xFFE2B423, blurb = "상큼한 노랑."),
        sp("sp_redbean", "팥", Rarity.COMMON, 240, 0xFFF6D2C8, MochiEar.ROUND, blurb = "달콤한 팥."),
        sp("sp_injeolmi", "인절미", Rarity.UNCOMMON, 460, 0xFFFFF6D8, MochiEar.NONE, accent = 0xFFD8C27A, blurb = "콩고물이 묻었어요."),
        sp("sp_donut", "도넛", Rarity.UNCOMMON, 480, 0xFFFFE0C8, MochiEar.NONE, accent = 0xFFE7A0B4, blurb = "분홍 아이싱."),
        sp("sp_pudding", "푸딩", Rarity.COMMON, 260, 0xFFFFE8B8, MochiEar.TUFT, blurb = "흔들흔들."),
        sp("sp_marsh", "마시멜로", Rarity.COMMON, 220, 0xFFFFF1F4, MochiEar.NONE, blurb = "폭신한 하양."),
        sp("sp_melon", "수박", Rarity.UNCOMMON, 500, 0xFFDDF5D8, MochiEar.NONE, accent = 0xFFE25B5B, blurb = "초록 줄무늬."),
        sp("sp_honey", "꿀", Rarity.UNCOMMON, 460, 0xFFFFE7A8, MochiEar.TUFT, accent = 0xFFE2A020, blurb = "꿀방울."),
        sp("sp_squirrel", "다람쥐", Rarity.COMMON, 280, 0xFFE7B98A, MochiEar.ROUND, earTint = 0xFFC9844A, blurb = "도토리를 좋아해요."),
        sp("sp_sheep", "양", Rarity.COMMON, 260, 0xFFFFF7EF, MochiEar.CLOUD, blurb = "양털 구름."),
        sp("sp_pig", "아기돼지", Rarity.COMMON, 240, 0xFFFFD6DE, MochiEar.ROUND, earTint = 0xFFFFB7C5, blurb = "분홍 코."),
        sp("sp_lion", "사자", Rarity.RARE, 960, 0xFFFFE3B0, MochiEar.HOOD, earTint = 0xFFE7A04A, blurb = "갈기가 후드처럼."),
        sp("sp_tiger", "호랑이", Rarity.RARE, 980, 0xFFFFE6C2, MochiEar.CAT, earTint = 0xFFE7A04A, accent = 0xFF5C4A3A, blurb = "작은 줄무늬."),
        sp("sp_deer", "사슴", Rarity.UNCOMMON, 560, 0xFFFFE6C9, MochiEar.TUFT, accent = 0xFFE7C56B, blurb = "작은 뿔."),
        sp("sp_koala", "코알라", Rarity.UNCOMMON, 520, 0xFFE7E0DA, MochiEar.ROUND, earTint = 0xFFB7A99E, blurb = "큰 귀."),
        sp("sp_raccoon", "너구리", Rarity.UNCOMMON, 500, 0xFFE7D3B8, MochiEar.ROUND, accent = 0xFF5C4A3A, blurb = "눈가 마스크."),
        sp("sp_snow", "눈송이", Rarity.RARE, 900, 0xFFF4F7FF, MochiEar.TUFT, accent = 0xFFB7D4F2, blurb = "차가운 볼."),
        sp("sp_moon", "달", Rarity.RARE, 940, 0xFFFFF6D8, MochiEar.NONE, accent = 0xFFE7C56B, blurb = "초승달."),
        sp("sp_rainbow", "무지개", Rarity.EPIC, 1700, 0xFFFFF1E8, MochiEar.TUFT, accent = 0xFF8FCBB6, blurb = "은은한 빛."),
        sp("sp_cherry", "벚꽃", Rarity.UNCOMMON, 480, 0xFFFFE0EA, MochiEar.TUFT, accent = 0xFFF2A0B8, blurb = "꽃잎이 앉아요."),
        sp("sp_pumpkin", "호박", Rarity.UNCOMMON, 460, 0xFFFFD09A, MochiEar.TUFT, accent = 0xFF7DBE6A, blurb = "동그란 호박."),
        sp("sp_mushroom", "버섯", Rarity.COMMON, 280, 0xFFFFF3E8, MochiEar.HOOD, earTint = 0xFFE25B5B, blurb = "빨간 갓."),
        sp("sp_acorn", "도토리", Rarity.COMMON, 240, 0xFFE7C29A, MochiEar.HOOD, earTint = 0xFF8A5A32, blurb = "모자 같은 깍지."),
        sp("sp_sea", "바다", Rarity.UNCOMMON, 500, 0xFFD5F0F4, MochiEar.NONE, accent = 0xFF7EB6D6, blurb = "파도색."),
        sp("sp_lavender", "라벤더", Rarity.UNCOMMON, 480, 0xFFE6D9F6, MochiEar.TUFT, accent = 0xFFB7A0D8, blurb = "꽃향기."),
        sp("sp_cheese", "치즈", Rarity.COMMON, 260, 0xFFFFE9A8, MochiEar.NONE, accent = 0xFFF0B429, blurb = "구멍 하나."),
        sp("sp_cookie", "쿠키", Rarity.COMMON, 220, 0xFFE7C29A, MochiEar.NONE, accent = 0xFF8A5A32, blurb = "초코칩."),
        sp("sp_night", "밤", Rarity.EPIC, 1800, 0xFF3C4A68, MochiEar.TUFT, earTint = 0xFF2C3A5A, accent = 0xFFFFE7A3, blurb = "별이 박힌 밤."),
    )

    fun byId(id: String?): SpeciesDef = all.find { it.id == id } ?: all.first { it.id == "sp_peach" }

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

    private fun sp(
        id: String,
        name: String,
        rarity: Rarity,
        price: Int,
        body: Long,
        ear: MochiEar,
        earTint: Long? = null,
        accent: Long = 0xFFE98B84,
        blurb: String,
    ): SpeciesDef {
        return SpeciesDef(id, name, rarity, price, blurb, SpeciesLook(body, ear, earTint ?: body, accent))
    }
}
