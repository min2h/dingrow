package app.mongle.ui

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.shadow
import kotlinx.coroutines.launch

import androidx.compose.runtime.rememberCoroutineScope

import androidx.compose.runtime.collectAsState

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import app.mongle.l10n.L10n
import app.mongle.model.EconomyConfig
import app.mongle.logic.Recurrence
import app.mongle.logic.ScheduleDraft
import app.mongle.logic.ScheduleTemplates
import app.mongle.logic.parseMillis
import app.mongle.logic.resellAmount
import app.mongle.logic.spec
import app.mongle.logic.StoreProduct
import app.mongle.model.AlarmSound
import app.mongle.model.AlertStyle
import app.mongle.model.AppSettings
import app.mongle.model.Catalog
import app.mongle.model.EditScope
import app.mongle.model.EndKind
import app.mongle.model.GameState
import app.mongle.model.ItemDef
import app.mongle.model.Rarity
import app.mongle.model.RepeatRule
import app.mongle.model.ScheduleView
import app.mongle.logic.StoreProducts
import kotlinx.datetime.Clock
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

private val PillShape = RoundedCornerShape(20.dp)
private val CardShapeLocal = RoundedCornerShape(24.dp)

data class DayCard(
    val view: ScheduleView,
    val occurrence: Long,
    val done: Boolean,
)

fun cardsForDay(views: List<ScheduleView>, dayStart: Long, dayEnd: Long, now: Long, tz: String): List<DayCard> {
    val cards = mutableListOf<DayCard>()
    views.forEach { view ->
        val hits = Recurrence.between(view.spec(tz), dayStart, dayEnd)
        val doneAt = parseMillis(view.doneDates)
        val skipped = parseMillis(view.skipDates)
        val excluded = parseMillis(view.exdates)
        val shown = hits.filter { it !in excluded }
        if (shown.isEmpty()) {
            if (!view.done) cards += DayCard(view, view.timeMillis, false)
            return@forEach
        }
        shown.forEach { millis ->
            val closed = millis in doneAt || millis in skipped || (view.done && view.repeat == RepeatRule.NONE)
            val id = if (millis == view.timeMillis) view.id else "${view.id}@$millis"
            cards += DayCard(
                view.copy(
                    id = id,
                    timeMillis = millis,
                    overdue = !closed && millis <= now,
                ),
                millis,
                closed,
            )
        }
    }
    return cards.sortedBy { it.occurrence }
}

