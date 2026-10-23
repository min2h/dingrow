package app.mongle

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import app.mongle.data.MemoryGameStore
import app.mongle.logic.LayoutEditor
import app.mongle.logic.RoomFloor
import app.mongle.logic.layoutOf
import app.mongle.service.GameService
import app.mongle.ui.RoomStage
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Bed, bookshelf, table, lamp, armchair and rug next to 몽이 at real-world scale (room + shop zoom). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w760dp-h480dp-hdpi")
class FurnitureScaleShotTest {
    @get:Rule val rule = createComposeRule()

    @Test fun furnishedRoom() {
        primeComposeResources()
        val s = GameService(MemoryGameStore(), MutableClock(System.currentTimeMillis()), TimeZone.of("Asia/Seoul")).also { it.giftChance = { false } }
        runBlocking { s.completeOnboarding("몽이") }
        val st = s.state.value
        val ed = LayoutEditor(layoutOf(emptyList(), null, st.activeRoom.wallpaperId))
        listOf("mint_rug", "wood_bed", "bookshelf", "round_table", "floor_lamp", "velvet_armchair").forEach { ed.placeFree(it) }
        val layout = ed.current
        layout.placements.forEach { p -> assertFalse("${p.defId} collides", RoomFloor.collides(p.defId, p.x, p.y, layout.placements.filterNot { it.defId == p.defId })) }
        println("placements: " + layout.placements.joinToString { "${it.defId}@${it.x},${it.y}" })
        rule.setContent {
            Row(Modifier.testTag("rooms").padding(8.dp)) {
                RoomStage(st, layout, Modifier.height(460.dp), petScale = 0.28f)
                RoomStage(st, layout, Modifier.height(460.dp).padding(start = 8.dp), petScale = 0.46f)
            }
        }
        rule.waitForIdle()
        val dir = File("/workspace/dingrow-combos").apply { mkdirs() }
        rule.onNodeWithTag("rooms").captureRoboImage(File(dir, "furniture-scale.png").path)
    }
}
