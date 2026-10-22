package app.mongle

import app.mongle.data.MemoryGameStore
import app.mongle.data.Seed
import app.mongle.logic.ScheduleDraft
import app.mongle.logic.WeatherSnapshot
import app.mongle.model.RepeatRule
import app.mongle.platform.JvmNotices
import app.mongle.service.GameService
import app.mongle.service.TimeProvider
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.*

/** Notification behaviour at the service level (what gets scheduled / posted). */
class NotificationsTest {
    private val seoul = TimeZone.of("Asia/Seoul")
    private fun at(h: Int, m: Int, day: Int = 22) = LocalDateTime(2026, 10, day, h, m).toInstant(seoul).toEpochMilliseconds()
    private class Clock(var now: Long) : TimeProvider { override fun now() = now }

    private fun svc(now: Long, block: (app.mongle.model.PersistedState) -> app.mongle.model.PersistedState = { it }): Pair<GameService, Clock> {
        JvmNotices.reset()
        val c = Clock(now)
        val seed = block(Seed.create(now).copy(onboarded = true))
        return GameService(MemoryGameStore(seed), c, seoul).also { it.giftChance = { false } } to c
    }
    private fun sched() = JvmNotices.synced.filter { it.scheduleId.isNotBlank() }

    @Test fun reminderAtRemindAheadTime() = runBlocking {
        val (s, _) = svc(at(10, 0))
        s.addDraft(ScheduleDraft("회의", at(15, 0), RepeatRule.NONE, leadsMinutes = listOf(10, 0)))
        val triggers = sched().map { it.triggerAtMillis }.sorted()
        assertEquals(listOf(at(14, 50), at(15, 0)), triggers)
        assertEquals(10, sched().first { it.triggerAtMillis == at(14, 50) }.leadMinutes)
    }

    @Test fun actionsAckFeedsSnoozeReschedulesDoneGivesExp() = runBlocking {
        val (s, c) = svc(at(10, 0))
        s.addSchedule("약", at(10, 1), RepeatRule.NONE)
        val id = s.state.value.schedules.single().id
        c.now = at(10, 1)
        val hunger = s.state.value.hunger
        assertTrue(s.acknowledge(id).ok)                      // 확인 = feed
        assertTrue(s.state.value.hunger > hunger)
        assertTrue(s.snooze(id).ok)                           // 5분 뒤
        assertEquals(c.now + 5 * 60_000, s.state.value.schedules.single().timeMillis)
        assertTrue(sched().any { it.triggerAtMillis == c.now + 5 * 60_000 })
        val xp = s.state.value.xp
        c.now += 5 * 60_000
        assertTrue(s.completeSchedule(id).ok)                 // 완료 = EXP
        assertTrue(s.state.value.xp > xp || s.state.value.stage != app.mongle.model.Stage.EGG)
        assertTrue(sched().none { it.scheduleId == id })
    }

    @Test fun recurringSchedulesNextOccurrenceAfterComplete() = runBlocking {
        val (s, c) = svc(at(8, 0))
        s.addSchedule("운동", at(9, 0), RepeatRule.DAILY)
        val id = s.state.value.schedules.single().id
        c.now = at(9, 1)
        s.completeSchedule(id)
        assertTrue(sched().any { it.triggerAtMillis == at(9, 0, 23) }, sched().toString())
    }

    @Test fun briefingTimeChangeReschedules() = runBlocking {
        val (s, _) = svc(at(6, 0))
        s.refreshAlarms()
        assertEquals(at(7, 30), JvmNotices.synced.single { it.id.startsWith("brief") }.triggerAtMillis)
        s.updateSettings(s.state.value.settings.copy(briefingMinute = 8 * 60))
        assertEquals(at(8, 0), JvmNotices.synced.single { it.id.startsWith("brief") }.triggerAtMillis)
        s.updateSettings(s.state.value.settings.copy(briefingEnabled = false))
        assertTrue(JvmNotices.synced.none { it.id.startsWith("brief") })
    }

    @Test fun quietHoursShiftReminders() = runBlocking {
        val (s, _) = svc(at(20, 0))
        s.updateSettings(s.state.value.settings.copy(quietStartMinute = 23 * 60, quietEndMinute = 7 * 60))
        s.addSchedule("늦은 알림", at(23, 30), RepeatRule.NONE)
        assertEquals(at(7, 0, 23), sched().single().triggerAtMillis)
    }

    @Test fun weatherAlertsOnlyWithLocation() = runBlocking {
        val (s, c) = svc(at(10, 0))
        val rain = WeatherSnapshot(14.0, 63, 5.0, 2.0, c.now, 37.5, 127.0)
        s.updateSettings(s.state.value.settings.copy(useLocation = false, weatherAlerts = true))
        JvmNotices.immediate.clear()
        s.setWeather(rain)
        assertTrue(JvmNotices.immediate.isEmpty())
        s.updateSettings(s.state.value.settings.copy(useLocation = true, weatherAlerts = true))
        s.setWeather(rain)
        assertEquals(1, JvmNotices.immediate.size)
        s.setWeather(rain)                                    // same event: not repeated
        assertEquals(1, JvmNotices.immediate.size)
    }

    @Test fun tipsRateLimitedAndQuiet() = runBlocking {
        val (s, c) = svc(at(10, 0))
        assertNotNull(s.maybeTipNotification(roll = 0))
        c.now += 60 * 60_000
        assertNull(s.maybeTipNotification(roll = 0))         // < 6h gap
        c.now = at(17, 0)
        assertNotNull(s.maybeTipNotification(roll = 0))
        c.now = at(22, 0)
        assertNull(s.maybeTipNotification(roll = 0, chancePercent = 100)) // after 21:00
        s.updateSettings(s.state.value.settings.copy(tipsEnabled = false))
        c.now = at(10, 0, 24)
        assertNull(s.maybeTipNotification(roll = 0))
    }

    @Test fun scheduleAlertsOffSchedulesNothing() = runBlocking {
        val (s, _) = svc(at(10, 0))
        s.updateSettings(s.state.value.settings.copy(scheduleAlerts = false))
        s.addSchedule("x", at(12, 0), RepeatRule.NONE)
        assertTrue(sched().isEmpty())
    }
}
