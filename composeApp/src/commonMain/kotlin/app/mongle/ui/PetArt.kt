package app.mongle.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.drawscope.scale
import app.mongle.model.GameState
import app.mongle.model.ItemMotif
import app.mongle.model.MochiEar
import app.mongle.model.Pose
import app.mongle.model.SpeciesCatalog
import app.mongle.model.SpeciesLook
import app.mongle.model.Stage
import app.mongle.model.TimeOfDay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 테스트에서만 쓴다. 값을 주면 무한 애니메이션을 켜지 않고 그 위상으로 그린다.
 * Compose UI 테스트의 유휴 대기가 무한 전환 때문에 끝나지 않는 것을 막는다.
 */
val LocalPetMotion = staticCompositionLocalOf<Pair<Float, Float>?> { null }

private val EyeInk = Color(0xFF4A3728)
private val Blush = Color(0xFFF4A6A4)

private fun argb(value: Long): Color = Color(value.toInt())

@Composable
private fun animatedPhase(pose: Pose, label: String): Float {
    val transition = rememberInfiniteTransition(label = label)
    val bounce by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (pose == Pose.SLEEP) 1700 else 760, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = label,
    )
    return bounce
}

@Composable
private fun animatedWalk(): Float {
    val transition = rememberInfiniteTransition(label = "room-walk")
    val walk by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(2400, easing = LinearEasing)),
        label = "room-walk",
    )
    return walk
}

private fun Color.shade(): Color = Color(
    red = red * 0.86f,
    green = green * 0.80f,
    blue = blue * 0.76f,
    alpha = alpha,
)

@Composable
fun AnimatedPet(
    stage: Stage,
    pose: Pose,
    outfitId: String?,
    modifier: Modifier = Modifier,
    speciesId: String = "sp_peach",
    still: Boolean = false,
) {
    val pinned = LocalPetMotion.current
    if (pinned != null || still) {
        PetCanvas(stage, pose, outfitId, speciesId, modifier, bounce = pinned?.first ?: 0.2f, walk = pinned?.second ?: 0.5f)
        return
    }
    val transition = rememberInfiniteTransition(label = "pet")
    val bounce by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (pose == Pose.SLEEP) 1700 else 760, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "bounce",
    )
    val walk by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(2400, easing = LinearEasing)),
        label = "walk",
    )
    PetCanvas(stage, pose, outfitId, speciesId, modifier, bounce, walk)
}

@Composable
private fun PetCanvas(
    stage: Stage,
    pose: Pose,
    outfitId: String?,
    speciesId: String,
    modifier: Modifier,
    bounce: Float,
    walk: Float,
) {
    val look = SpeciesCatalog.byId(speciesId).look
    Canvas(modifier) {
        drawMochi(look, speciesId, stage, pose, outfitId, bounce, walk)
    }
}

@Composable
fun RoomScene(state: GameState, modifier: Modifier = Modifier) {
    val pinned = LocalPetMotion.current
    if (pinned != null) {
        RoomCanvas(state, modifier, pinned.first, pinned.second)
    } else {
        RoomCanvas(state, modifier, animatedPhase(state.pose, "room-bounce"), animatedWalk())
    }
}

