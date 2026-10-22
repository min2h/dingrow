package app.mongle

import android.graphics.Bitmap
import android.graphics.Canvas
import app.mongle.data.MemoryGameStore
import app.mongle.logic.HourForecast
import app.mongle.logic.ScheduleDraft
import app.mongle.logic.WeatherSnapshot
import app.mongle.model.RepeatRule
import app.mongle.platform.AndroidHooks
import app.mongle.service.GameService
import app.mongle.wallpaper.WallpaperPainter
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class WallpaperV4Test {
    private val zone = TimeZone.of("Asia/Seoul")
    private fun at(h: Int, m: Int) = LocalDateTime(2026, 10, 22, h, m).toInstant(zone).toEpochMilliseconds()
    private fun wx(now: Long, code: Int, temp: Double) =
        WeatherSnapshot(temp, code, 6.0, if (code >= 60) 3.0 else 0.0, now, 37.57, 126.98, (1..10).map { HourForecast(now + it * 3_600_000L, code, temp - 1) })

    private fun state(h: Int, m: Int, lang: String, weather: ((Long) -> WeatherSnapshot)?): Pair<app.mongle.model.GameState, Long> = runBlocking {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Seoul"))
        AndroidHooks.sync = {}; AndroidHooks.notifyNow = { _, _ -> }; AndroidHooks.reloadWidgets = {}
        app.mongle.l10n.AppLocale.preference = lang
        val now = at(h, m)
        val s = GameService(MemoryGameStore(), MutableClock(now), zone)
        s.completeOnboarding("몽이")
        s.updateSettings(s.state.value.settings.copy(language = lang, useLocation = weather != null, newScheduleConfirm = false))
        s.addDraft(ScheduleDraft(if (lang == "ko") "병원" else "Clinic", now + 25 * 60_000L, RepeatRule.NONE, place = if (lang == "ko") "서울내과" else "Seoul Clinic"))
        weather?.let { s.setWeather(it(now)) }
        s.refresh()
        s.state.value to now
    }

    private fun shot(name: String, st: app.mongle.model.GameState, now: Long): Bitmap {
        val b = Bitmap.createBitmap(720, 1440, Bitmap.Config.ARGB_8888)
        val ctx = RuntimeEnvironment.getApplication()
        repeat(3) { WallpaperPainter.draw(Canvas(b), st, now + it * 16, ctx) }
        FileOutputStream(artifact(name)).use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return b
    }

    @Test fun wallpaperStates() {
        val (noon, n1) = state(14, 35, "ko", null)
        val a = shot("wallpaper-noon-noweather.png", noon, n1)
        val (rain, n2) = state(15, 20, "ko", { wx(it, 63, 11.0) })
        val b = shot("wallpaper-rain.png", rain, n2)
        val (night, n3) = state(22, 10, "ko", { wx(it, 0, 9.0) })
        shot("wallpaper-night.png", night, n3)
        val (en, n4) = state(7, 30, "en", { wx(it, 73, -2.0) })
        shot("en-wallpaper-snow.png", en, n4)
        assertNotEquals(a.getPixel(360, 700), b.getPixel(360, 700))
        app.mongle.l10n.AppLocale.preference = "system"
    }
}
