package app.mongle

import app.mongle.data.MemoryGameStore
import app.mongle.l10n.AppLocale
import app.mongle.model.RepeatRule
import app.mongle.platform.JvmNotices
import app.mongle.service.GameService
import app.mongle.service.TimeProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

class TipsAndLanguageTest {
    private val zone = TimeZone.of("Asia/Seoul")
    private class Clock(var now: Long) : TimeProvider { override fun now() = now }
    private fun at(h: Int) = LocalDateTime(2026, 10, 22, h, 0).toInstant(zone).toEpochMilliseconds()

    @Test
    fun tipNotificationsRespectGapHoursQuietAndToggle() = runBlocking {
        JvmNotices.reset()
        AppLocale.preference = "en"
        val clock = Clock(at(10))
        val s = GameService(MemoryGameStore(), clock, zone)
        assertNull(s.maybeTipNotification(roll = 0), "not before onboarding")
        s.completeOnboarding("Bori")
        s.updateSettings(s.state.value.settings.copy(language = "en"))
        assertNull(s.maybeTipNotification(roll = 99), "roll miss")
        val tip = s.maybeTipNotification(roll = 0)
        assertNotNull(tip)
        assertEquals("Dingrow tip", JvmNotices.immediate.last().first)
        assertNull(s.maybeTipNotification(roll = 0), "gap")
        clock.now += s.tipGapMillis
        assertNotNull(s.maybeTipNotification(roll = 0))
        clock.now = at(23) + 86_400_000L
        assertNull(s.maybeTipNotification(roll = 0), "night")
        clock.now = at(10) + 86_400_000L * 3
        s.updateSettings(s.state.value.settings.copy(tipsEnabled = false))
        assertNull(s.maybeTipNotification(roll = 0), "disabled")
        s.updateSettings(s.state.value.settings.copy(tipsEnabled = true, language = "ko"))
        assertNotNull(s.maybeTipNotification(roll = 0))
        assertEquals("딩그로 팁", JvmNotices.immediate.last().first)
        AppLocale.preference = "system"
    }

    @Test
    fun languageChangeResyncsNoticesAndReloadsWidgets() = runBlocking {
        JvmNotices.reset()
        val clock = Clock(at(10))
        val s = GameService(MemoryGameStore(), clock, zone)
        s.completeOnboarding("Bori")
        s.updateSettings(s.state.value.settings.copy(language = "ko"))
        s.addSchedule("walk", at(12), RepeatRule.NONE)
        val syncs = JvmNotices.syncCount
        val reloads = JvmNotices.reloads
        s.updateSettings(s.state.value.settings.copy(language = "en"))
        assertTrue(JvmNotices.syncCount > syncs, "notices resynced in the new language")
        assertTrue(JvmNotices.reloads > reloads, "widgets redrawn")
        assertTrue(JvmNotices.synced.isNotEmpty())
        AppLocale.preference = "system"
    }

    @Test
    fun percentEscapesAreUnescaped() {
        AppLocale.preference = "en"
        assertEquals("Fullness 80%", app.mongle.l10n.L10n.fmt("stat_hunger", 80))
        AppLocale.preference = "ko"
        assertEquals("행복 50%", app.mongle.l10n.L10n.fmt("stat_happy", 50))
        AppLocale.preference = "system"
    }

    @Test
    fun acknowledgeWhenFullStillGrantsExp() = runBlocking {
        val clock = Clock(at(10))
        val s = GameService(MemoryGameStore(), clock, zone)
        s.completeOnboarding("Bori")
        s.addSchedule("walk", at(12), RepeatRule.NONE)
        val id = s.state.value.schedules.single().id
        repeat(8) { s.acknowledge(id) }
        val hunger = s.state.value.hunger
        assertTrue(hunger >= 90, "fed up to full: $hunger")
        val xp = s.state.value.xp
        s.acknowledge(id)
        assertEquals(hunger, s.state.value.hunger, "no feed when full")
        assertTrue(s.state.value.xp > xp, "EXP still granted")
    }
}
