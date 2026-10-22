package app.mongle.l10n

import platform.Foundation.NSLocale
import platform.Foundation.preferredLanguages

actual fun systemLanguageCode(): String =
    NSLocale.preferredLanguages.firstOrNull()?.toString()?.substringBefore("-")?.lowercase().orEmpty()
