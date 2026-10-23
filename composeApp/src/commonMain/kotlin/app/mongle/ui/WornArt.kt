package app.mongle.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.IntSize
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

enum class WearSlot { HEAD, FACE, NECK, BODY, FEET }
enum class WearLayer { BACK, FRONT }

/**
 * Dedicated worn art for every outfit (never the shop icon). Each piece is drawn from the measured
 * body of the current sprite ([SpriteFit]): hats sit on the head top above the eyes, neckwear under the
 * mouth, garments are clipped to the body silhouette below the face, and hoods / backpacks / scarf tails
 * have a BACK layer drawn behind the body. Pure DrawScope, so the app, widget and wallpaper share it.
 */
object WornArt {
    val slots: Map<String, WearSlot> = mapOf(
        "ribbon" to WearSlot.HEAD, "beret" to WearSlot.HEAD, "crown" to WearSlot.HEAD, "sprout_hat" to WearSlot.HEAD,
        "straw_hat" to WearSlot.HEAD, "bunny_ears" to WearSlot.HEAD, "flower_crown" to WearSlot.HEAD, "beanie" to WearSlot.HEAD,
        "heart_hairpin" to WearSlot.HEAD, "sunglasses" to WearSlot.FACE,
        "knit_scarf" to WearSlot.NECK, "bow_tie" to WearSlot.NECK, "sailor_collar" to WearSlot.NECK, "pearl_necklace" to WearSlot.NECK,
        "raincoat" to WearSlot.BODY, "hoodie" to WearSlot.BODY, "cardigan" to WearSlot.BODY, "overalls" to WearSlot.BODY,
        "tiny_backpack" to WearSlot.BODY, "rain_boots" to WearSlot.FEET, "cozy_socks" to WearSlot.FEET,
    )
    fun known(id: String?) = id != null && id in slots
    fun hasBack(id: String) = id in setOf("raincoat", "hoodie", "bunny_ears", "tiny_backpack")

    /** Draws [layer] of [id] fitted to [fit]. [mask] is the pet sprite, used to keep garments on the body. */
    fun DrawScope.drawWear(id: String, fit: SpriteFit, layer: WearLayer, mask: ImageBitmap?) {
        if (!known(id)) return
        val g = Geo(fit, size.width)
        if (layer == WearLayer.BACK) { back(id, g); return }
        val clipped = id in setOf("raincoat", "hoodie", "cardigan", "overalls", "sailor_collar", "tiny_backpack", "knit_scarf")
        if (clipped && mask != null) {
            val bounds = Rect(Offset.Zero, size)
            drawContext.canvas.saveLayer(bounds, Paint())
            onBody(id, g)
            drawImage(mask, dstSize = IntSize(size.width.toInt(), size.height.toInt()), blendMode = BlendMode.DstIn)
            drawContext.canvas.restore()
        } else if (clipped) onBody(id, g)
        front(id, g)
    }

    private class Geo(val f: SpriteFit, val s: Float) {
        fun p(x: Float, y: Float) = Offset(x * s, y * s)
        val cx = f.cx
        val ht = f.headTop
        /** Hat line: dips onto the head but always clearly above the eyes. */
        val hatY = min(ht + 0.12f, f.eyeY - 0.09f)
        val headW = f.widthAt(min(hatY + 0.02f, f.eyeY - 0.04f))
        val neckY = f.faceBottom + 0.015f
        val bot = f.bottom
        val ow = 0.011f * s
    }

    private fun Color.shade(k: Float) = Color(red * k, green * k, blue * k, alpha)
    private fun Color.tint(k: Float) = Color(red + (1 - red) * k, green + (1 - green) * k, blue + (1 - blue) * k, alpha)

