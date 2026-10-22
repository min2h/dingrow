package app.mongle.ui

import androidx.compose.runtime.SideEffect

import androidx.compose.runtime.withFrameNanos

import androidx.compose.runtime.mutableLongStateOf

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
        act0 = petAct(stage, pose, energy = 80, status = ""),
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
            PlacedItems(app.mongle.logic.layoutOf(state.placements, state.outfitId, state.activeRoom.wallpaperId))
            PetFigure(
                speciesId = state.speciesId,
                stage = state.stage,
                act0 = act,
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
internal fun PetFigure(
    speciesId: String,
    stage: Stage,
    act0: PetAct,
    outfitId: String?,
    modifier: Modifier,
    animate: Boolean,
    breath: Float,
    walk: Float,
    body: String = "",
    onClick: (() -> Unit)? = null,
    hoisted: app.mongle.ui.motion.PetMotionEngine? = null,
) {
    val density = LocalDensity.current
    val sleeping = act0 == PetAct.SLEEP
    val walking = act0 == PetAct.WALK
    val engine = hoisted ?: remember { app.mongle.ui.motion.PetMotionEngine(speciesId.hashCode()) }
    val roaming = remember { mutableStateOf(false) }
    val act = if (roaming.value && act0 == PetAct.IDLE && stage != Stage.EGG) PetAct.WALK else act0
    // Only graphicsLayer/draw lambdas read this, so a frame never recomposes the screen.
    val frame = remember { mutableLongStateOf(0L) }
    SideEffect {
        engine.walking = walking && animate
        engine.sleeping = sleeping
    }
    if (animate) {
        LaunchedEffect(Unit) {
            var last = -1L
            while (true) {
                withFrameNanos { now ->
                    if (last > 0) engine.step((now - last) / 1_000_000_000f)
                    last = now
                    if (engine.roamMoving != roaming.value) roaming.value = engine.roamMoving
                    frame.longValue = now
                }
            }
        }
        LaunchedEffect(body) {
            if (body.isNotBlank()) engine.trigger(app.mongle.ui.motion.PetMotionEngine.shapeFor(body))
        }
        var seenAct by remember { mutableStateOf<PetAct?>(null) }
        LaunchedEffect(act) {
            if (seenAct != null) {
                engine.pulsePose()
                when (act) {
                    PetAct.HAPPY -> engine.trigger(app.mongle.ui.motion.PetMotionEngine.shapeFor("heart"))
                    PetAct.EAT -> engine.trigger(app.mongle.ui.motion.PetMotionEngine.shapeFor("eat"))
                    else -> Unit
                }
            }
            seenAct = act
        }
    }
    // Static pose for pinned screenshots and still previews.
    val staticWave = (breath - 0.5f) * if (sleeping) 0.45f else 1f
    val tap: (() -> Unit)? = if (onClick != null) {
        {
            if (animate) engine.trigger(app.mongle.ui.motion.PetMotionEngine.shapeFor("pat"))
            onClick()
        }
    } else null
    Box(
        modifier
            .graphicsLayer {
                frame.longValue
                transformOrigin = TransformOrigin(0.5f, 0.92f)
                if (animate) {
                    scaleX = engine.bodyScaleX()
                    scaleY = engine.bodyScaleY()
                    rotationZ = engine.rotation
                    translationY = engine.translationYdp.dp.toPx()
                    translationX = engine.translationXdp.dp.toPx()
                } else {
                    scaleX = 1f + 0.05f * staticWave
                    scaleY = (1f - 0.045f * staticWave) * if (sleeping) 0.94f else 1f
                    translationY = (-(if (sleeping) 3f else 7f) * staticWave).dp.toPx()
                    translationX = if (walking) (sin(walk * 2 * PI).toFloat() * 16f).dp.toPx() else 0f
                }
            }
            .then(if (tap != null) Modifier.clickable(onClick = tap) else Modifier),
    ) {
        Canvas(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(0.62f)
                .aspectRatio(3.4f)
                .graphicsLayer {
                    frame.longValue
                    // Counter the body transform so the shadow stays on the floor and lags the jump.
                    if (animate) {
                        val sx = engine.bodyScaleX().let { if (it == 0f) 1f else it }
                        scaleX = engine.shadowScale / sx
                        scaleY = engine.shadowScale / engine.bodyScaleY()
                        translationY = -engine.translationYdp.dp.toPx()
                        alpha = engine.shadowAlpha / 0.2f
                    } else {
                        scaleX = 1f + 0.08f * staticWave
                    }
                },
        ) {
            drawOval(Color(0x33000000))
        }
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    frame.longValue
                    val mirrorSafe = SpeciesPoses.mirrorSafeWalk(speciesId)
                    scaleX = if (animate) {
                        if (mirrorSafe) engine.facing.value.coerceIn(-1f, 1f).let { if (kotlin.math.abs(it) < 0.06f) 0.06f else it } else 1f
                    } else if (walking && mirrorSafe && sin(walk * 2 * PI) > 0) -1f else 1f
                },
        ) {
            val blinkOf: () -> Float = { if (animate) { frame.longValue; engine.blink } else 0f }
            if (animate && body.isBlank()) {
                Crossfade(
                    targetState = act,
                    modifier = Modifier.fillMaxSize(),
                    animationSpec = tween(420, easing = FastOutSlowInEasing),
                    label = "pose",
                ) { shown ->
                    PoseSprite(speciesId, stage, shown, if (shown == PetAct.IDLE) blinkOf else ({ 0f }))
                }
            } else {
                PoseSprite(speciesId, stage, act, if (animate) blinkOf else ({ 0f }))
            }
        }
        if (sleeping && !animate) {
            SleepMarks(phase = walk, modifier = Modifier.fillMaxSize())
        }
        if (stage != Stage.EGG && act != PetAct.EGG && outfitId != null && Art.known(outfitId)) {
            Image(
                painter = painterResource(Art.drawable(outfitId)),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth(0.38f)
                    .aspectRatio(1f)
                    .offset(y = (-4).dp)
                    .graphicsLayer {
                        frame.longValue
                        if (animate) {
                            translationY = engine.accessoryYdp.dp.toPx()
                            rotationZ = engine.accessoryYdp * 0.8f
                        }
                    },
                contentScale = ContentScale.Fit,
            )
        }
        if (animate) {
            Canvas(Modifier.fillMaxSize()) {
                frame.longValue
                drawParticles(engine, density.density)
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawParticles(engine: app.mongle.ui.motion.PetMotionEngine, dp: Float) {
    val cx = size.width / 2f
    val cy = size.height * 0.35f
    for (p in engine.particles) {
        val x = cx + p.x * dp
        val y = cy + p.y * dp
        val r = 6f * dp * p.scale
        val a = p.alpha
        when (p.kind) {
            app.mongle.ui.motion.ParticleKind.HEART -> {
                val c = Color(0xFFF07C8A).copy(alpha = a)
                drawCircle(c, r * 0.55f, androidx.compose.ui.geometry.Offset(x - r * 0.45f, y - r * 0.2f))
                drawCircle(c, r * 0.55f, androidx.compose.ui.geometry.Offset(x + r * 0.45f, y - r * 0.2f))
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(x - r, y); lineTo(x + r, y); lineTo(x, y + r * 1.1f); close()
                }
                drawPath(path, c)
            }
            app.mongle.ui.motion.ParticleKind.SPARKLE -> {
                val c = Color(0xFFFFD45C).copy(alpha = a)
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(x, y - r * 1.3f); lineTo(x + r * 0.3f, y - r * 0.3f); lineTo(x + r * 1.3f, y)
                    lineTo(x + r * 0.3f, y + r * 0.3f); lineTo(x, y + r * 1.3f); lineTo(x - r * 0.3f, y + r * 0.3f)
                    lineTo(x - r * 1.3f, y); lineTo(x - r * 0.3f, y - r * 0.3f); close()
                }
                drawPath(path, c)
            }
            app.mongle.ui.motion.ParticleKind.ZZZ -> {
                val c = Color(0xFF6E8CAE).copy(alpha = a)
                val w = r * 1.2f
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(x - w / 2, y - w / 2); lineTo(x + w / 2, y - w / 2); lineTo(x - w / 2, y + w / 2); lineTo(x + w / 2, y + w / 2)
                }
                drawPath(path, c, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.2f * dp))
            }
            app.mongle.ui.motion.ParticleKind.CRUMB -> {
                drawCircle(Color(0xFFC98B4E).copy(alpha = a), r * 0.35f, androidx.compose.ui.geometry.Offset(x, y + 30f * dp))
            }
        }
    }
}

@Composable
private fun PoseSprite(speciesId: String, stage: Stage, act: PetAct, blink: () -> Float) {
    val blinkId = petAssetId(speciesId, stage, PetAct.BLINK)
    val idleId = petAssetId(speciesId, stage, PetAct.IDLE)
    val canBlink = act == PetAct.IDLE && blinkId != idleId
    Image(
        painter = painterResource(Art.pet(speciesId, stage, act)),
        contentDescription = null,
        modifier = Modifier.fillMaxSize().graphicsLayer { alpha = if (canBlink) 1f - blink() else 1f },
        contentScale = ContentScale.Fit,
    )
    if (canBlink) {
        // Always composed; the eyelid frame fades in only through the layer alpha (no recomposition).
        Image(
            painter = painterResource(Art.drawable(blinkId)),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = blink() },
            contentScale = ContentScale.Fit,
        )
    }
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