@Composable
private fun RoomCanvas(state: GameState, modifier: Modifier, bounce: Float, walk: Float) {
    val look = SpeciesCatalog.byId(state.speciesId).look
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val wall = hexColor(state.wallHex)
        val floor = hexColor(state.floorHex)
        val night = state.darkRoom || state.timeOfDay == TimeOfDay.NIGHT
        drawRoundRect(wall, cornerRadius = CornerRadius(w * 0.06f, w * 0.06f))
        val floorPath = Path().apply {
            moveTo(w * 0.08f, h * 0.58f)
            lineTo(w * 0.92f, h * 0.58f)
            lineTo(w * 0.98f, h * 0.96f)
            lineTo(w * 0.02f, h * 0.96f)
            close()
        }
        drawPath(floorPath, floor)
        drawRoundRect(
            color = if (night) Color(0xFF3C4E78) else Color(0xFFD5EEF8),
            topLeft = Offset(w * 0.64f, h * 0.14f),
            size = Size(w * 0.22f, h * 0.28f),
            cornerRadius = CornerRadius(14f, 14f),
        )
        if (night) {
            drawCircle(Color(0xFFFFF3C4), w * 0.025f, Offset(w * 0.78f, h * 0.24f))
        } else {
            drawCircle(Color(0xFFFFE7A0), w * 0.03f, Offset(w * 0.70f, h * 0.22f))
        }
        drawOval(Color(0xFFB7DCCB), Offset(w * 0.30f, h * 0.70f), Size(w * 0.40f, h * 0.14f))
        if (state.activeRoom.bedId != null) {
            drawRoundRect(Color(0xFFFFFBF6), Offset(w * 0.10f, h * 0.46f), Size(w * 0.30f, h * 0.18f), CornerRadius(18f, 18f))
            drawRoundRect(Color(0xFFF7C7BC), Offset(w * 0.14f, h * 0.50f), Size(w * 0.16f, h * 0.07f), CornerRadius(10f, 10f))
        }
        if (state.activeRoom.lampId != null) {
            drawRoundRect(Color(0xFFE4C09A), Offset(w * 0.80f, h * 0.52f), Size(w * 0.025f, h * 0.16f), CornerRadius(4f, 4f))
            drawCircle(Color(0xFFFFE7A8), w * 0.045f, Offset(w * 0.812f, h * 0.50f))
        }
        if ("plant_pot" in state.owned) {
            drawRoundRect(Color(0xFFE7B98A), Offset(w * 0.08f, h * 0.34f), Size(w * 0.08f, h * 0.08f), CornerRadius(4f, 4f))
            drawOval(Color(0xFF8FCB8A), Offset(w * 0.06f, h * 0.26f), Size(w * 0.12f, h * 0.10f))
        }
        inset(w * 0.30f, h * 0.36f, w * 0.28f, h * 0.18f) {
            drawMochi(look, state.speciesId, state.stage, state.pose, state.outfitId, bounce, walk)
        }
    }
}

@Composable
fun ItemGlyph(motif: ItemMotif, modifier: Modifier = Modifier) {
    Canvas(modifier) { drawMotif(motif) }
}

private fun DrawScope.drawMochi(
    look: SpeciesLook,
    speciesId: String,
    stage: Stage,
    pose: Pose,
    outfitId: String?,
    bounce: Float,
    walk: Float,
) {
    if (stage == Stage.EGG || pose == Pose.EGG) {
        drawEggShell(bounce)
        return
    }
    val bodyScale = when (stage) {
        Stage.BABY -> 0.72f
        Stage.CHILD -> 0.86f
        else -> 1f
    }
    val bob = sin(bounce * PI).toFloat() * size.minDimension * 0.035f
    val slide = (walk - 0.5f) * size.width * 0.12f
    val closed = pose == Pose.SLEEP || bounce > 0.97f
    inset(size.width * 0.08f, size.height * 0.1f, size.width * 0.08f, size.height * 0.08f) {
        translatePet(slide, -bob) {
            scale(bodyScale, pivot = center) {
                val body = argb(look.body)
                val ear = argb(look.earColor)
                val accent = argb(look.accent)
                drawOval(Color(0x22000000), Offset(size.width * 0.22f, size.height * 0.78f), Size(size.width * 0.56f, size.height * 0.12f))
                drawEars(look.ear, ear, accent)
                drawBodyShape(look.ear, speciesId, body, accent)
                drawFace(closed, pose, accent, look.ear)
                drawSpeciesMarks(speciesId, accent, body)
                drawOutfit(outfitId, accent)
                if (pose == Pose.SLEEP) drawZzz()
            }
        }
    }
}

private fun DrawScope.translatePet(dx: Float, dy: Float, block: DrawScope.() -> Unit) {
    drawContext.canvas.save()
    drawContext.canvas.translate(dx, dy)
    try {
        block()
    } finally {
        drawContext.canvas.restore()
    }
}

