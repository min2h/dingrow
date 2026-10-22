package app.mongle.logic

import app.mongle.model.AlarmSound
import app.mongle.model.AlertStyle
import app.mongle.model.AppSettings
import app.mongle.model.Catalog
import app.mongle.model.EconomyConfig
import app.mongle.model.ItemDef
import app.mongle.model.Mood
import app.mongle.model.PetAnimState
import app.mongle.model.Placement
import app.mongle.model.Pose
import app.mongle.model.Rarity
import app.mongle.model.RepeatRule
import app.mongle.model.ScheduleRecord
import app.mongle.model.Stage
import kotlin.random.Random
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

enum class PetGesture { TAP, LONG_PRESS, SWIPE }

enum class PetClip { IDLE, BLINK, HAPPY, EAT, SLEEP, WALK }

data class ScheduleDraft(
    val title: String,
    val timeMillis: Long,
    val repeat: RepeatRule,
    val leadsMinutes: List<Int> = listOf(0),
    val alert: AlertStyle = AlertStyle.SOUND,
    val category: String = "task",
    val weekdays: Int = 0,
)

data class PetReaction(
    val id: String,
    val gesture: PetGesture,
    val line: String,
    val clip: PetClip,
    val tags: Set<String> = emptySet(),
)

data class StoreProduct(
    val id: String,
    val title: String,
    val coins: Int,
    val premium: Boolean,
)

object StoreProducts {
    val coinsSmall = StoreProduct("dingrow_coins_small", "코인 120", 120, false)
    val coinsMedium = StoreProduct("dingrow_coins_medium", "코인 400", 400, false)
    val coinsLarge = StoreProduct("dingrow_coins_large", "코인 1200", 1200, false)
    val premium = StoreProduct("dingrow_premium", "광고 없이", 0, true)
    val all = listOf(coinsSmall, coinsMedium, coinsLarge, premium)
    fun byId(id: String): StoreProduct? = all.find { it.id == id }
}

data class StoreReceipt(
    val productId: String,
    val token: String,
    val ok: Boolean,
    val message: String,
)

interface BillingGateway {
    suspend fun purchase(productId: String): StoreReceipt
    suspend fun restore(): List<StoreReceipt>
}

class FakeBilling : BillingGateway {
    private val receipts = mutableListOf<StoreReceipt>()

    override suspend fun purchase(productId: String): StoreReceipt {
        val known = StoreProducts.byId(productId)
            ?: return StoreReceipt(productId, "", false, "없는 상품이에요")
        val receipt = StoreReceipt(known.id, "fake:${known.id}:${receipts.size}", true, "테스트 구매")
        receipts += receipt
        return receipt
    }

    override suspend fun restore(): List<StoreReceipt> = receipts.toList()
}

object FreeDecor {
    val ids = setOf("cushion_bed", "mint_rug", "floor_lamp")
    fun allowed(id: String, owned: Set<String>): Boolean = id in ids || id in owned
}

fun resellAmount(def: ItemDef): Int = EconomyConfig.resellOf(def)

enum class DropKind { COINS, COMMON, RARE, LEGENDARY }

data class GiftDrop(
    val kind: DropKind,
    val coins: Int = 0,
    val defId: String? = null,
    val sparkle: Boolean = false,
    val reactionId: String = "",
    val reactionLine: String = "",
)

object GiftDrops {
    fun completionHit(seed: Long): Boolean =
        Random(seed).nextInt(1000) < EconomyConfig.completionGiftPerMille

    fun roll(seed: Long, level: Int, dryStreak: Int): GiftDrop {
        val random = Random(seed)
        val kind = if (dryStreak >= EconomyConfig.pityDryGifts) {
            val ticket = random.nextInt(EconomyConfig.weightRare + EconomyConfig.weightLegendary)
            if (ticket < EconomyConfig.weightRare) DropKind.RARE else DropKind.LEGENDARY
        } else {
            val ticket = random.nextInt(EconomyConfig.weightTotal)
            when {
                ticket < EconomyConfig.weightCoins -> DropKind.COINS
                ticket < EconomyConfig.weightCoins + EconomyConfig.weightCommon -> DropKind.COMMON
                ticket < EconomyConfig.weightCoins + EconomyConfig.weightCommon + EconomyConfig.weightRare -> DropKind.RARE
                else -> DropKind.LEGENDARY
            }
        }
        return when (kind) {
            DropKind.COINS -> GiftDrop(kind, coins = coinAmount(random, level))
            DropKind.COMMON -> GiftDrop(kind, defId = pick(random, Catalog.outfits + Catalog.furniture).id)
            DropKind.RARE -> GiftDrop(kind, defId = pick(random, Catalog.uniques.filter { it.rarity == Rarity.RARE }).id)
            DropKind.LEGENDARY -> {
                val item = pick(random, Catalog.uniques.filter { it.rarity == Rarity.EPIC })
                GiftDrop(
                    kind = kind,
                    defId = item.id,
                    sparkle = true,
                    reactionId = EconomyConfig.legendaryReactionId,
                    reactionLine = EconomyConfig.legendaryReactionLine,
                )
            }
        }
    }

