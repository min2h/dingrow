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
 * Session stored in NSUserDefaults (move to Keychain before release — see IOS_BUILD.md).
 */
object IosAuth {
    var appleSignIn: ((complete: (userId: String, name: String, email: String) -> Unit) -> Unit)? = null
    private val defaults get() = NSUserDefaults.standardUserDefaults

    private fun encode(s: AuthSession) = listOf(s.provider, s.userId, s.displayName, s.email, s.device, s.createdAt.toString()).joinToString("\u001F")
    private fun decode(raw: String?): AuthSession? {
        val p = raw?.split("\u001F") ?: return null
        if (p.size < 6) return null
        return AuthSession(p[0], p[1], p[2], p[3], p[4], p[5].toLongOrNull() ?: 0L)
    }

    val service: LocalAuthService by lazy {
        LocalAuthService(
            load = { decode(defaults.stringForKey("dingrow.auth")) },
            persist = { s -> if (s == null) defaults.removeObjectForKey("dingrow.auth") else defaults.setObject(encode(s), "dingrow.auth") },
            guestId = {
                defaults.stringForKey("dingrow.guest") ?: ("guest-" + NSUUID().UUIDString.lowercase()).also { defaults.setObject(it, "dingrow.guest") }
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
