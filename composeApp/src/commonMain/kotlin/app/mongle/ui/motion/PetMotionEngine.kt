package app.mongle.ui.motion

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Damped harmonic spring integrated with fixed sub-steps (semi-implicit Euler), so the result
 * does not depend on the frame rate. dampingRatio < 1 overshoots (follow-through), 1 is critical.
 */
class Spring(var value: Float, var stiffness: Float, var dampingRatio: Float) {
    var velocity = 0f
    var target = value

    fun step(dt: Float) {
        var left = dt
        val damping = 2f * dampingRatio * sqrt(stiffness)
        while (left > 0f) {
            val h = min(left, 1f / 240f)
            val accel = stiffness * (target - value) - damping * velocity
            velocity += accel * h
            value += velocity * h
            left -= h
        }
    }

    fun impulse(v: Float) { velocity += v }
    val atRest: Boolean get() = abs(target - value) < 0.002f && abs(velocity) < 0.01f
}

enum class MotionPhase { IDLE, ANTICIPATION, ACTION, FOLLOW_THROUGH, SETTLE }

/** One reaction gesture expressed as targets: stretch (>0 tall, <0 wide), lift in dp (up negative), tilt in degrees. */
data class ReactionShape(val stretch: Float, val liftDp: Float, val tiltDeg: Float, val repeats: Int = 1, val particle: ParticleKind? = null)

enum class ParticleKind { HEART, SPARKLE, ZZZ, CRUMB }

class Particle(val kind: ParticleKind, var x: Float, var y: Float, var vx: Float, var vy: Float, val life: Float, val spin: Float) {
    var age = 0f
    val progress: Float get() = (age / life).coerceIn(0f, 1f)
    /** Ease-out pop in, ease-in fade out. */
    val alpha: Float get() {
        val p = progress
        return if (p < 0.15f) easeOutCubic(p / 0.15f) else 1f - easeInQuad((p - 0.15f) / 0.85f)
    }
    val scale: Float get() = 0.4f + 0.6f * easeOutBack(min(1f, progress / 0.25f))
}

fun easeOutCubic(t: Float): Float { val u = 1f - t; return 1f - u * u * u }
fun easeInQuad(t: Float): Float = t * t
fun easeOutBack(t: Float): Float { val c1 = 1.70158f; val c3 = c1 + 1f; val u = t - 1f; return 1f + c3 * u * u * u + c1 * u * u }
fun easeInOutSine(t: Float): Float = (-(kotlin.math.cos(PI * t) - 1) / 2).toFloat()

/**
 * Pure, frame-clock-driven character motion. The composable calls [step] from withFrameNanos and
 * reads the output fields only inside graphicsLayer / draw lambdas, so nothing recomposes per frame.
 *
 * Every reaction runs anticipation -> action -> follow-through (under-damped release) -> settle.
 * A new trigger interrupts from the springs' current values and velocities, so nothing snaps.
 */
class PetMotionEngine(seed: Int = 7) {
    private val rng = Random(seed)

    // Body springs.
    val stretch = Spring(0f, 260f, 0.42f)      // squash/stretch, volume preserving
    val lift = Spring(0f, 170f, 0.55f)         // dp, negative is up
    val tilt = Spring(0f, 150f, 0.45f)         // degrees
    val facing = Spring(1f, 60f, 0.9f)         // eased scaleX flip, -1..1
    val walkX = Spring(0f, 9f, 1f)             // dp offset while wandering
    val accessory = Spring(0f, 120f, 0.28f)    // lags the body and wobbles
    val shadow = Spring(1f, 90f, 0.8f)         // lags lift
    val poseBlend = Spring(0f, 200f, 0.5f)     // small squash pulse during a pose crossfade

    var phase = MotionPhase.IDLE; private set
    var current: ReactionShape? = null; private set
    private var phaseTime = 0f
    private var repeatsLeft = 0

