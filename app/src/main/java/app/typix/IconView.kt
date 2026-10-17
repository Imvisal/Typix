package app.typix

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View

/** Crisp line icons drawn on a 24x24 grid (no images, no emoji fonts). */
class IconView(ctx: Context, private val kind: Int, private var color: Int) : View(ctx) {
    companion object { const val EMOJI = 0; const val CLIP = 1; const val EDIT = 2; const val TUNE = 3; const val GLOBE = 4; const val HOME = 5; const val PALETTE = 6; const val MIC = 7; const val SUN = 8; const val CHECK = 9 }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val r = RectF()
    private val path = Path()

    fun tint(c: Int) { color = c; invalidate() }

    private fun stroke() { p.style = Paint.Style.STROKE; p.strokeWidth = 1.9f }
    private fun fill() { p.style = Paint.Style.FILL }

    override fun onDraw(c: Canvas) {
        val s = minOf(width, height) / 24f
        c.save(); c.translate((width - 24 * s) / 2f, (height - 24 * s) / 2f); c.scale(s, s)
        p.color = color; stroke(); path.reset()
        when (kind) {
            EMOJI -> {
                c.drawCircle(12f, 12f, 9f, p)
                r.set(7.5f, 7.5f, 16.5f, 16.5f); c.drawArc(r, 30f, 120f, false, p)
                fill(); c.drawCircle(9f, 9.5f, 1.2f, p); c.drawCircle(15f, 9.5f, 1.2f, p)
            }
            CLIP -> {
                r.set(5f, 5f, 19f, 21f); c.drawRoundRect(r, 2.5f, 2.5f, p)
                r.set(9f, 3f, 15f, 7f); fill(); p.color = (color and 0x00FFFFFF) or 0x33000000; c.drawRoundRect(r, 1.5f, 1.5f, p)
                p.color = color; stroke(); c.drawRoundRect(r, 1.5f, 1.5f, p)
                c.drawLine(8.5f, 12f, 15.5f, 12f, p); c.drawLine(8.5f, 16f, 13.5f, 16f, p)
            }
            EDIT -> {
                path.moveTo(9f, 5f); path.lineTo(15f, 5f); path.moveTo(12f, 5f); path.lineTo(12f, 19f); path.moveTo(9f, 19f); path.lineTo(15f, 19f)
                path.moveTo(6f, 9f); path.lineTo(2.5f, 12f); path.lineTo(6f, 15f)
                path.moveTo(18f, 9f); path.lineTo(21.5f, 12f); path.lineTo(18f, 15f)
                c.drawPath(path, p)
            }
            TUNE -> {
                c.drawLine(4f, 7f, 20f, 7f, p); c.drawLine(4f, 12f, 20f, 12f, p); c.drawLine(4f, 17f, 20f, 17f, p)
                fill(); c.drawCircle(9f, 7f, 2.4f, p); c.drawCircle(15f, 12f, 2.4f, p); c.drawCircle(8f, 17f, 2.4f, p)
            }
            GLOBE -> {
                c.drawCircle(12f, 12f, 9f, p); r.set(8f, 3f, 16f, 21f); c.drawOval(r, p); c.drawLine(3f, 12f, 21f, 12f, p)
            }
            HOME -> {
                path.moveTo(3.5f, 11f); path.lineTo(12f, 4f); path.lineTo(20.5f, 11f)
                path.moveTo(6f, 9.5f); path.lineTo(6f, 20f); path.lineTo(18f, 20f); path.lineTo(18f, 9.5f)
                c.drawPath(path, p)
            }
            PALETTE -> {
                c.drawCircle(12f, 12f, 9f, p); fill()
                c.drawCircle(8f, 11f, 1.5f, p); c.drawCircle(12f, 7.5f, 1.5f, p); c.drawCircle(16f, 11f, 1.5f, p); c.drawCircle(9.5f, 15.5f, 1.5f, p)
            }
            MIC -> {
                r.set(9f, 3f, 15f, 14f); c.drawRoundRect(r, 3f, 3f, p)
                r.set(6f, 8f, 18f, 19f); c.drawArc(r, 0f, 180f, false, p)
                c.drawLine(12f, 19f, 12f, 21.5f, p)
            }
            SUN -> {
                c.drawCircle(12f, 12f, 3.6f, p)
                for (i in 0 until 8) {
                    val a = Math.toRadians(i * 45.0); val cs = Math.cos(a).toFloat(); val sn = Math.sin(a).toFloat()
                    c.drawLine(12f + cs * 7f, 12f + sn * 7f, 12f + cs * 9.6f, 12f + sn * 9.6f, p)
                }
            }
            CHECK -> {
                p.strokeWidth = 2.6f
                path.moveTo(6f, 12.5f); path.lineTo(10.5f, 17f); path.lineTo(18f, 8f)
                c.drawPath(path, p)
            }
        }
        c.restore()
    }
}
