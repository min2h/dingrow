package app.mongle.model

enum class Stage { EGG, BABY, CHILD, ADULT }

enum class RepeatRule { NONE, DAILY, WEEKLY, WEEKDAYS, WEEKENDS, MONTHLY, YEARLY, EVERY_N_DAYS }

enum class EndKind { NEVER, UNTIL, COUNT }

enum class EditScope { ONE, FOLLOWING, ALL }

enum class AlertStyle { SILENT, SOUND, VIBRATE }

enum class AlarmSound { SOFT, CHIME, BELL }

enum class ItemKind { FOOD, GIFT, OUTFIT, WALL, FLOOR, BED, RUG, LAMP, DECOR, COMPANION }

enum class ItemMotif {
    GENERIC, COOKIE, CANDY, PEACH, DUMPLING,
    PILLOW, BED, SHELF, TABLE, LAMP, CHAIR, RUG, PLANT, WINDOW, LIGHTS, TANK, PIANO,
    RIBBON, BERET, SCARF, COAT, CROWN, GLASSES, SPROUT, HOOD,
    SHELL, CLOVER, ACORN, PEBBLE, FEATHER, STAR, MUSHROOM, NOTE, TV, DESK, CLOSET, TUB, TOWER, BOWL, CLOCK, BLANKET,
    WALL, FLOOR,
}

enum class Rarity { COMMON, UNCOMMON, RARE, EPIC }

enum class DecorSlot { WALL, FLOOR, BED, RUG, LAMP }

enum class Mood { EGG, HAPPY, CALM, SAD, HUNGRY, SLEEPY, AWAY }

enum class Pose { EGG, IDLE, HAPPY, SAD, SLEEP, WALK, OUT }

enum class TimeOfDay { MORNING, DAY, EVENING, NIGHT }

data class ItemDef(
    val id: String,
    val name: String,
    val kind: ItemKind,
    val rarity: Rarity = Rarity.COMMON,
    val price: Int = 0,
    val sellPrice: Int = 0,
    val hunger: Int = 0,
    val happiness: Int = 0,
    val xp: Int = 0,
    val stackable: Boolean = false,
    val blurb: String = "",
    val motif: ItemMotif = ItemMotif.GENERIC,
    val fresh: Boolean = false,
    val artId: String = "",
) {
    fun pictureId(): String = artId.ifBlank { id }
}

data class GameBalance(
    val hungerPerHour: Int = 4,
    val happyPerHour: Int = 2,
    val happyPerHourHungry: Int = 5,
    val energyRegenPerHour: Int = 10,
    val decayCapMillis: Long = 72L * 60 * 60 * 1000,
    val hungryThreshold: Int = 30,
    val xpChild: Int = 50,
    val xpAdult: Int = 160,
    val minDaysChild: Int = 0,
    val minDaysAdult: Int = 0,
    val hatchCompletions: Int = 3,
    val dailyFullRewards: Int = 8,
    val instantWindowMillis: Long = 2 * 60 * 1000,
    val earlyWindowMillis: Long = 10 * 60 * 1000,
    val repeatGuardMillis: Long = 15 * 1000,
    val outingDurationMillis: Long = 20 * 60 * 1000,
    val outingCooldownMillis: Long = 90 * 60 * 1000,
    val outingMinHunger: Int = 35,
    val outingMinEnergy: Int = 30,
    val outingEnergyCost: Int = 35,
    val confirmWindowMillis: Long = 45 * 1000,
    val fullHunger: Int = 95,
    val maxSchedules: Int = 30,
    val starterCoins: Int = 40,
    val starterFood: Int = 3,
)

data class PetRecord(
    val id: String,
    val name: String,
    val stage: Stage,
    val xp: Int,
    val hunger: Int,
    val happiness: Int,
    val energy: Int,
    val outfitId: String?,
    val snapshotAt: Long,
    val createdAt: Long,
    val stageSince: Long,
    val completions: Int,
    val isOut: Boolean,
    val outingStartedAt: Long?,
    val outingReturnAt: Long?,
    val outingSeed: Long?,
    val outingGiftDefId: String?,
    val pendingGiftDefId: String?,
    val pendingGiftAt: Long?,
    val speciesId: String = "base",
)