@Composable
fun ScheduleForm(
    editing: Boolean,
    onSave: (ScheduleDraft, EditScope) -> Unit,
    onClose: () -> Unit,
    /** Schedule being edited: the form starts filled with it (it used to open blank, so edits could not be saved). */
    source: app.mongle.model.ScheduleView? = null,
    /** Occurrence time being edited (for "this one only" edits of a repeating schedule). */
    occurrenceMillis: Long? = null,
    /** Reason the last save was refused by the app (form stays open). */
    rejected: String? = null,
) {
    val zone = TimeZone.currentSystemDefault()
    val initial = remember {
        val at = occurrenceMillis ?: source?.timeMillis
        if (at != null) Instant.fromEpochMilliseconds(at).toLocalDateTime(zone).let { Upcoming(it.date, it.hour, it.minute) }
        else upcoming(Clock.System.now(), zone)
    }
    var title by remember { mutableStateOf(source?.title ?: "") }
    var date by remember { mutableStateOf(initial.date) }
    var isPm by remember { mutableStateOf(initial.hour >= 12) }
    var hour12 by remember { mutableIntStateOf(toHour12(initial.hour)) }
    var minute by remember { mutableIntStateOf(initial.minute) }
    var repeat by remember { mutableStateOf(source?.repeat ?: RepeatRule.NONE) }
    var interval by remember { mutableIntStateOf(source?.interval?.coerceAtLeast(1) ?: 1) }
    var weekdays by remember { mutableIntStateOf(source?.weekdays ?: 0) }
    var monthOrdinal by remember { mutableIntStateOf(source?.monthOrdinal ?: 0) }
    var endKind by remember { mutableStateOf(source?.endKind ?: EndKind.NEVER) }
    var endCount by remember { mutableIntStateOf(source?.endCount?.takeIf { it > 0 } ?: 10) }
    var endDate by remember { mutableStateOf(source?.endAt?.takeIf { it > 0 }?.let { Instant.fromEpochMilliseconds(it).toLocalDateTime(zone).date } ?: initial.date.plus(30, DateTimeUnit.DAY)) }
    var leads by remember { mutableStateOf(source?.leadsMinutes?.toSet()?.ifEmpty { setOf(0) } ?: setOf(0)) }
    var alert by remember { mutableStateOf(source?.alert ?: AlertStyle.SILENT) }
    var category by remember { mutableStateOf(source?.category ?: "other") }
    var place by remember { mutableStateOf(source?.place ?: "") }
    var memo by remember { mutableStateOf(source?.memo ?: "") }
    var scope by remember { mutableStateOf(EditScope.ALL) }
    var picking by remember { mutableStateOf<String?>(null) }
    var durationMin by remember { mutableIntStateOf(source?.let { v -> if (v.endMillis > v.timeMillis) ((v.endMillis - v.timeMillis) / 60_000L).toInt() else 0 } ?: 0) }
    var touched by remember { mutableStateOf(false) }

    fun draft() = ScheduleDraft(
                        title = title,
                        timeMillis = composeMillis(date, isPm, hour12, minute, zone),
                        repeat = repeat,
                        leadsMinutes = leads.toList().ifEmpty { listOf(0) },
                        alert = alert,
                        category = category,
                        place = place.trim(),
                        memo = memo.trim(),
                        weekdays = weekdays,
                        interval = interval.coerceAtLeast(1),
                        monthOrdinal = monthOrdinal,
                        endKind = endKind,
                        endAt = if (endKind == EndKind.UNTIL) composeMillis(endDate, true, 11, 59, zone) else 0L,
                        endCount = if (endKind == EndKind.COUNT) endCount else 0,
                        endMillis = if (durationMin > 0) composeMillis(date, isPm, hour12, minute, zone) + durationMin * 60_000L else 0L,
                    )
    val current = draft()
    val errors = app.mongle.logic.DraftValidator.errors(current, strictWeekdays = true)
    val warnings = app.mongle.logic.DraftValidator.warnings(current, Clock.System.now().toEpochMilliseconds())
    // Title "required" shows only after the user typed or tried to save; other errors show at once.
    val shown = if (touched) errors else errors - "title"
    val canSave = errors.isEmpty()

    fun save() {
        touched = true
        if (!canSave) return
        onSave(current, if (editing) scope else EditScope.ALL)
    }

    if (picking != null) {
        DateDialog(
            initial = if (picking == "end") endDate else date,
            onDismiss = { picking = null },
            onPick = { picked ->
                if (picking == "end") endDate = picked else date = picked
                picking = null
            },
        )
    }

    Column(Modifier.fillMaxSize().background(Cream)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onClose, modifier = Modifier.heightIn(min = 48.dp).testTag("overlay-back")) {
                Text("‹", color = Ink, fontSize = 22.sp)
            }
            Text(t("add_schedule"), modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink)
            TextButton(onClick = { save() }, modifier = Modifier.heightIn(min = 48.dp).testTag("save-top")) {
                Text(t("save"), color = if (canSave) PeachDeep else InkSoft, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        ) {
            FormField(title, { title = it; touched = true }, t("task_hint"), "field-title", big = true)
            FieldError(shown["title"], "err-title")
            Spacer(Modifier.height(16.dp))
            SectionLabel(t("quick_pick"))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ScheduleTemplates.all.forEach { template ->
                    val label = if (template.key.isBlank()) template.title else t(template.key)
                    ChoiceChip(label, title == label) {
                        title = label
                        repeat = template.repeat
                        interval = template.interval
                        weekdays = template.weekdays
                        monthOrdinal = template.monthOrdinal
                        category = template.category
                        isPm = template.hour >= 12
                        hour12 = toHour12(template.hour)
                        minute = template.minute
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            SectionLabel(t("place_label"))
            FormField(place, { place = it }, t("place_hint"), "field-place")
            FieldError(shown["place"], "err-place")
            Spacer(Modifier.height(10.dp))
            SectionLabel(t("memo_label"))
            FormField(memo, { memo = it }, t("memo_hint"), "field-memo")
            FieldError(shown["memo"], "err-memo")
            Spacer(Modifier.height(14.dp))
            SectionLabel(t("category"))
            CategoryRow(category) { category = it }
            Spacer(Modifier.height(14.dp))
            SectionLabel(t("time_label"))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(PillShape)
                    .background(CardWhite)
                    .clickable { picking = "start" }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(tf("date_row", L10n.formatDate(date)), color = Ink, modifier = Modifier.weight(1f))
                Text("›", color = InkSoft)
            }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(20.dp), spotColor = Color(0x22000000)).background(CardWhite, RoundedCornerShape(20.dp)).padding(horizontal = 12.dp, vertical = 4.dp)) {
            Box(Modifier.align(Alignment.Center).fillMaxWidth().height(36.dp).background(PeachSoft, RoundedCornerShape(12.dp)))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Wheel(
                    listOf(t("am"), t("pm")),
                    if (isPm) 1 else 0,
                    { isPm = it == 1 },
                    Modifier.weight(1f),
                )
                Wheel(
                    (1..12).map { it.toString() },
                    hour12 - 1,
                    { hour12 = it + 1 },
                    Modifier.weight(1f),
                )
                Wheel(
                    (0..59).map { it.toString().padStart(2, '0') },
                    minute,
                    { minute = it },
                    Modifier.weight(1f),
                )
            }
            }
            warnings["time"]?.let { Text(t(it), color = Color(0xFFB7791F), fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp).testTag("warn-past")) }
            Spacer(Modifier.height(10.dp))
            SectionLabel(t("end_time"))
            ChoiceRow(listOf(0 to t("end_time_none"), 30 to t("end_time_30"), 60 to t("end_time_60"), 120 to t("end_time_120")), durationMin) { durationMin = it }
            FieldError(shown["end"], "err-end")
            Spacer(Modifier.height(14.dp))
            SectionLabel(t("repeat"))
            ChoiceRow(
                listOf(
                    RepeatRule.NONE to t("repeat_off"),
                    RepeatRule.DAILY to t("repeat_daily"),
                    RepeatRule.WEEKDAYS to t("repeat_weekdays"),
                    RepeatRule.WEEKLY to t("repeat_pick_days"),
                ),
                repeat,
            ) { picked ->
                repeat = picked
                if (picked != RepeatRule.WEEKLY) interval = 1
                // '요일 선택' starts with the start day picked, so it's never empty by default.
                if (picked == RepeatRule.WEEKLY && weekdays == 0) weekdays = 1 shl (date.dayOfWeek.ordinal + 1) % 7
            }
            ChoiceRow(
                listOf(
                    RepeatRule.WEEKENDS to t("repeat_weekends"),
                    RepeatRule.WEEKLY to t("repeat_biweekly"),
                    RepeatRule.MONTHLY to t("repeat_monthly"),
                    RepeatRule.YEARLY to t("repeat_yearly"),
                    RepeatRule.EVERY_N_DAYS to t("repeat_n_days"),
                ),
                if (repeat == RepeatRule.WEEKLY && interval >= 2) RepeatRule.WEEKLY else repeat,
            ) { picked ->
                when (picked) {
                    RepeatRule.WEEKENDS -> {
                        repeat = RepeatRule.WEEKENDS
                        interval = 1
                    }
                    RepeatRule.WEEKLY -> {
                        repeat = RepeatRule.WEEKLY
                        interval = 2
                    }
                    else -> {
                        repeat = picked
                        if (picked == RepeatRule.EVERY_N_DAYS && interval < 2) interval = 2
                    }
                }
            }
            if (repeat == RepeatRule.WEEKLY || repeat == RepeatRule.MONTHLY && monthOrdinal != 0) {
                WeekdayToggles(weekdays) { weekdays = it }
            }
            FieldError(shown["weekdays"], "err-weekdays")
            FieldError(shown["interval"], "err-interval")
            if (repeat == RepeatRule.EVERY_N_DAYS || (repeat == RepeatRule.WEEKLY && interval >= 2)) {
                ChoiceRow(
                    listOf(2 to "2", 3 to "3", 4 to "4", 7 to "7"),
                    interval,
                ) { interval = it }
            }
            if (repeat == RepeatRule.MONTHLY) {
                ChoiceRow(
                    listOf(0 to t("by_date"), 1 to t("nth_1"), 2 to t("nth_2"), 3 to t("nth_3"), 4 to t("nth_4"), -1 to t("nth_last")),
                    monthOrdinal,
                ) { monthOrdinal = it }
            }
            Spacer(Modifier.height(8.dp))
            SectionLabel(t("end"))
            ChoiceRow(
                listOf(EndKind.NEVER to t("end_never"), EndKind.UNTIL to t("end_until"), EndKind.COUNT to t("end_count")),
                endKind,
            ) { endKind = it }
            if (endKind == EndKind.UNTIL) {
                TextButton(onClick = { picking = "end" }) { Text(L10n.formatDate(endDate)) }
            }
            if (endKind == EndKind.COUNT) {
                ChoiceRow(listOf(5 to t("times_5"), 10 to t("times_10"), 30 to t("times_30")), endCount) { endCount = it }
            }
            FieldError(shown["count"], "err-count")
            FieldError(shown["until"], "err-until")
            Spacer(Modifier.height(8.dp))
            SectionLabel(t("lead"))
            LeadRow(leads) { leads = it }
            Spacer(Modifier.height(8.dp))
            SectionLabel(t("alert_mode"))
            ChoiceRow(
                listOf(AlertStyle.SILENT to t("alert_silent"), AlertStyle.SOUND to t("alert_sound"), AlertStyle.VIBRATE to t("alert_vibrate")),
                alert,
            ) { alert = it }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().background(Color(0xFFFAF4EE), RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                StillPet("base", app.mongle.model.Stage.BABY, app.mongle.model.Pose.IDLE, Modifier.size(32.dp))
                Spacer(Modifier.width(8.dp))
                Text(t("form_tip_bubble"), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ink)
            }
            if (editing) {
                Spacer(Modifier.height(8.dp))
                Text(t("edit_scope"), color = InkSoft, fontSize = 13.sp)
                ChoiceRow(
                    listOf(EditScope.ONE to t("scope_one"), EditScope.FOLLOWING to t("scope_following"), EditScope.ALL to t("scope_all")),
                    scope,
                ) { scope = it }
            }
            Spacer(Modifier.height(12.dp))
        }
        // Save is always tappable: a blocked save shows the reason instead of silently doing nothing.
        val blockedReason = if (touched && errors.isNotEmpty()) t(errors.values.first()) else rejected?.takeIf { it.isNotBlank() }
        if (blockedReason != null) {
            Text(
                tf("save_blocked", blockedReason),
                color = Color(0xFFD9604F), fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(top = 8.dp).testTag("save-blocked"),
            )
        }
        Button(
            onClick = { save() },
            shape = PillShape,
            colors = ButtonDefaults.buttonColors(containerColor = if (canSave) Peach else Peach.copy(alpha = 0.55f), contentColor = Color.White),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp).height(48.dp).testTag("save-bottom"),
        ) { Text(t("save_big"), fontWeight = FontWeight.Bold, fontSize = 16.sp) }
    }
}

