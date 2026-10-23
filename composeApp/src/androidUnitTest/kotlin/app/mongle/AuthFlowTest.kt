package app.mongle

import org.junit.Assert.assertTrue
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsDisplayed
import app.mongle.auth.AndroidAuth
import app.mongle.auth.AuthResult
import app.mongle.auth.AuthSession
import app.mongle.auth.LocalAuthService
import app.mongle.ui.AuthGate
import app.mongle.ui.LanguageProvider
import app.mongle.ui.MongleTheme
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "ko-rKR-w360dp-h740dp-hdpi")
class AuthFlowTest {
    @get:Rule val rule = createComposeRule()

    private fun settle() { repeat(3) { rule.mainClock.advanceTimeBy(300); rule.waitForIdle() } }

    private fun show(auth: LocalAuthService, lang: String = "ko") {
        primeComposeResources()
        rule.mainClock.autoAdvance = false
        rule.setContent { LanguageProvider(lang) { MongleTheme { AuthGate(auth, splashMillis = 1_000) { Text("HOME") } } } }
        settle()
    }

    private fun store(): Pair<() -> AuthSession?, (AuthSession?) -> Unit> {
        var saved: AuthSession? = null
        return Pair({ saved }, { s: AuthSession? -> saved = s })
    }

    @Test fun guestFlowPersistsStableIdAndSignOut() {
        val (load, persist) = store()
        var ids = 0
        val auth = LocalAuthService(load, persist, guestId = { "guest-${++ids}" }, device = "Pixel 8 · Android 15", now = { 1_792_600_000_000 })
        auth.restore()
        show(auth)
        rule.onNodeWithTag("login").assertIsDisplayed()
        rule.onNodeWithText("게스트 데이터는 이 기기에만 저장돼요. 나중에 설정에서 연결할 수 있어요").assertIsDisplayed()
        rule.onRoot().captureRoboImage(artifact("login.png"))
        rule.onNodeWithTag("login-guest").performClick(); settle()
        // No separate account-details screen: guest lands straight in the app.
        assertTrue(rule.onAllNodesWithTag("guest").fetchSemanticsNodes().isEmpty())
        rule.onNodeWithText("HOME").assertIsDisplayed()
        assertEquals("guest", load()!!.provider)
        runBlocking { auth.signOut() }; settle()
        rule.onNodeWithTag("login").assertIsDisplayed()
        assertNull(load())
    }

    @Test fun returningToLoginKeepsGuestSessionAndData() {
        val (load, persist) = store()
        var ids = 0
        val auth = LocalAuthService(load, persist, guestId = { "guest-${++ids}" })
        auth.restore()
        show(auth)
        rule.onNodeWithTag("login-guest").performClick(); settle()
        val first = load()!!.userId
        // Onboarding's back (after confirm) only requests the login screen; no sign-out.
        app.mongle.ui.LoginReturn.requested = true; settle()
        rule.onNodeWithTag("login").assertIsDisplayed()
        assertEquals(first, load()!!.userId)
        rule.onNodeWithTag("login-guest").performClick(); settle()
        rule.onNodeWithText("HOME").assertIsDisplayed()
        assertEquals("same guest id resumed, data kept", first, load()!!.userId)
        assertEquals(1, ids)
    }

    @Test fun unconfiguredGoogleFallsBackWithMessage() {
        val auth = LocalAuthService(debugFake = true).also { it.restore() }
        show(auth)
        rule.onNodeWithTag("login-google").performClick(); settle()
        rule.onNodeWithTag("login-notice").assertIsDisplayed()
        rule.onNodeWithText("Google 로그인을 준비 중이에요").assertIsDisplayed()
        rule.onRoot().captureRoboImage(artifact("login-fallback.png"))
        assertNull(auth.session.value)
        rule.onNodeWithTag("login-fake").performClick(); settle()
        assertEquals("fake", auth.session.value!!.provider)
        rule.onNodeWithText("HOME").assertIsDisplayed()
    }

    @Test fun autoLoginShowsSplashThenHome() {
        val saved = AuthSession("google", "u1", "민형", "m@example.com", "Pixel", 1L)
        val auth = LocalAuthService(load = { saved }).also { it.restore() }
        assertTrue(auth.autoLoggedIn)
        show(auth)
        rule.onNodeWithTag("splash").assertIsDisplayed()
        rule.onNodeWithText("민형님, 다시 만나서 반가워요").assertIsDisplayed()
        rule.onRoot().captureRoboImage(artifact("login-splash.png"))
        rule.mainClock.advanceTimeBy(1_500); settle()
        rule.onNodeWithText("HOME").assertIsDisplayed()
    }

    @Test fun englishLogin() {
        val auth = LocalAuthService().also { it.restore() }
        show(auth, "en")
        rule.onNodeWithText("Start as guest").assertIsDisplayed()
        rule.onRoot().captureRoboImage(artifact("login-en.png"))
    }

    @Test fun androidAuthWithoutKeystoreOrClientIdIsSafe() {
        AndroidAuth.resetForTests()
        val ctx = RuntimeEnvironment.getApplication()
        assertNull(AndroidAuth.clientId(ctx))
        val a = AndroidAuth.get(ctx)
        val r = runBlocking { a.signInGoogle() }
        assertTrue(r is AuthResult.Unavailable)
        val g1 = (runBlocking { a.signInGuest() } as AuthResult.Success).session
        runBlocking { a.signOut() }
        val g2 = (runBlocking { a.signInGuest() } as AuthResult.Success).session
        assertEquals("stable guest id", g1.userId, g2.userId)
        assertTrue(g1.userId.startsWith("guest-"))
        AndroidAuth.resetForTests()
        assertEquals("guest", AndroidAuth.get(ctx).session.value?.provider)
        assertEquals(g1, AndroidAuth.decode(AndroidAuth.encode(g1)))
    }
}
