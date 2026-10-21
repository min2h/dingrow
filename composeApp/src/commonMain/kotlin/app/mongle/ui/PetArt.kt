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
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import app.mongle.model.GameState
import app.mongle.model.Pose
import app.mongle.model.Stage
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

private val Body = Color(0xFFFFE3C4)
private val BodyDeep = Color(0xFFF3C9A4)
private val Ear = Color(0xFFF6C7A1)
private val EarInner = Color(0xFFFFC1C8)
private val Blush = Color(0xFFF4A6A4)
private val Eye = Color(0xFF4A3728)
private val Belly = Color(0xFFFFF7EF)

@Composable
fun AnimatedPet(
    stage: Stage,
    pose: Pose,
    outfitId: String?,
    modifier: Modifier = Modifier,
) {
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
    Canvas(modifier) {
        val walking = pose == Pose.WALK || pose == Pose.OUT
        val phase = walk * (2f * PI.toFloat())
        val dx = if (walking) sin(phase) * size.minDimension * 0.14f else 0f
        val bob = when {
            pose == Pose.SLEEP -> 0f
            walking -> abs(sin(phase * 2f)) * size.minDimension * 0.028f
            else -> bounce * size.minDimension * 0.04f
        }
        val breath = if (pose == Pose.SLEEP || pose == Pose.EGG) 1f + bounce * 0.035f else 1f
        val stageScale = when (stage) {
            Stage.EGG -> 0.96f
            Stage.BABY -> 0.72f
            Stage.CHILD -> 0.86f
            Stage.ADULT -> 1f
        }
        translate(left = dx, top = -bob) {
            scale(stageScale * breath, stageScale * breath, pivot = center) {
                if (stage == Stage.EGG || pose == Pose.EGG) drawEgg(bounce) else drawCreature(pose, outfitId, phase)
            }
        }
    }
}

@Composable
fun RoomScene(state: GameState, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val wall = hexColor(state.wallHex)
        val floor = hexColor(state.floorHex)
        drawRect(wall)
        drawRect(
            color = floor,
            topLeft = Offset(0f, size.height * 0.62f),
            size = Size(size.width, size.height * 0.38f),
        )
        drawLine(
            color = Color.White.copy(alpha = 0.45f),
            start = Offset(0f, size.height * 0.62f),
            end = Offset(size.width, size.height * 0.62f),
            strokeWidth = 3f,
        )
        val room = state.activeRoom
        room.rugId?.let { drawRug(it) }
        room.bedId?.let { drawBed(it) }
        room.lampId?.let { drawLamp(it) }
        if (state.timeOfDay.name == "NIGHT" && !state.darkRoom) {
            drawRect(Color(0xFF1C2438).copy(alpha = 0.18f))
        }
        if (state.timeOfDay.name == "EVENING") {
            drawRect(Color(0xFFFFB086).copy(alpha = 0.12f))
        }
    }
}

private fun DrawScope.drawEgg(wobble: Float) {
    drawOval(
        color = Color(0x22000000),
        topLeft = Offset(size.width * 0.28f, size.height * 0.78f),
        size = Size(size.width * 0.44f, size.height * 0.08f),
    )
    drawOval(
        color = Color(0xFFFFF6EA),
        topLeft = Offset(size.width * 0.22f, size.height * 0.18f),
        size = Size(size.width * 0.56f, size.height * 0.64f),
    )
    drawOval(
        color = PeachSoft,
        topLeft = Offset(size.width * 0.30f, size.height * 0.28f),
        size = Size(size.width * 0.12f, size.height * 0.1f),
    )
    drawOval(
        color = Butter,
        topLeft = Offset(size.width * 0.55f, size.height * 0.42f),
        size = Size(size.width * 0.1f, size.height * 0.08f),
    )
    val crack = Path().apply {
        moveTo(size.width * 0.38f, size.height * 0.34f)
        lineTo(size.width * 0.48f, size.height * (0.42f + wobble * 0.02f))
        lineTo(size.width * 0.44f, size.height * 0.5f)
        lineTo(size.width * 0.58f, size.height * 0.58f)
    }
    drawPath(crack, Eye.copy(alpha = 0.55f), style = Stroke(width = size.minDimension * 0.015f))
}

