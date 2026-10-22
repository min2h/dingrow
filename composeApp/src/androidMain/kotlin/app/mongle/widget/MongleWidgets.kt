package app.mongle.widget

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.LocalContext
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.layout.fillMaxHeight
import androidx.glance.LocalSize
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import app.mongle.l10n.L10n
import app.mongle.logic.Emphasis
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.toInstant
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.glance.Button
import androidx.glance.ButtonDefaults
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.action.ActionParameters
import app.dingrow.R
import app.mongle.AppGraph
import app.mongle.l10n.AppLocale
import java.util.Locale
import app.mongle.appBrandName
import app.mongle.MainActivity
import app.mongle.logic.PetGesture
import app.mongle.model.GameState
import app.mongle.model.Pose
import app.mongle.model.TimeOfDay
import app.mongle.ui.ArtBitmaps
import app.mongle.ui.petAct
import app.mongle.ui.petAssetId
import app.mongle.ui.roomAssetFor

class SmallWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SmallPetWidget()
}

class MediumWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MediumPetWidget()
}

class LargeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LargePetWidget()
}

class SmallPetWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { SmallBody() }
    }
}

class MediumPetWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { MediumBody() }
    }
}

class LargePetWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { LargeBody() }
    }
}

private fun currentState(): GameState? = if (AppGraph.ready) AppGraph.service.state.value else null

private val ink = androidx.glance.color.ColorProvider(day = Color(0xFF5C4636), night = Color(0xFFF4E8DE))
private val inkSoft = androidx.glance.color.ColorProvider(day = Color(0xFF8A6A55), night = Color(0xFFC8B3A5))
private val accent = ColorProvider(Color(0xFFD9604F))
private val card = androidx.glance.color.ColorProvider(day = Color(0xFFFFFCF8), night = Color(0xFF2A2430))
private val rowBg = androidx.glance.color.ColorProvider(day = Color(0xFFFFF4EE), night = Color(0xFF352D3B))
private val rowNext = androidx.glance.color.ColorProvider(day = Color(0xFFFFE6DC), night = Color(0xFF4A3238))
private val peachDeep = ColorProvider(Color(0xFFF08A7A))
private val mintDone = ColorProvider(Color(0xFF4FA683))
private val cream = ColorProvider(Color(0xFFFFF6EE))
private val peach = ColorProvider(Color(0xFFFFE3D6))
private val mint = ColorProvider(Color(0xFFDDF3EA))
private val butter = ColorProvider(Color(0xFFFFF3C9))

/** Deep link that opens the app focused on one schedule. */
fun scheduleUri(id: String): android.net.Uri = android.net.Uri.parse("dingrow://schedule/" + android.net.Uri.encode(id))

