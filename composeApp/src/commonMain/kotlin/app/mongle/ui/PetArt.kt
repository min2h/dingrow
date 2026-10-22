package app.mongle.ui

import androidx.compose.animation.Crossfade
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
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import app.mongle.model.GameState
import app.mongle.model.Pose
import app.mongle.model.Stage
import app.mongle.model.TimeOfDay
import app.mongle.model.roomTheme
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.delay
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
fun RoomScene(
    state: GameState,
    modifier: Modifier = Modifier,
    petScale: Float = 0.28f,
    onPet: (() -> Unit)? = null,
) {
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
                body = if (pinned == null) state.reactionBody else "",
                onClick = onPet,
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
    body: String = "",
    onClick: (() -> Unit)? = null,
) {
    val motion = if (animate && body.isBlank()) liveMotion(act) else Motion(breath, walk, blink = 0f, pop = 1f)
    val squash = if (animate && body.isNotBlank()) rememberSquash(body) else null
    val poseEase = rememberPoseEase(act, enabled = animate && body.isBlank())
    val density = LocalDensity.current
    val sleeping = act == PetAct.SLEEP
    val walking = act == PetAct.WALK
    val wave = (motion.breath - 0.5f) * if (sleeping) 0.45f else 1f
    val stride = if (SpeciesPoses.weakWalk(speciesId)) 0.35f else 1f
    val scaleX = squash?.sx ?: ((1f + 0.05f * wave) * motion.pop * (1f + 0.14f * poseEase))
    val scaleY = squash?.sy ?: ((1f - 0.045f * wave) * motion.pop * (1f - 0.12f * poseEase) * if (sleeping) 0.94f else 1f)
    val bobDp = if (sleeping) 3f else 7f
    val lean = squash?.rot ?: if (walking) sin(motion.walk * 2 * PI).toFloat() * 7f * stride else 0f
    val driftDp = if (walking) sin(motion.walk * 2 * PI).toFloat() * 16f * stride else 0f
    val jump = squash?.yDp ?: 0f
    val mirror = walking && SpeciesPoses.mirrorSafeWalk(speciesId) && sin(motion.walk * 2 * PI) > 0
    Box(
        modifier
            .graphicsLayer {
                this.scaleX = scaleX
                this.scaleY = scaleY
                rotationZ = lean
                translationY = with(density) { ((-bobDp * wave) + jump).dp.toPx() }
                translationX = with(density) { driftDp.dp.toPx() }
                transformOrigin = TransformOrigin(0.5f, 0.92f)
            }
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
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
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { this.scaleX = if (mirror) -1f else 1f },
        ) {
            if (animate && body.isBlank()) {
                Crossfade(
                    targetState = act,
                    modifier = Modifier.fillMaxSize(),
                    animationSpec = tween(360, easing = FastOutSlowInEasing),
                    label = "pose",
                ) { frame ->
                    PoseSprite(speciesId, stage, frame, if (frame == PetAct.IDLE) motion.blink else 0f)
                }
            } else {
                PoseSprite(speciesId, stage, act, blink = 0f)
            }
        }
        if (sleeping) {
            SleepMarks(phase = motion.walk, modifier = Modifier.fillMaxSize())
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
private fun PoseSprite(speciesId: String, stage: Stage, act: PetAct, blink: Float) {
    val shown = if (act == PetAct.IDLE) PetAct.IDLE else act
    val blinkId = petAssetId(speciesId, stage, PetAct.BLINK)
    val idleId = petAssetId(speciesId, stage, PetAct.IDLE)
    val showBlink = act == PetAct.IDLE && blink > 0.01f && blinkId != idleId
    Image(
        painter = painterResource(Art.pet(speciesId, stage, shown)),
        contentDescription = null,
        modifier = Modifier.fillMaxSize().alpha(if (showBlink) 1f - blink else 1f),
        contentScale = ContentScale.Fit,
    )
    if (showBlink) {
        Image(
            painter = painterResource(Art.drawable(blinkId)),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().alpha(blink),
            contentScale = ContentScale.Fit,
        )
    }
}

/** Peaks halfway through a pose change so the crossfade eases through a squash, then settles. */
@Composable
private fun rememberPoseEase(act: PetAct, enabled: Boolean): Float {
    val ease = remember { Animatable(0f) }
    var seen by remember { mutableStateOf(false) }
    LaunchedEffect(act, enabled) {
        if (!enabled) {
            ease.snapTo(0f)
            return@LaunchedEffect
        }
        if (!seen) {
            seen = true
            return@LaunchedEffect
        }
        ease.snapTo(0f)
        ease.animateTo(1f, tween(150, easing = FastOutSlowInEasing))
        ease.animateTo(0f, tween(210, easing = FastOutSlowInEasing))
    }
    return if (enabled) ease.value else 0f
}

/** Sleep frames have no drawn zzz, so the marks float up in code. */
@Composable
private fun SleepMarks(phase: Float, modifier: Modifier = Modifier) {
    Box(modifier) {
        repeat(3) { index ->
            val travel = (phase + index * 0.33f) % 1f
            val fade = sin(travel * PI).toFloat().coerceIn(0f, 1f)
            Text(
                "z",
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 2.dp, end = 4.dp)
                    .offset(x = (index * 8).dp, y = ((1f - travel) * 34f).dp)
                    .alpha(fade)
                    .graphicsLayer {
                        val grow = 0.7f + travel * 0.6f
                        scaleX = grow
                        scaleY = grow
                    },
                color = Color(0xFF6E8CAE),
                fontSize = (11 + index * 3).sp,
                fontWeight = FontWeight.Bold,
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

@Composable
private fun rememberSquash(body: String): Squash {
    val frames = squashFrames(body)
    var index by remember(body) { mutableIntStateOf(0) }
    LaunchedEffect(body) {
        frames.indices.forEach { step ->
            index = step
            delay(150)
        }
    }
    return frames[index.coerceIn(frames.indices)]
}

private fun blinkWindow(cycle: Float): Float {
    val start = 0.90f
    if (cycle < start) return 0f
    val t = (cycle - start) / (1f - start)
    return if (t < 0.5f) t * 2f else (1f - t) * 2f
}
