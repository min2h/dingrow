package app.mongle.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import app.mongle.ui.WearFit
import app.mongle.ui.WearLayer
import app.mongle.ui.WornArt

/** Draws the same layered worn-outfit art as the in-app PetFigure onto a plain Android canvas (widget, live wallpaper). */
object WornBitmap {
    fun draw(c: Canvas, outfitId: String?, assetId: String, pet: Bitmap, dest: RectF, layer: WearLayer, paint: Paint? = null) {
        val ids = app.mongle.logic.Wardrobe.decode(outfitId)
        if (ids.isEmpty()) return
        if (layer == WearLayer.BACK && ids.none { WornArt.hasBack(it) }) return
        val fit = WearFit.of(assetId)
        val w = pet.width.coerceAtLeast(1); val h = pet.height.coerceAtLeast(1)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(out.asImageBitmap()), Size(w.toFloat(), h.toFloat())) {
            with(WornArt) { drawWorn(outfitId, fit, layer, if (layer == WearLayer.FRONT) pet.asImageBitmap() else null) }
        }
        c.drawBitmap(out, null, dest, paint ?: Paint(Paint.FILTER_BITMAP_FLAG))
        out.recycle()
    }
}
