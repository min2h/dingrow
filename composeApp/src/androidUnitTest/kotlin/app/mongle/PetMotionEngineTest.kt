package app.mongle

import app.mongle.ui.motion.MotionPhase
import app.mongle.ui.motion.ParticleKind
import app.mongle.ui.motion.PetMotionEngine
import app.mongle.ui.motion.ReactionShape
import app.mongle.ui.motion.Spring
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PetMotionEngineTest {
    private fun PetMotionEngine.run(seconds: Float, fps: Int = 60, each: (PetMotionEngine) -> Unit = {}) {
        repeat((seconds * fps).toInt()) { step(1f / fps); each(this) }
    }

    @Test
    fun reactionGoesThroughAllPhasesAndSettles() {
        val e = PetMotionEngine(1)
        val seen = mutableListOf<MotionPhase>()
        e.trigger(PetMotionEngine.shapeFor("jump"))
        e.run(3f) { if (seen.lastOrNull() != it.phase) seen += it.phase }
        assertEquals(
            listOf(MotionPhase.ANTICIPATION, MotionPhase.ACTION, MotionPhase.FOLLOW_THROUGH, MotionPhase.SETTLE, MotionPhase.IDLE),
            seen,
        )
        assertTrue(abs(e.lift.value) < 0.05f && abs(e.stretch.value) < 0.01f)
    }

    @Test
    fun anticipationCrouchesBeforeJumpAndLandingOvershoots() {
        val e = PetMotionEngine(2)
        e.trigger(PetMotionEngine.shapeFor("jump"))
        var minStretch = 0f
        var maxStretch = 0f
        var minLift = 0f
        e.run(0.09f) { minStretch = minOf(minStretch, it.stretch.value) }
        assertTrue("crouch first", minStretch < -0.03f)
        e.run(1.5f) {
            maxStretch = maxOf(maxStretch, it.stretch.value)
            minLift = minOf(minLift, it.lift.value)
            if (it.phase == MotionPhase.SETTLE) minStretch = minOf(minStretch, it.stretch.value)
        }
        assertTrue("stretch up in action", maxStretch > 0.1f)
        assertTrue("left the ground", minLift < -10f)
    }

    @Test
    fun squashPreservesVolume() {
        val e = PetMotionEngine(3)
        e.trigger(PetMotionEngine.shapeFor("squish"))
        e.run(1f) { assertEquals(1f, it.bodyScaleX() * it.bodyScaleY(), 1e-4f) }
    }

    @Test
    fun interruptBlendsFromCurrentValuesWithoutSnapping() {
        val e = PetMotionEngine(4)
        e.trigger(PetMotionEngine.shapeFor("jump"))
        e.run(0.2f)
        var prevY = e.translationYdp
        var prevSx = e.bodyScaleX()
        e.trigger(PetMotionEngine.shapeFor("spin"))
        e.run(1.2f) {
            // No frame-to-frame jump bigger than what springs can physically do at 60fps.
            assertTrue("y jump ${it.translationYdp - prevY}", abs(it.translationYdp - prevY) < 4f)
            assertTrue("sx jump", abs(it.bodyScaleX() - prevSx) < 0.08f)
            prevY = it.translationYdp
            prevSx = it.bodyScaleX()
        }
    }

    @Test
    fun frameRateIndependent() {
        val a = PetMotionEngine(5)
        val b = PetMotionEngine(5)
        val shape = ReactionShape(0.2f, -20f, 10f)
        a.trigger(shape); b.trigger(shape)
        a.run(0.5f, fps = 60); b.run(0.5f, fps = 120)
        assertEquals(a.lift.value, b.lift.value, 1.5f)
    }

    @Test
    fun underDampedSpringOvershootsCriticalDoesNot() {
        val under = Spring(0f, 200f, 0.4f).apply { target = 1f }
        val critical = Spring(0f, 200f, 1f).apply { target = 1f }
        var maxU = 0f; var maxC = 0f
        repeat(120) { under.step(1 / 60f); critical.step(1 / 60f); maxU = maxOf(maxU, under.value); maxC = maxOf(maxC, critical.value) }
        assertTrue(maxU > 1.05f)
        assertTrue(maxC <= 1.001f)
    }

    @Test
    fun blinksAtRandomizedIntervalsAndSleepsClosed() {
        val e = PetMotionEngine(6)
        val starts = mutableListOf<Float>()
        var was = false
        e.run(30f) {
            val on = it.blink > 0.5f
            if (on && !was) starts += it.time
            was = on
        }
        assertTrue("blinks ${starts.size}", starts.size in 4..16)
        val gaps = starts.zipWithNext { x, y -> y - x }.filter { it > 0.5f }
        assertTrue("randomized", gaps.max() - gaps.min() > 0.5f)
        e.sleeping = true
        e.step(1 / 60f)
        assertEquals(1f, e.blink, 0f)
    }

    @Test
    fun walkingFlipsFacingSmoothlyAndBobs() {
        val e = PetMotionEngine(7)
        e.walking = true
        var prev = e.facing.value
        var sawLeft = false; var sawRight = false; var bob = 0f
        e.run(20f) {
            assertTrue(abs(it.facing.value - prev) < 0.2f)
            prev = it.facing.value
            if (it.facing.value < -0.9f) sawLeft = true
            if (it.facing.value > 0.9f) sawRight = true
            bob = minOf(bob, it.translationYdp)
        }
        assertTrue(sawLeft && sawRight)
        assertTrue(bob < -3f)
    }

    @Test
    fun particlesFadeAndExpire() {
        val e = PetMotionEngine(8)
        e.burst(ParticleKind.HEART, 4)
        e.run(0.05f)
        assertTrue(e.particles.all { it.alpha in 0f..1f })
        e.run(0.6f)
        assertTrue(e.particles.isNotEmpty())
        e.run(1f)
        assertTrue(e.particles.isEmpty())
    }

    @Test
    fun everyReactionIdHasAShapeThatMoves() {
        listOf("bounce", "tickle", "react_unknown_42", "legend_sparkle", "wave").forEach { id ->
            val e = PetMotionEngine(9)
            e.trigger(PetMotionEngine.shapeFor(id))
            var peak = 0f
            e.run(0.4f) { peak = maxOf(peak, abs(it.stretch.value) + abs(it.lift.value) / 30f) }
            assertTrue(id, peak > 0.05f)
        }
    }

    @Test
    fun hugeFrameHitchDoesNotExplode() {
        val e = PetMotionEngine(10)
        e.trigger(PetMotionEngine.shapeFor("jump"))
        e.step(2f)
        assertTrue(e.lift.value.isFinite() && abs(e.lift.value) < 60f)
    }
}
