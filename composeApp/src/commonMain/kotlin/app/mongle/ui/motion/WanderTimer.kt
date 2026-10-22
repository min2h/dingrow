package app.mongle.ui.motion

import kotlin.random.Random

/** What 몽이 does at a roam stop (sheet 7): short look-around, gaze at the window, sit and rest. */
enum class RoamStop { PAUSE, LOOK, SIT }

/**
 * Timing for free roam, kept pure so it can be unit-tested:
 *  - short pauses 0.8–3 s between strolls, sometimes a window gaze (4–6 s) or a sit-down rest (8–14 s);
 *  - self-talk at most once per 2–6 min (re-rolled after each line), never twice in a row from the same stop;
 *  - [frequency] 0 = rare (×1.6 interval), 1 = normal, 2 = chatty (×0.6).
 */
class WanderTimer(seed: Int = 7, var frequency: Int = 1) {
    private val rng = Random(seed)
    private var nextTalkAt = rollTalk(0f)

    private fun scale() = when (frequency) { 0 -> 1.6f; 2 -> 0.6f; else -> 1f }

    private fun rollTalk(now: Float) = now + (120f + rng.nextFloat() * 240f) * scale()

    fun nextStop(): RoamStop {
        val r = rng.nextFloat()
        return when {
            r < 0.62f -> RoamStop.PAUSE
            r < 0.85f -> RoamStop.LOOK
            else -> RoamStop.SIT
        }
    }

    fun pauseSeconds(stop: RoamStop): Float = when (stop) {
        RoamStop.PAUSE -> 0.8f + rng.nextFloat() * 2.2f
        RoamStop.LOOK -> 4f + rng.nextFloat() * 2f
        RoamStop.SIT -> 8f + rng.nextFloat() * 6f
    }

    /** True when a self-talk line is due at [now] (seconds); schedules the next one. */
    fun talkDue(now: Float): Boolean {
        if (now < nextTalkAt) return false
        nextTalkAt = rollTalk(now)
        return true
    }

    /** Seconds until the next line may appear (for tests/diagnostics). */
    fun talkIn(now: Float): Float = (nextTalkAt - now).coerceAtLeast(0f)
}
