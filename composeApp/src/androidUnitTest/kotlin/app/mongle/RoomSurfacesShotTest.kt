package app.mongle

import androidx.compose.foundation.layout.Column
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
import app.mongle.logic.layoutOf
import app.mongle.model.GameState
import app.mongle.logic.Layout
import app.mongle.model.TimeOfDay
import app.mongle.service.GameService
import app.mongle.ui.RoomStage
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Wallpaper / floor surface items on every theme, day and night, plus wall-mounted items. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1080dp-h960dp-mdpi")
class RoomSurfacesShotTest {
    @get:Rule val rule = createComposeRule()
    private val themes = listOf("wood", "mint", "starry")

    private fun state(): GameState {
        primeComposeResources()
        val s = GameService(MemoryGameStore(), MutableClock(System.currentTimeMillis()), TimeZone.of("Asia/Seoul")).also { it.giftChance = { false } }
        runBlocking { s.completeOnboarding("몽이") }
        return s.state.value
    }

    private fun shot(name: String, st: GameState, layoutFor: (String) -> Layout) {
        rule.setContent {
            Column(Modifier.testTag("grid").padding(4.dp)) {
                listOf(TimeOfDay.DAY, TimeOfDay.NIGHT).forEach { tod ->
                    Row(Modifier.padding(2.dp)) {
                        themes.forEach { th -> RoomStage(st.copy(timeOfDay = tod), layoutFor(th), Modifier.height(460.dp).padding(2.dp)) }
                    }
                }
            }
        }
        rule.waitForIdle()
        val dir = File("/workspace/dingrow-combos").apply { mkdirs() }
        rule.onNodeWithTag("grid").captureRoboImage(File(dir, "$name.png").path)
    }

    private fun layout(theme: String, vararg ids: String): Layout {
        val ed = LayoutEditor(layoutOf(emptyList(), null, theme))
        ids.forEach { ed.placeFree(it) }
        return ed.current
    }

    @Test fun defaultRooms() { val st = state(); shot("surface-default", st) { layout(it) } }
    @Test fun wallStripe() { val st = state(); shot("surface-wall_stripe", st) { layout(it, "wall_stripe") } }
    @Test fun wallStarry() { val st = state(); shot("surface-wall_starry", st) { layout(it, "wall_starry") } }
    @Test fun floorHerringbone() { val st = state(); shot("surface-floor_herringbone", st) { layout(it, "floor_herringbone") } }
    @Test fun floorMintTile() { val st = state(); shot("surface-floor_mint_tile", st) { layout(it, "floor_mint_tile") } }
    @Test fun comboWithFurniture() {
        val st = state()
        shot("surface-combo", st) { layout(it, "wall_stripe", "floor_herringbone", "mint_rug", "wood_bed", "round_table", "floor_lamp", "picture_frame", "wall_clock") }
    }
    @Test fun wallItems() {
        val st = state()
        shot("surface-wall-items", st) { layout(it, "wall_starry", "floor_mint_tile", "window", "picture_frame", "wall_clock", "sun_poster", "ribbon_garland", "string_lights", "moon_mirror") }
    }
}