private fun DrawScope.drawCreature(pose: Pose, outfitId: String?, phase: Float) {
    val w = size.width
    val h = size.height
    drawOval(
        color = Color(0x22000000),
        topLeft = Offset(w * 0.22f, h * 0.82f),
        size = Size(w * 0.56f, h * 0.07f),
    )
    drawCircle(Ear, radius = w * 0.12f, center = Offset(w * 0.32f, h * 0.28f))
    drawCircle(Ear, radius = w * 0.12f, center = Offset(w * 0.68f, h * 0.28f))
    drawCircle(EarInner, radius = w * 0.06f, center = Offset(w * 0.32f, h * 0.30f))
    drawCircle(EarInner, radius = w * 0.06f, center = Offset(w * 0.68f, h * 0.30f))
    drawCircle(Body, radius = w * 0.30f, center = Offset(w * 0.5f, h * 0.52f))
    drawCircle(Belly, radius = w * 0.16f, center = Offset(w * 0.5f, h * 0.60f))
    val foot = sin(phase * 2f) * w * 0.03f
    if (pose == Pose.WALK || pose == Pose.OUT) {
        drawCircle(BodyDeep, radius = w * 0.045f, center = Offset(w * 0.40f + foot, h * 0.80f))
        drawCircle(BodyDeep, radius = w * 0.045f, center = Offset(w * 0.60f - foot, h * 0.80f))
    } else if (pose != Pose.SLEEP) {
        drawCircle(BodyDeep, radius = w * 0.04f, center = Offset(w * 0.40f, h * 0.80f))
        drawCircle(BodyDeep, radius = w * 0.04f, center = Offset(w * 0.60f, h * 0.80f))
    }
    drawEyes(pose, w, h)
    drawCircle(Blush.copy(alpha = 0.85f), radius = w * 0.045f, center = Offset(w * 0.34f, h * 0.58f))
    drawCircle(Blush.copy(alpha = 0.85f), radius = w * 0.045f, center = Offset(w * 0.66f, h * 0.58f))
    drawMouth(pose, w, h)
    drawOutfit(outfitId, w, h)
    if (pose == Pose.OUT) {
        drawRoundRect(
            color = Butter,
            topLeft = Offset(w * 0.62f, h * 0.62f),
            size = Size(w * 0.16f, h * 0.14f),
            cornerRadius = CornerRadius(w * 0.04f),
        )
    }
    if (pose == Pose.SLEEP) {
        drawCircle(Mint.copy(alpha = 0.8f), radius = w * 0.03f, center = Offset(w * 0.72f, h * 0.30f))
        drawCircle(Mint.copy(alpha = 0.55f), radius = w * 0.02f, center = Offset(w * 0.80f, h * 0.22f))
    }
}

private fun DrawScope.drawEyes(pose: Pose, w: Float, h: Float) {
    if (pose == Pose.SLEEP) {
        drawLine(Eye, Offset(w * 0.40f, h * 0.50f), Offset(w * 0.46f, h * 0.52f), strokeWidth = w * 0.012f)
        drawLine(Eye, Offset(w * 0.46f, h * 0.52f), Offset(w * 0.40f, h * 0.54f), strokeWidth = w * 0.012f)
        drawLine(Eye, Offset(w * 0.54f, h * 0.50f), Offset(w * 0.60f, h * 0.52f), strokeWidth = w * 0.012f)
        drawLine(Eye, Offset(w * 0.60f, h * 0.52f), Offset(w * 0.54f, h * 0.54f), strokeWidth = w * 0.012f)
        return
    }
    val eyeR = if (pose == Pose.HAPPY || pose == Pose.WALK) w * 0.035f else w * 0.028f
    drawCircle(Eye, eyeR, Offset(w * 0.42f, h * 0.50f))
    drawCircle(Eye, eyeR, Offset(w * 0.58f, h * 0.50f))
    drawCircle(Color.White, eyeR * 0.35f, Offset(w * 0.43f, h * 0.49f))
    drawCircle(Color.White, eyeR * 0.35f, Offset(w * 0.59f, h * 0.49f))
}