data class ScheduleRecord(
    val id: String,
    val title: String,
    val timeMillis: Long,
    val repeat: RepeatRule,
    val done: Boolean,
    val createdAt: Long,
    val completedAt: Long?,
    val tz: String,
    val leadsMinutes: List<Int> = listOf(0),
    val alert: AlertStyle = AlertStyle.SOUND,
    val category: String = "task",
    val weekdays: Int = 0,
    val interval: Int = 1,
    val monthOrdinal: Int = 0,
    val endKind: EndKind = EndKind.NEVER,
    val endAt: Long = 0,
    val endCount: Int = 0,
    val anchorMillis: Long = 0,
    val seriesId: String = "",
    val exdates: String = "",
    val doneDates: String = "",
    val skipDates: String = "",
    val snoozedFrom: Long = 0,
    val place: String = "",
    val memo: String = "",
    val endMillis: Long = 0,
)

data class CompletionRecord(
    val id: String,
    val scheduleId: String,
    val completedAt: Long,
    val xp: Int,
    val coins: Int,
)

data class GiftRecord(
    val instanceId: String,
    val defId: String,
    val acquiredAt: Long,
)

data class LedgerEntry(
    val id: String,
    val delta: Int,
    val reason: String,
    val refId: String?,
    val createdAt: Long,
    val idempotencyKey: String?,
)

data class RoomPreset(
    val index: Int,
    val name: String,
    val wallpaperId: String,
    val floorId: String,
    val bedId: String?,
    val rugId: String?,
    val lampId: String?,
)

data class ActionRecord(
    val key: String,
    val ok: Boolean,
    val message: String,
    val at: Long,
)

data class AppSettings(
    val tipsEnabled: Boolean = true,
    val quietStartMinute: Int = -1,
    val quietEndMinute: Int = -1,
    val defaultLeadMinutes: Int = 0,
    val soundEnabled: Boolean = true,
    val vibrateEnabled: Boolean = true,
    val alarmSound: AlarmSound = AlarmSound.SOFT,
    val language: String = "system",
    val theme: String = "system",
    val use24h: Boolean = true,
    val weekStartsMonday: Boolean = true,
    val fahrenheit: Boolean = false,
    val useLocation: Boolean = false,
    val city: String = "suwon",
    val weatherAlerts: Boolean = true,
    val scheduleAlerts: Boolean = true,
    val newScheduleConfirm: Boolean = true,
    val briefingWeather: Boolean = true,
    val briefingEnabled: Boolean = true,
    val briefingMinute: Int = 7 * 60 + 30,
    val giftAlerts: Boolean = true,
    val wander: Boolean = true,
    val bubbleFrequency: Int = 1,
    val motionLevel: Int = 1,
    val reduceMotion: Boolean = false,
    val haptics: Boolean = true,
    val sfxEnabled: Boolean = false,
    val sfxVolume: Int = 45,
    val widgetList: Boolean = true,
    val widgetCondition: Boolean = true,
    val widgetBubble: Boolean = true,
)

data class Placement(
    val defId: String,
    val x: Int = 50,
    val y: Int = 70,
    val rotation: Int = 0,
)

data class PetAnimState(
    val clip: String = "idle",
    val trigger: String = "none",
    val reactionId: String = "",
    val until: Long = 0,
)

data class PersistedState(
    val pet: PetRecord,
    val schedules: List<ScheduleRecord>,
    val completions: List<CompletionRecord>,
    val foods: Map<String, Int>,
    val gifts: List<GiftRecord>,
    val owned: Set<String>,
    val ownedAt: Map<String, Long>,
    val coins: Int,
    val ledger: List<LedgerEntry>,
    val presets: List<RoomPreset>,
    val activePreset: Int,
    val onboarded: Boolean,
    val rewardDay: String,
    val rewardCount: Int,
    val pendingConfirmDef: String?,
    val pendingConfirmUntil: Long,
    val lastOutingEndedAt: Long,
    val notifiedGiftKey: String,
    val statusMessage: String,
    val statusUntil: Long,
    val actionLog: List<ActionRecord>,
    val settings: AppSettings = AppSettings(),
    val placements: List<Placement> = emptyList(),
    val premium: Boolean = false,
    val iapTokens: Set<String> = emptySet(),
    val anim: PetAnimState = PetAnimState(),
    val lastTipAt: Long = 0,
    val tipIndex: Int = 0,
    val giftDryStreak: Int = 0,
    val pendingDropKind: String = "",
    val pendingDropCoins: Int = 0,
    val pendingSparkle: Boolean = false,
    val revealKind: String = "",
    val revealLabel: String = "",
    val revealSparkle: Boolean = false,
    /** Free-form persisted values (condition, weather cache, dialogue history). Stored as meta "x:<key>". */
    val extras: Map<String, String> = emptyMap(),
)

