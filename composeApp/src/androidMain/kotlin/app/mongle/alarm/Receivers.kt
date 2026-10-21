package app.mongle.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.mongle.AppGraph
import app.mongle.logic.Korean
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
                when (intent.action) {
                    AlarmScheduler.ACTION_DONE -> if (scheduleId.isNotBlank()) AppGraph.service.completeSchedule(scheduleId)
                    AlarmScheduler.ACTION_SNOOZE -> if (scheduleId.isNotBlank()) AppGraph.service.snooze(scheduleId)
                    else -> {
                        val state = AppGraph.service.state.value
                        val schedule = state.schedules.find { it.id == scheduleId }
                        if (schedule != null && !schedule.done) {
                            AlarmScheduler(app).showReminder(
                                title = state.petName,
                                body = Korean.reminder(state.petName, state.stage, schedule.title),
                                scheduleId = scheduleId,
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
                AppGraph.service.refresh()
            } finally {
                pending.finish()
            }
        }
    }
}
