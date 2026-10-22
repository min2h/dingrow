package app.mongle.weather

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import app.mongle.logic.Weather
import app.mongle.logic.WeatherSnapshot
import app.mongle.service.GameService
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Weather is ON only while the user enabled it AND coarse location is granted. Without permission
 * all weather is cleared (no city fallback). Fetches Open-Meteo at most hourly; on network errors
 * the cached snapshot stays until it goes stale, after which weather simply disappears.
 */
object WeatherFetcher {
    const val REFRESH_MS = 60 * 60_000L
    /** Test seam: replaces the network call. */
    var http: (String) -> String? = ::get
    /** Test seam: replaces the location lookup. */
    var locate: (Context) -> Pair<Double, Double>? = ::lastKnown

    fun granted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    suspend fun refresh(context: Context, service: GameService, force: Boolean = false) {
        val state = service.state.value
        if (!granted(context)) {
            // Permission revoked: the setting switches off and weather disappears.
            if (state.settings.useLocation) service.updateSettings(state.settings.copy(useLocation = false))
            service.setWeather(null)
            return
        }
        if (!state.settings.useLocation) { service.setWeather(null); return }
        val cached = state.weather
        val now = System.currentTimeMillis()
        if (!force && cached != null && now - cached.fetchedAt < REFRESH_MS) return
        val snap = withContext(Dispatchers.IO) { fetch(context, now) } ?: return
        service.setWeather(snap)
    }

    fun fetch(context: Context, now: Long): WeatherSnapshot? = runCatching {
        val (lat, lon) = locate(context) ?: return null
        // Coarse: round to ~1 km so the request never carries a precise position.
        val la = Math.round(lat * 100) / 100.0
        val lo = Math.round(lon * 100) / 100.0
        val body = http(Weather.url(la, lo)) ?: return null
        Weather.parse(body, now, la, lo)
    }.getOrNull()

    @SuppressLint("MissingPermission")
    private fun lastKnown(context: Context): Pair<Double, Double>? {
        if (!granted(context)) return null
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val best: Location? = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER, LocationManager.GPS_PROVIDER)
            .mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
            .maxByOrNull { it.time }
        return best?.let { it.latitude to it.longitude }
    }

    private fun get(url: String): String? {
        val c = URL(url).openConnection() as HttpURLConnection
        return try {
            c.connectTimeout = 8_000; c.readTimeout = 8_000
            if (c.responseCode != 200) null else c.inputStream.bufferedReader().use { it.readText() }
        } finally { c.disconnect() }
    }
}
