package app.mongle.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.mongle.generated.resources.Res
import app.mongle.generated.resources.pretendard_medium
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.compose.resources.Font

val Cream = Color(0xFFFFF6EE)
val Ink = Color(0xFF5C4636)
val InkSoft = Color(0xFF8A6A55)
val Peach = Color(0xFFF0A898)
val PeachDeep = Color(0xFFE98B84)
val PeachSoft = Color(0xFFFFE3D6)
val Mint = Color(0xFF8FCBB6)
val MintSoft = Color(0xFFDDF3EA)
val Butter = Color(0xFFFFE08A)
val ButterSoft = Color(0xFFFFF3C9)
val CardWhite = Color(0xFFFFFCF8)
val Line = Color(0xFFF0E2D6)

fun hexColor(hex: String): Color {
    val n = hex.removePrefix("#").toLong(16)
    val r = ((n shr 16) and 0xFF) / 255f
    val g = ((n shr 8) and 0xFF) / 255f
    val b = (n and 0xFF) / 255f
    return Color(r, g, b)
}

private val Scheme = lightColorScheme(
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
    MaterialTheme(colorScheme = Scheme, typography = typography, content = content)
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
