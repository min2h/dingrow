package app.mongle.data

import app.mongle.db.MongleDatabase
import app.mongle.logic.MetaCodec
import app.mongle.model.ActionRecord
import app.mongle.model.CompletionRecord
import app.mongle.model.GiftRecord
import app.mongle.model.LedgerEntry
import app.mongle.model.PersistedState
import app.mongle.model.PetRecord
import app.mongle.model.RoomPreset
import app.mongle.model.ScheduleRecord

class SqlGameStore(private val db: MongleDatabase) : GameStore {
    override fun load(): PersistedState? {
        val q = db.mongleQueries
        val pet = q.selectPet().executeAsOneOrNull() ?: return null
        val meta = q.selectMeta().executeAsList().associate { it.key to it.value_ }
        val wallet = q.selectWallet().executeAsOneOrNull() ?: 0L
        return PersistedState(
            pet = PetRecord(
                id = pet.id,
                name = pet.name,
                stage = parseStage(pet.stage),
                xp = pet.xp.toInt(),
                hunger = pet.hunger.toInt(),
                happiness = pet.happiness.toInt(),
                energy = pet.energy.toInt(),
                outfitId = pet.outfit_id,
                snapshotAt = pet.snapshot_at,
                createdAt = pet.created_at,
                stageSince = pet.stage_since,
                completions = pet.completions_total.toInt(),
                isOut = pet.is_out != 0L,
                outingStartedAt = pet.outing_started_at,
                outingReturnAt = pet.outing_return_at,
                outingSeed = pet.outing_seed,
                outingGiftDefId = pet.outing_gift_def_id,
                pendingGiftDefId = pet.pending_gift_def_id,
                pendingGiftAt = pet.pending_gift_at,
                speciesId = meta["species_id"]?.takeIf { it.isNotBlank() } ?: "base",
            ),
            schedules = q.selectSchedules().executeAsList().map { row ->
                MetaCodec.applySchedule(
                    ScheduleRecord(
                        id = row.id,
                        title = row.title,
                        timeMillis = row.time_millis,
                        repeat = parseRepeat(row.repeat_rule),
                        done = row.done != 0L,
                        createdAt = row.created_at,
                        completedAt = row.completed_at,
                        tz = row.tz,
                    ),
                    meta["sx:${row.id}"],
                )
            },
            completions = q.selectCompletions().executeAsList().map { row ->
                CompletionRecord(
                    id = row.id,
                    scheduleId = row.schedule_id,
                    completedAt = row.completed_at,
                    xp = row.xp.toInt(),
                    coins = row.coins.toInt(),
                )
            },
            foods = q.selectFood().executeAsList().associate { it.def_id to it.quantity.toInt() },
            gifts = q.selectGifts().executeAsList().map { row ->
                GiftRecord(row.instance_id, row.def_id, row.acquired_at)
            },
            owned = q.selectDecor().executeAsList().map { it.def_id }.toSet(),
            ownedAt = q.selectDecor().executeAsList().associate { it.def_id to it.acquired_at },
            coins = wallet.toInt(),
            ledger = q.selectLedger().executeAsList().map { row ->
                LedgerEntry(
                    id = row.id,
                    delta = row.delta.toInt(),
                    reason = row.reason,
                    refId = row.ref_id,
                    createdAt = row.created_at,
                    idempotencyKey = row.idempotency_key,
                )
            },
            presets = q.selectPresets().executeAsList().map { row ->
                RoomPreset(
                    index = row.preset_index.toInt(),
                    name = row.name,
                    wallpaperId = row.wallpaper_id,
                    floorId = row.floor_id,
                    bedId = row.bed_id,
                    rugId = row.rug_id,
                    lampId = row.lamp_id,
                )
            },
            activePreset = meta["active_preset"]?.toIntOrNull() ?: 0,
            onboarded = meta["onboarded"] == "1",
            rewardDay = meta["reward_day"].orEmpty(),
            rewardCount = meta["reward_count"]?.toIntOrNull() ?: 0,
            pendingConfirmDef = meta["pending_confirm_def"]?.takeIf { it.isNotBlank() },
            pendingConfirmUntil = meta["pending_confirm_until"]?.toLongOrNull() ?: 0L,
            lastOutingEndedAt = meta["last_outing_ended_at"]?.toLongOrNull() ?: 0L,
            notifiedGiftKey = meta["notified_gift_key"].orEmpty(),
            statusMessage = meta["status_message"].orEmpty(),
            statusUntil = meta["status_until"]?.toLongOrNull() ?: 0L,
            settings = MetaCodec.applySettings2(MetaCodec.parseSettings(meta["settings"]), meta["settings2"]),
            extras = meta.filterKeys { it.startsWith("x:") }.mapKeys { it.key.removePrefix("x:") },
            placements = MetaCodec.parsePlacements(meta["placements"]),
            premium = meta["premium"] == "1",
            iapTokens = MetaCodec.parseTokens(meta["iap_tokens"]),
            anim = MetaCodec.parseAnim(meta["anim"]),
            lastTipAt = meta["last_tip_at"]?.toLongOrNull() ?: 0L,
            tipIndex = meta["tip_index"]?.toIntOrNull() ?: 0,
            giftDryStreak = meta["gift_dry"]?.toIntOrNull() ?: 0,
            pendingDropKind = meta["pending_drop_kind"].orEmpty(),
            pendingDropCoins = meta["pending_drop_coins"]?.toIntOrNull() ?: 0,
            pendingSparkle = meta["pending_sparkle"] == "1",
            revealKind = meta["reveal_kind"].orEmpty(),
            revealLabel = meta["reveal_label"].orEmpty(),
            revealSparkle = meta["reveal_sparkle"] == "1",
            actionLog = q.selectActions().executeAsList().map { row ->
                ActionRecord(
                    key = row.idempotency_key,
                    ok = row.result_ok != 0L,
                    message = row.message,
                    at = row.created_at,
                )
            },
        )
    }

