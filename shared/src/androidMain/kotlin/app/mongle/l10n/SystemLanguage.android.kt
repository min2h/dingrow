package app.mongle.l10n

import java.util.Locale

actual fun systemLanguageCode(): String = Locale.getDefault().language.orEmpty()
