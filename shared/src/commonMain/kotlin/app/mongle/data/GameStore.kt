package app.mongle.data

import app.mongle.model.ActionRecord
import app.mongle.model.Catalog
import app.mongle.model.CompletionRecord
import app.mongle.model.GameBalance
import app.mongle.model.GiftRecord
import app.mongle.model.LedgerEntry
import app.mongle.model.PersistedState
import app.mongle.model.PetRecord
import app.mongle.model.RepeatRule
import app.mongle.model.RoomPreset
import app.mongle.model.ScheduleRecord
import app.mongle.model.Stage
import kotlin.random.Random

interface GameStore {
    fun load(): PersistedState?
    fun save(state: PersistedState)
}

class MemoryGameStore(initial: PersistedState? = null) : GameStore {
    private var current = initial
    override fun load(): PersistedState? = current
    override fun save(state: PersistedState) {
        current = state
    }
}

fun newId(now: Long): String =
    now.toString(16) + Random.nextInt().toUInt().toString(16)

object Seed {
    fun create(now: Long, balance: GameBalance = GameBalance()): PersistedState {
        val pet = PetRecord(
            id = "pet-" + newId(now),
            name = "몽이",
            stage = Stage.EGG,
            xp = 0,
            hunger = 80,
            happiness = 80,
            energy = 80,
            outfitId = null,
            snapshotAt = now,
            createdAt = now,
            stageSince = now,
            completions = 0,
            isOut = false,
            outingStartedAt = null,
            outingReturnAt = null,
            outingSeed = null,
            outingGiftDefId = null,
            pendingGiftDefId = null,
            pendingGiftAt = null,
        )
        val owned = listOf("peach", "egg")
        return PersistedState(
            pet = pet,
            schedules = emptyList(),
            completions = emptyList(),
            foods = mapOf(Catalog.foodBasic.id to balance.starterFood),
            gifts = emptyList(),
            owned = owned.toSet(),
            ownedAt = owned.associateWith { now },
            coins = balance.starterCoins,
            ledger = listOf(
                LedgerEntry(
                    id = "seed",
                    delta = balance.starterCoins,
                    reason = "SEED",
                    refId = null,
                    createdAt = now,
                    idempotencyKey = "seed",
                ),
            ),
            presets = listOf(
                RoomPreset(0, "포근한 방", "wood", "wood", null, null, null),
                RoomPreset(1, "민트 방", "mint", "mint", null, null, null),
                RoomPreset(2, "밤하늘 방", "starry", "starry", null, null, null),
            ),
            activePreset = 0,
            onboarded = false,
            rewardDay = "",
            rewardCount = 0,
            pendingConfirmDef = null,
            pendingConfirmUntil = 0,
            lastOutingEndedAt = 0,
            notifiedGiftKey = "",
            statusMessage = "",
            statusUntil = 0,
            actionLog = emptyList(),
        )
    }
}

fun PersistedState.trimmed(): PersistedState = copy(
    completions = completions.takeLast(200),
    ledger = ledger.takeLast(300),
    actionLog = actionLog.takeLast(40),
)

fun parseRepeat(raw: String): RepeatRule = when (raw) {
    "DAILY" -> RepeatRule.DAILY
    "WEEKLY" -> RepeatRule.WEEKLY
    else -> RepeatRule.NONE
}

fun parseStage(raw: String): Stage = when (raw) {
    "BABY" -> Stage.BABY
    "CHILD" -> Stage.CHILD
    "ADULT" -> Stage.ADULT
    else -> Stage.EGG
}

fun emptySchedule(): ScheduleRecord = ScheduleRecord("", "", 0, RepeatRule.NONE, false, 0, null, "UTC")

fun emptyGift(): GiftRecord = GiftRecord("", "", 0)

fun emptyCompletion(): CompletionRecord = CompletionRecord("", "", 0, 0, 0)

fun emptyAction(): ActionRecord = ActionRecord("", false, "", 0)
