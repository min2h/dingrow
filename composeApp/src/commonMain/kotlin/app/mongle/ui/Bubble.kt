package app.mongle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mongle.logic.Emphasis

/** Accent used for schedule details (time, place, title) on every surface. */
val Accent = Color(0xFFD9604F)

/** "**bold**" runs from the Secretary become bold accent spans. */
fun emphasized(text: String, accent: Color = Accent): AnnotatedString = buildAnnotatedString {
    Emphasis.runs(text).forEach { (part, strong) ->
        if (strong) withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold, color = accent)) { append(part) } else append(part)
    }
}

/**
 * Speech bubble whose tail points down at 몽이. [tailX] is the tail position from the bubble's
 * left edge in dp (computed by the anchoring logic so the tail stays over the pet's head
 * even when the bubble is clamped against a screen edge).
 */
@Composable
fun SpeechBubble(
    text: String,
    modifier: Modifier = Modifier,
    tailX: Dp? = null,
    tailXPx: (() -> Float)? = null,
    maxWidth: Dp = 240.dp,
    fontSize: Int = 14,
) {
    val tail = 9.dp
    Box(
        modifier
            .widthIn(max = maxWidth)
            .drawBehind {
                val tx = (tailXPx?.invoke() ?: tailX?.toPx() ?: (size.width / 2)).coerceIn(18.dp.toPx(), size.width - 18.dp.toPx())
                val bottom = size.height - tail.toPx()
                val p = Path().apply {
                    moveTo(tx - 8.dp.toPx(), bottom - 1f)
                    lineTo(tx, size.height)
                    lineTo(tx + 8.dp.toPx(), bottom - 1f)
                    close()
                }
                drawPath(p, Color.White)
            }
            .padding(bottom = tail),
    ) {
        Text(
            emphasized(text),
            color = Ink,
            fontSize = fontSize.sp,
            lineHeight = (fontSize + 5).sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .shadow(3.dp, RoundedCornerShape(16.dp), clip = false, ambientColor = Color(0x22000000), spotColor = Color(0x22000000))
                .background(Color.White, RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 9.dp),
        )
    }
}

/**
 * Pure anchoring math, shared by app, widget and wallpaper: places a bubble of [bubbleW]×[bubbleH]
 * above a head at ([headX], [headY]) inside a [areaW]-wide area, clamped by [margin], keeping the
 * tail over the head. If there is no room above ([minTop]), it flips below the head.
 */
data class BubblePlacement(val left: Float, val top: Float, val tailX: Float, val below: Boolean)

fun placeBubble(headX: Float, headY: Float, bubbleW: Float, bubbleH: Float, areaW: Float, margin: Float, minTop: Float = 0f, gap: Float = 4f): BubblePlacement {
    val w = bubbleW.coerceAtMost(areaW - 2 * margin)
    val left = (headX - w / 2).coerceIn(margin, (areaW - margin - w).coerceAtLeast(margin))
    var top = headY - gap - bubbleH
    val below = top < minTop
    if (below) top = headY + gap
    return BubblePlacement(left, top, (headX - left).coerceIn(18f, w - 18f), below)
}
