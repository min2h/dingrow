package app.mongle.service

import kotlinx.datetime.toLocalDateTime
import app.mongle.l10n.AppLocale
import app.mongle.l10n.L10n
import app.mongle.logic.Korean
import app.mongle.logic.PetAnimMachine
import app.mongle.logic.PetRules
import app.mongle.logic.Recurrence
import app.mongle.logic.ShopRotation
import app.mongle.logic.resellAmount
import app.mongle.logic.resolveState
import app.mongle.logic.spec
import app.mongle.model.Catalog
import app.mongle.model.FoodView
import app.mongle.model.GameBalance
import app.mongle.model.GameState
import app.mongle.model.GiftView
import app.mongle.model.ItemKind
import app.mongle.model.OutingView
import app.mongle.model.PersistedState
import app.mongle.model.Rarity
import app.mongle.model.ScheduleView
import app.mongle.model.Stage
import app.mongle.model.floorHex
import app.mongle.model.isDarkWall
import app.mongle.model.wallHex
import kotlinx.datetime.TimeZone
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val widgetJson = Json { encodeDefaults = true }

fun project(raw: PersistedState, now: Long, zone: TimeZone, balance: GameBalance): GameState {
    val state = resolveState(raw, now, zone, balance)
    val pet = state.pet
    val time = PetRules.timeOfDay(now, zone)
    val mood = PetRules.mood(pet, now, time)
    val room = state.presets.getOrElse(state.activePreset) { state.presets.first() }
    val schedules = state.schedules
        .sortedWith(compareBy<app.mongle.model.ScheduleRecord> { it.done }.thenBy { it.timeMillis })
        .map { row ->
            ScheduleView(
                id = row.id,
                title = row.title,
                timeMillis = row.timeMillis,
                timeLabel = L10n.formatWhen(row.timeMillis, zone),
                repeat = row.repeat,
                repeatLabel = Recurrence.label(row.spec()),
                done = row.done,
                overdue = !row.done && row.timeMillis <= now,
                leadsMinutes = row.leadsMinutes,
                alert = row.alert,
                category = row.category,
                place = row.place,
                memo = row.memo,
                endMillis = row.endMillis,
                categoryKey = app.mongle.logic.Categories.normalize(row.category),
                weekdays = row.weekdays,
                anchorMillis = if (row.anchorMillis > 0) row.anchorMillis else row.timeMillis,
                interval = row.interval,
                monthOrdinal = row.monthOrdinal,
                endKind = row.endKind,
                endAt = row.endAt,
                endCount = row.endCount,
                exdates = row.exdates,
                doneDates = row.doneDates,
                skipDates = row.skipDates,
                seriesId = row.seriesId,
            )
        }
    val next = schedules.filter { !it.done }.minByOrNull { it.timeMillis }
    val pending = pet.pendingGiftDefId?.let { id ->
        Catalog.byId(id)?.let { def ->
            GiftView("pending", def.id, L10n.item(def.id), def.rarity, resellAmount(def), pet.pendingGiftAt ?: now)
        }
    }
    val gifts = state.gifts.mapNotNull { row ->
        val def = Catalog.byId(row.defId) ?: return@mapNotNull null
        GiftView(row.instanceId, def.id, L10n.item(def.id), def.rarity, resellAmount(def), row.acquiredAt)
    }
    val foods = state.foods.mapNotNull { (id, qty) ->
        val def = Catalog.byId(id) ?: return@mapNotNull null
        if (qty <= 0) null else FoodView(id, L10n.item(id), qty, def.hunger)
    }.sortedBy { it.name }
    val offer = ShopRotation.today(now, zone, state.owned)
    val (fraction, xpNext, progress) = progressOf(pet.stage, pet.xp, pet.completions, balance)
    val clip = PetAnimMachine.resolve(pet.stage, PetRules.pose(pet.stage, mood, time), pet.energy, state.statusMessage, state.anim, now)
    val speech = when {
        state.anim.until >= now && state.statusMessage.isNotBlank() -> state.statusMessage
        // A waiting completion gift: the home/widget/wallpaper bubble says so until it's opened.
        pet.pendingGiftDefId != null || state.pendingDropKind == "COINS" -> L10n.t("gift_carry_title")
        else -> state.extras["line.text"].orEmpty()
    }
    val weather = if (state.settings.useLocation) app.mongle.logic.Weather.decode(state.extras["wx"])?.takeIf { it.usable(now) } else null
    val condState = app.mongle.logic.ConditionRules.read(state.extras)
    val condition = app.mongle.logic.ConditionRules.current(condState, weather, pet.outfitId, pet.hunger, pet.happiness)
    val outing = if (pet.isOut && pet.outingReturnAt != null) {
        val left = (((pet.outingReturnAt - now).coerceAtLeast(0)) / 60_000L).toInt()
        OutingView(pet.outingReturnAt, pet.outingStartedAt ?: (pet.outingReturnAt - 20 * 60_000L), left, if (left <= 0) L10n.t("outing_soon") else L10n.fmt("outing_later", left))
    } else {
        null
    }
    val confirm = if (state.pendingConfirmDef != null && now <= state.pendingConfirmUntil) {
        val def = Catalog.byId(state.pendingConfirmDef)
        if (def == null) "" else L10n.fmt("confirm_buy", L10n.item(def.id), offerPrice(state, now, zone))
    } else {
        ""
    }
    return GameState(
        petName = if (pet.name == "Mongi" || pet.name == "몽이") L10n.t("pet_default_name") else pet.name,
        stage = pet.stage,
        stageLabel = L10n.stage(pet.stage),
        xp = pet.xp,
        xpForNext = xpNext,
        progressFraction = fraction,
        progressLabel = progress,
        hunger = pet.hunger,
        happiness = pet.happiness,
        energy = pet.energy,
        mood = mood,
        moodLabel = L10n.t(
            when (mood) {
                app.mongle.model.Mood.EGG -> "mood_egg"
                app.mongle.model.Mood.HAPPY -> "mood_happy"
                app.mongle.model.Mood.CALM -> "mood_calm"
                app.mongle.model.Mood.SAD -> "mood_sad"
                app.mongle.model.Mood.HUNGRY -> "mood_hungry"
                app.mongle.model.Mood.SLEEPY -> "mood_sleepy"
                app.mongle.model.Mood.AWAY -> "mood_away"
            },
        ),
        pose = PetRules.pose(pet.stage, mood, time),
        outfitId = pet.outfitId,
        coins = state.coins,
        foods = foods,
        gifts = gifts,
        pendingGift = pending,
        outing = outing,
        schedules = schedules,
        nextSchedule = next,
        presets = state.presets,
        activePreset = state.activePreset,
        owned = state.owned,
        todayOffer = offer,
        confirmText = confirm,
        statusMessage = if (now <= state.statusUntil) state.statusMessage else "",
        onboarded = state.onboarded,
        completions = pet.completions,
        timeOfDay = time,
        outingBlockReason = PetRules.outingBlock(state, pet, now, balance),
        wallHex = wallHex(room.wallpaperId),
        floorHex = floorHex(room.floorId),
        darkRoom = isDarkWall(room.wallpaperId),
        speciesId = pet.speciesId,
        level = when (pet.stage) {
            Stage.EGG -> 1
            Stage.BABY -> 2
            Stage.CHILD -> 3
            Stage.ADULT -> 4
        },
        settings = state.settings,
        placements = state.placements,
        premium = state.premium,
        animClip = clip.name.lowercase(),
        animTrigger = PetAnimMachine.trigger(state.anim, now),
        speech = speech,
        pendingDropKind = state.pendingDropKind,
        pendingDropCoins = state.pendingDropCoins,
        pendingSparkle = state.pendingSparkle,
        revealKind = state.revealKind,
        revealLabel = state.revealLabel,
        revealSparkle = state.revealSparkle,
        reactionBody = if (state.anim.until >= now) state.anim.reactionId else "",
        condition = condition,
        sick = condState.sick,
        recovery = condState.recovery,
        weather = weather,
        phase = app.mongle.logic.DayPhase.of(now, zone),
    )
}