    var time = 0f; private set
    var walking = false
    var sleeping = false
    /** Half-width (dp) of the free-roam area; 0 disables wandering around the room. */
    var roamDp = 0f
    /** Cold: fast small shiver. */
    var shiver = false
    /** True while 몽이 is strolling during free roam (use the walk pose). */
    var roamMoving = false; private set
    private var roamPauseUntil = 0f
    private var roamTarget = 0f
    /** Called at a roam stop when a self-talk line is due (once per 2–6 min, see [WanderTimer]). */
    var onRoamRest: (() -> Unit)? = null
    /** Roam timing (pauses, window gazes, sit-downs, self-talk cadence). */
    val wander = WanderTimer(seed)
    /** Current roam stop kind while paused (null while walking or not roaming). */
    var roamStop: RoamStop? = null; private set

    // Blink: randomized interval, sometimes a double blink.
    var blink = 0f; private set
    private var nextBlinkAt = 2f + rng.nextFloat() * 3f
    private var blinkStart = -10f
    private var doubleBlink = false

    private var wanderTarget = 0f
    private var nextWanderAt = 0f

    val particles = ArrayList<Particle>()

    /** Final pose output, read in the draw phase. */
    val scaleX: Float get() = facingSign() * bodyScaleX()
    val scaleY: Float get() = bodyScaleY()
    val rotation: Float get() = tilt.value + swayDeg()
    val translationYdp: Float get() = lift.value + breathLiftDp() + walkBobDp()
    val translationXdp: Float get() = walkX.value
    val shadowScale: Float get() = shadow.value * (1f + 0.04f * breath())
    val shadowAlpha: Float get() = (0.22f * shadow.value).coerceIn(0.05f, 0.3f)
    val accessoryYdp: Float get() = accessory.value

    fun bodyScaleY(): Float = (1f + stretch.value + poseBlend.value * -0.08f) * (1f + 0.025f * breath()) * if (sleeping) 0.95f else 1f
    /** Width shrinks as height grows, keeping area constant. */
    fun bodyScaleX(): Float = 1f / bodyScaleY()

    private fun facingSign(): Float = facing.value.coerceIn(-1f, 1f).let { if (abs(it) < 0.08f) 0.08f * (if (it < 0) -1 else 1) else it }

    fun breath(): Float {
        val period = if (sleeping) 3.4f else 2.6f
        return sin(2 * PI * time / period).toFloat()
    }
    private fun breathLiftDp(): Float = -1.2f * (breath() * 0.5f + 0.5f)
    private fun swayDeg(): Float = (if (phase == MotionPhase.IDLE && !walking && !roamMoving) 1.6f * sin(2 * PI * time / 4.3f).toFloat() * if (sleeping) 0.4f else 1f else 0f) +
        if (shiver && !sleeping) 1.8f * sin(2 * PI * time * 9f).toFloat() else 0f
    private var stepPhase = 0f
    private fun walkBobDp(): Float = if (walking || roamMoving) -4.5f * abs(sin(stepPhase)) else 0f

    fun trigger(shape: ReactionShape) {
        current = shape
        repeatsLeft = max(1, shape.repeats)
        enter(MotionPhase.ANTICIPATION)
        // Anticipation: crouch opposite to the coming action, from wherever the body is now.
        stretch.target = if (shape.stretch >= 0f) -0.12f else 0.06f
        lift.target = 2f
        tilt.target = -shape.tiltDeg * 0.25f
        stretch.dampingRatio = 0.7f
    }

    fun pulsePose() {
        poseBlend.target = 0f
        poseBlend.impulse(9f)
    }

    private fun enter(next: MotionPhase) {
        phase = next
        phaseTime = 0f
    }

