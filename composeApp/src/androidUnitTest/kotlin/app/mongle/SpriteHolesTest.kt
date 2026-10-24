package app.mongle

import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** No item sprite may have transparent holes that aren't connected to its border (tools/sprite_holes.py). */
class SpriteHolesTest {
    @Test fun noInteriorTransparentHoles() {
        val script = generateSequence(File("").absoluteFile) { it.parentFile }.map { File(it, "tools/sprite_holes.py") }.firstOrNull { it.exists() }
        assumeTrue("sprite_holes.py not found", script != null)
        val ok = runCatching { ProcessBuilder("python3", "-c", "import numpy, scipy, PIL").start().waitFor() == 0 }.getOrDefault(false)
        assumeTrue("python3 with numpy/scipy/PIL not available", ok)
        val p = ProcessBuilder("python3", script!!.path).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        assertEquals("sprites with interior holes:\n$out", 0, p.waitFor())
    }
}
