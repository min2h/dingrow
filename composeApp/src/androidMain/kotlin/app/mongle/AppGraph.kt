package app.mongle

import android.content.Context
import app.mongle.platform.AndroidHooks
import app.mongle.platform.createAndroidDriver
import app.mongle.service.GameService
import app.mongle.service.createGameService
import app.mongle.alarm.AlarmScheduler
import androidx.glance.appwidget.updateAll
import app.mongle.widget.LargePetWidget
import app.mongle.widget.SmallPetWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object AppGraph {
    @Volatile
    var ready: Boolean = false
        private set

    lateinit var service: GameService
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()

    fun ensure(context: Context) {
        if (ready) return
        synchronized(lock) {
            if (ready) return
            val app = context.applicationContext
            val scheduler = AlarmScheduler(app)
            AndroidHooks.sync = { scheduler.sync(it) }
            AndroidHooks.notifyNow = { title, body -> scheduler.notifyNow(title, body) }
            AndroidHooks.reloadWidgets = {
                scope.launch {
                    runCatching {
                        SmallPetWidget().updateAll(app)
                        LargePetWidget().updateAll(app)
                    }
                }
            }
            service = createGameService(createAndroidDriver(app))
            ready = true
            scope.launch { service.refresh() }
        }
    }
}
