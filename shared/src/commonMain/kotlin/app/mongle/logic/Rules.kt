package app.mongle.logic

import app.mongle.model.Catalog
import app.mongle.model.GameBalance
import app.mongle.model.SpeciesCatalog
import app.mongle.model.GiftRecord
import app.mongle.model.ItemDef
import app.mongle.model.Mood
import app.mongle.model.PersistedState
import app.mongle.model.PetRecord
import app.mongle.model.Pose
import app.mongle.model.Rarity
import app.mongle.model.RepeatRule
import app.mongle.model.ShopOffer
import app.mongle.model.Stage
import app.mongle.model.TimeOfDay
import kotlin.random.Random
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

object Korean {
    fun hasBatchim(word: String): Boolean {
        val ch = word.lastOrNull() ?: return false
        if (ch !in '\uAC00'..'\uD7A3') return false
        return (ch.code - 0xAC00) % 28 != 0
    }

    fun iGa(word: String): String = if (hasBatchim(word)) "이" else "가"

    fun eulReul(word: String): String = if (hasBatchim(word)) "을" else "를"

    fun subject(name: String): String = name + iGa(name)

    fun stageLabel(stage: Stage): String = when (stage) {
        Stage.EGG -> "알"
        Stage.BABY -> "아기"
        Stage.CHILD -> "어린이"
        Stage.ADULT -> "어른"
    }

    fun moodLabel(mood: Mood): String = when (mood) {
        Mood.EGG -> "따듯하게 품는 중"
        Mood.HAPPY -> "행복해요"
        Mood.CALM -> "평온해요"
        Mood.SAD -> "시무룩해요"
        Mood.HUNGRY -> "배고파요"
        Mood.SLEEPY -> "자는 중"
        Mood.AWAY -> "외출 중"
    }

    fun repeatLabel(rule: RepeatRule): String = when (rule) {
        RepeatRule.NONE -> "한 번"
        RepeatRule.DAILY -> "매일"
        RepeatRule.WEEKLY -> "매주"
    }

    fun rarityLabel(rarity: Rarity): String = when (rarity) {
        Rarity.COMMON -> "흔함"
        Rarity.UNCOMMON -> "조금 특별"
        Rarity.RARE -> "희귀"
        Rarity.EPIC -> "아주 희귀"
    }

    fun timeOfDayLabel(time: TimeOfDay): String = when (time) {
        TimeOfDay.MORNING -> "아침"
        TimeOfDay.DAY -> "낮"
        TimeOfDay.EVENING -> "저녁"
        TimeOfDay.NIGHT -> "밤"
    }

    fun greeting(time: TimeOfDay, name: String): String = when (time) {
        TimeOfDay.MORNING -> "좋은 아침이에요. ${subject(name)} 기지개를 켜요."
        TimeOfDay.DAY -> "나른한 오후예요. 창밖을 보고 있어요."
        TimeOfDay.EVENING -> "저녁이 내려앉았어요."
        TimeOfDay.NIGHT -> "별이 뜨면 ${subject(name)} 눈을 감아요."
    }

    fun reminder(petName: String, stage: Stage, title: String): String = when (stage) {
        Stage.EGG -> "알 속의 $petName: \"$title\" 할 시간이에요"
        Stage.BABY -> "$petName: \"$title\" 하자! 나 기다릴게"
        Stage.CHILD -> "${subject(petName)} 알려줘요. \"$title\" 시간!"
        Stage.ADULT -> "$petName: \"$title\". 같이 해낼 수 있어요"
    }

    fun formatWhen(millis: Long, zone: TimeZone): String {
        val ldt = Instant.fromEpochMilliseconds(millis).toLocalDateTime(zone)
        val week = listOf("월", "화", "수", "목", "금", "토", "일")
        val w = week[ldt.dayOfWeek.ordinal]
        val ampm = if (ldt.hour < 12) "오전" else "오후"
        val hour12 = ldt.hour % 12
        val h = if (hour12 == 0) 12 else hour12
        val minute = ldt.minute.toString().padStart(2, '0')
        return "${ldt.monthNumber}월 ${ldt.dayOfMonth}일 ($w) $ampm $h:$minute"
    }

    fun dayKey(millis: Long, zone: TimeZone): String {
        val date = Instant.fromEpochMilliseconds(millis).toLocalDateTime(zone).date
        return "${date.year}-${date.monthNumber.toString().padStart(2, '0')}-${date.dayOfMonth.toString().padStart(2, '0')}"
    }
}

object ScheduleRules {
    fun step(timeMillis: Long, rule: RepeatRule, tz: String): Long {
        if (rule == RepeatRule.NONE) return timeMillis
        val zone = zoneOrUtc(tz)
        val local = Instant.fromEpochMilliseconds(timeMillis).toLocalDateTime(zone)
        val days = if (rule == RepeatRule.WEEKLY) 7 else 1
        val nextDate = local.date.plus(days, DateTimeUnit.DAY)
        return kotlinx.datetime.LocalDateTime(nextDate, local.time).toInstant(zone).toEpochMilliseconds()
    }