private fun offerPrice(state: PersistedState, now: Long, zone: TimeZone): Int {
    val id = state.pendingConfirmDef ?: return 0
    val offer = ShopRotation.today(now, zone, state.owned)
    return if (offer.item.id == id) offer.price else Catalog.byId(id)?.price ?: 0
}

private fun progressOf(stage: Stage, xp: Int, completions: Int, balance: GameBalance): Triple<Float, Int, String> {
    return when (stage) {
        Stage.EGG -> Triple(
            completions / balance.hatchCompletions.toFloat(),
            balance.hatchCompletions,
            L10n.fmt("progress_hatch", completions, balance.hatchCompletions),
        )
        Stage.BABY -> Triple(
            (xp / balance.xpChild.toFloat()).coerceIn(0f, 1f),
            balance.xpChild,
            L10n.fmt("progress_child", xp, balance.xpChild),
        )
        Stage.CHILD -> Triple(
            (xp / balance.xpAdult.toFloat()).coerceIn(0f, 1f),
            balance.xpAdult,
            L10n.fmt("progress_adult", xp, balance.xpAdult),
        )
        Stage.ADULT -> Triple(1f, 0, L10n.t("progress_done"))
    }
}

fun GameState.fingerprint(): String = listOf(
    stage.name,
    hunger,
    happiness,
    energy,
    coins,
    pose.name,
    nextSchedule?.id.orEmpty(),
    nextSchedule?.timeMillis ?: 0,
    pendingGift?.defId.orEmpty(),
    outing?.returnAt ?: 0,
    activePreset,
    confirmText,
    completions,
    petName,
    speech,
    reactionBody,
    settings.language,
).joinToString("|")

