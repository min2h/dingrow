package app.mongle

import android.app.Application
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import app.mongle.worker.PetTickWorker
import java.util.concurrent.TimeUnit

class MongleApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.ensure(this)
        if (!WorkManager.isInitialized()) {
            WorkManager.initialize(this, Configuration.Builder().build())
        }
        val request = PeriodicWorkRequestBuilder<PetTickWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "mongle-tick",
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
