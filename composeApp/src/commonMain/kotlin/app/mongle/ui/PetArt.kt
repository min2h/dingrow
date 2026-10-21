package app.mongle.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.mongle.model.GameState
import app.mongle.model.Pose
import app.mongle.model.Stage
import app.mongle.model.TimeOfDay
import app.mongle.model.roomTheme
import kotlin.math.PI
import kotlin.math.sin
import org.jetbrains.compose.resources.painterResource

/**
 * 테스트에서만 쓴다. 값을 주면 무한 애니메이션을 켜지 않고 그 위상으로 그린다.
 * Compose UI 테스트의 유휴 대기가 무한 전환 때문에 끝나지 않는 것을 막는다.
 */
val LocalPetMotion = staticCompositionLocalOf<Pair<Float, Float>?> { null }

private data class Motion(val breath: Float, val walk: Float, val blink: Float, val pop: Float)

@Composable
fun ArtImage(id: String, modifier: Modifier = Modifier, description: String? = null) {
    Image(
        painter = painterResource(Art.drawable(id)),
        contentDescription = description,
        modifier = modifier,
        contentScale = ContentScale.Fit,
    )
}

@Composable
fun StillPet(
    speciesId: String,
    stage: Stage,
    pose: Pose,
    modifier: Modifier = Modifier,
) {
    val act = when (pose) {
        Pose.EGG -> PetAct.EGG
        Pose.HAPPY -> PetAct.HAPPY
        Pose.SLEEP -> PetAct.SLEEP
        Pose.WALK, Pose.OUT -> PetAct.WALK
        else -> PetAct.IDLE
    }
    Image(
        painter = painterResource(Art.pet(speciesId, stage, act)),
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Fit,
    )
}

@Composable
fun AnimatedPet(
    stage: Stage,
    pose: Pose,
    outfitId: String?,
    modifier: Modifier = Modifier,
    speciesId: String = "base",
    still: Boolean = false,
) {
    val pinned = LocalPetMotion.current
    PetFigure(
        speciesId = speciesId,
        stage = stage,
        act = petAct(stage, pose, energy = 80, status = ""),
        outfitId = outfitId,
        modifier = modifier,
        animate = pinned == null && !still,
        breath = pinned?.first ?: 0.35f,
        walk = pinned?.second ?: 0.2f,
    )
}

@Composable
fun RoomScene(state: GameState, modifier: Modifier = Modifier, petScale: Float = 0.28f) {
    val pinned = LocalPetMotion.current
    val night = state.timeOfDay == TimeOfDay.NIGHT
    val theme = roomTheme(state.activeRoom.wallpaperId)
    val act = petAct(state.stage, state.pose, state.energy, state.statusMessage)
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .aspectRatio(3f / 4f, matchHeightConstraintsFirst = true)
                .fillMaxHeight()
                .clip(RoundedCornerShape(28.dp)),
        ) {
            Image(
                painter = painterResource(Art.room(theme, night, square = false)),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                alignment = Alignment.Center,
            )
            PetFigure(
                speciesId = state.speciesId,
                stage = state.stage,
                act = act,
                outfitId = state.outfitId,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp)
                    .fillMaxWidth(petScale)
                    .aspectRatio(1f),
                animate = pinned == null,
                breath = pinned?.first ?: 0.35f,
                walk = pinned?.second ?: 0.2f,
            )
        }
    }
}

@Composable
private fun PetFigure(
    speciesId: String,
    stage: Stage,
    act: PetAct,
    outfitId: String?,
    modifier: Modifier,
    animate: Boolean,
    breath: Float,
    walk: Float,
) {
    val motion = if (animate) liveMotion(act) else Motion(breath, walk, blink = 0f, pop = 1f)
    val density = LocalDensity.current
    val base = speciesId == "base" || speciesId == "sp_peach"
    val showBlink = base && act == PetAct.IDLE && motion.blink > 0.01f
    val main = Art.pet(speciesId, stage, if (act == PetAct.IDLE) PetAct.IDLE else act)
    val sleeping = act == PetAct.SLEEP
    val walking = act == PetAct.WALK
    val wave = (motion.breath - 0.5f) * if (sleeping) 0.45f else 1f
    val scaleX = (1f + 0.05f * wave) * motion.pop
    val scaleY = (1f - 0.045f * wave) * motion.pop * if (sleeping) 0.94f else 1f
    val bobDp = if (sleeping) 3f else 7f
    val lean = if (walking) sin(motion.walk * 2 * PI).toFloat() * 7f else 0f
    val driftDp = if (walking) sin(motion.walk * 2 * PI).toFloat() * 16f else 0f
    Box(
        modifier.graphicsLayer {
            this.scaleX = scaleX
            this.scaleY = scaleY
            rotationZ = lean
            translationY = with(density) { (-bobDp * wave).dp.toPx() }
            translationX = with(density) { driftDp.dp.toPx() }
            transformOrigin = TransformOrigin(0.5f, 0.92f)
        },
    ) {
        Canvas(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(0.62f)
                .aspectRatio(3.4f)
                .graphicsLayer { this.scaleX = 1f + 0.08f * wave },
        ) {
            drawOval(Color(0x33000000))
        }
        Image(
            painter = painterResource(main),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().alpha(if (showBlink) 1f - motion.blink else 1f),
            contentScale = ContentScale.Fit,
        )
        if (showBlink) {
            Image(
                painter = painterResource(Art.pet(speciesId, stage, PetAct.BLINK)),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().alpha(motion.blink),
                contentScale = ContentScale.Fit,
            )
        }
        if (stage != Stage.EGG && act != PetAct.EGG && outfitId != null && Art.known(outfitId)) {
            Image(
                painter = painterResource(Art.drawable(outfitId)),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth(0.38f)
                    .aspectRatio(1f)
                    .offset(y = (-4).dp),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

@Composable
private fun liveMotion(act: PetAct): Motion {
    val transition = rememberInfiniteTransition(label = "mongle-live")
    val breath by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (act == PetAct.SLEEP) 2800 else 2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breath",
    )
    val walk by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(3200, easing = LinearEasing)),
        label = "walk",
    )
    val blinkCycle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(4600, easing = LinearEasing)),
        label = "blink",
    )
    val pop = remember { Animatable(1f) }
    LaunchedEffect(act) {
        if (act == PetAct.HAPPY || act == PetAct.EAT) {
            pop.snapTo(0.84f)
            pop.animateTo(
                1f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
            )
        }
    }
    val blink = if (act == PetAct.IDLE) blinkWindow(blinkCycle) else 0f
    return Motion(breath, walk, blink, pop.value)
}

private fun blinkWindow(cycle: Float): Float {
    val start = 0.90f
    if (cycle < start) return 0f
    val t = (cycle - start) / (1f - start)
    return if (t < 0.5f) t * 2f else (1f - t) * 2f
}
