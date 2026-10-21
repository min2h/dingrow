package app.mongle.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow

val Cream = Color(0xFFFFF8F3)
val Ink = Color(0xFF4A3728)
val Peach = Color(0xFFE98B84)
val PeachSoft = Color(0xFFFFD9CF)
val Mint = Color(0xFF7DCFC0)
val Butter = Color(0xFFFFE08A)
val CardWhite = Color(0xFFFFFCF9)

fun hexColor(hex: String): Color {
    val n = hex.removePrefix("#").toLong(16)
    val r = ((n shr 16) and 0xFF) / 255f
    val g = ((n shr 8) and 0xFF) / 255f
    val b = (n and 0xFF) / 255f
    return Color(r, g, b)
}

private val Scheme = lightColorScheme(
    primary = Peach,
    onPrimary = Color.White,
    secondary = Mint,
    onSecondary = Ink,
    tertiary = Butter,
    background = Cream,
    onBackground = Ink,
    surface = CardWhite,
    onSurface = Ink,
    surfaceVariant = PeachSoft,
    onSurfaceVariant = Ink,
)

@Composable
fun MongleTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}

data class PermissionSnapshot(
    val notificationsOn: Boolean = false,
    val exactAlarmsOn: Boolean = true,
    val supportsExactAlarm: Boolean = false,
    val supportsWallpaper: Boolean = false,
)

object PermissionCenter {
    val snapshot = MutableStateFlow(PermissionSnapshot())
}