    fun step(dtRaw: Float) {
        val dt = dtRaw.coerceIn(0f, 1f / 20f) // avoid explosions after a hitch
        time += dt
        phaseTime += dt
        val shape = current
        when (phase) {
            MotionPhase.IDLE -> Unit
            MotionPhase.ANTICIPATION -> if (phaseTime >= 0.09f && shape != null) {
                enter(MotionPhase.ACTION)
                stretch.dampingRatio = 0.42f
                stretch.target = shape.stretch
                tilt.target = shape.tiltDeg
                lift.target = shape.liftDp
                if (shape.liftDp < 0f) lift.impulse(shape.liftDp * 9f)
                shape.particle?.let { burst(it, 3 + rng.nextInt(3)) }
            }
            MotionPhase.ACTION -> if (phaseTime >= 0.16f) {
                enter(MotionPhase.FOLLOW_THROUGH)
                // Release: springs overshoot past rest. Landing squash if we were airborne.
                val wasUp = lift.value < -3f
                stretch.target = 0f
                tilt.target = 0f
                lift.target = 0f
                if (wasUp) stretch.impulse(-2.6f)
            }
            MotionPhase.FOLLOW_THROUGH -> if (phaseTime >= 0.22f) {
                repeatsLeft -= 1
                if (repeatsLeft > 0 && shape != null) {
                    enter(MotionPhase.ACTION)
                    stretch.target = shape.stretch * 0.8f
                    tilt.target = shape.tiltDeg * if (repeatsLeft % 2 == 0) 1f else -1f
                    lift.target = shape.liftDp * 0.7f
                } else enter(MotionPhase.SETTLE)
            }
            MotionPhase.SETTLE -> if (stretch.atRest && lift.atRest && tilt.atRest) {
                current = null
                enter(MotionPhase.IDLE)
            }
        }
        stepBlink()
        stepWander(dt)
        if (sleeping && particles.count { it.kind == ParticleKind.ZZZ } < 1 && rng.nextFloat() < dt * 0.6f) burst(ParticleKind.ZZZ, 1)

        stretch.step(dt); lift.step(dt); tilt.step(dt); facing.step(dt); walkX.step(dt); poseBlend.step(dt)
        // Secondary motion: accessory trails body velocity; shadow shrinks as the body rises.
        accessory.target = 0f
        accessory.impulse(-lift.velocity * dt * 6f)
        accessory.step(dt)
        shadow.target = (1f + lift.value / 60f).coerceIn(0.55f, 1.05f)
        shadow.step(dt)
        stepParticles(dt)
    }

    private fun stepBlink() {
        if (sleeping) { blink = 1f; return }
        if (time >= nextBlinkAt) {
            blinkStart = time
            doubleBlink = rng.nextFloat() < 0.18f
            nextBlinkAt = time + 2.2f + rng.nextFloat() * 4.2f
        }
        val d = time - blinkStart
        val one = 0.16f
        blink = when {
            d < one -> sin(PI * d / one).toFloat()
            doubleBlink && d in 0.24f..(0.24f + one) -> sin(PI * (d - 0.24f) / one).toFloat()
            else -> 0f
        }.coerceIn(0f, 1f)
    }

    private fun stepWander(dt: Float) {
        if (!walking && roamDp > 0f && !sleeping) { stepRoam(dt); return }
        roamMoving = false
        walkX.stiffness = 9f
        if (!walking) { walkX.target = 0f; return }
        if (time >= nextWanderAt) {
            wanderTarget = (rng.nextFloat() * 2f - 1f) * 22f
            nextWanderAt = time + 2.5f + rng.nextFloat() * 2.5f
        }
        walkX.target = wanderTarget
        val v = walkX.velocity
        if (abs(v) > 0.6f) facing.target = if (v > 0) 1f else -1f
        stepPhase += dt * (4f + min(abs(v), 20f) * 0.25f)
    }

