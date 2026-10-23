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
import app.mongle.model.Catalog
import app.mongle.model.ItemKind
import app.mongle.model.Stage
import app.mongle.ui.PetAct
import app.mongle.ui.PetFigure
import app.mongle.ui.WearFit
import app.mongle.ui.WornArt
import app.mongle.ui.petAssetId
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Renders 몽이 wearing every outfit at each stage/pose for the wear contact sheet (/workspace/dingrow-wear). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1100dp-h400dp-mdpi")
class WearSheetTest {
    @get:Rule val rule = createComposeRule()

    private val cells = listOf(Stage.EGG to PetAct.EGG, Stage.BABY to PetAct.IDLE, Stage.CHILD to PetAct.HAPPY, Stage.ADULT to PetAct.IDLE, Stage.ADULT to PetAct.SLEEP, Stage.ADULT to PetAct.WALK)

    @Test fun everyOutfitIsDefinedAndFitted() {
        val outfits = Catalog.all.filter { it.kind == ItemKind.OUTFIT }
        assertTrue(outfits.size >= 21)
        outfits.forEach { assertTrue("worn art for ${it.id}", WornArt.known(it.id)) }
        // Every pose sprite has measured geometry, and hats always stay above the eyes.
        cells.forEach { (s, a) -> assertTrue(WearFit.has(petAssetId("base", s, a))) }
        WearFit.ids.forEach { id -> val f = WearFit.of(id); assertTrue("$id eyes below head top", f.eyeY > f.headTop + 0.08f && f.faceBottom > f.eyeY) }
    }

    @Test fun renderWearSheet() {
        primeComposeResources()
        val dir = File(System.getProperty("dingrow.wear") ?: "/workspace/dingrow-wear").apply { mkdirs() }
        val outfit = mutableStateOf<String?>(null)
        val species = mutableStateOf("base")
        rule.mainClock.autoAdvance = false
        rule.setContent {
            Row(Modifier.background(Color(0xFFFFF6EC)).testTag("sheet")) {
                cells.forEach { (s, a) ->
                    Box(Modifier.size(180.dp)) {
                        PetFigure(species.value, s, a, outfit.value, Modifier.size(180.dp), animate = false, breath = 0.5f, walk = 0f)
                    }
                }
            }
        }
        val ids = listOf<String?>(null) + Catalog.all.filter { it.kind == ItemKind.OUTFIT }.map { it.id }
        for (sp in listOf("base", "cat")) {
            species.value = sp
            ids.forEach { id ->
                outfit.value = id
                repeat(3) { rule.mainClock.advanceTimeBy(100); rule.waitForIdle() }
                rule.onNodeWithTag("sheet").captureRoboImage(File(dir, "row-$sp-${id ?: "none"}.png").path)
            }
        }
    }
}
