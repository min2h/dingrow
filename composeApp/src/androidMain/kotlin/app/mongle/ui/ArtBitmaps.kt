package app.mongle.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import java.io.InputStream
import kotlin.math.max

internal object ArtBitmaps {
    private val cache = LruCache<String, Bitmap>(8 * 1024)

    fun decode(context: Context, id: String, maxEdge: Int): Bitmap {
        val edge = maxEdge.coerceIn(48, 640)
        val key = "$id@$edge"
        cache.get(key)?.let { return it }
        val bitmap = decodeStream(context, id, edge) ?: fallback(edge)
        cache.put(key, bitmap)
        return bitmap
    }

    private fun decodeStream(context: Context, id: String, maxEdge: Int): Bitmap? {
        val bytes = open(context, id)?.use { it.readBytes() } ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxEdge)
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inScaled = false
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }

    private fun open(context: Context, id: String): InputStream? {
        val paths = listOf(
            "mongle/$id.png",
            "composeResources/app.mongle.generated.resources/drawable/$id.webp",
        )
        for (path in paths) {
            try {
                return context.assets.open(path)
            } catch (_: Exception) {
            }
        }
        val loader = context.classLoader ?: ArtBitmaps::class.java.classLoader
        for (path in paths) {
            loader?.getResourceAsStream(path)?.let { return it }
        }
        return null
    }

    private fun sampleSize(width: Int, height: Int, maxEdge: Int): Int {
        var sample = 1
        val longest = max(width, height)
        while (longest / (sample * 2) >= maxEdge) sample *= 2
        return sample
    }

    private fun fallback(edge: Int): Bitmap =
        Bitmap.createBitmap(edge, edge, Bitmap.Config.ARGB_8888)
}
