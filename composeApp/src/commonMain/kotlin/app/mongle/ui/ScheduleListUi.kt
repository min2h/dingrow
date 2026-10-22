package app.mongle.ui

import androidx.compose.animation.core.*
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mongle.model.GameState
import app.mongle.model.ScheduleView
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.datetime.*
import kotlinx.coroutines.launch

/**
 * To-do style list of the user's own schedules for one day: time · title · place, a satisfying
 * check animation (spring pop + confetti), focus/highlight when opened from the widget.
 */
@Composable
fun ScheduleDayList(
    state: GameState,
    cards: List<DayCard>,
    selected: LocalDate,
    highlightId: String?,
    onSelectDay: (LocalDate) -> Unit,
    onAdd: () -> Unit,
    onComplete: (String) -> Unit,
    onSnooze: (String) -> Unit,
    onSkip: (String) -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
    banner: @Composable () -> Unit = {},
) {
    val listState = rememberLazyListState()
    val focusIndex = cards.indexOfFirst { it.view.id.substringBefore('@') == highlightId?.substringBefore('@') }
    LaunchedEffect(highlightId, focusIndex) { if (focusIndex >= 0) listState.animateScrollToItem(focusIndex) }
    var expanded by remember { mutableStateOf<String?>(null) }
    val nextId = cards.firstOrNull { !it.done && it.occurrence >= kotlinx.datetime.Clock.System.now().toEpochMilliseconds() - 60_000 }?.view?.id
    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(longDate(selected), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = InkSoft, modifier = Modifier.weight(1f))
            Box(Modifier.height(28.dp).clip(RoundedCornerShape(14.dp)).background(Peach).clickable(onClick = onAdd).padding(horizontal = 14.dp).testTag("add-schedule"), contentAlignment = Alignment.Center) {
                Text(t("add_short"), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(8.dp))
        MiniWeek(selected, onSelectDay)
        banner()
        Spacer(Modifier.height(10.dp))
        if (cards.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(top = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                SpeechBubble(t("line_today_none_0"), maxWidth = 240.dp)
                StillPet(state.speciesId, state.stage, app.mongle.model.Pose.IDLE, Modifier.size(96.dp))
                TextButton(onClick = onAdd) { Text(t("add_first"), color = PeachDeep, fontWeight = FontWeight.Bold) }
            }
        }
        LazyColumn(state = listState, modifier = Modifier.weight(1f).testTag("schedule-list"), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            itemsIndexed(cards, key = { _, c -> c.view.id }) { _, card ->
                val id = card.view.id
                ScheduleRow(
                    view = card.view, occurrence = card.occurrence, done = card.done, use24h = state.settings.use24h,
                    isNext = id == nextId,
                    highlighted = highlightId != null && id.substringBefore('@') == highlightId.substringBefore('@'),
                    expanded = expanded == id,
                    onToggle = { expanded = if (expanded == id) null else id },
                    onComplete = { onComplete(id) },
                    onSnooze = { onSnooze(id) }, onSkip = { onSkip(id) }, onEdit = { onEdit(id) }, onDelete = { onDelete(id) },
                )
            }
        }
    }
}

private fun longDate(d: LocalDate): String {
    val wd = app.mongle.l10n.L10n.t("wd_long_" + ((d.dayOfWeek.ordinal + 1) % 7))
    return app.mongle.l10n.L10n.fmt("date_long", d.monthNumber, d.dayOfMonth, wd)
}

@Composable
private fun MiniWeek(selected: LocalDate, onSelect: (LocalDate) -> Unit) {
    val today = kotlinx.datetime.Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    val sunday = today.minus((today.dayOfWeek.ordinal + 1) % 7, DateTimeUnit.DAY)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        (0..6).forEach { i ->
            val d = sunday.plus(i, DateTimeUnit.DAY)
            val sel = d == selected
            Column(Modifier.width(38.dp).clip(RoundedCornerShape(12.dp)).background(if (sel) PeachSoft else Color.Transparent).clickable { onSelect(d) }.padding(vertical = 4.dp).testTag("day-$i"), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(t("wd_$i"), fontSize = 10.sp, color = InkSoft)
                Text("${d.dayOfMonth}", fontSize = 13.sp, fontWeight = if (sel || d == today) FontWeight.Bold else FontWeight.Medium, color = if (sel) PeachDeep else Ink)
            }
        }
    }
}