    override fun save(state: PersistedState) {
        val trimmed = state.trimmed()
        val q = db.mongleQueries
        val pet = trimmed.pet
        db.transaction {
            q.deletePets()
            q.insertPet(
                id = pet.id,
                name = pet.name,
                stage = pet.stage.name,
                xp = pet.xp.toLong(),
                hunger = pet.hunger.toLong(),
                happiness = pet.happiness.toLong(),
                energy = pet.energy.toLong(),
                outfitId = pet.outfitId,
                snapshotAt = pet.snapshotAt,
                createdAt = pet.createdAt,
                stageSince = pet.stageSince,
                completionsTotal = pet.completions.toLong(),
                isOut = if (pet.isOut) 1L else 0L,
                outingStartedAt = pet.outingStartedAt,
                outingReturnAt = pet.outingReturnAt,
                outingSeed = pet.outingSeed,
                outingGiftDefId = pet.outingGiftDefId,
                pendingGiftDefId = pet.pendingGiftDefId,
                pendingGiftAt = pet.pendingGiftAt,
            )
            q.deleteSchedules()
            trimmed.schedules.forEach { row ->
                q.insertSchedule(
                    id = row.id,
                    title = row.title,
                    timeMillis = row.timeMillis,
                    repeatRule = row.repeat.name,
                    done = if (row.done) 1L else 0L,
                    createdAt = row.createdAt,
                    completedAt = row.completedAt,
                    tz = row.tz,
                )
            }
            q.deleteCompletions()
            trimmed.completions.forEach { row ->
                q.insertCompletion(
                    id = row.id,
                    scheduleId = row.scheduleId,
                    completedAt = row.completedAt,
                    xp = row.xp.toLong(),
                    coins = row.coins.toLong(),
                )
            }
            q.deleteFood()
            trimmed.foods.forEach { (id, qty) ->
                if (qty > 0) q.insertFood(id, qty.toLong())
            }
            q.deleteGifts()
            trimmed.gifts.forEach { row ->
                q.insertGift(row.instanceId, row.defId, row.acquiredAt)
            }
            q.deleteDecor()
            trimmed.owned.forEach { id ->
                q.insertDecor(id, trimmed.ownedAt[id] ?: pet.createdAt)
            }
            q.deleteWallet()
            q.insertWallet(trimmed.coins.toLong())
            q.deleteLedger()
            trimmed.ledger.forEach { row ->
                q.insertLedger(
                    id = row.id,
                    delta = row.delta.toLong(),
                    reason = row.reason,
                    refId = row.refId,
                    createdAt = row.createdAt,
                    idempotencyKey = row.idempotencyKey,
                )
            }
            q.deletePresets()
            trimmed.presets.forEach { row ->
                q.insertPreset(
                    presetIndex = row.index.toLong(),
                    name = row.name,
                    wallpaperId = row.wallpaperId,
                    floorId = row.floorId,
                    bedId = row.bedId,
                    rugId = row.rugId,
                    lampId = row.lampId,
                )
            }
            q.deleteMeta()
            fun meta(key: String, value: String) = q.insertMeta(key, value)
            meta("active_preset", trimmed.activePreset.toString())
            meta("onboarded", if (trimmed.onboarded) "1" else "0")
            meta("reward_day", trimmed.rewardDay)
            meta("reward_count", trimmed.rewardCount.toString())
            meta("pending_confirm_def", trimmed.pendingConfirmDef.orEmpty())
            meta("pending_confirm_until", trimmed.pendingConfirmUntil.toString())
            meta("last_outing_ended_at", trimmed.lastOutingEndedAt.toString())
            meta("notified_gift_key", trimmed.notifiedGiftKey)
            meta("status_message", trimmed.statusMessage)
            meta("status_until", trimmed.statusUntil.toString())
            meta("species_id", pet.speciesId)
            meta("settings", MetaCodec.settings(trimmed.settings))
            meta("settings2", MetaCodec.settings2(trimmed.settings))
            trimmed.extras.forEach { (k, v) -> meta("x:$k", v) }
            meta("placements", MetaCodec.placements(trimmed.placements))
            meta("premium", if (trimmed.premium) "1" else "0")
            meta("iap_tokens", MetaCodec.tokens(trimmed.iapTokens))
            meta("anim", MetaCodec.anim(trimmed.anim))
            meta("last_tip_at", trimmed.lastTipAt.toString())
            meta("tip_index", trimmed.tipIndex.toString())
            meta("gift_dry", trimmed.giftDryStreak.toString())
            meta("pending_drop_kind", trimmed.pendingDropKind)
            meta("pending_drop_coins", trimmed.pendingDropCoins.toString())
            meta("pending_sparkle", if (trimmed.pendingSparkle) "1" else "0")
            meta("reveal_kind", trimmed.revealKind)
            meta("reveal_label", trimmed.revealLabel)
            meta("reveal_sparkle", if (trimmed.revealSparkle) "1" else "0")
            trimmed.schedules.forEach { row ->
                meta("sx:${row.id}", MetaCodec.scheduleExtra(row))
            }
            q.deleteActions()
            trimmed.actionLog.forEach { row ->
                q.insertAction(
                    idempotencyKey = row.key,
                    resultOk = if (row.ok) 1L else 0L,
                    message = row.message,
                    createdAt = row.at,
                )
            }
        }
    }
}
