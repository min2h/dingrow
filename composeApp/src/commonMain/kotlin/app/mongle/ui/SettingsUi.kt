package app.mongle.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mongle.model.AlarmSound
import app.mongle.model.AppSettings
import app.mongle.model.GameState
import kotlinx.coroutines.launch

/** Settings per mockup sheet 6: home + 알림 / 몽이·소리·위젯 / 데이터·계정 pages + language sheet. */
@Composable
fun SettingsV2(
    state: GameState,
    permissions: PermissionSnapshot,
    onChange: (AppSettings) -> Unit,
    onTip: () -> Unit,
    onPin: () -> Unit,
    onWallpaper: () -> Unit,
    onShop: () -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit,
    onExport: () -> Unit = {},
    onRequestNotifications: () -> Unit = {},
    onRestore: () -> Unit = {},
    version: String = "0.2.0 (2)",
    startPage: String = "home",
) {
    var page by remember { mutableStateOf(startPage) }
    var langSheet by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }
    val authForSignOut = LocalAuth.current
    val signOutScope = rememberCoroutineScope()
    val s = state.settings
    Box(Modifier.fillMaxSize().background(Cream).testTag("settings-$page")) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(CircleShape).clickable { if (page == "home") onClose() else page = "home" }.testTag("overlay-back"), contentAlignment = Alignment.Center) {
                    Text("‹", color = Ink, fontSize = 26.sp)
                }
                Text(t("settings_title_$page"), modifier = Modifier.weight(1f), textAlign = TextAlign.Center, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(44.dp))
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
                when (page) {
                    "home" -> {
                        ProfileCard(state) { page = "account" }
                        Section(t("set_sec_display")) {
                            ValueRow(t("lang_label"), langLabel(s.language), "row-language") { langSheet = true }
                            SegRow(t("set_theme"), listOf("light" to t("set_theme_light"), "dark" to t("set_theme_dark"), "system" to t("set_theme_system")), s.theme) { onChange(s.copy(theme = it)) }
                            SegRow(t("set_clock"), listOf(false to t("set_12h"), true to t("set_24h")), s.use24h) { onChange(s.copy(use24h = it)) }
                            SegRow(t("set_week_start"), listOf(false to t("wd_0"), true to t("wd_1")), s.weekStartsMonday) { onChange(s.copy(weekStartsMonday = it)) }
                            SegRow(t("set_temp_unit"), listOf(false to "°C", true to "°F"), s.fahrenheit, last = true) { onChange(s.copy(fahrenheit = it)) }
                        }
                        Section(t("set_sec_weather")) {
                            ToggleRow(t("set_use_location"), s.useLocation, "toggle-location") { on ->
                                if (!on) onChange(s.copy(useLocation = false)) else LocationBridge.request?.invoke() ?: onChange(s.copy(useLocation = true))
                            }
                            ToggleRow(t("set_weather_alerts"), s.weatherAlerts && s.useLocation, "toggle-wx-alerts", enabled = s.useLocation, last = true) { onChange(s.copy(weatherAlerts = it)) }
                        }
                        if (!s.useLocation) Text(t("set_weather_off_note"), fontSize = 11.sp, color = InkSoft, modifier = Modifier.padding(start = 10.dp, bottom = 12.dp, end = 10.dp))
                        Section(t("set_sec_more")) {
                            ValueRow(t("settings_title_alerts"), "", "row-alerts") { page = "alerts" }
                            ValueRow(t("settings_title_pet"), "", "row-pet") { page = "pet" }
                            ValueRow(t("settings_title_data"), "", "row-data", last = true) { page = "data" }
                        }
                        Section(t("set_sec_extras")) {
                            ValueRow(t("add_widget"), "", "row-widget", onClick = onPin)
                            ValueRow(t("set_wallpaper"), "", "row-wallpaper", onClick = onWallpaper)
                            ValueRow(t("coin_shop"), "", "row-shop", onClick = onShop)
                            ValueRow(t("perm_notifications"), if (permissions.notificationsOn) t("perm_on") else t("perm_off"), "row-perm", onClick = if (permissions.notificationsOn) null else onRequestNotifications)
                            ValueRow(t("perm_exact"), if (permissions.exactAlarmsOn) t("exact_on") else t("exact_off"), "row-exact", last = true)
                        }
                    }
                    "alerts" -> {
                        Section(t("set_sec_schedule")) {
                            ToggleRow(t("set_schedule_alerts"), s.scheduleAlerts, "toggle-sched") { onChange(s.copy(scheduleAlerts = it)) }
                            ValueRow(t("settings_lead"), if (s.defaultLeadMinutes == 0) t("lead_0") else tf("lead_minutes", s.defaultLeadMinutes), "row-lead") {
                                val opts = listOf(0, 5, 10, 30, 60); onChange(s.copy(defaultLeadMinutes = opts[(opts.indexOf(s.defaultLeadMinutes) + 1).mod(opts.size)]))
                            }
                            ToggleRow(t("set_new_confirm"), s.newScheduleConfirm, "toggle-newc", last = true) { onChange(s.copy(newScheduleConfirm = it)) }
                        }
                        Section(t("set_sec_brief")) {
                            ToggleRow(t("set_brief"), s.briefingEnabled, "toggle-brief") { onChange(s.copy(briefingEnabled = it)) }
                            ValueRow(t("set_brief_time"), clockMinute(s.briefingMinute, s.use24h), "row-brief-time") {
                                onChange(s.copy(briefingMinute = (s.briefingMinute + 30).mod(24 * 60)))
                            }
                            ToggleRow(t("set_brief_weather"), s.briefingWeather, "toggle-bwx", last = true) { onChange(s.copy(briefingWeather = it)) }
                        }
                        Section(t("set_sec_news")) {
                            ToggleRow(t("set_gift_alerts"), s.giftAlerts, "toggle-gift") { onChange(s.copy(giftAlerts = it)) }
                            ToggleRow(t("tips_toggle"), s.tipsEnabled, "toggle-tips") { onChange(s.copy(tipsEnabled = it)) }
                            ToggleRow(t("set_weather_change"), s.weatherAlerts && s.useLocation, "toggle-wx2", enabled = s.useLocation, last = true) { onChange(s.copy(weatherAlerts = it)) }
                        }
                        Section(t("set_sec_quiet")) {
                            ToggleRow(t("quiet"), s.quietStartMinute >= 0, "toggle-quiet") { on ->
                                onChange(if (on) s.copy(quietStartMinute = 23 * 60, quietEndMinute = 7 * 60) else s.copy(quietStartMinute = -1, quietEndMinute = -1))
                            }
                            ValueRow(t("set_quiet_time"), if (s.quietStartMinute >= 0) clockMinute(s.quietStartMinute, true) + " – " + clockMinute(s.quietEndMinute, true) else "—", "row-quiet", last = true)
                        }
                    }
                    "pet" -> {
                        Section(t("set_sec_pet")) {
                            ToggleRow(t("set_wander"), s.wander, "toggle-wander") { onChange(s.copy(wander = it)) }
                            SegRow(t("set_bubble_freq"), listOf(0 to t("set_less"), 1 to t("set_normal"), 2 to t("set_more")), s.bubbleFrequency) { onChange(s.copy(bubbleFrequency = it)) }
                            SegRow(t("set_motion"), listOf(0 to t("set_motion_low"), 1 to t("set_normal"), 2 to t("set_motion_rich")), s.motionLevel) { onChange(s.copy(motionLevel = it)) }
                            ToggleRow(t("set_reduce_motion"), s.reduceMotion, "toggle-reduce", last = true) { onChange(s.copy(reduceMotion = it)) }
                        }
                        Section(t("set_sec_sound")) {
                            ToggleRow(t("set_sfx"), s.sfxEnabled, "toggle-sfx") { onChange(s.copy(sfxEnabled = it)) }
                            Row(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(t("set_volume"), fontSize = 14.sp, color = if (s.sfxEnabled) Ink else InkSoft, modifier = Modifier.weight(1f))
                                Slider(
                                    value = s.sfxVolume / 100f, onValueChange = { onChange(s.copy(sfxVolume = (it * 100).toInt())) }, enabled = s.sfxEnabled,
                                    modifier = Modifier.width(130.dp).testTag("slider-volume"),
                                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Peach, inactiveTrackColor = Color(0xFFECE4DE)),
                                )
                            }
                            Divider()
                            ValueRow(t("alarm_sound"), when (s.alarmSound) { AlarmSound.SOFT -> t("sound_soft"); AlarmSound.CHIME -> t("sound_chime"); else -> t("sound_bell") }, "row-alarm-sound") {
                                val all = listOf(AlarmSound.SOFT, AlarmSound.CHIME, AlarmSound.BELL); onChange(s.copy(alarmSound = all[(all.indexOf(s.alarmSound) + 1) % all.size]))
                            }
                            ToggleRow(t("set_haptics"), s.haptics, "toggle-haptics", last = true) { onChange(s.copy(haptics = it, vibrateEnabled = it)) }
                        }
                        Section(t("set_sec_widget")) {
                            ToggleRow(t("set_widget_list"), s.widgetList, "toggle-wlist") { onChange(s.copy(widgetList = it)) }
                            ToggleRow(t("set_widget_cond"), s.widgetCondition, "toggle-wcond") { onChange(s.copy(widgetCondition = it)) }
                            ToggleRow(t("set_widget_bubble"), s.widgetBubble, "toggle-wbub", last = true) { onChange(s.copy(widgetBubble = it)) }
                        }
                    }
                    "account" -> {
                        val auth = LocalAuth.current
                        val session = auth?.session?.collectAsState()?.value
                        Section(t("set_sec_account")) {
                            ValueRow(t("set_login_account"), session?.let { t("provider_" + it.provider) } ?: t("provider_guest"), "acct-provider")
                            if (!session?.displayName.isNullOrBlank()) ValueRow(t("account_name"), session?.displayName.orEmpty(), "acct-name")
                            if (!session?.email.isNullOrBlank()) ValueRow(t("account_email"), session?.email.orEmpty(), "acct-email")
                            ValueRow(t("guest_row_device"), session?.device?.substringBefore(" ·").orEmpty(), "row-device", last = true)
                        }
                        Section(" ") {
                            ButtonRow(t("settings_sign_out"), t("settings_sign_out"), Color(0xFFDC5A5A), "sign-out", last = true) { confirmSignOut = true }
                        }
                    }
                    "data" -> {
                        Section(t("set_sec_data")) {
                            ValueRow(t("set_backup_export"), t("set_backup_export_sub"), "row-export", onClick = onExport)
                            ValueRow(t("set_backup_restore"), t("set_backup_restore_sub"), "row-restore", onClick = onRestore)
                            ButtonRow(t("set_reset"), t("set_reset_btn"), Color(0xFFDC5A5A), "row-reset", last = true) { confirmReset = true }
                        }
                        val auth = LocalAuth.current
                        val session = auth?.session?.collectAsState()?.value
                        val scope = rememberCoroutineScope()
                        Section(t("set_sec_account")) {
                            ValueRow(t("set_login_account"), session?.let { t("provider_" + it.provider) }.orEmpty(), "row-account", last = true) { page = "account" }
                        }
                        Section(t("set_sec_info")) {
                            ValueRow(t("set_version"), version, "row-version")
                            ValueRow(t("set_licenses"), "", "row-licenses")
                            ValueRow(t("set_privacy"), "", "row-privacy", last = true)
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
        if (langSheet) LanguageSheet(s.language, onPick = { onChange(s.copy(language = it)); langSheet = false }, onDismiss = { langSheet = false })
        if (confirmSignOut) ConfirmSheet(t("account_signout_confirm"), t("settings_sign_out"), onYes = { confirmSignOut = false; signOutScope.launch { authForSignOut?.signOut() } }, onNo = { confirmSignOut = false })
        if (confirmReset) ConfirmSheet(t("set_reset_confirm"), t("set_reset_btn"), onYes = { confirmReset = false; onReset() }, onNo = { confirmReset = false })
    }
}

