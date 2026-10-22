package app.mongle.platform

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import app.mongle.db.MongleDatabase
import java.io.File

object AndroidHooks {
    var sync: ((List<ScheduledNotice>) -> Unit)? = null
    var notifyNow: ((String, String) -> Unit)? = null
    var reloadWidgets: (() -> Unit)? = null
}

actual class PlatformNotifier actual constructor() {
    actual fun sync(notices: List<ScheduledNotice>) {
        AndroidHooks.sync?.invoke(notices)
    }

    actual fun notifyNow(title: String, body: String) {
        AndroidHooks.notifyNow?.invoke(title, body)
    }

    actual fun reloadWidgets() {
        AndroidHooks.reloadWidgets?.invoke()
    }
}

fun createAndroidDriver(context: Context): SqlDriver {
    copyLegacyDatabase(context.getDatabasePath("dingrow.db"), context.getDatabasePath("mongle.db"))
    return AndroidSqliteDriver(MongleDatabase.Schema, context, "dingrow.db")
}

private fun copyLegacyDatabase(dest: File, source: File) {
    if (dest.exists() || !source.exists()) return
    dest.parentFile?.mkdirs()
    listOf("", "-wal", "-shm", "-journal").forEach { suffix ->
        val from = File(source.absolutePath + suffix)
        val to = File(dest.absolutePath + suffix)
        if (from.exists() && !to.exists()) from.copyTo(to)
    }
}