@Serializable
data class WidgetSnapshot(
    val petName: String = "",
    val stageLabel: String = "",
    val moodLabel: String = "",
    val pose: String = "idle",
    val hunger: Int = 0,
    val happiness: Int = 0,
    val energy: Int = 0,
    val xp: Int = 0,
    val xpForNext: Int = 0,
    val coins: Int = 0,
    val nextTitle: String = "",
    val nextTime: String = "",
    val pendingGiftName: String = "",
    val pendingGiftPrice: Int = 0,
    val todayName: String = "",
    val todayPrice: Int = 0,
    val confirmText: String = "",
    val presetName: String = "",
    val wallHex: String = "#FFF6EE",
    val floorHex: String = "#F3D7B5",
    val bedId: String = "",
    val rugId: String = "",
    val lampId: String = "",
    val outfitId: String = "",
    val timeOfDay: String = "day",
    val statusMessage: String = "",
    val outingText: String = "",
    val darkRoom: Boolean = false,
    val progressLabel: String = "",
    val speciesId: String = "base",
    val level: Int = 1,
    val speech: String = "",
    val language: String = "",
    // v4 widget parity (iOS WidgetKit reads these from the App Group snapshot).
    val condition: String = "good",
    val conditionLabel: String = "",
    val sick: Boolean = false,
    val showList: Boolean = true,
    val showCondition: Boolean = true,
    val showBubble: Boolean = true,
    val weatherText: String = "",
    val todayCountLabel: String = "",
    val today: List<WidgetRow> = emptyList(),
)

@Serializable
data class WidgetRow(val id: String, val time: String, val title: String, val place: String, val done: Boolean, val next: Boolean, val deepLink: String)

fun GameState.toSnapshot(): WidgetSnapshot {
    val room = activeRoom
    val latestGift = pendingGift ?: gifts.maxByOrNull { it.acquiredAt }
    return WidgetSnapshot(
        petName = petName,
        stageLabel = stageLabel,
        moodLabel = moodLabel,
        pose = pose.name.lowercase(),
        hunger = hunger,
        happiness = happiness,
        energy = energy,
        xp = xp,
        xpForNext = xpForNext,
        coins = coins,
        nextTitle = nextSchedule?.title.orEmpty(),
        nextTime = nextSchedule?.timeLabel.orEmpty(),
        pendingGiftName = latestGift?.name.orEmpty(),
        pendingGiftPrice = latestGift?.sellPrice ?: 0,
        todayName = L10n.item(todayOffer.item.id),
        todayPrice = todayOffer.price,
        confirmText = confirmText,
        presetName = L10n.preset(room.wallpaperId),
        wallHex = wallHex,
        floorHex = floorHex,
        bedId = room.bedId.orEmpty(),
        rugId = room.rugId.orEmpty(),
        lampId = room.lampId.orEmpty(),
        outfitId = outfitId.orEmpty(),
        timeOfDay = timeOfDay.name.lowercase(),
        statusMessage = statusMessage,
        outingText = outing?.label.orEmpty(),
        darkRoom = darkRoom,
        progressLabel = progressLabel,
        speciesId = speciesId,
        level = level,
        speech = speech,
        language = AppLocale.code(),
        condition = condition.name.lowercase(),
        conditionLabel = L10n.t("cond_" + (if (sick) "sick" else condition.name.lowercase())),
        sick = sick,
        showList = settings.widgetList,
        showCondition = settings.widgetCondition,
        showBubble = settings.widgetBubble,
        weatherText = weather?.let { "${it.tempC.toInt()}° " + L10n.t("wx_" + it.kind.name.lowercase()) }.orEmpty(),
        todayCountLabel = L10n.fmt("widget_today_count", todayRows(this).size),
        today = todayRows(this),
    )
}

/** Today's schedules for widgets (one row per schedule whose time is today, done ones ticked). */
fun todayRows(state: GameState, now: Long = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(), zone: kotlinx.datetime.TimeZone = kotlinx.datetime.TimeZone.currentSystemDefault()): List<WidgetRow> {
    val day = kotlinx.datetime.Instant.fromEpochMilliseconds(now).toLocalDateTime(zone).date
    val list = state.schedules.filter { kotlinx.datetime.Instant.fromEpochMilliseconds(it.timeMillis).toLocalDateTime(zone).date == day }.sortedBy { it.timeMillis }
    val nextId = list.firstOrNull { !it.done }?.id
    return list.map { WidgetRow(it.id, L10n.clock(it.timeMillis, zone), it.title, it.place, it.done, it.id == nextId, "dingrow://schedule/" + it.id) }
}

fun GameState.toSnapshotJson(): String = widgetJson.encodeToString(toSnapshot())

fun ownedOutfits(owned: Set<String>): List<app.mongle.model.ItemDef> =
    Catalog.outfits.filter { it.id in owned }

fun ownedForSlot(owned: Set<String>, kind: ItemKind): List<app.mongle.model.ItemDef> =
    Catalog.furniture.filter { it.kind == kind && it.id in owned }
