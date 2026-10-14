package app.typix

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.SoundEffectConstants
import android.view.View

/** Flat, modern keyboard drawn on one Canvas (no per-key Views): light on RAM and CPU. */
class TypixKeyboardView(ctx: Context, private val l: Listener) : View(ctx) {
    interface Listener { fun onKey(c: Char); fun onSpace(); fun onBackspace(); fun onEnter(); fun onLang(); fun onPicker() }
    var sinhala = true
    private var hints = true; private var sound = false; private var vibrate = true
    private var shift = false; private var symbols = false
    private class Key(val label: String, val code: Int, val x: Float, val w: Float, val row: Int, val r: RectF = RectF())
    private var keys: List<Key> = emptyList()
    private val d = ctx.resources.displayMetrics.density
    private val bg = Paint()
    private val litP = Paint(Paint.ANTI_ALIAS_FLAG); private val accentP = Paint(Paint.ANTI_ALIAS_FLAG); private val pillP = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ic = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; strokeWidth = 1.9f * d }
    private val path = Path(); private val tmp = RectF()
    private var rowDp = 54f
    private val big = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; textSize = 21 * d }
    private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; textSize = 12.5f * d }
    private val hintP = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.RIGHT; textSize = 10 * d }
    private val digitP = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.RIGHT; textSize = 10.5f * d }
    private var textColor = 0; private var dimColor = 0; private var onAccent = 0; private var accent = 0
    private var down: Key? = null
    private var consumed = false
    private val rep = object : Runnable { override fun run() { l.onBackspace(); postDelayed(this, 45) } }
    private val longPress = object : Runnable {
        override fun run() {
            val k = down ?: return
            if (k.code == -7) { consumed = true; l.onPicker(); return }
            if (k.row == 0 && !symbols && k.code > 0) {
                val i = "qwertyuiop".indexOf(k.code.toChar())
                if (i >= 0) { consumed = true; if (vibrate) performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); l.onKey("1234567890"[i]) }
            }
        }
    }
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
        o.add(Key(if (symbols) "ABC" else "?123", -6, 0f, 1.5f, 3)); o.add(Key("🌐", -7, 1.5f, 1f, 3)); o.add(Key(",", ','.code, 2.5f, 1f, 3))
        o.add(Key("␣", -1, 3.5f, 4f, 3)); o.add(Key(".", '.'.code, 7.5f, 1f, 3)); o.add(Key("⏎", -3, 8.5f, 1.5f, 3))
        keys = o; layoutKeys(); invalidate()
    }

    private fun layoutKeys() {
        if (width == 0) return
        val u = width / 10f; val rh = rowDp * d; val pad = 2 * d; val top = 6 * d
        for (k in keys) k.r.set(k.x * u + pad, top + k.row * rh + pad, (k.x + k.w) * u - pad, top + (k.row + 1) * rh - pad)
    }

    private fun mix(a: Int, b: Int, t: Float): Int {
        fun ch(s: Int) = (((a shr s) and 255) * (1 - t) + ((b shr s) and 255) * t).toInt()
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    fun applySettings() {
        val t = Prefs.theme(context)
        bg.color = t.bg; litP.color = t.key; accentP.color = t.accent; accent = t.accent; textColor = t.text; onAccent = t.onAccent
        dimColor = mix(t.text, t.bg, 0.4f); pillP.color = mix(t.key, t.bg, 0.3f); hintP.color = t.accent; digitP.color = dimColor
        rowDp = Prefs.rowDp(context).toFloat(); requestLayout(); layoutKeys()
        hints = Prefs.bool(context, "hints", true); sound = Prefs.bool(context, "sound", false); vibrate = Prefs.bool(context, "vibrate", true)
        invalidate()
    }

    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(MeasureSpec.getSize(w), (4 * rowDp * d + 12 * d).toInt())
    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) = layoutKeys()

    override fun onDraw(c: Canvas) {
        c.drawPaint(bg)
        for (k in keys) {
            val lit = k === down || (k.code == -5 && shift)
            val cx = k.r.centerX(); val cy = k.r.centerY()
            when (k.code) {
                -1 -> {
                    val h = k.r.height() * 0.56f
                    tmp.set(k.r.left + 2 * d, cy - h / 2, k.r.right - 2 * d, cy + h / 2)
                    c.drawRoundRect(tmp, h / 2, h / 2, if (lit) accentP else pillP)
                    small.color = if (lit) onAccent else dimColor
                    c.drawText(if (sinhala) "සිංහල" else "English", cx, cy + 4.5f * d, small)
                }
                -3 -> {
                    c.drawCircle(cx, cy, minOf(k.r.width(), k.r.height()) / 2f - d, accentP)
                    ic.color = onAccent; path.reset()
                    path.moveTo(cx - 3 * d, cy - 6 * d); path.lineTo(cx + 3 * d, cy); path.lineTo(cx - 3 * d, cy + 6 * d)
                    c.drawPath(path, ic)
                }
                else -> {
                    if (lit) c.drawRoundRect(k.r, 10 * d, 10 * d, litP)
                    when (k.code) {
                        -5 -> {
                            ic.color = if (shift) accent else textColor; path.reset()
                            path.moveTo(cx, cy - 8 * d); path.lineTo(cx + 8 * d, cy); path.lineTo(cx + 4 * d, cy); path.lineTo(cx + 4 * d, cy + 7 * d)
                            path.lineTo(cx - 4 * d, cy + 7 * d); path.lineTo(cx - 4 * d, cy); path.lineTo(cx - 8 * d, cy); path.close()
                            ic.style = if (shift) Paint.Style.FILL_AND_STROKE else Paint.Style.STROKE
                            c.drawPath(path, ic); ic.style = Paint.Style.STROKE
                        }
                        -2 -> {
                            ic.color = textColor; path.reset()
                            path.moveTo(cx - 10 * d, cy); path.lineTo(cx - 4 * d, cy - 7 * d); path.lineTo(cx + 10 * d, cy - 7 * d)
                            path.lineTo(cx + 10 * d, cy + 7 * d); path.lineTo(cx - 4 * d, cy + 7 * d); path.close()
                            c.drawPath(path, ic)
                            c.drawLine(cx - d, cy - 3 * d, cx + 5 * d, cy + 3 * d, ic); c.drawLine(cx + 5 * d, cy - 3 * d, cx - d, cy + 3 * d, ic)
                        }
                        -7 -> {
                            ic.color = textColor
                            c.drawCircle(cx, cy, 8.5f * d, ic); tmp.set(cx - 3.5f * d, cy - 8.5f * d, cx + 3.5f * d, cy + 8.5f * d); c.drawOval(tmp, ic)
                            c.drawLine(cx - 8.5f * d, cy, cx + 8.5f * d, cy, ic)
                        }
                        -6 -> {
                            ic.color = dimColor; ic.strokeWidth = 1.4f * d
                            c.drawCircle(cx, cy - 11 * d, 4.5f * d, ic); ic.strokeWidth = 1.9f * d
                            small.color = textColor; c.drawText(k.label, cx, cy + 8 * d, small)
                        }
                        else -> {
                            val isLetter = k.code > 0 && k.label[0].isLetter()
                            big.color = textColor
                            c.drawText(if (isLetter && shift) k.label.uppercase() else k.label, cx, cy + 7 * d, big)
                            if (k.row == 0 && !symbols) c.drawText("1234567890"[k.x.toInt()].toString(), k.r.right - 5 * d, k.r.top + 12 * d, digitP)
                            if (hints && sinhala && isLetter && !shift) hintMap[k.code.toChar()]?.let { c.drawText(it, k.r.right - 5 * d, k.r.bottom - 6 * d, hintP) }
                        }
                    }
                }
            }
        }
    }

    private fun hit(x: Float, y: Float) = keys.firstOrNull { it.r.contains(x, y) }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                down = hit(e.x, e.y); consumed = false; invalidate()
                val k = down ?: return true
                if (vibrate) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                if (sound) playSoundEffect(SoundEffectConstants.CLICK)
                if (k.code == -2) { l.onBackspace(); postDelayed(rep, 400) } else postDelayed(longPress, 380)
            }
            MotionEvent.ACTION_UP -> {
                removeCallbacks(rep); removeCallbacks(longPress)
                val k = down; down = null; invalidate()
                if (k != null && !consumed && k === hit(e.x, e.y)) when (k.code) {
                    -1 -> l.onSpace(); -3 -> l.onEnter(); -2 -> {}
                    -7 -> l.onLang()
                    -5 -> { shift = !shift; invalidate() }
                    -6 -> { symbols = !symbols; shift = false; build() }
                    else -> {
                        val ch = k.code.toChar()
                        l.onKey(if (shift && ch.isLetter()) ch.uppercaseChar() else ch)
                        if (shift) { shift = false; invalidate() }
                    }
                }
            }
            MotionEvent.ACTION_CANCEL -> { removeCallbacks(rep); removeCallbacks(longPress); down = null; invalidate() }
        }
        return true
    }
}
