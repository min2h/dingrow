package app.mongle.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import app.mongle.logic.DayPhase
import app.mongle.logic.Emphasis
import app.mongle.logic.WeatherKind
import app.mongle.model.GameState
import app.mongle.model.Pose
import app.mongle.ui.Accent
import app.mongle.ui.ArtBitmaps
import app.mongle.ui.drawCondition
import app.mongle.ui.drawPrecip
import app.mongle.ui.gradeFor
import app.mongle.ui.petAct
import app.mongle.ui.petAssetId
import app.mongle.ui.placeBubble
import app.mongle.ui.roomAssetFor
import kotlin.math.min

/**
 * Renders the widget/wallpaper room scene to a bitmap: room art, time/weather grade, rain/snow,
 * 몽이 with condition FX, an optional level pill, and the speech bubble anchored over 몽이's head
 * (tail on the head, clamped inside the card, sized to the text, schedule details bold + accent).
 */
object WidgetScene {
    fun emphasisSpans(text: String, accent: Int = Accent.toArgb()): CharSequence {
        val sb = SpannableStringBuilder()
        Emphasis.runs(text).forEach { (part, strong) ->
            val start = sb.length
            sb.append(part)
            if (strong) {
                sb.setSpan(StyleSpan(Typeface.BOLD), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.setSpan(ForegroundColorSpan(accent), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        return sb
    }

    data class Result(val bitmap: Bitmap, val bubbleRect: RectF?, val petRect: RectF)

    fun render(
        context: Context, state: GameState, wPx: Int, hPx: Int, density: Float,
        showBubble: Boolean = true, levelPill: Boolean = false, petFraction: Float = 0.42f, petOffsetX: Float = 0f,
        bubbleTextDp: Float = 11f,
    ): Result {
        val bmp = Bitmap.createBitmap(wPx.coerceAtLeast(1), hPx.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val night = state.phase == DayPhase.NIGHT
        val square = wPx.toFloat() / hPx > 0.9f
        val room = ArtBitmaps.decode(context, roomAssetFor(state.activeRoom.wallpaperId, night, square), 640)
        // center-crop
        val scale = maxOf(wPx.toFloat() / room.width, hPx.toFloat() / room.height)
        val dw = room.width * scale; val dh = room.height * scale
        c.drawBitmap(room, null, RectF((wPx - dw) / 2, (hPx - dh) / 2, (wPx + dw) / 2, (hPx + dh) / 2), Paint(Paint.FILTER_BITMAP_FLAG))
        val grade = gradeFor(state.phase, state.weather?.kind)
        if (grade.alpha > 0f) c.drawColor(grade.toArgb())
        val kind = state.weather?.kind
        val size = Size(wPx.toFloat(), hPx.toFloat())
        val dens = Density(density)
        if (kind == WeatherKind.RAIN || kind == WeatherKind.SNOW || kind == WeatherKind.STORM) {
            CanvasDrawScope().draw(dens, LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(bmp.asImageBitmap()), size) { drawPrecip(kind, (System.currentTimeMillis() % 60_000) / 1000f, density) }
        }
        // 몽이
        val pet = (min(wPx, hPx) * petFraction).toInt().coerceAtLeast(8)
        val bottom = hPx * 0.06f
        val left = (wPx - pet) / 2f + petOffsetX * density
        val top = hPx - bottom - pet
        val petRect = RectF(left, top, left + pet, top + pet)
        val pose = state.pose
        val act = petAct(state.stage, pose, state.energy, "")
        val pb = ArtBitmaps.decode(context, petAssetId(state.speciesId, state.stage, act), pet * 2)
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33000000 }
        c.drawOval(RectF(left + pet * 0.2f, top + pet * 0.86f, left + pet * 0.8f, top + pet * 0.98f), shadow)
        val petId = petAssetId(state.speciesId, state.stage, act)
        WornBitmap.draw(c, state.outfitId, petId, pb, petRect, app.mongle.ui.WearLayer.BACK)
        c.drawBitmap(pb, null, petRect, Paint(Paint.FILTER_BITMAP_FLAG))
        WornBitmap.draw(c, state.outfitId, petId, pb, petRect, app.mongle.ui.WearLayer.FRONT)
        if (state.stage != app.mongle.model.Stage.EGG || true) {
            val fx = Bitmap.createBitmap(pet, pet, Bitmap.Config.ARGB_8888)
            CanvasDrawScope().draw(dens, LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(fx.asImageBitmap()), Size(pet.toFloat(), pet.toFloat())) { drawCondition(state.condition, 0.3f, density) }
            c.drawBitmap(fx, left, top, null)
        }
        if (levelPill) {
            val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f * density; color = 0xFF5C4636.toInt(); typeface = Typeface.DEFAULT_BOLD }
            val label = "${state.petName} · Lv.${state.level}"
            val tw = tp.measureText(label)
            val r = RectF(8 * density, 8 * density, 8 * density + tw + 16 * density, 8 * density + 20 * density)
            c.drawRoundRect(r, 10 * density, 10 * density, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xF2FFFFFF.toInt() })
            c.drawText(label, r.left + 8 * density, r.centerY() + tp.textSize * 0.35f, tp)
        }
        val bubbleRect = if (showBubble && state.speech.isNotBlank()) drawBubble(
            c, state.speech, left + pet / 2f, top + pet * 0.14f, wPx.toFloat(), density,
            if (levelPill) 30 * density else 4 * density, bubbleTextDp, maxFrac = 0.72f,
        ) else null
        return Result(bmp, bubbleRect, petRect)
    }

    /** Anchored speech bubble (tail on the head, clamped in [0, areaW], emphasis spans). Returns its bounds incl. tail. */
    fun drawBubble(c: Canvas, speech: String, headX: Float, headY: Float, areaW: Float, density: Float, minTop: Float, textDp: Float, maxFrac: Float = 0.72f, maxLines: Int = 2): RectF {
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = textDp * density; color = 0xFF5C4636.toInt() }
        val text = emphasisSpans(speech)
        val maxW = (areaW * maxFrac - 20 * density).toInt().coerceAtLeast(40)
        val natural = StaticLayout.getDesiredWidth(text, tp).toInt() + 1
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, tp, min(natural, maxW)).setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setMaxLines(maxLines).setEllipsize(android.text.TextUtils.TruncateAt.END).build()
        val padH = 10 * density; val padV = 6 * density; val tail = 6 * density
        val bw = layout.width + padH * 2; val bh = layout.height + padV * 2
        val p = placeBubble(headX, headY, bw, bh + tail, areaW, 6 * density, minTop = minTop)
        val r = RectF(p.left, p.top, p.left + bw, p.top + bh)
        c.drawRoundRect(r, 12 * density, 12 * density, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); setShadowLayer(3 * density, 0f, 1 * density, 0x26000000) })
        val tx = p.left + p.tailX.coerceIn(14 * density, bw - 14 * density)
        val path = Path().apply {
            if (!p.below) { moveTo(tx - 6 * density, r.bottom - 1); lineTo(tx, r.bottom + tail); lineTo(tx + 6 * density, r.bottom - 1) }
            else { moveTo(tx - 6 * density, r.top + 1); lineTo(tx, r.top - tail); lineTo(tx + 6 * density, r.top + 1) }
            close()
        }
        c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() })
        c.save(); c.translate(r.left + padH, r.top + padV); layout.draw(c); c.restore()
        return if (p.below) RectF(r.left, r.top - tail, r.right, r.bottom) else RectF(r.left, r.top, r.right, r.bottom + tail)
    }

    /** Time/weather grade + rain/snow over [bmpCanvas] (needs the backing bitmap for Compose drawing). */
    fun drawWeatherFx(bmp: Bitmap, state: GameState, density: Float, tSec: Float) {
        val kind = state.weather?.kind
        if (kind == WeatherKind.RAIN || kind == WeatherKind.SNOW || kind == WeatherKind.STORM) {
            CanvasDrawScope().draw(Density(density), LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(bmp.asImageBitmap()), Size(bmp.width.toFloat(), bmp.height.toFloat())) { drawPrecip(kind, tSec, density) }
        }
    }

    /** Condition FX (cold puffs, sweat, ice pack, hearts…) drawn around a pet of [pet] px at (left, top). */
    fun drawConditionFx(c: Canvas, state: GameState, left: Float, top: Float, pet: Int, density: Float, t: Float) {
        val fx = Bitmap.createBitmap(pet.coerceAtLeast(1), pet.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        CanvasDrawScope().draw(Density(density), LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(fx.asImageBitmap()), Size(pet.toFloat(), pet.toFloat())) { drawCondition(state.condition, t, density) }
        c.drawBitmap(fx, left, top, null)
    }

    /** Small fullness ring (sheet 4): track + mint arc. */
    fun ring(sizePx: Int, frac: Float, density: Float): Bitmap {
        val b = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val st = 2.6f * density
        val r = RectF(st, st, sizePx - st, sizePx - st)
        c.drawArc(r, 0f, 360f, false, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = st; color = 0xFFE8E0DA.toInt() })
        c.drawArc(r, -90f, 360f * frac.coerceIn(0f, 1f), false, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = st; strokeCap = Paint.Cap.ROUND; color = 0xFF6EBE96.toInt() })
        return b
    }
}
