package app.typix

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
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
    private var hints = true; private var soundStyle = 0; private var vibrate = false; private var vibStrength = 1; private var borders = false; private var swipeCursor = true
    private var shift = false; private var caps = false; private var symbols = false; private var page2 = false
    private var lastShiftTap = 0L

    private class Key(val label: String, val upper: String, val code: Int, val x: Float, val w: Float, val row: Int, val r: RectF = RectF(), val cell: RectF = RectF())
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
    private val startY = FloatArray(maxP)
    private var lpId = -1                  // pointer waiting for / showing the long-press popup
    private var spaceId = -1; private var spaceAcc = 0f
    private var popupKey: Key? = null; private var popupSel = -1
    private val popupChars = ".,?!:;-_'\"@"
    private val popupRect = RectF()
    private var keyPopup = true
    private val bubble = RectF()
    private val bubbleP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val bubbleT = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = face }

    private val rep = object : Runnable { override fun run() { l.onBackspace(); postDelayed(this, 42) } }
    private val longPress = object : Runnable {
        override fun run() {
            val id = lpId; if (id < 0) return
            val k = pk[id] ?: return
            if (k.code == -7) { fired[id] = true; l.onPicker(); return }
            if (k.code == '.'.code) { openPopup(k); return }
            if (k.row == 0 && !symbols && k.code > 0 && k.code < 128) {
                val i = "qwertyuiop".indexOf(k.code.toChar())
                if (i >= 0) { fired[id] = true; buzz(true); l.onKey("1234567890"[i]) }
            }
        }
    }

    init {
        val h = mapOf('q' to "ද", 'w' to "ව", 'e' to "එ", 'r' to "ර", 't' to "ට", 'y' to "ය", 'u' to "උ", 'i' to "ඉ",
            'o' to "ඔ", 'p' to "ප", 'a' to "අ", 's' to "ස", 'd' to "ඩ", 'f' to "ෆ", 'g' to "ග", 'h' to "හ", 'j' to "ජ", 'k' to "ක",
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
        val s = sizePct / 100f
        for (k in keys) {
            k.cell.set(k.x * u, top + k.row * rh, (k.x + k.w) * u, top + (k.row + 1) * rh)     // full touch cell: no dead gaps between keys
            k.r.set(k.x * u + padX, top + k.row * rh + padY, (k.x + k.w) * u - padX, top + (k.row + 1) * rh - padY)
            if (s != 1f) { val cx = k.r.centerX(); val hw = minOf(k.r.width() / 2 * s, k.cell.width() / 2 - 1 * d); k.r.set(cx - hw, k.r.top, cx + hw, k.r.bottom) }   // width only: height follows the row height
        }
    }

    private var gap = 1f
    private var sizePct = 100; private var numColor = 0; private var hasBg = false
    private var rad = 12f * 3f

    fun applySettings() {
        val t = Prefs.theme(context)
        // per-theme customising: key colour, letter colour, number colour, button size
        val keyC = Prefs.ov(context, t.name, "key").let { if (it != 0) it else t.key }
        val textC = Prefs.ov(context, t.name, "text").let { if (it != 0) it else t.text }
        numColor = Prefs.ov(context, t.name, "num")
        sizePct = Prefs.ov(context, t.name, "size", 100).coerceIn(70, 120)
        hasBg = KbBackground.get(context, t.name) != null
        val ka = Prefs.ov(context, t.name, "kalpha", 100).coerceIn(20, 100)
        bg.color = t.bg; accentP.color = t.accent; accent = t.accent; textColor = textC; onAccent = t.onAccent
        fillP.color = keyC
        specialP.color = if (lumaOf(t.bg) < 128) mixColor(keyC, t.bg, 0.25f) else mixColor(keyC, t.bg, 0.55f)
        litP.color = mixColor(keyC, textC, if (lumaOf(t.bg) < 128) 0.16f else 0.10f)
        fillP.alpha = ka * 255 / 100; specialP.alpha = ka * 255 / 100; litP.alpha = minOf(100, ka + 25) * 255 / 100
        strokeP.color = mixColor(keyC, textC, 0.22f)
        dimColor = mixColor(textC, t.bg, 0.45f)
        hintBase = mixColor(textC, t.bg, 0.55f); hintP.color = hintBase
        rowDp = Prefs.rowDp(context).toFloat(); requestLayout()
        hints = Prefs.bool(context, "hints", true); soundStyle = Prefs.int(context, "soundstyle", 0).coerceIn(0, 2)
        vibrate = Prefs.bool(context, "vibrate", false); vibStrength = Prefs.int(context, "vibstr", 1).coerceIn(0, 2); borders = Prefs.bool(context, "borders", false)
        swipeCursor = Prefs.bool(context, "swipecursor", true); keyPopup = Prefs.bool(context, "keypopup", true)
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
        if (!hasBg) c.drawPaint(bg)
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
                    big.color = if (numColor != 0 && k.code in 48..57) numColor else textColor
                    c.drawText(if (isLetter && upper) k.upper else k.label, cx, cy + 7.5f * d, big)
                    var hint: String? = null; var digitHint = false
                    if (isLetter) {
                        if (hints && sinhala) hint = if (upper) shiftTab[k.code] else hintTab[k.code]
                        else if (k.row == 0 && !symbols) { hint = digitTab[k.x.toInt()]; digitHint = true }
                    }
                    if (hint != null) { hintP.color = if (digitHint && numColor != 0) numColor else if (upper) accent else hintBase; c.drawText(hint, k.r.right - 5 * d, k.r.top + 12.5f * d, hintP) }
                }
            }
        }
        if (keyPopup && popupKey == null) for (i in 0 until maxP) { val k = pk[i] ?: continue; if (k.code > 32 && !(k.code == '.'.code && false)) drawBubble(c, k, upper) }
        popupKey?.let { drawPopup(c, it) }
    }

    /** Key-press preview (Gboard style): the pressed character, enlarged above the finger. */
    private fun bubbleRect(k: Key, out: RectF) {
        val w = k.r.width() + 12 * d; val h = 50 * d
        val cx = k.r.centerX()
        var l = cx - w / 2; if (l < 2 * d) l = 2 * d; if (l + w > width - 2 * d) l = width - 2 * d - w
        var t = k.r.top - h - 4 * d
        if (t < 0f) t = 0f                                   // top row: the bubble stays inside the keyboard and covers the key
        out.set(l, t, l + w, if (t == 0f) maxOf(h, k.r.bottom) else t + h)
    }
    private fun drawBubble(c: Canvas, k: Key, upper: Boolean) {
        if (k.code <= 32) return
        bubbleRect(k, bubble)
        bubbleP.color = mixColor(fillP.color, textColor, 0.18f)
        c.drawRoundRect(bubble, 14 * d, 14 * d, bubbleP)
        strokeP.alpha = 255; c.drawRoundRect(bubble, 14 * d, 14 * d, strokeP)
        bubbleT.color = textColor; bubbleT.textSize = 30 * d
        val label = if (k.code in 97..122 && upper) k.upper else k.label
        c.drawText(label, bubble.centerX(), bubble.centerY() + 10.5f * d, bubbleT)
        if (k.code in 97..122 && sinhala && hints) {
            val h = if (upper) shiftTab[k.code] else hintTab[k.code]
            if (h != null) { hintP.color = accent; c.drawText(h, bubble.right - 6 * d, bubble.top + 14 * d, hintP) }
        }
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
        buzz(true)
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
    /** Back to the letter page (from numbers / symbols), keeping the language. */
    fun showLetters() { if (symbols) { symbols = false; page2 = false; shift = false; caps = false; build() } }

    private fun hit(x0: Float, y0: Float): Key? {
        val x = x0; val y = y0 - 3 * d      // thumbs land low on a key: bias the target up a little so fast typing hits the intended key
        for (k in keys) if (k.cell.contains(x, y)) return k          // touch cells tile the keyboard, so small / shrunk keys still catch every tap
        var best: Key? = null; var bd = Float.MAX_VALUE
        for (k in keys) {                                   // edges (e.g. left of "a", right of "l") and a little above / below: nearest key
            val dx = if (x < k.cell.left) k.cell.left - x else if (x > k.cell.right) x - k.cell.right else 0f
            val dy = if (y < k.cell.top) k.cell.top - y else if (y > k.cell.bottom) y - k.cell.bottom else 0f
            if (dx > 20 * d || dy > 10 * d) continue
            val dd = dx * dx + dy * dy
            if (dd < bd) { bd = dd; best = k }
        }
        return best
    }

    /** A fresh first finger means no finger is down any more: forget leftovers (a lost "up" event must never leave a key stuck or repeating). */
    private fun resetTouch() {
        removeCallbacks(rep); removeCallbacks(longPress)
        for (j in 0 until maxP) { pk[j] = null; fired[j] = false }
        lpId = -1; spaceId = -1; popupKey = null; popupSel = -1; invalidate()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        // keys at the screen edges (q, p, a, l ...) must not be eaten by the back-swipe gesture
        if (Build.VERSION.SDK_INT >= 29 && right > left) systemGestureExclusionRects = listOf(Rect(0, 0, right - left, minOf(bottom - top, (190 * d).toInt())))
    }

    private val bRect = RectF(); private val dirty2 = Rect()
    private fun repaint(k: Key) {
        k.r.roundOut(dirty); dirty.inset(-2, -2)
        if (keyPopup && k.code > 32) { bubbleRect(k, bRect); bRect.roundOut(dirty2); dirty2.inset(-3, -3); dirty.union(dirty2) }
        invalidate(dirty)
    }

    private val vibrator: Vibrator? by lazy {
        try {
            if (Build.VERSION.SDK_INT >= 31) (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            else (context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)
        } catch (e: Exception) { null }
    }

    /** Key vibration straight from the phone's vibrator (does not depend on the system "touch feedback" setting). */
    private fun buzz(long: Boolean) {
        if (!vibrate) return
        val v = vibrator ?: return
        try {
            if (!v.hasVibrator()) return
            val ms = (longArrayOf(9, 16, 26)[vibStrength] * (if (long) 2 else 1))
            if (Build.VERSION.SDK_INT >= 26) {
                val amp = if (v.hasAmplitudeControl()) intArrayOf(70, 150, 255)[vibStrength] else VibrationEffect.DEFAULT_AMPLITUDE
                v.vibrate(VibrationEffect.createOneShot(ms, amp))
            } else v.vibrate(ms)
        } catch (e: Exception) { }
    }

    private val audio by lazy { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    /** Vibration and key sound (system sounds only; they follow the phone's "touch sounds" setting). */
    private fun feedback(code: Int = 0) {
        buzz(false)
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

    private fun press(id: Int, k: Key, x: Float, y: Float, single: Boolean) {
        pk[id] = k; fired[id] = false; startX[id] = x; startY[id] = y
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
            // Fast typing: fingers drift a little before lifting. Type the key that was PRESSED unless the finger really slid away.
            val dx = x - startX[id]; val dy = y - startY[id]
            if (k.code == -1 || dx * dx + dy * dy <= (40 * d) * (40 * d) || hit(x, y) === k) fire(k)
        }
        fired[id] = false
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                if (e.actionMasked == MotionEvent.ACTION_DOWN) resetTouch()
                val i = e.actionIndex; val id = e.getPointerId(i)
                if (id >= maxP) return true
                // a new finger: make sure earlier fingers' keys fire first, so typing order is kept
                if (e.pointerCount > 1) for (j in 0 until maxP) { val pkk = pk[j]; if (pkk != null && !fired[j] && pkk.code != -1 && pkk.code != -2 && pkk.code != -3) { fired[j] = true; fire(pkk) } }
                if (lpId >= 0) { removeCallbacks(longPress); lpId = -1 }
                val k = hit(e.getX(i), e.getY(i)) ?: return true
                press(id, k, e.getX(i), e.getY(i), e.pointerCount == 1)
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
