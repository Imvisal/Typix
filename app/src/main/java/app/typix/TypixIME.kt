package app.typix

import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.os.SystemClock
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*

class TypixIME : InputMethodService(), TypixKeyboardView.Listener {
    private var sinhala = true
    private var secure = false
    private var menuOpen = false
    private var selecting = false
    private val buf = StringBuilder()
    private lateinit var th: Theme
    private var keys: TypixKeyboardView? = null
    private lateinit var body: FrameLayout
    private lateinit var strip: LinearLayout
    private lateinit var tabEn: TextView
    private lateinit var tabSi: TextView

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun lpw(w: Float) = LinearLayout.LayoutParams(0, -1, w).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) }
    private fun lpv(w: Float) = LinearLayout.LayoutParams(-1, 0, w).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) }

    override fun onCreate() { super.onCreate(); Dict.load(this); ClipStore.start(this) }
    override fun onDestroy() { ClipStore.stop(); super.onDestroy() }
    override fun onEvaluateFullscreenMode() = false

    // ---------- views ----------
    private fun cell(t: String, size: Float, color: Int, bg: Int = 0, onClick: () -> Unit) = TextView(this).apply {
        text = t; textSize = size; setTextColor(color); gravity = Gravity.CENTER
        if (bg != 0) background = GradientDrawable().apply { cornerRadius = dp(10).toFloat(); setColor(bg) }
        setOnClickListener { onClick() }
    }
    private fun btn(t: String, accent: Boolean = false, onClick: () -> Unit) =
        cell(t, 14f, if (accent) th.onAccent else th.text, if (accent) th.accent else th.key, onClick)
    private fun hrow(vararg v: View) = LinearLayout(this).apply { v.forEach { addView(it, lpw(1f)) } }

    private var panel = 0
    private lateinit var icons: List<IconView>
    private fun bodyH() = dp((4 * Prefs.rowDp(this) + 12).toInt())

    override fun onCreateInputView(): View {
        th = Prefs.theme(this)
        val r = dp(22).toFloat()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { setColor(th.bg); cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f) }
            setPadding(dp(4), dp(6), dp(4), dp(8))
        }
        val bar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        bar.addView(LogoView(this, th.accent).apply { setOnClickListener { openApp() } },
            LinearLayout.LayoutParams(dp(34), dp(34)).apply { setMargins(dp(4), 0, dp(8), 0) })
        tabEn = cell("En", 16f, th.text) { setLang(false) }.apply { minWidth = dp(46) }
        tabSi = cell("සිං", 16f, th.text) { setLang(true) }.apply { minWidth = dp(46) }
        bar.addView(tabEn, LinearLayout.LayoutParams(-2, dp(34)))
        bar.addView(tabSi, LinearLayout.LayoutParams(-2, dp(34)).apply { leftMargin = dp(4) })
        bar.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        val kinds = listOf(IconView.EMOJI, IconView.CLIP, IconView.EDIT, IconView.TUNE, IconView.GLOBE)
        icons = kinds.mapIndexed { i, k ->
            IconView(this, k, th.text).apply { setOnClickListener { when (i) { 0, 1, 2 -> togglePanel(i + 1); 3 -> openApp(); else -> switchKeyboard() } } }
        }
        for (v in icons) bar.addView(v, LinearLayout.LayoutParams(dp(42), dp(40)))
        root.addView(bar, LinearLayout.LayoutParams(-1, dp(42)))
        strip = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        root.addView(strip, LinearLayout.LayoutParams(-1, dp(38)))
        body = FrameLayout(this)
        root.addView(body, LinearLayout.LayoutParams(-1, bodyH()))
        keys = TypixKeyboardView(this, this).also { it.sinhala = sinhala }
        showKeys(); refreshTabs(); updateStrip()
        return root
    }

    private fun togglePanel(n: Int) {
        if (panel == n) showKeys() else when (n) { 1 -> showEmoji(); 2 -> showClip(); else -> showEditor() }
    }

    private fun refreshIcons() {
        if (!::icons.isInitialized) return
        val dim = (th.text and 0x00FFFFFF) or 0xCC000000.toInt()
        icons.forEachIndexed { i, v -> v.tint(if (i < 3 && panel == i + 1) th.accent else dim) }
    }

    private fun refreshTabs() {
        for ((v, on) in listOf(tabEn to !sinhala, tabSi to sinhala)) {
            v.setTextColor(if (on) th.accent else (th.text and 0x00FFFFFF) or 0x99000000.toInt())
            v.background = if (on) GradientDrawable().apply { cornerRadius = dp(10).toFloat(); setColor(th.key) } else null
        }
    }

    private fun updateStrip() {
        strip.removeAllViews()
        if (!sinhala || secure || buf.isEmpty()) return
        Dict.suggest(buf.toString()).forEachIndexed { i, w ->
            if (i > 0) strip.addView(View(this).apply { setBackgroundColor(th.key) }, LinearLayout.LayoutParams(dp(1), dp(20)))
            strip.addView(cell(w, 18f, if (i == 0) th.accent else th.text) { pick(w) }, LinearLayout.LayoutParams(0, -1, 1f))
        }
    }

    private fun showKeys() { panel = 0; body.removeAllViews(); keys?.let { body.addView(it) }; refreshIcons() }
    private fun panelBase() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; body.removeAllViews(); body.addView(this, FrameLayout.LayoutParams(-1, -1)) }
    private fun bottom(vararg extra: Pair<String, () -> Unit>) = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        addView(cell("ABC", 15f, th.text, th.key) { showKeys() }, LinearLayout.LayoutParams(dp(72), -1).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) })
        addView(View(this@TypixIME), LinearLayout.LayoutParams(0, 1, 1f))
        for ((t, a) in extra) addView(cell(t, 15f, th.text, th.key, a), LinearLayout.LayoutParams(dp(96), -1).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) })
    }

    // ---------- panels ----------
    private fun showEmoji() {
        panel = 1; refreshIcons()
        val col = panelBase()
        val scroll = ScrollView(this)
        val tabs = LinearLayout(this)
        val cw = (resources.displayMetrics.widthPixels - dp(8)) / 7
        fun fill(list: List<String>) {
            scroll.removeAllViews()
            val g = GridLayout(this).apply { columnCount = 7 }
            for (e in list) g.addView(cell(e, 25f, th.text) { commit(e); Prefs.addRecent(this@TypixIME, e) },
                GridLayout.LayoutParams().apply { width = cw; height = dp(46) })
            scroll.addView(g)
        }
        val recent = Prefs.recent(this).ifEmpty { Emojis.smileys.take(21) }
        val cats = listOf("🕘" to recent, "😀" to Emojis.smileys, "👋" to Emojis.people, "🐱" to Emojis.animals, "🍔" to Emojis.food, "⚽" to Emojis.activities, "🚗" to Emojis.travel, "💡" to Emojis.objects, "❤️" to Emojis.symbols, "🏁" to Emojis.flags)
        for ((icon, list) in cats) tabs.addView(cell(icon, 20f, th.text) { fill(list) }, LinearLayout.LayoutParams(dp(48), -1))
        fill(recent)
        col.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(tabs) }, LinearLayout.LayoutParams(-1, dp(40)))
        col.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        col.addView(bottom("⌫" to { onBackspace() }), LinearLayout.LayoutParams(-1, dp(40)))
    }

    private fun showClip() {
        panel = 2; refreshIcons()
        val col = panelBase()
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fun label(t: String) = TextView(this).apply { text = t; textSize = 14f; setTextColor(th.accent); setPadding(dp(8), dp(8), 0, dp(4)) }
        fun row(s: String, pinned: Boolean, refresh: () -> Unit) = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), dp(10), dp(12), dp(10))
            background = GradientDrawable().apply { cornerRadius = dp(10).toFloat(); setColor(th.key) }
            addView(TextView(this@TypixIME).apply { text = s; textSize = 15f; maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END; setTextColor(th.text)
                setOnClickListener { commit(s) } }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(TextView(this@TypixIME).apply { text = "📌"; textSize = 18f; alpha = if (pinned) 1f else 0.35f; setPadding(dp(10), 0, 0, 0)
                setOnClickListener { ClipStore.togglePin(this@TypixIME, s); refresh() } })
        }
        fun refresh() {
            list.removeAllViews()
            val pins = Prefs.pins(this); val rec = ClipStore.recent(this)
            if (pins.isEmpty() && rec.isEmpty()) list.addView(label("Copy something and it shows up here"))
            if (pins.isNotEmpty()) list.addView(label("📌 Pinned"))
            for (s in pins) list.addView(row(s, true) { refresh() }, lpv(0f).apply { height = -2 })
            if (rec.isNotEmpty()) list.addView(label("🕘 Recent"))
            for (s in rec) list.addView(row(s, false) { refresh() }, lpv(0f).apply { height = -2 })
        }
        refresh()
        col.addView(ScrollView(this).apply { addView(list) }, LinearLayout.LayoutParams(-1, 0, 1f))
        col.addView(bottom("Clear" to { ClipStore.clearRecent(); refresh() }), LinearLayout.LayoutParams(-1, dp(40)))
    }

    private fun showEditor() {
        panel = 3; refreshIcons()
        selecting = false
        val col = panelBase()
        fun key(code: Int) {
            flush(); val ic = currentInputConnection ?: return
            val t = SystemClock.uptimeMillis(); val m = if (selecting) KeyEvent.META_SHIFT_ON else 0
            ic.sendKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0, m)); ic.sendKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0, m))
        }
        fun act(id: Int) { flush(); currentInputConnection?.performContextMenuAction(id) }
        lateinit var sel: TextView
        sel = btn("SELECT") {
            selecting = !selecting
            sel.setTextColor(if (selecting) th.onAccent else th.text)
            (sel.background as? GradientDrawable)?.setColor(if (selecting) th.accent else th.key)
        }
        val pad = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL
            addView(hrow(View(this@TypixIME), btn("⌃") { key(KeyEvent.KEYCODE_DPAD_UP) }, View(this@TypixIME)), lpv(1f))
            addView(hrow(btn("‹") { key(KeyEvent.KEYCODE_DPAD_LEFT) }, sel, btn("›") { key(KeyEvent.KEYCODE_DPAD_RIGHT) }), lpv(1f))
            addView(hrow(View(this@TypixIME), btn("⌄") { key(KeyEvent.KEYCODE_DPAD_DOWN) }, View(this@TypixIME)), lpv(1f)) }
        val acts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL
            addView(btn("CUT") { act(android.R.id.cut) }, lpv(1f)); addView(btn("COPY") { act(android.R.id.copy) }, lpv(1f))
            addView(btn("PASTE", true) { act(android.R.id.paste) }, lpv(1f)); addView(btn("SELECT ALL") { act(android.R.id.selectAll) }, lpv(1f)) }
        val top = LinearLayout(this)
        top.addView(pad, LinearLayout.LayoutParams(0, -1, 1f)); top.addView(acts, LinearLayout.LayoutParams(dp(110), -1))
        col.addView(top, LinearLayout.LayoutParams(-1, 0, 1f))
        col.addView(hrow(btn("⇤") { key(KeyEvent.KEYCODE_MOVE_HOME) }, btn("⇥") { key(KeyEvent.KEYCODE_MOVE_END) }, btn("⌫") { onBackspace() }), LinearLayout.LayoutParams(-1, dp(44)))
        col.addView(bottom(), LinearLayout.LayoutParams(-1, dp(40)))
    }

    private fun openApp() = startActivity(Intent(this, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    private fun switchKeyboard() = (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()

    // ---------- input ----------
    override fun onStartInput(info: EditorInfo?, restarting: Boolean) {
        super.onStartInput(info, restarting)
        buf.setLength(0); selecting = false
        val t = info?.inputType ?: 0
        val cls = t and InputType.TYPE_MASK_CLASS
        val v = t and InputType.TYPE_MASK_VARIATION
        secure = (cls == InputType.TYPE_CLASS_TEXT && (v == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                v == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD || v == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)) ||
                (cls == InputType.TYPE_CLASS_NUMBER && v == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (::th.isInitialized && Prefs.theme(this).name != th.name) { setInputView(onCreateInputView()); return }
        if (::body.isInitialized) { showKeys(); keys?.applySettings(); body.layoutParams = LinearLayout.LayoutParams(-1, bodyH()); body.requestLayout(); updateStrip() }
    }

    override fun onFinishInput() { buf.setLength(0); super.onFinishInput() }

    private fun setLang(s: Boolean) { flush(); sinhala = s; keys?.sinhala = s; keys?.invalidate(); refreshTabs(); updateStrip() }
    private fun flush() { if (buf.isNotEmpty()) { currentInputConnection?.finishComposingText(); buf.setLength(0); updateStrip() } }
    private fun commit(s: String) { flush(); currentInputConnection?.commitText(s, 1) }
    private fun pick(w: String) {
        val ic = currentInputConnection ?: return
        ic.setComposingText(w, 1); ic.finishComposingText(); buf.setLength(0); ic.commitText(" ", 1); updateStrip()
    }

    override fun onKey(c: Char) {
        val ic = currentInputConnection ?: return
        if (sinhala && !secure && c.isLetter()) {
            buf.append(c); ic.setComposingText(Transliterator.transliterate(buf.toString()), 1); updateStrip()
        } else { flush(); ic.commitText(c.toString(), 1) }
    }

    override fun onSpace() {
        val ic = currentInputConnection ?: return
        if (buf.isNotEmpty()) {
            if (Prefs.bool(this, "autocorrect", true)) Dict.exact(buf.toString())?.let { ic.setComposingText(it, 1) }
            ic.finishComposingText(); buf.setLength(0); updateStrip()
        }
        ic.commitText(" ", 1)
    }

    override fun onBackspace() {
        val ic = currentInputConnection ?: return
        if (buf.isNotEmpty()) {
            buf.deleteCharAt(buf.length - 1)
            if (buf.isEmpty()) ic.commitText("", 1) else ic.setComposingText(Transliterator.transliterate(buf.toString()), 1)
            updateStrip()
        } else sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
    }

    override fun onEnter() { flush(); sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER) }
}
