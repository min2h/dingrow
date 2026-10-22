package app.mongle.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.mongle.AppGraph
import app.mongle.l10n.L10n
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AppGraph.ensure(app)
                val scheduleId = intent.getStringExtra(AlarmScheduler.EXTRA_SCHEDULE_ID).orEmpty()
                val scheduler = AlarmScheduler(app)
                when (intent.action) {
                    AlarmScheduler.ACTION_DONE -> if (scheduleId.isNotBlank()) {
                        AppGraph.service.completeSchedule(scheduleId)
                        scheduler.dismiss(scheduleId)
                    }
                    AlarmScheduler.ACTION_SNOOZE -> if (scheduleId.isNotBlank()) {
                        AppGraph.service.snooze(scheduleId)
                        scheduler.dismiss(scheduleId)
                    }
                    AlarmScheduler.ACTION_ACK -> if (scheduleId.isNotBlank()) {
                        AppGraph.service.acknowledge(scheduleId)
                        scheduler.dismiss(scheduleId)
                    }
                    else -> {
                        val state = AppGraph.service.state.value
                        val schedule = state.schedules.find { it.id == scheduleId }
                        if (schedule != null && !schedule.done) {
                            val zone = kotlinx.datetime.TimeZone.currentSystemDefault()
                            val clock = L10n.clock(schedule.timeMillis, zone)
                            scheduler.showReminder(
                                title = L10n.calling(state.petName),
                                body = L10n.noticeBody(clock, schedule.title),
                                scheduleId = scheduleId,
                                alert = schedule.alert.name,
                                sound = state.settings.alarmSound.name,
                                fullScreen = schedule.alert.name == "SOUND",
                            )
                        }
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AppGraph.ensure(app)
                AppGraph.service.refreshAlarms()
            } finally {
                pending.finish()
            }
        }
    }
}
