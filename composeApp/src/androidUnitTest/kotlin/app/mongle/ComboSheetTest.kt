package app.mongle

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import app.mongle.model.Stage
import app.mongle.ui.PetAct
import app.mongle.ui.PetFigure
import app.mongle.ui.motion.PetMotionEngine
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Multi-slot outfit combos and animation frame strips of a fully equipped 몽이 (/workspace/dingrow-combos). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1100dp-h400dp-mdpi")
class ComboSheetTest {
    @get:Rule val rule = createComposeRule()
    private val dir = File(System.getProperty("dingrow.combos") ?: "/workspace/dingrow-combos").apply { mkdirs() }

    companion object {
        const val FULL = "rain_boots,raincoat,tiny_backpack,knit_scarf,sunglasses,crown"
        val combos = listOf(
            "raincoat,sunglasses", "hoodie,knit_scarf,crown", "raincoat,crown", "hoodie,bow_tie",
            "overalls,sailor_collar,sunglasses,beanie", "cardigan,pearl_necklace,flower_crown,cozy_socks",
            "rain_boots,raincoat,tiny_backpack,knit_scarf,sunglasses", FULL,
        )
        val cells = listOf(Stage.BABY to PetAct.IDLE, Stage.CHILD to PetAct.HAPPY, Stage.ADULT to PetAct.IDLE, Stage.ADULT to PetAct.SLEEP, Stage.ADULT to PetAct.WALK, Stage.ADULT to PetAct.EAT)
    }

    @Test fun renderCombos() {
        primeComposeResources()
        val outfit = mutableStateOf<String?>(null)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            Row(Modifier.background(Color(0xFFFFF6EC)).testTag("sheet")) {
                cells.forEach { (s, a) -> Box(Modifier.size(180.dp)) { PetFigure("base", s, a, outfit.value, Modifier.size(180.dp), animate = false, breath = 0.5f, walk = 0.3f) } }
            }
        }
        combos.forEachIndexed { i, c ->
            outfit.value = c
            repeat(3) { rule.mainClock.advanceTimeBy(100); rule.waitForIdle() }
            rule.onNodeWithTag("sheet").captureRoboImage(File(dir, "combo-$i.png").path)
        }
    }

    /** Live motion engine (bounce/squash, idle, blink, walk) at every growth stage, all slots filled. */
    private fun strip(name: String, act: PetAct, frames: Int, stepMs: Long, kick: (PetMotionEngine) -> Unit) {
        primeComposeResources()
        val stages = Stage.entries
        val engines = stages.map { PetMotionEngine(42) }
        rule.mainClock.autoAdvance = false
        rule.setContent {
            Row(Modifier.background(Color(0xFFFFF6EC)).testTag("strip")) {
                stages.forEachIndexed { i, s ->
                    Box(Modifier.size(200.dp)) {
                        PetFigure("base", s, if (s == Stage.EGG) PetAct.EGG else act, FULL, Modifier.size(200.dp), animate = true, breath = 0.5f, walk = 0f, hoisted = engines[i])
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(500); rule.waitForIdle()
        engines.forEach(kick)
        repeat(frames) { f ->
            rule.mainClock.advanceTimeBy(stepMs); rule.waitForIdle()
            rule.onNodeWithTag("strip").captureRoboImage(File(dir, "frames/$name-%02d.png".format(f)).path)
        }
    }

    @Test fun bounceStrip() = strip("bounce", PetAct.IDLE, 14, 70) { it.trigger(PetMotionEngine.shapeFor("pat")) }
    @Test fun idleStrip() = strip("idle", PetAct.IDLE, 16, 250) { }
    @Test fun walkStrip() = strip("walk", PetAct.WALK, 12, 90) { }
}