@Composable
private fun FieldError(key: String?, tag: String) {
    if (key == null) return
    Text(t(key), color = Color(0xFFD9604F), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp, start = 4.dp).testTag(tag))
}

@Composable
fun AlarmScreen(
    timeLabel: String,
    title: String,
    speech: String,
    onAck: () -> Unit,
    onSnooze: () -> Unit,
    onDone: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().background(Cream).padding(horizontal = 28.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Text(timeLabel, fontSize = 56.sp, color = Ink)
        val dueTail = t("due_suffix").substringAfter("%s").trim()
        Text(if (dueTail.isNotEmpty() && title.endsWith(dueTail)) title else tf("due_suffix", title), color = Ink, fontSize = 18.sp)
        Spacer(Modifier.weight(1f))
        Box(contentAlignment = Alignment.TopEnd) {
            StillPet(
                "base",
                app.mongle.model.Stage.BABY,
                app.mongle.model.Pose.HAPPY,
                Modifier.size(180.dp),
            )
            Text(
                speech,
                modifier = Modifier
                    .clip(PillShape)
                    .background(CardWhite)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                color = Ink,
                fontSize = 13.sp,
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(t("alarm_feed_hint"), color = PeachDeep, fontSize = 14.sp)
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onAck,
            shape = PillShape,
            colors = ButtonDefaults.buttonColors(containerColor = PeachDeep),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text(t("ack")) }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onSnooze,
                shape = PillShape,
                colors = ButtonDefaults.buttonColors(containerColor = CardWhite, contentColor = Ink),
                modifier = Modifier.weight(1f).height(48.dp),
            ) { Text(t("snooze_again")) }
            Button(
                onClick = onDone,
                shape = PillShape,
                colors = ButtonDefaults.buttonColors(containerColor = CardWhite, contentColor = Ink),
                modifier = Modifier.weight(1f).height(48.dp),
            ) { Text(t("done")) }
        }
    }
}