    /** 놓친 반복은 건너뛰고, 지금 이후의 다음 회차로 옮긴다. */
    fun advance(timeMillis: Long, rule: RepeatRule, tz: String, now: Long): Long {
        if (rule == RepeatRule.NONE) return timeMillis
        var next = step(timeMillis, rule, tz)
        var guard = 0
        while (next <= now && guard < 500) {
            next = step(next, rule, tz)
            guard++
        }
        return next
    }

    fun zoneOrUtc(tz: String): TimeZone = try {
        TimeZone.of(tz)
    } catch (_: Exception) {
        TimeZone.UTC
    }
}

data class Reward(val xp: Int, val coins: Int, val happiness: Int, val factor: Double)

object RewardPolicy {
    fun compute(
        stage: Stage,
        createdAt: Long,
        scheduledAt: Long,
        now: Long,
        rewardedToday: Int,
        balance: GameBalance,
    ): Reward {
        val baseXp = if (stage == Stage.EGG) 4 else 14
        val baseCoins = if (stage == Stage.EGG) 2 else 8
        val baseHappy = if (stage == Stage.EGG) 6 else 10
        var factor = 1.0
        if (now - createdAt < balance.instantWindowMillis) factor = 0.25
        else if (now + balance.earlyWindowMillis < scheduledAt) factor = 0.5
        if (rewardedToday >= balance.dailyFullRewards) factor *= 0.4
        return Reward(
            xp = (baseXp * factor).toInt().coerceAtLeast(1),
            coins = (baseCoins * factor).toInt().coerceAtLeast(0),
            happiness = (baseHappy * factor).toInt().coerceAtLeast(0),
            factor = factor,
        )
    }
}

object PetRules {
    fun collapse(pet: PetRecord, now: Long, balance: GameBalance): PetRecord {
        if (pet.stage == Stage.EGG) {
            return if (pet.snapshotAt == now) pet else pet.copy(snapshotAt = now)
        }
        val elapsed = (now - pet.snapshotAt).coerceIn(0, balance.decayCapMillis)
        if (elapsed == 0L) return pet
        val hungerLoss = ((elapsed * balance.hungerPerHour) / 3_600_000L).toInt()
        val happyRate = if (pet.hunger < balance.hungryThreshold) balance.happyPerHourHungry else balance.happyPerHour
        val happyLoss = ((elapsed * happyRate) / 3_600_000L).toInt()
        val hunger = (pet.hunger - hungerLoss).coerceIn(0, 100)
        val happiness = (pet.happiness - happyLoss).coerceIn(0, 100)
        val energy = if (pet.isOut) {
            pet.energy
        } else {
            val gain = ((elapsed * balance.energyRegenPerHour) / 3_600_000L).toInt()
            (pet.energy + gain).coerceIn(0, 100)
        }
        return pet.copy(hunger = hunger, happiness = happiness, energy = energy, snapshotAt = now)
    }

    fun daysInStage(pet: PetRecord, now: Long, zone: TimeZone): Int {
        val start = Instant.fromEpochMilliseconds(pet.stageSince).toLocalDateTime(zone).date
        val end = Instant.fromEpochMilliseconds(now).toLocalDateTime(zone).date
        return start.daysUntil(end).coerceAtLeast(0)
    }

    fun evolve(pet: PetRecord, now: Long, zone: TimeZone, balance: GameBalance): PetRecord {
        val days = daysInStage(pet, now, zone)
        val next = when (pet.stage) {
            Stage.EGG -> if (pet.completions >= balance.hatchCompletions) Stage.BABY else null
            Stage.BABY -> if (pet.xp >= balance.xpChild && days >= balance.minDaysChild) Stage.CHILD else null
            Stage.CHILD -> if (pet.xp >= balance.xpAdult && days >= balance.minDaysAdult) Stage.ADULT else null
            Stage.ADULT -> null
        } ?: return pet
        return if (next == Stage.BABY) {
            pet.copy(
                stage = Stage.BABY,
                stageSince = now,
                snapshotAt = now,
                xp = maxOf(pet.xp, 8),
                hunger = 80,
                happiness = 88,
                energy = 90,
            )
        } else {
            pet.copy(stage = next, stageSince = now, snapshotAt = now)
        }
    }

    fun timeOfDay(now: Long, zone: TimeZone): TimeOfDay {
        val hour = Instant.fromEpochMilliseconds(now).toLocalDateTime(zone).hour
        return when (hour) {
            in 5..10 -> TimeOfDay.MORNING
            in 11..16 -> TimeOfDay.DAY
            in 17..20 -> TimeOfDay.EVENING
            else -> TimeOfDay.NIGHT
        }
    }

    fun mood(pet: PetRecord, now: Long, time: TimeOfDay): Mood {
        if (pet.stage == Stage.EGG) return Mood.EGG
        if (pet.isOut && (pet.outingReturnAt ?: Long.MAX_VALUE) > now) return Mood.AWAY
        if (pet.hunger < 25) return Mood.HUNGRY
        if (pet.happiness < 35) return Mood.SAD
        if (time == TimeOfDay.NIGHT) return Mood.SLEEPY
        if (pet.happiness >= 75) return Mood.HAPPY
        return Mood.CALM
    }

