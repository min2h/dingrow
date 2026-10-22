package app.mongle.auth

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import app.dingrow.BuildConfig
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import java.lang.ref.WeakReference
import java.util.UUID
import app.mongle.cloud.CloudHooks

object AndroidAuth {
    /** Placeholder until a real OAuth web client id is configured (res/values/auth.xml). */
    const val PLACEHOLDER = "REPLACE_WITH_WEB_CLIENT_ID"
    @Volatile private var service: LocalAuthService? = null
    /** The activity used to show the Credential Manager sheet. */
    var activity: WeakReference<Activity>? = null

    internal fun prefs(context: Context): SharedPreferences = runCatching {
        val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            context, "dingrow_auth", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }.getOrElse {
        // Keystore unavailable (e.g. Robolectric or a broken OEM keystore): stay usable, never crash.
        context.getSharedPreferences("dingrow_auth_fallback", Context.MODE_PRIVATE)
    }

    fun clientId(context: Context): String? {
        val id = context.resources.getIdentifier("google_web_client_id", "string", context.packageName)
        val value = if (id == 0) "" else context.getString(id)
        return value.takeIf { it.isNotBlank() && it != PLACEHOLDER }
    }

    fun get(context: Context): LocalAuthService = service ?: synchronized(this) {
        service ?: create(context.applicationContext).also { service = it }
    }

    internal fun resetForTests() { service = null }

    private fun create(app: Context): LocalAuthService {
        val p = prefs(app)
        val device = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}"
        val clientId = clientId(app)
        val google: (suspend () -> AuthResult)? = clientId?.let { cid ->
            suspend fn@{
                val act = activity?.get() ?: return@fn AuthResult.Unavailable("login_google_unavailable")
                try {
                    val option = GetGoogleIdOption.Builder().setFilterByAuthorizedAccounts(false).setServerClientId(cid).setAutoSelectEnabled(true).build()
                    val res = CredentialManager.create(act).getCredential(act, GetCredentialRequest.Builder().addCredentialOption(option).build())
                    val g = GoogleIdTokenCredential.createFrom(res.credential.data)
                    // Link the anonymous Firebase user to this Google account (or switch to it on a new device) and sync.
                    runCatching { CloudHooks.link("google", g.idToken) }
                    AuthResult.Success(AuthSession("google", g.id, g.displayName.orEmpty(), g.id))
                } catch (e: GetCredentialCancellationException) {
                    AuthResult.Cancelled
                } catch (e: Throwable) {
                    AuthResult.Unavailable("login_google_unavailable")
                }
            }
        }
        return LocalAuthService(
            load = { decode(p.getString("session", null)) },
            persist = { s -> p.edit().apply { if (s == null) remove("session") else putString("session", encode(s)) }.apply() },
            guestId = {
                p.getString("guest_id", null) ?: ("guest-" + UUID.randomUUID().toString()).also { p.edit().putString("guest_id", it).apply() }
            },
            device = device,
            now = { System.currentTimeMillis() },
            appleAvailable = false, // Sign in with Apple is offered on iOS only.
            debugFake = BuildConfig.DEBUG,
            google = google,
        ).also { it.restore() }
    }

    private fun esc(v: String) = v.replace("\\", "\\\\").replace("|", "\\p")
    private fun unesc(v: String) = v.replace("\\p", "|").replace("\\\\", "\\")
    fun encode(s: AuthSession) = listOf(s.provider, s.userId, s.displayName, s.email, s.device, s.createdAt.toString()).joinToString("|") { esc(it) }
    fun decode(raw: String?): AuthSession? {
        if (raw.isNullOrBlank()) return null
        val f = raw.split("|").map { unesc(it) }
        if (f.size < 6) return null
        return AuthSession(f[0], f[1], f[2], f[3], f[4], f[5].toLongOrNull() ?: 0L)
    }
}