private fun DrawScope.drawEggShell(bounce: Float) {
    val bob = sin(bounce * PI).toFloat() * size.minDimension * 0.02f
    val cx = center.x
    val cy = center.y + bob
    val rx = size.minDimension * 0.30f
    val ry = size.minDimension * 0.36f
    drawOval(Color(0x22000000), Offset(cx - rx, cy + ry * 0.7f), Size(rx * 2f, ry * 0.28f))
    drawOval(Color(0xFFFFF6EE), Offset(cx - rx, cy - ry), Size(rx * 2, ry * 2))
    drawOval(Color(0xFFF6E2D2), Offset(cx - rx, cy - ry), Size(rx * 2, ry * 2), style = Stroke(width = 3f))
    val crack = Path().apply {
        moveTo(cx - rx * 0.15f, cy - ry * 0.15f)
        lineTo(cx + rx * 0.05f, cy - ry * 0.45f)
        lineTo(cx + rx * 0.22f, cy - ry * 0.2f)
    }
    drawPath(crack, Color(0xFFE7C8B0), style = Stroke(width = 3f))
    drawCircle(Blush.copy(alpha = 0.8f), rx * 0.16f, Offset(cx - rx * 0.38f, cy + ry * 0.12f))
    drawCircle(Blush.copy(alpha = 0.8f), rx * 0.16f, Offset(cx + rx * 0.38f, cy + ry * 0.12f))
    drawCircle(EyeInk, rx * 0.07f, Offset(cx - rx * 0.22f, cy))
    drawCircle(EyeInk, rx * 0.07f, Offset(cx + rx * 0.22f, cy))
}

private fun DrawScope.drawEars(ear: MochiEar, color: Color, accent: Color) {
    val w = size.width
    val h = size.height
    when (ear) {
        MochiEar.CAT, MochiEar.FOX -> {
            val pathL = Path().apply {
                moveTo(w * 0.28f, h * 0.38f)
                lineTo(w * 0.30f, h * 0.12f)
                lineTo(w * 0.44f, h * 0.34f)
                close()
            }
            val pathR = Path().apply {
                moveTo(w * 0.72f, h * 0.38f)
                lineTo(w * 0.70f, h * 0.12f)
                lineTo(w * 0.56f, h * 0.34f)
                close()
            }
            drawPath(pathL, color)
            drawPath(pathR, color)
            if (ear == MochiEar.FOX) {
                drawCircle(accent, w * 0.035f, Offset(w * 0.34f, h * 0.26f))
                drawCircle(accent, w * 0.035f, Offset(w * 0.66f, h * 0.26f))
            }
        }
        MochiEar.BUNNY -> {
            drawRoundRect(color, Offset(w * 0.28f, h * 0.02f), Size(w * 0.12f, h * 0.36f), CornerRadius(40f, 40f))
            drawRoundRect(color, Offset(w * 0.60f, h * 0.02f), Size(w * 0.12f, h * 0.36f), CornerRadius(40f, 40f))
            drawRoundRect(Color(0xFFFFC9D2), Offset(w * 0.31f, h * 0.08f), Size(w * 0.06f, h * 0.22f), CornerRadius(20f, 20f))
            drawRoundRect(Color(0xFFFFC9D2), Offset(w * 0.63f, h * 0.08f), Size(w * 0.06f, h * 0.22f), CornerRadius(20f, 20f))
        }
        MochiEar.BEAR, MochiEar.ROUND, MochiEar.HAMSTER, MochiEar.PANDA, MochiEar.SEAL -> {
            val earColor = if (ear == MochiEar.PANDA) Color(0xFF2C2C2C) else color
            drawCircle(earColor, w * 0.11f, Offset(w * 0.28f, h * 0.28f))
            drawCircle(earColor, w * 0.11f, Offset(w * 0.72f, h * 0.28f))
        }
        MochiEar.FROG -> {
            drawRoundRect(color, Offset(w * 0.18f, h * 0.16f), Size(w * 0.64f, h * 0.28f), CornerRadius(40f, 40f))
            drawCircle(Color(0xFFFFFBF6), w * 0.09f, Offset(w * 0.36f, h * 0.22f))
            drawCircle(Color(0xFFFFFBF6), w * 0.09f, Offset(w * 0.64f, h * 0.22f))
        }
        MochiEar.TUFT -> drawOval(accent, Offset(w * 0.44f, h * 0.08f), Size(w * 0.12f, h * 0.16f))
        MochiEar.SPROUT -> {
            drawOval(Color(0xFF7DBE6A), Offset(w * 0.40f, h * 0.06f), Size(w * 0.14f, h * 0.16f))
            drawOval(Color(0xFF8FCB8A), Offset(w * 0.50f, h * 0.02f), Size(w * 0.14f, h * 0.16f))
            drawRoundRect(Color(0xFF6AAA58), Offset(w * 0.48f, h * 0.14f), Size(w * 0.035f, h * 0.12f), CornerRadius(4f, 4f))
        }
        MochiEar.STAR -> Unit
        MochiEar.DRAGON -> {
            drawCircle(accent, w * 0.045f, Offset(w * 0.38f, h * 0.16f))
            drawCircle(accent, w * 0.045f, Offset(w * 0.62f, h * 0.16f))
            val wing = Path().apply {
                moveTo(w * 0.16f, h * 0.48f)
                lineTo(w * 0.02f, h * 0.34f)
                lineTo(w * 0.18f, h * 0.40f)
                close()
            }
            val wingR = Path().apply {
                moveTo(w * 0.84f, h * 0.48f)
                lineTo(w * 0.98f, h * 0.34f)
                lineTo(w * 0.82f, h * 0.40f)
                close()
            }
            drawPath(wing, color.shade())
            drawPath(wingR, color.shade())
        }
        MochiEar.HOOD -> drawArcHood(color)
        MochiEar.PENGUIN -> drawArcHood(Color(0xFF3E4654))
        MochiEar.GHOST, MochiEar.CLOUD, MochiEar.EGG, MochiEar.NONE, MochiEar.RIBBON -> Unit
    }
}