    fun pose(stage: Stage, mood: Mood, time: TimeOfDay): Pose = when {
        stage == Stage.EGG -> Pose.EGG
        mood == Mood.AWAY -> Pose.OUT
        mood == Mood.SLEEPY -> Pose.SLEEP
        mood == Mood.HUNGRY || mood == Mood.SAD -> Pose.SAD
        mood == Mood.HAPPY && time != TimeOfDay.NIGHT -> Pose.WALK
        mood == Mood.HAPPY -> Pose.HAPPY
        else -> Pose.IDLE
    }

    fun outingBlock(state: PersistedState, pet: PetRecord, now: Long, balance: GameBalance): String? {
        if (pet.stage == Stage.EGG || pet.stage == Stage.BABY) return "어린이가 되면 밖에 나갈 수 있어요"
        if (pet.isOut) return "이미 외출 중이에요"
        if (pet.pendingGiftDefId != null) return "선물을 먼저 정리해 주세요"
        if (pet.hunger < balance.outingMinHunger) return "배가 고파서 나가지 않아요"
        if (pet.energy < balance.outingMinEnergy) return "피곤해서 쉬고 있어요"
        if (state.lastOutingEndedAt > 0 && now - state.lastOutingEndedAt < balance.outingCooldownMillis) {
            return "조금 쉬었다가 다시 나갈 수 있어요"
        }
        return null
    }
}

object GiftRoller {
    private val weights = listOf(
        350 to "gift_shell",
        350 to "gift_clover",
        140 to "gift_pebble",
        80 to "gift_letter",
        60 to "gift_acorn",
        20 to "gift_star",
    )

    fun roll(seed: Long): ItemDef {
        val ticket = Random(seed).nextInt(1000)
        var cursor = 0
        for ((weight, id) in weights) {
            cursor += weight
            if (ticket < cursor) return Catalog.byId(id)!!
        }
        return Catalog.byId("gift_shell")!!
    }
}

object ShopRotation {
    fun epochDay(now: Long, zone: TimeZone): Long {
        val date: LocalDate = Instant.fromEpochMilliseconds(now).toLocalDateTime(zone).date
        return date.toEpochDays().toLong()
    }

    fun today(now: Long, zone: TimeZone, owned: Set<String>): ShopOffer {
        val pool = Catalog.rotating()
        val start = (epochDay(now, zone) % pool.size).toInt()
        for (offset in pool.indices) {
            val item = pool[(start + offset) % pool.size]
            if (item.stackable || item.id !in owned) {
                val price = (item.price * 9) / 10
                return ShopOffer(item, price.coerceAtLeast(1), discounted = true)
            }
        }
        val fallback = pool[start]
        return ShopOffer(fallback, ((fallback.price * 9) / 10).coerceAtLeast(1), discounted = true)
    }
}

fun resolveState(state: PersistedState, now: Long, zone: TimeZone, balance: GameBalance): PersistedState {
    val collapsed = PetRules.collapse(state.pet, now, balance)
    if (!collapsed.isOut) return state.copy(pet = collapsed)
    val backAt = collapsed.outingReturnAt ?: return state.copy(pet = collapsed)
    if (now < backAt) return state.copy(pet = collapsed)
    val giftId = collapsed.outingGiftDefId
    val found = collapsed.outingSeed?.let { SpeciesCatalog.unlockFor(it, state.owned) }
    val owned = if (found == null) state.owned else state.owned + found
    val ownedAt = if (found == null) state.ownedAt else state.ownedAt + (found to now)
    return state.copy(
        pet = collapsed.copy(
            isOut = false,
            pendingGiftDefId = giftId,
            pendingGiftAt = now,
            outingGiftDefId = null,
            outingReturnAt = null,
            outingStartedAt = null,
            outingSeed = null,
        ),
        owned = owned,
        ownedAt = ownedAt,
        lastOutingEndedAt = now,
    )
}

fun beginOuting(state: PersistedState, now: Long, balance: GameBalance): PersistedState {
    val seed = now xor state.pet.id.hashCode().toLong() xor state.pet.completions.toLong()
    val gift = GiftRoller.roll(seed)
    return state.copy(
        pet = state.pet.copy(
            isOut = true,
            energy = (state.pet.energy - balance.outingEnergyCost).coerceAtLeast(0),
            outingStartedAt = now,
            outingReturnAt = now + balance.outingDurationMillis,
            outingSeed = seed,
            outingGiftDefId = gift.id,
            snapshotAt = now,
        ),
    )
}

fun keptGift(defId: String, now: Long): GiftRecord {
    return GiftRecord(
        instanceId = "g" + now.toString(16) + defId.hashCode().toUInt().toString(16),
        defId = defId,
        acquiredAt = now,
    )
}
