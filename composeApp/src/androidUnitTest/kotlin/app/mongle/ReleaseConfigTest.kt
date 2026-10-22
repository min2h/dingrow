package app.mongle

import androidx.test.core.app.ApplicationProvider
import app.dingrow.BuildConfig
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Runs in both variants; `testReleaseUnitTest` proves the release build has no fake billing / fake login. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReleaseConfigTest {
    @Test fun fakeBillingAndFakeLoginOnlyInDebug() {
        assertFalse("release on a real device → Play Billing", AppGraph.useFakeBilling(debug = false, robot = false))
        assertTrue(AppGraph.useFakeBilling(debug = true, robot = false))
        app.mongle.auth.AndroidAuth.resetForTests()
        val auth = app.mongle.auth.AndroidAuth.get(ApplicationProvider.getApplicationContext())
        assertEquals("fake Google login button only in debug", BuildConfig.DEBUG, auth.debugFake)
        if (!BuildConfig.DEBUG) assertEquals("app.dingrow", BuildConfig.APPLICATION_ID)
    }
}
