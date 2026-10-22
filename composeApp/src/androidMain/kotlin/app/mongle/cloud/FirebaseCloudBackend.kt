package app.mongle.cloud

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.OAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/** Firebase (Auth + Firestore + Functions) bridge. Unavailable when google-services.json was not bundled. */
class FirebaseCloudBackend(context: Context) : CloudBackend {
    private val app: FirebaseApp? = runCatching {
        FirebaseApp.getApps(context).firstOrNull() ?: FirebaseApp.initializeApp(context)
    }.getOrNull()
    override val available: Boolean = app != null
    private val auth by lazy { FirebaseAuth.getInstance(app!!) }
    private val db by lazy { FirebaseFirestore.getInstance(app!!) }
    private val functions by lazy { FirebaseFunctions.getInstance(app!!, REGION) }

    override suspend fun ensureUser(): String? = withTimeout(TIMEOUT) {
        auth.currentUser?.uid ?: auth.signInAnonymously().await().user?.uid
    }

    override suspend fun fetch(uid: String): CloudDoc? = withTimeout(TIMEOUT) {
        val snap = db.collection("users").document(uid).get().await()
        if (!snap.exists()) return@withTimeout null
        val state = snap.getString("state") ?: return@withTimeout null
        CloudDoc(state, snap.getTimestamp("updatedAt")?.toDate()?.time ?: 0L)
    }

    override suspend fun credits(uid: String): List<ServerCredit> = withTimeout(TIMEOUT) {
        db.collection("users").document(uid).collection("credits").get().await().documents.map { d ->
            ServerCredit(
                token = d.getString("token") ?: d.id,
                productId = d.getString("productId").orEmpty(),
                coins = (d.getLong("coins") ?: 0L).toInt(),
                premium = d.getBoolean("premium") ?: false,
                legendary = d.getBoolean("legendary") ?: false,
                createdAt = d.getTimestamp("createdAt")?.toDate()?.time ?: 0L,
            )
        }
    }

    override suspend fun push(uid: String, doc: CloudDoc): Long? = withTimeout(TIMEOUT) {
        val ref = db.collection("users").document(uid)
        ref.set(mapOf("state" to doc.state, "schema" to 1, "summary" to doc.summary, "updatedAt" to FieldValue.serverTimestamp()), SetOptions.merge()).await()
        ref.get().await().getTimestamp("updatedAt")?.toDate()?.time
    }

    override suspend fun link(provider: String, idToken: String, nonce: String?): String? = withTimeout(TIMEOUT) {
        val credential = when (provider) {
            "google" -> GoogleAuthProvider.getCredential(idToken, null)
            "apple" -> OAuthProvider.newCredentialBuilder("apple.com").setIdTokenWithRawNonce(idToken, nonce).build()
            else -> return@withTimeout null
        }
        val user = auth.currentUser ?: auth.signInAnonymously().await().user ?: return@withTimeout null
        try {
            user.linkWithCredential(credential).await().user?.uid
        } catch (e: FirebaseAuthUserCollisionException) {
            // This Google/Apple account already has Dingrow data (reinstall / new device): switch to it; sync merges.
            auth.signInWithCredential(e.updatedCredential ?: credential).await().user?.uid
        }
    }

    override suspend fun verifyPurchase(platform: String, productId: String, token: String): VerifyResult = try {
        ensureUser()
        val res = withTimeout(TIMEOUT) {
            functions.getHttpsCallable("verifyPurchase").call(mapOf("platform" to platform, "productId" to productId, "token" to token)).await()
        }
        val data = res.getData() as? Map<*, *>
        VerifyResult.Verified((data?.get("coins") as? Number)?.toInt() ?: 0)
    } catch (e: FirebaseFunctionsException) {
        when (e.code) {
            FirebaseFunctionsException.Code.INVALID_ARGUMENT, FirebaseFunctionsException.Code.PERMISSION_DENIED,
            FirebaseFunctionsException.Code.FAILED_PRECONDITION -> VerifyResult.Rejected(e.code.name)
            else -> VerifyResult.Unavailable // NOT_FOUND (not deployed), UNAVAILABLE, INTERNAL ... -> local fallback
        }
    } catch (e: Throwable) {
        VerifyResult.Unavailable
    }

    override suspend fun deleteAccount(): Boolean {
        val user = auth.currentUser ?: return true
        val viaFunction = runCatching { withTimeout(TIMEOUT) { functions.getHttpsCallable("deleteAccount").call().await() } }.isSuccess
        if (!viaFunction) {
            // Functions not deployed: delete what the rules let the owner delete, then the Auth user.
            val ref = db.collection("users").document(user.uid)
            runCatching { withTimeout(TIMEOUT) { ref.delete().await() } }.getOrElse { return false }
            runCatching { withTimeout(TIMEOUT) { user.delete().await() } }
        }
        auth.signOut()
        return true
    }

    override suspend fun signOut() { if (available) auth.signOut() }

    companion object {
        const val REGION = "asia-northeast3"
        const val TIMEOUT = 15_000L
    }
}
