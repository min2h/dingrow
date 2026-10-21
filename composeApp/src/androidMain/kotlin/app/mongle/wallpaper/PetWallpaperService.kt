package app.mongle.wallpaper

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import app.mongle.AppGraph
import app.mongle.model.Pose
import app.mongle.model.Stage
import app.mongle.ui.MochiBitmap
import kotlin.math.PI
import kotlin.math.sin

class PetWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = PetEngine()

    private inner class PetEngine : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private var visible = false
        private val tick = object : Runnable {
            override fun run() {
                drawFrame()
                if (visible) handler.postDelayed(this, 80)
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            if (visible) tick.run() else handler.removeCallbacks(tick)
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            visible = false
            handler.removeCallbacks(tick)
            super.onSurfaceDestroyed(holder)
        }

        override fun onDestroy() {
            handler.removeCallbacks(tick)
            super.onDestroy()
        }

        private fun drawFrame() {
            val holder = surfaceHolder
            val canvas = holder.lockCanvas() ?: return
            try {
                drawPet(canvas)
            } finally {
                holder.unlockCanvasAndPost(canvas)
            }
        }

        private fun drawPet(canvas: Canvas) {
            val state = if (AppGraph.ready) AppGraph.service.state.value else null
            WallpaperPainter.draw(canvas, state, System.currentTimeMillis())
        }
    }
}

internal object WallpaperPainter {
    fun draw(canvas: Canvas, state: app.mongle.model.GameState?, nowMillis: Long) {
        val night = state?.darkRoom == true || state?.timeOfDay?.name == "NIGHT"
        canvas.drawColor(if (night) Color.parseColor("#2C3A5A") else Color.parseColor("#FFF6EE"))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val w = canvas.width.toFloat()
        val h = canvas.height.toFloat()
        paint.color = Color.parseColor(if (night) "#3C4E78" else "#D5EEF8")
        canvas.drawRoundRect(w * 0.62f, h * 0.16f, w * 0.88f, h * 0.42f, 24f, 24f, paint)
        paint.color = Color.parseColor(state?.floorHex ?: "#E7C8A4")
        canvas.drawRect(0f, h * 0.62f, w, h, paint)
        paint.color = Color.parseColor("#B7DCCB")
        canvas.drawOval(w * 0.28f, h * 0.70f, w * 0.72f, h * 0.84f, paint)
        val walking = state?.pose == Pose.WALK || state?.pose == Pose.OUT
        val phase = (nowMillis % 2400) / 2400f
        val dx = sin(phase * 2 * PI).toFloat() * if (walking) w * 0.12f else 8f
        val bob = MochiBitmap.bob(nowMillis, walking)
        MochiBitmap.draw(
            canvas = canvas,
            cx = w / 2f + dx,
            cy = h * 0.56f - bob,
            radius = w.coerceAtMost(h) * 0.16f,
            speciesId = state?.speciesId ?: "sp_peach",
            stage = state?.stage ?: Stage.BABY,
            pose = state?.pose ?: Pose.IDLE,
            outfitId = state?.outfitId,
        )
    }
}
