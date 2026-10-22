package app.mongle.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/** Life-event category glyphs (no pet-care icons). Same rounded, soft line style as CuteIcons. */
@Composable
fun CategoryIcon(category: String, modifier: Modifier, tint: Color = Ink) {
    Canvas(modifier) { drawCategory(app.mongle.logic.Categories.normalize(category), tint) }
}

fun categoryColor(category: String): Color = when (app.mongle.logic.Categories.normalize(category)) {
    "work" -> Color(0xFF7A8FB8)
    "study" -> Color(0xFF8E7CC3)
    "exercise" -> Color(0xFF5FB28C)
    "meeting" -> Color(0xFFE79A6B)
    "social" -> Color(0xFFD9A441)
    "health" -> Color(0xFFE07474)
    "chores" -> Color(0xFF6FA7C9)
    else -> Color(0xFFA08C7C)
}

fun DrawScope.drawCategory(cat: String, tint: Color) {
    val w = size.width; val sw = w * 0.09f
    val st = Stroke(sw, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
    when (cat) {
        "work" -> { // briefcase
            drawRoundRect(tint, Offset(w * 0.1f, w * 0.32f), Size(w * 0.8f, w * 0.52f), CornerRadius(w * 0.1f), style = st)
            drawRoundRect(tint, Offset(w * 0.36f, w * 0.16f), Size(w * 0.28f, w * 0.16f), CornerRadius(w * 0.05f), style = st)
            drawLine(tint, Offset(w * 0.1f, w * 0.52f), Offset(w * 0.9f, w * 0.52f), sw)
        }
        "study" -> { // open book
            drawLine(tint, Offset(w * 0.5f, w * 0.28f), Offset(w * 0.5f, w * 0.82f), sw)
            drawRoundRect(tint, Offset(w * 0.1f, w * 0.22f), Size(w * 0.4f, w * 0.6f), CornerRadius(w * 0.06f), style = st)
            drawRoundRect(tint, Offset(w * 0.5f, w * 0.22f), Size(w * 0.4f, w * 0.6f), CornerRadius(w * 0.06f), style = st)
        }
        "exercise" -> { // dumbbell
            drawLine(tint, Offset(w * 0.28f, w * 0.5f), Offset(w * 0.72f, w * 0.5f), sw)
            drawRoundRect(tint, Offset(w * 0.12f, w * 0.3f), Size(w * 0.16f, w * 0.4f), CornerRadius(w * 0.05f))
            drawRoundRect(tint, Offset(w * 0.72f, w * 0.3f), Size(w * 0.16f, w * 0.4f), CornerRadius(w * 0.05f))
        }
        "meeting" -> { // two people
            drawCircle(tint, w * 0.11f, Offset(w * 0.36f, w * 0.34f), style = st)
            drawCircle(tint, w * 0.11f, Offset(w * 0.66f, w * 0.34f), style = st)
            drawArc(tint, 180f, 180f, false, Offset(w * 0.16f, w * 0.56f), Size(w * 0.4f, w * 0.36f), style = st)
            drawArc(tint, 180f, 180f, false, Offset(w * 0.46f, w * 0.56f), Size(w * 0.4f, w * 0.36f), style = st)
        }
        "social" -> { // fork & knife (dinner plans)
            drawLine(tint, Offset(w * 0.34f, w * 0.14f), Offset(w * 0.34f, w * 0.86f), sw)
            drawLine(tint, Offset(w * 0.24f, w * 0.14f), Offset(w * 0.24f, w * 0.36f), sw)
            drawLine(tint, Offset(w * 0.44f, w * 0.14f), Offset(w * 0.44f, w * 0.36f), sw)
            drawArc(tint, 0f, 180f, false, Offset(w * 0.24f, w * 0.26f), Size(w * 0.2f, w * 0.2f), style = st)
            drawRoundRect(tint, Offset(w * 0.6f, w * 0.14f), Size(w * 0.14f, w * 0.4f), CornerRadius(w * 0.07f))
            drawLine(tint, Offset(w * 0.67f, w * 0.5f), Offset(w * 0.67f, w * 0.86f), sw)
        }
        "health" -> { // medical cross
            drawRoundRect(tint, Offset(w * 0.4f, w * 0.16f), Size(w * 0.2f, w * 0.68f), CornerRadius(w * 0.06f))
            drawRoundRect(tint, Offset(w * 0.16f, w * 0.4f), Size(w * 0.68f, w * 0.2f), CornerRadius(w * 0.06f))
        }
        "chores" -> { // cart
            drawLine(tint, Offset(w * 0.08f, w * 0.22f), Offset(w * 0.22f, w * 0.22f), sw)
            drawLine(tint, Offset(w * 0.22f, w * 0.22f), Offset(w * 0.3f, w * 0.64f), sw)
            drawLine(tint, Offset(w * 0.3f, w * 0.64f), Offset(w * 0.8f, w * 0.64f), sw)
            drawLine(tint, Offset(w * 0.25f, w * 0.34f), Offset(w * 0.88f, w * 0.34f), sw)
            drawLine(tint, Offset(w * 0.88f, w * 0.34f), Offset(w * 0.8f, w * 0.64f), sw)
            drawCircle(tint, w * 0.07f, Offset(w * 0.36f, w * 0.8f)); drawCircle(tint, w * 0.07f, Offset(w * 0.74f, w * 0.8f))
        }
        else -> repeat(3) { i -> drawCircle(tint, w * 0.08f, Offset(w * (0.25f + i * 0.25f), w * 0.5f)) }
    }
}
