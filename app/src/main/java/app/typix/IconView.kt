package app.typix

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View

/** Crisp line icons drawn on a 24x24 grid (no images, no emoji fonts). */
class IconView(ctx: Context, private val kind: Int, private var color: Int) : View(ctx) {
    companion object { const val EMOJI = 0; const val CLIP = 1; const val EDIT = 2; const val TUNE = 3; const val GLOBE = 4; const val HOME = 5; const val PALETTE = 6; const val MIC = 7; const val SUN = 8; const val CHECK = 9
        const val CLOCK = 10; const val PERSON = 11; const val FLOWER = 12; const val CUP = 13; const val BALL = 14
        const val CAR = 15; const val BULB = 16; const val HEART = 17; const val FLAG = 18; const val BACK = 19 }
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
            CLOCK -> { c.drawCircle(12f, 12f, 9f, p); c.drawLine(12f, 7f, 12f, 12f, p); c.drawLine(12f, 12f, 15.5f, 14f, p) }
            PERSON -> { c.drawCircle(12f, 8f, 3.6f, p); r.set(5f, 14f, 19f, 28f); c.drawArc(r, 200f, 140f, false, p) }
            FLOWER -> {
                for (i in 0 until 5) {
                    val a = Math.toRadians(i * 72.0 - 90.0); c.drawCircle(12f + Math.cos(a).toFloat() * 4.6f, 12f + Math.sin(a).toFloat() * 4.6f, 3.2f, p)
                }
                c.drawCircle(12f, 12f, 1.6f, p)
            }
            CUP -> {
                path.moveTo(5f, 9f); path.lineTo(5f, 15f); path.quadTo(5f, 19f, 9f, 19f); path.lineTo(12f, 19f); path.quadTo(16f, 19f, 16f, 15f); path.lineTo(16f, 9f); path.close()
                c.drawPath(path, p); r.set(14f, 10f, 20f, 16f); c.drawArc(r, -90f, 180f, false, p)
                c.drawLine(8f, 3.5f, 8f, 6f, p); c.drawLine(12f, 3.5f, 12f, 6f, p)
            }
            BALL -> {
                c.drawCircle(12f, 12f, 9f, p); c.drawLine(3f, 12f, 21f, 12f, p); c.drawLine(12f, 3f, 12f, 21f, p)
                r.set(-3f, 6f, 8f, 18f); c.drawArc(r, -60f, 120f, false, p); r.set(16f, 6f, 27f, 18f); c.drawArc(r, 120f, 120f, false, p)
            }
            CAR -> {
                path.moveTo(3.5f, 15f); path.lineTo(3.5f, 11f); path.lineTo(6f, 6.5f); path.lineTo(18f, 6.5f); path.lineTo(20.5f, 11f); path.lineTo(20.5f, 15f); path.close()
                c.drawPath(path, p); c.drawLine(3.5f, 11f, 20.5f, 11f, p); c.drawLine(6f, 18.5f, 6f, 15f, p); c.drawLine(18f, 18.5f, 18f, 15f, p)
                fill(); c.drawCircle(7f, 13.2f, 1.1f, p); c.drawCircle(17f, 13.2f, 1.1f, p)
            }
            BULB -> {
                c.drawCircle(12f, 10f, 6f, p); c.drawLine(9.5f, 17.5f, 14.5f, 17.5f, p); c.drawLine(10f, 20.5f, 14f, 20.5f, p)
                c.drawLine(10f, 14.5f, 10f, 17.5f, p); c.drawLine(14f, 14.5f, 14f, 17.5f, p)
            }
            HEART -> {
                path.moveTo(12f, 20f); path.cubicTo(2f, 13f, 4f, 5f, 9f, 5f); path.cubicTo(11f, 5f, 12f, 7f, 12f, 8f)
                path.cubicTo(12f, 7f, 13f, 5f, 15f, 5f); path.cubicTo(20f, 5f, 22f, 13f, 12f, 20f); c.drawPath(path, p)
            }
            FLAG -> {
                c.drawLine(5f, 21f, 5f, 3.5f, p)
                path.moveTo(5f, 4.5f); path.cubicTo(9f, 2.5f, 13f, 7f, 19f, 4.5f); path.lineTo(19f, 13f); path.cubicTo(13f, 15.5f, 9f, 11f, 5f, 13f); c.drawPath(path, p)
            }
            BACK -> { c.drawLine(19f, 12f, 5f, 12f, p); path.moveTo(11f, 6f); path.lineTo(5f, 12f); path.lineTo(11f, 18f); c.drawPath(path, p) }
        }
        c.restore()
    }
}
