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
    private var selecting = false
    private val buf = StringBuilder()      // Singlish being composed
    private val enBuf = StringBuilder()    // English word being typed (for completions)
    private lateinit var th: Theme
    private var keys: TypixKeyboardView? = null
    private lateinit var body: FrameLayout
    private lateinit var strip: LinearLayout
    private lateinit var topBar: LinearLayout
    private var panelOn = false
    private var rootView: LinearLayout? = null
    private fun applyHand() {
        val r = rootView ?: return
        val side = (resources.displayMetrics.widthPixels * 0.18f).toInt()
        val h = Prefs.int(this, "onehand", 0)
        r.setPadding(dp(4) + (if (h == 2) side else 0), dp(6), dp(4) + (if (h == 1) side else 0), dp(2))
    }
    private lateinit var emojiRow: LinearLayout
    private val sug = arrayOfNulls<TextView>(3)
    private val sugDiv = arrayOfNulls<View>(2)
    private val emojiCells = ArrayList<TextView>()
    private var suggestions: List<String> = emptyList()
    private var stripMode = 0                // 0 = Singlish word, 1 = English word, 2 = starter words
    private lateinit var tabEn: TabView
    private lateinit var tabSi: TabView
    private var prefEn = true; private var prefEmojiRow = true; private var prefAutoCap = true
    private var prefDblSpace = true; private var prefAutocorrect = true
    private var lastStartLang = -1
    private var lastSpaceAt = 0L
    private var inputType = 0
    private var imeAction = 0

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun lpw(w: Float) = LinearLayout.LayoutParams(0, -1, w).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) }
    private fun lpv(w: Float) = LinearLayout.LayoutParams(-1, 0, w).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) }

    override fun onCreate() {
        super.onCreate()
        ClipStore.start(this)
        // heavy loading off the UI thread: dictionaries + emoji glyph checks, so the first keystroke never waits
        Thread {
            Dict.load(this); EnDict.load(this)
            Emojis.smileys; Emojis.people; Emojis.animals; Emojis.food; Emojis.activities; Emojis.travel; Emojis.objects; Emojis.symbols; Emojis.flags
        }.apply { priority = Thread.MIN_PRIORITY; start() }
    }
    override fun onDestroy() { voice?.destroy(); ClipStore.stop(); super.onDestroy() }
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

    private fun loadPrefs() {
        prefEn = Prefs.bool(this, "ensugg", true); prefEmojiRow = Prefs.bool(this, "emojirow", true)
        prefAutoCap = Prefs.bool(this, "autocap", true); prefDblSpace = Prefs.bool(this, "dblspace", true)
        prefAutocorrect = Prefs.bool(this, "autocorrect", true)
        val sl = Prefs.int(this, "startlang", 0)
        if (sl != lastStartLang) { lastStartLang = sl; sinhala = sl == 0; keys?.sinhala = sinhala }
    }

    override fun onCreateInputView(): View {
        th = Prefs.theme(this); loadPrefs()
        val r = dp(28).toFloat()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { setColor(th.bg); cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f) }
            setPadding(dp(4), dp(6), dp(4), dp(2))
        }
        iconPanels.clear(); iconBoxes.clear(); emojiCells.clear()
        val bar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        bar.addView(LogoView(this, th.accent).apply { setOnClickListener { togglePanel(4) } },
            LinearLayout.LayoutParams(dp(34), dp(34)).apply { setMargins(dp(8), 0, dp(8), 0) })
        tabEn = TabView(this, "En", 16f) { setLang(false) }
        tabSi = TabView(this, "සිං", 17f) { setLang(true) }
        bar.addView(tabEn, LinearLayout.LayoutParams(dp(52), dp(40)).apply { setMargins(dp(2), 0, dp(2), 0) })
        bar.addView(tabSi, LinearLayout.LayoutParams(dp(58), dp(40)).apply { setMargins(dp(2), 0, dp(2), 0) })
        fun tool(kind: Int, panelId: Int, onLong: (() -> Unit)? = null, action: () -> Unit) {
            val iv = IconView(this, kind, th.text)
            val fl = FrameLayout(this).apply {
                addView(iv, FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
                setOnClickListener { action() }
                if (onLong != null) setOnLongClickListener { onLong(); true }
            }
            iconPanels.add(iv to panelId); iconBoxes.add(fl)
            if (kind == IconView.MIC) { micIv = iv; micBox = fl }
            bar.addView(fl, LinearLayout.LayoutParams(dp(42), dp(40)))
        }
        tool(IconView.EMOJI, 1) { togglePanel(1) }
        tool(IconView.MIC, 0, onLong = { startVoiceKeyboard() }) { toggleVoice() }
        voiceHint = TextView(this).apply {
            textSize = 13f; maxLines = 1; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(8), 0, dp(8), 0)
            ellipsize = android.text.TextUtils.TruncateAt.END; typeface = Fonts.get(this@TypixIME, 500); setTextColor(th.accent)
        }
        bar.addView(voiceHint, LinearLayout.LayoutParams(0, -1, 1f))
        tool(IconView.TUNE, 0) { openApp() }
        topBar = bar

        // 3 suggestion slots with thin dividers (views are created once and only re-texted while typing)
        strip = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        for (i in 0 until 3) {
            val t = TextView(this).apply {
                textSize = 19f; gravity = Gravity.CENTER; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
                setTextColor(th.text); typeface = Fonts.get(this@TypixIME, 500); setPadding(dp(6), 0, dp(6), 0)
                setOnClickListener { onSuggestion(i) }
            }
            sug[i] = t; strip.addView(t, LinearLayout.LayoutParams(0, -1, 1f))
            if (i < 2) { val dv = View(this).apply { setBackgroundColor(th.key) }; sugDiv[i] = dv; strip.addView(dv, LinearLayout.LayoutParams(dp(1), dp(22))) }
        }
        val top = FrameLayout(this)
        top.addView(topBar, FrameLayout.LayoutParams(-1, -1))
        top.addView(strip, FrameLayout.LayoutParams(-1, -1).apply { setMargins(dp(4), 0, dp(4), 0) })
        root.addView(top, LinearLayout.LayoutParams(-1, dp(46)))
        emojiRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        for (i in 0 until 7) {
            val t = TextView(this).apply { textSize = 22f; gravity = Gravity.CENTER; setOnClickListener { commit(text.toString()) } }
            emojiCells.add(t); emojiRow.addView(t, LinearLayout.LayoutParams(0, -1, 1f))
        }
        root.addView(emojiRow, LinearLayout.LayoutParams(-1, dp(40)))
        body = FrameLayout(this)
        root.addView(body, LinearLayout.LayoutParams(-1, bodyH()))
        keys = TypixKeyboardView(this, this).also { it.sinhala = sinhala; it.enterKind = enterKind() }
        showKeys(); refreshTabs(); refreshEmojiRow(); updateStrip(); rootView = root; applyNav(); applyHand()
        return root
    }

    private fun enterKind() = when (imeAction) {
        EditorInfo.IME_ACTION_SEARCH -> 1; EditorInfo.IME_ACTION_SEND -> 2
        EditorInfo.IME_ACTION_GO, EditorInfo.IME_ACTION_NEXT -> 3; EditorInfo.IME_ACTION_DONE -> 4; else -> 0
    }

    // ---------- voice typing ----------
    private var voice: VoiceInput? = null
    private var voiceOn = false
    private var micIv: IconView? = null
    private var micBox: FrameLayout? = null
    private var voiceHint: TextView? = null
    private var hasPartial = false
    private val hintHandler = android.os.Handler(android.os.Looper.getMainLooper())

    private fun voiceLang() = if (sinhala) "si-LK" else "en-US"
    private fun voiceLabel() = if (sinhala) "සිංහල" else "English"

    private fun micAllowed() = android.os.Build.VERSION.SDK_INT < 23 ||
        checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun hint(t: String, ms: Long = 0) {
        voiceHint?.text = t
        hintHandler.removeCallbacksAndMessages(null)
        if (ms > 0) hintHandler.postDelayed({ if (!voiceOn) voiceHint?.text = "" }, ms)
    }

    private val voiceCb = object : VoiceInput.Callback {
        override fun onPartial(text: String) {
            val ic = currentInputConnection ?: return
            if (text.isEmpty()) { if (hasPartial) { ic.setComposingText("", 1); ic.finishComposingText() }; hasPartial = false; return }
            ic.setComposingText(voiceText(text, !hasPartial), 1); hasPartial = true
        }
        override fun onFinal(text: String) {
            val ic = currentInputConnection ?: return
            ic.beginBatchEdit()
            ic.setComposingText(voiceText(text, !hasPartial), 1); ic.finishComposingText(); ic.commitText(" ", 1)
            ic.endBatchEdit()
            hasPartial = false; enBuf.setLength(0); lastSpaceAt = 0; updateStrip()
        }
        override fun onListening(on: Boolean) {
            voiceOn = on
            if (on) { hint("Listening · ${voiceLabel()}") } else { micIv?.scaleX = 1f; micIv?.scaleY = 1f; hint("") }
            refreshIcons()
        }
        override fun onLevel(level: Float) { micIv?.let { val s = 1f + 0.35f * level; it.scaleX = s; it.scaleY = s } }
        override fun onMessage(msg: String, fatal: Boolean) {
            hint(msg, 3500)
            if (fatal) Toast.makeText(this@TypixIME, msg, Toast.LENGTH_SHORT).show()
        }
    }

    /** Spoken text -> what gets typed: capital letter at sentence start (English) and a space if we are glued to a previous word. */
    private fun voiceText(t: String, first: Boolean): String {
        var s = t
        if (!first) return s
        val ic = currentInputConnection ?: return s
        val before = ic.getTextBeforeCursor(1, 0)?.toString().orEmpty()
        if (!sinhala && prefAutoCap && ic.getCursorCapsMode(inputType) != 0 && s.isNotEmpty()) s = s.substring(0, 1).uppercase() + s.substring(1)
        if (before.isNotEmpty() && !before[0].isWhitespace() && before[0] !in "([{\"'") s = " $s"
        return s
    }

    /** Mic tap: start / stop built-in voice typing (asks for the microphone the first time). */
    private fun toggleVoice() {
        val v = voice
        if (v != null && v.active) { v.stop(); return }
        flush(); enBuf.setLength(0); updateStrip()
        if (secure) { Toast.makeText(this, "Voice typing is off for password fields", Toast.LENGTH_SHORT).show(); return }
        if (!micAllowed()) {
            startActivity(Intent(this, MicPermissionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        val vi = v ?: VoiceInput(this, voiceCb).also { voice = it }
        if (!vi.available()) { startVoiceKeyboard(); return }     // no recogniser on the phone: use its voice keyboard instead
        if (panel != 0) showKeys()
        vi.start(voiceLang())
    }

    private fun stopVoice() { voice?.let { if (it.active) it.stop() } }

    /** Long-press on the mic: hand over to the phone's own voice-typing keyboard (e.g. Google voice typing) if one is enabled. */
    private fun startVoiceKeyboard() {
        stopVoice(); flush()
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
        if (voiceOn) micIv?.let { it.tint(th.accent); micBox?.background = GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(th.key) } }
    }

    private fun refreshTabs() { tabEn.setActive(!sinhala, th); tabSi.setActive(sinhala, th) }

    private val quickEmoji = listOf("😂", "❤️", "😊", "🙏", "👍", "😍", "😭")
    private val sinhalaStarters = listOf("මම", "ඔයා", "අපි")
    private val englishStarters = listOf("I", "The", "And")

    /** Re-text the 3 suggestion slots. Cheap: nothing is created or removed. */
    private fun updateStrip() {
        if (!::strip.isInitialized) return
        val list: List<String>
        if (secure) { list = emptyList(); stripMode = 2 }
        else if (sinhala && buf.isNotEmpty()) { list = Dict.suggest(buf.toString()); stripMode = 0 }
        else if (!sinhala && enBuf.isNotEmpty() && prefEn) {
            val typed = enBuf.toString()
            val l = ArrayList<String>(EnDict.suggest(typed, 2))
            if (l.none { it == typed }) l.add(typed)
            list = l; stripMode = 1
        } else { list = emptyList(); stripMode = 2 }
        suggestions = list
        val typing = stripMode != 2
        for (i in 0 until 3) {
            val t = sug[i] ?: continue
            val w = list.getOrNull(i) ?: ""
            if (t.text.toString() != w) t.text = w
            val tf = Fonts.get(this, if (i == 0 && typing) 600 else 500)
            if (t.typeface !== tf) t.typeface = tf
        }
        for (i in 0 until 2) sugDiv[i]?.visibility = if (list.size > i + 1) View.VISIBLE else View.INVISIBLE
        syncTop()
    }

    /** Suggestions replace the toolbar row while typing (like Gboard); the toolbar comes back otherwise. */
    private fun syncTop() {
        if (!::strip.isInitialized || !::topBar.isInitialized) return
        val s = stripMode != 2 && !panelOn
        val sv = if (s) View.VISIBLE else View.GONE; val bv = if (s) View.GONE else View.VISIBLE
        if (strip.visibility != sv) strip.visibility = sv
        if (topBar.visibility != bv) topBar.visibility = bv
    }

    private fun onSuggestion(i: Int) {
        val w = suggestions.getOrNull(i) ?: return
        when (stripMode) { 0 -> pick(w); 1 -> pickEn(w); else -> commit("$w ") }
    }

    private fun refreshEmojiRow() {
        if (emojiCells.isEmpty()) return
        val l = (Prefs.recent(this) + quickEmoji).distinct().take(7)
        for (i in 0 until 7) emojiCells[i].text = l.getOrNull(i) ?: ""
    }

    private fun setPanelMode(on: Boolean) {
        val showEmojiRow = prefEmojiRow && !secure && !on
        panelOn = on; syncTop()
        emojiRow.visibility = if (showEmojiRow) View.VISIBLE else View.GONE
        val extra = if (on && prefEmojiRow && !secure) dp(40) else 0
        body.layoutParams = LinearLayout.LayoutParams(-1, bodyH() + extra)
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
            addView(IconView(this@TypixIME, kind, th.accent), LinearLayout.LayoutParams(dp(32), dp(32)).apply { bottomMargin = dp(10) })
            addView(TextView(this@TypixIME).apply { text = name; textSize = 15f; gravity = Gravity.CENTER; setTextColor(th.text); typeface = Fonts.get(this@TypixIME, 500) },
                LinearLayout.LayoutParams(-1, -2))
            setOnClickListener { act() }
        }
        fun lp() = LinearLayout.LayoutParams(0, dp(120), 1f).apply { setMargins(dp(6), dp(10), dp(6), dp(10)) }
        row.addView(tile(IconView.CLIP, "Clipboard") { showClip() }, lp())
        row.addView(tile(IconView.EDIT, "Text editor") { showEditor() }, lp())
        row.addView(tile(IconView.TUNE, "Settings") { openApp() }, lp())
        col.addView(row, LinearLayout.LayoutParams(-1, 0, 1f))
        col.addView(bottom(), LinearLayout.LayoutParams(-1, dp(44)))
    }

    private var emojiCat = -1
    private class EmojiCat(val kind: Int, val name: String, val list: () -> List<String>)

    private fun showEmoji() {
        panel = 1; refreshIcons()
        val col = panelBase()
        val cats = listOf(
            EmojiCat(IconView.CLOCK, "Recent") { Prefs.recent(this) }, EmojiCat(IconView.EMOJI, "Smileys") { Emojis.smileys },
            EmojiCat(IconView.PERSON, "People") { Emojis.people }, EmojiCat(IconView.FLOWER, "Animals") { Emojis.animals },
            EmojiCat(IconView.CUP, "Food") { Emojis.food }, EmojiCat(IconView.BALL, "Activities") { Emojis.activities },
            EmojiCat(IconView.CAR, "Travel") { Emojis.travel }, EmojiCat(IconView.BULB, "Objects") { Emojis.objects },
            EmojiCat(IconView.HEART, "Symbols") { Emojis.symbols }, EmojiCat(IconView.FLAG, "Flags") { Emojis.flags }
        )
        if (emojiCat < 0) emojiCat = if (Prefs.recent(this).isNotEmpty()) 0 else 1
        val dim = mixColor(th.text, th.bg, 0.40f)
        val grid = EmojiGridView(this, th) { e -> commit(e); Prefs.addRecent(this, e); refreshEmojiRow() }
        val title = TextView(this).apply { textSize = 13f; setTextColor(dim); typeface = Fonts.get(this@TypixIME, 500); setPadding(dp(12), dp(6), 0, dp(2)) }
        val icons = ArrayList<IconView>(); val boxes = ArrayList<FrameLayout>()
        fun select(i: Int) {
            emojiCat = i
            for (j in icons.indices) {
                icons[j].tint(if (j == i) th.accent else dim)
                boxes[j].background = if (j == i) GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(th.key) } else null
            }
            title.text = cats[i].name.uppercase()
            val list = cats[i].list()
            grid.setItems(list, if (list.isEmpty()) "No recent emojis yet\nEmojis you use will show up here" else "")
        }
        val tabs = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val back = FrameLayout(this).apply { addView(IconView(this@TypixIME, IconView.BACK, dim), FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER)); setOnClickListener { showKeys() } }
        tabs.addView(back, LinearLayout.LayoutParams(0, dp(38), 1f).apply { setMargins(dp(1), 0, dp(1), 0) })
        cats.forEachIndexed { i, c ->
            val iv = IconView(this, c.kind, dim)
            val fl = FrameLayout(this).apply { addView(iv, FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER)); setOnClickListener { select(i) } }
            icons.add(iv); boxes.add(fl)
            tabs.addView(fl, LinearLayout.LayoutParams(0, dp(38), 1f).apply { setMargins(dp(1), 0, dp(1), 0) })
        }
        col.addView(tabs, LinearLayout.LayoutParams(-1, dp(42)).apply { setMargins(dp(4), 0, dp(4), 0) })
        col.addView(View(this).apply { setBackgroundColor(th.key) }, LinearLayout.LayoutParams(-1, dp(1)).apply { setMargins(dp(8), dp(3), dp(8), 0) })
        col.addView(title, LinearLayout.LayoutParams(-1, -2))
        col.addView(grid, LinearLayout.LayoutParams(-1, 0, 1f))
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
        if (!restarting) stopVoice()
        hasPartial = false
        buf.setLength(0); enBuf.setLength(0); selecting = false
        val t = info?.inputType ?: 0
        inputType = t
        val cls = t and InputType.TYPE_MASK_CLASS
        val v = t and InputType.TYPE_MASK_VARIATION
        secure = (cls == InputType.TYPE_CLASS_TEXT && (v == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                v == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD || v == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)) ||
                (cls == InputType.TYPE_CLASS_NUMBER && v == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
        val io = info?.imeOptions ?: 0
        imeAction = if (io and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) 0 else io and EditorInfo.IME_MASK_ACTION
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (::th.isInitialized && Prefs.theme(this).name != th.name) { setInputView(onCreateInputView()); return }
        if (::th.isInitialized) applyNav()
        loadPrefs(); applyHand()
        if (::body.isInitialized) {
            showKeys(); keys?.applySettings(); keys?.sinhala = sinhala; keys?.enterKind = enterKind()
            refreshTabs(); refreshEmojiRow(); updateStrip(); autoShift()
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) { stopVoice(); super.onFinishInputView(finishingInput) }
    override fun onFinishInput() { stopVoice(); buf.setLength(0); enBuf.setLength(0); super.onFinishInput() }

    /** English: start of sentence / field asks for capitals -> one-shot Shift, like a normal keyboard. */
    private fun autoShift() {
        val k = keys ?: return
        if (sinhala || secure || !prefAutoCap) { k.setAutoShift(false); return }
        val m = currentInputConnection?.getCursorCapsMode(inputType) ?: 0
        k.setAutoShift(m != 0)
    }

    private fun setLang(s: Boolean) {
        flush(); enBuf.setLength(0); sinhala = s; keys?.sinhala = s
        refreshTabs(); updateStrip(); autoShift()
        voice?.let { if (it.active) { it.setLanguage(voiceLang()); hint("Listening · ${voiceLabel()}") } }
    }
    private fun flush() { if (buf.isNotEmpty()) { currentInputConnection?.finishComposingText(); buf.setLength(0); updateStrip() } }
    private fun commit(s: String) { flush(); enBuf.setLength(0); currentInputConnection?.commitText(s, 1); updateStrip() }

    private fun pick(w: String) {
        val ic = currentInputConnection ?: return
        ic.beginBatchEdit()
        ic.setComposingText(w, 1); ic.finishComposingText(); ic.commitText(" ", 1)
        ic.endBatchEdit()
        buf.setLength(0); lastSpaceAt = 0; updateStrip()
    }

    /** Replace the English word being typed with the chosen completion (only if the text before the cursor really is that word). */
    private fun pickEn(w: String) {
        val ic = currentInputConnection ?: return
        val n = enBuf.length
        ic.beginBatchEdit()
        val before = ic.getTextBeforeCursor(n, 0)
        if (n > 0 && before != null && before.toString().equals(enBuf.toString(), ignoreCase = true)) ic.deleteSurroundingText(n, 0)
        ic.commitText("$w ", 1)
        ic.endBatchEdit()
        enBuf.setLength(0); lastSpaceAt = 0; updateStrip(); autoShift()
    }

    override fun onKey(c: Char) {
        stopVoice()
        val ic = currentInputConnection ?: return
        if (sinhala && !secure && c.isLetter()) {
            buf.append(c); ic.setComposingText(Transliterator.transliterate(buf.toString()), 1); updateStrip(); return
        }
        flush()
        if (!sinhala && !secure && (c.isLetter() || (c == '\'' && enBuf.isNotEmpty()))) enBuf.append(c) else enBuf.setLength(0)
        ic.commitText(c.toString(), 1)
        updateStrip()
        if (!sinhala && (c == '.' || c == '!' || c == '?')) autoShift()
    }

    override fun onSpace() {
        stopVoice()
        val ic = currentInputConnection ?: return
        ic.beginBatchEdit()
        if (buf.isNotEmpty()) {
            if (prefAutocorrect) Dict.exact(buf.toString())?.let { ic.setComposingText(it, 1) }
            ic.finishComposingText(); buf.setLength(0)
        }
        val now = SystemClock.uptimeMillis()
        var dot = false
        if (prefDblSpace && now - lastSpaceAt < 450) {          // double space -> ". "
            val before = ic.getTextBeforeCursor(2, 0)
            if (before != null && before.length == 2 && before[1] == ' ' && !before[0].isWhitespace() && before[0] !in ".,!?;:") {
                ic.deleteSurroundingText(1, 0); ic.commitText(". ", 1); dot = true
            }
        }
        if (!dot) ic.commitText(" ", 1)
        ic.endBatchEdit()
        lastSpaceAt = if (dot) 0 else now
        enBuf.setLength(0); updateStrip(); autoShift()
    }

    override fun onBackspace() {
        val ic = currentInputConnection ?: return
        if (buf.isNotEmpty()) {
            buf.deleteCharAt(buf.length - 1)
            if (buf.isEmpty()) ic.commitText("", 1) else ic.setComposingText(Transliterator.transliterate(buf.toString()), 1)
            updateStrip()
        } else {
            if (enBuf.isNotEmpty()) enBuf.setLength(enBuf.length - 1)
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
            updateStrip()
        }
    }

    override fun onEnter() {
        stopVoice(); flush(); enBuf.setLength(0)
        val ic = currentInputConnection ?: return
        if (imeAction != 0 && imeAction != EditorInfo.IME_ACTION_NONE && imeAction != EditorInfo.IME_ACTION_UNSPECIFIED) ic.performEditorAction(imeAction)
        else sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        updateStrip(); autoShift()
    }

    override fun onCursor(dir: Int) {
        flush(); enBuf.setLength(0)
        sendDownUpKeyEvents(if (dir < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT)
        updateStrip()
    }

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