    fun nextStreak(current: Int, kind: DropKind): Int =
        if (kind == DropKind.RARE || kind == DropKind.LEGENDARY) 0 else current + 1

    fun label(kind: String): String = when (kind) {
        "COINS" -> "코인"
        "COMMON" -> "흔함"
        "RARE" -> "희귀"
        "LEGENDARY" -> "전설"
        else -> ""
    }

    private fun coinAmount(random: Random, level: Int): Int {
        val span = (EconomyConfig.coinMax - EconomyConfig.coinMin + 1).coerceAtLeast(1)
        val base = EconomyConfig.coinMin + random.nextInt(span)
        val bonus = base * (level - 1).coerceAtLeast(0) * EconomyConfig.coinLevelPercent / 100
        return base + bonus
    }

    private fun pick(random: Random, pool: List<ItemDef>): ItemDef = pool[random.nextInt(pool.size)]
}

object Tips {
    val lines = listOf(
        "일정을 등록하고 완수하면 선물을 얻어요",
        "완료한 일정마다 경험치와 코인이 조금 쌓여요",
        "배가 고프면 산책을 나가지 않아요",
        "외출에서 돌아오면 선물을 보관하거나 팔 수 있어요",
        "선물을 팔면 코인이 되고, 코인으로 가구를 사요",
        "이미 가진 가구는 코인 없이 옮겨 둘 수 있어요",
        "쿠션 침대, 민트 러그, 스탠드 조명은 기본으로 놓을 수 있어요",
        "되팔기는 상점 가격의 약 70%예요",
        "아주 가끔 상점에 없는 희귀 선물이 따라와요",
        "알림을 확인하면 몽이가 밥을 먹어요",
        "배가 부르면 밥 대신 경험치가 조금 올라요",
        "정시, 5분 전, 하루 전처럼 알림을 여러 개 둘 수 있어요",
        "평일만 반복하거나 요일을 직접 고를 수 있어요",
        "조용한 시간은 설정에서 켜요. 기본은 꺼져 있어요",
        "알람 소리, 진동, 무음 중에 고를 수 있어요",
        "정확한 알람을 허용하면 제시간에 더 잘 울려요",
        "홈 화면을 길게 눌러도 위젯을 찾을 수 있어요",
        "위젯을 누르면 몽이가 반응해요",
        "배경화면을 터치해도 몽이가 반응해요",
        "펫을 짧게 누르고, 길게 누르고, 문질러 보세요",
        "기분, 시간, 배고픔에 따라 대사가 달라져요",
        "도감의 친구는 만나면 데려올 수 있어요",
        "오늘의 상품은 하루에 한 번 바뀌어요",
        "같은 일정은 너무 빨리 다시 완료할 수 없어요",
        "반복 일정은 끝낸 뒤 다음 회로 넘어가요",
        "코인이 부족하면 일정을 끝내고 선물을 팔아 보세요",
        "프리미엄은 광고를 끄고, 코인 꾸러미는 상점을 도와요",
        "구매를 복원하면 이 기기의 결제 기록을 다시 반영해요",
        "팁은 설정에서 끌 수 있어요",
        "밤에는 몽이가 졸려 해요",
        "행복이 높으면 방을 더 자주 걸어 다녀요",
        "이름을 부르듯 머리를 톡톡 눌러 주세요",
    )
    fun next(index: Int): String = lines[index.floorMod(lines.size)]
}

private fun Int.floorMod(mod: Int): Int {
    val r = this % mod
    return if (r < 0) r + mod else r
}

