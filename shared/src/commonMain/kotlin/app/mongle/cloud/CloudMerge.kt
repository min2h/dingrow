package app.mongle.cloud

import app.mongle.model.LedgerEntry
import app.mongle.model.PersistedState
import app.mongle.logic.StoreProducts

/** A coin credit written by the Cloud Function after it verified a store purchase (users/{uid}/credits/{id}). */
data class ServerCredit(
    val token: String,
    val productId: String,
    val coins: Int,
    val premium: Boolean = false,
    val legendary: Boolean = false,
    val createdAt: Long = 0L,
)

/**
 * Pure sync rules (offline-first):
 * - Whole-document fields (pet, level/EXP, schedules, room, outfits, settings) follow the newer write by
 *   server timestamp ("last writer wins").
 * - Coins are ledger-based: the loser's ledger entries written since the last successful sync are replayed
 *   onto the winner, deduplicated by entry id and idempotency key, so no grant is ever counted twice.
 * - Owned items, IAP tokens and completions are unions (minus items the replayed ledger sold).
 */
object CloudMerge {
    const val LAST_SYNC = "cloud:lastSync"
    const val CHANGED_AT = "cloud:changedAt"

    fun lastSync(s: PersistedState): Long = s.extras[LAST_SYNC]?.toLongOrNull() ?: 0L
    fun changedAt(s: PersistedState): Long = s.extras[CHANGED_AT]?.toLongOrNull() ?: 0L

    fun merge(local: PersistedState, remote: PersistedState?, remoteUpdatedAt: Long): PersistedState {
        if (remote == null) return local
        val since = lastSync(local)
        val localDirty = changedAt(local) > since
        val remoteDirty = remoteUpdatedAt > since
        // Only one side changed since the last sync: it wins outright. Both changed: newer timestamp wins.
        val localWins = when {
            since == 0L -> false
            localDirty && !remoteDirty -> true
            !localDirty -> false
            else -> changedAt(local) > remoteUpdatedAt
        }
        val w = if (localWins) local else remote
        val l = if (localWins) remote else local
        val wIds = w.ledger.map { it.id }.toSet()
        val wKeys = w.ledger.mapNotNull { it.idempotencyKey }.toSet() + w.iapTokens.map { "iap:$it" }
        val firstSync = since == 0L
        val replay = l.ledger.filter { e ->
            e.createdAt > since && e.id !in wIds && (e.idempotencyKey == null || e.idempotencyKey !in wKeys) &&
                // First sync of this install onto an existing account: the account wins; only real-money grants carry over.
                (!firstSync || e.reason == "IAP")
        }
        val soldByReplay = replay.filter { it.reason == "SELL" }.mapNotNull { it.refId }.toSet()
        val soldInstances = replay.filter { it.reason == "SELL" }.mapNotNull { it.idempotencyKey }.toSet()
        val wSoldInstances = w.ledger.filter { it.reason == "SELL" }.mapNotNull { it.idempotencyKey }.toSet()
        val newerOwned = if (firstSync) emptyList() else l.owned.filter { (l.ownedAt[it] ?: 0L) > since && it !in soldByReplay }
        val owned = (w.owned - soldByReplay) + newerOwned + replay.filter { it.reason == "BUY" }.mapNotNull { it.refId }
            .filter { it !in soldByReplay && it in l.owned }
        val ownedAt = (l.ownedAt + w.ownedAt).filterKeys { it in owned }
        val gifts = w.gifts.filterNot { it.instanceId in soldInstances } +
            l.gifts.filter { g -> !firstSync && g.acquiredAt > since && w.gifts.none { it.instanceId == g.instanceId } && g.instanceId !in wSoldInstances }
        val completions = (w.completions + l.completions.filter { c -> !firstSync && w.completions.none { it.id == c.id } }).sortedBy { it.completedAt }
        val iapPremium = l.iapTokens.any { it !in w.iapTokens } && l.premium
        val iapLegendary = replay.any { it.reason == "IAP" && StoreProducts.byId(it.refId ?: "")?.grantsLegendary == true }
        val merged = w.copy(
            coins = (w.coins + replay.sumOf { it.delta }).coerceAtLeast(0),
            ledger = (w.ledger + replay).sortedBy { it.createdAt },
            owned = if (iapLegendary) owned + "comet_shard" else owned,
            ownedAt = ownedAt,
            gifts = gifts,
            completions = completions,
            iapTokens = w.iapTokens + l.iapTokens,
            premium = w.premium || iapPremium,
        )
        return merged.copy(extras = merged.extras + (CHANGED_AT to maxOf(changedAt(local), remoteUpdatedAt).toString()))
    }

    /** Applies server-verified credits once each (key "iap:<token>", shared with the local grant path). */
    fun applyCredits(state: PersistedState, credits: List<ServerCredit>, now: Long): PersistedState {
        var s = state
        credits.forEach { c ->
            val key = "iap:${c.token}"
            if (c.token.isBlank() || c.token in s.iapTokens || s.ledger.any { it.idempotencyKey == key }) return@forEach
            val product = StoreProducts.byId(c.productId)
            val coins = product?.coins ?: c.coins
            s = s.copy(
                coins = s.coins + coins,
                premium = s.premium || c.premium || product?.premium == true,
                owned = if (c.legendary || product?.grantsLegendary == true) s.owned + "comet_shard" else s.owned,
                iapTokens = s.iapTokens + c.token,
                ledger = s.ledger + LedgerEntry("srv-${c.token.take(24)}", coins, "IAP", c.productId, if (c.createdAt > 0) c.createdAt else now, key),
            )
        }
        return s
    }
}
