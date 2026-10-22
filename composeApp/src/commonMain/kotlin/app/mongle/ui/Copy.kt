package app.mongle.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import app.mongle.l10n.AppLocale
import app.mongle.l10n.L10n
import org.jetbrains.compose.resources.stringResource

/**
 * In-app language. Kept for callers that reset it in tests; string lookups read [LocalAppLanguage],
 * a CompositionLocal, so a change reaches every Text even through skipped composables.
 */
@PublishedApi
internal val languageTick: MutableState<String> = mutableStateOf("system")

val LocalAppLanguage = compositionLocalOf { "system" }

private fun explicit(language: String): Boolean = language == "ko" || language == "en"

/** UI copy always comes from the shared L10n tables (the same tables widgets and notifications use). */
@Composable
fun t(key: String): String {
    val language = LocalAppLanguage.current
    return if (explicit(language)) L10n.t(key, language) else L10n.t(key)
}

@Composable
fun tf(key: String, vararg args: Any): String {
    LocalAppLanguage.current
    return L10n.fmt(key, *args)
}

/**
 * Applies the saved language before the screen is drawn and rebuilds the subtree when it changes
 * (key), so remembered strings and service-formatted labels are recomputed too.
 */
@Composable
fun LanguageProvider(language: String, content: @Composable () -> Unit) {
    AppLocale.preference = language
    CompositionLocalProvider(LocalAppLanguage provides language) {
        key(language) { content() }
    }
}
