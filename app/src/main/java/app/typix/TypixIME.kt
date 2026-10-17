package app.typix

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
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
    private lateinit var tabEn: TabView
    private lateinit var tabSi: TabView

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun lpw(w: Float) = LinearLayout.LayoutParams(0, -1, w).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) }
    private fun lpv(w: Float) = LinearLayout.LayoutParams(-1, 0, w).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) }

    override fun onCreate() { super.onCreate(); Dict.load(this); ClipStore.start(this) }
    override fun onDestroy() { ClipStore.stop(); super.onDestroy() }
    override fun onEvaluateFullscreenMode() = false

    // ---------- views ----------
    private fun cell(t: String, size: Float, color: Int, bg: Int = 0, onClick: () -> Unit) = TextView(this).apply {
        text = t; textSize = size; setTextColor(color); gravity = Gravity.CENTER; typeface = Fonts.get(this@TypixIME, 500)
        if (bg != 0) background = GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(bg) }
        setOnClickListener { onClick() }
    }
    private fun btn(t: String, accent: Boolean = false, onClick: () -> Unit) =
        cell(t, 14f, if (accent) th.onAccent else th.text, if (accent) th.accent else th.key, onClick)
    private fun hrow(vararg v: View) = LinearLayout(this).apply { v.forEach { addView(it, lpw(1f)) } }

    private var panel = 0
    private val iconPanels = ArrayList<Pair<IconView, Int>>()   // icon -> panel it opens (0 = none)
    private val iconBoxes = ArrayList<FrameLayout>()
    private fun bodyH() = dp((4 * Prefs.rowDp(this) + 8).toInt())

    override fun onCreateInputView(): View {
        th = Prefs.theme(this)
        val r = dp(28).toFloat()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { setColor(th.bg); cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f) }
            setPadding(dp(4), dp(8), dp(4), dp(8))
        }
        iconPanels.clear(); iconBoxes.clear()
        val bar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        bar.addView(LogoView(this, th.accent).apply { setOnClickListener { togglePanel(4) } },
            LinearLayout.LayoutParams(dp(34), dp(34)).apply { setMargins(dp(8), 0, dp(8), 0) })
        tabEn = TabView(this, "En", 16f) { setLang(false) }
        tabSi = TabView(this, "සිං", 17f) { setLang(true) }
        bar.addView(tabEn, LinearLayout.LayoutParams(dp(52), dp(40)).apply { setMargins(dp(2), 0, dp(2), 0) })
        bar.addView(tabSi, LinearLayout.LayoutParams(dp(58), dp(40)).apply { setMargins(dp(2), 0, dp(2), 0) })
        fun tool(kind: Int, panelId: Int, action: () -> Unit) {
            val iv = IconView(this, kind, th.text)
            val fl = FrameLayout(this).apply {
                addView(iv, FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
                setOnClickListener { action() }
            }
            iconPanels.add(iv to panelId); iconBoxes.add(fl)
            bar.addView(fl, LinearLayout.LayoutParams(dp(42), dp(40)))
        }
        tool(IconView.EMOJI, 1) { togglePanel(1) }
        tool(IconView.MIC, 0) { startVoice() }
        bar.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        tool(IconView.SUN, 0) { openApp() }
        root.addView(bar, LinearLayout.LayoutParams(-1, dp(44)))
        strip = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        root.addView(strip, LinearLayout.LayoutParams(-1, dp(46)).apply { setMargins(dp(4), dp(2), dp(4), dp(2)) })
        body = FrameLayout(this)
        root.addView(body, LinearLayout.LayoutParams(-1, bodyH()))
        keys = TypixKeyboardView(this, this).also { it.sinhala = sinhala }
        showKeys(); refreshTabs(); updateStrip(); applyNav()
        return root
    }

    /** Hand over to the voice-typing keyboard (e.g. Google voice typing) if one is enabled. */
    private fun startVoice() {
        flush()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            for (info in imm.enabledInputMethodList) for (st in imm.getEnabledInputMethodSubtypeList(info, true)) {
                if (st.mode == "voice") { try { switchInputMethod(info.id, st); return } catch (e: Exception) { } }
            }
        }
        Toast.makeText(this, "No voice keyboard is enabled on this phone", Toast.LENGTH_SHORT).show()
    }

    /** Paint the system navigation bar the same colour as the keyboard (no black strip at the bottom). */
    private fun applyNav() {
        val w = window?.window ?: return
        w.navigationBarColor = th.bg
        if (android.os.Build.VERSION.SDK_INT >= 28) w.navigationBarDividerColor = th.bg
        val r = (th.bg shr 16) and 255; val g = (th.bg shr 8) and 255; val b = th.bg and 255
        val light = r * 0.299 + g * 0.587 + b * 0.114 > 150
        val dv = w.decorView
        dv.systemUiVisibility = if (light) dv.systemUiVisibility or 0x10 else dv.systemUiVisibility and 0x10.inv()
    }

    private fun togglePanel(n: Int) {
        if (panel == n) showKeys() else when (n) { 1 -> showEmoji(); 2 -> showClip(); 3 -> showEditor(); else -> showMenu() }
    }

    private fun refreshIcons() {
        if (iconPanels.isEmpty()) return
        val dim = mixColor(th.text, th.bg, 0.30f)
        iconPanels.forEachIndexed { i, (v, pn) ->
            val on = pn != 0 && panel == pn
            v.tint(if (on) th.accent else dim)
            iconBoxes[i].background = if (on) GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(th.key) } else null
        }
    }

    private fun refreshTabs() { tabEn.setActive(!sinhala, th); tabSi.setActive(sinhala, th) }

    private val quickEmoji = listOf("😂", "❤️", "😊", "🙏", "👍", "😍", "😭")
    private fun updateStrip() {
        strip.removeAllViews()
        if (secure) return
        if (sinhala && buf.isNotEmpty()) {
            Dict.suggest(buf.toString()).forEachIndexed { i, w ->
                val c = cell(w, 19f, th.text) { pick(w) }
                c.typeface = Fonts.get(this, if (i == 0) 600 else 500)
                if (i == 0) c.background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(th.key) }
                strip.addView(c, LinearLayout.LayoutParams(0, -1, 1f).apply { setMargins(dp(3), 0, dp(3), 0) })
            }
            return
        }
        for (e in (Prefs.recent(this) + quickEmoji).distinct().take(7))
            strip.addView(cell(e, 22f, th.text) { commit(e) }, LinearLayout.LayoutParams(0, -1, 1f))
    }

    private fun setPanelMode(on: Boolean) {
        strip.visibility = if (on) View.GONE else View.VISIBLE
        body.layoutParams = LinearLayout.LayoutParams(-1, bodyH() + if (on) dp(52) else 0)
    }
    private fun showKeys() { panel = 0; setPanelMode(false); body.removeAllViews(); keys?.let { body.addView(it) }; refreshIcons() }
    private fun panelBase() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPanelMode(true); body.removeAllViews(); body.addView(this, FrameLayout.LayoutParams(-1, -1)) }
    private fun bottom(vararg extra: Pair<String, () -> Unit>) = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        addView(cell("ABC", 15f, th.text, th.key) { showKeys() }, LinearLayout.LayoutParams(dp(72), -1).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) })
        addView(View(this@TypixIME), LinearLayout.LayoutParams(0, 1, 1f))
        for ((t, a) in extra) addView(cell(t, 15f, th.text, th.key, a), LinearLayout.LayoutParams(dp(96), -1).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) })
    }

    // ---------- panels ----------
    /** Tools menu (opened from the logo): clipboard, text editor, settings. Keeps the toolbar identical to the design. */
    private fun showMenu() {
        panel = 4; refreshIcons()
        val col = panelBase()
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER }
        fun tile(kind: Int, name: String, act: () -> Unit) = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            background = GradientDrawable().apply { cornerRadius = dp(18).toFloat(); setColor(th.key) }
            addView(IconView(this@TypixIME, kind, th.accent), LinearLayout.LayoutParams(dp(30), dp(30)))
            addView(TextView(this@TypixIME).apply { text = name; textSize = 15f; setTextColor(th.text); typeface = Fonts.get(this@TypixIME, 500); setPadding(0, dp(8), 0, 0) })
            setOnClickListener { act() }
        }
        val m = LinearLayout.LayoutParams(0, -1, 1f).apply { setMargins(dp(6), dp(10), dp(6), dp(10)) }
        row.addView(tile(IconView.CLIP, "Clipboard") { showClip() }, m)
        row.addView(tile(IconView.EDIT, "Text editor") { showEditor() }, LinearLayout.LayoutParams(m))
        row.addView(tile(IconView.TUNE, "Settings") { openApp() }, LinearLayout.LayoutParams(m))
        col.addView(row, LinearLayout.LayoutParams(-1, 0, 1f))
        col.addView(bottom(), LinearLayout.LayoutParams(-1, dp(44)))
    }

    private var emojiCat = -1
    private class EmojiCat(val icon: String, val name: String, val list: () -> List<String>)

    private fun showEmoji() {
        panel = 1; refreshIcons()
        val col = panelBase()
        val cats = listOf(
            EmojiCat("🕘", "Recent") { Prefs.recent(this) }, EmojiCat("😀", "Smileys") { Emojis.smileys },
            EmojiCat("👋", "People") { Emojis.people }, EmojiCat("🐱", "Animals") { Emojis.animals },
            EmojiCat("🍔", "Food") { Emojis.food }, EmojiCat("⚽", "Activities") { Emojis.activities },
            EmojiCat("🚗", "Travel") { Emojis.travel }, EmojiCat("💡", "Objects") { Emojis.objects },
            EmojiCat("❤️", "Symbols") { Emojis.symbols }, EmojiCat("🏁", "Flags") { Emojis.flags }
        )
        if (emojiCat < 0) emojiCat = if (Prefs.recent(this).isNotEmpty()) 0 else 1
        val dim = mixColor(th.text, th.bg, 0.40f)
        val scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false }
        val title = TextView(this).apply { textSize = 13f; setTextColor(dim); typeface = Fonts.get(this@TypixIME, 500); setPadding(dp(12), dp(6), 0, dp(2)) }
        val cw = (resources.displayMetrics.widthPixels - dp(8)) / 7
        val tabViews = ArrayList<TabView>()
        fun select(i: Int) {
            emojiCat = i
            tabViews.forEachIndexed { j, t -> t.setActive(j == i, th); t.alpha = if (j == i) 1f else 0.6f }
            title.text = cats[i].name.uppercase()
            val list = cats[i].list()
            scroll.removeAllViews(); scroll.scrollTo(0, 0)
            if (list.isEmpty()) {
                scroll.addView(TextView(this).apply {
                    text = "No recent emojis yet\nEmojis you use will show up here"; textSize = 14f; gravity = Gravity.CENTER
                    setTextColor(dim); typeface = Fonts.get(this@TypixIME, 400); setPadding(dp(16), dp(40), dp(16), 0)
                })
            } else {
                val g = GridLayout(this).apply { columnCount = 7 }
                for (e in list) g.addView(cell(e, 25f, th.text) { commit(e); Prefs.addRecent(this@TypixIME, e) },
                    GridLayout.LayoutParams().apply { width = cw; height = dp(46) })
                scroll.addView(g)
            }
        }
        val tabs = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        cats.forEachIndexed { i, c ->
            val t = TabView(this, c.icon, 19f) { select(i) }
            tabViews.add(t); tabs.addView(t, LinearLayout.LayoutParams(dp(50), dp(38)).apply { setMargins(dp(2), 0, dp(2), 0) })
        }
        col.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; setPadding(dp(4), 0, dp(4), 0); addView(tabs) }, LinearLayout.LayoutParams(-1, dp(42)))
        col.addView(View(this).apply { setBackgroundColor(th.key) }, LinearLayout.LayoutParams(-1, dp(1)).apply { setMargins(dp(8), dp(3), dp(8), 0) })
        col.addView(title, LinearLayout.LayoutParams(-1, -2))
        col.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        col.addView(bottom("⌫" to { onBackspace() }), LinearLayout.LayoutParams(-1, dp(44)))
        select(emojiCat)
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
        if (::th.isInitialized) applyNav()
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
    override fun onLang() = setLang(!sinhala)
    override fun onPicker() = switchKeyboard()
}

/** Language tab: label, tinted pill and a thin accent underline when active (matches the design). */
class TabView(ctx: Context, label: String, size: Float, private val onTap: () -> Unit) : TextView(ctx) {
    private var active = false
    private var accent = 0
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val d = ctx.resources.displayMetrics.density
    init { text = label; textSize = size; gravity = Gravity.CENTER; typeface = Fonts.get(ctx, 600); setOnClickListener { onTap() } }
    fun setActive(on: Boolean, th: Theme) {
        active = on; accent = th.accent
        setTextColor(if (on) th.text else mixColor(th.text, th.bg, 0.45f))
        background = if (on) GradientDrawable().apply { cornerRadii = floatArrayOf(12 * d, 12 * d, 12 * d, 12 * d, 4 * d, 4 * d, 4 * d, 4 * d); setColor(th.key) } else null
        invalidate()
    }
    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        if (active) { p.color = accent; c.drawRoundRect(width * 0.16f, height - 3 * d, width * 0.84f, height.toFloat(), 1.5f * d, 1.5f * d, p) }
    }
}