object PetReactions {
    val all: List<PetReaction> = listOf(
        line("t01", PetGesture.TAP, "간지러워요", PetClip.HAPPY),
        line("t02", PetGesture.TAP, "또 눌러 주세요", PetClip.HAPPY),
        line("t03", PetGesture.TAP, "헤헤, 간식 생각나요", PetClip.EAT, "hungry"),
        line("t04", PetGesture.TAP, "오늘도 같이 해요", PetClip.WALK),
        line("t05", PetGesture.TAP, "볼이 따뜻해졌어요", PetClip.HAPPY),
        line("t06", PetGesture.TAP, "깜짝이야!", PetClip.BLINK),
        line("t07", PetGesture.TAP, "꼬리가 흔들려요", PetClip.HAPPY),
        line("t08", PetGesture.TAP, "나 여기 있어요", PetClip.IDLE),
        line("t09", PetGesture.TAP, "쓰담쓰담 좋아요", PetClip.HAPPY),
        line("t10", PetGesture.TAP, "눈이 반짝", PetClip.BLINK),
        line("t11", PetGesture.TAP, "배고파요… 농담이에요", PetClip.EAT, "hungry"),
        line("t12", PetGesture.TAP, "일정 하나만 같이 해요", PetClip.WALK),
        line("t13", PetGesture.TAP, "포슬포슬해요", PetClip.IDLE),
        line("t14", PetGesture.TAP, "하품이 나와요", PetClip.SLEEP, "night"),
        line("t15", PetGesture.TAP, "춤출까요?", PetClip.HAPPY),
        line("t16", PetGesture.TAP, "손 흔들어요", PetClip.WALK),
        line("t17", PetGesture.TAP, "심장이 두근", PetClip.HAPPY),
        line("t18", PetGesture.TAP, "별이 떨어졌어요", PetClip.BLINK, "night"),
        line("t19", PetGesture.TAP, "부끄러워요", PetClip.IDLE),
        line("t20", PetGesture.TAP, "안아 주세요", PetClip.HAPPY),
        line("t21", PetGesture.TAP, "오늘은 운이 좋아요", PetClip.HAPPY),
        line("t22", PetGesture.TAP, "물 한 잔 어때요", PetClip.IDLE),
        line("t23", PetGesture.TAP, "나랑 눈 마주쳤어요", PetClip.BLINK),
        line("t24", PetGesture.TAP, "살금살금", PetClip.WALK),
        line("t25", PetGesture.TAP, "팡! 하고 튀었어요", PetClip.HAPPY),
        line("t26", PetGesture.TAP, "기분 최고예요", PetClip.HAPPY),
        line("t27", PetGesture.TAP, "조금 졸려요", PetClip.SLEEP, "night"),
        line("t28", PetGesture.TAP, "해가 좋아요", PetClip.IDLE, "morning"),
        line("t29", PetGesture.TAP, "달이 보고 있어요", PetClip.SLEEP, "night"),
        line("t30", PetGesture.TAP, "레벨업 하고 싶어요", PetClip.HAPPY),
        line("t31", PetGesture.TAP, "쿠키 냄새나요", PetClip.EAT, "hungry"),
        line("t32", PetGesture.TAP, "여기 앉아 있을게요", PetClip.IDLE),
        line("t33", PetGesture.TAP, "또 만져도 돼요", PetClip.HAPPY),
        line("t34", PetGesture.TAP, "사랑이 둥실", PetClip.HAPPY),
        line("l01", PetGesture.LONG_PRESS, "오래 눌러 줘서 고마워요", PetClip.HAPPY),
        line("l02", PetGesture.LONG_PRESS, "안심이 돼요", PetClip.IDLE),
        line("l03", PetGesture.LONG_PRESS, "꼭 안긴 기분이에요", PetClip.HAPPY),
        line("l04", PetGesture.LONG_PRESS, "숨고 싶지 않아요", PetClip.IDLE),
        line("l05", PetGesture.LONG_PRESS, "마음이 느긋해져요", PetClip.SLEEP),
        line("l06", PetGesture.LONG_PRESS, "이대로 잠깐만요", PetClip.IDLE),
        line("l07", PetGesture.LONG_PRESS, "손바닥이 포근해요", PetClip.HAPPY),
        line("l08", PetGesture.LONG_PRESS, "잠이 솔솔", PetClip.SLEEP, "night"),
        line("l09", PetGesture.LONG_PRESS, "힘내는 중이에요", PetClip.WALK),
        line("l10", PetGesture.LONG_PRESS, "오래 있어 줘서 좋아", PetClip.HAPPY),
        line("l11", PetGesture.LONG_PRESS, "꼭 잡고 있을게요", PetClip.IDLE),
        line("l12", PetGesture.LONG_PRESS, "고요해서 좋아요", PetClip.SLEEP),
        line("s01", PetGesture.SWIPE, "간지럼 참기 실패", PetClip.HAPPY),
        line("s02", PetGesture.SWIPE, "까르르", PetClip.HAPPY),
        line("s03", PetGesture.SWIPE, "배가 간지러워요", PetClip.WALK),
        line("s04", PetGesture.SWIPE, "도망갈까 말까", PetClip.WALK),
        line("s05", PetGesture.SWIPE, "더 간지럽혀요", PetClip.HAPPY),
        line("s06", PetGesture.SWIPE, "발로 살짝 밀어요", PetClip.WALK),
        line("s07", PetGesture.SWIPE, "웃음이 새요", PetClip.HAPPY),
        line("s08", PetGesture.SWIPE, "스르륵, 기분 좋아요", PetClip.IDLE),
        line("s09", PetGesture.SWIPE, "털이 곤두섰다가 풀렸어요", PetClip.BLINK),
        line("s10", PetGesture.SWIPE, "한 번 더 스윽", PetClip.WALK),
        line("s11", PetGesture.SWIPE, "간지럼 공격이에요", PetClip.HAPPY),
        line("s12", PetGesture.SWIPE, "데굴데굴", PetClip.WALK),
    )

