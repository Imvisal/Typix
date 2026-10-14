package app.typix

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View

/** One custom View drawn on a Canvas (no per-key Views): light on RAM/CPU, no allocation while drawing. */
class TypixKeyboardView(ctx: Context, private val l: Listener) : View(ctx) {
    interface Listener {
        fun onKey(c: Char); fun onSpace(); fun onBackspace(); fun onEnter(); fun onToggleMode()
    }
    var sinhala = true
    private class Key(val label: String, val code: Int, val r: RectF = RectF())
    private val keys = ArrayList<Key>()
    private val d = ctx.resources.displayMetrics.density
    private val bg = Paint().apply { color = 0xFF0E1224.toInt() }
    private val keyP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2A3262.toInt() }
    private val hitP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFC8F135.toInt() }
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFEEF1FF.toInt(); textAlign = Paint.Align.CENTER; textSize = 19 * d }
    private var down: Key? = null

    init {
        for (row in listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")) for (ch in row) keys.add(Key(ch.toString(), ch.code))
        keys.add(Key("", -4)); keys.add(Key("␣", -1)); keys.add(Key("⌫", -2)); keys.add(Key("⏎", -3))
    }

    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(MeasureSpec.getSize(w), (4 * 54 * d + 12 * d).toInt())

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        val u = w / 10f; val rh = 54 * d; val pad = 3 * d; val top = 6 * d
        fun put(k: Key, x: Float, wd: Float, row: Int) =
            k.r.set(x * u + pad, top + row * rh + pad, (x + wd) * u - pad, top + (row + 1) * rh - pad)
        var i = 0
        for ((row, n) in listOf(10, 9, 7).withIndex()) {
            val off = when (row) { 0 -> 0f; 1 -> 0.5f; else -> 1.5f }
            for (c in 0 until n) put(keys[i++], off + c, 1f, row)
        }
        put(keys[i++], 0f, 2f, 3); put(keys[i++], 2f, 5f, 3); put(keys[i++], 7f, 1.5f, 3); put(keys[i], 8.5f, 1.5f, 3)
    }

    override fun onDraw(c: Canvas) {
        c.drawPaint(bg)
        for (k in keys) {
            c.drawRoundRect(k.r, 8 * d, 8 * d, if (k === down) hitP else keyP)
            val t = if (k.code == -4) (if (sinhala) "සිං" else "En") else k.label
            c.drawText(t, k.r.centerX(), k.r.centerY() + 7 * d, txt)
        }
    }

    private fun hit(x: Float, y: Float) = keys.firstOrNull { it.r.contains(x, y) }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { down = hit(e.x, e.y); invalidate() }
            MotionEvent.ACTION_UP -> {
                val k = down; down = null; invalidate()
                if (k != null && k === hit(e.x, e.y)) when (k.code) {
                    -1 -> l.onSpace(); -2 -> l.onBackspace(); -3 -> l.onEnter(); -4 -> l.onToggleMode()
                    else -> l.onKey(k.code.toChar())
                }
            }
            MotionEvent.ACTION_CANCEL -> { down = null; invalidate() }
        }
        return true
    }
}
