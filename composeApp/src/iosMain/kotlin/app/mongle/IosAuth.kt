package app.mongle

import app.mongle.auth.AuthResult
import app.mongle.auth.AuthSession
import app.mongle.auth.LocalAuthService
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDefaults

/**
 * iOS sign-in: guest (stable UUID) + Sign in with Apple.
 * Swift installs [appleSignIn] (ASAuthorizationController) and calls the completion with the Apple user id/name/email,
 * or an empty id when cancelled. Without the hook (e.g. no capability yet) Apple returns "준비 중".
 * Session is stored in the Keychain through [secureGet]/[secureSet], which iOSApp.swift installs (SecItem*).
 * Older sessions in NSUserDefaults are migrated into the Keychain and removed on first read.
 */
object IosAuth {
    var appleSignIn: ((complete: (userId: String, name: String, email: String) -> Unit) -> Unit)? = null
    private val defaults get() = NSUserDefaults.standardUserDefaults
    /** Keychain read installed by Swift. */
    var secureGet: ((key: String) -> String?)? = null
    /** Keychain write (null value deletes) installed by Swift. */
    var secureSet: ((key: String, value: String?) -> Unit)? = null

    private fun read(key: String): String? {
        val get = secureGet ?: return defaults.stringForKey(key)
        get(key)?.let { return it }
        val legacy = defaults.stringForKey(key) ?: return null
        secureSet?.invoke(key, legacy)
        defaults.removeObjectForKey(key)
        return legacy
    }
    private fun write(key: String, value: String?) {
        val set = secureSet
        if (set != null) { set(key, value); defaults.removeObjectForKey(key); return }
        if (value == null) defaults.removeObjectForKey(key) else defaults.setObject(value, key)
    }

    private fun encode(s: AuthSession) = listOf(s.provider, s.userId, s.displayName, s.email, s.device, s.createdAt.toString()).joinToString("\u001F")
    private fun decode(raw: String?): AuthSession? {
        val p = raw?.split("\u001F") ?: return null
        if (p.size < 6) return null
        return AuthSession(p[0], p[1], p[2], p[3], p[4], p[5].toLongOrNull() ?: 0L)
    }

    val service: LocalAuthService by lazy {
        LocalAuthService(
            load = { decode(read("dingrow.auth")) },
            persist = { s -> write("dingrow.auth", s?.let(::encode)) },
            guestId = {
                read("dingrow.guest") ?: ("guest-" + NSUUID().UUIDString.lowercase()).also { write("dingrow.guest", it) }
            },
            device = "ios",
            now = { kotlinx.datetime.Clock.System.now().toEpochMilliseconds() },
            appleAvailable = true,
            debugFake = false,
            apple = apple@{
                val hook = appleSignIn ?: return@apple AuthResult.Unavailable("login_apple_unavailable")
                kotlinx.coroutines.suspendCancellableCoroutine { cont ->
                    hook { id, name, email ->
                        cont.resumeWith(Result.success(
                            if (id.isBlank()) AuthResult.Cancelled
                            else AuthResult.Success(AuthSession("apple", id, name, email, "ios", kotlinx.datetime.Clock.System.now().toEpochMilliseconds())),
                        ))
                    }
                }
            },
        )
    }
}
// touch
