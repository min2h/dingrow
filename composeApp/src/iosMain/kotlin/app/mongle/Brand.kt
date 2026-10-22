package app.mongle

import platform.Foundation.NSLocale
import platform.Foundation.preferredLanguages

actual fun appBrandName(): String {
    val language = NSLocale.preferredLanguages.firstOrNull()?.toString().orEmpty()
    return if (language.startsWith("ko")) "딩그로" else "Dingrow"
}