private fun clockMinute(m: Int, use24h: Boolean): String {
    val h = m / 60; val mm = (m % 60).toString().padStart(2, '0')
    if (use24h) return "${h.toString().padStart(2, '0')}:$mm"
    val h12 = if (h % 12 == 0) 12 else h % 12
    return app.mongle.l10n.L10n.fmt(if (h < 12) "clock_am" else "clock_pm", "$h12:$mm")
}

@Composable
private fun langLabel(code: String): String = when (code) {
    "ko" -> t("lang_ko"); "en" -> t("lang_en")
    else -> t("lang_system") + " (" + (if (app.mongle.l10n.L10n.code() == "ko") t("lang_ko") else t("lang_en")) + ")"
}

@Composable
private fun ProfileCard(state: GameState, onClick: () -> Unit = {}) {
    val session = LocalAuth.current?.session?.collectAsState()?.value
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 16.dp).shadow(5.dp, RoundedCornerShape(20.dp), spotColor = Color(0x22000000)).background(CardWhite, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick).testTag("row-profile").padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StillPet(state.speciesId, state.stage, app.mongle.model.Pose.HAPPY, Modifier.size(48.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("${state.petName} · Lv.${state.level}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ink)
            val acct = session?.let { listOf(it.email.ifBlank { null }, t("provider_" + it.provider)).filterNotNull().joinToString(" · ") } ?: t("provider_guest")
            Text(acct, fontSize = 11.sp, color = InkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("›", fontSize = 18.sp, color = InkSoft)
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PeachDeep, modifier = Modifier.padding(start = 10.dp, bottom = 6.dp))
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp).shadow(4.dp, RoundedCornerShape(18.dp), spotColor = Color(0x1A000000)).background(CardWhite, RoundedCornerShape(18.dp)), content = content)
}

