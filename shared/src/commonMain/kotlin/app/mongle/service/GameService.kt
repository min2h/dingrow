package app.mongle.service

import app.mongle.data.GameStore
import app.mongle.data.Seed
import app.mongle.data.newId
import app.mongle.logic.Korean
import app.mongle.logic.PetRules
import app.mongle.logic.ScheduleRules
import app.mongle.logic.beginOuting
import app.mongle.logic.keptGift
import app.mongle.logic.resolveState
import app.mongle.model.ActionRecord
import app.mongle.model.ActionResult
import app.mongle.model.Catalog
import app.mongle.model.CompletionRecord
import app.mongle.model.DecorSlot
import app.mongle.model.GameBalance
import app.mongle.model.ItemKind
import app.mongle.model.LedgerEntry
import app.mongle.model.PersistedState
import app.mongle.model.RepeatRule
import app.mongle.model.RoomPreset
import app.mongle.model.ScheduleRecord
import app.mongle.model.Stage
import app.mongle.platform.PlatformNotifier
import app.mongle.platform.ScheduledNotice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone

fun interface TimeProvider {
    fun now(): Long
}

object SystemTime : TimeProvider {
    override fun now(): Long = Clock.System.now().toEpochMilliseconds()
}

class GameService(
    private val store: GameStore,
    private val time: TimeProvider = SystemTime,
    private val zone: TimeZone = TimeZone.currentSystemDefault(),
    private val balance: GameBalance = GameBalance(),
    private val authority: EconomyAuthority = LocalEconomyAuthority(balance),
    private val notifier: PlatformNotifier = PlatformNotifier(),
) {
    private val mutex = Mutex()
    private var lastNotices: List<ScheduledNotice>? = null
    private var lastFingerprint: String? = null

    private val initial = store.load() ?: Seed.create(time.now(), balance).also { store.save(it) }
    private val _state = MutableStateFlow(project(initial, time.now(), zone, balance))
    val state: StateFlow<app.mongle.model.GameState> = _state

    init {
        lastFingerprint = _state.value.fingerprint()
    }

    suspend fun refresh(): ActionResult = mutate { state, _ -> done(state, ActionResult(true, "")) }

    suspend fun tick(): ActionResult = mutate { state, now ->
        val pet = state.pet
        val block = PetRules.outingBlock(state, pet, now, balance)
        if (block == null) done(beginOuting(state, now, balance), ActionResult(true, "몽이가 외출을 떠났어요"))
        else done(state, ActionResult(true, ""))
    }

    suspend fun setName(name: String): ActionResult = mutate { state, _ ->
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return@mutate done(state, ActionResult(false, "이름을 적어 주세요"))
        if (trimmed.length > 12) return@mutate done(state, ActionResult(false, "이름은 12자까지예요"))
        done(state.copy(pet = state.pet.copy(name = trimmed)), ActionResult(true, "${trimmed}라고 부를게요"))
    }

    suspend fun completeOnboarding(name: String): ActionResult = mutate { state, _ ->
        val trimmed = name.trim().ifBlank { state.pet.name }
        if (trimmed.length > 12) return@mutate done(state, ActionResult(false, "이름은 12자까지예요"))
        done(
            state.copy(pet = state.pet.copy(name = trimmed), onboarded = true),
            ActionResult(true, "잘 부탁해, $trimmed"),
        )
    }

    suspend fun addSchedule(title: String, timeMillis: Long, repeat: RepeatRule): ActionResult = mutate { state, now ->
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return@mutate done(state, ActionResult(false, "할 일을 적어 주세요"))
        if (trimmed.length > 40) return@mutate done(state, ActionResult(false, "제목은 40자까지예요"))
        if (state.schedules.count { !it.done } >= balance.maxSchedules) {
            return@mutate done(state, ActionResult(false, "일정은 ${balance.maxSchedules}개까지예요"))
        }
        val row = ScheduleRecord(
            id = newId(now),
            title = trimmed,
            timeMillis = timeMillis,
            repeat = repeat,
            done = false,
            createdAt = now,
            completedAt = null,
            tz = zone.id,
        )
        done(state.copy(schedules = state.schedules + row), ActionResult(true, "\"$trimmed\" 알려줄게요"))
    }

    suspend fun deleteSchedule(id: String): ActionResult = mutate { state, _ ->
        done(state.copy(schedules = state.schedules.filterNot { it.id == id }), ActionResult(true, "일정을 지웠어요"))
    }

    suspend fun snooze(id: String): ActionResult = mutate { state, now ->
        val row = state.schedules.find { it.id == id } ?: return@mutate done(state, ActionResult(false, "일정을 찾지 못했어요"))
        val moved = row.copy(timeMillis = now + 10 * 60 * 1000, done = false)
        val next = state.schedules.map { if (it.id == id) moved else it }
        done(state.copy(schedules = next), ActionResult(true, "10분 뒤로 미뤘어요"))
    }

    suspend fun completeSchedule(id: String): ActionResult = mutate { state, now -> complete(state, id, now) }

    suspend fun completeNext(): ActionResult = mutate { state, now ->
        val next = state.schedules.filter { !it.done }.minByOrNull { it.timeMillis }
            ?: return@mutate done(state, ActionResult(false, "남은 일정이 없어요"))
        complete(state, next.id, now)
    }

    suspend fun feed(foodId: String? = null): ActionResult = mutate { state, now ->
        val pet = state.pet
        if (pet.hunger >= balance.fullHunger) return@mutate done(state, ActionResult(false, "배가 불러요"))
        val chosen = foodId ?: state.foods.entries.firstOrNull { it.value > 0 }?.key
        val def = Catalog.byId(chosen)
        if (def == null || def.kind != ItemKind.FOOD) return@mutate done(state, ActionResult(false, "먹이가 없어요. 상점에서 사 주세요"))
        val qty = state.foods[def.id] ?: 0
        if (qty <= 0) return@mutate done(state, ActionResult(false, "먹이가 없어요. 상점에서 사 주세요"))
        var updated = pet.copy(
            hunger = (pet.hunger + def.hunger).coerceAtMost(100),
            happiness = (pet.happiness + def.happiness).coerceAtMost(100),
            xp = pet.xp + def.xp,
        )
        val before = updated.stage
        updated = PetRules.evolve(updated, now, zone, balance)
        val foods = state.foods.toMutableMap()
        foods[def.id] = qty - 1
        val message = if (updated.stage != before) {
            "${Korean.stageLabel(updated.stage)}로 자랐어요"
        } else {
            "${def.name}${Korean.eulReul(def.name)} 먹였어요. 포만감 +${def.hunger}"
        }
        done(state.copy(pet = updated, foods = foods), ActionResult(true, message))
    }

    suspend fun startOuting(): ActionResult = mutate { state, now ->
        val reason = PetRules.outingBlock(state, state.pet, now, balance)
        if (reason != null) return@mutate done(state, ActionResult(false, reason))
        done(beginOuting(state, now, balance), ActionResult(true, "몽이가 산책을 떠났어요"))
    }

    suspend fun keepGift(): ActionResult = mutate { state, now ->
        val defId = state.pet.pendingGiftDefId ?: return@mutate done(state, ActionResult(false, "도착한 선물이 없어요"))
        val def = Catalog.byId(defId)
        val gift = keptGift(defId, now)
        done(
            state.copy(
                pet = state.pet.copy(pendingGiftDefId = null, pendingGiftAt = null),
                gifts = state.gifts + gift,
            ),
            ActionResult(true, "${def?.name ?: "선물"}을 보관했어요"),
        )
    }

    suspend fun sellPendingOrLatest(): ActionResult = mutate { state, now ->
        val pending = state.pet.pendingGiftDefId
        if (pending != null) return@mutate sellDef(state, pending, now, fromPending = true, instanceId = null)
        val latest = state.gifts.maxByOrNull { it.acquiredAt }
            ?: return@mutate done(state, ActionResult(false, "팔 선물이 없어요"))
        sellDef(state, latest.defId, now, fromPending = false, instanceId = latest.instanceId)
    }

    suspend fun sellGift(instanceId: String): ActionResult = mutate { state, now ->
        val gift = state.gifts.find { it.instanceId == instanceId }
            ?: return@mutate done(state, ActionResult(false, "선물을 찾지 못했어요"))
        sellDef(state, gift.defId, now, fromPending = false, instanceId = instanceId)
    }

    suspend fun buy(defId: String): ActionResult = mutate { state, now -> purchase(state, defId, now, idempotencyKey = null) }

    suspend fun beginBuyToday(): ActionResult = mutate { state, now ->
        val offer = stateViewOffer(state, now)
        val decision = authority.grantPurchase(
            PurchaseRequest(
                defId = offer.item.id,
                price = offer.price,
                coins = state.coins,
                alreadyOwned = offer.item.id in state.owned,
                stackable = offer.item.stackable,
            ),
        )
        if (decision is GrantDecision.Deny) return@mutate done(state, ActionResult(false, decision.message))
        done(
            state.copy(pendingConfirmDef = offer.item.id, pendingConfirmUntil = now + balance.confirmWindowMillis),
            ActionResult(true, "${offer.item.name}${Korean.eulReul(offer.item.name)} ${offer.price}코인에 살까요?"),
        )
    }

    suspend fun confirmBuy(): ActionResult = mutate { state, now ->
        val defId = state.pendingConfirmDef ?: return@mutate done(state, ActionResult(false, "확인할 구매가 없어요"))
        if (now > state.pendingConfirmUntil) {
            return@mutate done(
                state.copy(pendingConfirmDef = null, pendingConfirmUntil = 0),
                ActionResult(false, "구매 확인 시간이 지났어요"),
            )
        }
        val key = "buy:$defId:${state.pendingConfirmUntil}"
        val prior = state.actionLog.find { it.key == key }
        if (prior != null) return@mutate done(state, ActionResult(prior.ok, prior.message))
        val cleared = state.copy(pendingConfirmDef = null, pendingConfirmUntil = 0)
        val result = purchase(cleared, defId, now, idempotencyKey = key)
        val withLog = result.state.copy(
            actionLog = result.state.actionLog + ActionRecord(key, result.result.ok, result.result.message, now),
        )
        done(withLog, result.result)
    }

    suspend fun cancelBuy(): ActionResult = mutate { state, _ ->
        done(
            state.copy(pendingConfirmDef = null, pendingConfirmUntil = 0),
            ActionResult(true, "구매를 취소했어요"),
        )
    }

    suspend fun equipOutfit(defId: String?): ActionResult = mutate { state, _ ->
        if (defId != null && (defId !in state.owned || Catalog.byId(defId)?.kind != ItemKind.OUTFIT)) {
            return@mutate done(state, ActionResult(false, "가지고 있는 옷이 아니에요"))
        }
        val label = if (defId == null) "옷을 벗었어요" else "${Catalog.byId(defId)?.name}을 입었어요"
        done(state.copy(pet = state.pet.copy(outfitId = defId)), ActionResult(true, label))
    }

    suspend fun setSlot(slot: DecorSlot, defId: String?): ActionResult = mutate { state, _ ->
        if (defId != null) {
            val item = Catalog.byId(defId)
            if (item == null || item.id !in state.owned || Catalog.slotOf(item.kind) != slot) {
                return@mutate done(state, ActionResult(false, "이 칸에 놓을 수 없어요"))
            }
        }
        val presets = state.presets.map { preset ->
            if (preset.index != state.activePreset) preset else applySlot(preset, slot, defId)
        }
        val name = if (defId == null) "치웠어요" else "${Catalog.byId(defId)?.name}을 두었어요"
        done(state.copy(presets = presets), ActionResult(true, name))
    }

    suspend fun selectPreset(index: Int): ActionResult = mutate { state, _ ->
        if (state.presets.none { it.index == index }) return@mutate done(state, ActionResult(false, "방이 없어요"))
        val name = state.presets.first { it.index == index }.name
        done(state.copy(activePreset = index), ActionResult(true, "$name 으로 바꿨어요"))
    }

    suspend fun cyclePreset(): ActionResult = mutate { state, _ ->
        val order = state.presets.sortedBy { it.index }
        val pos = order.indexOfFirst { it.index == state.activePreset }.coerceAtLeast(0)
        val next = order[(pos + 1) % order.size]
        done(state.copy(activePreset = next.index), ActionResult(true, "${next.name}으로 바꿨어요"))
    }

    private fun complete(state: PersistedState, id: String, now: Long): Mutate {
        val row = state.schedules.find { it.id == id } ?: return done(state, ActionResult(false, "일정을 찾지 못했어요"))
        if (row.done && row.repeat == RepeatRule.NONE) return done(state, ActionResult(false, "이미 완료한 일정이에요"))
        val last = state.completions.filter { it.scheduleId == id }.maxByOrNull { it.completedAt }
        if (last != null && now - last.completedAt < balance.repeatGuardMillis) {
            return done(state, ActionResult(false, "방금 완료했어요"))
        }
        val day = Korean.dayKey(now, zone)
        val rewardedToday = if (state.rewardDay == day) state.rewardCount else 0
        val decision = authority.grantCompletion(
            CompletionRequest(
                createdAt = row.createdAt,
                scheduledAt = row.timeMillis,
                now = now,
                stage = state.pet.stage,
                rewardedToday = rewardedToday,
            ),
        )
        if (decision is GrantDecision.Deny) return done(state, ActionResult(false, decision.message))
        val allow = decision as GrantDecision.Allow
        val beforeStage = state.pet.stage
        var pet = state.pet.copy(
            xp = state.pet.xp + allow.xp,
            happiness = (state.pet.happiness + allow.happiness).coerceAtMost(100),
            completions = state.pet.completions + 1,
        )
        pet = PetRules.evolve(pet, now, zone, balance)
        val schedules = state.schedules.map { item ->
            if (item.id != id) item
            else if (item.repeat == RepeatRule.NONE) item.copy(done = true, completedAt = now)
            else item.copy(
                done = false,
                completedAt = now,
                timeMillis = ScheduleRules.advance(item.timeMillis, item.repeat, item.tz, now),
            )
        }
        val message = when {
            beforeStage == Stage.EGG && pet.stage == Stage.BABY -> "알이 부화했어요! ${pet.name}를 만나요"
            pet.stage != beforeStage -> "${Korean.stageLabel(pet.stage)}로 자랐어요"
            else -> "완료! 경험치 +${allow.xp}, 코인 +${allow.coins}"
        }
        return done(
            state.copy(
                pet = pet,
                schedules = schedules,
                coins = state.coins + allow.coins,
                rewardDay = day,
                rewardCount = rewardedToday + 1,
                completions = state.completions + CompletionRecord(newId(now), id, now, allow.xp, allow.coins),
                ledger = state.ledger + LedgerEntry(newId(now), allow.coins, "COMPLETE", id, now, null),
            ),
            ActionResult(true, message),
        )
    }

    private fun purchase(state: PersistedState, defId: String, now: Long, idempotencyKey: String?): Mutate {
        val item = Catalog.byId(defId) ?: return done(state, ActionResult(false, "없는 물건이에요"))
        if (item.price <= 0) return done(state, ActionResult(false, "살 수 없는 물건이에요"))
        val offer = stateViewOffer(state, now)
        val price = if (offer.item.id == defId) offer.price else item.price
        val decision = authority.grantPurchase(
            PurchaseRequest(defId, price, state.coins, defId in state.owned, item.stackable),
        )
        if (decision is GrantDecision.Deny) return done(state, ActionResult(false, decision.message))
        val foods = state.foods.toMutableMap()
        val owned = state.owned.toMutableSet()
        val ownedAt = state.ownedAt.toMutableMap()
        if (item.kind == ItemKind.FOOD) {
            foods[item.id] = (foods[item.id] ?: 0) + 1
        } else {
            owned += item.id
            ownedAt.putIfAbsent(item.id, now)
        }
        return done(
            state.copy(
                coins = state.coins - price,
                foods = foods,
                owned = owned,
                ownedAt = ownedAt,
                ledger = state.ledger + LedgerEntry(newId(now), -price, "BUY", defId, now, idempotencyKey),
            ),
            ActionResult(true, "${item.name}을 샀어요"),
        )
    }

    private fun sellDef(
        state: PersistedState,
        defId: String,
        now: Long,
        fromPending: Boolean,
        instanceId: String?,
    ): Mutate {
        val def = Catalog.byId(defId) ?: return done(state, ActionResult(false, "선물을 찾지 못했어요"))
        val coins = state.coins + def.sellPrice
        val pet = if (fromPending) state.pet.copy(pendingGiftDefId = null, pendingGiftAt = null) else state.pet
        val gifts = if (instanceId == null) state.gifts else state.gifts.filterNot { it.instanceId == instanceId }
        return done(
            state.copy(
                pet = pet,
                gifts = gifts,
                coins = coins,
                ledger = state.ledger + LedgerEntry(newId(now), def.sellPrice, "SELL", defId, now, instanceId ?: "pending:$defId:$now"),
            ),
            ActionResult(true, "${def.name}을 ${def.sellPrice}코인에 팔았어요"),
        )
    }

    private fun stateViewOffer(state: PersistedState, now: Long) = app.mongle.logic.ShopRotation.today(now, zone, state.owned)

    private data class Mutate(val state: PersistedState, val result: ActionResult)

    private fun done(state: PersistedState, result: ActionResult) = Mutate(state, result)

    private suspend fun mutate(block: (PersistedState, Long) -> Mutate): ActionResult {
        var sync: List<ScheduledNotice>? = null
        var reload = false
        var immediate: Pair<String, String>? = null
        val result = mutex.withLock {
            val now = time.now()
            val raw = store.load() ?: Seed.create(now, balance)
            val resolved = resolveState(raw, now, zone, balance)
            var working = resolved
            val returned = raw.pet.isOut && !resolved.pet.isOut && resolved.pet.pendingGiftDefId != null
            if (returned) {
                val key = resolved.pet.pendingGiftDefId + ":" + (resolved.pet.pendingGiftAt ?: now)
                if (raw.notifiedGiftKey != key) {
                    val giftName = Catalog.byId(resolved.pet.pendingGiftDefId)?.name ?: "선물"
                    immediate = "선물이 도착했어요" to "${Korean.subject(resolved.pet.name)} $giftName${Korean.eulReul(giftName)} 가져왔어요"
                    working = resolved.copy(notifiedGiftKey = key)
                }
            }
            val outcome = block(working, now)
            val stamped = if (outcome.result.message.isNotBlank()) {
                outcome.state.copy(statusMessage = outcome.result.message, statusUntil = now + 8_000)
            } else {
                outcome.state
            }
            store.save(stamped)
            val view = project(stamped, now, zone, balance)
            _state.value = view
            val fp = view.fingerprint()
            reload = fp != lastFingerprint
            lastFingerprint = fp
            val notices = buildNotices(stamped, now)
            if (notices != lastNotices) {
                lastNotices = notices
                sync = notices
            }
            outcome.result
        }
        if (sync != null) notifier.sync(sync!!)
        if (immediate != null) notifier.notifyNow(immediate!!.first, immediate!!.second)
        if (reload || immediate != null) notifier.reloadWidgets()
        return result
    }

    private fun buildNotices(state: PersistedState, now: Long): List<ScheduledNotice> {
        val open = state.schedules.filter { !it.done }
        val overdue = open.filter { it.timeMillis <= now }.minByOrNull { it.timeMillis }
        val future = open.filter { it.timeMillis > now }.sortedBy { it.timeMillis }.take(12)
        val notices = mutableListOf<ScheduledNotice>()
        if (overdue != null) {
            notices += notice(state, overdue, now + 2_000)
        }
        future.forEach { notices += notice(state, it, it.timeMillis) }
        return notices
    }

    private fun notice(state: PersistedState, row: ScheduleRecord, trigger: Long) = ScheduledNotice(
        id = "sched-" + row.id,
        scheduleId = row.id,
        title = state.pet.name,
        body = Korean.reminder(state.pet.name, state.pet.stage, row.title),
        triggerAtMillis = trigger,
    )
}

private fun applySlot(preset: RoomPreset, slot: DecorSlot, defId: String?): RoomPreset = when (slot) {
    DecorSlot.WALL -> preset.copy(wallpaperId = defId ?: preset.wallpaperId)
    DecorSlot.FLOOR -> preset.copy(floorId = defId ?: preset.floorId)
    DecorSlot.BED -> preset.copy(bedId = defId)
    DecorSlot.RUG -> preset.copy(rugId = defId)
    DecorSlot.LAMP -> preset.copy(lampId = defId)
}

fun createGameService(
    driver: app.cash.sqldelight.db.SqlDriver,
    time: TimeProvider = SystemTime,
    zone: TimeZone = TimeZone.currentSystemDefault(),
): GameService {
    val db = app.mongle.db.MongleDatabase(driver)
    return GameService(app.mongle.data.SqlGameStore(db), time, zone)
}
