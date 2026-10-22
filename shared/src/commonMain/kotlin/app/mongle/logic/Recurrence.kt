package app.mongle.logic

import app.mongle.model.AlertStyle
import app.mongle.l10n.L10n
import app.mongle.model.EndKind
import app.mongle.model.RepeatRule
import app.mongle.model.ScheduleRecord
import app.mongle.model.ScheduleView
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

data class RecurSpec(
    val rule: RepeatRule,
    val interval: Int = 1,
    val weekdays: Int = 0,
    val monthOrdinal: Int = 0,
    val anchorMillis: Long = 0,
    val endKind: EndKind = EndKind.NEVER,
    val endAt: Long = 0,
    val endCount: Int = 0,
    val tz: String = "UTC",
)

data class ScheduleTemplate(
    val title: String,
    val repeat: RepeatRule,
    val hour: Int,
    val minute: Int,
    val weekdays: Int = 0,
    val interval: Int = 1,
    val category: String = "task",
    val monthOrdinal: Int = 0,
    val key: String = "",
)

object ScheduleTemplates {
    private const val MONDAY = 1 shl 1

    val all: List<ScheduleTemplate> = listOf(
        ScheduleTemplate("아침 물 마시기", RepeatRule.DAILY, 8, 0, category = "meal", key = "template_water"),
        ScheduleTemplate("약 먹기", RepeatRule.DAILY, 9, 0, category = "pill", key = "template_pill"),
        ScheduleTemplate("운동", RepeatRule.WEEKDAYS, 18, 0, category = "sport", key = "template_sport"),
        ScheduleTemplate("분리수거", RepeatRule.WEEKLY, 9, 0, weekdays = MONDAY, category = "task", key = "template_recycle"),
    )
}

object Recurrence {
    fun stepFrom(cursor: Long, spec: RecurSpec): Long {
        if (spec.rule == RepeatRule.NONE) return cursor
        val zone = ScheduleRules.zoneOrUtc(spec.tz)
        val interval = spec.interval.coerceAtLeast(1)
        val anchor = if (spec.anchorMillis > 0) spec.anchorMillis else cursor
        return when (spec.rule) {
            RepeatRule.NONE -> cursor
            RepeatRule.DAILY, RepeatRule.EVERY_N_DAYS -> shiftDays(cursor, interval, zone)
            RepeatRule.WEEKLY -> {
                val mask = spec.weekdays
                if (mask == 0) shiftDays(cursor, 7 * interval, zone) else nextMasked(cursor, anchor, mask, interval, zone)
            }
            RepeatRule.WEEKDAYS -> nextMasked(cursor, anchor, if (spec.weekdays != 0) spec.weekdays else WEEKDAY_MASK, 1, zone)
            RepeatRule.WEEKENDS -> nextMasked(cursor, anchor, if (spec.weekdays != 0) spec.weekdays else WEEKEND_MASK, 1, zone)
            RepeatRule.MONTHLY -> nextMonthly(anchor, cursor, interval, spec.monthOrdinal, spec.weekdays, zone)
            RepeatRule.YEARLY -> nextYearly(anchor, cursor, interval, zone)
        }
    }

    /** 소비한 회차 다음으로, now 이후이면서 끝난 규칙과 제외일에 걸리지 않는 시각. 없으면 null. */
    fun nextOpen(consumed: Long, spec: RecurSpec, now: Long, closed: Set<Long> = emptySet()): Long? {
        if (spec.rule == RepeatRule.NONE) return null
        var cursor = consumed
        var guard = 0
        while (guard++ < 800) {
            val next = stepFrom(cursor, spec)
            if (next <= cursor) return null
            if (!passesEnd(next, spec)) return null
            if (next > now && next !in closed) return next
            cursor = next
        }
        return null
    }

    /** [startInclusive, endExclusive) 안의 회차. 끝 규칙을 넘기면 멈춘다. */
    fun between(spec: RecurSpec, startInclusive: Long, endExclusive: Long, limit: Int = 64): List<Long> {
        if (endExclusive <= startInclusive) return emptyList()
        val anchor = spec.anchorMillis
        if (anchor <= 0 || spec.rule == RepeatRule.NONE) {
            return if (anchor in startInclusive until endExclusive && passesEnd(anchor, spec)) listOf(anchor) else emptyList()
        }
        val out = ArrayList<Long>(8)
        var cursor = anchor
        var guard = 0
        if (passesEnd(cursor, spec) && cursor < endExclusive) {
            if (cursor >= startInclusive) out += cursor
        }
        while (out.size < limit && guard++ < 2000) {
            val next = stepFrom(cursor, spec.copy(endKind = EndKind.NEVER, endCount = 0))
            if (next <= cursor) break
            if (!passesEnd(next, spec)) break
            if (next >= endExclusive) break
            if (next >= startInclusive) out += next
            cursor = next
        }
        return out
    }

