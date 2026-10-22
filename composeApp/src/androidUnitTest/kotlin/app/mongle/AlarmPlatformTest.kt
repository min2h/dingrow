package app.mongle

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import app.mongle.alarm.AlarmScheduler
import app.mongle.alarm.BootReceiver
import app.mongle.platform.ScheduledNotice
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

/** Platform alarm behaviour: exact vs. inexact fallback, boot / time-zone reschedule, gift deep link. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AlarmPlatformTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val am get() = shadowOf(ctx.getSystemService(AlarmManager::class.java))
    private fun notice(at: Long) = ScheduledNotice("n1", "s1", "몽이", "15:00 회의", at)

    @After fun reset() { ShadowAlarmManager.setCanScheduleExactAlarms(true) }

    @Test fun exactWhenAllowed() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        val at = System.currentTimeMillis() + 3_600_000
        AlarmScheduler(ctx).sync(listOf(notice(at)))
        val a = am.scheduledAlarms.single()
        assertEquals(at, a.triggerAtMs)
        assertTrue(a.isAllowWhileIdle)
    }

    @Test fun inexactWindowFallbackWithoutExactPermission() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        val at = System.currentTimeMillis() + 3_600_000
        AlarmScheduler(ctx).sync(listOf(notice(at)))
        val a = am.scheduledAlarms.single()
        assertEquals(at, a.triggerAtMs)
        assertTrue("window, not exact", a.windowLengthMs > 0)
    }

    @Test fun resyncReplacesOldAlarms() {
        val now = System.currentTimeMillis()
        val s = AlarmScheduler(ctx)
        s.sync(listOf(notice(now + 60_000), ScheduledNotice("n2", "s2", "몽이", "b", now + 120_000)))
        s.sync(listOf(ScheduledNotice("n3", "s3", "몽이", "c", now + 180_000)))
        assertEquals(listOf(now + 180_000), am.scheduledAlarms.map { it.triggerAtMs })
    }

    @Test fun bootAndTimezoneChangeReschedule() {
        AppGraph.resetForTests()
        AppGraph.ensure(ctx)
        kotlinx.coroutines.runBlocking {
            AppGraph.service.completeOnboarding("몽이")
            AppGraph.service.addSchedule("회의", System.currentTimeMillis() + 3_600_000, app.mongle.model.RepeatRule.NONE)
        }
        listOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_TIME_CHANGED).forEach { action ->
            val manager = ctx.getSystemService(AlarmManager::class.java)
            am.scheduledAlarms.toList().forEach { op -> op.operation?.let { manager.cancel(it) } }
            assertTrue(am.scheduledAlarms.isEmpty())
            BootReceiver().onReceive(ctx, Intent(action))
            val deadline = System.currentTimeMillis() + 5_000
            while (am.scheduledAlarms.isEmpty() && System.currentTimeMillis() < deadline) { Thread.sleep(50); org.robolectric.shadows.ShadowLooper.idleMainLooper() }
            assertTrue("$action → alarms rescheduled", am.scheduledAlarms.isNotEmpty())
        }
    }

    @Test fun giftNotificationOpensGiftRoute() {
        AlarmScheduler(ctx).notifyNow("딩그로", "몽이가 선물을 가지고 왔어요! 🎁")
        val nm = shadowOf(ctx.getSystemService(android.app.NotificationManager::class.java))
        val n = nm.allNotifications.last()
        val intent = shadowOf(n.contentIntent).savedIntent
        assertEquals("gift", intent.getStringExtra("route"))
    }
}
