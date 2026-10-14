package app.typix

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.SoundEffectConstants
import android.view.View

/** Letter / symbol keys drawn on one Canvas (no per-key Views): light on RAM and CPU. */
class TypixKeyboardView(ctx: Context, private val l: Listener) : View(ctx) {
    interface Listener { fun onKey(c: Char); fun onSpace(); fun onBackspace(); fun onEnter() }
    var sinhala = true
    private var hints = true; private var sound = false; private var vibrate = true
    private var shift = false; private var symbols = false
    private class Key(val label: String, val code: Int, val x: Float, val w: Float, val row: Int, val r: RectF = RectF())
    private var keys: List<Key> = emptyList()
    private val d = ctx.resources.displayMetrics.density
    private val bg = Paint(); private val keyP = Paint(Paint.ANTI_ALIAS_FLAG); private val hitP = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowP = Paint(Paint.ANTI_ALIAS_FLAG); private val specialP = Paint(Paint.ANTI_ALIAS_FLAG)
    private var rowDp = 54f
    private val big = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; textSize = 19 * d }
    private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; textSize = 13 * d }
    private val hintP = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.RIGHT; textSize = 10 * d; alpha = 170 }
    private var textColor = 0
    private var pressedText = 0xFF0E1224.toInt()
    private var down: Key? = null
    private val rep = object : Runnable { override fun run() { l.onBackspace(); postDelayed(this, 45) } }
    private val hintMap = mapOf('q' to "ඤ", 'w' to "ව", 'e' to "එ", 'r' to "ර", 't' to "ත", 'y' to "ය", 'u' to "උ", 'i' to "ඉ",
        'o' to "ඔ", 'p' to "ප", 'a' to "අ", 's' to "ස", 'd' to "ද", 'f' to "ෆ", 'g' to "ග", 'h' to "හ", 'j' to "ජ", 'k' to "ක",
        'l' to "ල", 'z' to "ශ", 'x' to "ඛ", 'c' to "ච", 'v' to "ව", 'b' to "බ", 'n' to "න", 'm' to "ම")

    init { build(); applySettings() }

    private fun build() {
        val rows = if (symbols) listOf("1234567890", "@#\$&-+()/", "*\"':;!?") else listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
        val o = ArrayList<Key>()
        rows[0].forEachIndexed { i, c -> o.add(Key(c.toString(), c.code, i.toFloat(), 1f, 0)) }
        rows[1].forEachIndexed { i, c -> o.add(Key(c.toString(), c.code, 0.5f + i, 1f, 1)) }
        o.add(if (symbols) Key("=", '='.code, 0f, 1.5f, 2) else Key("⇧", -5, 0f, 1.5f, 2))
        rows[2].forEachIndexed { i, c -> o.add(Key(c.toString(), c.code, 1.5f + i, 1f, 2)) }
        o.add(Key("⌫", -2, 8.5f, 1.5f, 2))
        o.add(Key(if (symbols) "ABC" else "?123", -6, 0f, 1.5f, 3)); o.add(Key(",", ','.code, 1.5f, 1f, 3))
        o.add(Key("␣", -1, 2.5f, 5f, 3)); o.add(Key(".", '.'.code, 7.5f, 1f, 3)); o.add(Key("⏎", -3, 8.5f, 1.5f, 3))
        keys = o; layoutKeys(); invalidate()
    }

    private fun layoutKeys() {
        if (width == 0) return
        val u = width / 10f; val rh = rowDp * d; val pad = 3 * d; val top = 6 * d
        for (k in keys) k.r.set(k.x * u + pad, top + k.row * rh + pad, (k.x + k.w) * u - pad, top + (k.row + 1) * rh - pad)
    }

    private fun mix(a: Int, b: Int, t: Float): Int {
        fun ch(s: Int) = (((a shr s) and 255) * (1 - t) + ((b shr s) and 255) * t).toInt()
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    fun applySettings() {
        val t = Prefs.theme(context)
        bg.color = t.bg; keyP.color = t.key; hitP.color = t.accent; textColor = t.text; hintP.color = t.accent
        shadowP.color = mix(t.key, 0xFF000000.toInt(), 0.35f); specialP.color = mix(t.key, t.bg, 0.45f)
        pressedText = t.onAccent; rowDp = Prefs.rowDp(context).toFloat(); requestLayout(); layoutKeys()
        hints = Prefs.bool(context, "hints", true); sound = Prefs.bool(context, "sound", false); vibrate = Prefs.bool(context, "vibrate", true)
        invalidate()
    }

    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(MeasureSpec.getSize(w), (4 * rowDp * d + 12 * d).toInt())
    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) = layoutKeys()

    override fun onDraw(c: Canvas) {
        c.drawPaint(bg)
        for (k in keys) {
            val lit = k === down || (k.code == -5 && shift)
            val special = k.code < 0 && k.code != -1
            c.drawRoundRect(k.r.left, k.r.top + 2 * d, k.r.right, k.r.bottom + 2 * d, 9 * d, 9 * d, shadowP)
            c.drawRoundRect(k.r, 9 * d, 9 * d, if (lit || k.code == -3) hitP else if (special) specialP else keyP)
            val p = if (k.label.length > 1 || k.code == -1) small else big
            p.color = if (lit || k.code == -3) pressedText else textColor
            val isLetter = k.code > 0 && k.label[0].isLetter()
            val t = when {
                k.code == -1 -> if (sinhala) "typix · සිංහල" else "typix · English"
                isLetter && shift -> k.label.uppercase()
                else -> k.label
            }
            c.drawText(t, k.r.centerX(), k.r.centerY() + (if (p === big) 7 else 5) * d, p)
            if (hints && sinhala && isLetter && !shift) hintMap[k.code.toChar()]?.let { c.drawText(it, k.r.right - 5 * d, k.r.top + 13 * d, hintP) }
        }
    }

    private fun hit(x: Float, y: Float) = keys.firstOrNull { it.r.contains(x, y) }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                down = hit(e.x, e.y); invalidate()
                val k = down ?: return true
                if (vibrate) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                if (sound) playSoundEffect(SoundEffectConstants.CLICK)
                if (k.code == -2) { l.onBackspace(); postDelayed(rep, 400) }
            }
            MotionEvent.ACTION_UP -> {
                removeCallbacks(rep)
                val k = down; down = null; invalidate()
                if (k != null && k === hit(e.x, e.y)) when (k.code) {
                    -1 -> l.onSpace(); -3 -> l.onEnter(); -2 -> {}
                    -5 -> { shift = !shift; invalidate() }
                    -6 -> { symbols = !symbols; shift = false; build() }
                    else -> {
                        val ch = k.code.toChar()
                        l.onKey(if (shift && ch.isLetter()) ch.uppercaseChar() else ch)
                        if (shift) { shift = false; invalidate() }
                    }
                }
            }
            MotionEvent.ACTION_CANCEL -> { removeCallbacks(rep); down = null; invalidate() }
        }
        return true
    }
}
