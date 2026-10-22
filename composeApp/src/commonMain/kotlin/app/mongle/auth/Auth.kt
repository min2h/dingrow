package app.mongle.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A signed-in account. Guests get a stable random id kept in encrypted on-device storage. */
data class AuthSession(
    val provider: String, // "guest" | "google" | "apple" | "fake"
    val userId: String,
    val displayName: String = "",
    val email: String = "",
    val device: String = "",
    val createdAt: Long = 0L,
)

sealed interface AuthResult {
    data class Success(val session: AuthSession) : AuthResult
    /** Provider not configured on this build/device: the UI offers guest with [messageKey]. */
    data class Unavailable(val messageKey: String) : AuthResult
    data object Cancelled : AuthResult
    data class Failed(val message: String) : AuthResult
}

interface AuthService {
    /** null = signed out (show login). */
    val session: StateFlow<AuthSession?>
    /** True once the persisted session was read (auto-login). */
    val restored: StateFlow<Boolean>
    /** True when this launch restored a previous session (show the welcome-back splash). */
    val autoLoggedIn: Boolean
    val appleAvailable: Boolean
    /** Debug builds expose a fake Google account so the flow can be tested without a client id. */
    val debugFake: Boolean
    suspend fun signInGuest(): AuthResult
    suspend fun signInGoogle(): AuthResult
    suspend fun signInApple(): AuthResult
    suspend fun signInFake(): AuthResult
    suspend fun signOut()
}

/**
 * Pure in-memory/pluggable implementation used by tests, previews and as the base of platform
 * services. [persist] / [load] are the storage hooks; TODO(backend): exchange provider tokens with
 * the Dingrow server and migrate guest data on account link.
 */
class LocalAuthService(
    private val load: () -> AuthSession? = { null },
    private val persist: (AuthSession?) -> Unit = {},
    private val guestId: () -> String = { "guest-" + kotlin.random.Random.nextLong().toULong().toString(16) },
    private val device: String = "",
    private val now: () -> Long = { 0L },
    override val appleAvailable: Boolean = false,
    override val debugFake: Boolean = false,
    private val google: (suspend () -> AuthResult)? = null,
    private val apple: (suspend () -> AuthResult)? = null,
) : AuthService {
    private val state = MutableStateFlow<AuthSession?>(null)
    private val ready = MutableStateFlow(false)
    override val session: StateFlow<AuthSession?> = state.asStateFlow()
    override val restored: StateFlow<Boolean> = ready.asStateFlow()
    override var autoLoggedIn: Boolean = false
        private set

    fun restore() {
        val saved = runCatching { load() }.getOrNull()
        autoLoggedIn = saved != null
        state.value = saved
        ready.value = true
    }

    private fun accept(s: AuthSession): AuthResult {
        runCatching { persist(s) }
        state.value = s
        return AuthResult.Success(s)
    }

    override suspend fun signInGuest(): AuthResult =
        accept(AuthSession("guest", guestId(), device = device, createdAt = now()))

    override suspend fun signInGoogle(): AuthResult {
        val g = google ?: return AuthResult.Unavailable("login_google_unavailable")
        return when (val r = runCatching { g() }.getOrElse { AuthResult.Failed(it.message.orEmpty()) }) {
            is AuthResult.Success -> accept(r.session.copy(device = device, createdAt = now()))
            else -> r
        }
    }

    override suspend fun signInApple(): AuthResult {
        val a = apple ?: return AuthResult.Unavailable("login_apple_unavailable")
        return when (val r = runCatching { a() }.getOrElse { AuthResult.Failed(it.message.orEmpty()) }) {
            is AuthResult.Success -> accept(r.session.copy(device = device, createdAt = now()))
            else -> r
        }
    }

    override suspend fun signInFake(): AuthResult =
        if (!debugFake) AuthResult.Unavailable("login_google_unavailable")
        else accept(AuthSession("fake", "fake-google-0001", "테스트 사용자", "tester@example.com", device, now()))

    override suspend fun signOut() {
        runCatching { persist(null) }
        autoLoggedIn = false
        state.value = null
    }
}