private fun DrawScope.drawArcHood(color: Color) {
    drawArc(
        color = color,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = true,
        topLeft = Offset(size.width * 0.18f, size.height * 0.16f),
        size = Size(size.width * 0.64f, size.height * 0.42f),
    )
}

private fun DrawScope.drawBodyShape(ear: MochiEar, speciesId: String, body: Color, accent: Color) {
    val w = size.width
    val h = size.height
    when (ear) {
        MochiEar.STAR -> drawPath(starPath(center, size.minDimension * 0.40f), body)
        MochiEar.CLOUD -> {
            drawCircle(body, w * 0.24f, Offset(w * 0.38f, h * 0.52f))
            drawCircle(body, w * 0.22f, Offset(w * 0.62f, h * 0.54f))
            drawCircle(body, w * 0.18f, Offset(w * 0.50f, h * 0.40f))
        }
        MochiEar.GHOST -> {
            val path = Path().apply {
                moveTo(w * 0.22f, h * 0.72f)
                quadraticTo(w * 0.22f, h * 0.28f, w * 0.50f, h * 0.24f)
                quadraticTo(w * 0.78f, h * 0.28f, w * 0.78f, h * 0.72f)
                quadraticTo(w * 0.68f, h * 0.64f, w * 0.58f, h * 0.78f)
                quadraticTo(w * 0.50f, h * 0.66f, w * 0.40f, h * 0.78f)
                quadraticTo(w * 0.30f, h * 0.64f, w * 0.22f, h * 0.72f)
                close()
            }
            drawPath(path, body)
        }
        MochiEar.EGG -> drawOval(body, Offset(w * 0.24f, h * 0.18f), Size(w * 0.52f, h * 0.66f))
        else -> drawOval(body, Offset(w * 0.16f, h * 0.28f), Size(w * 0.68f, h * 0.52f))
    }
    if (ear == MochiEar.PENGUIN) {
        drawOval(Color(0xFFFFFBF6), Offset(w * 0.30f, h * 0.40f), Size(w * 0.40f, h * 0.32f))
        drawCircle(accent, w * 0.045f, Offset(w * 0.50f, h * 0.50f))
    }
    if (ear == MochiEar.RIBBON || speciesId == "sp_dumpling") {
        drawArc(body.shade(), 200f, 140f, false, Offset(w * 0.22f, h * 0.34f), Size(w * 0.20f, h * 0.28f), style = Stroke(3f))
        drawArc(body.shade(), 200f, 140f, false, Offset(w * 0.58f, h * 0.34f), Size(w * 0.20f, h * 0.28f), style = Stroke(3f))
    }
    if (ear == MochiEar.PANDA) {
        drawOval(Color(0xFF2C2C2C), Offset(w * 0.26f, h * 0.42f), Size(w * 0.16f, h * 0.12f))
        drawOval(Color(0xFF2C2C2C), Offset(w * 0.58f, h * 0.42f), Size(w * 0.16f, h * 0.12f))
    }
}