    fun passesEnd(millis: Long, spec: RecurSpec): Boolean {
        val zone = ScheduleRules.zoneOrUtc(spec.tz)
        return when (spec.endKind) {
            EndKind.NEVER -> true
            EndKind.UNTIL -> {
                if (spec.endAt <= 0) true
                else {
                    val day = Instant.fromEpochMilliseconds(millis).toLocalDateTime(zone).date
                    val end = Instant.fromEpochMilliseconds(spec.endAt).toLocalDateTime(zone).date
                    day <= end
                }
            }
            EndKind.COUNT -> {
                if (spec.endCount <= 0) true else indexOf(millis, spec) <= spec.endCount
            }
        }
    }

    fun indexOf(millis: Long, spec: RecurSpec): Int {
        val anchor = spec.anchorMillis
        if (anchor <= 0 || millis <= anchor) return 1
        val open = spec.copy(endKind = EndKind.NEVER, endCount = 0, endAt = 0)
        var cursor = anchor
        var index = 1
        var guard = 0
        while (guard++ < 2000) {
            val next = stepFrom(cursor, open)
            if (next <= cursor) return index
            index++
            if (next >= millis) return index
            cursor = next
        }
        return index
    }

    fun label(spec: RecurSpec): String {
        val interval = spec.interval.coerceAtLeast(1)
        val days = dayNames(spec.weekdays)
        val base = when (spec.rule) {
            RepeatRule.NONE -> L10n.t("rule_once")
            RepeatRule.DAILY -> if (interval <= 1) L10n.t("rule_daily") else L10n.fmt("rule_every_n_days", interval)
            RepeatRule.EVERY_N_DAYS -> L10n.fmt("rule_every_n_days", interval)
            RepeatRule.WEEKDAYS -> L10n.t("rule_weekdays")
            RepeatRule.WEEKENDS -> L10n.t("rule_weekends")
            RepeatRule.WEEKLY -> when {
                spec.weekdays != 0 && interval <= 1 -> L10n.fmt("rule_weekly_days", days)
                spec.weekdays != 0 -> L10n.fmt("rule_n_weeks_days", interval, days)
                interval == 2 -> L10n.t("rule_biweekly")
                interval > 1 -> L10n.fmt("rule_n_weeks", interval)
                else -> L10n.t("rule_weekly")
            }
            RepeatRule.MONTHLY -> when (spec.monthOrdinal) {
                0 -> if (interval <= 1) L10n.t("rule_monthly") else L10n.fmt("rule_n_months", interval)
                -1 -> L10n.t("rule_month_last")
                else -> L10n.fmt("rule_month_nth", spec.monthOrdinal)
            }
            RepeatRule.YEARLY -> if (interval <= 1) L10n.t("rule_yearly") else L10n.fmt("rule_n_years", interval)
        }
        val tail = when (spec.endKind) {
            EndKind.COUNT -> if (spec.endCount > 0) L10n.fmt("rule_end_count", spec.endCount) else ""
            EndKind.UNTIL -> L10n.t("rule_end_until")
            EndKind.NEVER -> ""
        }
        return base + tail
    }
}

fun ScheduleRecord.spec(): RecurSpec = RecurSpec(
    rule = repeat,
    interval = interval.coerceAtLeast(1),
    weekdays = weekdays,
    monthOrdinal = monthOrdinal,
    anchorMillis = if (anchorMillis > 0) anchorMillis else timeMillis,
    endKind = endKind,
    endAt = endAt,
    endCount = endCount,
    tz = tz.ifBlank { "UTC" },
)

fun ScheduleView.spec(tz: String): RecurSpec = RecurSpec(
    rule = repeat,
    interval = interval.coerceAtLeast(1),
    weekdays = weekdays,
    monthOrdinal = monthOrdinal,
    anchorMillis = if (anchorMillis > 0) anchorMillis else timeMillis,
    endKind = endKind,
    endAt = endAt,
    endCount = endCount,
    tz = tz,
)

fun parseMillis(raw: String): Set<Long> =
    raw.split(',').mapNotNull { it.toLongOrNull() }.toSet()

fun addMillis(raw: String, value: Long): String {
    val values = parseMillis(raw) + value
    return values.sorted().joinToString(",")
}

fun draftAlert(name: String): AlertStyle =
    runCatching { AlertStyle.valueOf(name) }.getOrDefault(AlertStyle.SOUND)

private const val WEEKEND_MASK = 0b1000001