@Composable
fun SettingsScreen(
    state: GameState,
    permissions: PermissionSnapshot,
    onChange: (AppSettings) -> Unit,
    onTip: () -> Unit,
    onPin: () -> Unit,
    onWallpaper: () -> Unit,
    onShop: () -> Unit,
    onClose: () -> Unit,
) {
    val settings = state.settings
    Column(Modifier.fillMaxSize().background(Cream)) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onClose, modifier = Modifier.heightIn(min = 48.dp).testTag("overlay-back")) {
                Text("‹", color = Ink, fontSize = 22.sp)
            }
            Text(t("settings"), modifier = Modifier.weight(1f), textAlign = TextAlign.Center, color = Ink, fontSize = 18.sp)
            Spacer(Modifier.width(48.dp))
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            StillPet("base", state.stage, app.mongle.model.Pose.IDLE, Modifier.size(96.dp).align(Alignment.CenterHorizontally))
            Text(state.petName, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = Ink)
            Spacer(Modifier.height(12.dp))
            Text(t("lang_label"), color = InkSoft, fontSize = 13.sp)
            ChoiceRow(
                listOf("system" to t("lang_system"), "ko" to t("lang_ko"), "en" to t("lang_en")),
                settings.language,
            ) { onChange(settings.copy(language = it)) }
            SettingRow(
                t("settings_lead"),
                if (settings.defaultLeadMinutes == 0) t("lead_0") else tf("lead_minutes", settings.defaultLeadMinutes),
            )
            SettingToggle(t("sound"), settings.soundEnabled) { onChange(settings.copy(soundEnabled = it)) }
            SettingToggle(t("vibrate"), settings.vibrateEnabled) { onChange(settings.copy(vibrateEnabled = it)) }
            SettingToggle(
                t("quiet"),
                settings.quietStartMinute >= 0,
            ) { on ->
                onChange(
                    if (on) settings.copy(quietStartMinute = 23 * 60, quietEndMinute = 7 * 60)
                    else settings.copy(quietStartMinute = -1, quietEndMinute = -1),
                )
            }
            Text(t("alarm_sound"), color = InkSoft, fontSize = 13.sp)
            ChoiceRow(
                listOf(AlarmSound.SOFT to t("sound_soft"), AlarmSound.CHIME to t("sound_chime"), AlarmSound.BELL to t("sound_bell")),
                settings.alarmSound,
            ) { onChange(settings.copy(alarmSound = it)) }
            SettingRow(t("perm_notifications"), if (permissions.notificationsOn) t("perm_on") else t("perm_off"))
            SettingRow(t("perm_exact"), if (permissions.exactAlarmsOn) t("exact_on") else t("exact_off"))
            SettingRow(t("perm_battery"), if (permissions.notificationsOn) t("battery_on") else t("battery_off"))
            SettingToggle(t("settings_weather"), settings.useLocation) { on ->
                if (!on) onChange(settings.copy(useLocation = false))
                else LocationBridge.request?.invoke() ?: onChange(settings.copy(useLocation = true))
            }
            SettingToggle(t("tips_toggle"), settings.tipsEnabled) { onChange(settings.copy(tipsEnabled = it)) }
            TextButton(onClick = onTip) { Text(t("tip_one")) }
            Button(
                onClick = onPin,
                shape = PillShape,
                colors = ButtonDefaults.buttonColors(containerColor = PeachSoft, contentColor = Ink),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(t("add_widget")) }
            Text(
                t("pin_fallback"),
                color = InkSoft,
                fontSize = 12.sp,
                modifier = Modifier.padding(vertical = 4.dp),
            )
            Button(
                onClick = onWallpaper,
                shape = PillShape,
                colors = ButtonDefaults.buttonColors(containerColor = CardWhite, contentColor = Ink),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(t("set_wallpaper")) }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onShop,
                shape = PillShape,
                colors = ButtonDefaults.buttonColors(containerColor = PeachDeep),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(t("coin_shop")) }
            LocalAuth.current?.let { auth ->
                val session by auth.session.collectAsState()
                val scope = rememberCoroutineScope()
                SettingRow(t("settings_account"), session?.let { t("provider_" + it.provider) + (if (it.email.isNotBlank()) " · " + it.email else "") }.orEmpty())
                TextButton(onClick = { scope.launch { auth.signOut() } }, modifier = Modifier.testTag("sign-out")) { Text(t("settings_sign_out"), color = PeachDeep) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun CoinShopScreen(
    coins: Int,
    phase: String,
    product: StoreProduct?,
    message: String,
    onPick: (StoreProduct) -> Unit,
    onPay: () -> Unit,
    onRestore: () -> Unit,
    onClose: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(Cream).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onClose) { Text("‹") }
            Text(t("coin_shop"), modifier = Modifier.weight(1f), textAlign = TextAlign.Center, color = Ink, fontSize = 18.sp)
            Text("$coins", color = Ink)
        }
        if (phase == "done") {
            Spacer(Modifier.weight(1f))
            Text(t("shop_done_title"), fontSize = 28.sp, color = Ink)
            StillPet("base", app.mongle.model.Stage.BABY, app.mongle.model.Pose.HAPPY, Modifier.size(160.dp))
            Text(message, color = InkSoft)
            Spacer(Modifier.weight(1f))
            Button(onClick = onClose, shape = PillShape, colors = ButtonDefaults.buttonColors(containerColor = PeachDeep), modifier = Modifier.fillMaxWidth()) {
                Text(t("ack"))
            }
            return@Column
        }
        if (phase == "confirm" && product != null) {
            Spacer(Modifier.weight(1f))
            Text(tf("iap_confirm", t("iap_${product.id}")) + " · " + app.mongle.logic.StorePrices.label(product), color = Ink)
            Text(product.priceLabel, fontSize = 28.sp, color = Ink)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onPay, shape = PillShape, colors = ButtonDefaults.buttonColors(containerColor = PeachDeep), modifier = Modifier.fillMaxWidth()) {
                Text(t("pay"))
            }
            Spacer(Modifier.weight(1f))
            return@Column
        }
        StoreProducts.all.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { item ->
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(CardShapeLocal)
                            .background(CardWhite)
                            .clickable { onPick(item) }
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (item.popular) Text(t("popular"), color = PeachDeep, fontSize = 11.sp)
                        Text(t("iap_${item.id}"), color = Ink, fontSize = 14.sp)
                        Text("${t("iap_${item.id}_blurb")} · ${app.mongle.logic.StorePrices.label(item)}", color = InkSoft, fontSize = 12.sp)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        TextButton(onClick = onRestore) { Text(t("restore")) }
    }
}

