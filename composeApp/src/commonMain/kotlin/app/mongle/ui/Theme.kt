package app.mongle.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.mongle.generated.resources.Res
import app.mongle.generated.resources.pretendard_medium
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.compose.resources.Font

/** App theme state: "light" | "dark" | "system". Colors below read [dark], so every screen recomposes instantly. */
object ThemeState {
    var mode by androidx.compose.runtime.mutableStateOf("light")
    var dark by androidx.compose.runtime.mutableStateOf(false)
}

private fun pick(light: Long, night: Long) = Color(if (ThemeState.dark) night else light)

val Cream: Color get() = pick(0xFFFFF6EE, 0xFF1E1A22)
val Ink: Color get() = pick(0xFF5C4636, 0xFFF4E8DE)
val InkSoft: Color get() = pick(0xFF8A6A55, 0xFFC8B3A5)
val Peach: Color get() = pick(0xFFF0A898, 0xFFE89A8A)
val PeachDeep: Color get() = pick(0xFFE98B84, 0xFFF09A92)
val PeachSoft: Color get() = pick(0xFFFFE3D6, 0xFF4A3238)
val Mint: Color get() = pick(0xFF8FCBB6, 0xFF8FCBB6)
val MintSoft: Color get() = pick(0xFFDDF3EA, 0xFF233F37)
val Butter: Color get() = pick(0xFFFFE08A, 0xFFE8C766)
val ButterSoft: Color get() = pick(0xFFFFF3C9, 0xFF433A22)
val CardWhite: Color get() = pick(0xFFFFFCF8, 0xFF2A2430)
val Line: Color get() = pick(0xFFF0E2D6, 0xFF3B3342)

fun hexColor(hex: String): Color {
    val n = hex.removePrefix("#").toLong(16)
    val r = ((n shr 16) and 0xFF) / 255f
    val g = ((n shr 8) and 0xFF) / 255f
    val b = (n and 0xFF) / 255f
    return Color(r, g, b)
}

private fun scheme() = if (ThemeState.dark) androidx.compose.material3.darkColorScheme(
    primary = PeachDeep, onPrimary = Color.White, secondary = Mint, onSecondary = Ink, tertiary = Butter,
    background = Cream, onBackground = Ink, surface = CardWhite, onSurface = Ink, surfaceVariant = PeachSoft, onSurfaceVariant = InkSoft,
) else lightColorScheme(
    primary = PeachDeep,
    onPrimary = Color.White,
    secondary = Mint,
    onSecondary = Ink,
    tertiary = Butter,
    background = Cream,
    onBackground = Ink,
    surface = CardWhite,
    onSurface = Ink,
    surfaceVariant = PeachSoft,
    onSurfaceVariant = InkSoft,
)

@Composable
fun MongleTheme(content: @Composable () -> Unit) {
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    ThemeState.dark = when (ThemeState.mode) { "dark" -> true; "system" -> systemDark; else -> false }
    val family = FontFamily(Font(Res.font.pretendard_medium, weight = FontWeight.Medium))
    val typography = Typography(
        headlineMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 24.sp, color = Ink),
        titleLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 20.sp, color = Ink),
        titleMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 16.sp, color = Ink),
        bodyLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 16.sp, color = Ink),
        bodyMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = Ink),
        labelLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = Ink),
        labelMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = InkSoft),
    )
    MaterialTheme(colorScheme = scheme(), typography = typography, content = content)
}

data class PermissionSnapshot(
    val notificationsOn: Boolean = false,
    val exactAlarmsOn: Boolean = true,
    val supportsExactAlarm: Boolean = false,
    val supportsWallpaper: Boolean = false,
    val canPinWidget: Boolean = false,
)

object PermissionCenter {
    val snapshot = MutableStateFlow(PermissionSnapshot())
}

/** Platform bridge: the settings weather toggle asks the OS for coarse location through this. */
object LocationBridge {
    var request: (() -> Unit)? = null
}