private fun DrawScope.drawFace(closed: Boolean, pose: Pose, accent: Color, ear: MochiEar) {
    val w = size.width
    val h = size.height
    val eyeY = h * 0.50f
    val left = Offset(w * 0.40f, eyeY)
    val right = Offset(w * 0.60f, eyeY)
    drawCircle(Blush.copy(alpha = 0.85f), w * 0.055f, Offset(w * 0.30f, h * 0.56f))
    drawCircle(Blush.copy(alpha = 0.85f), w * 0.055f, Offset(w * 0.70f, h * 0.56f))
    if (ear == MochiEar.HAMSTER) {
        drawCircle(Color(0xFFFFD0B0), w * 0.07f, Offset(w * 0.26f, h * 0.58f))
        drawCircle(Color(0xFFFFD0B0), w * 0.07f, Offset(w * 0.74f, h * 0.58f))
    }
    if (closed || pose == Pose.SAD) {
        drawLineEye(left, closed)
        drawLineEye(right, closed)
    } else {
        drawCircle(EyeInk, w * 0.028f, left)
        drawCircle(EyeInk, w * 0.028f, right)
        drawCircle(Color.White, w * 0.01f, left + Offset(-2f, -2f))
        drawCircle(Color.White, w * 0.01f, right + Offset(-2f, -2f))
    }
    if (ear == MochiEar.TUFT || ear == MochiEar.PENGUIN) {
        drawCircle(accent, w * 0.04f, Offset(w * 0.50f, h * 0.56f))
    } else {
        val mouth = Path().apply {
            moveTo(w * 0.46f, h * 0.58f)
            quadraticTo(w * 0.50f, if (pose == Pose.SAD) h * 0.56f else h * 0.62f, w * 0.54f, h * 0.58f)
        }
        drawPath(mouth, EyeInk, style = Stroke(width = 2.4f))
    }
}

private fun DrawScope.drawLineEye(at: Offset, closed: Boolean) {
    val path = Path().apply {
        moveTo(at.x - 7f, at.y)
        quadraticTo(at.x, at.y + if (closed) 1f else 5f, at.x + 7f, at.y)
    }
    drawPath(path, EyeInk, style = Stroke(2.4f))
}

private fun DrawScope.drawSpeciesMarks(speciesId: String, accent: Color, body: Color) {
    val w = size.width
    val h = size.height
    when (speciesId) {
        "sp_berry" -> {
            drawOval(accent, Offset(w * 0.42f, h * 0.08f), Size(w * 0.16f, h * 0.16f))
            drawOval(Color(0xFF7DBE6A), Offset(w * 0.44f, h * 0.04f), Size(w * 0.08f, h * 0.08f))
        }
        "sp_tiger" -> {
            drawRoundRect(accent, Offset(w * 0.34f, h * 0.36f), Size(w * 0.08f, h * 0.03f), CornerRadius(4f, 4f))
            drawRoundRect(accent, Offset(w * 0.58f, h * 0.36f), Size(w * 0.08f, h * 0.03f), CornerRadius(4f, 4f))
        }
        "sp_raccoon" -> {
            drawOval(accent.copy(alpha = 0.85f), Offset(w * 0.28f, h * 0.44f), Size(w * 0.16f, h * 0.10f))
            drawOval(accent.copy(alpha = 0.85f), Offset(w * 0.56f, h * 0.44f), Size(w * 0.16f, h * 0.10f))
        }
        "sp_cheese" -> drawCircle(body.shade(), w * 0.04f, Offset(w * 0.62f, h * 0.46f))
        "sp_cookie" -> {
            drawCircle(accent, w * 0.025f, Offset(w * 0.40f, h * 0.42f))
            drawCircle(accent, w * 0.025f, Offset(w * 0.62f, h * 0.48f))
        }
        "sp_moon" -> drawCircle(accent, w * 0.05f, Offset(w * 0.66f, h * 0.22f))
        "sp_night" -> {
            drawCircle(accent, w * 0.02f, Offset(w * 0.34f, h * 0.40f))
            drawCircle(accent, w * 0.015f, Offset(w * 0.66f, h * 0.44f))
        }
        "sp_melon" -> drawRoundRect(accent.copy(alpha = 0.35f), Offset(w * 0.46f, h * 0.32f), Size(w * 0.08f, h * 0.40f), CornerRadius(8f, 8f))
        "sp_injeolmi" -> {
            drawCircle(accent, w * 0.015f, Offset(w * 0.36f, h * 0.40f))
            drawCircle(accent, w * 0.015f, Offset(w * 0.66f, h * 0.42f))
            drawCircle(accent, w * 0.015f, Offset(w * 0.50f, h * 0.36f))
        }
    }
}

