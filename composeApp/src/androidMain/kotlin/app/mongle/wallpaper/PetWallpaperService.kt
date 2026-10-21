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
            val night = state?.darkRoom == true || state?.timeOfDay?.name == "NIGHT"
            canvas.drawColor(if (night) Color.parseColor("#243044") else Color.parseColor("#FFF3EA"))
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val w = canvas.width.toFloat()
            val h = canvas.height.toFloat()
            paint.color = Color.parseColor("#E7C8A4")
            canvas.drawRect(0f, h * 0.72f, w, h, paint)
            val phase = (System.currentTimeMillis() % 2400) / 2400f
            val walking = state?.pose == Pose.WALK || state?.pose == Pose.OUT
            val bob = if (walking) kotlin.math.abs(sin(phase * 4 * PI)).toFloat() * 18f else sin(phase * 2 * PI).toFloat() * 14f
            val dx = if (walking) sin(phase * 2 * PI).toFloat() * w * 0.12f else 0f
            val cx = w / 2f + dx
            val cy = h * 0.58f - bob
            val radius = w.coerceAtMost(h) * 0.16f
            paint.color = Color.parseColor("#22000000")
            canvas.drawOval(cx - radius, h * 0.78f, cx + radius, h * 0.84f, paint)
            paint.color = Color.parseColor("#F6C7A1")
            canvas.drawCircle(cx - radius * 0.55f, cy - radius * 0.85f, radius * 0.38f, paint)
            canvas.drawCircle(cx + radius * 0.55f, cy - radius * 0.85f, radius * 0.38f, paint)
            paint.color = if (state?.pose == Pose.SAD) Color.parseColor("#F3D2B4") else Color.parseColor("#FFE3C4")
            canvas.drawCircle(cx, cy, radius, paint)
            paint.color = Color.parseColor("#4A3728")
            if (state?.pose == Pose.SLEEP) {
                canvas.drawLine(cx - radius * 0.35f, cy - 4f, cx - radius * 0.1f, cy + 4f, paint)
                canvas.drawLine(cx + radius * 0.1f, cy - 4f, cx + radius * 0.35f, cy + 4f, paint)
            } else {
                canvas.drawCircle(cx - radius * 0.28f, cy - radius * 0.1f, radius * 0.08f, paint)
                canvas.drawCircle(cx + radius * 0.28f, cy - radius * 0.1f, radius * 0.08f, paint)
            }
            paint.color = Color.parseColor("#F4A6A4")
            canvas.drawCircle(cx - radius * 0.48f, cy + radius * 0.22f, radius * 0.12f, paint)
            canvas.drawCircle(cx + radius * 0.48f, cy + radius * 0.22f, radius * 0.12f, paint)
        }
    }
}
