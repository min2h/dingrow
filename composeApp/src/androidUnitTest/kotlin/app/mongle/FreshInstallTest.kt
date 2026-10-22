package app.mongle

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h740dp-xhdpi")
class FreshInstallTest {
    init {
        // Clean state: no session left in prefs/static by an earlier test in the same JVM.
        androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>().let { ctx ->
            ctx.getSharedPreferences("dingrow_auth", 0).edit().clear().commit()
        }
        app.mongle.auth.AndroidAuth.resetForTests(); AppGraph.resetForTests(); primeComposeResources()
    }
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun freshInstallShowsLoginThenFakeGoogleThenAutoLogin() {
        rule.waitUntil(20_000) { rule.onAllNodesWithTag("login").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("login-fake").performClick()
        rule.waitUntil(20_000) { rule.onAllNodesWithTag("login").fetchSemanticsNodes().isEmpty() }
        val auth = app.mongle.auth.AndroidAuth.get(rule.activity)
        assertTrue(auth.session.value?.provider == "fake")
        // Next launch: restored from storage.
        app.mongle.auth.AndroidAuth.resetForTests()
        val again = app.mongle.auth.AndroidAuth.get(rule.activity)
        assertTrue(again.autoLoggedIn)
    }
}
