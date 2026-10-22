package app.mongle.logic

import app.mongle.l10n.L10n
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Time of day from the device clock and zone. Works without location. */
enum class DayPhase { DAWN, MORNING, NOON, EVENING, NIGHT;
    companion object {
        fun of(millis: Long, zone: TimeZone): DayPhase {
            val h = Instant.fromEpochMilliseconds(millis).toLocalDateTime(zone).hour
            return when (h) {
                in 4..5 -> DAWN
                in 6..10 -> MORNING
                in 11..16 -> NOON
                in 17..19 -> EVENING
                else -> NIGHT
            }
        }
    }
}

enum class WeatherKind { CLEAR, CLOUDY, RAIN, SNOW, STORM;
    val wet: Boolean get() = this == RAIN || this == SNOW || this == STORM
}

data class HourForecast(val atMillis: Long, val code: Int, val tempC: Double) {
    val kind: WeatherKind get() = Weather.kindOf(code, 0.0)
}

/** Cached current weather. Only exists while the user allows location. */
data class WeatherSnapshot(
    val tempC: Double,
    val code: Int,
    val windKmh: Double,
    val precipMm: Double,
    val fetchedAt: Long,
    val lat: Double,
    val lon: Double,
    val hours: List<HourForecast> = emptyList(),
) {
    val kind: WeatherKind get() = Weather.kindOf(code, windKmh)
    fun stale(now: Long) = now - fetchedAt > Weather.REFRESH_MILLIS
    fun usable(now: Long) = now - fetchedAt < Weather.MAX_AGE_MILLIS
}

object Weather {
    const val REFRESH_MILLIS = 60L * 60 * 1000
    const val MAX_AGE_MILLIS = 6L * 60 * 60 * 1000
    /** Wind at or above this is treated as storm / typhoon wind. */
    const val STORM_WIND_KMH = 50.0

    fun url(lat: Double, lon: Double): String =
        "https://api.open-meteo.com/v1/forecast?latitude=${fmt(lat)}&longitude=${fmt(lon)}" +
            "&current=temperature_2m,weather_code,wind_speed_10m,precipitation" +
            "&hourly=weather_code,temperature_2m&forecast_hours=12&timeformat=unixtime&timezone=auto"

    private fun fmt(v: Double) = ((v * 100).roundToInt() / 100.0).toString() // coarse: ~1km

    fun kindOf(code: Int, windKmh: Double): WeatherKind = when {
        code in 95..99 || windKmh >= STORM_WIND_KMH -> WeatherKind.STORM
        code in 71..77 || code in 85..86 -> WeatherKind.SNOW
        code in 51..67 || code in 80..82 -> WeatherKind.RAIN
        code in 2..48 -> WeatherKind.CLOUDY
        else -> WeatherKind.CLEAR
    }

