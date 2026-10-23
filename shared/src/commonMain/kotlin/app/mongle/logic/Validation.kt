package app.mongle.logic

import app.mongle.model.EndKind
import app.mongle.model.RepeatRule

/** Schedule form validation: errors block saving (inline), warnings don't. Values are l10n keys. */
object DraftValidator {
    const val TITLE_MAX = 40
    const val PLACE_MAX = 40
    const val MEMO_MAX = 200

    /** [strictWeekdays]: the form's '요일 선택' needs ≥1 day; stored drafts with 0 mean 'the start weekday'. */
    fun errors(d: ScheduleDraft, strictWeekdays: Boolean = false): Map<String, String> = buildMap {
        when {
            d.title.isBlank() -> put("title", "err_title_required")
            d.title.trim().length > TITLE_MAX -> put("title", "err_title_long")
        }
        if (d.endMillis != 0L && d.endMillis <= d.timeMillis) put("end", "err_end_before")
        if (strictWeekdays && d.repeat == RepeatRule.WEEKLY && d.interval < 2 && d.weekdays == 0) put("weekdays", "err_weekdays")
        if ((d.repeat == RepeatRule.EVERY_N_DAYS || d.repeat == RepeatRule.WEEKLY) && d.interval < 1) put("interval", "err_interval")
        if (d.repeat != RepeatRule.NONE && d.endKind == EndKind.COUNT && d.endCount < 1) put("count", "err_count")
        if (d.repeat != RepeatRule.NONE && d.endKind == EndKind.UNTIL && d.endAt <= d.timeMillis) put("until", "err_until_before")
        if (d.place.trim().length > PLACE_MAX) put("place", "err_place_long")
        if (d.memo.trim().length > MEMO_MAX) put("memo", "err_memo_long")
    }

    fun warnings(d: ScheduleDraft, now: Long): Map<String, String> = buildMap {
        if (d.repeat == RepeatRule.NONE && d.timeMillis < now) put("time", "warn_past")
    }

    /** Pet naming: 1–10 characters after trimming. */
    const val PET_NAME_MAX = 12
    fun petNameError(name: String): String? = when {
        name.isBlank() -> "err_name_required"
        name.trim().length > PET_NAME_MAX -> "err_name_long"
        else -> null
    }
}
