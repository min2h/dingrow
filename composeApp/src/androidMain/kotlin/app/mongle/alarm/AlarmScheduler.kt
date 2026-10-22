package app.mongle.alarm

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import app.dingrow.R
import app.mongle.MainActivity
import app.mongle.l10n.AppLocale
import app.mongle.platform.ScheduledNotice
import java.util.Locale

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

    fun showReminder(
        title: String,
        body: String,
        scheduleId: String,
        alert: String = "SOUND",
        sound: String = "SOFT",
        fullScreen: Boolean = false,
    ) {
        ensureChannels()
        val open = PendingIntent.getActivity(
            context,
            scheduleId.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra("alarmScheduleId", scheduleId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val channel = when (alert) {
            "SILENT" -> CHANNEL_SILENT
            "VIBRATE" -> CHANNEL_VIBRATE
            else -> when (sound) {
                "BELL" -> CHANNEL_BELL
                "CHIME" -> CHANNEL_CHIME
                else -> CHANNEL_SOFT
            }
        }
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_pet)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(if (alert == "SILENT") NotificationCompat.PRIORITY_DEFAULT else NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(0, text(R.string.action_ack), actionIntent(ACTION_ACK, scheduleId))
            .addAction(0, text(R.string.action_snooze), actionIntent(ACTION_SNOOZE, scheduleId))
            .addAction(0, text(R.string.action_done), actionIntent(ACTION_DONE, scheduleId))
        if (fullScreen) builder.setFullScreenIntent(open, true)
        notifications.notify(scheduleId.hashCode(), builder.build())
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
        val gift = NotificationChannel(
            CHANNEL_GIFT,
            text(R.string.channel_gift),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = text(R.string.channel_gift_desc)
        }
        notifications.createNotificationChannel(gift)
        // Channel display names follow the in-app language. Creating the same id again updates the name.
        notifications.createNotificationChannel(channel(CHANNEL_SILENT, text(R.string.channel_silent), NotificationManager.IMPORTANCE_DEFAULT, null, false))
        notifications.createNotificationChannel(channel(CHANNEL_VIBRATE, text(R.string.channel_vibrate), NotificationManager.IMPORTANCE_HIGH, null, true))
        notifications.createNotificationChannel(channel(CHANNEL_SOFT, text(R.string.channel_soft), NotificationManager.IMPORTANCE_HIGH, RingtoneManager.TYPE_NOTIFICATION, true))
        notifications.createNotificationChannel(channel(CHANNEL_CHIME, text(R.string.channel_chime), NotificationManager.IMPORTANCE_HIGH, RingtoneManager.TYPE_ALARM, true))
        notifications.createNotificationChannel(channel(CHANNEL_BELL, text(R.string.channel_bell), NotificationManager.IMPORTANCE_HIGH, RingtoneManager.TYPE_RINGTONE, true))
    }

    private fun channel(id: String, name: String, importance: Int, ringtoneType: Int?, vibrate: Boolean): NotificationChannel {
        return NotificationChannel(id, name, importance).apply {
            description = text(R.string.channel_schedule_desc)
            enableVibration(vibrate)
            if (ringtoneType == null) {
                setSound(null, null)
            } else {
                val uri = RingtoneManager.getDefaultUri(ringtoneType)
                val usage = if (ringtoneType == RingtoneManager.TYPE_ALARM) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_NOTIFICATION
                setSound(uri, AudioAttributes.Builder().setUsage(usage).build())
            }
        }
    }

    private fun text(id: Int): String = localized().getString(id)

    private fun localized(): Context {
        val locale = if (AppLocale.code() == "ko") Locale.KOREAN else Locale.ENGLISH
        val config = android.content.res.Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }

    companion object {
        const val ACTION_SHOW = "app.dingrow.SHOW"
        const val ACTION_DONE = "app.dingrow.DONE"
        const val ACTION_SNOOZE = "app.dingrow.SNOOZE"
        const val ACTION_ACK = "app.dingrow.ACK"
        const val EXTRA_ID = "id"
        const val EXTRA_SCHEDULE_ID = "scheduleId"
        const val EXTRA_TITLE = "title"
        const val EXTRA_BODY = "body"
        const val CHANNEL_SCHEDULE = "dingrow.schedule"
        const val CHANNEL_GIFT = "dingrow.gift"
        const val CHANNEL_SILENT = "dingrow.schedule.silent"
        const val CHANNEL_VIBRATE = "dingrow.schedule.vibrate"
        const val CHANNEL_SOFT = "dingrow.schedule.soft"
        const val CHANNEL_CHIME = "dingrow.schedule.chime"
        const val CHANNEL_BELL = "dingrow.schedule.bell"
        private const val KEY_IDS = "ids"
        private const val GIFT_ID = 4401
        private const val WINDOW_MILLIS = 15 * 60 * 1000L
    }
}