private fun openSchedule(context: Context, id: String) = actionStartActivity(
    Intent(Intent.ACTION_VIEW, scheduleUri(id), context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
)

/** Today's occurrences for the widget list (done ones stay, ticked). */
internal fun widgetRows(state: GameState, now: Long = System.currentTimeMillis()): List<app.mongle.ui.DayCard> {
    val zone = kotlinx.datetime.TimeZone.currentSystemDefault()
    val today = kotlinx.datetime.Instant.fromEpochMilliseconds(now).toLocalDateTime(zone).date
    val start = kotlinx.datetime.LocalDateTime(today.year, today.monthNumber, today.dayOfMonth, 0, 0).toInstant(zone).toEpochMilliseconds()
    return app.mongle.ui.cardsForDay(state.schedules, start, start + 86_400_000L, now, zone.id)
}

@Composable
private fun SceneImage(state: GameState, wDp: Float, hDp: Float, modifier: GlanceModifier, levelPill: Boolean, petFraction: Float, textDp: Float = 11f) {
    val context = LocalContext.current
    val d = context.resources.displayMetrics.density.coerceAtMost(2.5f)
    val scene = WidgetScene.render(
        context, state, (wDp * d).toInt(), (hDp * d).toInt(), d,
        showBubble = state.settings.widgetBubble, levelPill = levelPill, petFraction = petFraction, bubbleTextDp = textDp,
    )
    Image(
        ImageProvider(scene.bitmap),
        Emphasis.strip(state.speech).ifBlank { state.petName },
        modifier.clickable(actionRunCallback<ReactAction>()),
        contentScale = ContentScale.FillBounds,
    )
}

@Composable
private fun StatusRow(state: GameState, showCount: Boolean) {
    if (!state.settings.widgetCondition) return
    val context = LocalContext.current
    val d = context.resources.displayMetrics.density.coerceAtMost(2.5f)
    Row(GlanceModifier.fillMaxWidth().padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Image(ImageProvider(WidgetScene.ring((14 * d).toInt(), state.hunger / 100f, d)), L10n.t("stat_full_l"), GlanceModifier.size(14.dp))
        Spacer(GlanceModifier.width(4.dp))
        Text("${state.hunger}%", style = TextStyle(color = ink, fontSize = 11.sp, fontWeight = FontWeight.Bold), modifier = GlanceModifier.padding(end = 8.dp))
        Text("●", style = TextStyle(color = ColorProvider(app.mongle.ui.conditionColors(state.condition, state.sick).second), fontSize = 9.sp))
        Spacer(GlanceModifier.width(3.dp))
        Text(L10n.t("cond_" + (if (state.sick) "sick" else state.condition.name.lowercase())), style = TextStyle(color = ink, fontSize = 11.sp, fontWeight = FontWeight.Bold), maxLines = 1, modifier = GlanceModifier.padding(end = 8.dp))
        if (showCount) {
            Text("♥ " + state.moodLabel, style = TextStyle(color = ink, fontSize = 11.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            Spacer(GlanceModifier.defaultWeight())
            Text(L10n.fmt("widget_today_count", widgetRows(state).size), style = TextStyle(color = inkSoft, fontSize = 10.sp), maxLines = 1)
        }
    }
}

@Composable
private fun ScheduleRowGlance(card: app.mongle.ui.DayCard, isNext: Boolean, use24h: Boolean) {
    val context = LocalContext.current
    val v = card.view
    Row(
        GlanceModifier.fillMaxWidth().height(30.dp).cornerRadius(10.dp).background(if (isNext) rowNext else rowBg)
            .padding(horizontal = 10.dp).clickable(openSchedule(context, v.id)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Split Texts = Glance "spans": time and title bold, time in the accent colour.
        Text(app.mongle.ui.clockOf(card.occurrence, use24h), style = TextStyle(color = if (card.done) inkSoft else accent, fontSize = 11.sp, fontWeight = FontWeight.Bold), maxLines = 1)
        Spacer(GlanceModifier.width(10.dp))
        Text(
            v.title + if (v.place.isNotBlank()) " · " + v.place else "",
            style = TextStyle(color = if (card.done) inkSoft else ink, fontSize = 12.sp, fontWeight = FontWeight.Bold),
            maxLines = 1, modifier = GlanceModifier.defaultWeight(),
        )
        if (card.done) Text("✓", style = TextStyle(color = mintDone, fontSize = 12.sp, fontWeight = FontWeight.Bold))
        else if (isNext) Text(
            L10n.t("widget_next_badge"), style = TextStyle(color = ColorProvider(Color.White), fontSize = 9.sp, fontWeight = FontWeight.Bold),
            modifier = GlanceModifier.background(peachDeep).cornerRadius(8.dp).padding(horizontal = 7.dp, vertical = 1.dp),
        )
    }
}

@Composable
private fun Fallback() {
    Box(GlanceModifier.fillMaxSize().background(card).cornerRadius(22.dp), contentAlignment = Alignment.Center) {
        Text(appBrandName(), style = TextStyle(color = ink, fontWeight = FontWeight.Bold))
    }
}

@Composable
internal fun SmallBody(state: GameState? = currentState()) {
    if (state == null) { Fallback(); return }
    val size = LocalSize.current
    Column(GlanceModifier.fillMaxSize().background(card).cornerRadius(22.dp).padding(6.dp)) {
        val sceneH = (size.height.value - 12f - (if (state.settings.widgetCondition) 22f else 0f)).coerceAtLeast(60f)
        SceneImage(state, size.width.value - 12f, sceneH, GlanceModifier.fillMaxWidth().defaultWeight().cornerRadius(16.dp), levelPill = false, petFraction = 0.46f, textDp = 9.5f)
        if (state.settings.widgetCondition) {
            Spacer(GlanceModifier.height(4.dp))
            StatusRow(state, showCount = false)
        }
    }
}

@Composable
internal fun MediumBody(state: GameState? = currentState()) {
    if (state == null) { Fallback(); return }
    val size = LocalSize.current
    val rows = widgetRows(state).filter { !it.done }.take(2)
    Row(GlanceModifier.fillMaxSize().background(card).cornerRadius(22.dp).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        val h = size.height.value - 16f
        SceneImage(state, h * 0.95f, h, GlanceModifier.width((h * 0.95f).dp).fillMaxHeight().cornerRadius(16.dp), levelPill = false, petFraction = 0.5f, textDp = 9f)
        Spacer(GlanceModifier.width(10.dp))
        Column(GlanceModifier.defaultWeight().fillMaxHeight()) {
            Text(L10n.t("widget_upcoming"), style = TextStyle(color = inkSoft, fontSize = 10.sp, fontWeight = FontWeight.Bold))
            Spacer(GlanceModifier.height(4.dp))
            if (rows.isEmpty()) Text(L10n.t("home_no_schedule"), style = TextStyle(color = ink, fontSize = 12.sp, fontWeight = FontWeight.Bold))
            rows.forEachIndexed { i, r ->
                ScheduleRowGlance(r, isNext = i == 0, use24h = state.settings.use24h)
                Spacer(GlanceModifier.height(4.dp))
            }
            Spacer(GlanceModifier.defaultWeight())
            StatusRow(state, showCount = false)
        }
    }
}

@Composable
internal fun LargeBody(state: GameState? = currentState()) {
    if (state == null) { Fallback(); return }
    val size = LocalSize.current
    val rows = widgetRows(state)
    val nextId = rows.firstOrNull { !it.done }?.view?.id
    Column(GlanceModifier.fillMaxSize().background(card).cornerRadius(24.dp).padding(8.dp)) {
        val sceneH = if (state.settings.widgetList) (size.height.value * 0.46f).coerceIn(110f, 190f) else size.height.value - 40f
        SceneImage(state, size.width.value - 16f, sceneH, GlanceModifier.fillMaxWidth().height(sceneH.dp).cornerRadius(18.dp), levelPill = true, petFraction = 0.36f)
        Spacer(GlanceModifier.height(6.dp))
        StatusRow(state, showCount = true)
        if (state.settings.widgetList) {
            Spacer(GlanceModifier.height(6.dp))
            if (rows.isEmpty()) {
                Text(L10n.t("home_no_schedule"), style = TextStyle(color = inkSoft, fontSize = 12.sp), modifier = GlanceModifier.padding(start = 4.dp).clickable(actionRunCallback<AddScheduleAction>()))
            } else if (rows.size <= 5) {
                // Plain column for short days (renders everywhere, incl. previews); lazy list for long days.
                rows.forEach { r ->
                    ScheduleRowGlance(r, isNext = r.view.id == nextId, use24h = state.settings.use24h)
                    Spacer(GlanceModifier.height(4.dp))
                }
            } else {
                androidx.glance.appwidget.lazy.LazyColumn(GlanceModifier.fillMaxWidth().defaultWeight()) {
                    items(rows.size, itemId = { rows[it].view.id.hashCode().toLong() }) { i ->
                        Column {
                            ScheduleRowGlance(rows[i], isNext = rows[i].view.id == nextId, use24h = state.settings.use24h)
                            Spacer(GlanceModifier.height(4.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PetImage(state: GameState, size: Int, awake: Boolean = false, react: Boolean = false) {
    val context = LocalContext.current
    val pose = if (awake && state.pose == Pose.SLEEP) Pose.IDLE else state.pose
    val energy = if (awake) 80 else state.energy
    val act = petAct(state.stage, pose, energy, if (awake) "" else state.statusMessage)
    val bitmap = ArtBitmaps.decode(context, petAssetId(state.speciesId, state.stage, act), size * 2)
    val modifier = if (react) {
        GlanceModifier.size(size.dp).clickable(actionRunCallback<ReactAction>())
    } else {
        GlanceModifier.size(size.dp)
    }
    Image(ImageProvider(bitmap), state.petName, modifier)
}

@Composable
private fun localizedContext(): Context {
    val base = LocalContext.current
    val locale = if (AppLocale.code() == "ko") Locale.KOREAN else Locale.ENGLISH
    val config = Configuration(base.resources.configuration)
    config.setLocale(locale)
    return base.createConfigurationContext(config)
}

@Composable
private fun s(id: Int): String = localizedContext().getString(id)

@Composable
private fun sf(id: Int, vararg args: Any): String {
    var index = 0
    return Regex("%[sd]").replace(localizedContext().getString(id)) {
        args.getOrNull(index++)?.toString().orEmpty()
    }.replace("%%", "%")
}

class ReactAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        AppGraph.ensure(context)
        AppGraph.service.react(PetGesture.TAP)
    }
}

class FeedAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        AppGraph.ensure(context)
        AppGraph.service.feed()
    }
}

class CompleteAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        AppGraph.ensure(context)
        AppGraph.service.completeNext()
    }
}

class SellAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        AppGraph.ensure(context)
        AppGraph.service.sellPendingOrLatest()
    }
}

class BeginBuyAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        AppGraph.ensure(context)
        AppGraph.service.beginBuyToday()
    }
}

class ConfirmBuyAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        AppGraph.ensure(context)
        AppGraph.service.confirmBuy()
    }
}

class CancelBuyAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        AppGraph.ensure(context)
        AppGraph.service.cancelBuy()
    }
}

class PresetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        AppGraph.ensure(context)
        AppGraph.service.cyclePreset()
    }
}

class OpenAppAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        context.startActivity(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

class AddScheduleAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra("route", "add-schedule")
        }
        context.startActivity(intent)
    }
}
