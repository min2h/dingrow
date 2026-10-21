package app.mongle.platform

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import app.mongle.db.MongleDatabase

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

fun createAndroidDriver(context: Context): SqlDriver =
    AndroidSqliteDriver(MongleDatabase.Schema, context, "mongle.db")
