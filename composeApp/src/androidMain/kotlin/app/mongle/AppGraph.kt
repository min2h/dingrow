package app.mongle

import app.mongle.widget.MediumPetWidget
import android.content.Context
import app.mongle.platform.AndroidHooks
import app.mongle.platform.createAndroidDriver
import app.mongle.service.GameService
import app.mongle.service.createGameService
import app.mongle.billing.PlayBillingGateway
import app.mongle.logic.BillingGateway
import app.mongle.logic.FakeBilling
import app.mongle.logic.StorePrices
import android.os.Build
import app.dingrow.BuildConfig
import app.mongle.alarm.AlarmScheduler
import androidx.glance.appwidget.updateAll
import app.mongle.widget.LargePetWidget
import app.mongle.widget.SmallPetWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import app.mongle.cloud.CloudSync
import app.mongle.cloud.CloudHooks
import app.mongle.cloud.FirebaseCloudBackend

object AppGraph {
    @Volatile
    var ready: Boolean = false
        private set

    lateinit var service: GameService
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()

    /** 단위 테스트가 시계나 메모리 저장소를 넣은 서비스를 위젯 버튼에 연결한다. */
    internal fun installForTests(game: GameService) {
        synchronized(lock) {
            service = game
            ready = true
            AndroidHooks.sync = {}
            AndroidHooks.notifyNow = { _, _ -> }
            AndroidHooks.reloadWidgets = {}
        }
    }

    internal fun resetForTests() {
        synchronized(lock) {
            ready = false
        }
    }

    fun launch(block: suspend () -> Unit) {
        scope.launch { block() }
    }

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
                        MediumPetWidget().updateAll(app)
                    }
                }
            }
            val billing = gateway(app)
            service = createGameService(createAndroidDriver(app), billing = billing)
            if (billing is PlayBillingGateway) scope.launch {
                runCatching { StorePrices.update(billing.prices()) }
                // Grant + consume purchases that completed while the app was closed (pending payments, interrupted flows).
                runCatching { service.claimPendingPurchases() }
            }
            installCloud(app)
            ready = true
            scope.launch { service.refresh(); runCatching { cloud?.syncNow() } }
        }
    }

    @Volatile var cloud: CloudSync? = null
        private set
    private var pushJob: kotlinx.coroutines.Job? = null

    /** Firebase server save: offline-first, pushes 3 s after the last change; no-op without google-services.json. */
    private fun installCloud(ctx: Context) {
        val robot = Build.FINGERPRINT.orEmpty().contains("robolectric")
        if (robot) return
        val backend = runCatching { FirebaseCloudBackend(ctx) }.getOrNull() ?: return
        if (!backend.available) return
        val sync = CloudSync(backend, service)
        cloud = sync
        CloudHooks.sync = sync
        service.purchaseVerifier = { productId, token -> sync.verify("android", productId, token) }
        service.onChanged = {
            pushJob?.cancel()
            pushJob = scope.launch { kotlinx.coroutines.delay(3_000); runCatching { sync.syncNow() } }
        }
    }

    private fun gateway(context: Context): BillingGateway {
        val robot = Build.FINGERPRINT.orEmpty().contains("robolectric")
        return if (useFakeBilling(BuildConfig.DEBUG, robot)) FakeBilling() else PlayBillingGateway(context)
    }

    /** Fake billing only in debug builds or under Robolectric; release builds always talk to Google Play. */
    internal fun useFakeBilling(debug: Boolean, robot: Boolean) = debug || robot
}