    private fun DrawScope.fillOutlined(path: Path, base: Color, g: Geo, top: Float, bottom: Float) {
        drawPath(path, Brush.verticalGradient(listOf(base.tint(0.18f), base, base.shade(0.86f)), top * g.s, bottom * g.s))
        drawPath(path, base.shade(0.62f).copy(alpha = 0.9f), style = Stroke(g.ow, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }

    private fun DrawScope.ball(c: Offset, r: Float, base: Color, g: Geo, outline: Boolean = true) {
        drawCircle(Brush.radialGradient(listOf(base.tint(0.45f), base, base.shade(0.8f)), c + Offset(-r * 0.35f, -r * 0.35f), r * 1.4f), r, c)
        if (outline) drawCircle(base.shade(0.6f).copy(alpha = 0.85f), r, c, style = Stroke(g.ow * 0.7f))
    }

    private fun DrawScope.shine(c: Offset, w: Float, h: Float) =
        drawOval(Color.White.copy(alpha = 0.38f), c - Offset(w / 2, h / 2), Size(w, h))

    private fun ellipsePath(c: Offset, rx: Float, ry: Float, from: Float = 0f, to: Float = 360f, steps: Int = 48): Path = Path().apply {
        for (i in 0..steps) {
            val a = (from + (to - from) * i / steps) * PI.toFloat() / 180f
            val pt = Offset(c.x + rx * cos(a), c.y + ry * sin(a))
            if (i == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y)
        }
    }

    // ---------------- BACK (behind the body) ----------------
    private fun DrawScope.back(id: String, g: Geo) {
        val f = g.f
        when (id) {
            "raincoat", "hoodie" -> {
                val base = if (id == "raincoat") RainBlue else Lilac
                // Hood behind the head: only its rim shows around the head, the face stays uncovered.
                val cy = (g.ht + f.faceBottom) / 2
                val rx = f.widthAt(cy) / 2 + 0.035f
                val ry = (f.faceBottom - g.ht) / 2 + 0.05f
                val path = ellipsePath(g.p(g.cx, cy), rx * g.s, ry * g.s)
                fillOutlined(path, base.shade(0.9f), g, cy - ry, cy + ry)
            }
            "bunny_ears" -> for (side in listOf(-1f, 1f)) {
                val base = g.p(g.cx + side * 0.085f, g.ht + 0.05f)
                rotate(side * 14f, base) {
                    val ear = Path().apply { addOval(Rect(base.x - 0.045f * g.s, base.y - 0.26f * g.s, base.x + 0.045f * g.s, base.y + 0.02f * g.s)) }
                    fillOutlined(ear, Color(0xFFFFF6F0), g, g.ht - 0.21f, g.ht + 0.07f)
                    drawOval(Color(0xFFF7B6C6), Offset(base.x - 0.022f * g.s, base.y - 0.215f * g.s), Size(0.044f * g.s, 0.19f * g.s))
                }
            }
            "tiny_backpack" -> {
                val y0 = g.neckY + 0.02f
                val x0 = f.rightAt(y0 + 0.08f) - 0.10f
                val r = Rect(x0 * g.s, y0 * g.s, (x0 + 0.17f) * g.s, (y0 + 0.21f) * g.s)
                val pack = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(r, CornerRadius(0.05f * g.s))) }
                fillOutlined(pack, Peach, g, y0, y0 + 0.21f)
                drawRoundRect(Peach.shade(0.85f), Offset(r.left + 0.08f * g.s, r.top + 0.05f * g.s), Size(0.07f * g.s, 0.10f * g.s), CornerRadius(0.02f * g.s))
            }
            "knit_scarf" -> {
                // The far tail falls behind the body on the left.
                val x = f.leftAt(g.neckY + 0.06f) + 0.015f
                val tail = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(Rect((x - 0.05f) * g.s, (g.neckY + 0.02f) * g.s, (x + 0.02f) * g.s, (g.neckY + 0.17f) * g.s), CornerRadius(0.02f * g.s))) }
                fillOutlined(tail, ScarfRed.shade(0.82f), g, g.neckY, g.neckY + 0.17f)
            }
        }
    }

    // ---------------- garments clipped to the body ----------------
    private fun DrawScope.onBody(id: String, g: Geo) {
        val f = g.f
        val y0 = f.faceBottom + 0.005f
        val l = 0f; val r = g.s
        fun coat(base: Color, hem: Float) {
            // Neckline dips under the mouth so the face is never covered.
            val path = Path().apply {
                moveTo(l, (y0 + 0.02f) * g.s)
                quadraticTo(g.cx * g.s, (y0 + 0.07f) * g.s, r, (y0 + 0.02f) * g.s)
                lineTo(r, hem * g.s); quadraticTo(g.cx * g.s, (hem + 0.025f) * g.s, l, hem * g.s); close()
            }
            drawPath(path, Brush.radialGradient(listOf(base.tint(0.22f), base, base.shade(0.78f)), g.p(g.cx - 0.05f, y0 + 0.1f), 0.42f * g.s))
            drawPath(path, base.shade(0.6f), style = Stroke(g.ow * 1.6f))
        }
        fun hood(base: Color) {
            // Covers the whole head except an oval around the face (eyes, cheeks, mouth stay visible).
            val fc = g.p(g.cx, (f.eyeY + f.faceBottom) / 2 - 0.005f)
            val rx = ((f.eyeRX - f.eyeLX) / 2 + 0.155f) * g.s; val ry = ((f.faceBottom - f.eyeY) / 2 + 0.115f) * g.s
            drawRect(Brush.radialGradient(listOf(base.tint(0.25f), base, base.shade(0.8f)), g.p(g.cx - 0.08f, g.ht + 0.08f), 0.5f * g.s), Offset.Zero, Size(g.s, (y0 + 0.06f) * g.s))
            drawOval(Color.Black, fc - Offset(rx, ry), Size(rx * 2, ry * 2), blendMode = BlendMode.Clear)
            drawOval(base.shade(0.93f), fc - Offset(rx + 0.012f * g.s, ry + 0.012f * g.s), Size(rx * 2 + 0.024f * g.s, ry * 2 + 0.024f * g.s), style = Stroke(0.035f * g.s))
            drawOval(base.shade(0.62f), fc - Offset(rx, ry), Size(rx * 2, ry * 2), style = Stroke(g.ow))
        }
        when (id) {
            "raincoat" -> {
                val hem = g.bot - 0.045f
                coat(RainBlue, hem)
                hood(RainBlue)
                drawLine(RainBlue.shade(0.65f), g.p(g.cx, y0 + 0.07f), g.p(g.cx, hem + 0.02f), g.ow)
                for (k in 0..2) {
                    val y = y0 + 0.11f + k * (hem - y0 - 0.14f) / 2
                    drawRoundRect(Cream, g.p(g.cx - 0.035f, y - 0.009f), Size(0.07f * g.s, 0.018f * g.s), CornerRadius(0.01f * g.s))
                    drawRoundRect(Cream.shade(0.75f), g.p(g.cx - 0.035f, y - 0.009f), Size(0.07f * g.s, 0.018f * g.s), CornerRadius(0.01f * g.s), style = Stroke(g.ow * 0.6f))
                }
                for (side in listOf(-1f, 1f)) {
                    val px = g.cx + side * 0.17f - 0.055f
                    drawRoundRect(RainBlue.shade(0.82f), g.p(px, hem - 0.13f), Size(0.11f * g.s, 0.035f * g.s), CornerRadius(0.012f * g.s))
                }
            }
            "hoodie" -> {
                val hem = g.bot - 0.05f
                coat(Lilac, hem)
                hood(Lilac)
                drawRect(Lilac.shade(0.85f), g.p(0f, hem - 0.04f), Size(g.s, 0.06f * g.s))
                val pocket = Path().apply {
                    moveTo((g.cx - 0.14f) * g.s, (hem - 0.05f) * g.s); lineTo((g.cx - 0.10f) * g.s, (hem - 0.15f) * g.s)
                    lineTo((g.cx + 0.10f) * g.s, (hem - 0.15f) * g.s); lineTo((g.cx + 0.14f) * g.s, (hem - 0.05f) * g.s); close()
                }
                drawPath(pocket, Lilac.shade(0.9f)); drawPath(pocket, Lilac.shade(0.62f), style = Stroke(g.ow))
            }
            "cardigan" -> {
                val hem = g.bot - 0.05f
                for (side in listOf(-1f, 1f)) {
                    val inner = g.cx + side * 0.045f
                    val outer = if (side < 0) 0f else 1f
                    val path = Path().apply {
                        moveTo(inner * g.s, (y0 + 0.11f) * g.s); lineTo((inner + side * 0.09f) * g.s, (y0 + 0.02f) * g.s)
                        lineTo(outer * g.s, (y0 + 0.0f) * g.s); lineTo(outer * g.s, hem * g.s); lineTo(inner * g.s, hem * g.s); close()
                    }
                    drawPath(path, Brush.radialGradient(listOf(Mustard.tint(0.2f), Mustard, Mustard.shade(0.8f)), g.p(g.cx, y0 + 0.1f), 0.42f * g.s))
                    drawPath(path, Mustard.shade(0.6f), style = Stroke(g.ow * 1.4f))
                }
                drawRect(Mustard.shade(0.88f), g.p(0f, hem - 0.035f), Size(g.s, 0.05f * g.s))
                for (k in 0..2) ball(g.p(g.cx - 0.06f, y0 + 0.15f + k * 0.07f), 0.013f * g.s, Cream, g)
            }
            "overalls" -> {
                val hip = g.bot - 0.17f
                val pants = Path().apply { addRect(Rect(0f, hip * g.s, g.s, g.s)) }
                drawPath(pants, Brush.radialGradient(listOf(Denim.tint(0.2f), Denim, Denim.shade(0.78f)), g.p(g.cx, hip + 0.05f), 0.4f * g.s))
                drawLine(Denim.shade(0.6f), g.p(0f, hip), g.p(1f, hip), g.ow * 1.4f)
                val bibTop = y0 + 0.08f
                val bib = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(Rect((g.cx - 0.12f) * g.s, bibTop * g.s, (g.cx + 0.12f) * g.s, (hip + 0.01f) * g.s), CornerRadius(0.025f * g.s))) }
                fillOutlined(bib, Denim, g, bibTop, hip)
                drawRoundRect(Denim.shade(0.86f), g.p(g.cx - 0.055f, bibTop + 0.04f), Size(0.11f * g.s, 0.06f * g.s), CornerRadius(0.012f * g.s))
                for (side in listOf(-1f, 1f)) {
                    val bx = g.cx + side * 0.09f
                    drawLine(Denim.shade(0.92f), g.p(bx, bibTop + 0.01f), g.p(g.cx + side * 0.19f, y0 + 0.05f), 0.035f * g.s, cap = StrokeCap.Round)
                    ball(g.p(bx, bibTop + 0.02f), 0.014f * g.s, Gold, g)
                }
            }
            "sailor_collar" -> {
                val y = y0 - 0.005f
                val collar = Path().apply {
                    moveTo(0f, y * g.s); lineTo((g.cx - 0.12f) * g.s, y * g.s); lineTo(g.cx * g.s, (y + 0.13f) * g.s)
                    lineTo((g.cx + 0.12f) * g.s, y * g.s); lineTo(g.s, y * g.s); lineTo(g.s, (y + 0.08f) * g.s)
                    lineTo(g.cx * g.s, (y + 0.22f) * g.s); lineTo(0f, (y + 0.08f) * g.s); close()
                }
                drawPath(collar, Brush.verticalGradient(listOf(Navy.tint(0.15f), Navy), y * g.s, (y + 0.22f) * g.s))
                drawPath(collar, Navy.shade(0.6f), style = Stroke(g.ow * 1.4f))
                val stripe = Path().apply { moveTo(0f, (y + 0.06f) * g.s); lineTo(g.cx * g.s, (y + 0.19f) * g.s); lineTo(g.s, (y + 0.06f) * g.s) }
                drawPath(stripe, Color.White, style = Stroke(g.ow * 1.2f))
            }
            "knit_scarf" -> {
                val y = g.neckY + 0.01f
                val band = Path().apply {
                    moveTo(0f, y * g.s); quadraticTo(g.cx * g.s, (y + 0.05f) * g.s, g.s, y * g.s)
                    lineTo(g.s, (y + 0.085f) * g.s); quadraticTo(g.cx * g.s, (y + 0.14f) * g.s, 0f, (y + 0.085f) * g.s); close()
                }
                drawPath(band, Brush.radialGradient(listOf(ScarfRed.tint(0.2f), ScarfRed, ScarfRed.shade(0.75f)), g.p(g.cx, y + 0.05f), 0.4f * g.s))
                drawPath(band, ScarfRed.shade(0.6f), style = Stroke(g.ow * 1.6f))
                var x = 0.04f
                while (x < 0.98f) { val t = x; val dip = 4 * t * (1 - t) * 0.05f
                    drawLine(ScarfRed.shade(0.8f), g.p(x, y + dip + 0.018f), g.p(x, y + dip + 0.07f), g.ow * 0.6f); x += 0.035f }
            }
            "tiny_backpack" -> for (side in listOf(-1f, 1f)) {
                val path = Path().apply {
                    moveTo((g.cx + side * 0.22f) * g.s, (y0 + 0.07f) * g.s)
                    quadraticTo((g.cx + side * 0.25f) * g.s, (y0 + 0.16f) * g.s, (g.cx + side * 0.32f) * g.s, (y0 + 0.3f) * g.s)
                }
                drawPath(path, Peach.shade(0.8f), style = Stroke(0.035f * g.s, cap = StrokeCap.Round))
                drawPath(path, Peach.shade(0.6f), style = Stroke(g.ow * 0.6f))
            }
        }
    }

    // ---------------- FRONT (over the body, not clipped) ----------------
    private fun DrawScope.front(id: String, g: Geo) {
        val f = g.f
        val tilt = -10f
        when (id) {
            "beret" -> {
                val c = g.p(g.cx + 0.03f, g.hatY - 0.045f)
                val rx = min(0.29f, g.headW * 0.48f); val ry = 0.095f
                rotate(tilt, c) {
                    val body = ellipsePath(c, rx * g.s, ry * g.s)
                    fillOutlined(body, BeretRed, g, g.hatY - 0.11f, g.hatY + 0.04f)
                    drawPath(ellipsePath(c + Offset(0f, 0.03f * g.s), rx * 0.82f * g.s, ry * 0.45f * g.s, 20f, 160f), BeretRed.shade(0.68f), style = Stroke(g.ow * 1.6f, cap = StrokeCap.Round))
                    drawRoundRect(BeretRed.shade(0.8f), c + Offset(-0.012f * g.s, -(ry + 0.03f) * g.s), Size(0.024f * g.s, 0.04f * g.s), CornerRadius(0.012f * g.s))
                    shine(c + Offset(-rx * 0.4f * g.s, -ry * 0.45f * g.s), rx * 0.5f * g.s, ry * 0.4f * g.s)
                }
            }
            "straw_hat" -> {
                val c = g.p(g.cx, g.hatY - 0.005f)
                val rx = min(0.38f, g.headW * 0.66f)
                rotate(tilt * 0.6f, c) {
                    fillOutlined(ellipsePath(c, rx * g.s, 0.055f * g.s), Straw, g, g.hatY - 0.06f, g.hatY + 0.05f)
                    val crown = Path().apply {
                        val w = rx * 0.55f
                        moveTo(c.x - w * g.s, c.y); lineTo(c.x - w * 0.9f * g.s, c.y - 0.10f * g.s)
                        quadraticTo(c.x, c.y - 0.15f * g.s, c.x + w * 0.9f * g.s, c.y - 0.10f * g.s); lineTo(c.x + w * g.s, c.y); close()
                    }
                    fillOutlined(crown, Straw, g, g.hatY - 0.14f, g.hatY)
                    drawRect(BeretRed, Offset(c.x - rx * 0.53f * g.s, c.y - 0.035f * g.s), Size(rx * 1.06f * g.s, 0.03f * g.s))
                    for (k in -3..3) drawLine(Straw.shade(0.75f), Offset(c.x + k * rx * 0.13f * g.s, c.y + 0.012f * g.s), Offset(c.x + k * rx * 0.16f * g.s, c.y + 0.04f * g.s), g.ow * 0.5f)
                }
            }
            "crown" -> {
                val c = g.p(g.cx + 0.01f, g.hatY - 0.02f)
                val w = min(0.17f, g.headW * 0.3f)
                rotate(tilt, c) {
                    val path = Path().apply {
                        moveTo(c.x - w * g.s, c.y + 0.02f * g.s); lineTo(c.x - w * g.s, c.y - 0.06f * g.s); lineTo(c.x - w * 0.5f * g.s, c.y - 0.02f * g.s)
                        lineTo(c.x, c.y - 0.095f * g.s); lineTo(c.x + w * 0.5f * g.s, c.y - 0.02f * g.s); lineTo(c.x + w * g.s, c.y - 0.06f * g.s)
                        lineTo(c.x + w * g.s, c.y + 0.02f * g.s); close()
                    }
                    fillOutlined(path, Gold, g, g.hatY - 0.12f, g.hatY)
                    ball(Offset(c.x, c.y - 0.1f * g.s), 0.014f * g.s, Gold, g)
                    ball(Offset(c.x - w * g.s, c.y - 0.065f * g.s), 0.012f * g.s, Gold, g)
                    ball(Offset(c.x + w * g.s, c.y - 0.065f * g.s), 0.012f * g.s, Gold, g)
                    ball(Offset(c.x, c.y - 0.005f * g.s), 0.017f * g.s, Color(0xFFE2546B), g)
                    ball(Offset(c.x - w * 0.6f * g.s, c.y + 0.002f * g.s), 0.011f * g.s, Color(0xFF6FA8E8), g)
                    ball(Offset(c.x + w * 0.6f * g.s, c.y + 0.002f * g.s), 0.011f * g.s, Color(0xFF6FA8E8), g)
                }
            }
            "sprout_hat" -> {
                val c = g.p(g.cx, g.hatY + 0.005f)
                val rx = min(0.24f, g.headW * 0.4f)
                rotate(tilt * 0.5f, c) {
                    val stem = Path().apply { moveTo(c.x, c.y - 0.095f * g.s); quadraticTo(c.x + 0.01f * g.s, c.y - 0.13f * g.s, c.x - 0.005f * g.s, c.y - 0.16f * g.s) }
                    drawPath(stem, Leaf.shade(0.7f), style = Stroke(g.ow * 1.4f, cap = StrokeCap.Round))
                    for (side in listOf(-1f, 1f)) {
                        val lc = Offset(c.x + side * 0.04f * g.s, c.y - 0.17f * g.s)
                        rotate(side * 30f, lc) { fillOutlined(ellipsePath(lc, 0.045f * g.s, 0.022f * g.s), Leaf, g, 0f, 1f) }
                    }
                    fillOutlined(ellipsePath(c, rx * g.s, 0.105f * g.s, 180f, 360f).apply { close() }, Mint, g, g.hatY - 0.09f, g.hatY)
                    drawLine(Mint.shade(0.7f), Offset(c.x - rx * g.s, c.y), Offset(c.x + rx * g.s, c.y), g.ow * 1.4f, cap = StrokeCap.Round)
                    shine(c + Offset(-rx * 0.4f * g.s, -0.05f * g.s), rx * 0.4f * g.s, 0.025f * g.s)
                }
            }
            "beanie" -> {
                val by = g.f.eyeY - 0.095f
                val c = g.p(g.cx, by)
                val rx = g.f.widthAt(by) / 2 + 0.012f
                val ry = by - g.ht + 0.025f
                ball(Offset(c.x, c.y - (ry + 0.01f) * g.s), 0.035f * g.s, Cream, g)
                fillOutlined(ellipsePath(c, rx * g.s, ry * g.s, 180f, 360f).apply { close() }, Coral, g, g.ht - 0.03f, by)
                for (k in -3..3) drawLine(Coral.shade(0.85f), Offset(c.x + k * rx * 0.25f * g.s, c.y - 0.01f * g.s), Offset(c.x + k * rx * 0.16f * g.s, c.y - ry * 0.85f * g.s), g.ow * 0.6f)
                val band = Rect(c.x - (rx + 0.01f) * g.s, c.y - 0.02f * g.s, c.x + (rx + 0.01f) * g.s, c.y + 0.03f * g.s)
                val bandPath = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(band, CornerRadius(0.025f * g.s))) }
                fillOutlined(bandPath, Coral.shade(0.9f), g, by - 0.02f, by + 0.04f)
                var x = band.left + 0.02f * g.s
                while (x < band.right - 0.01f * g.s) { drawLine(Coral.shade(0.72f), Offset(x, band.top + 0.008f * g.s), Offset(x, band.bottom - 0.008f * g.s), g.ow * 0.5f); x += 0.025f * g.s }
            }
            "flower_crown" -> {
                val y = g.hatY + 0.005f
                val rx = g.f.widthAt(y) / 2 - 0.02f
                val cols = listOf(Color(0xFFF59AB6), Color(0xFFFFE07A), Color(0xFFB7A6F0), Color(0xFFF59AB6), Color(0xFFFFFFFF), Color(0xFFFFE07A), Color(0xFFB7A6F0))
                for (i in 0..6) {
                    val a = (200f + i * 140f / 6) * PI.toFloat() / 180f
                    val pt = g.p(g.cx + rx * cos(a), y + 0.07f * sin(a) + 0.03f)
                    if (i < 6) { val a2 = (200f + (i + 0.5f) * 140f / 6) * PI.toFloat() / 180f
                        val lp = g.p(g.cx + rx * cos(a2), y + 0.07f * sin(a2) + 0.03f)
                        drawOval(Leaf, lp - Offset(0.022f * g.s, 0.011f * g.s), Size(0.044f * g.s, 0.022f * g.s)) }
                    for (k in 0..4) { val pa = k * 72f * PI.toFloat() / 180f
                        ball(pt + Offset(cos(pa) * 0.025f * g.s, sin(pa) * 0.025f * g.s), 0.021f * g.s, cols[i], g, outline = false) }
                    ball(pt, 0.014f * g.s, Color(0xFFF4B740), g, outline = false)
                }
            }
            "bunny_ears" -> {
                val c = g.p(g.cx, g.hatY + 0.02f)
                val rx = g.f.widthAt(g.hatY) / 2
                drawPath(ellipsePath(c, rx * g.s, (g.hatY + 0.02f - g.ht + 0.005f) * g.s, 200f, 340f), Color(0xFFF59AB6), style = Stroke(0.022f * g.s, cap = StrokeCap.Round))
            }
            "ribbon" -> {
                val y = g.ht + 0.075f
                val c = g.p(g.f.leftAt(y) + 0.085f, y)
                bow(c, 0.06f * g.s, RibbonPink, g, -22f)
            }
            "heart_hairpin" -> {
                val y = g.ht + 0.10f
                val c = g.p(g.f.rightAt(y) - 0.10f, y)
                rotate(18f, c) {
                    drawRoundRect(Gold, c + Offset(-0.05f * g.s, 0.018f * g.s), Size(0.10f * g.s, 0.014f * g.s), CornerRadius(0.007f * g.s))
                    val r = 0.042f * g.s
                    val heart = Path().apply {
                        moveTo(c.x, c.y + r * 1.1f)
                        cubicTo(c.x - r * 2.0f, c.y - r * 0.2f, c.x - r * 0.9f, c.y - r * 1.6f, c.x, c.y - r * 0.55f)
                        cubicTo(c.x + r * 0.9f, c.y - r * 1.6f, c.x + r * 2.0f, c.y - r * 0.2f, c.x, c.y + r * 1.1f); close()
                    }
                    fillOutlined(heart, Color(0xFFF0627F), g, (c.y - r) / g.s, (c.y + r) / g.s)
                    shine(c + Offset(-r * 0.6f, -r * 0.55f), r * 0.6f, r * 0.35f)
                }
            }
            "sunglasses" -> {
                val gap = f.eyeRX - f.eyeLX
                val lw = max(0.10f, min(0.15f, gap * 0.7f)); val lh = lw * 0.72f
                val frame = Color(0xFF2E2A33)
                for (ex in listOf(f.eyeLX, f.eyeRX)) {
                    val tl = g.p(ex - lw / 2, f.eyeY - lh / 2)
                    val sz = Size(lw * g.s, lh * g.s)
                    drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF4A3F5C), Color(0xFF1F1B26)), tl.y, tl.y + sz.height), tl, sz, CornerRadius(lh * 0.45f * g.s))
                    drawRoundRect(frame, tl, sz, CornerRadius(lh * 0.45f * g.s), style = Stroke(g.ow * 1.3f))
                    drawLine(Color.White.copy(alpha = 0.55f), tl + Offset(sz.width * 0.22f, sz.height * 0.25f), tl + Offset(sz.width * 0.42f, sz.height * 0.25f), g.ow, cap = StrokeCap.Round)
                }
                val bridge = Path().apply { moveTo((f.eyeLX + lw / 2) * g.s, (f.eyeY - lh * 0.15f) * g.s); quadraticTo(g.cx * g.s, (f.eyeY - lh * 0.4f) * g.s, (f.eyeRX - lw / 2) * g.s, (f.eyeY - lh * 0.15f) * g.s) }
                drawPath(bridge, frame, style = Stroke(g.ow * 1.3f))
            }
            "knit_scarf" -> {
                val y = g.neckY + 0.01f
                val tx = g.cx + 0.10f
                val tail = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(Rect(tx * g.s, (y + 0.07f) * g.s, (tx + 0.075f) * g.s, (y + 0.22f) * g.s), CornerRadius(0.02f * g.s))) }
                rotate(-6f, g.p(tx, y + 0.07f)) {
                    fillOutlined(tail, ScarfRed, g, y + 0.07f, y + 0.22f)
                    drawRect(Cream, g.p(tx, y + 0.15f), Size(0.075f * g.s, 0.022f * g.s))
                    for (k in 0..3) drawLine(ScarfRed.shade(0.75f), g.p(tx + 0.012f + k * 0.017f, y + 0.22f), g.p(tx + 0.012f + k * 0.017f, y + 0.245f), g.ow * 0.8f, cap = StrokeCap.Round)
                }
            }
            "bow_tie" -> bow(g.p(g.cx, g.neckY + 0.06f), 0.06f * g.s, Navy.tint(0.15f), g, 0f)
            "sailor_collar" -> bow(g.p(g.cx, f.faceBottom + 0.15f), 0.032f * g.s, BeretRed, g, 0f)
            "pearl_necklace" -> {
                val y = g.neckY + 0.005f
                val lx = f.leftAt(y + 0.03f) + 0.08f; val rx = f.rightAt(y + 0.03f) - 0.08f
                val n = 9
                for (i in 0..n) { val t = i / n.toFloat()
                    val pt = g.p(lx + (rx - lx) * t, y + 0.01f + 4 * t * (1 - t) * 0.07f)
                    ball(pt, (if (i == n / 2 || i == n / 2 + 1) 0.024f else 0.02f) * g.s, Color(0xFFFFF8F0), g) }
            }
            "tiny_backpack" -> {
                for (side in listOf(-1f, 1f)) ball(g.p(g.cx + side * 0.235f, f.faceBottom + 0.14f), 0.012f * g.s, Gold, g)
            }
            "rain_boots", "cozy_socks" -> for (side in listOf(-1f, 1f)) {
                val x = g.cx + side * 0.13f
                val h = if (id == "rain_boots") 0.13f else 0.09f
                val w = 0.145f
                val top = g.bot - h + 0.01f
                val shoe = Path().apply {
                    moveTo((x - w / 2) * g.s, top * g.s); lineTo((x + w / 2) * g.s, top * g.s); lineTo((x + w / 2) * g.s, (g.bot - 0.02f) * g.s)
                    quadraticTo((x + w / 2 + side * 0.02f) * g.s, (g.bot + 0.012f) * g.s, x * g.s, (g.bot + 0.012f) * g.s)
                    quadraticTo((x - w / 2 - side * 0.0f) * g.s, (g.bot + 0.012f) * g.s, (x - w / 2) * g.s, (g.bot - 0.02f) * g.s); close()
                }
                if (id == "rain_boots") {
                    fillOutlined(shoe, Sun, g, top, g.bot)
                    drawRect(Sun.shade(0.8f), g.p(x - w / 2, g.bot - 0.012f), Size(w * g.s, 0.02f * g.s))
                    drawOval(Sun.shade(0.85f), g.p(x - w / 2 - 0.005f, top - 0.012f), Size((w + 0.01f) * g.s, 0.024f * g.s))
                    shine(g.p(x - w * 0.2f, top + 0.04f), 0.02f * g.s, 0.045f * g.s)
                } else {
                    fillOutlined(shoe, Cream, g, top, g.bot)
                    for (k in 0..1) drawRect(Coral, g.p(x - w / 2 + 0.004f, top + 0.012f + k * 0.025f), Size((w - 0.008f) * g.s, 0.012f * g.s))
                }
            }
        }
    }

    private fun DrawScope.bow(c: Offset, r: Float, base: Color, g: Geo, deg: Float) = rotate(deg, c) {
        for (side in listOf(-1f, 1f)) {
            val loop = Path().apply {
                moveTo(c.x, c.y)
                cubicTo(c.x + side * r * 0.6f, c.y - r * 1.1f, c.x + side * r * 1.6f, c.y - r * 0.9f, c.x + side * r * 1.5f, c.y)
                cubicTo(c.x + side * r * 1.6f, c.y + r * 0.9f, c.x + side * r * 0.6f, c.y + r * 1.1f, c.x, c.y); close()
            }
            fillOutlined(loop, base, g, (c.y - r) / g.s, (c.y + r) / g.s)
        }
        ball(c, r * 0.32f, base.shade(0.9f), g)
    }

    private val RainBlue = Color(0xFF9CCBEF)
    private val Lilac = Color(0xFFC8B4EA)
    private val Mustard = Color(0xFFF7D978)
    private val Denim = Color(0xFF7AA3D6)
    private val Navy = Color(0xFF3E5A8C)
    private val Peach = Color(0xFFF4A766)
    private val ScarfRed = Color(0xFFE2655A)
    private val BeretRed = Color(0xFFE0574B)
    private val Straw = Color(0xFFF1D17E)
    private val Gold = Color(0xFFF2C14E)
    private val Leaf = Color(0xFF79C26A)
    private val Mint = Color(0xFF9ED9B5)
    private val Coral = Color(0xFFF08F86)
    private val Cream = Color(0xFFFFF4E2)
    private val RibbonPink = Color(0xFFF48FB1)
    private val Sun = Color(0xFFFFD34E)
}
