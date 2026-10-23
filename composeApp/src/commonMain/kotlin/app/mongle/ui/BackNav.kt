package app.mongle.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.platform.testTag

/** System/predictive back (Android) — on iOS the same exits are reachable through each screen's close button. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun Back(enabled: Boolean = true, onBack: () -> Unit) = BackHandler(enabled, onBack)

/**
 * Registers back for a screen with unsaved changes. Returns the exit request to use for the close button too,
 * so back and ✕ ask "변경사항을 버릴까요?" exactly once and leave directly when nothing changed.
 */
@Composable
internal fun rememberDiscardGuard(dirty: Boolean, onExit: () -> Unit): () -> Unit {
    var ask by remember { mutableStateOf(false) }
    val isDirty by rememberUpdatedState(dirty)
    val exit by rememberUpdatedState(onExit)
    val request = remember { { if (isDirty) ask = true else exit() } }
    Back(enabled = !ask) { request() }
    if (ask) {
        AlertDialog(
            onDismissRequest = { ask = false },
            title = { Text(t("discard_title")) },
            confirmButton = { TextButton(onClick = { ask = false; exit() }, modifier = Modifier.testTag("discard-yes")) { Text(t("discard_yes")) } },
            dismissButton = { TextButton(onClick = { ask = false }, modifier = Modifier.testTag("discard-no")) { Text(t("discard_no")) } },
            modifier = Modifier.testTag("discard-dialog"),
        )
    }
    return request
}