    fun forGesture(gesture: PetGesture): List<PetReaction> = all.filter { it.gesture == gesture }

    fun pick(gesture: PetGesture, mood: Mood, hour: Int, hunger: Int, level: Int, salt: Long): PetReaction {
        val pool = forGesture(gesture)
        val want = buildSet {
            if (hunger < 40 || mood == Mood.HUNGRY) add("hungry")
            if (hour >= 21 || hour < 5 || mood == Mood.SLEEPY) add("night")
            if (hour in 5..10) add("morning")
            if (level >= 3) add("high")
        }
        val biased = pool.filter { it.tags.any { tag -> tag in want } }
        val use = if (biased.isNotEmpty() && salt % 3L != 0L) biased else pool
        val index = (salt and Long.MAX_VALUE).mod(use.size.toLong()).toInt()
        return use[index]
    }

    private fun line(
        id: String,
        gesture: PetGesture,
        text: String,
        clip: PetClip,
        vararg tags: String,
    ) = PetReaction(id, gesture, text, clip, tags.toSet())
}

object NoticePlan {
    fun triggers(
        timeMillis: Long,
        leads: List<Int>,
        now: Long,
        quietStart: Int,
        quietEnd: Int,
        tz: String,
    ): List<Long> {
        val minutes = leads.ifEmpty { listOf(0) }.distinct()
        return minutes
            .map { timeMillis - it * 60_000L }
            .map { shiftQuiet(it, quietStart, quietEnd, tz) }
            .distinct()
            .filter { it > now }
            .sorted()
    }

    fun shiftQuiet(trigger: Long, startMinute: Int, endMinute: Int, tz: String): Long {
        if (startMinute < 0 || endMinute < 0 || startMinute == endMinute) return trigger
        val zone = ScheduleRules.zoneOrUtc(tz)
        if (!inQuiet(trigger, startMinute, endMinute, zone)) return trigger
        return quietEnd(trigger, endMinute, zone)
    }

    private fun inQuiet(millis: Long, start: Int, end: Int, zone: TimeZone): Boolean {
        val minute = minuteOf(millis, zone)
        return if (start < end) minute in start until end else minute >= start || minute < end
    }

    private fun quietEnd(millis: Long, endMinute: Int, zone: TimeZone): Long {
        val local = Instant.fromEpochMilliseconds(millis).toLocalDateTime(zone)
        val minute = local.hour * 60 + local.minute
        val date = if (minute >= endMinute) local.date.plus(1, DateTimeUnit.DAY) else local.date
        val hour = endMinute / 60
        val min = endMinute % 60
        return LocalDateTime(date.year, date.monthNumber, date.dayOfMonth, hour, min).toInstant(zone).toEpochMilliseconds()
    }

    private fun minuteOf(millis: Long, zone: TimeZone): Int {
        val local = Instant.fromEpochMilliseconds(millis).toLocalDateTime(zone)
        return local.hour * 60 + local.minute
    }
}

