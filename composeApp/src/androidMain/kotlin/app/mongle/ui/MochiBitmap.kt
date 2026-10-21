package app.mongle.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import app.mongle.model.MochiEar
import app.mongle.model.Pose
import app.mongle.model.SpeciesCatalog
import app.mongle.model.Stage
import kotlin.math.PI
import kotlin.math.sin

internal object MochiBitmap {
    fun pet(speciesId: String, stage: Stage, pose: Pose, outfitId: String?, size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        draw(canvas, size / 2f, size / 2f, size * 0.34f, speciesId, stage, pose, outfitId)
        return bitmap
    }

    fun draw(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        speciesId: String,
        stage: Stage,
        pose: Pose,
        outfitId: String?,
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val look = SpeciesCatalog.byId(speciesId).look
        if (stage == Stage.EGG || pose == Pose.EGG) {
            paint.color = Color.parseColor("#22000000")
            canvas.drawOval(cx - radius, cy + radius * 0.7f, cx + radius, cy + radius, paint)
            paint.color = Color.parseColor("#FFF6EE")
            canvas.drawOval(cx - radius * 0.8f, cy - radius, cx + radius * 0.8f, cy + radius * 0.9f, paint)
            paint.color = 0xFF4A3728.toInt()
            canvas.drawCircle(cx - radius * 0.25f, cy, radius * 0.08f, paint)
            canvas.drawCircle(cx + radius * 0.25f, cy, radius * 0.08f, paint)
            return
        }
        val scale = when (stage) {
            Stage.BABY -> 0.72f
            Stage.CHILD -> 0.86f
            else -> 1f
        }
        val r = radius * scale
        val body = look.body.toInt()
        val ear = look.earColor.toInt()
        paint.color = Color.parseColor("#22000000")
        canvas.drawOval(cx - r * 0.8f, cy + r * 0.75f, cx + r * 0.8f, cy + r * 1.05f, paint)
        paint.color = ear
        when (look.ear) {
            MochiEar.BUNNY -> {
                canvas.drawRoundRect(cx - r * 0.55f, cy - r * 1.35f, cx - r * 0.2f, cy - r * 0.15f, 20f, 20f, paint)
                canvas.drawRoundRect(cx + r * 0.2f, cy - r * 1.35f, cx + r * 0.55f, cy - r * 0.15f, 20f, 20f, paint)
            }
            MochiEar.CAT, MochiEar.FOX -> {
                canvas.drawCircle(cx - r * 0.55f, cy - r * 0.7f, r * 0.28f, paint)
                canvas.drawCircle(cx + r * 0.55f, cy - r * 0.7f, r * 0.28f, paint)
            }
            MochiEar.BEAR, MochiEar.ROUND, MochiEar.HAMSTER, MochiEar.PANDA, MochiEar.SEAL -> {
                paint.color = if (look.ear == MochiEar.PANDA) Color.BLACK else ear
                canvas.drawCircle(cx - r * 0.62f, cy - r * 0.55f, r * 0.28f, paint)
                canvas.drawCircle(cx + r * 0.62f, cy - r * 0.55f, r * 0.28f, paint)
            }
            MochiEar.FROG, MochiEar.PENGUIN, MochiEar.HOOD -> {
                paint.color = if (look.ear == MochiEar.PENGUIN) 0xFF3E4654.toInt() else ear
                canvas.drawOval(cx - r, cy - r * 1.05f, cx + r, cy - r * 0.1f, paint)
            }
            MochiEar.DRAGON -> {
                canvas.drawCircle(cx - r * 0.35f, cy - r * 1.05f, r * 0.12f, paint)
                canvas.drawCircle(cx + r * 0.35f, cy - r * 1.05f, r * 0.12f, paint)
            }
            else -> Unit
        }
        paint.color = body
        if (look.ear == MochiEar.STAR) {
            canvas.drawCircle(cx, cy, r * 0.95f, paint)
        } else {
            canvas.drawOval(cx - r, cy - r * 0.72f, cx + r, cy + r * 0.78f, paint)
        }
        if (look.ear == MochiEar.PANDA) {
            paint.color = Color.BLACK
            canvas.drawOval(cx - r * 0.55f, cy - r * 0.15f, cx - r * 0.15f, cy + r * 0.2f, paint)
            canvas.drawOval(cx + r * 0.15f, cy - r * 0.15f, cx + r * 0.55f, cy + r * 0.2f, paint)
        }
        paint.color = 0xD0F4A6A4.toInt()
        canvas.drawCircle(cx - r * 0.48f, cy + r * 0.12f, r * 0.16f, paint)
        canvas.drawCircle(cx + r * 0.48f, cy + r * 0.12f, r * 0.16f, paint)
        paint.color = 0xFF4A3728.toInt()
        if (pose == Pose.SLEEP) {
            paint.strokeWidth = 3f
            paint.style = Paint.Style.STROKE
            canvas.drawArc(RectF(cx - r * 0.38f, cy - r * 0.12f, cx - r * 0.12f, cy + r * 0.08f), 20f, 140f, false, paint)
            canvas.drawArc(RectF(cx + r * 0.12f, cy - r * 0.12f, cx + r * 0.38f, cy + r * 0.08f), 20f, 140f, false, paint)
            paint.style = Paint.Style.FILL
        } else {
            canvas.drawCircle(cx - r * 0.24f, cy - r * 0.02f, r * 0.07f, paint)
            canvas.drawCircle(cx + r * 0.24f, cy - r * 0.02f, r * 0.07f, paint)
        }
        when (outfitId) {
            "hat_sprout" -> {
                paint.color = Color.parseColor("#7DBE6A")
                canvas.drawOval(cx - r * 0.15f, cy - r * 1.15f, cx + r * 0.25f, cy - r * 0.7f, paint)
            }
            "scarf_cloud" -> {
                paint.color = Color.parseColor("#FFF6EA")
                canvas.drawRoundRect(cx - r * 0.7f, cy + r * 0.25f, cx + r * 0.7f, cy + r * 0.48f, 16f, 16f, paint)
            }
            "ribbon_berry" -> {
                paint.color = look.accent.toInt()
                canvas.drawCircle(cx - r * 0.1f, cy - r * 0.55f, r * 0.12f, paint)
                canvas.drawCircle(cx + r * 0.12f, cy - r * 0.55f, r * 0.12f, paint)
            }
            "hood_yellow" -> {
                paint.color = Color.parseColor("#FFE08A")
                canvas.drawOval(cx - r * 0.9f, cy - r * 1.1f, cx + r * 0.9f, cy - r * 0.05f, paint)
            }
            "crown" -> {
                paint.color = Color.parseColor("#E7C56B")
                canvas.drawRect(cx - r * 0.45f, cy - r * 0.95f, cx + r * 0.45f, cy - r * 0.6f, paint)
            }
        }
    }

    fun bob(nowMillis: Long, walking: Boolean): Float {
        val phase = (nowMillis % 2400) / 2400f
        return if (walking) {
            kotlin.math.abs(sin(phase * 4 * PI)).toFloat() * 18f
        } else {
            sin(phase * 2 * PI).toFloat() * 14f
        }
    }
}