@Composable
fun ScheduleRow(
    view: ScheduleView,
    occurrence: Long,
    done: Boolean,
    use24h: Boolean,
    isNext: Boolean,
    highlighted: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    onComplete: () -> Unit,
    onSnooze: () -> Unit,
    onSkip: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    // Highlight pulse when focused from the widget deep link.
    val pulse = remember { Animatable(0f) }
    LaunchedEffect(highlighted) {
        if (highlighted) repeat(3) { pulse.animateTo(1f, tween(320)); pulse.animateTo(0.35f, tween(380)) }
    }
    // Check pop: spring overshoot + confetti burst on the false -> true transition.
    var wasDone by remember { mutableStateOf(done) }
    val pop = remember { Animatable(if (done) 1f else 0f) }
    val burst = remember { Animatable(1f) }
    LaunchedEffect(done) {
        if (done && !wasDone) {
            burst.snapTo(0f)
            kotlinx.coroutines.coroutineScope {
                launch { pop.animateTo(1f, spring(dampingRatio = 0.38f, stiffness = 420f)) }
                launch { burst.animateTo(1f, tween(650, easing = LinearOutSlowInEasing)) }
            }
        } else if (!done) pop.snapTo(0f)
        wasDone = done
    }
    val shape = RoundedCornerShape(18.dp)
    val hl = highlighted || pulse.value > 0f && pulse.isRunning
    Column(
        Modifier.fillMaxWidth()
            .shadow(if (hl) 8.dp else 4.dp, shape, spotColor = if (hl) Peach else Color(0x22000000))
            .background(if (hl) Color(0xFFFFF0EA) else CardWhite, shape)
            .then(if (hl) Modifier.border((1.5f + pulse.value).dp, Peach, shape) else Modifier)
            .clip(shape).clickable(onClick = onToggle).animateContentSize()
            .testTag(if (highlighted) "row-highlight" else "row-${view.id}"),
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(clockOf(occurrence, use24h), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (isNext || hl) Accent else InkSoft, modifier = Modifier.width(if (use24h) 52.dp else 70.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CategoryIcon(view.category, Modifier.size(14.dp), categoryColor(view.category))
                    Spacer(Modifier.width(5.dp))
                    Text(
                        view.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (done) InkSoft else Ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        textDecoration = if (done) TextDecoration.LineThrough else null,
                    )
                }
                val sub = listOfNotNull(view.place.takeIf { it.isNotBlank() }, view.repeatLabel.takeIf { it.isNotBlank() }, view.memo.takeIf { it.isNotBlank() }).joinToString(" · ")
                if (sub.isNotBlank()) Text(sub, fontSize = 11.sp, color = InkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(width = 62.dp, height = 40.dp)) {
                if (done || pop.value > 0f) {
                    Canvas(Modifier.size(64.dp)) {
                        val b = burst.value
                        if (b in 0.01f..0.99f) repeat(10) { i ->
                            val a = 2 * PI * i / 10 + 0.3
                            val r = size.width * (0.18f + 0.34f * b)
                            val col = listOf(Peach, Mint, Butter, Color(0xFF9CC8F0))[i % 4]
                            drawCircle(col.copy(alpha = 1f - b), 3.dp.toPx() * (1f - b * 0.5f), Offset(center.x + (cos(a) * r).toFloat(), center.y + (sin(a) * r).toFloat()))
                        }
                    }
                    Box(
                        Modifier.size(30.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value }.background(MintSoft, CircleShape).testTag("done-${view.id}"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Canvas(Modifier.size(14.dp)) {
                            val p = Path().apply { moveTo(size.width * 0.1f, size.height * 0.55f); lineTo(size.width * 0.4f, size.height * 0.82f); lineTo(size.width * 0.92f, size.height * 0.2f) }
                            drawPath(p, Color(0xFF4FA683), style = Stroke(2.4.dp.toPx(), cap = StrokeCap.Round))
                        }
                    }
                } else {
                    Box(
                        Modifier.height(26.dp).widthIn(min = 54.dp).clip(RoundedCornerShape(13.dp)).background(if (hl) Peach else PeachSoft).clickable(onClick = onComplete).padding(horizontal = 12.dp).testTag("complete-${view.id}"),
                        contentAlignment = Alignment.Center,
                    ) { Text(t("done_short"), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (hl) Color.White else PeachDeep) }
                }
            }
        }
        if (expanded && !done) {
            Row(Modifier.padding(start = 8.dp, bottom = 4.dp)) {
                TextButton(onClick = onSnooze) { Text(t("snooze_5"), fontSize = 12.sp) }
                TextButton(onClick = onSkip) { Text(t("skip"), fontSize = 12.sp) }
                TextButton(onClick = onEdit) { Text(t("edit"), fontSize = 12.sp) }
                TextButton(onClick = onDelete) { Text(t("delete"), fontSize = 12.sp, color = PeachDeep) }
            }
        }
    }
}
