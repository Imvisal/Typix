package app.typix

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.media.AudioManager
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.SoundEffectConstants
import android.view.View

/**
 * Flat, modern keyboard drawn on one Canvas (no per-key Views): light on RAM and CPU.
 * - multi-touch: fast typing with two thumbs never drops or re-orders keys
 * - only the touched key is redrawn (dirty rect), nothing is allocated while drawing
 * - space-bar swipe moves the cursor, long-press "." gives punctuation, double-tap Shift = Caps Lock
 */
class TypixKeyboardView(ctx: Context, private val l: Listener) : View(ctx) {
    interface Listener {
        fun onKey(c: Char); fun onSpace(); fun onBackspace(); fun onEnter(); fun onLang(); fun onPicker(); fun onCursor(dir: Int)
    }
    var sinhala = true
        set(v) { field = v; invalidate() }
    /** 0 = return arrow, 1 = search, 2 = send, 3 = go / next, 4 = done */
    var enterKind = 0
        set(v) { if (field != v) { field = v; invalidate() } }
    private var hints = true; private var soundStyle = 0; private var vibrate = false; private var borders = false; private var swipeCursor = true
    private var shift = false; private var caps = false; private var symbols = false; private var page2 = false
    private var lastShiftTap = 0L

    private class Key(val label: String, val upper: String, val code: Int, val x: Float, val w: Float, val row: Int, val r: RectF = RectF())
    private var keys: Array<Key> = emptyArray()
    private val d = ctx.resources.displayMetrics.density
    private val bg = Paint()
    private val fillP = Paint(Paint.ANTI_ALIAS_FLAG); private val specialP = Paint(Paint.ANTI_ALIAS_FLAG); private val litP = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokeP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1f * d }
    private val accentP = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ic = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; strokeWidth = 2f * d }
    private val path = Path()
    private val tmp = RectF(); private val dirty = Rect()
    private var rowDp = 54f
    private val face = Fonts.get(ctx, 500); private val faceBold = Fonts.get(ctx, 600)
    private val big = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; textSize = 22 * d; typeface = face }
    private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; textSize = 15 * d; typeface = faceBold }
    private val spaceP = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; textSize = 13 * d; typeface = face; letterSpacing = 0.12f }
    private val hintP = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.RIGHT; textSize = 10 * d }
    private var textColor = 0; private var dimColor = 0; private var onAccent = 0; private var accent = 0; private var hintBase = 0

    // precomputed hint tables (index = char code) so drawing never allocates
    private val hintTab = arrayOfNulls<String>(128)
    private val shiftTab = arrayOfNulls<String>(128)
    private val digitTab = Array(10) { ('0' + ((it + 1) % 10)).toString() }

    // touch state, one slot per pointer id
    private val maxP = 10
    private val pk = arrayOfNulls<Key>(maxP)
    private val fired = BooleanArray(maxP)
    private val startX = FloatArray(maxP)
    private var lpId = -1                  // pointer waiting for / showing the long-press popup
    private var spaceId = -1; private var spaceAcc = 0f
    private var popupKey: Key? = null; private var popupSel = -1
    private val popupChars = ".,?!:;-_'\"@"
    private val popupRect = RectF()

    private val rep = object : Runnable { override fun run() { l.onBackspace(); postDelayed(this, 42) } }
    private val longPress = object : Runnable {
        override fun run() {
            val id = lpId; if (id < 0) return
            val k = pk[id] ?: return
            if (k.code == -7) { fired[id] = true; l.onPicker(); return }
            if (k.code == '.'.code) { openPopup(k); return }
            if (k.row == 0 && !symbols && k.code > 0 && k.code < 128) {
                val i = "qwertyuiop".indexOf(k.code.toChar())
                if (i >= 0) { fired[id] = true; if (vibrate) performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); l.onKey("1234567890"[i]) }
            }
        }
    }

    init {
        val h = mapOf('q' to "ද", 'w' to "ව", 'e' to "එ", 'r' to "ර", 't' to "ත", 'y' to "ය", 'u' to "උ", 'i' to "ඉ",
            'o' to "ඔ", 'p' to "ප", 'a' to "අ", 's' to "ස", 'd' to "ද", 'f' to "ෆ", 'g' to "ග", 'h' to "හ", 'j' to "ජ", 'k' to "ක",
            'l' to "ල", 'z' to "ශ", 'x' to "ං", 'c' to "ච", 'v' to "ව", 'b' to "බ", 'n' to "න", 'm' to "ම")
        for ((c, s) in h) hintTab[c.code] = s
        // Shift (capital) letters: A = ඇ, N = ණ, L = ළ ...
        val s = mapOf('q' to "ධ", 'a' to "ඇ", 'k' to "ඛ", 'n' to "ණ", 'l' to "ළ", 's' to "ෂ", 'x' to "ං", 't' to "ඨ", 'd' to "ඪ", 'b' to "භ", 'g' to "ඝ", 'c' to "ඡ", 'j' to "ඣ")
        for ((c, v) in s) shiftTab[c.code] = v
        build(); applySettings()
    }

    private fun build() {
        val rows = when {
            !symbols -> listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
            page2 -> listOf("~`|•√π÷×¶∆", "£€¥^°={}\\%", "©®™[]<>")
            else -> listOf("1234567890", "@#\$&-+()/", "*\"':;!?")
        }
        val o = ArrayList<Key>()
        fun add(label: String, code: Int, x: Float, w: Float, row: Int) = o.add(Key(label, label.uppercase(), code, x, w, row))
        rows[0].forEachIndexed { i, c -> add(c.toString(), c.code, i.toFloat(), 1f, 0) }
        rows[1].forEachIndexed { i, c -> add(c.toString(), c.code, 0.5f + i, 1f, 1) }
        if (symbols) add(if (page2) "1/2" else "=\\<", -8, 0f, 1.5f, 2) else add("⇧", -5, 0f, 1.5f, 2)
        rows[2].forEachIndexed { i, c -> add(c.toString(), c.code, 1.5f + i, 1f, 2) }
        add("⌫", -2, 8.5f, 1.5f, 2)
        add(if (symbols) "ABC" else "?123", -6, 0f, 1.5f, 3); add("🌐", -7, 1.5f, 1f, 3); add(",", ','.code, 2.5f, 1f, 3)
        add("␣", -1, 3.5f, 4f, 3); add(".", '.'.code, 7.5f, 1f, 3); add("⏎", -3, 8.5f, 1.5f, 3)
        keys = o.toTypedArray(); layoutKeys(); invalidate()
    }

    private fun layoutKeys() {
        if (width == 0) return
        val u = width / 10f; val rh = rowDp * d; val padX = gap * 2.5f * d; val padY = gap * 3.5f * d; val top = 4 * d
        for (k in keys) k.r.set(k.x * u + padX, top + k.row * rh + padY, (k.x + k.w) * u - padX, top + (k.row + 1) * rh - padY)
    }

    private var gap = 1f
    private var rad = 12f * 3f

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
        hints = Prefs.bool(context, "hints", true); soundStyle = Prefs.int(context, "soundstyle", 0).coerceIn(0, 2)
        vibrate = Prefs.bool(context, "vibrate", false); borders = Prefs.bool(context, "borders", false)
        swipeCursor = Prefs.bool(context, "swipecursor", true)
        gap = floatArrayOf(0.6f, 1f, 1.5f)[Prefs.int(context, "keygap", 1).coerceIn(0, 2)]
        rad = floatArrayOf(12f, 5f, 22f)[Prefs.int(context, "keyshape", 0).coerceIn(0, 2)] * d
        val ts = floatArrayOf(0.88f, 1f, 1.12f)[Prefs.int(context, "keytext", 1).coerceIn(0, 2)]
        big.textSize = 22 * d * ts; small.textSize = 15 * d * ts
        layoutKeys(); invalidate()
    }

    /** Auto-capitalisation from the editor (start of sentence): one-shot shift. */
    fun setAutoShift(on: Boolean) {
        if (caps || symbols || shift == on) return
        shift = on; invalidate()
    }

    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(MeasureSpec.getSize(w), (4 * rowDp * d + 8 * d).toInt())
    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) = layoutKeys()

    private fun isDown(k: Key): Boolean { for (i in 0 until maxP) if (pk[i] === k) return true; return false }

    override fun onDraw(c: Canvas) {
        c.drawPaint(bg)
        val upper = shift || caps
        for (k in keys) {
            val pressed = isDown(k)
            val cx = k.r.centerX(); val cy = k.r.centerY()
            if (k.code == -3) {
                accentP.alpha = if (pressed) 200 else 255
                c.drawRoundRect(k.r, rad, rad, accentP); accentP.alpha = 255
                drawEnter(c, cx, cy)
                continue
            }
            val base = if (k.code < 0 && k.code != -1) specialP else fillP
            c.drawRoundRect(k.r, rad, rad, if (pressed) litP else base)
            if (borders) c.drawRoundRect(k.r, rad, rad, strokeP)
            when (k.code) {
                -1 -> {
                    spaceP.color = dimColor
                    c.drawText(if (sinhala) "Typix · සිංහල" else "Typix · English", cx, cy + 4.5f * d, spaceP)
                }
                -5 -> {
                    ic.color = if (upper) accent else textColor; path.reset()
                    path.moveTo(cx, cy - 8 * d); path.lineTo(cx + 8 * d, cy); path.lineTo(cx + 4 * d, cy); path.lineTo(cx + 4 * d, cy + 6 * d)
                    path.lineTo(cx - 4 * d, cy + 6 * d); path.lineTo(cx - 4 * d, cy); path.lineTo(cx - 8 * d, cy); path.close()
                    ic.style = if (upper) Paint.Style.FILL_AND_STROKE else Paint.Style.STROKE
                    c.drawPath(path, ic); ic.style = Paint.Style.STROKE
                    if (caps) c.drawLine(cx - 4 * d, cy + 10 * d, cx + 4 * d, cy + 10 * d, ic)
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
                -6, -8 -> { small.color = textColor; c.drawText(k.label, cx, cy + 5.5f * d, small) }
                else -> {
                    val isLetter = k.code in 97..122
                    big.color = textColor
                    c.drawText(if (isLetter && upper) k.upper else k.label, cx, cy + 7.5f * d, big)
                    var hint: String? = null
                    if (isLetter) {
                        if (hints && sinhala) hint = if (upper) shiftTab[k.code] else hintTab[k.code]
                        else if (k.row == 0 && !symbols) hint = digitTab[k.x.toInt()]
                    }
                    if (hint != null) { hintP.color = if (upper) accent else hintBase; c.drawText(hint, k.r.right - 5 * d, k.r.top + 12.5f * d, hintP) }
                }
            }
        }
        popupKey?.let { drawPopup(c, it) }
    }

    private fun drawEnter(c: Canvas, cx: Float, cy: Float) {
        ic.color = onAccent; ic.strokeWidth = 2.2f * d; path.reset()
        when (enterKind) {
            1 -> { c.drawCircle(cx - 1.5f * d, cy - 1.5f * d, 6 * d, ic); c.drawLine(cx + 3 * d, cy + 3 * d, cx + 8 * d, cy + 8 * d, ic) }
            2 -> { path.moveTo(cx - 8 * d, cy - 7 * d); path.lineTo(cx + 9 * d, cy); path.lineTo(cx - 8 * d, cy + 7 * d); path.lineTo(cx - 5 * d, cy); path.close(); c.drawPath(path, ic) }
            3 -> { path.moveTo(cx - 8 * d, cy); path.lineTo(cx + 8 * d, cy); path.moveTo(cx + 3 * d, cy - 5 * d); path.lineTo(cx + 8 * d, cy); path.lineTo(cx + 3 * d, cy + 5 * d); c.drawPath(path, ic) }
            4 -> { path.moveTo(cx - 7 * d, cy + 1 * d); path.lineTo(cx - 2 * d, cy + 6 * d); path.lineTo(cx + 8 * d, cy - 5 * d); c.drawPath(path, ic) }
            else -> {
                path.moveTo(cx + 7 * d, cy - 6 * d); path.lineTo(cx + 7 * d, cy + 1 * d); path.lineTo(cx - 7 * d, cy + 1 * d)
                path.moveTo(cx - 2.5f * d, cy - 3.5f * d); path.lineTo(cx - 7 * d, cy + 1 * d); path.lineTo(cx - 2.5f * d, cy + 5.5f * d)
                c.drawPath(path, ic)
            }
        }
        ic.strokeWidth = 2f * d
    }

    // ---------- punctuation popup (long-press ".") ----------
    private fun openPopup(k: Key) {
        popupKey = k; popupSel = -1
        val w = 34 * d; val h = 46 * d; val n = popupChars.length
        var left = k.r.right - n * w; if (left < 4 * d) left = 4 * d
        val top = k.r.top - h - 6 * d
        popupRect.set(left, top, left + n * w, top + h)
        if (vibrate) performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        invalidate()
    }

    private fun drawPopup(c: Canvas, k: Key) {
        c.drawRoundRect(popupRect, 14 * d, 14 * d, litP)
        c.drawRoundRect(popupRect, 14 * d, 14 * d, strokeP)
        val n = popupChars.length; val w = popupRect.width() / n
        for (i in 0 until n) {
            val x = popupRect.left + i * w
            if (i == popupSel) { tmp.set(x + 2 * d, popupRect.top + 4 * d, x + w - 2 * d, popupRect.bottom - 4 * d); c.drawRoundRect(tmp, 10 * d, 10 * d, accentP) }
            big.color = if (i == popupSel) onAccent else textColor
            c.drawText(popupChars[i].toString(), x + w / 2, popupRect.centerY() + 7.5f * d, big)
        }
    }

    // ---------- touch ----------
    private fun hit(x: Float, y: Float): Key? {
        for (k in keys) if (k.r.contains(x, y)) return k
        var best: Key? = null; var bd = Float.MAX_VALUE
        for (k in keys) {                                   // forgive taps that land in the gaps between keys
            if (x < k.r.left - 4 * d || x > k.r.right + 4 * d || y < k.r.top - 5 * d || y > k.r.bottom + 5 * d) continue
            val dx = x - k.r.centerX(); val dy = y - k.r.centerY(); val dd = dx * dx + dy * dy
            if (dd < bd) { bd = dd; best = k }
        }
        return best
    }

    private fun repaint(k: Key) { k.r.roundOut(dirty); dirty.inset(-2, -2); invalidate(dirty) }

    private val audio by lazy { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    /** Vibration and key sound (system sounds only; they follow the phone's "touch sounds" setting). */
    private fun feedback(code: Int = 0) {
        if (vibrate) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        when (soundStyle) {
            1 -> audio.playSoundEffect(AudioManager.FX_KEY_CLICK)
            2 -> audio.playSoundEffect(when (code) {
                -1 -> AudioManager.FX_KEYPRESS_SPACEBAR; -2 -> AudioManager.FX_KEYPRESS_DELETE
                -3 -> AudioManager.FX_KEYPRESS_RETURN; else -> AudioManager.FX_KEYPRESS_STANDARD })
        }
    }

    /** Run the action of a key (called on finger-up, or earlier when a second finger lands). */
    private fun fire(k: Key) {
        when (k.code) {
            -1 -> l.onSpace(); -3 -> l.onEnter(); -2 -> {}
            -7 -> l.onLang()
            -5 -> {
                val now = SystemClock.uptimeMillis()
                if (caps) { caps = false; shift = false }
                else if (shift && now - lastShiftTap < 350) { caps = true }
                else shift = !shift
                lastShiftTap = now; invalidate()
            }
            -6 -> { symbols = !symbols; page2 = false; shift = false; caps = false; build() }
            -8 -> { page2 = !page2; build() }
            else -> {
                val ch = k.code.toChar()
                l.onKey(if ((shift || caps) && ch.isLetter()) ch.uppercaseChar() else ch)
                if (shift && !caps) { shift = false; invalidate() }
            }
        }
    }

    private fun press(id: Int, k: Key, x: Float, single: Boolean) {
        pk[id] = k; fired[id] = false; startX[id] = x
        feedback(k.code); repaint(k)
        when {
            k.code == -2 -> { fired[id] = true; l.onBackspace(); postDelayed(rep, 400) }
            k.code == -1 && swipeCursor -> { spaceId = id; spaceAcc = 0f }
        }
        if (single && (k.code == '.'.code || k.code == -7 || (k.row == 0 && !symbols && k.code > 0))) { lpId = id; postDelayed(longPress, 380) }
    }

    private fun release(id: Int, x: Float, y: Float, cancel: Boolean) {
        val k = pk[id] ?: return
        pk[id] = null
        if (id == lpId) { removeCallbacks(longPress); lpId = -1 }
        if (k.code == -2) removeCallbacks(rep)
        if (id == spaceId) spaceId = -1
        val pop = popupKey
        if (pop != null && !cancel) {
            val sel = popupSel; popupKey = null; popupSel = -1; invalidate()
            if (sel >= 0) l.onKey(popupChars[sel])
            return
        }
        popupKey = null
        repaint(k)
        if (!cancel && !fired[id]) {
            val up = hit(x, y)
            if (k === up || k.code == -1) fire(k)
        }
        fired[id] = false
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex; val id = e.getPointerId(i)
                if (id >= maxP) return true
                // a new finger: make sure earlier fingers' keys fire first, so typing order is kept
                if (e.pointerCount > 1) for (j in 0 until maxP) { val pkk = pk[j]; if (pkk != null && !fired[j] && pkk.code != -1 && pkk.code != -2 && pkk.code != -3) { fired[j] = true; fire(pkk) } }
                if (lpId >= 0) { removeCallbacks(longPress); lpId = -1 }
                val k = hit(e.getX(i), e.getY(i)) ?: return true
                press(id, k, e.getX(i), e.pointerCount == 1)
            }
            MotionEvent.ACTION_MOVE -> {
                val pop = popupKey
                if (pop != null && lpId >= 0) {
                    val i = e.findPointerIndex(lpId)
                    if (i >= 0) {
                        val w = popupRect.width() / popupChars.length
                        val sel = if (e.getX(i) >= popupRect.left && e.getX(i) <= popupRect.right) ((e.getX(i) - popupRect.left) / w).toInt() else -1
                        if (sel != popupSel) { popupSel = sel; invalidate() }
                    }
                } else if (spaceId >= 0) {
                    val i = e.findPointerIndex(spaceId)
                    if (i >= 0) {
                        val dx = e.getX(i) - startX[spaceId]
                        if (Math.abs(dx) > 10 * d || fired[spaceId]) {
                            fired[spaceId] = true                       // it's a swipe, not a tap on space
                            spaceAcc += dx; startX[spaceId] = e.getX(i)
                            val step = 11 * d
                            while (spaceAcc >= step) { l.onCursor(1); spaceAcc -= step }
                            while (spaceAcc <= -step) { l.onCursor(-1); spaceAcc += step }
                        }
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val i = e.actionIndex; val id = e.getPointerId(i)
                if (id < maxP) release(id, e.getX(i), e.getY(i), false)
            }
            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(rep); removeCallbacks(longPress); lpId = -1; spaceId = -1; popupKey = null; popupSel = -1
                for (j in 0 until maxP) if (pk[j] != null) release(j, 0f, 0f, true)
                invalidate()
            }
        }
        return true
    }
}