@Composable
fun OfferSheet(
    item: ItemDef,
    price: Int,
    coins: Int,
    onBuy: () -> Unit,
    onShop: () -> Unit,
    onClose: () -> Unit,
) {
    val short = coins < price
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(CardWhite).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ArtImage(item.pictureId(), Modifier.size(120.dp), L10n.item(item.id))
        Text(L10n.item(item.id), fontSize = 20.sp, color = Ink)
        Text("$price", color = Ink, fontSize = 22.sp)
        Spacer(Modifier.height(8.dp))
        if (short) {
            Text(t("short_coins"), color = Ink)
            Text(t("earn_loop"), color = InkSoft, fontSize = 13.sp, textAlign = TextAlign.Center)
        }
        Button(
            onClick = onBuy,
            enabled = !short,
            shape = PillShape,
            colors = ButtonDefaults.buttonColors(containerColor = PeachSoft, contentColor = Ink),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(t("buy")) }
        Row {
            TextButton(onClick = onClose) { Text(t("ack")) }
            TextButton(onClick = onShop) { Text(t("coin_shop")) }
        }
    }
}

@Composable
fun ResellSheet(item: ItemDef, onSell: () -> Unit, onClose: () -> Unit) {
    val sell = resellAmount(item)
    val common = item.price > 0 && item.rarity == Rarity.COMMON
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(CardWhite).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(t("resell_title"), color = Ink, fontSize = 18.sp)
        ArtImage(item.pictureId(), Modifier.size(96.dp), L10n.item(item.id))
        Text(L10n.item(item.id), color = Ink)
        if (item.rarity == Rarity.EPIC) Text(t("rarity_epic_badge"), color = PeachDeep, fontSize = 12.sp)
        Text(
            if (common) tf("resell_common", item.price, sell, EconomyConfig.commonResellPercent) else tf("resell_fixed", sell),
            color = InkSoft,
        )
        Button(onClick = onSell, shape = PillShape, colors = ButtonDefaults.buttonColors(containerColor = PeachDeep), modifier = Modifier.fillMaxWidth()) {
            Text(t("sell_short"))
        }
        TextButton(onClick = onClose) { Text(t("close")) }
    }
}