object PetAnimMachine {
    fun resolve(
        stage: Stage,
        pose: Pose,
        energy: Int,
        status: String,
        anim: PetAnimState,
        now: Long,
    ): PetClip {
        if (anim.until > now) {
            return runCatching { PetClip.valueOf(anim.clip.uppercase()) }.getOrDefault(PetClip.IDLE)
        }
        if (stage == Stage.EGG || pose == Pose.EGG) return PetClip.IDLE
        if ("먹였" in status) return PetClip.EAT
        if (pose == Pose.SLEEP || energy <= 15) return PetClip.SLEEP
        if (pose == Pose.WALK || pose == Pose.OUT) return PetClip.WALK
        if (pose == Pose.HAPPY || "완료" in status || "자랐" in status) return PetClip.HAPPY
        return PetClip.IDLE
    }

    fun trigger(anim: PetAnimState, now: Long): String = if (anim.until > now) anim.trigger else "none"
}

object MetaCodec {
    fun settings(value: AppSettings): String = listOf(
        if (value.tipsEnabled) "1" else "0",
        value.quietStartMinute.toString(),
        value.quietEndMinute.toString(),
        value.defaultLeadMinutes.toString(),
        if (value.soundEnabled) "1" else "0",
        if (value.vibrateEnabled) "1" else "0",
        value.alarmSound.name,
    ).joinToString("|")

    fun parseSettings(raw: String?): AppSettings {
        if (raw.isNullOrBlank()) return AppSettings()
        val parts = raw.split("|")
        if (parts.size < 7) return AppSettings()
        return AppSettings(
            tipsEnabled = parts[0] == "1",
            quietStartMinute = parts[1].toIntOrNull() ?: -1,
            quietEndMinute = parts[2].toIntOrNull() ?: -1,
            defaultLeadMinutes = parts[3].toIntOrNull() ?: 0,
            soundEnabled = parts[4] != "0",
            vibrateEnabled = parts[5] != "0",
            alarmSound = runCatching { AlarmSound.valueOf(parts[6]) }.getOrDefault(AlarmSound.SOFT),
        )
    }

    fun placements(list: List<Placement>): String =
        list.joinToString(";") { "${it.defId}@${it.x}@${it.y}@${it.rotation}" }

    fun parsePlacements(raw: String?): List<Placement> =
        raw.orEmpty().split(";").mapNotNull { part ->
            if (part.isBlank()) return@mapNotNull null
            val bits = part.split("@")
            if (bits.size < 4 || bits[0].isBlank()) return@mapNotNull null
            Placement(
                defId = bits[0],
                x = bits[1].toIntOrNull() ?: 50,
                y = bits[2].toIntOrNull() ?: 70,
                rotation = bits[3].toIntOrNull() ?: 0,
            )
        }

    fun scheduleExtra(row: ScheduleRecord): String = listOf(
        row.leadsMinutes.joinToString(","),
        row.alert.name,
        row.category,
        row.weekdays.toString(),
    ).joinToString("|")

    fun applySchedule(row: ScheduleRecord, raw: String?): ScheduleRecord {
        if (raw.isNullOrBlank()) return row
        val parts = raw.split("|")
        if (parts.size < 4) return row
        val leads = parts[0].split(",").mapNotNull { it.toIntOrNull() }.ifEmpty { listOf(0) }
        val alert = runCatching { AlertStyle.valueOf(parts[1]) }.getOrDefault(AlertStyle.SOUND)
        return row.copy(
            leadsMinutes = leads,
            alert = alert,
            category = parts[2].ifBlank { "task" },
            weekdays = parts[3].toIntOrNull() ?: 0,
        )
    }

    fun anim(value: PetAnimState): String = listOf(value.clip, value.trigger, value.reactionId, value.until.toString()).joinToString("|")

    fun parseAnim(raw: String?): PetAnimState {
        if (raw.isNullOrBlank()) return PetAnimState()
        val parts = raw.split("|")
        if (parts.size < 4) return PetAnimState()
        return PetAnimState(
            clip = parts[0].ifBlank { "idle" },
            trigger = parts[1].ifBlank { "none" },
            reactionId = parts[2],
            until = parts[3].toLongOrNull() ?: 0L,
        )
    }

    fun tokens(values: Set<String>): String = values.joinToString("\n")

    fun parseTokens(raw: String?): Set<String> = raw.orEmpty().split("\n").filter { it.isNotBlank() }.toSet()
}

fun weekdayBit(dayOfWeekOrdinal: Int): Int = (dayOfWeekOrdinal + 1) % 7

const val WEEKDAY_MASK = 0b0111110
