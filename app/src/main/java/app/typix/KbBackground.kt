package app.typix

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

/** Optional keyboard background picture, one per theme, stored privately in the app (never uploaded anywhere). */
object KbBackground {
    private var cacheKey = ""
    private var bmp: Bitmap? = null
    private var shader: BitmapShader? = null
    private val m = Matrix()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rect = RectF()
    private var pathW = -1f; private var pathH = -1f; private var pathR = -1f

    private fun file(c: Context, theme: String) = File(c.filesDir, "kbbg_$theme.jpg")

    fun get(c: Context, theme: String): Bitmap? {
        val f = file(c, theme)
        if (!f.exists()) { bmp = null; shader = null; cacheKey = ""; return null }
        val k = "$theme${f.lastModified()}${f.length()}"
        if (k != cacheKey) {
            bmp = try { BitmapFactory.decodeFile(f.path) } catch (e: Throwable) { null }
            shader = bmp?.let { BitmapShader(it, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
            cacheKey = k; pathW = -1f
        }
        return bmp
    }

    fun has(c: Context, theme: String) = file(c, theme).exists()
    fun remove(c: Context, theme: String) { file(c, theme).delete(); cacheKey = ""; bmp = null; shader = null }

    /** Shrinks the picked picture (longest side 1280 px) and keeps it as a private JPEG. */
    fun save(c: Context, theme: String, uri: Uri): Boolean = try {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        c.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
        var sample = 1
        while (maxOf(o.outWidth, o.outHeight) / (sample * 2) >= 1280) sample *= 2
        val o2 = BitmapFactory.Options().apply { inSampleSize = sample }
        val raw = c.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o2) }
        if (raw == null) false else {
            val s = 1280f / maxOf(raw.width, raw.height)
            val out = if (s < 1f) Bitmap.createScaledBitmap(raw, (raw.width * s).toInt().coerceAtLeast(1), (raw.height * s).toInt().coerceAtLeast(1), true) else raw
            FileOutputStream(file(c, theme)).use { out.compress(Bitmap.CompressFormat.JPEG, 86, it) }
            cacheKey = ""; true
        }
    } catch (e: Throwable) { false }

    /** Draws the picture (centre-cropped, rounded top corners) plus the dim layer. Returns false when this theme has no picture. */
    fun draw(canvas: Canvas, c: Context, th: Theme, w: Float, h: Float, topR: Float): Boolean {
        val b = get(c, th.name) ?: return false
        val sh = shader ?: return false
        if (w != pathW || h != pathH || topR != pathR) {
            path.reset(); rect.set(0f, 0f, w, h)
            path.addRoundRect(rect, floatArrayOf(topR, topR, topR, topR, 0f, 0f, 0f, 0f), Path.Direction.CW)
            pathW = w; pathH = h; pathR = topR
        }
        val sc = maxOf(w / b.width, h / b.height)
        m.setScale(sc, sc); m.postTranslate((w - b.width * sc) / 2f, (h - b.height * sc) / 2f)
        sh.setLocalMatrix(m); paint.shader = sh
        canvas.drawPath(path, paint)
        val dim = Prefs.ov(c, th.name, "bgdim", 35).coerceIn(0, 85)
        if (dim > 0) { dimPaint.color = th.bg; dimPaint.alpha = dim * 255 / 100; canvas.drawPath(path, dimPaint) }
        return true
    }
}
