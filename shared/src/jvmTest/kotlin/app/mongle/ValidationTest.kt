package app.mongle

import app.mongle.data.MemoryGameStore
import app.mongle.data.Seed
import app.mongle.logic.DraftValidator
import app.mongle.logic.ScheduleDraft
import app.mongle.model.EndKind
import app.mongle.model.RepeatRule
import app.mongle.service.GameService
import app.mongle.service.TimeProvider
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import kotlin.test.*

class ValidationTest {
    private val t0 = 1_792_000_000_000
    private fun d(vararg mods: (ScheduleDraft) -> ScheduleDraft) = mods.fold(ScheduleDraft("운동", t0 + 3_600_000, RepeatRule.NONE)) { a, f -> f(a) }

    @Test fun eachRule() {
        assertTrue(DraftValidator.errors(d()).isEmpty())
        assertEquals("err_title_required", DraftValidator.errors(d({ it.copy(title = "  ") }))["title"])
        assertEquals("err_title_long", DraftValidator.errors(d({ it.copy(title = "가".repeat(41)) }))["title"])
        assertEquals("err_end_before", DraftValidator.errors(d({ it.copy(endMillis = it.timeMillis) }))["end"])
        assertNull(DraftValidator.errors(d({ it.copy(endMillis = it.timeMillis + 60_000) }))["end"])
        assertEquals("err_weekdays", DraftValidator.errors(d({ it.copy(repeat = RepeatRule.WEEKLY, weekdays = 0) }), strictWeekdays = true)["weekdays"])
        assertNull(DraftValidator.errors(d({ it.copy(repeat = RepeatRule.WEEKLY, weekdays = 0b10) }))["weekdays"])
        assertEquals("err_interval", DraftValidator.errors(d({ it.copy(repeat = RepeatRule.EVERY_N_DAYS, interval = 0) }))["interval"])
        assertEquals("err_count", DraftValidator.errors(d({ it.copy(repeat = RepeatRule.DAILY, endKind = EndKind.COUNT, endCount = 0) }))["count"])
        assertEquals("err_until_before", DraftValidator.errors(d({ it.copy(repeat = RepeatRule.DAILY, endKind = EndKind.UNTIL, endAt = it.timeMillis - 1) }))["until"])
        assertEquals("err_place_long", DraftValidator.errors(d({ it.copy(place = "x".repeat(41)) }))["place"])
        assertEquals("err_memo_long", DraftValidator.errors(d({ it.copy(memo = "x".repeat(201)) }))["memo"])
        assertEquals("warn_past", DraftValidator.warnings(d({ it.copy(timeMillis = t0 - 1) }), t0)["time"])
        assertTrue(DraftValidator.warnings(d({ it.copy(timeMillis = t0 - 1, repeat = RepeatRule.DAILY) }), t0).isEmpty())
        assertEquals("err_name_required", DraftValidator.petNameError(" "))
        assertEquals("err_name_long", DraftValidator.petNameError("가".repeat(13)))
        assertNull(DraftValidator.petNameError("몽이"))
    }

    @Test fun serviceRejectsInvalidDrafts() = runBlocking {
        val s = GameService(MemoryGameStore(Seed.create(t0).copy(onboarded = true)), object : TimeProvider { override fun now() = t0 }, TimeZone.of("Asia/Seoul"))
        assertFalse(s.addDraft(d({ it.copy(title = "") })).ok)
        assertFalse(s.addDraft(d({ it.copy(repeat = RepeatRule.DAILY, endKind = EndKind.COUNT, endCount = 0) })).ok)
        assertTrue(s.state.value.schedules.isEmpty())
        assertTrue(s.addDraft(d()).ok)
    }

    @Test fun purchaseAndMedicineGuards() = runBlocking {
        val s = GameService(MemoryGameStore(Seed.create(t0).copy(onboarded = true, coins = 30)), object : TimeProvider { override fun now() = t0 }, TimeZone.of("Asia/Seoul"))
        assertFalse(s.buyAll(listOf("canopy_bed")).ok)          // insufficient
        assertFalse(s.takeMedicine().ok)                         // not sick
        assertEquals(30, s.state.value.coins)
    }
}