@Composable
fun ReactionGallery(previewPeak: Boolean) {
    val ids = listOf("bounce", "squish", "spin", "jump", "heart", "blush", "yawn", "surprise", "dance", "bow", "pat", "tickle")
    val titles = ids.map { t("react_${it}_name") }
    val lines = ids.map { t("react_$it") }
    Column(Modifier.fillMaxSize().background(Cream).verticalScroll(rememberScrollState()).padding(12.dp)) {
        Text(t("reactions_title"), color = Ink, fontSize = 22.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        titles.chunked(4).forEachIndexed { rowIndex, row ->
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEachIndexed { col, name ->
                    val index = rowIndex * 4 + col
                    val body = ReactionBodiesSafe[index]
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(92.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(reactionTint(index)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(lines[index], color = Ink, fontSize = 10.sp, modifier = Modifier.align(Alignment.TopCenter).padding(top = 4.dp))
                            StillPet(
                                "base",
                                app.mongle.model.Stage.BABY,
                                app.mongle.model.Pose.IDLE,
                                Modifier.size(54.dp).graphicsLayer {
                                    val peak = if (previewPeak) peakSquash(body) else Squash(1f, 1f, 0f, 0f)
                                    scaleX = peak.sx
                                    scaleY = peak.sy
                                    rotationZ = peak.rot
                                    translationY = peak.yDp
                                },
                            )
                        }
                        Text("${index + 1} $name", color = Ink, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

private val ReactionBodiesSafe = app.mongle.logic.ReactionBodies.order

internal data class Squash(val sx: Float, val sy: Float, val rot: Float, val yDp: Float)

internal fun peakSquash(body: String): Squash = when (body) {
    "bounce" -> Squash(1.34f, 0.66f, 0f, 0f)
    "squish" -> Squash(1.42f, 0.56f, 0f, 4f)
    "spin" -> Squash(1.18f, 0.82f, 28f, 0f)
    "jump" -> Squash(0.76f, 1.36f, 0f, -18f)
    "heart" -> Squash(1.26f, 1.22f, 0f, -6f)
    "blush" -> Squash(0.88f, 0.82f, 0f, 4f)
    "yawn" -> Squash(0.76f, 1.42f, 0f, 0f)
    "surprise" -> Squash(0.68f, 1.48f, 0f, -8f)
    "dance" -> Squash(1.3f, 0.7f, -18f, 0f)
    "bow" -> Squash(1.14f, 0.68f, 24f, 8f)
    "pat" -> Squash(1.2f, 0.78f, 0f, 6f)
    "tickle" -> Squash(1.38f, 0.62f, 10f, 0f)
    else -> Squash(1f, 1f, 0f, 0f)
}

internal fun squashFrames(body: String): List<Squash> {
    val peak = peakSquash(body)
    val settle = Squash(1f, 1f, 0f, 0f)
    return when (body) {
        "bounce" -> listOf(settle, peak, Squash(0.8f, 1.3f, 0f, -8f), Squash(1.12f, 0.9f, 0f, 0f), settle)
        "tickle" -> listOf(settle, peak, settle, peak, settle, peak, settle)
        "jump" -> listOf(settle, Squash(1.2f, 0.75f, 0f, 4f), peak, Squash(1.28f, 0.7f, 0f, 2f), settle)
        else -> listOf(settle, peak, Squash(peak.sx * 0.92f, peak.sy * 1.05f, peak.rot * 0.4f, peak.yDp * 0.3f), settle)
    }
}

private fun reactionTint(index: Int): Color {
    val colors = listOf(PeachSoft, Color(0xFFD7ECFF), MintSoft, ButterSoft, Color(0xFFE6D9FF), Color(0xFFFFE0CC))
    return colors[index % colors.size]
}

@Composable
private fun Wheel(items: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier) {
    val safe = selected.coerceIn(0, items.lastIndex)
    val state = rememberLazyListState(safe)
    Box(
        modifier.height(132.dp),
    ) {
        LazyColumn(state = state, modifier = Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 44.dp)) {
            itemsIndexed(items) { index, label ->
                Text(
                    label,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clickable { onSelect(index) }
                        .padding(top = 10.dp),
                    textAlign = TextAlign.Center,
                    color = if (index == safe) Ink else InkSoft.copy(alpha = 0.6f),
                    fontSize = if (index == safe) 22.sp else 16.sp,
                    fontWeight = if (index == safe) FontWeight.Bold else FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun <T> ChoiceRow(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            ChoiceChip(label, value == selected) { onSelect(value) }
        }
    }
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        modifier = Modifier
            .height(30.dp)
            .clip(PillShape)
            .background(if (selected) Peach else CardWhite)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 7.dp),
        color = if (selected) Color.White else Ink,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
    )
}

@Composable
private fun WeekdayToggles(mask: Int, onChange: (Int) -> Unit) {
    val names = L10n.weekLetters()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        names.forEachIndexed { index, name ->
            val bit = 1 shl index
            val on = mask and bit != 0
            Text(
                name,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (on) PeachDeep else CardWhite)
                    .clickable { onChange(if (on) mask and bit.inv() else mask or bit) }
                    .padding(top = 8.dp),
                color = if (on) Color.White else Ink,
                textAlign = TextAlign.Center,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun LeadRow(selected: Set<Int>, onChange: (Set<Int>) -> Unit) {
    val options = listOf(0 to t("lead_0"), 5 to t("lead_5"), 10 to t("lead_10"), 30 to t("lead_30"), 60 to t("lead_60"), 1440 to t("lead_1440"))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            ChoiceChip(label, value in selected) {
                val next = if (value in selected) selected - value else selected + value
                onChange(next.ifEmpty { setOf(0) })
            }
        }
    }
}

@Composable
private fun CategoryRow(selected: String, onSelect: (String) -> Unit) {
    val sel = app.mongle.logic.Categories.normalize(selected)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        app.mongle.logic.Categories.all.forEach { id ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(52.dp).clickable { onSelect(id) }.testTag("cat-$id")) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(if (sel == id) PeachSoft else CardWhite).border(1.5.dp, if (sel == id) PeachDeep else Color.Transparent, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { CategoryIcon(id, Modifier.size(22.dp)) }
                Text(t("cat_$id"), color = Ink, fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = InkSoft, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun FormField(value: String, onChange: (String) -> Unit, hint: String, tag: String, big: Boolean = false) {
    androidx.compose.foundation.text.BasicTextField(
        value = value, onValueChange = onChange, singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = if (big) 16.sp else 14.sp, fontWeight = if (big) FontWeight.Bold else FontWeight.Medium, color = Ink),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(Peach),
        modifier = Modifier.fillMaxWidth().height(48.dp).shadow(if (big) 4.dp else 0.dp, RoundedCornerShape(16.dp), spotColor = Color(0x22000000))
            .background(CardWhite, RoundedCornerShape(16.dp)).testTag(tag),
        decorationBox = { inner ->
            Box(Modifier.fillMaxSize().padding(horizontal = 18.dp), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) Text(hint, color = InkSoft.copy(alpha = 0.6f), fontSize = if (big) 16.sp else 14.sp)
                inner()
            }
        },
    )
}

@Composable
private fun SettingRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Ink, modifier = Modifier.weight(1f))
        Text(value, color = InkSoft, fontSize = 13.sp)
    }
}

@Composable
private fun SettingToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Ink, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun DateDialog(initial: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    var cursor by remember { mutableStateOf(LocalDate(initial.year, initial.monthNumber, 1)) }
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.clip(CardShapeLocal).background(CardWhite).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { cursor = cursor.minus(1, DateTimeUnit.MONTH) }) { Text("‹") }
                Text("${cursor.year}.${cursor.monthNumber}", modifier = Modifier.weight(1f), textAlign = TextAlign.Center, color = Ink)
                TextButton(onClick = { cursor = cursor.plus(1, DateTimeUnit.MONTH) }) { Text("›") }
            }
            val length = daysIn(cursor.year, cursor.monthNumber)
            (1..length).chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    week.forEach { day ->
                        Text(
                            "$day",
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .clickable { onPick(LocalDate(cursor.year, cursor.monthNumber, day)) }
                                .padding(top = 6.dp),
                            textAlign = TextAlign.Center,
                            color = Ink,
                        )
                    }
                }
            }
        }
    }
}

