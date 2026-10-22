package app.mongle.cloud

import app.mongle.platform.MongleSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.coroutines.resume

/**
 * Callback hooks installed by iosApp/iosApp/FirebaseCloud.swift (Firebase iOS SDK via SPM).
 * When the Firebase packages / GoogleService-Info.plist are missing, nothing is installed and sync stays OFF.
 */
object IosCloudBridge {
    var ensureUser: ((done: (String?) -> Unit) -> Unit)? = null
    var fetch: ((uid: String, done: (state: String?, updatedAtMillis: Long) -> Unit) -> Unit)? = null
    /** JSON array of {token, productId, coins, premium, legendary, createdAt}. */
    var credits: ((uid: String, done: (json: String) -> Unit) -> Unit)? = null
    /** Writes the doc with a server timestamp; done(-1) on failure. */
    var push: ((uid: String, state: String, summaryJson: String, done: (Long) -> Unit) -> Unit)? = null
    var link: ((provider: String, idToken: String, nonce: String, done: (String?) -> Unit) -> Unit)? = null
    /** done("ok"|"rejected"|"unavailable", coins). */
    var verify: ((platform: String, productId: String, token: String, done: (String, Int) -> Unit) -> Unit)? = null
    var deleteAccount: ((done: (Boolean) -> Unit) -> Unit)? = null
    var signOut: (() -> Unit)? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var pushJob: Job? = null

    /** Called from MainViewController after MongleBridge.start(). */
    fun install() {
        val backend = IosCloudBackend()
        if (!backend.available || CloudHooks.sync != null) return
        val service = MongleSession.service
        val sync = CloudSync(backend, service)
        CloudHooks.sync = sync
        service.onChanged = {
            pushJob?.cancel()
            pushJob = scope.launch { delay(3_000); runCatching { sync.syncNow() } }
        }
        scope.launch { runCatching { sync.syncNow() } }
    }

    /** Swift sync trigger after a StoreKit grant / foregrounding. */
    fun syncSoon() { scope.launch { runCatching { CloudHooks.syncNow() } } }
}

private fun summaryJson(m: Map<String, Any?>): String = JsonObject(m.mapValues { (_, v) ->
    when (v) {
        null -> JsonNull
        is Number -> JsonPrimitive(v)
        is Boolean -> JsonPrimitive(v)
        is List<*> -> JsonArray(v.map { JsonPrimitive(it.toString()) })
        else -> JsonPrimitive(v.toString())
    }
}).toString()

class IosCloudBackend : CloudBackend {
    private val b = IosCloudBridge
    override val available: Boolean get() = b.ensureUser != null && b.fetch != null && b.push != null

    override suspend fun ensureUser(): String? = suspendCancellableCoroutine { c -> b.ensureUser!!.invoke { c.resume(it) } }
    override suspend fun fetch(uid: String): CloudDoc? = suspendCancellableCoroutine { c ->
        b.fetch!!.invoke(uid) { state, at -> c.resume(state?.let { CloudDoc(it, at) }) }
    }
    override suspend fun credits(uid: String): List<ServerCredit> {
        val hook = b.credits ?: return emptyList()
        val raw: String = suspendCancellableCoroutine { c -> hook(uid) { c.resume(it) } }
        return runCatching {
            Json.parseToJsonElement(raw).jsonArray.map { e ->
                val o = e.jsonObject
                ServerCredit(
                    token = o["token"]!!.jsonPrimitive.content,
                    productId = o["productId"]?.jsonPrimitive?.content.orEmpty(),
                    coins = o["coins"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
                    premium = o["premium"]?.jsonPrimitive?.booleanOrNull ?: false,
                    legendary = o["legendary"]?.jsonPrimitive?.booleanOrNull ?: false,
                    createdAt = o["createdAt"]?.jsonPrimitive?.longOrNull ?: 0L,
                )
            }
        }.getOrDefault(emptyList())
    }
    override suspend fun push(uid: String, doc: CloudDoc): Long? = suspendCancellableCoroutine<Long> { c ->
        b.push!!.invoke(uid, doc.state, summaryJson(doc.summary)) { c.resume(it) }
    }.takeIf { it >= 0 }
    override suspend fun link(provider: String, idToken: String, nonce: String?): String? {
        val hook = b.link ?: return null
        return suspendCancellableCoroutine { c -> hook(provider, idToken, nonce.orEmpty()) { c.resume(it) } }
    }
    override suspend fun verifyPurchase(platform: String, productId: String, token: String): VerifyResult {
        val hook = b.verify ?: return VerifyResult.Unavailable
        val (status, coins) = suspendCancellableCoroutine<Pair<String, Int>> { c -> hook(platform, productId, token) { s, n -> c.resume(s to n) } }
        return when (status) { "ok" -> VerifyResult.Verified(coins); "rejected" -> VerifyResult.Rejected("store"); else -> VerifyResult.Unavailable }
    }
    override suspend fun deleteAccount(): Boolean {
        val hook = b.deleteAccount ?: return true
        return suspendCancellableCoroutine { c -> hook { c.resume(it) } }
    }
    override suspend fun signOut() { b.signOut?.invoke() }
}
