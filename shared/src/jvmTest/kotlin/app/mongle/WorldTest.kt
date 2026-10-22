package app.mongle

import app.mongle.l10n.L10n
import app.mongle.l10n.AppLocale
import app.mongle.logic.*
import kotlinx.datetime.TimeZone
import kotlin.random.Random
import kotlinx.datetime.toInstant
import kotlin.test.*

class WorldTest {
    private val seoul = TimeZone.of("Asia/Seoul")
    private val base = 1_793_000_000_000L
    private fun sit(
        mins: Int? = null, place: String? = null, weather: WeatherSnapshot? = null,
        cond: Condition = Condition.GOOD, today: Int = 0, left: Int = 0,
    ) = Situation(
        now = base, phase = DayPhase.NOON, condition = cond, weather = weather, fahrenheit = false,
        nextTitle = mins?.let { "술약속" }, nextPlace = place, nextAt = mins?.let { base + it * 60_000L },
        nextClock = mins?.let { "19:00" }, todayCount = today, todayLeft = left, hunger = 80,
    )
    private fun wx(code: Int, laterCode: Int? = null) = WeatherSnapshot(
        tempC = 11.0, code = code, windKmh = 5.0, precipMm = 0.0, fetchedAt = base, lat = 37.5, lon = 127.0,
        hours = laterCode?.let { listOf(HourForecast(base + 3 * 3_600_000L, it, 9.0)) } ?: emptyList(),
    )

    @Test fun dayPhases() {
        // 2026-10-22 06:00 KST etc. computed from a known midnight
        val midnight = 1
        fun at(h: Int): Long = kotlinx.datetime.LocalDateTime(2026, 10, 22, h, 0).toInstant(seoul).toEpochMilliseconds()
        assertEquals(DayPhase.DAWN, DayPhase.of(at(5), seoul))
        assertEquals(DayPhase.MORNING, DayPhase.of(at(8), seoul))
        assertEquals(DayPhase.NIGHT, DayPhase.of(at(23), seoul))
        assertNotNull(midnight)
    }

    @Test fun priorityImminentBeatsWeatherBeatsSummary() {
        val c = Secretary.candidates(sit(mins = 30, place = "강남역", weather = wx(61), today = 2, left = 2)).map { it.first }
        assertEquals("soon_place", c.first())
        assertTrue(c.indexOf("rain_now") < c.indexOf("today_many"))
        assertEquals("today_none", Secretary.candidates(sit()).first().first)
    }

    @Test fun noWeatherLinesWithoutWeather() {
        val all = Secretary.candidates(sit(today = 1, left = 1, mins = 300)).map { it.first }
        assertTrue(all.none { it.startsWith("rain") || it == "temp" || it.startsWith("snow") || it.startsWith("storm") })
    }

    @Test fun noRepeatAmongLastEight() {
        AppLocale.preference = "ko"
        var recent = emptyList<String>()
        val r = Random(1)
        repeat(40) {
            val l = Secretary.pick(sit(), recent, r, selfTalk = true)
            assertFalse(l.key in recent.takeLast(7), "repeat ${l.key}")
            recent = Secretary.remember(recent, l.key)
        }
    }

    @Test fun placeLineEmphasisAndPositionalArgs() {
        AppLocale.preference = "en"
        val l = Secretary.format("Don't forget: %2\$s in %1\$s min!", listOf(30, "Gym"))
        assertEquals("Don't forget: **Gym** in **30** min!", l)
        val runs = Emphasis.runs(l)
        assertEquals(listOf("Gym", "30"), runs.filter { it.second }.map { it.first })
        assertEquals("Don't forget: Gym in 30 min!", Emphasis.strip(l))
        AppLocale.preference = "ko"
        Secretary.groups.forEach { (g, n) -> Secretary.keys(g).forEach { k -> assertNotEquals(k, L10n.t(k), "missing $k") }; assertTrue(n > 0) }
        assertTrue(Secretary.groups.values.sum() >= 100)
    }