private data class Upcoming(val date: LocalDate, val hour: Int, val minute: Int)

private fun upcoming(now: Instant, zone: TimeZone): Upcoming {
    val local = now.toLocalDateTime(zone)
    var minute = local.minute + 1
    var hour = local.hour
    var date = local.date
    if (minute >= 60) {
        minute = 0
        hour += 1
    }
    if (hour >= 24) {
        hour = 9
        minute = 0
        date = date.plus(1, DateTimeUnit.DAY)
    }
    return Upcoming(date, hour, minute)
}

private fun toHour12(hour24: Int): Int {
    val hour = hour24 % 12
    return if (hour == 0) 12 else hour
}

private fun composeMillis(date: LocalDate, isPm: Boolean, hour12: Int, minute: Int, zone: TimeZone): Long {
    val hour24 = when {
        hour12 == 12 && !isPm -> 0
        hour12 == 12 && isPm -> 12
        isPm -> hour12 + 12
        else -> hour12
    }
    return LocalDateTime(date.year, date.monthNumber, date.dayOfMonth, hour24, minute).toInstant(zone).toEpochMilliseconds()
}

private fun daysIn(year: Int, month: Int): Int {
    val start = LocalDate(year, month, 1)
    val next = if (month == 12) LocalDate(year + 1, 1, 1) else LocalDate(year, month + 1, 1)
    return start.daysUntil(next)
}