private fun DrawScope.drawOutfit(outfitId: String?, accent: Color) {
    val w = size.width
    val h = size.height
    when (outfitId) {
        "hat_sprout" -> {
            drawOval(Color(0xFF7DBE6A), Offset(w * 0.40f, h * 0.16f), Size(w * 0.12f, h * 0.12f))
            drawOval(Color(0xFF8FCB8A), Offset(w * 0.50f, h * 0.12f), Size(w * 0.12f, h * 0.12f))
        }
        "scarf_cloud" -> drawRoundRect(Color(0xFFFFF6EA), Offset(w * 0.28f, h * 0.58f), Size(w * 0.44f, h * 0.08f), CornerRadius(20f, 20f))
        "ribbon_berry" -> {
            drawCircle(accent, w * 0.05f, Offset(w * 0.40f, h * 0.30f))
            drawCircle(accent, w * 0.05f, Offset(w * 0.50f, h * 0.30f))
            drawCircle(Color(0xFFFFC9D4), w * 0.025f, Offset(w * 0.45f, h * 0.30f))
        }
        "beret" -> drawOval(Color(0xFFD4655A), Offset(w * 0.30f, h * 0.20f), Size(w * 0.40f, h * 0.14f))
        "raincoat" -> drawRoundRect(Color(0xFF8FB7E8), Offset(w * 0.24f, h * 0.52f), Size(w * 0.52f, h * 0.22f), CornerRadius(18f, 18f))
        "crown" -> {
            val path = Path().apply {
                moveTo(w * 0.32f, h * 0.30f)
                lineTo(w * 0.36f, h * 0.16f)
                lineTo(w * 0.44f, h * 0.28f)
                lineTo(w * 0.50f, h * 0.12f)
                lineTo(w * 0.56f, h * 0.28f)
                lineTo(w * 0.64f, h * 0.16f)
                lineTo(w * 0.68f, h * 0.30f)
                close()
            }
            drawPath(path, Color(0xFFE7C56B))
        }
        "sunglasses" -> drawRoundRect(Color(0xFF2C2C2C), Offset(w * 0.32f, h * 0.46f), Size(w * 0.36f, h * 0.08f), CornerRadius(8f, 8f))
        "hood_yellow" -> drawArcHood(Color(0xFFFFE08A))
        else -> Unit
    }
}

private fun DrawScope.drawZzz() {
    drawCircle(EyeInk.copy(alpha = 0.55f), 3f, Offset(size.width * 0.74f, size.height * 0.28f))
    drawCircle(EyeInk.copy(alpha = 0.4f), 4.5f, Offset(size.width * 0.82f, size.height * 0.20f))
}

