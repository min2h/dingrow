package app.mongle.cloud

import app.mongle.model.PersistedState
import app.mongle.service.Backup
import app.mongle.service.GameService

/** Result of a server purchase verification. */
sealed interface VerifyResult {
    /** The server verified and credited (or had already credited) this token. */
    data class Verified(val coins: Int) : VerifyResult
    /** The store said the token is invalid: do not grant. */
    data class Rejected(val reason: String) : VerifyResult
    /** Functions not deployed / offline: the client falls back to its local idempotent grant. */
    data object Unavailable : VerifyResult
}

/** Firestore document users/{uid}. [state] is the versioned Backup JSON; the rest is a readable summary. */
data class CloudDoc(
    val state: String,
    val updatedAt: Long,
    val summary: Map<String, Any?> = emptyMap(),
)

/** Platform bridge (Android: Firebase SDK; iOS: Swift Firebase SDK hooks; tests: [FakeCloudBackend]). */
interface CloudBackend {
    /** False when no google-services.json / GoogleService-Info.plist is configured. */
    val available: Boolean
    /** Current Firebase uid, signing in anonymously when nobody is signed in. */
    suspend fun ensureUser(): String?
    suspend fun fetch(uid: String): CloudDoc?
    suspend fun credits(uid: String): List<ServerCredit>
    /** Writes the doc with a server timestamp; returns that timestamp (ms) or null on failure. */
    suspend fun push(uid: String, doc: CloudDoc): Long?
    /** Links the anonymous user to a Google/Apple credential; on "already linked" signs into that account. */
    suspend fun link(provider: String, idToken: String, nonce: String? = null): String?
    suspend fun verifyPurchase(platform: String, productId: String, token: String): VerifyResult
    /** Deletes users/{uid} (+ credits) and the Auth user. */
    suspend fun deleteAccount(): Boolean
    suspend fun signOut() {}
}

enum class SyncStatus { OFF, OK, OFFLINE }

fun summaryOf(s: PersistedState): Map<String, Any?> = mapOf(
    "coins" to s.coins,
    "owned" to s.owned.sorted(),
    "stage" to s.pet.stage.name,
    "xp" to s.pet.xp,
    "outfit" to s.pet.outfitId,
    "species" to s.pet.speciesId,
    "condition" to s.extras["cond"],
    "schedules" to s.schedules.size,
    "giftPity" to s.giftDryStreak,
    "activePreset" to s.activePreset,
    "language" to s.settings.language,
)

/** Offline-first sync: the local store is the source of truth for the UI; the server is a mirror. */
class CloudSync(private val backend: CloudBackend, private val service: GameService) {
    var status: SyncStatus = if (backend.available) SyncStatus.OK else SyncStatus.OFF
        private set

    /** Launch / sign-in / change: pull, merge, apply server credits, push the merged state. */
    suspend fun syncNow(): SyncStatus {
        if (!backend.available) return SyncStatus.OFF.also { status = it }
        val ok = runCatching {
            val uid = backend.ensureUser() ?: return@runCatching false
            val remote = backend.fetch(uid)
            val credits = runCatching { backend.credits(uid) }.getOrDefault(emptyList())
            val merged = service.mergeCloud { local, now ->
                val remoteState = remote?.let { Backup.decode(it.state) }
                CloudMerge.applyCredits(CloudMerge.merge(local, remoteState, remote?.updatedAt ?: 0L), credits, now)
            }
            val at = backend.push(uid, CloudDoc(Backup.encode(merged), 0L, summaryOf(merged))) ?: return@runCatching false
            service.markSynced(at)
            true
        }.getOrDefault(false)
        status = if (ok) SyncStatus.OK else SyncStatus.OFFLINE
        return status
    }

    suspend fun link(provider: String, idToken: String, nonce: String? = null): Boolean {
        if (!backend.available) return false
        val uid = runCatching { backend.link(provider, idToken, nonce) }.getOrNull() ?: return false
        syncNow()
        return uid.isNotBlank()
    }

    suspend fun verify(platform: String, productId: String, token: String): VerifyResult =
        if (!backend.available) VerifyResult.Unavailable
        else runCatching { backend.verifyPurchase(platform, productId, token) }.getOrDefault(VerifyResult.Unavailable)

    /** Deletes server data first (if configured), then the caller resets local data. */
    suspend fun deleteAccount(): Boolean = !backend.available || runCatching { backend.deleteAccount() }.getOrDefault(false)
}

/** In-memory backend for tests; mimics Firestore + the Cloud Functions (idempotent credit by token). */
class FakeCloudBackend(override val available: Boolean = true) : CloudBackend {
    var uid: String? = null
    var serverTime = 1_000L
    val docs = mutableMapOf<String, CloudDoc>()
    val credits = mutableMapOf<String, MutableMap<String, ServerCredit>>()
    val linked = mutableMapOf<String, String>() // provider:sub -> uid
    var offline = false
    var functionsDeployed = true
    var validTokens: (String) -> Boolean = { !it.startsWith("bad") }
    private var anon = 0

    override suspend fun ensureUser(): String? { check(!offline); return uid ?: "anon-${++anon}".also { uid = it } }
    override suspend fun fetch(uid: String): CloudDoc? { check(!offline); return docs[uid] }
    override suspend fun credits(uid: String): List<ServerCredit> = credits[uid]?.values?.toList().orEmpty()
    override suspend fun push(uid: String, doc: CloudDoc): Long? {
        if (offline) return null
        serverTime += 1000
        docs[uid] = doc.copy(updatedAt = serverTime)
        return serverTime
    }
    override suspend fun link(provider: String, idToken: String, nonce: String?): String? {
        val key = "$provider:$idToken"
        val existing = linked[key]
        if (existing != null) { uid = existing; return existing }
        val me = ensureUser() ?: return null
        linked[key] = me
        return me
    }
    override suspend fun verifyPurchase(platform: String, productId: String, token: String): VerifyResult {
        if (offline || !functionsDeployed) return VerifyResult.Unavailable
        if (!validTokens(token)) return VerifyResult.Rejected("invalid")
        val me = ensureUser() ?: return VerifyResult.Unavailable
        val coins = app.mongle.logic.StoreProducts.byId(productId)?.coins ?: return VerifyResult.Rejected("unknown product")
        // Transaction keyed by token: a replay returns the original credit instead of crediting again.
        val map = credits.getOrPut(me) { mutableMapOf() }
        map.getOrPut(token) { ServerCredit(token, productId, coins, createdAt = serverTime) }
        return VerifyResult.Verified(coins)
    }
    override suspend fun deleteAccount(): Boolean {
        val me = uid ?: return true
        docs.remove(me); credits.remove(me); linked.values.removeAll { it == me }; uid = null
        return true
    }
    override suspend fun signOut() { uid = null }
}
