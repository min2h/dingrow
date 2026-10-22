package app.mongle.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mongle.logic.Condition
import app.mongle.logic.DayPhase
import app.mongle.logic.Weather
import app.mongle.logic.WeatherKind
import app.mongle.logic.WeatherSnapshot
import app.mongle.model.GameState
import app.mongle.model.ScheduleView
import app.mongle.model.roomTheme
import app.mongle.ui.motion.PetMotionEngine
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.painterResource

/** "15:00" (or "3:00 PM") for a schedule time. */
fun clockOf(millis: Long, use24h: Boolean = true): String {
    val t = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault())
    if (use24h) return "${t.hour.toString().padStart(2, '0')}:${t.minute.toString().padStart(2, '0')}"
    val h = if (t.hour % 12 == 0) 12 else t.hour % 12
    return "$h:${t.minute.toString().padStart(2, '0')} ${if (t.hour < 12) "AM" else "PM"}"
}

/** "**19:00** **강남역** **술약속**": time, place and title emphasized for every surface. */
fun scheduleHeadline(v: ScheduleView, use24h: Boolean = true): String =
    listOfNotNull(clockOf(v.timeMillis, use24h), v.place.takeIf { it.isNotBlank() }, v.title).joinToString(" ") { "**$it**" }

@Composable
fun WeatherChip(w: WeatherSnapshot, fahrenheit: Boolean) {
    Row(
        Modifier.height(30.dp).shadow(4.dp, RoundedCornerShape(15.dp), spotColor = Color(0x22000000)).background(CardWhite, RoundedCornerShape(15.dp)).padding(horizontal = 12.dp).testTag("weather-chip"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WeatherGlyph(w.kind, Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(Weather.label(w.kind) + " " + Weather.temp(w.tempC, fahrenheit), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ink)
    }
}

@Composable
fun WeatherGlyph(kind: WeatherKind?, modifier: Modifier, phase: DayPhase? = null) {
    Canvas(modifier) { drawWeatherGlyph(kind, phase) }
}

fun DrawScope.drawWeatherGlyph(kind: WeatherKind?, phase: DayPhase? = null) {
    val r = size.minDimension / 2; val c = center
    val sun = Color(0xFFFFBE3C)
    if (kind == null || kind == WeatherKind.CLEAR) {
        if (phase == DayPhase.NIGHT) {
            drawCircle(Color(0xFFFFD678), r * 0.7f, c)
            drawCircle(CardWhite, r * 0.62f, Offset(c.x + r * 0.32f, c.y - r * 0.25f))
            return
        }
        drawCircle(sun, r * 0.5f, c)
        repeat(8) { i ->
            val a = PI / 4 * i
            drawLine(sun, Offset(c.x + (kotlin.math.cos(a) * r * 0.72f).toFloat(), c.y + (sin(a) * r * 0.72f).toFloat()), Offset(c.x + (kotlin.math.cos(a) * r).toFloat(), c.y + (sin(a) * r).toFloat()), r * 0.16f)
        }
        return
    }
    val cc = if (kind == WeatherKind.STORM) Color(0xFF646A7C) else Color(0xFF96A0B4)
    drawCircle(cc, r * 0.42f, Offset(c.x - r * 0.35f, c.y + r * 0.05f))
    drawCircle(cc, r * 0.55f, Offset(c.x + r * 0.1f, c.y - r * 0.2f))
    drawCircle(cc, r * 0.38f, Offset(c.x + r * 0.5f, c.y + r * 0.08f))
    drawRect(cc, Offset(c.x - r * 0.7f, c.y), Size(r * 1.55f, r * 0.45f))
    when (kind) {
        WeatherKind.RAIN -> repeat(3) { i -> drawLine(Color(0xFF6EA0E6), Offset(c.x - r * 0.4f + i * r * 0.45f, c.y + r * 0.6f), Offset(c.x - r * 0.5f + i * r * 0.45f, c.y + r), r * 0.16f) }
        WeatherKind.SNOW -> repeat(3) { i -> drawCircle(Color(0xFF96B4DC), r * 0.12f, Offset(c.x - r * 0.45f + i * r * 0.45f, c.y + r * 0.8f)) }
        WeatherKind.STORM -> drawPath(Path().apply { moveTo(c.x, c.y + r * 0.3f); lineTo(c.x - r * 0.3f, c.y + r * 0.85f); lineTo(c.x, c.y + r * 0.8f); lineTo(c.x - r * 0.15f, c.y + r * 1.2f); lineTo(c.x + r * 0.3f, c.y + r * 0.65f); lineTo(c.x, c.y + r * 0.7f); close() }, Color(0xFFFFC83C))
        else -> Unit
    }
}

/** Tint over the room art for the time of day and weather (night uses the night art). */
internal fun gradeFor(phase: DayPhase, kind: WeatherKind?): Color = when (kind) {
    WeatherKind.STORM -> Color(0x6630364A)
    WeatherKind.RAIN -> Color(0x4D5A6A82)
    WeatherKind.SNOW -> Color(0x33C8D6EC)
    WeatherKind.CLOUDY -> Color(0x1F6A7080)
    else -> when (phase) {
        DayPhase.DAWN -> Color(0x40806CC8)
        DayPhase.EVENING -> Color(0x33FF8A50)
        else -> Color.Transparent
    }
}

/**
 * The home room: art + time/weather grade + animated rain/snow + 몽이 (free roam, condition FX)
 * + a speech bubble anchored over 몽이's head that follows it every frame without recomposing.
 */
@Composable
fun LiveRoom(
    state: GameState,
    modifier: Modifier = Modifier,
    onPet: (() -> Unit)? = null,
    onRoamRest: (() -> Unit)? = null,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val pinned = LocalPetMotion.current
    val animate = pinned == null
    val weather = state.weather
    val night = state.phase == DayPhase.NIGHT
    val theme = roomTheme(state.activeRoom.wallpaperId)
    val engine = remember { PetMotionEngine(state.speciesId.hashCode()) }
    val frame = remember { mutableLongStateOf(0L) }
    if (animate) LaunchedEffect(Unit) { while (true) withFrameNanos { frame.longValue = it } }
    val density = LocalDensity.current
    BoxWithConstraints(modifier.shadow(14.dp, RoundedCornerShape(26.dp), spotColor = Color(0x40000000)).clip(RoundedCornerShape(26.dp)).testTag("room")) {
        val w = maxWidth; val h = maxHeight
        val pet = w * 0.34f
        val bottomPad = h * 0.07f
        val wx = weather?.kind
        val toast = when (wx) {
            WeatherKind.RAIN -> tf("wx_toast_rain", ((weather.precipMm * 10).toInt() / 10.0).toString())
            WeatherKind.SNOW -> tf("wx_toast_snow", Weather.temp(weather.tempC - 4, state.settings.fahrenheit))
            WeatherKind.STORM -> tf("wx_toast_storm", weather.windKmh.toInt())
            else -> null
        }
        SideEffect {
            engine.roamDp = if (state.settings.wander && !state.settings.reduceMotion) ((w - pet) / 2 - 10.dp).value.coerceAtLeast(0f) else 0f
            engine.shiver = state.condition == Condition.COLD
            engine.onRoamRest = onRoamRest
        }
        Image(painterResource(Art.room(theme, night, square = false)), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        val grade = gradeFor(state.phase, wx)
        if (grade.alpha > 0f) Box(Modifier.fillMaxSize().background(grade))
        if (wx == WeatherKind.RAIN || wx == WeatherKind.STORM || wx == WeatherKind.SNOW) {
            Canvas(Modifier.fillMaxSize().testTag("weather-fx")) {
                val t = if (animate) frame.longValue / 1e9f else 0.6f
                drawPrecip(wx, t, density.density)
            }
        }
        // 몽이 (+ condition effects) in a box that follows the engine's free-roam offset.
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = bottomPad).size(pet)) {
            PetFigure(
                speciesId = state.speciesId, stage = state.stage, act0 = petAct(state.stage, state.pose, state.energy, state.statusMessage),
                outfitId = state.outfitId, modifier = Modifier.fillMaxSize(), animate = animate,
                breath = pinned?.first ?: 0.35f, walk = pinned?.second ?: 0.2f,
                body = if (animate) state.reactionBody else "", onClick = onPet, hoisted = engine,
            )
            Canvas(Modifier.fillMaxSize().graphicsLayer { frame.longValue; translationX = engine.translationXdp.dp.toPx(); translationY = engine.translationYdp.dp.toPx() }.testTag("cond-${state.condition.name.lowercase()}")) {
                drawCondition(state.condition, if (animate) frame.longValue / 1e9f else 0.3f, density.density)
            }
        }
        if (toast != null) {
            Text(
                toast, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp).background(Color(0xFF5C4636), RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
        // Anchored bubble.
        val speech = state.speech
        if (speech.isNotBlank()) {
            var bubbleSize by remember { mutableStateOf(IntSize.Zero) }
            val tailPx = remember { mutableFloatStateOf(0f) }
            val areaW = with(density) { w.toPx() }
            val areaH = with(density) { h.toPx() }
            val petPx = with(density) { pet.toPx() }
            val bottomPx = with(density) { bottomPad.toPx() }
            val minTop = with(density) { (if (toast != null) 48.dp else 10.dp).toPx() }
            val margin = with(density) { 10.dp.toPx() }
            fun placement(): BubblePlacement {
                val headX = areaW / 2 + with(density) { engine.translationXdp.dp.toPx() }
                val headY = areaH - bottomPx - petPx * 0.88f + with(density) { engine.translationYdp.dp.toPx() } * 0.5f
                return placeBubble(headX, headY, bubbleSize.width.toFloat(), bubbleSize.height.toFloat(), areaW, margin, minTop)
            }
            Box(
                Modifier
                    .onSizeChanged { bubbleSize = it }
                    .graphicsLayer {
                        frame.longValue
                        val p = placement()
                        translationX = p.left; translationY = p.top
                        tailPx.floatValue = p.tailX
                        alpha = if (bubbleSize == IntSize.Zero) 0f else 1f
                    }
                    .testTag("speech-bubble"),
            ) {
                SpeechBubble(speech, tailXPx = { frame.longValue; tailPx.floatValue }, maxWidth = minOf(w - 20.dp, 230.dp), fontSize = 13)
            }
        }
        overlay()
    }
}

internal fun DrawScope.drawPrecip(kind: WeatherKind, t: Float, dp: Float) {
    val rnd = Random(7)
    val n = when (kind) { WeatherKind.STORM -> 90; WeatherKind.RAIN -> 55; else -> 60 }
    repeat(n) {
        val x0 = rnd.nextFloat() * size.width; val y0 = rnd.nextFloat() * size.height
        val speed = (0.8f + rnd.nextFloat() * 0.6f)
        if (kind == WeatherKind.SNOW) {
            val y = (y0 + t * 28f * dp * speed) % size.height
            val x = x0 + sin(t * 1.3f + it) * 6f * dp
            drawCircle(Color.White.copy(alpha = 0.85f), (1.6f + rnd.nextFloat() * 2.2f) * dp, Offset(x, y))
        } else {
            val y = (y0 + t * 420f * dp * speed) % size.height
            val len = (if (kind == WeatherKind.STORM) 18f else 13f) * dp
            drawLine(Color.White.copy(alpha = 0.55f), Offset(x0, y), Offset(x0 - len * 0.18f, y + len), 1.4f * dp)
        }
    }
    if (kind == WeatherKind.STORM) {
        val cycle = t % 6f
        if (cycle < 0.25f) drawRect(Color(0xFFFFFFF0).copy(alpha = 0.35f * (1f - cycle / 0.25f)))
    }
}

/** Condition visuals around the pet box (size = pet). */
internal fun DrawScope.drawCondition(c: Condition, t: Float, dp: Float) {
    val s = size.width; val cx = s / 2; val cy = s * 0.55f
    when (c) {
        Condition.COLD -> {
            val blue = Color(0xFF78AAE6)
            for (k in listOf(-1, 1)) for (j in 0 until 3) {
                val x = cx + k * (s * 0.42f + j * 5 * dp); val y = cy - s * 0.08f + j * 8 * dp
                drawArc(blue, if (k < 0) 90f else 270f, 180f, false, Offset(x - 6 * dp, y - 10 * dp), Size(12 * dp, 20 * dp), style = Stroke(2.2f * dp))
            }
            val puff = (sin(t * 2.2f) * 0.5f + 0.5f)
            listOf(Triple(0.42f, -0.05f, 7f), Triple(0.55f, -0.14f, 9f), Triple(0.68f, -0.2f, 6f)).forEach { (dx, dy, r) ->
                drawCircle(Color.White.copy(alpha = 0.6f + 0.35f * puff), r * dp, Offset(cx + s * dx, cy + s * dy))
                drawCircle(Color(0xFFC8D7EB), r * dp, Offset(cx + s * dx, cy + s * dy), style = Stroke(dp))
            }
        }
        Condition.HOT -> {
            val bob = sin(t * 3f) * 2 * dp
            drop(Offset(cx + s * 0.36f, cy - s * 0.22f + bob), s * 0.05f)
            drop(Offset(cx - s * 0.38f, cy - s * 0.05f - bob), s * 0.04f)
            drop(Offset(cx + s * 0.2f, cy - s * 0.38f + bob), s * 0.035f)
            repeat(3) { i ->
                val x = cx - s * 0.25f + i * s * 0.25f; val y = cy - s * 0.62f
                val p = Path(); for (k in 0..18) { val px = x + sin(k / 3f + t * 4f) * 4 * dp; val py = y + k * dp; if (k == 0) p.moveTo(px, py) else p.lineTo(px, py) }
                drawPath(p, Color(0xFFFF966E), style = Stroke(2 * dp))
            }
        }
        Condition.SICK -> {
            rotate(-12f, Offset(cx + s * 0.08f, cy - s * 0.34f)) {
                drawRoundRect(Color(0xFFA8D6FA), Offset(cx + s * 0.08f - s * 0.25f, cy - s * 0.34f - s * 0.1f), Size(s * 0.5f, s * 0.2f), CornerRadius(8 * dp))
                drawRoundRect(Color(0xFF78B4E6), Offset(cx + s * 0.08f - s * 0.25f, cy - s * 0.34f - s * 0.1f), Size(s * 0.5f, s * 0.2f), CornerRadius(8 * dp), style = Stroke(1.5f * dp))
            }
            drawOval(Color(0x96FF8C8C), Offset(cx - s * 0.26f, cy + s * 0.02f), Size(s * 0.14f, s * 0.08f))
            drawOval(Color(0x96FF8C8C), Offset(cx + s * 0.12f, cy + s * 0.02f), Size(s * 0.14f, s * 0.08f))
            val tx = cx + s * 0.42f; val ty = cy - s * 0.42f
            drawRoundRect(Color.White, Offset(tx - 4 * dp, ty - 18 * dp), Size(8 * dp, 28 * dp), CornerRadius(4 * dp))
            drawRoundRect(Color(0xFFC87878), Offset(tx - 4 * dp, ty - 18 * dp), Size(8 * dp, 28 * dp), CornerRadius(4 * dp), style = Stroke(1.2f * dp))
            drawCircle(Color(0xFFEB5A5A), 7 * dp, Offset(tx, ty + 13 * dp))
            drawRect(Color(0xFFEB5A5A), Offset(tx - 1.5f * dp, ty - 6 * dp), Size(3 * dp, 14 * dp))
        }
        Condition.HUNGRY -> {
            val bx = cx + s * 0.42f; val by = cy - s * 0.62f + sin(t * 2f) * 2 * dp
            drawCircle(Color.White, 4 * dp, Offset(bx - 18 * dp, by + 30 * dp)); drawCircle(Color.White, 6 * dp, Offset(bx - 10 * dp, by + 18 * dp))
            drawRoundRect(Color.White, Offset(bx - 6 * dp, by - 28 * dp), Size(56 * dp, 42 * dp), CornerRadius(20 * dp))
            val ck = Offset(bx + 22 * dp, by - 7 * dp)
            drawCircle(Color(0xFFE0A660), 13 * dp, ck)
            listOf(-5f to -4f, 4f to -2f, -2f to 5f, 6f to 6f).forEach { (dx, dy) -> drawCircle(Color(0xFF6B4226), 2.2f * dp, Offset(ck.x + dx * dp, ck.y + dy * dp)) }
        }
        Condition.HAPPY -> {
            val bob = sin(t * 2.5f) * 3 * dp
            heart(Offset(cx - s * 0.45f, cy - s * 0.35f + bob), 8 * dp)
            heart(Offset(cx + s * 0.48f, cy - s * 0.45f - bob), 10 * dp)
            sparkle(Offset(cx + s * 0.3f, cy - s * 0.6f + bob), 7 * dp)
        }
        Condition.GOOD -> Unit
    }
}

private fun DrawScope.drop(c: Offset, r: Float) {
    val col = Color(0xFF8CC8FF)
    drawCircle(col, r, c)
    drawPath(Path().apply { moveTo(c.x - r * 0.9f, c.y - r * 0.3f); lineTo(c.x + r * 0.9f, c.y - r * 0.3f); lineTo(c.x, c.y - r * 2.2f); close() }, col)
    drawCircle(Color.White.copy(alpha = 0.8f), r * 0.22f, Offset(c.x - r * 0.25f, c.y - r * 0.25f))
}

private fun DrawScope.heart(c: Offset, r: Float, col: Color = Color(0xFFF07C8A)) {
    drawCircle(col, r * 0.5f, Offset(c.x - r * 0.5f, c.y - r * 0.1f)); drawCircle(col, r * 0.5f, Offset(c.x + r * 0.5f, c.y - r * 0.1f))
    drawPath(Path().apply { moveTo(c.x - r * 0.95f, c.y); lineTo(c.x + r * 0.95f, c.y); lineTo(c.x, c.y + r * 1.05f); close() }, col)
}

private fun DrawScope.sparkle(c: Offset, r: Float) {
    val p = Path()
    for (i in 0 until 8) { val a = PI / 4 * i; val rr = if (i % 2 == 0) r else r * 0.3f; val x = c.x + (kotlin.math.cos(a) * rr).toFloat(); val y = c.y + (sin(a) * rr).toFloat(); if (i == 0) p.moveTo(x, y) else p.lineTo(x, y) }
    p.close(); drawPath(p, Color(0xFFFFD45C))
}

@Composable
fun StatBarChip(label: String, value: String, bg: Color, bar: Color, frac: Float, modifier: Modifier = Modifier) {
    Column(modifier.height(46.dp).background(bg, RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = InkSoft, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
            Spacer(Modifier.weight(1f))
            Text(value, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Ink, maxLines = 1)
        }
        Spacer(Modifier.weight(1f))
        Box(Modifier.fillMaxWidth().height(5.dp).background(Color.White.copy(alpha = 0.8f), RoundedCornerShape(3.dp))) {
            Box(Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).fillMaxHeight().background(bar, RoundedCornerShape(3.dp)))
        }
    }
}

fun conditionColors(c: Condition, sick: Boolean): Pair<Color, Color> = when {
    sick || c == Condition.SICK -> Color(0xFFFFE4E4) to Color(0xFFE07070)
    c == Condition.COLD -> Color(0xFFE8F0FF) to Color(0xFF78A0E6)
    c == Condition.HOT -> Color(0xFFFFEEDC) to Color(0xFFE6A050)
    c == Condition.HUNGRY -> Color(0xFFFFF3D6) to Color(0xFFDCAA5A)
    else -> Color(0xFFE8F0FF) to Color(0xFF6EBE96)
}

fun conditionFrac(state: GameState): Float = when {
    state.sick -> state.recovery / 100f
    state.condition == Condition.COLD || state.condition == Condition.HOT -> 0.5f
    state.condition == Condition.HUNGRY -> 0.4f
    state.condition == Condition.HAPPY -> 1f
    else -> 0.85f
}

@Composable
fun HomeV4(
    state: GameState,
    onPet: () -> Unit,
    onRoamRest: () -> Unit,
    onComplete: (String) -> Unit,
    onAdd: () -> Unit,
    actions: @Composable BoxScope.() -> Unit,
    extra: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val roomH = (maxWidth - 44.dp) * 1.2f
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp).testTag("home-scroll")) {
            Spacer(Modifier.height(6.dp))
            LiveRoom(state, Modifier.fillMaxWidth().height(roomH.coerceAtMost(this@BoxWithConstraints.maxHeight * 0.62f)), onPet = onPet, onRoamRest = onRoamRest, overlay = actions)
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(state.petName, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
                Spacer(Modifier.width(8.dp))
                Text("Lv.${state.level} ${state.stageLabel}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PeachDeep, modifier = Modifier.padding(bottom = 3.dp))
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatBarChip(t("stat_full_l"), "${state.hunger}%", MintSoft, Color(0xFF6EBE96), state.hunger / 100f, Modifier.weight(1.06f).testTag("stat-hunger"))
                StatBarChip(t("stat_happy_l"), "${state.happiness}%", PeachSoft, Peach, state.happiness / 100f, Modifier.weight(0.98f))
                val (bg, bar) = conditionColors(state.condition, state.sick)
                val condLabel = t("cond_" + (if (state.sick) "sick" else state.condition.name.lowercase()))
                StatBarChip(t("stat_cond_l"), condLabel, bg, bar, conditionFrac(state), Modifier.weight(1f).testTag("stat-condition"))
            }
            Spacer(Modifier.height(12.dp))
            NextCard(state, onComplete, onAdd)
            extra()
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun NextCard(state: GameState, onComplete: (String) -> Unit, onAdd: () -> Unit) {
    val next = state.nextSchedule
    Row(
        Modifier.fillMaxWidth().height(66.dp).shadow(5.dp, RoundedCornerShape(20.dp), spotColor = Color(0x22000000)).background(CardWhite, RoundedCornerShape(20.dp)).padding(horizontal = 18.dp).testTag("next-card"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(t("next_task"), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = InkSoft)
            if (next != null) {
                Text(emphasized(scheduleHeadline(next, state.settings.use24h)), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("next-headline"))
            } else {
                Text("—  " + t("home_no_schedule"), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ink, maxLines = 1)
            }
        }
        val label = if (next != null) t("done_short") else t("add_short")
        Box(
            Modifier.height(26.dp).widthIn(min = 62.dp).clip(RoundedCornerShape(13.dp)).background(Peach)
                .clickable { if (next != null) onComplete(next.id) else onAdd() }.padding(horizontal = 12.dp)
                .testTag(if (next != null) "next-complete" else "next-add"),
            contentAlignment = Alignment.Center,
        ) { Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
    }
}

@Composable
fun RoomActionButton(mark: Mark, label: String, tag: String, onClick: () -> Unit) {
    Box(
        Modifier.size(38.dp).shadow(4.dp, CircleShape).background(Color.White.copy(alpha = 0.92f), CircleShape).clip(CircleShape).clickable(onClick = onClick).testTag(tag),
        contentAlignment = Alignment.Center,
    ) { MarkIcon(mark, Modifier.size(18.dp), PeachDeep) }
}

/** Shown in the shop only while 몽이 is sick (sheet 3): 50 coins, recovery +80%. */
@Composable
fun MedicineCard(coins: Int, onBuy: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().shadow(5.dp, RoundedCornerShape(20.dp), spotColor = Color(0x33E07070)).background(Color(0xFFFFF5F5), RoundedCornerShape(20.dp)).padding(14.dp).testTag("medicine-card"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(48.dp)) {
            val w = size.width
            drawRoundRect(Color(0xFFF7B5C0), Offset(w * 0.2f, w * 0.28f), Size(w * 0.6f, w * 0.66f), CornerRadius(w * 0.14f))
            drawRoundRect(Color(0xFF7CC6A8), Offset(w * 0.26f, w * 0.1f), Size(w * 0.48f, w * 0.2f), CornerRadius(w * 0.06f))
            drawRoundRect(Color.White, Offset(w * 0.3f, w * 0.44f), Size(w * 0.4f, w * 0.34f), CornerRadius(w * 0.06f))
            drawRect(Color(0xFFE07070), Offset(w * 0.46f, w * 0.48f), Size(w * 0.08f, w * 0.26f)); drawRect(Color(0xFFE07070), Offset(w * 0.37f, w * 0.57f), Size(w * 0.26f, w * 0.08f))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t("medicine_badge"), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE07070), modifier = Modifier.background(Color(0xFFFFE0E4), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 2.dp))
            Text(t("medicine_name"), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.padding(top = 3.dp))
            Text(t("medicine_desc"), fontSize = 11.sp, color = InkSoft)
        }
        val can = coins >= app.mongle.logic.ConditionRules.MEDICINE_PRICE
        Box(Modifier.height(32.dp).clip(RoundedCornerShape(16.dp)).background(if (can) Color(0xFFF08A7A) else Color(0xFFE6D6D0)).clickable(enabled = can, onClick = onBuy).padding(horizontal = 12.dp).testTag("buy-medicine"), contentAlignment = Alignment.Center) {
            Text(tf("medicine_buy", app.mongle.logic.ConditionRules.MEDICINE_PRICE), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}
