package app.mongle.service

import app.mongle.data.GameStore
import app.mongle.data.Seed
import app.mongle.data.newId
import app.mongle.logic.BillingGateway
import app.mongle.l10n.AppLocale
import app.mongle.l10n.L10n
import app.mongle.logic.FakeBilling
import app.mongle.logic.FreeDecor
import app.mongle.logic.GiftDrops
import app.mongle.logic.Korean
import app.mongle.logic.NoticePlan
import app.mongle.logic.PetGesture
import app.mongle.logic.PetReactions
import app.mongle.logic.PetRules
import app.mongle.logic.ReactionBodies
import app.mongle.logic.Recurrence
import app.mongle.logic.ScheduleDraft
import app.mongle.logic.ScheduleRules
import app.mongle.logic.StoreProducts
import app.mongle.logic.Tips
import app.mongle.logic.ConditionRules
import app.mongle.logic.DayPhase
import app.mongle.logic.Secretary
import app.mongle.logic.Situation
import app.mongle.logic.Weather
import app.mongle.logic.WeatherSnapshot
import kotlinx.datetime.plus
import app.mongle.logic.addMillis
import app.mongle.logic.beginOuting
import app.mongle.logic.grantImmediate
import app.mongle.logic.keptGift
import app.mongle.logic.levelOf
import app.mongle.logic.parseMillis
import app.mongle.logic.resellAmount
import app.mongle.logic.resolveState
import app.mongle.logic.spec
import app.mongle.logic.withDrop
import app.mongle.model.ActionRecord
import app.mongle.model.ActionResult
import app.mongle.model.AlertStyle
import app.mongle.model.AppSettings
import app.mongle.model.Catalog
import app.mongle.model.CompletionRecord
import app.mongle.model.DecorSlot
import app.mongle.model.EditScope
import app.mongle.model.EndKind
import app.mongle.model.GameBalance
import app.mongle.model.ItemKind
import app.mongle.model.LegacyIds
import app.mongle.model.LedgerEntry
import app.mongle.model.PersistedState
import app.mongle.model.PetAnimState
import app.mongle.model.Placement
import app.mongle.model.RepeatRule
import app.mongle.model.RoomPreset
import app.mongle.model.ScheduleRecord
import app.mongle.model.SpeciesCatalog
import app.mongle.model.Stage
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
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
    private val billing: BillingGateway = FakeBilling(),
) {
    private val mutex = Mutex()
    private var lastNotices: List<ScheduledNotice>? = null
    private var forceNotices = false
    private var forceReload = false
    private var pendingImmediate: Pair<String, String>? = null
    val tipGapMillis = 6L * 60 * 60 * 1000
    private var lastFingerprint: String? = null

    private val initial = run {
        val loaded = store.load()
        if (loaded == null) {
            Seed.create(time.now(), balance).also { store.save(it) }
        } else {
            val migrated = LegacyIds.migrate(loaded)
            if (migrated != loaded) store.save(migrated)
            migrated
        }
    }
    private val _state = MutableStateFlow(project(initial, time.now(), zone, balance))
    val state: StateFlow<app.mongle.model.GameState> = _state

    init {
        AppLocale.preference = initial.settings.language
        lastFingerprint = _state.value.fingerprint()
    }

    suspend fun refresh(): ActionResult = mutate { state, _ -> done(state, ActionResult(true, "")) }

    /** 재부팅·시간대 변경 뒤 알람 매니저가 비어도 다음 회차를 다시 등록한다. */
    suspend fun refreshAlarms(): ActionResult {
        forceNotices = true
        return refresh()
    }

    suspend fun tick(): ActionResult = mutate { state, now ->
        val pet = state.pet
        val block = PetRules.outingBlock(state, pet, now, balance)
        if (block == null) done(beginOuting(state, now, balance), ActionResult(true, L10n.t("msg_outing_left_short")))
        else done(state, ActionResult(true, ""))
    }

    suspend fun setName(name: String): ActionResult = mutate { state, _ ->
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return@mutate done(state, ActionResult(false, L10n.t("msg_need_name")))
        if (trimmed.length > 12) return@mutate done(state, ActionResult(false, L10n.t("msg_name_len")))
        done(state.copy(pet = state.pet.copy(name = trimmed)), ActionResult(true, L10n.fmt("msg_named", trimmed)))
    }

    suspend fun completeOnboarding(name: String): ActionResult = mutate { state, _ ->
        val trimmed = name.trim().ifBlank { state.pet.name }
        if (trimmed.length > 12) return@mutate done(state, ActionResult(false, L10n.t("msg_name_len")))
        done(
            state.copy(pet = state.pet.copy(name = trimmed), onboarded = true),
            ActionResult(true, L10n.fmt("msg_hello", trimmed)),
        )
    }

    suspend fun addSchedule(title: String, timeMillis: Long, repeat: RepeatRule): ActionResult = mutate { state, now ->
        insertDraft(
            state,
            ScheduleDraft(
                title = title,
                timeMillis = timeMillis,
                repeat = repeat,
                leadsMinutes = listOf(state.settings.defaultLeadMinutes),
            ),
            now,
        )
    }

    suspend fun addDraft(draft: ScheduleDraft): ActionResult = mutate { state, now -> insertDraft(state, draft, now) }

    suspend fun editSchedule(rawId: String, scope: EditScope, draft: ScheduleDraft): ActionResult = mutate { state, now ->
        val id = rawId.substringBefore('@')
        val row = state.schedules.find { it.id == id } ?: return@mutate done(state, ActionResult(false, L10n.t("msg_missing_task")))
        val occurrence = rawId.substringAfter('@', "").toLongOrNull() ?: row.timeMillis
        val trimmed = draft.title.trim()
        if (trimmed.isEmpty()) return@mutate done(state, ActionResult(false, L10n.t("msg_need_task")))
        when (scope) {
            EditScope.ONE -> {
                val parent = row.copy(exdates = addMillis(row.exdates, occurrence))
                val advanced = if (occurrence == row.timeMillis || occurrence == row.snoozedFrom) {
                    val spec = parent.spec()
                    val next = Recurrence.nextOpen(occurrence, spec, now, closedOf(parent))
                    if (next == null) parent.copy(done = true, snoozedFrom = 0) else parent.copy(timeMillis = next, snoozedFrom = 0, anchorMillis = spec.anchorMillis)
                } else {
                    parent
                }
                val child = fromDraft(draft.copy(title = trimmed, repeat = RepeatRule.NONE), now).copy(seriesId = row.id)
                done(
                    state.copy(schedules = state.schedules.map { if (it.id == id) advanced else it } + child),
                    ActionResult(true, L10n.t("msg_edited_one")),
                )
            }
            EditScope.FOLLOWING -> {
                val tz = ScheduleRules.zoneOrUtc(row.tz.ifBlank { zone.id })
                val prev = Instant.fromEpochMilliseconds(occurrence).toLocalDateTime(tz).date.minus(1, DateTimeUnit.DAY)
                val endAt = LocalDateTime(prev.year, prev.monthNumber, prev.dayOfMonth, 23, 59).toInstant(tz).toEpochMilliseconds()
                val parent = row.copy(endKind = EndKind.UNTIL, endAt = endAt)
                val stopped = if (row.timeMillis >= occurrence) parent.copy(done = true, snoozedFrom = 0) else parent
                val child = fromDraft(draft.copy(title = trimmed), now)
                done(
                    state.copy(schedules = state.schedules.map { if (it.id == id) stopped else it } + child),
                    ActionResult(true, L10n.t("msg_edited_following")),
                )
            }
            EditScope.ALL -> {
                val updated = fromDraft(draft.copy(title = trimmed), row.createdAt).copy(
                    id = row.id,
                    seriesId = row.seriesId,
                    createdAt = row.createdAt,
                    doneDates = row.doneDates,
                    skipDates = row.skipDates,
                )
                done(
                    state.copy(schedules = state.schedules.map { if (it.id == id) updated else it }),
                    ActionResult(true, L10n.t("msg_edited_all")),
                )
            }
        }
    }

    suspend fun skipSchedule(rawId: String): ActionResult = mutate { state, now ->
        val located = locate(state, rawId) ?: return@mutate done(state, ActionResult(false, L10n.t("msg_missing_task")))
        val (row, at) = located
        val skipped = row.copy(skipDates = addMillis(row.skipDates, at))
        val nextRow = if (row.repeat == RepeatRule.NONE) {
            skipped.copy(done = true, snoozedFrom = 0)
        } else {
            val spec = skipped.spec()
            val next = Recurrence.nextOpen(at, spec, now, closedOf(skipped))
            if (next == null) skipped.copy(done = true, snoozedFrom = 0) else skipped.copy(timeMillis = next, snoozedFrom = 0, done = false, anchorMillis = spec.anchorMillis)
        }
        done(
            state.copy(schedules = state.schedules.map { if (it.id == row.id) nextRow else it }),
            ActionResult(true, L10n.t("msg_skipped")),
        )
    }

    suspend fun deleteSchedule(id: String): ActionResult = mutate { state, _ ->
        done(state.copy(schedules = state.schedules.filterNot { it.id == id }), ActionResult(true, L10n.t("msg_deleted")))
    }

    suspend fun snooze(rawId: String): ActionResult = mutate { state, now ->
        val located = locate(state, rawId) ?: return@mutate done(state, ActionResult(false, L10n.t("msg_missing_task")))
        val (row, at) = located
        val origin = if (row.snoozedFrom != 0L) row.snoozedFrom else at
        val anchor = if (row.anchorMillis > 0) row.anchorMillis else origin
        val moved = row.copy(
            timeMillis = now + 5 * 60 * 1000,
            done = false,
            anchorMillis = anchor,
            snoozedFrom = if (row.repeat == RepeatRule.NONE) 0L else origin,
        )
        val next = state.schedules.map { if (it.id == row.id) moved else it }
        done(state.copy(schedules = next), ActionResult(true, L10n.t("msg_snoozed")))
    }

    suspend fun acknowledge(rawId: String): ActionResult = mutate { state, now ->
        if (locate(state, rawId) == null && rawId.isNotBlank() && state.schedules.none { it.id == rawId.substringBefore('@') }) {
            return@mutate done(state, ActionResult(false, L10n.t("msg_missing_task")))
        }
        val full = state.pet.hunger >= balance.fullHunger
        val xp = 4
        var pet = state.pet.copy(
            hunger = if (full) state.pet.hunger else (state.pet.hunger + 18).coerceAtMost(100),
            happiness = (state.pet.happiness + if (full) 0 else 4).coerceAtMost(100),
            xp = state.pet.xp + xp,
        )
        pet = PetRules.evolve(pet, now, zone, balance)
        val message = if (full) L10n.fmt("msg_ack_full", xp) else L10n.fmt("msg_ack_feed", xp)
        val healed = if (full) ConditionRules.read(state.extras) else ConditionRules.heal(ConditionRules.read(state.extras), ConditionRules.RECOVERY_FEED)
        done(state.copy(pet = pet, extras = ConditionRules.write(state.extras, healed)), ActionResult(true, message))
    }

    suspend fun react(gesture: PetGesture): ActionResult = mutate { state, now ->
        val hour = Instant.fromEpochMilliseconds(now).toLocalDateTime(zone).hour
        val mood = PetRules.mood(state.pet, now, PetRules.timeOfDay(now, zone))
        val picked = PetReactions.pick(
            gesture,
            mood,
            hour,
            state.pet.hunger,
            levelOf(state.pet.stage),
            now xor state.pet.completions.toLong(),
        )
        val body = ReactionBodies.guess(picked)
        val anim = PetAnimState(
            clip = picked.clip.name.lowercase(),
            trigger = body,
            reactionId = body,
            until = now + 1_200,
        )
        done(state.copy(anim = anim), ActionResult(true, L10n.react(picked.id, picked.line)))
    }

    suspend fun updateSettings(next: AppSettings): ActionResult = mutate { state, _ ->
        if (state.settings.language != next.language) {
            forceNotices = true
            forceReload = true
        }
        AppLocale.preference = next.language
        done(state.copy(settings = next), ActionResult(true, L10n.t("msg_settings_saved")))
    }

    /** Medicine: 50 coins, only while sick, recovery +80. */
    suspend fun takeMedicine(): ActionResult = mutate { state, now ->
        val c = ConditionRules.read(state.extras)
        if (!c.sick) return@mutate done(state, ActionResult(false, L10n.t("msg_not_sick")))
        if (state.coins < ConditionRules.MEDICINE_PRICE) return@mutate done(state, ActionResult(false, L10n.t("msg_need_coins")))
        val healed = ConditionRules.heal(c, ConditionRules.RECOVERY_MEDICINE)
        val extras = ConditionRules.write(state.extras, healed) + if (!healed.sick) mapOf("recovered.at" to now.toString()) else emptyMap()
        val msg = if (healed.sick) L10n.fmt("msg_medicine", healed.recovery) else L10n.t("msg_recovered")
        done(
            state.copy(
                coins = state.coins - ConditionRules.MEDICINE_PRICE,
                extras = extras,
                ledger = state.ledger + LedgerEntry(newId(now), -ConditionRules.MEDICINE_PRICE, "MEDICINE", ConditionRules.MEDICINE_ID, now, null),
                anim = PetAnimState("happy", "medicine", "eat", now + 2_400),
            ),
            ActionResult(true, msg),
        )
    }

    /** Weather from the platform (null = location off or denied: weather features disappear). */
    suspend fun setWeather(snapshot: WeatherSnapshot?): ActionResult {
        var alert: Pair<String, String>? = null
        val r = mutate { state, now ->
            if (snapshot == null || !state.settings.useLocation) {
                return@mutate done(state.copy(extras = state.extras - "wx"), ActionResult(true, ""))
            }
            var extras = state.extras + ("wx" to Weather.encode(snapshot))
            val change = Weather.upcomingChange(snapshot, now)
            val nowKey = if (snapshot.kind.wet) "now:${snapshot.kind}" else null
            val key = change?.let { "${it.kind}:${it.atMillis / 3_600_000L}" } ?: nowKey?.let { "$it:${now / (6 * 3_600_000L)}" }
            if (state.settings.weatherAlerts && key != null && state.extras["wx.alert"] != key && !inQuiet(state.settings, now)) {
                extras = extras + ("wx.alert" to key)
                val kind = change?.kind ?: snapshot.kind
                val at = change?.let { L10n.clock(it.atMillis, zone) }
                alert = L10n.t("notice_wx_title_" + kind.name.lowercase()) to
                    (if (at != null) L10n.fmt("notice_wx_later", at, Weather.label(kind)) else L10n.fmt("notice_wx_now", Weather.label(kind), Weather.temp(snapshot.tempC, state.settings.fahrenheit)))
            }
            done(state.copy(extras = extras), ActionResult(true, ""))
        }
        alert?.let { notifier.notifyNow(it.first, it.second) }
        return r
    }

    /** Test/debug hook: force a condition state (used by screenshot tests and the debug menu). */
    suspend fun debugSetCondition(c: app.mongle.logic.ConditionState): ActionResult = mutate { state, _ ->
        done(state.copy(extras = ConditionRules.write(state.extras, c) - "line.text"), ActionResult(true, ""))
    }

    /** Asks 몽이 to mutter something to itself (used while wandering). */
    suspend fun selfTalk(): ActionResult = mutate { state, now -> done(rotateLine(state, now, selfTalk = true), ActionResult(true, "")) }

    private fun inQuiet(settings: AppSettings, now: Long): Boolean {
        val qs = settings.quietStartMinute; val qe = settings.quietEndMinute
        if (qs < 0 || qe < 0) return false
        val m = Instant.fromEpochMilliseconds(now).toLocalDateTime(zone).let { it.hour * 60 + it.minute }
        return if (qs <= qe) m in qs until qe else m >= qs || m < qe
    }

    internal fun weatherOf(state: PersistedState, now: Long): WeatherSnapshot? =
        if (!state.settings.useLocation) null else Weather.decode(state.extras["wx"])?.takeIf { it.usable(now) }

    internal fun situation(state: PersistedState, now: Long): Situation {
        val w = weatherOf(state, now)
        val c = ConditionRules.read(state.extras)
        val cond = ConditionRules.current(c, w, state.pet.outfitId, state.pet.hunger, state.pet.happiness)
        val today = Instant.fromEpochMilliseconds(now).toLocalDateTime(zone).date
        val open = state.schedules.filter { !it.done }
        val todays = state.schedules.filter { Instant.fromEpochMilliseconds(it.timeMillis).toLocalDateTime(zone).date == today }
        val next = open.filter { it.timeMillis >= now - 30 * 60_000L }.minByOrNull { it.timeMillis }
        val recoveredAt = state.extras["recovered.at"]?.toLongOrNull() ?: 0L
        return Situation(
            now = now, phase = DayPhase.of(now, zone), condition = cond, weather = w, fahrenheit = state.settings.fahrenheit,
            nextTitle = next?.title, nextPlace = next?.place?.takeIf { it.isNotBlank() }, nextAt = next?.timeMillis,
            nextClock = next?.let { L10n.clock(it.timeMillis, zone) },
            todayCount = todays.size, todayLeft = todays.count { !it.done }, hunger = state.pet.hunger,
            recovered = now - recoveredAt < 10 * 60_000L,
            afternoon = w?.let { Weather.afternoon(it, now, zone) }?.let { Weather.label(it.first) },
        )
    }

    /** Keeps the current speech line stable; replaces it when it ages out or the top priority changes. */
    internal fun rotateLine(state: PersistedState, now: Long, selfTalk: Boolean = false): PersistedState {
        val sit = situation(state, now)
        val top = Secretary.candidates(sit).firstOrNull()?.first.orEmpty()
        val at = state.extras["line.at"]?.toLongOrNull() ?: 0L
        val age = when (state.settings.bubbleFrequency) { 0 -> 6 * 60_000L; 2 -> 90_000L; else -> 3 * 60_000L }
        val sameTop = state.extras["line.top"] == top
        if (!selfTalk && sameTop && now - at < age && state.extras["line.text"] != null) return state
        val recent = state.extras["line.recent"].orEmpty().split(",").filter { it.isNotBlank() }
        val line = Secretary.pick(sit, recent, kotlin.random.Random(now xor state.pet.completions.toLong()), selfTalk)
        return state.copy(
            extras = state.extras + mapOf(
                "line.text" to line.text, "line.key" to line.key, "line.at" to now.toString(), "line.top" to top,
                "line.recent" to Secretary.remember(recent, line.key).joinToString(","),
            ),
        )
    }

    /**
     * Background tip notification. Called by the periodic worker; at most one tip every
     * [tipGapMillis], only 9:00-21:00 local, never in quiet hours, and only with tips on.
     * Returns the tip text when one was posted.
     */
    suspend fun maybeTipNotification(roll: Int = kotlin.random.Random.nextInt(100), chancePercent: Int = 35): String? {
        var posted: String? = null
        mutate { state, now ->
            val settings = state.settings
            val minute = Instant.fromEpochMilliseconds(now).toLocalDateTime(zone).let { it.hour * 60 + it.minute }
            val qs = settings.quietStartMinute
            val qe = settings.quietEndMinute
            val quiet = qs >= 0 && qe >= 0 && (if (qs <= qe) minute in qs until qe else minute >= qs || minute < qe)
            val ok = state.onboarded && settings.tipsEnabled && !quiet && minute in (9 * 60) until (21 * 60) &&
                (state.lastTipAt == 0L || now - state.lastTipAt >= tipGapMillis) && roll < chancePercent
            if (!ok) return@mutate done(state, ActionResult(true, ""))
            val line = Tips.next(state.tipIndex + roll)
            posted = line
            done(state.copy(lastTipAt = now, tipIndex = state.tipIndex + 1 + roll), ActionResult(true, ""))
        }
        posted?.let { notifier.notifyNow(L10n.t("tip_notice_title"), it) }
        return posted
    }

    suspend fun showTip(): ActionResult = mutate { state, now ->
        if (!state.settings.tipsEnabled) return@mutate done(state, ActionResult(false, L10n.t("msg_tips_off")))
        if (now - state.lastTipAt < 60_000 && state.lastTipAt != 0L) {
            return@mutate done(state, ActionResult(false, L10n.t("msg_tip_wait")))
        }
        val line = Tips.next(state.tipIndex)
        done(state.copy(lastTipAt = now, tipIndex = state.tipIndex + 1), ActionResult(true, line))
    }

    suspend fun placeDecor(defId: String): ActionResult = mutate { state, now ->
        if (!FreeDecor.allowed(defId, state.owned)) return@mutate done(state, ActionResult(false, L10n.t("msg_buy_first")))
        if (state.placements.any { it.defId == defId }) return@mutate done(state, ActionResult(true, L10n.t("msg_already_placed")))
        val placed = state.placements + Placement(defId, 50, 70, 0)
        done(state.copy(placements = placed), ActionResult(true, L10n.t("msg_placed_free")))
    }

    suspend fun rotatePlacement(defId: String): ActionResult = mutate { state, _ ->
        val next = state.placements.map { if (it.defId == defId) it.copy(rotation = (it.rotation + 90) % 360) else it }
        done(state.copy(placements = next), ActionResult(true, L10n.t("msg_rotated")))
    }

    suspend fun removePlacement(defId: String): ActionResult = mutate { state, _ ->
        done(state.copy(placements = state.placements.filterNot { it.defId == defId }), ActionResult(true, L10n.t("msg_cleared")))
    }

    suspend fun resellOwned(defId: String): ActionResult = mutate { state, now ->
        if (defId !in state.owned && state.gifts.none { it.defId == defId }) {
            return@mutate done(state, ActionResult(false, L10n.t("msg_not_owned")))
        }
        val gift = state.gifts.find { it.defId == defId }
        sellDef(state, defId, now, fromPending = false, instanceId = gift?.instanceId)
    }

    suspend fun purchaseProduct(productId: String): ActionResult {
        val receipt = billing.purchase(productId)
        if (!receipt.ok) return receiptToResult(receipt.message, false)
        return grantStoreProduct(productId, receipt.token)
    }

    suspend fun restorePurchases(): ActionResult {
        val receipts = billing.restore().filter { it.ok && it.token.isNotBlank() }
        if (receipts.isEmpty()) return receiptToResult(L10n.t("msg_nothing_restore"), false)
        var last = ActionResult(true, L10n.t("msg_restored"))
        receipts.forEach { receipt ->
            last = grantStoreProduct(receipt.productId, receipt.token)
        }
        return last
    }

    suspend fun grantStoreProduct(productId: String, token: String): ActionResult = mutate { state, now ->
        val product = StoreProducts.byId(productId) ?: return@mutate done(state, ActionResult(false, L10n.t("msg_unknown_product")))
        val key = "iap:$token"
        if (token.isBlank()) return@mutate done(state, ActionResult(false, L10n.t("msg_no_receipt")))
        if (token in state.iapTokens || state.ledger.any { it.idempotencyKey == key }) {
            return@mutate done(state, ActionResult(true, L10n.t("msg_already_granted")))
        }
        val owned = if (product.grantsLegendary) state.owned + "comet_shard" else state.owned
        done(
            state.copy(
                coins = state.coins + product.coins,
                premium = state.premium || product.premium,
                owned = owned,
                iapTokens = state.iapTokens + token,
                ledger = state.ledger + LedgerEntry(newId(now), product.coins, "IAP", productId, now, key),
            ),
            ActionResult(true, L10n.fmt("msg_coins_in", product.coins)),
        )
    }

    private suspend fun receiptToResult(message: String, ok: Boolean): ActionResult = mutate { state, _ ->
        done(state, ActionResult(ok, message))
    }

    suspend fun completeSchedule(id: String): ActionResult = mutate { state, now -> complete(state, id, now) }

    suspend fun completeNext(): ActionResult = mutate { state, now ->
        val next = state.schedules.filter { !it.done }.minByOrNull { it.timeMillis }
            ?: return@mutate done(state, ActionResult(false, L10n.t("msg_no_tasks_left")))
        complete(state, next.id, now)
    }

    suspend fun feed(foodId: String? = null): ActionResult = mutate { state, now ->
        val pet = state.pet
        if (pet.hunger >= balance.fullHunger) return@mutate done(state, ActionResult(false, L10n.t("msg_full")))
        val chosen = foodId ?: state.foods.entries.firstOrNull { it.value > 0 }?.key
        val def = Catalog.byId(chosen)
        if (def == null || def.kind != ItemKind.FOOD) return@mutate done(state, ActionResult(false, L10n.t("msg_no_food")))
        val qty = state.foods[def.id] ?: 0
        if (qty <= 0) return@mutate done(state, ActionResult(false, L10n.t("msg_no_food")))
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
            L10n.fmt("msg_grew", L10n.stage(updated.stage))
        } else {
            L10n.fmt("msg_fed", L10n.item(def.id), def.hunger)
        }
        val healed = ConditionRules.heal(ConditionRules.read(state.extras), ConditionRules.RECOVERY_FEED)
        done(state.copy(pet = updated, foods = foods, extras = ConditionRules.write(state.extras, healed)), ActionResult(true, message))
    }

    suspend fun startOuting(): ActionResult = mutate { state, now ->
        val reason = PetRules.outingBlock(state, state.pet, now, balance)
        if (reason != null) return@mutate done(state, ActionResult(false, reason))
        done(beginOuting(state, now, balance), ActionResult(true, L10n.t("msg_outing_left")))
    }

    suspend fun keepGift(): ActionResult = mutate { state, now ->
        if (state.pendingDropKind == "COINS") return@mutate claimCoins(state, now)
        val defId = state.pet.pendingGiftDefId ?: return@mutate done(state, ActionResult(false, L10n.t("msg_no_gift")))
        val def = Catalog.byId(defId)
        val gift = keptGift(defId, now)
        done(
            state.copy(
                pet = state.pet.copy(pendingGiftDefId = null, pendingGiftAt = null),
                gifts = state.gifts + gift,
                pendingDropKind = "",
                pendingDropCoins = 0,
                pendingSparkle = false,
                owned = if (defId in Catalog.uniqueIds) state.owned + defId else state.owned,
                ownedAt = if (defId in Catalog.uniqueIds) state.ownedAt + (defId to now) else state.ownedAt,
            ),
            ActionResult(true, L10n.fmt("msg_kept", def?.let { L10n.item(it.id) } ?: L10n.t("gift"))),
        )
    }

    suspend fun sellPendingOrLatest(): ActionResult = mutate { state, now ->
        if (state.pendingDropKind == "COINS") return@mutate claimCoins(state, now)
        val pending = state.pet.pendingGiftDefId
        if (pending != null) return@mutate sellDef(state, pending, now, fromPending = true, instanceId = null)
        val latest = state.gifts.maxByOrNull { it.acquiredAt }
            ?: return@mutate done(state, ActionResult(false, L10n.t("msg_no_sell")))
        sellDef(state, latest.defId, now, fromPending = false, instanceId = latest.instanceId)
    }

    suspend fun sellGift(instanceId: String): ActionResult = mutate { state, now ->
        val gift = state.gifts.find { it.instanceId == instanceId }
            ?: return@mutate done(state, ActionResult(false, L10n.t("msg_gift_missing")))
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
            ActionResult(true, L10n.fmt("confirm_buy", L10n.item(offer.item.id), offer.price)),
        )
    }

    suspend fun confirmBuy(): ActionResult = mutate { state, now ->
        val defId = state.pendingConfirmDef ?: return@mutate done(state, ActionResult(false, L10n.t("msg_no_confirm")))
        if (now > state.pendingConfirmUntil) {
            return@mutate done(
                state.copy(pendingConfirmDef = null, pendingConfirmUntil = 0),
                ActionResult(false, L10n.t("msg_confirm_timeout")),
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
            ActionResult(true, L10n.t("msg_buy_cancelled")),
        )
    }

    suspend fun equipOutfit(defId: String?): ActionResult = mutate { state, _ ->
        if (defId != null && (defId !in state.owned || Catalog.byId(defId)?.kind != ItemKind.OUTFIT)) {
            return@mutate done(state, ActionResult(false, L10n.t("msg_not_outfit")))
        }
        val label = if (defId == null) L10n.t("msg_outfit_off") else L10n.fmt("msg_outfit_on", L10n.item(defId))
        done(state.copy(pet = state.pet.copy(outfitId = defId)), ActionResult(true, label))
    }

    suspend fun equipSpecies(defId: String): ActionResult = mutate { state, now ->
        val species = SpeciesCatalog.all.find { it.id == defId }
            ?: return@mutate done(state, ActionResult(false, L10n.t("msg_unknown_friend")))
        val free = species.price == 0
        if (defId !in state.owned && !free) {
            return@mutate done(state, ActionResult(false, L10n.t("msg_not_met")))
        }
        val owned = if (defId in state.owned) state.owned else state.owned + defId
        val ownedAt = if (defId in state.ownedAt) state.ownedAt else state.ownedAt + (defId to now)
        done(
            state.copy(pet = state.pet.copy(speciesId = species.id), owned = owned, ownedAt = ownedAt),
            ActionResult(true, L10n.fmt("msg_brought", L10n.species(species.id))),
        )
    }

    suspend fun setSlot(slot: DecorSlot, defId: String?): ActionResult = mutate { state, _ ->
        if (defId != null) {
            val item = Catalog.byId(defId)
            if (item == null || item.id !in state.owned || Catalog.slotOf(item.kind) != slot) {
                return@mutate done(state, ActionResult(false, L10n.t("msg_bad_slot")))
            }
        }
        val presets = state.presets.map { preset ->
            if (preset.index != state.activePreset) preset else applySlot(preset, slot, defId)
        }
        val name = if (defId == null) L10n.t("msg_slot_cleared") else L10n.fmt("msg_slot_set", L10n.item(defId))
        done(state.copy(presets = presets), ActionResult(true, name))
    }

    suspend fun selectPreset(index: Int): ActionResult = mutate { state, _ ->
        if (state.presets.none { it.index == index }) return@mutate done(state, ActionResult(false, L10n.t("msg_no_room")))
        val picked = state.presets.first { it.index == index }
        done(state.copy(activePreset = index), ActionResult(true, L10n.fmt("msg_room_changed", roomTo(L10n.preset(picked.wallpaperId)))))
    }

    suspend fun cyclePreset(): ActionResult = mutate { state, _ ->
        val order = state.presets.sortedBy { it.index }
        val pos = order.indexOfFirst { it.index == state.activePreset }.coerceAtLeast(0)
        val next = order[(pos + 1) % order.size]
        done(state.copy(activePreset = next.index), ActionResult(true, L10n.fmt("msg_room_changed", roomTo(L10n.preset(next.wallpaperId)))))
    }

    private fun complete(state: PersistedState, rawId: String, now: Long): Mutate {
        val located = locate(state, rawId) ?: return done(state, ActionResult(false, L10n.t("msg_missing_task")))
        val (row, consumed) = located
        val id = row.id
        if (row.done && row.repeat == RepeatRule.NONE) return done(state, ActionResult(false, L10n.t("msg_already_done")))
        if (consumed in parseMillis(row.doneDates)) return done(state, ActionResult(false, L10n.t("msg_already_done")))
        val last = state.completions.filter { it.scheduleId == id }.maxByOrNull { it.completedAt }
        if (last != null && now - last.completedAt < balance.repeatGuardMillis) {
            return done(state, ActionResult(false, L10n.t("msg_just_done")))
        }
        val day = Korean.dayKey(now, zone)
        val rewardedToday = if (state.rewardDay == day) state.rewardCount else 0
        val decision = authority.grantCompletion(
            CompletionRequest(
                createdAt = row.createdAt,
                scheduledAt = consumed,
                now = now,
                stage = state.pet.stage,
                rewardedToday = rewardedToday,
            ),
        )
        if (decision is GrantDecision.Deny) return done(state, ActionResult(false, decision.message))
        val allow = decision as GrantDecision.Allow
        val beforeStage = state.pet.stage
        val condBefore = ConditionRules.read(state.extras)
        val gainedXp = ConditionRules.xp(allow.xp, condBefore)
        val condAfter = ConditionRules.heal(condBefore, ConditionRules.RECOVERY_PER_TASK)
        var pet = state.pet.copy(
            xp = state.pet.xp + gainedXp,
            happiness = (state.pet.happiness + allow.happiness).coerceAtMost(100),
            completions = state.pet.completions + 1,
        )
        pet = PetRules.evolve(pet, now, zone, balance)
        val schedules = state.schedules.map { item ->
            if (item.id != id) item else finishOccurrence(item, consumed, now)
        }
        val message = when {
            beforeStage == Stage.EGG && pet.stage == Stage.BABY -> L10n.fmt("msg_hatched", pet.name)
            pet.stage != beforeStage -> L10n.fmt("msg_grew", L10n.stage(pet.stage))
            condBefore.sick && !condAfter.sick -> L10n.t("msg_recovered")
            condBefore.sick -> L10n.fmt("msg_complete_sick", gainedXp, allow.coins, condAfter.recovery)
            else -> L10n.fmt("msg_complete", gainedXp, allow.coins)
        }
        var next = state.copy(
            extras = ConditionRules.write(state.extras, condAfter) + if (condBefore.sick && !condAfter.sick) mapOf("recovered.at" to now.toString()) else emptyMap(),
            pet = pet,
            schedules = schedules,
            coins = state.coins + allow.coins,
            rewardDay = day,
            rewardCount = rewardedToday + 1,
            completions = state.completions + CompletionRecord(newId(now), id, now, gainedXp, allow.coins),
            ledger = state.ledger + LedgerEntry(newId(now), allow.coins, "COMPLETE", id, now, null),
        )
        if (next.pet.pendingGiftDefId == null && next.pendingDropKind.isBlank()) {
            val chanceSeed = now xor id.hashCode().toLong() xor next.pet.completions.toLong()
            if (GiftDrops.completionHit(chanceSeed)) {
                val drop = GiftDrops.roll(chanceSeed xor 0xC0FFEE, levelOf(next.pet.stage), next.giftDryStreak)
                next = next.grantImmediate(drop, now)
            }
        }
        return done(next, ActionResult(true, message))
    }

    private fun claimCoins(state: PersistedState, now: Long): Mutate {
        val amount = state.pendingDropCoins
        if (state.pendingDropKind != "COINS" || amount <= 0) {
            return done(state, ActionResult(false, L10n.t("msg_no_coins_in")))
        }
        return done(
            state.copy(
                coins = state.coins + amount,
                pendingDropKind = "",
                pendingDropCoins = 0,
                pendingSparkle = false,
                pet = state.pet.copy(pendingGiftDefId = null, pendingGiftAt = null),
                ledger = state.ledger + LedgerEntry(newId(now), amount, "GIFT_COIN", null, now, "gift-coin:$now"),
            ),
            ActionResult(true, L10n.fmt("msg_coins_got", amount)),
        )
    }

    private fun purchase(state: PersistedState, defId: String, now: Long, idempotencyKey: String?): Mutate {
        val item = Catalog.byId(defId) ?: return done(state, ActionResult(false, L10n.t("msg_no_item")))
        if (item.price <= 0) return done(state, ActionResult(false, L10n.t("msg_unsellable")))
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
            ActionResult(true, L10n.fmt("msg_bought", L10n.item(item.id))),
        )
    }

    private fun sellDef(
        state: PersistedState,
        defId: String,
        now: Long,
        fromPending: Boolean,
        instanceId: String?,
    ): Mutate {
        val def = Catalog.byId(defId) ?: return done(state, ActionResult(false, L10n.t("msg_gift_missing")))
        val price = resellAmount(def)
        val coins = state.coins + price
        val pet = if (fromPending) state.pet.copy(pendingGiftDefId = null, pendingGiftAt = null) else state.pet
        val gifts = if (instanceId == null) state.gifts else state.gifts.filterNot { it.instanceId == instanceId }
        val owned = if (fromPending) state.owned else state.owned - defId
        return done(
            state.copy(
                pet = pet,
                gifts = gifts,
                owned = owned,
                coins = coins,
                pendingDropKind = if (fromPending) "" else state.pendingDropKind,
                pendingDropCoins = if (fromPending) 0 else state.pendingDropCoins,
                pendingSparkle = if (fromPending) false else state.pendingSparkle,
                ledger = state.ledger + LedgerEntry(newId(now), price, "SELL", defId, now, instanceId ?: "pending:$defId:$now"),
            ),
            ActionResult(true, L10n.fmt("msg_sold", L10n.item(def.id), price)),
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
            val raw = LegacyIds.migrate(store.load() ?: Seed.create(now, balance))
            val resolved = resolveState(raw, now, zone, balance)
            var working = resolved
            val returned = raw.pet.isOut && !resolved.pet.isOut &&
                (resolved.pet.pendingGiftDefId != null || resolved.pendingDropKind == "COINS")
            if (returned) {
                val key = (resolved.pet.pendingGiftDefId ?: resolved.pendingDropKind) + ":" + (resolved.pet.pendingGiftAt ?: now)
                if (raw.notifiedGiftKey != key) {
                    val giftName = resolved.pet.pendingGiftDefId?.let { L10n.item(it) }
                        ?: if (resolved.pendingDropKind == "COINS") L10n.fmt("msg_coins_got", resolved.pendingDropCoins) else L10n.t("gift")
                    val rarity = GiftDrops.label(resolved.pendingDropKind)
                    immediate = L10n.t("msg_gift_here") to L10n.fmt("msg_brought_gift", if (AppLocale.code() == "ko") Korean.subject(resolved.pet.name) else resolved.pet.name, giftName, rarity)
                    working = resolved.copy(notifiedGiftKey = key)
                }
            }
            working = run {
                val w = weatherOf(working, now)
                val roll = ((now / 3_600_000L) xor working.pet.id.hashCode().toLong()).mod(100L).toInt()
                val c = ConditionRules.tick(ConditionRules.read(working.extras), now, w, working.pet.outfitId, working.pet.hunger, roll)
                working.copy(extras = ConditionRules.write(working.extras, c))
            }
            val outcome0 = block(working, now)
            val outcome = outcome0.copy(state = rotateLine(outcome0.state, now))
            val stamped = if (outcome.result.message.isNotBlank()) {
                outcome.state.copy(statusMessage = outcome.result.message, statusUntil = now + 8_000)
            } else {
                outcome.state
            }
            store.save(stamped)
            val view = project(stamped, now, zone, balance)
            _state.value = view
            val fp = view.fingerprint()
            reload = fp != lastFingerprint || forceReload
            forceReload = false
            lastFingerprint = fp
            val notices = buildNotices(stamped, now)
            if (forceNotices || notices != lastNotices) {
                forceNotices = false
                lastNotices = notices
                sync = notices
            }
            outcome.result
        }
        pendingImmediate?.let { notifier.notifyNow(it.first, it.second) }
        pendingImmediate = null
        if (sync != null) notifier.sync(sync!!)
        if (immediate != null) notifier.notifyNow(immediate!!.first, immediate!!.second)
        if (reload || immediate != null) notifier.reloadWidgets()
        return result
    }

    private fun buildNotices(state: PersistedState, now: Long): List<ScheduledNotice> {
        val notices = mutableListOf<ScheduledNotice>()
        briefingNotice(state, now)?.let { notices += it }
        if (state.settings.scheduleAlerts) state.schedules.filter { !it.done }.forEach { row ->
            val moment = row.timeMillis
            val leads = if (row.snoozedFrom != 0L) listOf(0) else row.leadsMinutes.ifEmpty { listOf(0) }
            val tz = row.tz.ifBlank { zone.id }
            val triggers = NoticePlan.triggers(
                moment,
                leads,
                now,
                state.settings.quietStartMinute,
                state.settings.quietEndMinute,
                tz,
            )
            if (triggers.isEmpty() && moment <= now) {
                notices += notice(state, row, now + 2_000, 0)
            } else {
                triggers.forEach { trigger ->
                    val lead = ((moment - trigger) / 60_000L).toInt().coerceAtLeast(0)
                    notices += notice(state, row, trigger, lead)
                }
            }
        }
        return notices.take(24)
    }

    /** Next daily briefing at the chosen minute: today's schedules + weather. */
    internal fun briefingNotice(state: PersistedState, now: Long): ScheduledNotice? {
        if (!state.settings.briefingEnabled || !state.onboarded) return null
        val local = Instant.fromEpochMilliseconds(now).toLocalDateTime(zone)
        var day = local.date
        val minute = state.settings.briefingMinute
        fun at(d: kotlinx.datetime.LocalDate) = kotlinx.datetime.LocalDateTime(d.year, d.monthNumber, d.dayOfMonth, minute / 60, minute % 60).toInstant(zone).toEpochMilliseconds()
        var trigger = at(day)
        if (trigger <= now) { day = day.plus(1, kotlinx.datetime.DateTimeUnit.DAY); trigger = at(day) }
        val todays = state.schedules.filter { !it.done && Instant.fromEpochMilliseconds(it.timeMillis).toLocalDateTime(zone).date == day }.sortedBy { it.timeMillis }
        val list = todays.take(4).joinToString(", ") { L10n.clock(it.timeMillis, zone) + " " + (if (it.place.isNotBlank()) it.place + " " else "") + it.title }
        val w = weatherOf(state, now)
        val pm = w?.let { Weather.afternoon(it, trigger - 1, zone) ?: Weather.afternoon(it, now, zone) }
        val wx = w?.let {
            L10n.fmt("brief_weather", Weather.label(it.kind), Weather.temp(it.tempC, state.settings.fahrenheit)) + " " +
                (pm?.let { p -> L10n.fmt("brief_pm", Weather.label(p.first), Weather.temp(p.second, state.settings.fahrenheit)) + " " }.orEmpty())
        }.orEmpty()
        val body = wx + if (todays.isEmpty()) L10n.t("brief_none") else L10n.fmt("brief_list", todays.size, list)
        return ScheduledNotice(id = "brief-$day", scheduleId = "", title = L10n.t("brief_title"), body = body, triggerAtMillis = trigger, alert = "INFO")
    }

    private fun notice(state: PersistedState, row: ScheduleRecord, trigger: Long, lead: Int): ScheduledNotice {
        val tz = ScheduleRules.zoneOrUtc(row.tz.ifBlank { zone.id })
        val clock = L10n.clock(row.timeMillis, tz)
        return ScheduledNotice(
            id = "sched-${row.id}-$lead-$trigger",
            scheduleId = row.id,
            title = L10n.calling(state.pet.name),
            body = L10n.noticeBody(clock, if (row.place.isNotBlank()) row.place + " " + row.title else row.title),
            triggerAtMillis = trigger,
            alert = row.alert.name,
            fullScreen = row.alert == AlertStyle.SOUND,
            leadMinutes = lead,
        )
    }

    private fun insertDraft(state: PersistedState, draft: ScheduleDraft, now: Long): Mutate {
        val trimmed = draft.title.trim()
        if (trimmed.isEmpty()) return done(state, ActionResult(false, L10n.t("msg_need_task")))
        if (trimmed.length > 40) return done(state, ActionResult(false, L10n.t("msg_title_len")))
        if (state.schedules.count { !it.done } >= balance.maxSchedules) {
            return done(state, ActionResult(false, L10n.fmt("msg_too_many", balance.maxSchedules)))
        }
        val row = fromDraft(draft.copy(title = trimmed), now)
        if (state.settings.newScheduleConfirm) {
            val label = L10n.formatWhen(row.timeMillis, zone) + (if (row.place.isNotBlank()) " · " + row.place else "")
            pendingImmediate = L10n.t("notice_new_title") to L10n.fmt("notice_new_body", label, trimmed)
        }
        return done(state.copy(schedules = state.schedules + row), ActionResult(true, L10n.fmt("msg_will_remind", trimmed)))
    }

    private fun fromDraft(draft: ScheduleDraft, createdAt: Long): ScheduleRecord {
        val leads = draft.leadsMinutes.ifEmpty { listOf(0) }.distinct().sorted()
        return ScheduleRecord(
            id = newId(createdAt),
            title = draft.title.trim(),
            timeMillis = draft.timeMillis,
            repeat = draft.repeat,
            done = false,
            createdAt = createdAt,
            completedAt = null,
            tz = zone.id,
            leadsMinutes = leads,
            alert = draft.alert,
            category = draft.category.ifBlank { "task" },
            place = draft.place.trim().take(40),
            memo = draft.memo.trim().take(80),
            endMillis = draft.endMillis,
            weekdays = draft.weekdays,
            interval = draft.interval.coerceAtLeast(1),
            monthOrdinal = draft.monthOrdinal,
            endKind = draft.endKind,
            endAt = draft.endAt,
            endCount = draft.endCount,
            anchorMillis = draft.timeMillis,
        )
    }

    private fun finishOccurrence(row: ScheduleRecord, consumed: Long, now: Long): ScheduleRecord {
        val marked = row.copy(doneDates = addMillis(row.doneDates, consumed), completedAt = now, snoozedFrom = 0)
        if (row.repeat == RepeatRule.NONE) return marked.copy(done = true)
        val spec = marked.spec()
        val next = Recurrence.nextOpen(consumed, spec, now, closedOf(marked))
        return if (next == null) {
            marked.copy(done = true)
        } else {
            marked.copy(done = false, timeMillis = next, anchorMillis = spec.anchorMillis)
        }
    }

    private fun locate(state: PersistedState, rawId: String): Pair<ScheduleRecord, Long>? {
        val id = rawId.substringBefore('@')
        val row = state.schedules.find { it.id == id } ?: return null
        val specified = rawId.substringAfter('@', "").toLongOrNull()
        val at = specified ?: if (row.snoozedFrom != 0L) row.snoozedFrom else row.timeMillis
        return row to at
    }

    private fun closedOf(row: ScheduleRecord): Set<Long> =
        parseMillis(row.doneDates) + parseMillis(row.skipDates) + parseMillis(row.exdates)
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
    billing: app.mongle.logic.BillingGateway = app.mongle.logic.FakeBilling(),
): GameService {
    val db = app.mongle.db.MongleDatabase(driver)
    return GameService(app.mongle.data.SqlGameStore(db), time, zone, billing = billing)
}

/** Korean 으로/로 particle (ㄹ batchim and no batchim take 로); English uses the name as is. */
internal fun roomTo(name: String): String {
    if (AppLocale.code() != "ko") return name
    val last = name.lastOrNull() ?: return name
    if (last !in '가'..'힣') return name + "(으)로"
    val jong = (last - '가') % 28
    return name + if (jong == 0 || jong == 8) "로" else "으로"
}