    /** Stroll to a random spot, stop (pause / look / sit per [wander]), repeat. Speed is eased by the walkX spring. */
    private fun stepRoam(dt: Float) {
        walkX.stiffness = 4f
        if (roamMoving) {
            val v = walkX.velocity
            if (abs(v) > 0.6f) facing.target = if (v > 0) 1f else -1f
            stepPhase += dt * (4f + min(abs(v), 20f) * 0.25f)
            if (abs(walkX.value - roamTarget) < 1.5f && abs(v) < 2f) {
                roamMoving = false
                val stop = wander.nextStop()
                roamStop = stop
                roamPauseUntil = time + wander.pauseSeconds(stop)
                if (stop == RoamStop.LOOK) facing.target = if (walkX.value > 0) 1f else -1f
                if (wander.talkDue(time)) onRoamRest?.invoke()
            }
        } else if (time >= roamPauseUntil && phase == MotionPhase.IDLE) {
            var next = (rng.nextFloat() * 2f - 1f) * roamDp
            if (abs(next - walkX.value) < roamDp * 0.3f) next = -next
            roamTarget = next
            walkX.target = next
            roamMoving = true
            roamStop = null
        }
    }

    fun burst(kind: ParticleKind, count: Int) {
        repeat(count) {
            val life = when (kind) { ParticleKind.ZZZ -> 2.4f; ParticleKind.CRUMB -> 0.7f; else -> 1.2f }
            particles += Particle(
                kind,
                x = (rng.nextFloat() - 0.5f) * 30f,
                y = -40f,
                vx = (rng.nextFloat() - 0.5f) * if (kind == ParticleKind.CRUMB) 70f else 26f,
                vy = when (kind) { ParticleKind.CRUMB -> -40f; ParticleKind.ZZZ -> -14f; else -> -36f - rng.nextFloat() * 20f },
                life = life,
                spin = (rng.nextFloat() - 0.5f) * 90f,
            )
        }
        while (particles.size > 24) particles.removeAt(0)
    }

    private fun stepParticles(dt: Float) {
        val it = particles.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.age += dt
            if (p.age >= p.life) { it.remove(); continue }
            val drag = exp(-1.6f * dt)
            p.vx *= drag
            if (p.kind == ParticleKind.CRUMB) p.vy += 220f * dt else p.vy *= drag
            if (p.kind == ParticleKind.ZZZ) p.x += sin(p.age * 3f) * 6f * dt
            p.x += p.vx * dt
            p.y += p.vy * dt
        }
    }

    companion object {
        private val named = mapOf(
            "bounce" to ReactionShape(-0.22f, 0f, 0f),
            "squish" to ReactionShape(-0.3f, 3f, 0f),
            "spin" to ReactionShape(-0.1f, -6f, 22f, repeats = 2, particle = ParticleKind.SPARKLE),
            "jump" to ReactionShape(0.24f, -26f, 0f),
            "heart" to ReactionShape(0.12f, -10f, 0f, particle = ParticleKind.HEART),
            "blush" to ReactionShape(-0.1f, 2f, -6f, particle = ParticleKind.HEART),
            "yawn" to ReactionShape(0.26f, 0f, 0f),
            "surprise" to ReactionShape(0.3f, -12f, 0f, particle = ParticleKind.SPARKLE),
            "dance" to ReactionShape(-0.14f, -4f, -16f, repeats = 3),
            "bow" to ReactionShape(-0.18f, 4f, 20f),
            "pat" to ReactionShape(-0.18f, 4f, 0f, particle = ParticleKind.HEART),
            "tickle" to ReactionShape(-0.2f, 0f, 9f, repeats = 3),
            "eat" to ReactionShape(-0.12f, 2f, 4f, repeats = 2, particle = ParticleKind.CRUMB),
            "legend_sparkle" to ReactionShape(0.2f, -20f, 12f, repeats = 2, particle = ParticleKind.SPARKLE),
        )
        private val pool = named.values.toList()

        /** Every reaction id maps to a shape; ids without their own entry get a stable pick. */
        fun shapeFor(id: String): ReactionShape {
            named[id]?.let { return it }
            named.entries.firstOrNull { id.contains(it.key) }?.let { return it.value }
            return pool[(id.hashCode() and 0x7fffffff) % pool.size]
        }
    }
}
