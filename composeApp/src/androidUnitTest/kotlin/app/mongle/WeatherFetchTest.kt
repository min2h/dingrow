package app.mongle

import app.mongle.data.MemoryGameStore
import app.mongle.service.GameService
import app.mongle.weather.WeatherFetcher
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WeatherFetchTest {
    private val ctx = RuntimeEnvironment.getApplication()
    private var calls = 0
    private fun body(now: Long) = """{"current":{"temperature_2m":11.4,"weather_code":0,"wind_speed_10m":5,"precipitation":0},
        "hourly":{"time":[${now / 1000 + 3600}],"weather_code":[61],"temperature_2m":[10]}}"""

    private fun service(): GameService = GameService(MemoryGameStore(), object : app.mongle.service.TimeProvider { override fun now() = System.currentTimeMillis() }, TimeZone.of("Asia/Seoul")).also {
        runBlocking { it.completeOnboarding("보리") }
    }

    @After fun reset() { WeatherFetcher.http = { null }; WeatherFetcher.locate = { null } }

    @Test fun deniedMeansNoWeatherAndSettingOff() = runBlocking {
        val s = service()
        s.updateSettings(s.state.value.settings.copy(useLocation = true))
        WeatherFetcher.locate = { 37.5 to 127.0 }
        WeatherFetcher.http = { calls++; body(System.currentTimeMillis()) }
        WeatherFetcher.refresh(ctx, s, force = true)
        assertFalse(s.state.value.settings.useLocation)
        assertNull(s.state.value.weather)
        assertEquals("never fetches without permission (no city fallback)", 0, calls)
    }

    @Test fun grantedFetchesCachesHourlyAndSurvivesOffline() = runBlocking {
        shadowOf(ctx).grantPermissions(android.Manifest.permission.ACCESS_COARSE_LOCATION)
        val s = service()
        s.updateSettings(s.state.value.settings.copy(useLocation = true))
        var url = ""
        WeatherFetcher.locate = { 37.56789 to 126.97654 }
        WeatherFetcher.http = { calls++; url = it; body(System.currentTimeMillis()) }
        WeatherFetcher.refresh(ctx, s)
        val w = s.state.value.weather
        assertNotNull(w); assertEquals(11.4, w!!.tempC, 0.01)
        assertTrue("coarse coords", url.contains("latitude=37.57") && url.contains("longitude=126.98"))
        WeatherFetcher.refresh(ctx, s)
        assertEquals("cached within the hour", 1, calls)
        WeatherFetcher.http = { null }
        WeatherFetcher.refresh(ctx, s, force = true)
        assertNotNull("offline keeps cache", s.state.value.weather)
        // Turning the setting off hides weather entirely.
        s.updateSettings(s.state.value.settings.copy(useLocation = false))
        assertNull(s.state.value.weather)
    }
}
