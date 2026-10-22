package app.mongle.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.mongle.AppGraph

class PetTickWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        AppGraph.ensure(applicationContext)
        AppGraph.service.tick()
        AppGraph.service.maybeTipNotification()
        return Result.success()
    }
}