private fun shiftDays(millis: Long, days: Int, zone: TimeZone): Long {
    val local = Instant.fromEpochMilliseconds(millis).toLocalDateTime(zone)
    val date = local.date.plus(days, DateTimeUnit.DAY)
    return LocalDateTime(date, local.time).toInstant(zone).toEpochMilliseconds()
}

private fun nextMasked(cursor: Long, anchor: Long, mask: Int, interval: Int, zone: TimeZone): Long {
    val local = Instant.fromEpochMilliseconds(cursor).toLocalDateTime(zone)
    val anchorDate = Instant.fromEpochMilliseconds(anchor).toLocalDateTime(zone).date
    val anchorWeek = startOfWeek(anchorDate)
    val stepWeeks = interval.coerceAtLeast(1)
    var date = local.date
    val span = stepWeeks * 14 + 21
    repeat(span) {
        date = date.plus(1, DateTimeUnit.DAY)
        val bit = weekdayBit(date.dayOfWeek.ordinal)
        if (mask and (1 shl bit) == 0) return@repeat
        val weeks = anchorWeek.daysUntil(startOfWeek(date)) / 7
        if (weeks % stepWeeks != 0) return@repeat
        return LocalDateTime(date, local.time).toInstant(zone).toEpochMilliseconds()
    }
    return shiftDays(cursor, 1, zone)
}

private fun nextMonthly(
    anchor: Long,
    after: Long,
    interval: Int,
    ordinal: Int,
    weekdays: Int,
    zone: TimeZone,
): Long {
    val origin = Instant.fromEpochMilliseconds(anchor).toLocalDateTime(zone)
    val step = interval.coerceAtLeast(1)
    val bit = preferredBit(weekdays, origin.date)
    var year = origin.year
    var month = origin.monthNumber
    repeat(720) {
        val date = if (ordinal == 0) {
            LocalDate(year, month, origin.dayOfMonth.coerceAtMost(daysInMonth(year, month)))
        } else {
            nthDate(year, month, ordinal, bit)
        }
        if (date != null) {
            val millis = LocalDateTime(date, origin.time).toInstant(zone).toEpochMilliseconds()
            if (millis > after) return millis
        }
        month += step
        while (month > 12) {
            month -= 12
            year += 1
        }
    }
    return shiftDays(after, 31, zone)
}

private fun nextYearly(anchor: Long, after: Long, interval: Int, zone: TimeZone): Long {
    val origin = Instant.fromEpochMilliseconds(anchor).toLocalDateTime(zone)
    val step = interval.coerceAtLeast(1)
    var year = origin.year
    repeat(400) {
        val day = origin.dayOfMonth.coerceAtMost(daysInMonth(year, origin.monthNumber))
        val millis = LocalDateTime(year, origin.monthNumber, day, origin.hour, origin.minute, origin.second, origin.nanosecond)
            .toInstant(zone)
            .toEpochMilliseconds()
        if (millis > after) return millis
        year += step
    }
    return shiftDays(after, 366, zone)
}

private fun nthDate(year: Int, month: Int, ordinal: Int, bit: Int): LocalDate? {
    val length = daysInMonth(year, month)
    if (ordinal == -1) {
        var day = length
        while (day >= 1) {
            val date = LocalDate(year, month, day)
            if (weekdayBit(date.dayOfWeek.ordinal) == bit) return date
            day--
        }
        return null
    }
    if (ordinal < 1) return null
    var seen = 0
    for (day in 1..length) {
        val date = LocalDate(year, month, day)
        if (weekdayBit(date.dayOfWeek.ordinal) == bit) {
            seen++
            if (seen == ordinal) return date
        }
    }
    return null
}

private fun preferredBit(mask: Int, fallback: LocalDate): Int {
    if (mask != 0) {
        for (bit in 0..6) {
            if (mask and (1 shl bit) != 0) return bit
        }
    }
    return weekdayBit(fallback.dayOfWeek.ordinal)
}

private fun startOfWeek(date: LocalDate): LocalDate {
    val back = weekdayBit(date.dayOfWeek.ordinal)
    return date.minus(back, DateTimeUnit.DAY)
}

private fun daysInMonth(year: Int, month: Int): Int {
    val start = LocalDate(year, month, 1)
    val next = if (month == 12) LocalDate(year + 1, 1, 1) else LocalDate(year, month + 1, 1)
    return start.daysUntil(next)
}

private fun dayNames(mask: Int): String {
    val names = L10n.weekLetters()
    val sep = if (L10n.code() == "ko") "" else " "
    return names.filterIndexed { index, _ -> mask and (1 shl index) != 0 }.joinToString(sep)
}
