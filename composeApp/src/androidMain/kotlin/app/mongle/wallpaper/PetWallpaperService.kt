package app.mongle.wallpaper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import app.mongle.AppGraph
import app.mongle.model.GameState
import app.mongle.model.Pose
import app.mongle.model.Stage
import app.mongle.model.TimeOfDay
import app.mongle.ui.ArtBitmaps
import app.mongle.ui.PetAct
import app.mongle.ui.petAct
import app.mongle.ui.petAssetId
import app.mongle.ui.roomAssetFor
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
                if (visible) handler.postDelayed(this, 16)
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
                WallpaperPainter.draw(canvas, if (AppGraph.ready) AppGraph.service.state.value else null, System.currentTimeMillis(), this@PetWallpaperService)
            } finally {
                holder.unlockCanvasAndPost(canvas)
            }
        }
    }
}

internal object WallpaperPainter {
    fun draw(canvas: Canvas, state: GameState?, nowMillis: Long, context: Context) {
        val night = state?.timeOfDay == TimeOfDay.NIGHT
        val wallpaper = state?.activeRoom?.wallpaperId
        val stage = state?.stage ?: Stage.BABY
        val pose = state?.pose ?: Pose.IDLE
        val species = state?.speciesId ?: "base"
        val act = if (state == null) PetAct.IDLE else petAct(stage, pose, state.energy, state.statusMessage)
        val room = ArtBitmaps.decode(context, roomAssetFor(wallpaper, night, square = false), 720)
        drawCover(canvas, room)
        val walking = act == PetAct.WALK
        val phase = (nowMillis % 3200) / 3200f
        val wave = sin(phase * 2 * PI).toFloat()
        val w = canvas.width.toFloat()
        val h = canvas.height.toFloat()
        val petEdge = (w.coerceAtMost(h) * 0.42f).toInt().coerceIn(120, 420)
        val pet = ArtBitmaps.decode(context, petAssetId(species, stage, act), petEdge)
        val bob = wave * if (act == PetAct.SLEEP) 6f else 14f
        val drift = if (walking) sin(phase * 2 * PI).toFloat() * w * 0.08f else wave * 8f
        val cx = w / 2f + drift
        val cy = h * 0.62f - bob
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.parseColor("#33000000")
        val shadowW = petEdge * 0.46f
        canvas.drawOval(cx - shadowW, cy + petEdge * 0.28f, cx + shadowW, cy + petEdge * 0.38f, paint)
        val scaleY = if (act == PetAct.SLEEP) 0.92f else 1f + wave * 0.03f
        val scaleX = if (act == PetAct.SLEEP) 1.04f else 1f - wave * 0.025f
        val dw = petEdge * scaleX
        val dh = petEdge * scaleY
        val dest = Rect(
            (cx - dw / 2f).toInt(),
            (cy - dh * 0.72f).toInt(),
            (cx + dw / 2f).toInt(),
            (cy + dh * 0.28f).toInt(),
        )
        canvas.drawBitmap(pet, null, dest, paint)
    }

    private fun drawCover(canvas: Canvas, bitmap: Bitmap) {
        val viewW = canvas.width.toFloat()
        val viewH = canvas.height.toFloat()
        val scale = maxOf(viewW / bitmap.width, viewH / bitmap.height)
        val dw = bitmap.width * scale
        val dh = bitmap.height * scale
        val left = (viewW - dw) / 2f
        val top = viewH - dh
        val dest = Rect(left.toInt(), top.toInt(), (left + dw).toInt(), (top + dh).toInt())
        canvas.drawBitmap(bitmap, null, dest, null)
    }
}
