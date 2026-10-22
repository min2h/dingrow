package app.mongle

import app.mongle.ui.motion.PetMotionEngine
import app.mongle.ui.motion.RoamStop
import app.mongle.ui.motion.WanderTimer
import org.junit.Assert.assertTrue
import org.junit.Test

class WanderTimerTest {
    @Test fun pausesWithinSheet7Ranges() {
        val w = WanderTimer(3)
        repeat(300) {
            val stop = w.nextStop()
            val p = w.pauseSeconds(stop)
            when (stop) {
                RoamStop.PAUSE -> assertTrue(p in 0.8f..3f)
                RoamStop.LOOK -> assertTrue(p in 4f..6f)
                RoamStop.SIT -> assertTrue(p in 8f..14f)
            }
        }
    }

    @Test fun allStopKindsHappen() {
        val w = WanderTimer(5)
        val kinds = (1..200).map { w.nextStop() }.toSet()
        assertTrue(kinds.size == 3)
    }

    @Test fun selfTalkEveryTwoToSixMinutes() {
        val w = WanderTimer(9)
        var last = 0f; var t = 0f; val gaps = mutableListOf<Float>()
        while (t < 3600f) { if (w.talkDue(t)) { gaps += t - last; last = t }; t += 1f }
        assertTrue(gaps.size in 9..30)
        assertTrue(gaps.all { it in 119f..361f })
    }

    @Test fun frequencyScalesCadence() {
        fun count(f: Int): Int { val w = WanderTimer(11, f); var n = 0; var t = 0f; while (t < 7200f) { if (w.talkDue(t)) n++; t += 1f }; return n }
        assertTrue(count(2) > count(1)); assertTrue(count(1) > count(0))
    }

    @Test fun engineRoamsButTalksRarely() {
        val e = PetMotionEngine(4)
        e.roamDp = 100f
        var talks = 0; var moves = 0; var wasMoving = false
        e.onRoamRest = { talks++ }
        repeat(20 * 60 * 10) { // 20 min at 10 Hz
            e.step(0.1f)
            if (e.roamMoving && !wasMoving) moves++
            wasMoving = e.roamMoving
        }
        assertTrue("strolls: $moves", moves >= 40)
        assertTrue("talks: $talks", talks in 2..10)
    }

    @Test fun wanderOffStaysPut() {
        val e = PetMotionEngine(4)
        e.roamDp = 0f
        repeat(600) { e.step(0.1f) }
        assertTrue(!e.roamMoving)
    }
}
