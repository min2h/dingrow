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
import android.view.MotionEvent
import android.view.SurfaceHolder
import app.mongle.AppGraph
import app.mongle.logic.PetGesture
import app.mongle.model.GameState
import app.mongle.model.Pose
import app.mongle.model.Stage
import app.mongle.model.TimeOfDay
import app.mongle.ui.ArtBitmaps
import app.mongle.ui.PetAct
import app.mongle.ui.SpeciesPoses
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

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setTouchEventsEnabled(true)
        }

        override fun onTouchEvent(event: MotionEvent) {
            if (event.action == MotionEvent.ACTION_UP && AppGraph.ready) {
                WallpaperPainter.motion.trigger(app.mongle.ui.motion.PetMotionEngine.shapeFor("pat"))
                AppGraph.launch { AppGraph.service.react(PetGesture.TAP) }
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
    private var fromAct = PetAct.IDLE
    private var fromSpecies = ""
    private var toAct = PetAct.IDLE
    private var toSpecies = ""
    private var blendStart = 0L
    /** Same spring engine as the in-app pet, stepped by wall-clock deltas each frame. */
    val motion = app.mongle.ui.motion.PetMotionEngine(11)
    private var lastMillis = 0L
    private var lastBody = ""

    fun draw(canvas: Canvas, state: GameState?, nowMillis: Long, context: Context) {
        val night = state?.timeOfDay == TimeOfDay.NIGHT
        val wallpaper = state?.activeRoom?.wallpaperId
        val stage = state?.stage ?: Stage.BABY
        val pose = state?.pose ?: Pose.IDLE
        val species = state?.speciesId ?: "base"
        val act = if (state == null) PetAct.IDLE else petAct(stage, pose, state.energy, state.statusMessage)
        if (toSpecies.isEmpty()) {
            fromAct = act
            fromSpecies = species
            toAct = act
            toSpecies = species
            blendStart = nowMillis - 1_000L
        } else if (toAct != act || toSpecies != species) {
            fromAct = toAct
            fromSpecies = toSpecies
            toAct = act
            toSpecies = species
            blendStart = nowMillis
            motion.pulsePose()
            if (act == PetAct.HAPPY) motion.trigger(app.mongle.ui.motion.PetMotionEngine.shapeFor("heart"))
            if (act == PetAct.EAT) motion.trigger(app.mongle.ui.motion.PetMotionEngine.shapeFor("eat"))
        }
        val body = state?.reactionBody.orEmpty()
        if (body.isNotBlank() && body != lastBody) motion.trigger(app.mongle.ui.motion.PetMotionEngine.shapeFor(body))
        lastBody = body
        motion.walking = act == PetAct.WALK
        motion.sleeping = act == PetAct.SLEEP
        val dtMs = if (lastMillis == 0L) 16L else nowMillis - lastMillis
        lastMillis = nowMillis
        // Large gaps (screen off) are stepped in chunks so springs stay stable; the engine clamps each step.
        var left = dtMs.coerceIn(0L, 2_000L) / 1000f
        while (left > 0f) { val h = minOf(left, 0.05f); motion.step(h); left -= h }
        val blend = ((nowMillis - blendStart) / 360f).coerceIn(0f, 1f)
        val eased = blend * blend * (3f - 2f * blend)
        val squash = sin(eased * PI).toFloat()
        canvas.drawColor(if (night) Color.parseColor("#2C3A5A") else Color.parseColor("#FFF6EE"))
        val room = ArtBitmaps.decode(context, roomAssetFor(wallpaper, night, square = false), 720)
        val placed = drawContain(canvas, room)
        val walking = act == PetAct.WALK
        val phase = (nowMillis % 3200) / 3200f
        val wave = sin(phase * 2 * PI).toFloat()
        val stride = if (SpeciesPoses.weakWalk(species)) 0.35f else 1f
        val imgW = placed.width().coerceAtLeast(1).toFloat()
        val petEdge = (imgW * 0.28f).toInt().coerceIn(48, 420)
        val unit = petEdge / 110f // engine works in dp for a ~110dp pet
        val cx = placed.exactCenterX() + motion.translationXdp * unit * stride + if (walking) 0f else wave * 2f
        val cy = placed.bottom - petEdge * 0.28f + motion.translationYdp * unit
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.parseColor("#33000000")
        val shadowW = petEdge * 0.46f * motion.shadowScale
        val floorY = placed.bottom - petEdge * 0.28f
        paint.alpha = (255 * (motion.shadowAlpha / 0.2f) * 0.2f).toInt().coerceIn(10, 90)
        canvas.drawOval(cx - shadowW, floorY + petEdge * 0.28f, cx + shadowW, floorY + petEdge * 0.38f, paint)
        paint.alpha = 255
        val scaleY = motion.bodyScaleY() * (1f - 0.04f * squash)
        val scaleX = motion.bodyScaleX() * (1f + 0.04f * squash)
        val dw = petEdge * scaleX
        val dh = petEdge * scaleY
        val dest = Rect(
            (cx - dw / 2f).toInt(),
            (cy - dh * 0.72f).toInt(),
            (cx + dw / 2f).toInt(),
            (cy + dh * 0.28f).toInt(),
        )
        val mirror = walking && SpeciesPoses.mirrorSafeWalk(species) && motion.facing.value < 0f
        canvas.save()
        canvas.rotate(motion.rotation, cx, cy + dh * 0.28f)
        drawPet(canvas, context, species, stage, act, petEdge, dest, paint, eased, mirror, phase)
        canvas.restore()
        if (act == PetAct.SLEEP) drawSleepMarks(canvas, dest, phase)
        val speech = state?.speech.orEmpty().take(24)
        if (speech.isNotBlank()) {
            val bubble = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = (canvas.width * 0.04f).coerceIn(28f, 48f)
            }
            val width = bubble.measureText(speech) + 36f
            val left = (cx - width / 2f).coerceAtLeast(12f)
            val top = dest.top - 64f
            canvas.drawRoundRect(left, top, left + width, top + 48f, 24f, 24f, bubble)
            bubble.color = Color.parseColor("#5C4636")
            canvas.drawText(speech, left + 18f, top + 32f, bubble)
        }
    }

    private fun drawPet(
        canvas: Canvas,
        context: Context,
        species: String,
        stage: Stage,
        act: PetAct,
        petEdge: Int,
        dest: Rect,
        paint: Paint,
        eased: Float,
        mirror: Boolean,
        phase: Float,
    ) {
        fun blit(bitmap: Bitmap, alpha: Float) {
            if (alpha <= 0.01f) return
            paint.alpha = (alpha * 255).toInt().coerceIn(0, 255)
            if (!mirror) {
                canvas.drawBitmap(bitmap, null, dest, paint)
                return
            }
            canvas.save()
            canvas.scale(-1f, 1f, dest.exactCenterX(), dest.exactCenterY())
            canvas.drawBitmap(bitmap, null, dest, paint)
            canvas.restore()
        }
        val current = ArtBitmaps.decode(context, petAssetId(species, stage, act), petEdge)
        if (eased < 0.98f && (fromAct != act || fromSpecies != species)) {
            val previous = ArtBitmaps.decode(context, petAssetId(fromSpecies.ifBlank { species }, stage, fromAct), petEdge)
            blit(previous, 1f - eased)
            blit(current, eased)
        } else if (act == PetAct.IDLE) {
            val blinkId = petAssetId(species, stage, PetAct.BLINK)
            val idleId = petAssetId(species, stage, PetAct.IDLE)
            blit(current, 1f)
            if (blinkId != idleId) {
                val blink = ((phase + 0.62f) % 1f)
                val amount = if (blink > 0.9f) {
                    val t = (blink - 0.9f) / 0.1f
                    if (t < 0.5f) t * 2f else (1f - t) * 2f
                } else {
                    0f
                }
                if (amount > 0.02f) {
                    blit(ArtBitmaps.decode(context, blinkId, petEdge), amount)
                }
            }
        } else {
            blit(current, 1f)
        }
        paint.alpha = 255
    }

    private fun drawSleepMarks(canvas: Canvas, dest: Rect, phase: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#6E8CAE")
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        repeat(3) { index ->
            val travel = (phase + index * 0.33f) % 1f
            val fade = sin(travel * PI).toFloat().coerceIn(0f, 1f)
            paint.alpha = (fade * 220).toInt()
            paint.textSize = dest.height() * (0.08f + index * 0.025f)
            val x = dest.right - dest.width() * (0.18f - index * 0.06f)
            val y = dest.top + dest.height() * (0.42f - travel * 0.36f)
            canvas.drawText("z", x, y, paint)
        }
    }

    private fun drawContain(canvas: Canvas, bitmap: Bitmap): Rect {
        val viewW = canvas.width.toFloat().coerceAtLeast(1f)
        val viewH = canvas.height.toFloat().coerceAtLeast(1f)
        val bw = bitmap.width.coerceAtLeast(1).toFloat()
        val bh = bitmap.height.coerceAtLeast(1).toFloat()
        val scale = minOf(viewW / bw, viewH / bh)
        val dw = bw * scale
        val dh = bh * scale
        val left = (viewW - dw) / 2f
        val top = (viewH - dh) / 2f
        val dest = Rect(left.toInt(), top.toInt(), (left + dw).toInt(), (top + dh).toInt())
        canvas.drawBitmap(bitmap, null, dest, null)
        return dest
    }
}
