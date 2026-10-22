package app.mongle.l10n

import app.mongle.logic.Korean
import app.mongle.model.Catalog
import app.mongle.model.SpeciesCatalog
import app.mongle.model.Stage
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * In-app language. "system" follows the device (Korean if the language is ko, otherwise English).
 * "ko" and "en" stick until the setting changes.
 */
object AppLocale {
    @Volatile
    var preference: String = "system"

    fun code(): String {
        val chosen = preference
        if (chosen == "ko" || chosen == "en") return chosen
        return if (systemLanguageCode().startsWith("ko")) "ko" else "en"
    }
}

expect fun systemLanguageCode(): String

object L10n {
    private val token = Regex("%[sd]")

    fun code(): String = AppLocale.code()

    fun t(key: String, lang: String = code()): String {
        val table = if (lang == "ko") Ko else En
        return table[key] ?: En[key] ?: key
    }

    fun fmt(key: String, vararg args: Any): String {
        var index = 0
        return Regex("%(\\d+)\\$[sd]").replace(token.replace(t(key).replace(Regex("%(\\d+)\\$([sd])")) { "\u0000" + it.groupValues[1] + "\u0000" }) {
            args.getOrNull(index++)?.toString().orEmpty()
        }) { it.value }.replace(Regex("\u0000(\\d+)\u0000")) { args.getOrNull(it.groupValues[1].toInt() - 1)?.toString().orEmpty() }.replace("%%", "%")
    }

    fun table(lang: String): Map<String, String> = if (lang == "ko") Ko else En

    fun item(id: String): String {
        val key = "item_$id"
        val value = t(key)
        return if (value == key) Catalog.byId(id)?.name ?: id else value
    }

    fun species(id: String): String {
        if (id.isBlank() || id == "base" || id == "sp_peach") return t("species_base")
        val key = "species_$id"
        val value = t(key)
        return if (value == key) SpeciesCatalog.byId(id).name else value
    }

    fun speciesBlurb(id: String): String {
        val key = "species_${id}_blurb"
        val value = t(key)
        return if (value == key) SpeciesCatalog.byId(id).blurb else value
    }

    fun preset(wallpaperId: String?): String = when (wallpaperId) {
        "mint" -> t("preset_mint")
        "starry" -> t("preset_starry")
        else -> t("preset_wood")
    }

    fun stage(stage: Stage): String = t(
        when (stage) {
            Stage.EGG -> "stage_egg"
            Stage.BABY -> "stage_baby"
            Stage.CHILD -> "stage_child"
            Stage.ADULT -> "stage_adult"
        },
    )

    fun calling(name: String): String =
        if (code() == "ko") fmt("notice_title", Korean.subject(name)) else fmt("notice_title", name)

    fun noticeBody(clock: String, title: String): String = fmt("notice_body", clock, title)

    fun clock(millis: Long, zone: TimeZone): String {
        val ldt = Instant.fromEpochMilliseconds(millis).toLocalDateTime(zone)
        if (code() == "ko") {
            return ldt.hour.toString().padStart(2, '0') + ":" + ldt.minute.toString().padStart(2, '0')
        }
        val suffix = if (ldt.hour < 12) t("am") else t("pm")
        val hour12 = ldt.hour % 12
        val hour = if (hour12 == 0) 12 else hour12
        return "$hour:${ldt.minute.toString().padStart(2, '0')} $suffix"
    }

    fun formatWhen(millis: Long, zone: TimeZone): String {
        if (code() == "ko") return Korean.formatWhen(millis, zone)
        val ldt = Instant.fromEpochMilliseconds(millis).toLocalDateTime(zone)
        return formatDate(ldt.date) + " " + clock(millis, zone)
    }

    fun formatDate(date: LocalDate): String {
        val weekday = t("wd_iso_${date.dayOfWeek.ordinal}")
        return if (code() == "ko") {
            "${date.monthNumber}월 ${date.dayOfMonth}일 ($weekday)"
        } else {
            "${t("month_${date.monthNumber}")} ${date.dayOfMonth} ($weekday)"
        }
    }

    fun weekLetters(): List<String> = (0..6).map { t("wd_$it") }

    fun react(id: String, fallback: String): String {
        val value = t("react_$id")
        return if (value == "react_$id") fallback else value
    }

    fun tip(index: Int, size: Int): String {
        if (size <= 0) return ""
        val slot = ((index % size) + size) % size
        return t("tip_$slot")
    }
}