private fun DrawScope.drawMouth(pose: Pose, w: Float, h: Float) {
    val mouth = Path()
    if (pose == Pose.SAD) {
        mouth.moveTo(w * 0.44f, h * 0.64f)
        mouth.quadraticTo(w * 0.5f, h * 0.58f, w * 0.56f, h * 0.64f)
    } else if (pose == Pose.HAPPY || pose == Pose.WALK || pose == Pose.OUT) {
        mouth.moveTo(w * 0.44f, h * 0.60f)
        mouth.quadraticTo(w * 0.5f, h * 0.70f, w * 0.56f, h * 0.60f)
    } else {
        mouth.moveTo(w * 0.46f, h * 0.62f)
        mouth.quadraticTo(w * 0.5f, h * 0.66f, w * 0.54f, h * 0.62f)
    }
    drawPath(mouth, Eye, style = Stroke(width = w * 0.012f))
}

private fun DrawScope.drawOutfit(outfitId: String?, w: Float, h: Float) {
    when (outfitId) {
        "hat_sprout" -> {
            drawRoundRect(
                Mint,
                topLeft = Offset(w * 0.36f, h * 0.16f),
                size = Size(w * 0.28f, h * 0.08f),
                cornerRadius = CornerRadius(w * 0.04f),
            )
            drawRoundRect(
                Color(0xFF3E8F6A),
                topLeft = Offset(w * 0.48f, h * 0.08f),
                size = Size(w * 0.04f, h * 0.1f),
                cornerRadius = CornerRadius(8f),
            )
        }
        "scarf_cloud" -> {
            drawRoundRect(
                Color(0xFFD7EEF8),
                topLeft = Offset(w * 0.34f, h * 0.66f),
                size = Size(w * 0.32f, h * 0.08f),
                cornerRadius = CornerRadius(w * 0.04f),
            )
        }
        "ribbon_berry" -> {
            drawCircle(Color(0xFFD4537E), radius = w * 0.05f, center = Offset(w * 0.70f, h * 0.36f))
            drawCircle(Color(0xFFD4537E), radius = w * 0.035f, center = Offset(w * 0.76f, h * 0.40f))
        }
    }
}

private fun DrawScope.drawRug(id: String) {
    val color = if (id == "rug_star") Butter else Mint.copy(alpha = 0.85f)
    drawOval(
        color,
        topLeft = Offset(size.width * 0.18f, size.height * 0.70f),
        size = Size(size.width * 0.64f, size.height * 0.16f),
    )
}

private fun DrawScope.drawBed(id: String) {
    val wood = id == "bed_wood"
    val color = if (wood) Color(0xFFC9845A) else Color.White
    drawRoundRect(
        color,
        topLeft = Offset(size.width * 0.08f, size.height * 0.48f),
        size = Size(size.width * 0.28f, size.height * 0.22f),
        cornerRadius = CornerRadius(18f),
    )
    drawRoundRect(
        if (wood) Color(0xFFE7C8A4) else PeachSoft,
        topLeft = Offset(size.width * 0.12f, size.height * 0.50f),
        size = Size(size.width * 0.12f, size.height * 0.08f),
        cornerRadius = CornerRadius(10f),
    )
}

private fun DrawScope.drawLamp(id: String) {
    val glow = if (id == "lamp_sun") Butter else Color(0xFFD7E4FF)
    drawLine(
        Ink.copy(alpha = 0.7f),
        start = Offset(size.width * 0.84f, size.height * 0.62f),
        end = Offset(size.width * 0.84f, size.height * 0.28f),
        strokeWidth = 4f,
    )
    drawCircle(glow, radius = size.minDimension * 0.06f, center = Offset(size.width * 0.84f, size.height * 0.24f))
}
