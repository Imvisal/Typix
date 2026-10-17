package app.typix

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

/** Colours offered for key / letter / number customising. 0 = "theme default". */
object Swatches {
    val colors = intArrayOf(
        0, 0xFFFFFFFF.toInt(), 0xFFD9DEF2.toInt(), 0xFF14182B.toInt(), 0xFF2A3262.toInt(), 0xFFC8F135.toInt(), 0xFF2ECC71.toInt(), 0xFF12A37B.toInt(),
        0xFF4CC9F0.toInt(), 0xFF2B3BFF.toInt(), 0xFF8B5CF6.toInt(), 0xFFE879F9.toInt(), 0xFFFF4D6D.toInt(), 0xFFFF7A45.toInt(), 0xFFFFD23F.toInt(), 0xFF8A93C0.toInt()
    )
}

/** One round colour swatch; a ring marks the selected one; "default" (0) shows the theme colour with a slash. */
class SwatchView(ctx: Context, private val color: Int, private val shown: Int, private val selected: Boolean, private val ring: Int, private val edge: Int,
                 private val onTap: () -> Unit) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val d = ctx.resources.displayMetrics.density
    init { setOnClickListener { onTap() } }
    override fun onMeasure(w: Int, h: Int) { val s = MeasureSpec.getSize(w); setMeasuredDimension(s, (40 * d).toInt()) }
    override fun onDraw(c: Canvas) {
        val cx = width / 2f; val cy = height / 2f; val r = Math.min(width / 2f, height / 2f) - 5 * d
        p.style = Paint.Style.FILL; p.color = shown; c.drawCircle(cx, cy, r, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 1f * d; p.color = edge; c.drawCircle(cx, cy, r, p)
        if (color == 0) { p.strokeWidth = 2f * d; p.color = edge; val o = r * 0.55f; c.drawLine(cx - o, cy + o, cx + o, cy - o, p) }
        if (selected) { p.strokeWidth = 2.5f * d; p.color = ring; c.drawCircle(cx, cy, r + 3.5f * d, p) }
    }
}

/** Live mini keyboard that shows the chosen key size / key colour / letter colour / number colour. */
class KeyPreviewView(ctx: Context, private val th: Theme, private val keyC: Int, private val textC: Int, private val numC: Int, private val sizePct: Int) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val t = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Fonts.get(ctx, 500) }
    private val r = RectF()
    private val d = ctx.resources.displayMetrics.density
    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(MeasureSpec.getSize(w), (150 * d).toInt())
    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        p.style = Paint.Style.FILL; p.color = th.bg; r.set(0f, 0f, w, h); c.drawRoundRect(r, 22 * d, 22 * d, p)
        val rows = listOf("qwertyu", "asdfghj", "zxcvbnm")
        val padX = 12 * d; val padY = 10 * d
        val u = (w - 2 * padX) / 7f; val rh = (h - 2 * padY) / 3f
        val s = sizePct / 100f
        for ((i, row) in rows.withIndex()) for ((j, ch) in row.withIndex()) {
            val cx = padX + (j + 0.5f) * u; val cy = padY + (i + 0.5f) * rh
            val kw = (u - 5 * d) * s; val kh = (rh - 7 * d) * s
            r.set(cx - kw / 2, cy - kh / 2, cx + kw / 2, cy + kh / 2)
            p.color = keyC; c.drawRoundRect(r, 9 * d, 9 * d, p)
            t.color = textC; t.textSize = 18 * d * Math.min(1f, 0.85f + s * 0.15f); c.drawText(ch.toString(), cx, cy + 6.5f * d, t)
            if (i == 0) { t.color = numC; t.textSize = 9 * d; c.drawText("${(j + 1) % 10}", r.right - 6 * d, r.top + 11 * d, t) }
        }
    }
}