    @Test fun conditionSicknessAndRecovery() {
        val sick = ConditionState(sick = true, recovery = 0, discomfort = 0, checkedAt = base)
        assertEquals(90, ConditionRules.xp(100, sick))
        var c = sick
        repeat(4) { c = ConditionRules.heal(c, ConditionRules.RECOVERY_PER_TASK) }
        assertTrue(c.sick); assertEquals(80, c.recovery)
        c = ConditionRules.heal(c, ConditionRules.RECOVERY_PER_TASK)
        assertFalse(c.sick)
        assertFalse(ConditionRules.heal(sick, ConditionRules.RECOVERY_MEDICINE + 20).sick)
        assertEquals(50, ConditionRules.MEDICINE_PRICE)
        val back = ConditionRules.read(ConditionRules.write(emptyMap(), ConditionState(true, 40, 3, base)))
        assertEquals(40, back.recovery)
    }

    @Test fun coldWeatherWithoutCoatLeadsToDiscomfort() {
        val cold = wx(0).copy(tempC = -5.0)
        val c = ConditionRules.tick(ConditionState(checkedAt = base), base + 4 * 3_600_000L, cold, null, 80, roll = 99)
        assertTrue(c.discomfort > 0)
        assertEquals(Condition.COLD, ConditionRules.current(c, cold, null, 80, 80))
        val warm = ConditionRules.tick(ConditionState(checkedAt = base), base + 4 * 3_600_000L, null, null, 80, roll = 0)
        assertFalse(warm.sick, "no weather -> no weather-based sickness")
    }

    @Test fun weatherParseEncodeAndForecast() {
        val json = """{"current":{"temperature_2m":11.2,"weather_code":3,"wind_speed_10m":7,"precipitation":0},
          "hourly":{"time":[${base / 1000 + 3600},${base / 1000 + 4 * 3600}],"weather_code":[3,63],"temperature_2m":[11,9]}}"""
        val w = Weather.parse(json, base, 37.5, 127.0)!!
        assertEquals(WeatherKind.CLOUDY, w.kind)
        assertEquals(WeatherKind.RAIN, Weather.upcomingChange(w, base)!!.kind)
        assertEquals(w, Weather.decode(Weather.encode(w)))
        assertNull(Weather.parse("garbage", base, 0.0, 0.0))
        assertTrue(w.usable(base + 3_600_000L)); assertFalse(w.usable(base + 7 * 3_600_000L))
        assertEquals(WeatherKind.STORM, wx(95).kind)
        assertTrue(Weather.url(37.5, 127.0).startsWith("https://api.open-meteo.com/"))
    }

    @Test fun fmtPositional() {
        AppLocale.preference = "en"
        assertEquals("Rain expected around 15:00. Bring an umbrella!", L10n.fmt("notice_wx_later", "15:00", "Rain"))
        AppLocale.preference = "ko"
        assertEquals("15:00쯤 비 소식이 있어요. 우산 챙겨요!", L10n.fmt("notice_wx_later", "15:00", "비"))
        assertEquals("행복 50%", L10n.fmt("stat_happy", 50))
        AppLocale.preference = "system"
    }

    @Test fun afternoonForecastAndWeatherLines() {
        fun at(h: Int): Long = kotlinx.datetime.LocalDateTime(2026, 10, 22, h, 0).toInstant(seoul).toEpochMilliseconds()
        val w = WeatherSnapshot(11.0, 0, 3.0, 0.0, at(9), 37.5, 127.0, (10..20).map { HourForecast(at(it), if (it == 15) 61 else 1, 14.0) })
        assertEquals(WeatherKind.RAIN, Weather.afternoon(w, at(9), seoul)!!.first)
        assertNull(Weather.afternoon(w, at(21), seoul))
        AppLocale.preference = "ko"
        val s = Situation(at(9), DayPhase.MORNING, Condition.GOOD, w, false, null, null, null, null, 0, 0, 80, afternoon = "비")
        val groups = Secretary.candidates(s).map { it.first }
        assertTrue(groups.containsAll(listOf("wx_now", "wx_pm")))
        val line = Secretary.format(L10n.t("line_wx_now_0"), Secretary.candidates(s).first { it.first == "wx_now" }.second)
        assertEquals("지금 **11°**, 날씨는 **맑음**", line)
        AppLocale.preference = "system"
    }
}