private fun starPath(center: Offset, radius: Float): Path {
    val path = Path()
    for (i in 0 until 10) {
        val r = if (i % 2 == 0) radius else radius * 0.46f
        val angle = -PI / 2 + i * PI / 5
        val x = center.x + cos(angle).toFloat() * r
        val y = center.y + sin(angle).toFloat() * r
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

private fun DrawScope.drawMotif(motif: ItemMotif) {
    val c = center
    val r = size.minDimension * 0.28f
    val wood = Color(0xFFE7C29A)
    val leaf = Color(0xFF8FCB8A)
    val peach = Color(0xFFF6C1B0)
    val ink = Color(0xFF5C4636)
    when (motif) {
        ItemMotif.COOKIE -> drawCircle(Color(0xFFE7C29A), r, c)
        ItemMotif.CANDY -> drawCircle(Color(0xFFFFE08A), r, c)
        ItemMotif.PEACH -> drawCircle(peach, r, c)
        ItemMotif.DUMPLING -> drawOval(Color(0xFFFFF3D0), Offset(c.x - r, c.y - r * 0.7f), Size(r * 2, r * 1.4f))
        ItemMotif.PILLOW, ItemMotif.BED -> {
            drawRoundRect(wood, Offset(c.x - r * 1.3f, c.y - r * 0.45f), Size(r * 2.6f, r * 0.9f), CornerRadius(12f, 12f))
            drawRoundRect(Color.White, Offset(c.x - r, c.y - r * 0.7f), Size(r * 1.1f, r * 0.55f), CornerRadius(8f, 8f))
        }
        ItemMotif.SHELF -> drawRoundRect(wood, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), CornerRadius(6f, 6f))
        ItemMotif.TABLE, ItemMotif.DESK -> {
            drawOval(wood, Offset(c.x - r * 1.2f, c.y - r * 0.35f), Size(r * 2.4f, r * 0.7f))
            drawRoundRect(wood.shade(), Offset(c.x - r * 0.1f, c.y), Size(r * 0.2f, r), CornerRadius(4f, 4f))
        }
        ItemMotif.LAMP -> {
            drawCircle(Color(0xFFFFE7A8), r * 0.7f, c - Offset(0f, r * 0.2f))
            drawRoundRect(wood, Offset(c.x - r * 0.08f, c.y), Size(r * 0.16f, r), CornerRadius(3f, 3f))
        }
        ItemMotif.CHAIR -> drawRoundRect(Color(0xFFFFF6EA), Offset(c.x - r, c.y - r * 0.6f), Size(r * 2, r * 1.4f), CornerRadius(r, r))
        ItemMotif.RUG -> drawOval(Color(0xFFD7F0E4), Offset(c.x - r * 1.3f, c.y - r * 0.7f), Size(r * 2.6f, r * 1.4f))
        ItemMotif.PLANT, ItemMotif.SPROUT -> {
            drawRoundRect(wood, Offset(c.x - r * 0.45f, c.y), Size(r * 0.9f, r * 0.7f), CornerRadius(4f, 4f))
            drawOval(leaf, Offset(c.x - r, c.y - r), Size(r * 2, r))
        }
        ItemMotif.WINDOW -> drawRoundRect(Color(0xFFD5EEF8), Offset(c.x - r, c.y - r), Size(r * 2, r * 2), CornerRadius(8f, 8f), style = Stroke(4f))
        ItemMotif.LIGHTS -> {
            drawCircle(Color(0xFFFFE08A), r * 0.35f, c - Offset(r, 0f))
            drawCircle(Color(0xFFFFE08A), r * 0.35f, c)
            drawCircle(Color(0xFFFFE08A), r * 0.35f, c + Offset(r, 0f))
        }
        ItemMotif.TANK -> drawRoundRect(Color(0xFFD5EEF8), Offset(c.x - r, c.y - r * 0.8f), Size(r * 2, r * 1.6f), CornerRadius(8f, 8f))
        ItemMotif.PIANO -> drawRoundRect(Color(0xFF5C4636), Offset(c.x - r * 1.2f, c.y - r * 0.5f), Size(r * 2.4f, r), CornerRadius(8f, 8f))
        ItemMotif.RIBBON -> drawCircle(peach, r * 0.7f, c)
        ItemMotif.BERET -> drawOval(Color(0xFFD4655A), Offset(c.x - r, c.y - r * 0.45f), Size(r * 2, r * 0.9f))
        ItemMotif.SCARF -> drawRoundRect(Color(0xFFFFE08A), Offset(c.x - r, c.y - r * 0.35f), Size(r * 2, r * 0.7f), CornerRadius(20f, 20f))
        ItemMotif.COAT -> drawRoundRect(Color(0xFF8FB7E8), Offset(c.x - r * 0.8f, c.y - r), Size(r * 1.6f, r * 2), CornerRadius(16f, 16f))
        ItemMotif.CROWN -> drawPath(starPath(c, r), Color(0xFFE7C56B))
        ItemMotif.GLASSES -> drawRoundRect(ink, Offset(c.x - r, c.y - r * 0.3f), Size(r * 2, r * 0.6f), CornerRadius(8f, 8f))
        ItemMotif.HOOD -> drawCircle(Color(0xFFFFE08A), r, c)
        ItemMotif.SHELL -> drawArc(peach, 200f, 140f, true, Offset(c.x - r, c.y - r), Size(r * 2, r * 2))
        ItemMotif.CLOVER -> {
            drawCircle(leaf, r * 0.45f, c + Offset(-r * 0.35f, -r * 0.2f))
            drawCircle(leaf, r * 0.45f, c + Offset(r * 0.35f, -r * 0.2f))
            drawCircle(leaf, r * 0.45f, c + Offset(0f, r * 0.35f))
        }
        ItemMotif.ACORN -> drawOval(Color(0xFF8A5A32), Offset(c.x - r * 0.55f, c.y - r), Size(r * 1.1f, r * 1.6f))
        ItemMotif.PEBBLE -> drawOval(Color(0xFFC9C2BA), Offset(c.x - r, c.y - r * 0.6f), Size(r * 2, r * 1.2f))
        ItemMotif.FEATHER -> drawOval(Color.White, Offset(c.x - r * 0.35f, c.y - r), Size(r * 0.7f, r * 2), )
        ItemMotif.STAR -> drawPath(starPath(c, r), Color(0xFFFFE08A))
        ItemMotif.MUSHROOM -> {
            drawArc(Color(0xFFE25B5B), 180f, 180f, true, Offset(c.x - r, c.y - r), Size(r * 2, r * 1.4f))
            drawRoundRect(Color(0xFFFFF6EA), Offset(c.x - r * 0.28f, c.y), Size(r * 0.56f, r * 0.8f), CornerRadius(6f, 6f))
        }
        ItemMotif.NOTE -> drawRoundRect(Color(0xFFFFF6EA), Offset(c.x - r * 0.7f, c.y - r), Size(r * 1.4f, r * 1.8f), CornerRadius(6f, 6f))
        ItemMotif.TV -> drawRoundRect(ink, Offset(c.x - r, c.y - r * 0.7f), Size(r * 2, r * 1.4f), CornerRadius(8f, 8f))
        ItemMotif.CLOSET -> drawRoundRect(wood, Offset(c.x - r * 0.8f, c.y - r), Size(r * 1.6f, r * 2), CornerRadius(6f, 6f))
        ItemMotif.TUB -> drawOval(Color(0xFFF7FBFF), Offset(c.x - r * 1.2f, c.y - r * 0.5f), Size(r * 2.4f, r))
        ItemMotif.TOWER -> drawRoundRect(peach, Offset(c.x - r * 0.45f, c.y - r), Size(r * 0.9f, r * 2), CornerRadius(20f, 20f))
        ItemMotif.BOWL -> drawOval(Color(0xFFFFF6EA), Offset(c.x - r, c.y - r * 0.4f), Size(r * 2, r * 0.9f))
        ItemMotif.CLOCK -> {
            drawCircle(Color(0xFFFFF6EA), r, c)
            drawCircle(ink, r, c, style = Stroke(3f))
        }
        ItemMotif.BLANKET -> drawRoundRect(peach, Offset(c.x - r, c.y - r * 0.55f), Size(r * 2, r * 1.1f), CornerRadius(12f, 12f))
        ItemMotif.WALL -> drawRoundRect(peach, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), CornerRadius(10f, 10f))
        ItemMotif.FLOOR -> drawRoundRect(wood, Offset(c.x - r, c.y - r * 0.7f), Size(r * 2, r * 1.4f), CornerRadius(8f, 8f))
        ItemMotif.GENERIC -> drawCircle(peach, r, c)
    }
}