@Composable
private fun Divider() = Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(1.dp).background(Line))

@Composable
private fun RowShell(label: String, tag: String, last: Boolean, enabled: Boolean = true, onClick: (() -> Unit)? = null, trailing: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier).padding(horizontal = 16.dp).testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 14.sp, color = if (enabled) Ink else InkSoft, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        trailing()
    }
    if (!last) Divider()
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, tag: String, enabled: Boolean = true, last: Boolean = false, onChange: (Boolean) -> Unit) {
    RowShell(label, tag, last, enabled, onClick = { if (enabled) onChange(!checked) }) { CuteToggle(checked, enabled) }
}

@Composable
fun CuteToggle(on: Boolean, enabled: Boolean = true) {
    val x by androidx.compose.animation.core.animateDpAsState(if (on) 21.dp else 3.dp, androidx.compose.animation.core.spring(dampingRatio = 0.6f, stiffness = 500f), label = "knob")
    Box(Modifier.size(width = 42.dp, height = 24.dp).clip(RoundedCornerShape(12.dp)).background((if (on) Peach else Color(0xFFE2DAD4)).copy(alpha = if (enabled) 1f else 0.5f))) {
        Box(Modifier.offset(x = x, y = 3.dp).size(18.dp).shadow(2.dp, CircleShape).background(Color.White, CircleShape))
    }
}