data class ActionResult(val ok: Boolean, val message: String)

data class ScheduleView(
    val id: String,
    val title: String,
    val timeMillis: Long,
    val timeLabel: String,
    val repeat: RepeatRule,
    val repeatLabel: String,
    val done: Boolean,
    val overdue: Boolean,
    val leadsMinutes: List<Int> = listOf(0),
    val alert: AlertStyle = AlertStyle.SOUND,
    val category: String = "task",
    val weekdays: Int = 0,
    val anchorMillis: Long = 0,
    val interval: Int = 1,
    val monthOrdinal: Int = 0,
    val endKind: EndKind = EndKind.NEVER,
    val endAt: Long = 0,
    val endCount: Int = 0,
    val exdates: String = "",
    val doneDates: String = "",
    val skipDates: String = "",
    val seriesId: String = "",
    val place: String = "",
    val memo: String = "",
    val endMillis: Long = 0,
    val categoryKey: String = "other",
)

data class GiftView(
    val instanceId: String,
    val defId: String,
    val name: String,
    val rarity: Rarity,
    val sellPrice: Int,
    val acquiredAt: Long,
)

data class FoodView(
    val defId: String,
    val name: String,
    val quantity: Int,
    val hunger: Int,
)

data class OutingView(
    val returnAt: Long,
    val startedAt: Long = returnAt - 20 * 60_000L,
    val minutesLeft: Int,
    val label: String,
)

data class ShopOffer(
    val item: ItemDef,
    val price: Int,
    val discounted: Boolean,
)

data class GameState(
    val petName: String,
    val stage: Stage,
    val stageLabel: String,
    val xp: Int,
    val xpForNext: Int,
    val progressFraction: Float,
    val progressLabel: String,
    val hunger: Int,
    val happiness: Int,
    val energy: Int,
    val mood: Mood,
    val moodLabel: String,
    val pose: Pose,
    val outfitId: String?,
    val coins: Int,
    val foods: List<FoodView>,
    val gifts: List<GiftView>,
    val pendingGift: GiftView?,
    val outing: OutingView?,
    val schedules: List<ScheduleView>,
    val nextSchedule: ScheduleView?,
    val presets: List<RoomPreset>,
    val activePreset: Int,
    val owned: Set<String>,
    val todayOffer: ShopOffer,
    val confirmText: String,
    val statusMessage: String,
    val onboarded: Boolean,
    val completions: Int,
    val timeOfDay: TimeOfDay,
    val outingBlockReason: String?,
    val wallHex: String,
    val floorHex: String,
    val darkRoom: Boolean,
    val speciesId: String = "base",
    val level: Int = 1,
    val settings: AppSettings = AppSettings(),
    val placements: List<Placement> = emptyList(),
    val premium: Boolean = false,
    val animClip: String = "idle",
    val animTrigger: String = "none",
    val speech: String = "",
    val pendingDropKind: String = "",
    val pendingDropCoins: Int = 0,
    val pendingSparkle: Boolean = false,
    val revealKind: String = "",
    val revealLabel: String = "",
    val revealSparkle: Boolean = false,
    val reactionBody: String = "",
    val condition: app.mongle.logic.Condition = app.mongle.logic.Condition.GOOD,
    val sick: Boolean = false,
    val recovery: Int = 0,
    val weather: app.mongle.logic.WeatherSnapshot? = null,
    val phase: app.mongle.logic.DayPhase = app.mongle.logic.DayPhase.NOON,
) {
    val activeRoom: RoomPreset get() = presets[activePreset.coerceIn(0, presets.lastIndex.coerceAtLeast(0))]
}
