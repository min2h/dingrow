package app.mongle.service

import app.mongle.logic.Korean
import app.mongle.logic.PetRules
import app.mongle.logic.ShopRotation
import app.mongle.logic.resolveState
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
                timeLabel = Korean.formatWhen(row.timeMillis, zone),
                repeat = row.repeat,
                repeatLabel = Korean.repeatLabel(row.repeat),
                done = row.done,
                overdue = !row.done && row.timeMillis <= now,
            )
        }
    val next = schedules.filter { !it.done }.minByOrNull { it.timeMillis }
    val pending = pet.pendingGiftDefId?.let { id ->
        Catalog.byId(id)?.let { def ->
            GiftView("pending", def.id, def.name, def.rarity, def.sellPrice, pet.pendingGiftAt ?: now)
        }
    }
    val gifts = state.gifts.mapNotNull { row ->
        val def = Catalog.byId(row.defId) ?: return@mapNotNull null
        GiftView(row.instanceId, def.id, def.name, def.rarity, def.sellPrice, row.acquiredAt)
    }
    val foods = state.foods.mapNotNull { (id, qty) ->
        val def = Catalog.byId(id) ?: return@mapNotNull null
        if (qty <= 0) null else FoodView(id, def.name, qty, def.hunger)
    }.sortedBy { it.name }
    val offer = ShopRotation.today(now, zone, state.owned)
    val (fraction, xpNext, progress) = progressOf(pet.stage, pet.xp, pet.completions, balance)
    val outing = if (pet.isOut && pet.outingReturnAt != null) {
        val left = (((pet.outingReturnAt - now).coerceAtLeast(0)) / 60_000L).toInt()
        OutingView(pet.outingReturnAt, left, if (left <= 0) "곧 돌아와요" else "약 ${left}분 뒤 돌아와요")
    } else {
        null
    }
    val confirm = if (state.pendingConfirmDef != null && now <= state.pendingConfirmUntil) {
        val def = Catalog.byId(state.pendingConfirmDef)
        if (def == null) "" else "${def.name}${Korean.eulReul(def.name)} ${offerPrice(state, now, zone)}코인에 살까요?"
    } else {
        ""
    }
    return GameState(
        petName = pet.name,
        stage = pet.stage,
        stageLabel = Korean.stageLabel(pet.stage),
        xp = pet.xp,
        xpForNext = xpNext,
        progressFraction = fraction,
        progressLabel = progress,
        hunger = pet.hunger,
        happiness = pet.happiness,
        energy = pet.energy,
        mood = mood,
        moodLabel = Korean.moodLabel(mood),
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
            "부화까지 일정 $completions/${balance.hatchCompletions}",
        )
        Stage.BABY -> Triple(
            (xp / balance.xpChild.toFloat()).coerceIn(0f, 1f),
            balance.xpChild,
            "어린이까지 경험치 $xp/${balance.xpChild}",
        )
        Stage.CHILD -> Triple(
            (xp / balance.xpAdult.toFloat()).coerceIn(0f, 1f),
            balance.xpAdult,
            "어른까지 경험치 $xp/${balance.xpAdult}",
        )
        Stage.ADULT -> Triple(1f, 0, "다 자랐어요")
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
)

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
        todayName = todayOffer.item.name,
        todayPrice = todayOffer.price,
        confirmText = confirmText,
        presetName = room.name,
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
    )
}

fun GameState.toSnapshotJson(): String = widgetJson.encodeToString(toSnapshot())

fun ownedOutfits(owned: Set<String>): List<app.mongle.model.ItemDef> =
    Catalog.outfits.filter { it.id in owned }

fun ownedForSlot(owned: Set<String>, kind: ItemKind): List<app.mongle.model.ItemDef> =
    Catalog.furniture.filter { it.kind == kind && it.id in owned }