@Composable
private fun ValueRow(label: String, value: String, tag: String, last: Boolean = false, onClick: (() -> Unit)? = null) {
    RowShell(label, tag, last, onClick = onClick) {
        if (value.isNotBlank()) Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = InkSoft, maxLines = 1)
        if (onClick != null) Text("  ›", fontSize = 16.sp, color = InkSoft)
    }
}

@Composable
private fun ButtonRow(label: String, button: String, color: Color, tag: String, last: Boolean = false, onClick: () -> Unit) {
    RowShell(label, tag, last, onClick = onClick) { Text(button, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color) }
}

@Composable
private fun <T> SegRow(label: String, options: List<Pair<T, String>>, selected: T, last: Boolean = false, onPick: (T) -> Unit) {
    RowShell(label, "seg-$label", last) {
        Row(Modifier.clip(RoundedCornerShape(13.dp)).background(Line).padding(3.dp)) {
            options.forEach { (v, l) ->
                val sel = v == selected
                Text(
                    l, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (sel) Ink else InkSoft,
                    modifier = Modifier.then(if (sel) Modifier.shadow(2.dp, RoundedCornerShape(10.dp)).background(CardWhite, RoundedCornerShape(10.dp)) else Modifier)
                        .clip(RoundedCornerShape(10.dp)).clickable { onPick(v) }.padding(horizontal = 8.dp, vertical = 3.dp).testTag("seg-opt-$l"),
                )
            }
        }
    }
}

@Composable
private fun BoxScope.Sheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0x6E28201A)).clickable(onClick = onDismiss))
    Column(
        Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(CardWhite, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).clickable(enabled = false) {}.padding(horizontal = 22.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally, content = {
            Box(Modifier.size(width = 40.dp, height = 5.dp).background(Color(0xFFDCD6D0), RoundedCornerShape(3.dp)))
            Spacer(Modifier.height(18.dp))
            content()
            Spacer(Modifier.height(24.dp))
        },
    )
}

@Composable
private fun BoxScope.LanguageSheet(current: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    Sheet(onDismiss) {
        Text(t("lang_label"), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Ink)
        Text(t("lang_sheet_sub"), fontSize = 11.sp, color = InkSoft, modifier = Modifier.padding(top = 4.dp, bottom = 14.dp))
        listOf("system" to t("lang_system_follow"), "ko" to "한국어", "en" to "English").forEach { (code, label) ->
            val sel = code == current
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp).height(50.dp).clip(RoundedCornerShape(16.dp)).background(if (sel) PeachSoft else Color(0xFFFAF6F2)).clickable { onPick(code) }.padding(horizontal = 18.dp).testTag("lang-$code"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(label, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ink)
                    if (code == "system") Text(tf("lang_now", if (app.mongle.l10n.L10n.code() == "ko") "한국어" else "English"), fontSize = 11.sp, color = InkSoft)
                }
                Box(Modifier.size(20.dp).border(2.dp, Peach, CircleShape), contentAlignment = Alignment.Center) { if (sel) Box(Modifier.size(10.dp).background(Peach, CircleShape)) }
            }
        }
    }
}

@Composable
private fun BoxScope.ConfirmSheet(message: String, yes: String, onYes: () -> Unit, onNo: () -> Unit) {
    Sheet(onNo) {
        Text(message, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(24.dp)).background(Line).clickable(onClick = onNo), contentAlignment = Alignment.Center) { Text(t("cancel"), color = Ink, fontWeight = FontWeight.Bold) }
            Box(Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(24.dp)).background(Color(0xFFDC5A5A)).clickable(onClick = onYes).testTag("confirm-yes"), contentAlignment = Alignment.Center) { Text(yes, color = Color.White, fontWeight = FontWeight.Bold) }
        }
    }
}
