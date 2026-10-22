package app.mongle.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import app.mongle.l10n.AppLocale
import app.mongle.l10n.L10n
import org.jetbrains.compose.resources.stringResource

/**
 * Snapshot of the in-app language. [AppLocale.preference] is a plain field, so Compose would
 * otherwise skip text that does not take the language as a parameter (button labels stayed
 * in the previous language). Reading this state subscribes every string lookup.
 */
@PublishedApi
internal val languageTick: MutableState<String> = mutableStateOf("system")

private fun explicit(language: String): Boolean = language == "ko" || language == "en"

@Composable
fun t(key: String): String {
    val language = languageTick.value
    val loaded = stringResource(uiString(key))
    if (explicit(language)) return L10n.t(key, language)
    return loaded.ifBlank { L10n.t(key) }
}

@Composable
fun tf(key: String, vararg args: Any): String {
    val language = languageTick.value
    val loaded = stringResource(uiString(key), *args)
    val fromTable = L10n.fmt(key, *args)
    if (explicit(language)) return fromTable
    val unfinished = loaded.isBlank() || loaded.contains("%s") || loaded.contains("%d")
    return if (unfinished) fromTable else loaded
}

/**
 * Applies the saved language before the screen is drawn.
 * System keeps the device locale. Korean and English read the matching string table immediately.
 * Inline so existing `return`s inside the app screen still leave the screen function.
 */
@Composable
inline fun LanguageProvider(language: String, content: @Composable () -> Unit) {
    AppLocale.preference = language
    languageTick.value = language
    content()
}
