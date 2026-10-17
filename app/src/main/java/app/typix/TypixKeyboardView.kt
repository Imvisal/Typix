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

/**
 * Flat, modern keyboard drawn on one Canvas (no per-key Views): light on RAM and CPU.
 * Look follows the Typix design: soft rounded keys, tiny Sinhala hint top-right, lime return key,
 * "typix · සිංහල" space bar.
 */
class TypixKeyboardView(ctx: Context, private val l: Listener) : View(ctx) {
    interface Listener { fun onKey(c: Char); fun onSpace(); fun onBackspace(); fun onEnter(); fun onLang(); fun onPicker() }
    var sinhala = true
        set(v) { field = v; invalidate() }
    private var hints = true; private var sound = false; private var vibrate = true; private var borders = false
    private var shift = false; private var symbols = false
    private class Key(val label: String, val code: Int, val x: Float, val w: Float, val row: Int, val r: RectF = RectF())
    private var keys: List<Key> = emptyList()
    private val d = ctx.resources.displayMetrics.density
    private val bg = Paint()
    private val fillP = Paint(Paint.ANTI_ALIAS_FLAG); private val specialP = Paint(Paint.ANTI_ALIAS_FLAG); private val litP = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokeP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1f * d }
    private val accentP = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ic = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; strokeWidth = 2f * d }
    private val path = Path()
    private var rowDp = 54f
    private val face = Fonts.get(ctx, 500); private val faceBold = Fonts.get(ctx, 600)
    private val big = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; textSize = 22 * d; typeface = face }
    private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; textSize = 15 * d; typeface = faceBold }
    private val spaceP = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; textSize = 13 * d; typeface = face; letterSpacing = 0.12f }
    private val hintP = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.RIGHT; textSize = 10 * d }
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
    /** Letters you get with Shift (capital): A = ඇ, N = ණ, L = ළ ... shown as the key hint while Shift is on. */
    private val shiftMap = mapOf('a' to "ඇ", 'k' to "ඛ", 'n' to "ණ", 'l' to "ළ", 's' to "ෂ", 't' to "ඨ", 'd' to "ඪ", 'b' to "භ", 'g' to "ඝ", 'c' to "ඡ", 'j' to "ඣ")
    private var hintBase = 0

    init { build(); applySettings() }

    private fun build() {
        val rows = if (symbols) listOf("1234567890", "@#\$&-+()/", ",\"':;!?") else listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
        val o = ArrayList<Key>()
        rows[0].forEachIndexed { i, c -> o.add(Key(c.toString(), c.code, i.toFloat(), 1f, 0)) }
        rows[1].forEachIndexed { i, c -> o.add(Key(c.toString(), c.code, 0.5f + i, 1f, 1)) }
        o.add(if (symbols) Key("=", '='.code, 0f, 1.5f, 2) else Key("⇧", -5, 0f, 1.5f, 2))
        rows[2].forEachIndexed { i, c -> o.add(Key(c.toString(), c.code, 1.5f + i, 1f, 2)) }
        o.add(Key("⌫", -2, 8.5f, 1.5f, 2))
        o.add(Key(if (symbols) "ABC" else "?123", -6, 0f, 1.5f, 3)); o.add(Key("🌐", -7, 1.5f, 1f, 3))
        o.add(Key("␣", -1, 2.5f, 5f, 3)); o.add(Key(".", '.'.code, 7.5f, 1f, 3)); o.add(Key("⏎", -3, 8.5f, 1.5f, 3))
        keys = o; layoutKeys(); invalidate()
    }

    private fun layoutKeys() {
        if (width == 0) return
        val u = width / 10f; val rh = rowDp * d; val padX = 2.5f * d; val padY = 3.5f * d; val top = 4 * d
        for (k in keys) k.r.set(k.x * u + padX, top + k.row * rh + padY, (k.x + k.w) * u - padX, top + (k.row + 1) * rh - padY)
    }

    fun applySettings() {
        val t = Prefs.theme(context)
        bg.color = t.bg; accentP.color = t.accent; accent = t.accent; textColor = t.text; onAccent = t.onAccent
        fillP.color = t.key
        specialP.color = if (lumaOf(t.bg) < 128) mixColor(t.key, t.bg, 0.25f) else mixColor(t.key, t.bg, 0.55f)
        litP.color = mixColor(t.key, t.text, if (lumaOf(t.bg) < 128) 0.16f else 0.10f)
        strokeP.color = mixColor(t.key, t.text, 0.22f)
        dimColor = mixColor(t.text, t.bg, 0.45f)
        hintBase = mixColor(t.text, t.bg, 0.55f); hintP.color = hintBase
        rowDp = Prefs.rowDp(context).toFloat(); requestLayout()
        hints = Prefs.bool(context, "hints", true); sound = Prefs.bool(context, "sound", false)
        vibrate = Prefs.bool(context, "vibrate", true); borders = Prefs.bool(context, "borders", false)
        layoutKeys(); invalidate()
    }

    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(MeasureSpec.getSize(w), (4 * rowDp * d + 8 * d).toInt())
    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) = layoutKeys()

    override fun onDraw(c: Canvas) {
        c.drawPaint(bg)
        val rad = 12 * d
        for (k in keys) {
            val pressed = k === down
            val cx = k.r.centerX(); val cy = k.r.centerY()
            if (k.code == -3) {
                accentP.alpha = if (pressed) 200 else 255
                c.drawRoundRect(k.r, rad, rad, accentP); accentP.alpha = 255
                ic.color = onAccent; ic.strokeWidth = 2.2f * d; path.reset()
                path.moveTo(cx + 7 * d, cy - 6 * d); path.lineTo(cx + 7 * d, cy + 1 * d); path.lineTo(cx - 7 * d, cy + 1 * d)
                path.moveTo(cx - 2.5f * d, cy - 3.5f * d); path.lineTo(cx - 7 * d, cy + 1 * d); path.lineTo(cx - 2.5f * d, cy + 5.5f * d)
                c.drawPath(path, ic); ic.strokeWidth = 2f * d
                continue
            }
            val base = if (k.code < 0 && k.code != -1) specialP else fillP
            c.drawRoundRect(k.r, rad, rad, if (pressed) litP else base)
            if (borders) c.drawRoundRect(k.r, rad, rad, strokeP)
            when (k.code) {
                -1 -> {
                    spaceP.color = dimColor
                    c.drawText(if (sinhala) "typix · සිංහල" else "typix · English", cx, cy + 4.5f * d, spaceP)
                }
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
                    ic.color = textColor; ic.strokeWidth = 1.8f * d
                    c.drawCircle(cx, cy, 8.5f * d, ic)
                    c.drawOval(cx - 3.5f * d, cy - 8.5f * d, cx + 3.5f * d, cy + 8.5f * d, ic)
                    c.drawLine(cx - 8.5f * d, cy, cx + 8.5f * d, cy, ic); ic.strokeWidth = 2f * d
                }
                -6 -> { small.color = textColor; c.drawText(k.label, cx, cy + 5.5f * d, small) }
                else -> {
                    val isLetter = k.code > 0 && k.label[0].isLetter()
                    big.color = textColor
                    c.drawText(if (isLetter && shift) k.label.uppercase() else k.label, cx, cy + 7.5f * d, big)
                    val hint: String? = when {
                        hints && sinhala && isLetter -> if (shift) shiftMap[k.code.toChar()] else hintMap[k.code.toChar()]
                        k.row == 0 && !symbols && isLetter -> "1234567890"[k.x.toInt()].toString()
                        else -> null
                    }
                    hintP.color = if (shift) accent else hintBase
                    if (hint != null) c.drawText(hint, k.r.right - 5 * d, k.r.top + 12.5f * d, hintP)
                }
            }
        }
    }

    private fun hit(x: Float, y: Float) = keys.firstOrNull { it.r.contains(x, y) }
        ?: keys.minByOrNull { val dx = x - it.r.centerX(); val dy = y - it.r.centerY(); dx * dx + dy * dy }?.takeIf {
            // forgive taps that land in the gaps between keys
            x >= it.r.left - 4 * d && x <= it.r.right + 4 * d && y >= it.r.top - 5 * d && y <= it.r.bottom + 5 * d
        }

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