    fun parse(body: String, now: Long, lat: Double, lon: Double): WeatherSnapshot? = runCatching {
        val root = Json.parseToJsonElement(body).jsonObject
        val cur = root["current"]!!.jsonObject
        val hourly = root["hourly"] as? JsonObject
        val times = (hourly?.get("time") as? JsonArray)?.map { it.jsonPrimitive.content.toLong() * 1000 }.orEmpty()
        val codes = (hourly?.get("weather_code") as? JsonArray)?.map { it.jsonPrimitive.intOrNull ?: 0 }.orEmpty()
        val temps = (hourly?.get("temperature_2m") as? JsonArray)?.map { it.jsonPrimitive.doubleOrNull ?: 0.0 }.orEmpty()
        WeatherSnapshot(
            tempC = cur["temperature_2m"]!!.jsonPrimitive.doubleOrNull ?: return null,
            code = cur["weather_code"]?.jsonPrimitive?.intOrNull ?: 0,
            windKmh = cur["wind_speed_10m"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
            precipMm = cur["precipitation"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
            fetchedAt = now, lat = lat, lon = lon,
            hours = times.indices.filter { it < codes.size && it < temps.size }.map { HourForecast(times[it], codes[it], temps[it]) },
        )
    }.getOrNull()

    fun encode(w: WeatherSnapshot): String = listOf(w.tempC, w.code, w.windKmh, w.precipMm, w.fetchedAt, w.lat, w.lon).joinToString(",") +
        "#" + w.hours.joinToString(";") { "${it.atMillis},${it.code},${it.tempC}" }

    fun decode(raw: String?): WeatherSnapshot? = runCatching {
        if (raw.isNullOrBlank()) return null
        val (head, tail) = raw.split("#", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        val p = head.split(",")
        WeatherSnapshot(p[0].toDouble(), p[1].toInt(), p[2].toDouble(), p[3].toDouble(), p[4].toLong(), p[5].toDouble(), p[6].toDouble(),
            tail.split(";").filter { it.isNotBlank() }.map { val h = it.split(","); HourForecast(h[0].toLong(), h[1].toInt(), h[2].toDouble()) })
    }.getOrNull()

    /** First upcoming hour (within [withinHours]) whose kind is wet and differs from now. */
    fun upcomingChange(w: WeatherSnapshot, now: Long, withinHours: Int = 6): HourForecast? =
        w.hours.firstOrNull { it.atMillis > now && it.atMillis <= now + withinHours * 3_600_000L && it.kind.wet && it.kind != w.kind }

    fun label(kind: WeatherKind): String = L10n.t("wx_" + kind.name.lowercase())

    fun temp(c: Double, fahrenheit: Boolean): String =
        if (fahrenheit) "${(c * 9 / 5 + 32).roundToInt()}°F" else "${c.roundToInt()}°"
}

/** Life-event categories for the user's own schedules. */
object Categories {
    val all = listOf("work", "study", "exercise", "meeting", "social", "health", "chores", "other")
    fun normalize(raw: String): String = when (raw) {
        in all -> raw
        "sport" -> "exercise"
        "pill" -> "health"
        "meal", "task" -> "other"
        else -> "other"
    }
}

enum class Condition { GOOD, HAPPY, HUNGRY, COLD, HOT, SICK }

/** Outfit warmth: positive keeps warm, negative keeps cool. Raincoat also blocks rain. */
object OutfitClimate {
    val warmth = mapOf("knit_scarf" to 2, "hoodie" to 1, "raincoat" to 1, "beret" to 1, "blanket" to 1,
        "straw_hat" to -2, "sunglasses" to -1)
    fun of(id: String?) = warmth[id] ?: 0
    fun rainproof(id: String?) = id == "raincoat" || id == "rain_boots"
}

data class ConditionState(
    val sick: Boolean = false,
    /** 0..100 while sick. */
    val recovery: Int = 0,
    /** Minutes spent cold / hot / starving since last check. */
    val discomfort: Int = 0,
    val checkedAt: Long = 0,
)

object ConditionRules {
    const val SICK_XP_PERCENT = 90
    const val RECOVERY_PER_TASK = 20
    const val RECOVERY_MEDICINE = 80
    const val RECOVERY_FEED = 5
    const val MEDICINE_PRICE = 50
    const val MEDICINE_ID = "medicine"
    const val DISCOMFORT_LIMIT = 180
    const val SICK_CHANCE_PERCENT = 35

    fun read(extras: Map<String, String>): ConditionState {
        val p = extras["cond"]?.split(",") ?: return ConditionState()
        return ConditionState(p.getOrNull(0) == "1", p.getOrNull(1)?.toIntOrNull() ?: 0, p.getOrNull(2)?.toIntOrNull() ?: 0, p.getOrNull(3)?.toLongOrNull() ?: 0)
    }
    fun write(extras: Map<String, String>, c: ConditionState) =
        extras + ("cond" to listOf(if (c.sick) "1" else "0", c.recovery, c.discomfort, c.checkedAt).joinToString(","))

    /** Felt climate: null when weather is off. */
    fun climate(weather: WeatherSnapshot?, outfit: String?): Condition? {
        weather ?: return null
        val feel = weather.tempC + OutfitClimate.of(outfit) * 4
        val wetChill = weather.kind.wet && !OutfitClimate.rainproof(outfit) && weather.tempC < 15
        return when {
            feel < 8 || wetChill -> Condition.COLD
            feel > 27 -> Condition.HOT
            else -> null
        }
    }

    fun current(c: ConditionState, weather: WeatherSnapshot?, outfit: String?, hunger: Int, happiness: Int): Condition = when {
        c.sick -> Condition.SICK
        else -> climate(weather, outfit) ?: when {
            hunger < 30 -> Condition.HUNGRY
            happiness >= 80 && hunger >= 60 -> Condition.HAPPY
            else -> Condition.GOOD
        }
    }

    /** Advance discomfort and maybe fall sick. [roll] 0..99 decides the occasional sickness. */
    fun tick(c: ConditionState, now: Long, weather: WeatherSnapshot?, outfit: String?, hunger: Int, roll: Int): ConditionState {
        if (c.checkedAt == 0L) return c.copy(checkedAt = now)
        val minutes = ((now - c.checkedAt) / 60_000L).toInt().coerceIn(0, 72 * 60)
        if (minutes <= 0) return c
        if (c.sick) return c.copy(checkedAt = now)
        val uncomfortable = climate(weather, outfit) != null || hunger < 20
        val d = if (uncomfortable) c.discomfort + minutes else (c.discomfort - minutes).coerceAtLeast(0)
        if (d >= DISCOMFORT_LIMIT) {
            return if (roll < SICK_CHANCE_PERCENT) ConditionState(true, 0, 0, now) else c.copy(discomfort = DISCOMFORT_LIMIT / 2, checkedAt = now)
        }
        return c.copy(discomfort = d, checkedAt = now)
    }

    fun heal(c: ConditionState, amount: Int): ConditionState {
        if (!c.sick) return c.copy(discomfort = (c.discomfort - amount * 3).coerceAtLeast(0))
        val r = c.recovery + amount
        return if (r >= 100) ConditionState(false, 0, 0, c.checkedAt) else c.copy(recovery = r)
    }

    fun xp(base: Int, c: ConditionState): Int = if (c.sick) (base * SICK_XP_PERCENT / 100).coerceAtLeast(if (base > 0) 1 else 0) else base

    fun labelKey(cond: Condition) = "cond_" + cond.name.lowercase()
}

/** What 몽이 knows about the moment, for picking a line. */
data class Situation(
    val now: Long,
    val phase: DayPhase,
    val condition: Condition,
    val weather: WeatherSnapshot?,
    val fahrenheit: Boolean,
    val nextTitle: String?,
    val nextPlace: String?,
    val nextAt: Long?,
    val nextClock: String?,
    val todayCount: Int,
    val todayLeft: Int,
    val hunger: Int,
    val recovered: Boolean = false,
)

data class Line(val key: String, val text: String)

/**
 * 몽이 the secretary. Priority: imminent schedule > weather warning > today summary > condition / casual.
 * Lines live in the L10n tables as line_<group>_<n>; some take %s arguments. The last [HISTORY] keys never repeat.
 */
object Secretary {
    const val HISTORY = 8

    val groups: Map<String, Int> = linkedMapOf(
        "soon" to 6, "soon_rain" to 3, "soon_place" to 4, "late" to 3,
        "rain_now" to 4, "snow_now" to 4, "storm_now" to 3, "rain_later" to 4, "snow_later" to 2, "temp" to 4,
        "today_one" to 3, "today_many" to 3, "today_none" to 4, "today_done" to 3, "tonight" to 3,
        "sick" to 5, "recovered" to 3, "cold" to 5, "hot" to 5, "hungry" to 5, "happy" to 5,
        "dawn" to 3, "morning" to 4, "noon" to 3, "evening" to 3, "night" to 4, "self" to 8,
    )

    fun keys(group: String): List<String> = (0 until (groups[group] ?: 0)).map { "line_${group}_$it" }

    private fun minutesUntil(s: Situation) = s.nextAt?.let { ((it - s.now) / 60_000L).toInt() }

    /** Candidate groups in priority order with their format args. */
    fun candidates(s: Situation, selfTalk: Boolean = false): List<Pair<String, List<Any>>> {
        val out = mutableListOf<Pair<String, List<Any>>>()
        if (selfTalk) { out += "self" to emptyList(); return out }
        val mins = minutesUntil(s)
        val title = s.nextTitle.orEmpty()
        val place = s.nextPlace.orEmpty()
        val w = s.weather?.takeIf { it.usable(s.now) }
        val later = w?.let { Weather.upcomingChange(it, s.now, 12) }
        if (mins != null && mins in -30..-1) out += "late" to listOf(title)
        if (mins != null && mins in 0..60) {
            if (later != null && s.nextAt != null && later.atMillis <= s.nextAt + 3_600_000L && later.kind == app.mongle.logic.WeatherKind.RAIN) out += "soon_rain" to listOf(title)
            if (place.isNotBlank()) out += "soon_place" to listOf(mins, place, title)
            out += "soon" to listOf(mins, title)
        }
        if (s.condition == Condition.SICK) out += "sick" to emptyList()
        if (s.recovered) out += "recovered" to emptyList()
        if (w != null) {
            when (w.kind) {
                WeatherKind.RAIN -> out += "rain_now" to emptyList()
                WeatherKind.SNOW -> out += "snow_now" to emptyList()
                WeatherKind.STORM -> out += "storm_now" to emptyList()
                else -> Unit
            }
            if (later != null && later.kind == WeatherKind.RAIN) out += "rain_later" to emptyList()
            if (later != null && later.kind == WeatherKind.SNOW) out += "snow_later" to emptyList()
        }
        when {
            s.todayCount == 0 -> out += "today_none" to emptyList()
            s.todayLeft == 0 -> out += "today_done" to emptyList()
            s.todayLeft == 1 && s.nextClock != null -> out += "today_one" to listOf(s.nextClock, if (place.isNotBlank()) "$place $title" else title)
            s.nextClock != null -> out += "today_many" to listOf(s.todayLeft, s.nextClock, title)
        }
        if (s.phase == DayPhase.EVENING && s.todayLeft > 0 && s.nextClock != null) out += "tonight" to listOf(title)
        when (s.condition) {
            Condition.COLD -> out += "cold" to emptyList()
            Condition.HOT -> out += "hot" to emptyList()
            Condition.HUNGRY -> out += "hungry" to emptyList()
            Condition.HAPPY -> out += "happy" to emptyList()
            else -> Unit
        }
        if (w != null) out += "temp" to listOf(Weather.temp(w.tempC, s.fahrenheit))
        out += s.phase.name.lowercase() to emptyList()
        out += "self" to emptyList()
        return out
    }

    /** Highest-priority group that still has a line not in [recent]. */
    fun pick(s: Situation, recent: List<String>, random: Random = Random.Default, selfTalk: Boolean = false): Line {
        for ((group, args) in candidates(s, selfTalk)) {
            val fresh = keys(group).filter { it !in recent.takeLast(HISTORY) }
            if (fresh.isEmpty()) continue
            val key = fresh[random.nextInt(fresh.size)]
            return Line(key, format(L10n.t(key), args))
        }
        // Everything recent: reuse the least recently shown line of the top group.
        val (group, args) = candidates(s, selfTalk).first()
        val key = keys(group).minByOrNull { k -> recent.indexOf(k).let { if (it < 0) -1 else it } } ?: keys("self").first()
        return Line(key, format(L10n.t(key), args))
    }

    /** Inserted schedule/weather details are wrapped in ** so every surface can emphasize them. */
    fun format(template: String, args: List<Any>): String = L10n.format(template, args) { Emphasis.wrap(it) }

    fun remember(recent: List<String>, key: String) = (recent + key).takeLast(HISTORY)
}

/** Lightweight emphasis markup: **bold accent**. */
object Emphasis {
    const val MARK = "**"
    fun wrap(v: String) = MARK + v + MARK
    fun strip(text: String) = text.replace(MARK, "")
    /** Splits into (text, emphasized) runs. */
    fun runs(text: String): List<Pair<String, Boolean>> {
        val parts = text.split(MARK)
        return parts.mapIndexedNotNull { i, p -> if (p.isEmpty()) null else p to (i % 2 == 1) }
    }
}
