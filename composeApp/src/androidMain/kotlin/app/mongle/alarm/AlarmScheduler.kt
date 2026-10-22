package app.mongle.alarm

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import app.dingrow.R
import app.mongle.MainActivity
import app.mongle.platform.ScheduledNotice

class AlarmScheduler(private val context: Context) {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val prefs = context.getSharedPreferences("dingrow_alarms", Context.MODE_PRIVATE)

    fun canScheduleExact(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) alarms.canScheduleExactAlarms() else true
    }

    fun sync(notices: List<ScheduledNotice>) {
        ensureChannels()
        val previous = prefs.getStringSet(KEY_IDS, emptySet()).orEmpty()
        previous.forEach { cancel(it) }
        notices.forEach { schedule(it) }
        prefs.edit().putStringSet(KEY_IDS, notices.map { it.id }.toSet()).apply()
    }

    fun notifyNow(title: String, body: String) {
        ensureChannels()
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_GIFT)
            .setSmallIcon(R.drawable.ic_stat_pet)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        notifications.notify(GIFT_ID, notification)
    }

    fun dismiss(scheduleId: String) {
        notifications.cancel(scheduleId.hashCode())
    }

    fun showReminder(title: String, body: String, scheduleId: String) {
        ensureChannels()
        val open = PendingIntent.getActivity(
            context,
            scheduleId.hashCode(),
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_SCHEDULE)
            .setSmallIcon(R.drawable.ic_stat_pet)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(0, "완료", actionIntent(ACTION_DONE, scheduleId))
            .addAction(0, "10분 뒤", actionIntent(ACTION_SNOOZE, scheduleId))
            .build()
        notifications.notify(scheduleId.hashCode(), notification)
    }

    private fun schedule(notice: ScheduledNotice) {
        val pending = broadcast(ACTION_SHOW, notice.id, notice.scheduleId, notice.title, notice.body)
        val trigger = notice.triggerAtMillis.coerceAtLeast(System.currentTimeMillis() + 1_000)
        if (canScheduleExact()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
        } else {
            alarms.setWindow(AlarmManager.RTC_WAKEUP, trigger, WINDOW_MILLIS, pending)
        }
    }

    private fun cancel(id: String) {
        alarms.cancel(broadcast(ACTION_SHOW, id, id, "", ""))
    }

    private fun actionIntent(action: String, scheduleId: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            this.action = action
            data = Uri.parse("dingrow://alarm/$scheduleId/$action")
            putExtra(EXTRA_SCHEDULE_ID, scheduleId)
        }
        return PendingIntent.getBroadcast(
            context,
            (scheduleId + action).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun broadcast(action: String, id: String, scheduleId: String, title: String, body: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            this.action = action
            data = Uri.parse("dingrow://alarm/$id/$action")
            putExtra(EXTRA_ID, id)
            putExtra(EXTRA_SCHEDULE_ID, scheduleId)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_BODY, body)
        }
        return PendingIntent.getBroadcast(
            context,
            id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val schedule = NotificationChannel(
            CHANNEL_SCHEDULE,
            context.getString(R.string.channel_schedule),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.channel_schedule_desc)
            enableVibration(true)
        }
        val gift = NotificationChannel(
            CHANNEL_GIFT,
            context.getString(R.string.channel_gift),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.channel_gift_desc)
        }
        notifications.createNotificationChannel(schedule)
        notifications.createNotificationChannel(gift)
    }

    companion object {
        const val ACTION_SHOW = "app.dingrow.SHOW"
        const val ACTION_DONE = "app.dingrow.DONE"
        const val ACTION_SNOOZE = "app.dingrow.SNOOZE"
        const val EXTRA_ID = "id"
        const val EXTRA_SCHEDULE_ID = "scheduleId"
        const val EXTRA_TITLE = "title"
        const val EXTRA_BODY = "body"
        const val CHANNEL_SCHEDULE = "dingrow.schedule"
        const val CHANNEL_GIFT = "dingrow.gift"
        private const val KEY_IDS = "ids"
        private const val GIFT_ID = 4401
        private const val WINDOW_MILLIS = 15 * 60 * 1000L
    }
}
