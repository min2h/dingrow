package app.mongle.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke

enum class Mark {
    Paw,
    Coin,
    Bell,
    Bowl,
    Gift,
    Leaf,
    Heart,
    Calendar,
    Bed,
    Person,
    House,
    Hanger,
    Sport,
    Meal,
    Study,
    Pill,
    More,
    Gear,
}

@Composable
fun MarkIcon(mark: Mark, modifier: Modifier = Modifier, tint: Color = Ink) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        when (mark) {
            Mark.Paw -> {
                drawCircle(tint, w * 0.16f, Offset(w * 0.28f, h * 0.30f))
                drawCircle(tint, w * 0.16f, Offset(w * 0.50f, h * 0.18f))
                drawCircle(tint, w * 0.16f, Offset(w * 0.72f, h * 0.30f))
                drawOval(tint, Offset(w * 0.26f, h * 0.42f), Size(w * 0.48f, h * 0.42f))
            }
            Mark.Coin -> {
                drawCircle(Butter, w * 0.42f, center)
                drawCircle(tint, w * 0.42f, center, style = Stroke(w * 0.08f))
                drawCircle(tint, w * 0.12f, center)
            }
            Mark.Bell -> {
                val path = Path().apply {
                    moveTo(w * 0.50f, h * 0.10f)
                    lineTo(w * 0.22f, h * 0.62f)
                    lineTo(w * 0.78f, h * 0.62f)
                    close()
                }
                drawPath(path, tint)
                drawRoundRect(tint, Offset(w * 0.18f, h * 0.62f), Size(w * 0.64f, h * 0.12f), CornerRadius(8f, 8f))
                drawCircle(tint, w * 0.08f, Offset(w * 0.50f, h * 0.84f))
            }
            Mark.Bowl -> {
                drawArc(
                    color = tint,
                    startAngle = 0f,
                    sweepAngle = 180f,
                    useCenter = true,
                    topLeft = Offset(w * 0.12f, h * 0.28f),
                    size = Size(w * 0.76f, h * 0.56f),
                )
                drawOval(PeachDeep, Offset(w * 0.30f, h * 0.42f), Size(w * 0.18f, h * 0.12f))
                drawOval(PeachDeep, Offset(w * 0.52f, h * 0.40f), Size(w * 0.16f, h * 0.12f))
            }
            Mark.Gift -> {
                drawRoundRect(tint, Offset(w * 0.16f, h * 0.38f), Size(w * 0.68f, h * 0.46f), CornerRadius(10f, 10f))
                drawRect(PeachSoft, Offset(w * 0.44f, h * 0.38f), Size(w * 0.12f, h * 0.46f))
                drawRect(PeachSoft, Offset(w * 0.16f, h * 0.52f), Size(w * 0.68f, h * 0.10f))
                drawCircle(tint, w * 0.12f, Offset(w * 0.38f, h * 0.28f))
                drawCircle(tint, w * 0.12f, Offset(w * 0.62f, h * 0.28f))
            }
            Mark.Leaf -> {
                val path = Path().apply {
                    moveTo(w * 0.50f, h * 0.88f)
                    quadraticTo(w * 0.05f, h * 0.45f, w * 0.42f, h * 0.12f)
                    quadraticTo(w * 0.95f, h * 0.40f, w * 0.50f, h * 0.88f)
                    close()
                }
                drawPath(path, Mint)
                drawLine(tint, Offset(w * 0.48f, h * 0.78f), Offset(w * 0.46f, h * 0.28f), strokeWidth = w * 0.06f)
            }
            Mark.Heart -> {
                drawCircle(PeachDeep, w * 0.22f, Offset(w * 0.35f, h * 0.36f))
                drawCircle(PeachDeep, w * 0.22f, Offset(w * 0.65f, h * 0.36f))
                val path = Path().apply {
                    moveTo(w * 0.16f, h * 0.42f)
                    lineTo(w * 0.50f, h * 0.84f)
                    lineTo(w * 0.84f, h * 0.42f)
                    close()
                }
                drawPath(path, PeachDeep)
            }
            Mark.Calendar -> {
                drawRoundRect(tint, Offset(w * 0.16f, h * 0.22f), Size(w * 0.68f, h * 0.62f), CornerRadius(12f, 12f))
                drawRect(PeachSoft, Offset(w * 0.16f, h * 0.22f), Size(w * 0.68f, h * 0.18f))
                drawLine(CardWhite, Offset(w * 0.32f, h * 0.14f), Offset(w * 0.32f, h * 0.32f), strokeWidth = w * 0.08f)
                drawLine(CardWhite, Offset(w * 0.68f, h * 0.14f), Offset(w * 0.68f, h * 0.32f), strokeWidth = w * 0.08f)
            }
            Mark.Bed -> {
                drawRoundRect(tint, Offset(w * 0.12f, h * 0.42f), Size(w * 0.76f, h * 0.28f), CornerRadius(16f, 16f))
                drawCircle(PeachSoft, w * 0.14f, Offset(w * 0.30f, h * 0.48f))
                drawLine(tint, Offset(w * 0.18f, h * 0.70f), Offset(w * 0.18f, h * 0.86f), strokeWidth = w * 0.07f)
                drawLine(tint, Offset(w * 0.82f, h * 0.70f), Offset(w * 0.82f, h * 0.86f), strokeWidth = w * 0.07f)
            }
            Mark.Person -> {
                drawCircle(tint, w * 0.18f, Offset(w * 0.50f, h * 0.28f))
                drawArc(
                    color = tint,
                    startAngle = 200f,
                    sweepAngle = 140f,
                    useCenter = true,
                    topLeft = Offset(w * 0.18f, h * 0.48f),
                    size = Size(w * 0.64f, h * 0.50f),
                )
            }
            Mark.House -> {
                val roof = Path().apply {
                    moveTo(w * 0.50f, h * 0.12f)
                    lineTo(w * 0.12f, h * 0.46f)
                    lineTo(w * 0.88f, h * 0.46f)
                    close()
                }
                drawPath(roof, tint)
                drawRoundRect(tint, Offset(w * 0.24f, h * 0.44f), Size(w * 0.52f, h * 0.40f), CornerRadius(6f, 6f))
                drawRect(PeachSoft, Offset(w * 0.42f, h * 0.58f), Size(w * 0.16f, h * 0.26f))
            }
            Mark.Hanger -> {
                drawArc(
                    color = tint,
                    startAngle = 200f,
                    sweepAngle = 140f,
                    useCenter = false,
                    topLeft = Offset(w * 0.34f, h * 0.08f),
                    size = Size(w * 0.32f, h * 0.28f),
                    style = Stroke(w * 0.08f),
                )
                val path = Path().apply {
                    moveTo(w * 0.50f, h * 0.34f)
                    lineTo(w * 0.12f, h * 0.78f)
                    lineTo(w * 0.88f, h * 0.78f)
                    close()
                }
                drawPath(path, tint)
            }
            Mark.Sport -> {
                drawCircle(tint, w * 0.16f, Offset(w * 0.22f, h * 0.50f))
                drawCircle(tint, w * 0.16f, Offset(w * 0.78f, h * 0.50f))
                drawLine(tint, Offset(w * 0.34f, h * 0.50f), Offset(w * 0.66f, h * 0.50f), strokeWidth = w * 0.1f)
            }
            Mark.Meal -> {
                drawCircle(tint, w * 0.34f, center)
                drawCircle(CardWhite, w * 0.18f, center)
                drawLine(tint, Offset(w * 0.50f, h * 0.18f), Offset(w * 0.42f, h * 0.02f), strokeWidth = w * 0.05f)
                drawLine(tint, Offset(w * 0.62f, h * 0.22f), Offset(w * 0.72f, h * 0.04f), strokeWidth = w * 0.05f)
            }
            Mark.Study -> {
                drawRoundRect(tint, Offset(w * 0.16f, h * 0.22f), Size(w * 0.68f, h * 0.56f), CornerRadius(8f, 8f))
                drawLine(PeachSoft, Offset(w * 0.28f, h * 0.40f), Offset(w * 0.72f, h * 0.40f), strokeWidth = w * 0.06f)
                drawLine(PeachSoft, Offset(w * 0.28f, h * 0.54f), Offset(w * 0.64f, h * 0.54f), strokeWidth = w * 0.06f)
            }
            Mark.Pill -> {
                drawRoundRect(tint, Offset(w * 0.18f, h * 0.34f), Size(w * 0.64f, h * 0.32f), CornerRadius(w * 0.16f, w * 0.16f))
                drawRect(PeachSoft, Offset(w * 0.50f, h * 0.34f), Size(w * 0.32f, h * 0.32f))
            }
            Mark.More -> {
                drawCircle(tint, w * 0.08f, Offset(w * 0.22f, h * 0.50f))
                drawCircle(tint, w * 0.08f, Offset(w * 0.50f, h * 0.50f))
                drawCircle(tint, w * 0.08f, Offset(w * 0.78f, h * 0.50f))
            }
            Mark.Gear -> {
                for (i in 0 until 8) {
                    val a = kotlin.math.PI / 4 * i
                    drawLine(tint, Offset(w * 0.5f + (kotlin.math.cos(a) * w * 0.28f).toFloat(), h * 0.5f + (kotlin.math.sin(a) * h * 0.28f).toFloat()), Offset(w * 0.5f + (kotlin.math.cos(a) * w * 0.44f).toFloat(), h * 0.5f + (kotlin.math.sin(a) * h * 0.44f).toFloat()), w * 0.14f)
                }
                drawCircle(tint, w * 0.3f, Offset(w * 0.5f, h * 0.5f))
                drawCircle(androidx.compose.ui.graphics.Color.White, w * 0.12f, Offset(w * 0.5f, h * 0.5f))
            }
        }
    }
}
