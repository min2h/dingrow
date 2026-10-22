package app.mongle

import app.mongle.model.Stage
import app.mongle.ui.PetAct
import app.mongle.ui.SpeciesPoses
import app.mongle.ui.petAssetId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeciesPoseTest {
    @Test
    fun everySpeciesHasFiveInterimPoses() {
        assertEquals(250, SpeciesPoses.count)
        val species = listOf(
            "cat", "tiger", "whale", "sunflower", "sweetpotato", "peach", "egg",
        )
        species.forEach { id ->
            assertEquals("${id}_blink", petAssetId(id, Stage.BABY, PetAct.BLINK))
            assertEquals("${id}_happy", petAssetId(id, Stage.BABY, PetAct.HAPPY))
            assertEquals("${id}_sleep", petAssetId(id, Stage.CHILD, PetAct.SLEEP))
            assertEquals("${id}_eat", petAssetId(id, Stage.ADULT, PetAct.EAT))
            assertEquals("${id}_walk_left", petAssetId(id, Stage.BABY, PetAct.WALK))
            assertEquals(id, petAssetId(id, Stage.BABY, PetAct.IDLE))
            assertNotNull(SpeciesPoses.drawable("${id}_sleep"))
        }
        assertEquals("egg", petAssetId("cat", Stage.EGG, PetAct.WALK))
        assertEquals("base_sleep", petAssetId("base", Stage.BABY, PetAct.SLEEP))
        assertEquals("base_walk_left", petAssetId("sp_peach", Stage.BABY, PetAct.WALK))
        assertTrue(SpeciesPoses.mirrorSafeWalk("tiger"))
        assertTrue(SpeciesPoses.mirrorSafeWalk("whale"))
        assertTrue(SpeciesPoses.mirrorSafeWalk("sunflower"))
        assertFalse(SpeciesPoses.mirrorSafeWalk("cat"))
        assertTrue(SpeciesPoses.weakWalk("sweetpotato"))
        assertFalse(SpeciesPoses.weakWalk("tiger"))
    }
}
