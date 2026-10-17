package app.typix

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.text.InputType
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*

/** Miniature keyboard drawn with a theme's colours (themes grid + height preview). */
class PreviewView(ctx: Context, private val th: Theme, private val heightDp: Float, private val selected: Boolean = false) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()
    private val d = ctx.resources.displayMetrics.density
    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(MeasureSpec.getSize(w), (heightDp * d).toInt())
    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        p.style = Paint.Style.FILL
        p.color = th.bg; r.set(0f, 0f, w, h); c.drawRoundRect(r, 22 * d, 22 * d, p)
        val keyC = if (lumaOf(th.bg) < 128) mixColor(th.key, th.text, 0.10f) else th.key
        val padX = 14 * d; val padT = 14 * d; val padB = 14 * d
        val rows = listOf(8 to 0f, 7 to 0.5f, 6 to 1f)
        val u = (w - 2 * padX) / 8f
        val rh = (h - padT - padB) / 4f
        val kh = rh * 0.62f
        p.color = keyC
        for ((i, row) in rows.withIndex()) for (k in 0 until row.first) {
            val x = padX + (row.second + k) * u + u * 0.09f; val y = padT + i * rh + (rh - kh) / 2
            r.set(x, y, x + u * 0.82f, y + kh); c.drawRoundRect(r, 5 * d, 5 * d, p)
        }
        val y = padT + 3 * rh + (rh - kh) / 2
        r.set(padX + u * 0.09f, y, padX + u * 1.9f, y + kh); c.drawRoundRect(r, 6 * d, 6 * d, p)
        r.set(padX + u * 2.1f, y, padX + u * 6.2f, y + kh); c.drawRoundRect(r, 6 * d, 6 * d, p)
        p.color = th.accent
        r.set(padX + u * 6.4f, y, padX + u * 7.91f, y + kh); c.drawRoundRect(r, 6 * d, 6 * d, p)
        if (selected) {
            p.style = Paint.Style.STROKE; p.strokeWidth = 3 * d; p.color = th.accent
            val i = 1.5f * d; r.set(i, i, w - i, h - i); c.drawRoundRect(r, 21 * d, 21 * d, p)
        }
    }
}

/** Pill toggle from the design: lime track + dark thumb when on, navy track + lavender thumb when off. */
class ToggleView(ctx: Context, private var on: Boolean, private val onTrack: Int, private val offTrack: Int,
                 private val onThumb: Int, private val offThumb: Int, private val onChange: (Boolean) -> Unit) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()
    private val d = ctx.resources.displayMetrics.density
    private var t = if (on) 1f else 0f
    init {
        setOnClickListener {
            on = !on; onChange(on)
            ValueAnimator.ofFloat(t, if (on) 1f else 0f).apply { duration = 160; addUpdateListener { t = it.animatedValue as Float; invalidate() }; start() }
        }
    }
    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension((52 * d).toInt(), (32 * d).toInt())
    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        p.color = mixColor(offTrack, onTrack, t); r.set(0f, 0f, w, h); c.drawRoundRect(r, h / 2, h / 2, p)
        p.color = mixColor(offThumb, onThumb, t)
        c.drawCircle(h / 2 + t * (w - h), h / 2, h / 2 - 5 * d, p)
    }
}

/** Home / Themes / Settings, built in code (no layouts, no libraries). Follows the Typix design. */
class SettingsActivity : Activity() {
    private var tab = 0
    private var pending: String? = null
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private var built = false
    override fun onResume() { super.onResume(); if (!built || tab == 0) { built = true; show(tab) } }

    private var dark = true
    private val navy = 0xFF0E1224.toInt()
    private val pageBg: Int get() = if (dark) navy else 0xFFF2F4FB.toInt()
    private val surface: Int get() = if (dark) 0xFF1B2140.toInt() else 0xFFFFFFFF.toInt()
    private val field: Int get() = if (dark) 0xFF131833.toInt() else 0xFFFFFFFF.toInt()
    private val border: Int get() = if (dark) 0xFF2A3260.toInt() else 0xFFDDE2F2.toInt()
    private val line: Int get() = if (dark) 0xFF1B2140.toInt() else 0xFFE3E6F3.toInt()
    private val ink: Int get() = if (dark) 0xFFEEF1FF.toInt() else 0xFF1A1F3A.toInt()
    private val soft: Int get() = if (dark) 0xFF8A93C0.toInt() else 0xFF6B73A0.toInt()
    private val chip: Int get() = if (dark) 0xFF232B52.toInt() else 0xFFE9ECF7.toInt()
    private val heroBg: Int get() = if (dark) 0xFF1B2140.toInt() else navy
    private val light = 0xFFEEF1FF.toInt()
    private val muted = 0xFF8A93C0.toInt()

    /** Accent used as *text* on the page: readable on the current page background. */
    private fun accentText(th: Theme): Int = if (dark && lumaOf(th.accent) > 90) th.accent else if (dark) light else ink

    private fun lp(w: Int = -1, h: Int = -2, top: Int = 0, bottom: Int = 0) =
        LinearLayout.LayoutParams(w, h).apply { topMargin = dp(top); bottomMargin = dp(bottom) }

    private fun tv(t: CharSequence, size: Float, weight: Int = 400, color: Int = ink) =
        TextView(this).apply { text = t; textSize = size; setTextColor(color); typeface = Fonts.get(this@SettingsActivity, weight) }

    private fun label(t: String) = tv(t, 15f, 500, soft).apply { setPadding(dp(2), dp(22), 0, dp(10)) }

    private fun card(padH: Int = 20, padV: Int = 6) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(padH), dp(padV), dp(padH), dp(padV))
        background = GradientDrawable().apply { cornerRadius = dp(24).toFloat(); setColor(surface); if (!dark) setStroke(dp(1), border) }
    }

    private fun divider() = View(this).apply { setBackgroundColor(line); layoutParams = LinearLayout.LayoutParams(-1, dp(1)) }

    private fun pill(text: String, fill: Int, color: Int, onClick: () -> Unit) = tv(text, 16f, 600, color).apply {
        gravity = Gravity.CENTER; setPadding(dp(20), dp(10), dp(20), dp(10))
        background = GradientDrawable().apply { cornerRadius = dp(16).toFloat(); setColor(fill) }
        setOnClickListener { onClick() }
    }

    private fun bigButton(th: Theme, text: String, onClick: () -> Unit) = tv(text, 19f, 600, th.onAccent).apply {
        gravity = Gravity.CENTER
        background = GradientDrawable().apply { cornerRadius = dp(28).toFloat(); setColor(th.accent) }
        setOnClickListener { onClick() }
    }

    private fun toggle(th: Theme, k: String, def: Boolean) = ToggleView(this, Prefs.bool(this, k, def),
        th.accent, if (dark) 0xFF2A3262.toInt() else 0xFFD3D8EA.toInt(), th.onAccent, if (dark) muted else 0xFFFFFFFF.toInt()
    ) { Prefs.setBool(this, k, it) }

    private fun switchRow(c: LinearLayout, th: Theme, k: String, title: String, sub: String?, def: Boolean) {
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(14), 0, dp(14)) }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(tv(title, 17f))
        if (sub != null) col.addView(tv(sub, 13f, 400, soft).apply { setPadding(0, dp(2), 0, 0) })
        row.addView(col, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(12) })
        row.addView(toggle(th, k, def))
        c.addView(row)
    }

    private fun seg(th: Theme, opts: List<String>, sel: Int, onPick: (Int) -> Unit) = LinearLayout(this).apply {
        background = GradientDrawable().apply { cornerRadius = dp(16).toFloat(); setColor(if (dark) 0xFF131833.toInt() else 0xFFEEF0F9.toInt()) }
        setPadding(dp(4), dp(4), dp(4), dp(4))
        opts.forEachIndexed { i, o ->
            addView(tv(o, 14f, if (i == sel) 600 else 500, if (i == sel) th.onAccent else soft).apply {
                gravity = Gravity.CENTER; setPadding(0, dp(11), 0, dp(11))
                if (i == sel) background = GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(th.accent) }
                setOnClickListener { onPick(i) }
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
    }

    private fun step(th: Theme, title: String, done: Boolean, action: String, onClick: () -> Unit) = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(10), 0, dp(10))
        val mark = FrameLayout(this@SettingsActivity).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                if (done) setColor(th.accent) else { setColor(0); setStroke(dp(2), if (dark) 0xFF3A4380.toInt() else 0xFFC3C9E0.toInt()) }
            }
            if (done) addView(IconView(this@SettingsActivity, IconView.CHECK, th.onAccent), FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER))
        }
        addView(mark, LinearLayout.LayoutParams(dp(28), dp(28)).apply { rightMargin = dp(16) })
        addView(tv(title, 17f, 500), LinearLayout.LayoutParams(0, -2, 1f))
        if (!done) addView(pill(action, th.accent, th.onAccent, onClick))
    }

    private fun saveSettings() = startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
        .setType("application/json").putExtra(Intent.EXTRA_TITLE, "typix-settings.json"), 41)
    private fun loadSettings() = startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), 42)

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            if (requestCode == 41) {
                contentResolver.openOutputStream(uri)?.use { it.write(Prefs.exportJson(this).toByteArray(Charsets.UTF_8)) }
                Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
            } else if (requestCode == 42) {
                val out = java.io.ByteArrayOutputStream(); val buf = ByteArray(8192)
                contentResolver.openInputStream(uri)?.use { s ->
                    while (true) { val n = s.read(buf); if (n < 0) break; out.write(buf, 0, n); if (out.size() > 262144) throw java.io.IOException("too big") }
                }
                val n = Prefs.importJson(this, out.toString("UTF-8"))
                Toast.makeText(this, if (n > 0) "Settings restored" else "That isn't a Typix settings file", Toast.LENGTH_SHORT).show()
                show(tab)
            }
        } catch (e: Exception) { Toast.makeText(this, "Couldn't read or write that file", Toast.LENGTH_SHORT).show() }
    }

    override fun onPause() {
        super.onPause()
        if (Prefs.bool(this, "autosync", true) && CloudSync.signedIn(this)) CloudSync.upload(this) { }
    }

    private var scroller: ScrollView? = null
    private fun show(t: Int) {
        val keepY = if (t == tab) scroller?.scrollY ?: 0 else 0
        tab = t; val th = Prefs.theme(this)
        dark = when (Prefs.appMode(this)) { 1 -> false; 2 -> true; else -> (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES }
        window.statusBarColor = pageBg; window.navigationBarColor = pageBg
        var flags = 0
        if (!dark) flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or (if (android.os.Build.VERSION.SDK_INT >= 26) 0x10 else 0)
        window.decorView.systemUiVisibility = flags
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(pageBg); fitsSystemWindows = true }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(14), dp(20), dp(24)) }
        when (t) { 0 -> home(col, th); 1 -> themes(col, th); else -> settings(col, th) }
        val sv = ScrollView(this).apply { isVerticalScrollBarEnabled = false; isFillViewport = true; addView(col) }
        scroller = sv
        if (keepY > 0) sv.post { sv.scrollTo(0, keepY) }
        root.addView(sv, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(View(this).apply { setBackgroundColor(line) }, LinearLayout.LayoutParams(-1, dp(1)))
        val bar = LinearLayout(this).apply { setBackgroundColor(pageBg); setPadding(dp(8), dp(10), dp(8), dp(10)) }
        listOf(Triple("Home", IconView.HOME, 0), Triple("Themes", IconView.PALETTE, 1), Triple("Settings", IconView.TUNE, 2)).forEach { (n, k, i) ->
            val on = i == t
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(0, dp(10), 0, dp(10))
                if (on) background = GradientDrawable().apply { cornerRadius = dp(20).toFloat(); setColor(surface) }
                setOnClickListener { if (i != tab) show(i) }
            }
            val tint = if (on) accentText(th) else soft
            item.addView(IconView(this, k, tint), LinearLayout.LayoutParams(dp(26), dp(26)))
            item.addView(tv(n, 13.5f, if (on) 600 else 500, tint).apply { gravity = Gravity.CENTER; setPadding(0, dp(3), 0, 0) })
            bar.addView(item, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(4), 0, dp(4), 0) })
        }
        root.addView(bar, LinearLayout.LayoutParams(-1, -2))
        setContentView(root)
    }

    private fun home(c: LinearLayout, th: Theme) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val on = imm.enabledInputMethodList.any { it.packageName == packageName }
        val cur = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)?.startsWith(packageName) == true
        val ready = on && cur

        // header: wordmark + light/dark toggle
        val head = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val mark = SpannableString("typix.").apply { setSpan(ForegroundColorSpan(th.accent), 5, 6, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
        head.addView(tv(mark, 36f, 700).apply { letterSpacing = -0.03f }, LinearLayout.LayoutParams(0, -2, 1f))
        val mode = FrameLayout(this).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(surface); if (!dark) setStroke(dp(1), border) }
            addView(IconView(this@SettingsActivity, IconView.SUN, ink), FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER))
            setOnClickListener { Prefs.setAppMode(this@SettingsActivity, if (dark) 1 else 2); show(tab) }
        }
        head.addView(mode, LinearLayout.LayoutParams(dp(44), dp(44)))
        c.addView(head, lp(bottom = 18))

        // hero
        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(20), dp(20), dp(22))
            background = GradientDrawable().apply { cornerRadius = dp(28).toFloat(); setColor(heroBg) }
        }
        val status = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL; setPadding(dp(14), dp(9), dp(18), dp(9))
            background = GradientDrawable().apply { cornerRadius = dp(20).toFloat(); setColor(navy) }
        }
        val dot = if (ready) th.accent else 0xFFFFA726.toInt()
        status.addView(View(this).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(dot) } },
            LinearLayout.LayoutParams(dp(9), dp(9)).apply { rightMargin = dp(10) })
        status.addView(tv(if (ready) "Active" else if (on) "Not selected yet" else "Not enabled yet", 16f, 500, dot))
        hero.addView(status, LinearLayout.LayoutParams(-2, -2))
        hero.addView(tv(if (ready) "කීබෝඩ් එක සූදානම්" else "සූදානම් කරමු", 28f, 600, light).apply { setPadding(0, dp(14), 0, dp(8)) })
        hero.addView(tv("Type Sinhala the way you speak it. Write mama and get මම.", 16f, 400, muted).apply { setLineSpacing(0f, 1.15f) })
        c.addView(hero)

        c.addView(label("Try it here"))
        c.addView(EditText(this).apply {
            hint = "amma, thaththa, ayubowan…"; textSize = 18f; typeface = Fonts.get(this@SettingsActivity, 400)
            setTextColor(ink); setHintTextColor(mixColor(soft, pageBg, 0.25f)); setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT
            setPadding(dp(18), dp(16), dp(18), dp(16))
            val bgD = GradientDrawable().apply { cornerRadius = dp(18).toFloat(); setColor(field); setStroke(dp(2), border) }
            background = bgD
            setOnFocusChangeListener { _, f -> bgD.setStroke(dp(2), if (f) th.accent else border) }
        }, lp())

        c.addView(label("Setup"))
        val setup = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        setup.addView(step(th, "Enable Typix keyboard", on, "Enable") { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) })
        setup.addView(step(th, "Switch to Typix", cur, "Switch") { imm.showInputMethodPicker() })
        c.addView(setup)

        c.addView(View(this).apply { setBackgroundColor(line) }, lp(h = 1, top = 14))
        val q = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fun row(k: String, title: String, def: Boolean, last: Boolean = false) {
            switchRow(q, th, k, title, null, def); if (!last) q.addView(divider())
        }
        row("hints", "Sinhala key hints", true); row("autocorrect", "Auto-correct Singlish", true, true)
        c.addView(q)

        c.addView(label("How to type"))
        val tips = listOf("mama" to "මම", "aa" to "ආ", "A" to "ඇ", "ii" to "ඊ", "th" to "ත", "dh" to "ද", "kh" to "ඛ", "q" to "ද", "x" to "ං",
            "N" to "ණ", "L" to "ළ", "S" to "ෂ", "T" to "ඨ", "D" to "ඪ", "B" to "භ", "G" to "ඝ", "C" to "ඡ", "J" to "ඣ")
        val grid = card(12, 12)
        tips.chunked(3).forEachIndexed { ri, rowTips ->
            val r = LinearLayout(this).apply { weightSum = 3f }
            for (t in rowTips) {
                val cellV = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(8), dp(10), dp(8), dp(10)) }
                cellV.addView(tv(t.first, 15f, 500, soft), LinearLayout.LayoutParams(0, -2, 1f))
                cellV.addView(tv("→", 14f, 400, soft).apply { setPadding(dp(4), 0, dp(8), 0) })
                cellV.addView(tv(t.second, 22f, 600, accentText(th)))
                r.addView(cellV, LinearLayout.LayoutParams(0, -2, 1f))
            }
            grid.addView(r)
            if (ri < (tips.size - 1) / 3) grid.addView(divider())
        }
        c.addView(grid)
        c.addView(tv("Tip: Shift + a types ඇ. Press and hold a letter for its number, hold \".\" for punctuation, and swipe on the space bar to move the cursor.", 13.5f, 400, soft).apply { setPadding(dp(4), dp(12), dp(4), 0); setLineSpacing(0f, 1.15f) })
    }

    private fun themes(c: LinearLayout, th: Theme) {
        if (pending == null) pending = th.name
        c.addView(tv("Themes", 36f, 700).apply { letterSpacing = -0.02f; setPadding(dp(2), 0, 0, 0) })
        c.addView(tv("Pick how your keyboard looks.", 17f, 400, soft).apply { setPadding(dp(2), dp(4), 0, dp(20)) })
        val sel = pending
        Prefs.themes.chunked(2).forEach { pair ->
            val row = LinearLayout(this).apply { weightSum = 2f }
            pair.forEachIndexed { i, t ->
                val tile = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setOnClickListener { pending = t.name; show(1) } }
                tile.addView(PreviewView(this, t, 116f, t.name == sel), lp())
                val nameRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(4), dp(8), dp(4), dp(0)) }
                nameRow.addView(tv(t.name, 17f, 500), LinearLayout.LayoutParams(0, -2, 1f))
                if (t.name == sel) nameRow.addView(FrameLayout(this).apply {
                    background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(t.accent) }
                    addView(IconView(this@SettingsActivity, IconView.CHECK, t.onAccent), FrameLayout.LayoutParams(dp(16), dp(16), Gravity.CENTER))
                }, LinearLayout.LayoutParams(dp(26), dp(26)))
                tile.addView(nameRow)
                row.addView(tile, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(if (i == 0) 0 else dp(8), 0, if (i == 0) dp(8) else 0, dp(16)) })
            }
            c.addView(row)
        }
        val chosen = Prefs.themeByName(sel)
        c.addView(bigButton(chosen, if (sel == th.name) "Theme applied" else "Apply theme") {
            Prefs.setTheme(this, chosen.name); pending = null; Toast.makeText(this, "${chosen.name} applied", Toast.LENGTH_SHORT).show(); show(1)
        }, lp(h = dp(56), top = 8))
        customize(c, th)
    }

    /** Per-theme customising of the applied theme: button size, key colour, letter colour, number colour. */
    private fun customize(c: LinearLayout, th: Theme) {
        val n = th.name
        c.addView(label("Customize · $n"))
        val k = card(); k.setPadding(dp(16), dp(16), dp(16), dp(16))
        val keyC = Prefs.ov(this, n, "key"); val textC = Prefs.ov(this, n, "text"); val numC = Prefs.ov(this, n, "num")
        val size = Prefs.ov(this, n, "size", 100).coerceIn(70, 108)
        k.addView(KeyPreviewView(this, th, if (keyC != 0) keyC else th.key, if (textC != 0) textC else th.text,
            if (numC != 0) numC else mixColor(th.text, th.bg, 0.55f), size))
        // button size stepper
        k.addView(tv("Button size", 17f).apply { setPadding(dp(4), dp(18), 0, dp(10)) })
        val step = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        fun stepBtn(t: String, delta: Int) = tv(t, 24f, 600, th.onAccent).apply {
            gravity = Gravity.CENTER
            background = GradientDrawable().apply { cornerRadius = dp(16).toFloat(); setColor(th.accent) }
            setOnClickListener { Prefs.setOv(this@SettingsActivity, n, "size", (size + delta).coerceIn(70, 108)); show(1) }
        }
        step.addView(stepBtn("−", -4), LinearLayout.LayoutParams(dp(64), dp(48)))
        step.addView(tv("$size%", 19f, 600).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(0, -2, 1f))
        step.addView(stepBtn("+", 4), LinearLayout.LayoutParams(dp(64), dp(48)))
        k.addView(step)
        fun colors(title: String, f: String, cur: Int, shownDefault: Int) {
            k.addView(tv(title, 17f).apply { setPadding(dp(4), dp(20), 0, dp(6)) })
            Swatches.colors.toList().chunked(8).forEach { rowColors ->
                val row = LinearLayout(this).apply { weightSum = 8f }
                for (col in rowColors) row.addView(SwatchView(this, col, if (col == 0) shownDefault else col, col == cur, th.accent, if (dark) 0xFF4A5380.toInt() else 0xFFB8BED6.toInt()) {
                    Prefs.setOv(this, n, f, col); show(1)
                }, LinearLayout.LayoutParams(0, -2, 1f))
                k.addView(row)
            }
        }
        colors("Key color", "key", keyC, th.key)
        colors("Letter color", "text", textC, th.text)
        colors("Number color", "num", numC, mixColor(th.text, th.bg, 0.55f))
        k.addView(tv("Reset $n customizing", 16f, 600, accentText(th)).apply {
            setPadding(0, dp(20), 0, dp(4))
            setOnClickListener { Prefs.clearOv(this@SettingsActivity, n); show(1) }
        })
        c.addView(k)
        c.addView(tv("These changes belong to the $n theme, so every theme can have its own look.", 13f, 400, soft).apply { setPadding(dp(4), dp(10), dp(4), dp(10)) })
    }

    private fun settings(c: LinearLayout, th: Theme) {
        c.addView(tv("Settings", 36f, 700).apply { letterSpacing = -0.02f; setPadding(dp(2), 0, 0, 0) })
        c.addView(label("Appearance").apply { setPadding(dp(2), dp(10), 0, dp(10)) })
        var k = card(); k.setPadding(dp(16), dp(14), dp(16), dp(14))
        k.addView(tv("App theme", 17f).apply { setPadding(dp(4), 0, 0, dp(10)) })
        k.addView(seg(th, listOf("Auto", "Light", "Dark"), Prefs.appMode(this)) { Prefs.setAppMode(this, it); show(2) })
        c.addView(k)
        c.addView(label("Language"))
        k = card(); k.setPadding(dp(16), dp(14), dp(16), dp(14))
        k.addView(tv("Start typing in", 17f).apply { setPadding(dp(4), 0, 0, dp(10)) })
        k.addView(seg(th, listOf("සිංහල", "English"), Prefs.int(this, "startlang", 0)) { Prefs.setInt(this, "startlang", it); show(2) })
        c.addView(k)
        c.addView(label("Typing"))
        k = card()
        switchRow(k, th, "borders", "Key outlines", "Draw a thin border around every key", false); k.addView(divider())
        switchRow(k, th, "hints", "Sinhala key hints", "Show the Sinhala letter on each key", true); k.addView(divider())
        switchRow(k, th, "autocorrect", "Auto-correct Singlish", "Fix common words as you type", true); k.addView(divider())
        switchRow(k, th, "autocap", "Auto-capitalize", "Capital letter at the start of English sentences", true); k.addView(divider())
        switchRow(k, th, "dblspace", "Double space for full stop", "Tap space twice to type \". \"", true); k.addView(divider())
        switchRow(k, th, "swipecursor", "Space-bar cursor swipe", "Slide on the space bar to move the cursor", true)
        c.addView(k)
        c.addView(label("Suggestions"))
        k = card()
        switchRow(k, th, "ensugg", "English suggestions", "Word completions while typing English", true); k.addView(divider())
        switchRow(k, th, "emojirow", "Emoji row", "Show your recent emojis above the keys", true)
        c.addView(k)
        c.addView(label("Feedback"))
        k = card()
        switchRow(k, th, "vibrate", "Vibrate on key press", null, false)
        k.addView(divider())
        k.addView(seg(th, listOf("Light", "Medium", "Strong"), Prefs.int(this, "vibstr", 1)) { Prefs.setInt(this, "vibstr", it); show(2) }, lp(top = 12, bottom = 8))
        c.addView(k)
        c.addView(label("Key sound"))
        k = card(); k.setPadding(dp(16), dp(14), dp(16), dp(14))
        k.addView(seg(th, listOf("Off", "Click", "Keypress"), Prefs.int(this, "soundstyle", 0)) { Prefs.setInt(this, "soundstyle", it); show(2) })
        k.addView(tv("Uses the phone's own key sounds, so \"Touch sounds\" must be on in the phone's sound settings.", 13f, 400, soft).apply { setPadding(0, dp(10), 0, 0) })
        c.addView(k)
        c.addView(label("Key style"))
        k = card(); k.setPadding(dp(16), dp(14), dp(16), dp(14))
        k.addView(tv("Key shape", 17f).apply { setPadding(dp(4), 0, 0, dp(10)) })
        k.addView(seg(th, listOf("Soft", "Square", "Round"), Prefs.int(this, "keyshape", 0)) { Prefs.setInt(this, "keyshape", it); show(2) })
        k.addView(tv("Key spacing", 17f).apply { setPadding(dp(4), dp(16), 0, dp(10)) })
        k.addView(seg(th, listOf("Tight", "Normal", "Wide"), Prefs.int(this, "keygap", 1)) { Prefs.setInt(this, "keygap", it); show(2) })
        k.addView(tv("Letter size", 17f).apply { setPadding(dp(4), dp(16), 0, dp(10)) })
        k.addView(seg(th, listOf("Small", "Medium", "Large"), Prefs.int(this, "keytext", 1)) { Prefs.setInt(this, "keytext", it); show(2) })
        c.addView(k)
        c.addView(label("One-handed mode"))
        k = card(); k.setPadding(dp(16), dp(14), dp(16), dp(14))
        k.addView(seg(th, listOf("Off", "Left", "Right"), Prefs.int(this, "onehand", 0)) { Prefs.setInt(this, "onehand", it); show(2) })
        c.addView(k)
        c.addView(label("Keyboard height"))
        k = card(); k.setPadding(dp(16), dp(16), dp(16), dp(16))
        val idx = Prefs.heightIdx(this)
        k.addView(seg(th, Prefs.heights.map { it.first }, idx) { Prefs.setHeightIdx(this, it); show(2) })
        k.addView(PreviewView(this, th, Prefs.heights[idx].second * 2f + 20f), lp(top = 14))
        c.addView(k)
        c.addView(label("Clipboard"))
        k = card()
        switchRow(k, th, "clipboard", "Clipboard history", "Keep recent copies in the clipboard panel", true)
        k.addView(divider())
        k.addView(tv("Clear pinned clips", 16f, 600, accentText(th)).apply {
            setPadding(0, dp(16), 0, dp(16))
            setOnClickListener { Prefs.setPins(this@SettingsActivity, emptyList()); ClipStore.clearRecent(); Toast.makeText(this@SettingsActivity, "Cleared", Toast.LENGTH_SHORT).show() }
        })
        c.addView(k)
        c.addView(label("Google account"))
        k = card()
        val acct = CloudSync.account(this)
        if (acct == null) {
            k.addView(tv("Sign in with Google", 16f, 600, accentText(th)).apply { setPadding(0, dp(16), 0, dp(16)); setOnClickListener {
                CloudSync.signIn(this@SettingsActivity) { err ->
                    Toast.makeText(this@SettingsActivity, err ?: "Signed in - settings synced", Toast.LENGTH_SHORT).show(); show(tab)
                }
            } })
        } else {
            k.addView(tv(acct, 16f, 600).apply { setPadding(0, dp(16), 0, dp(4)) })
            k.addView(tv("Signed in with Google", 13f, 400, soft).apply { setPadding(0, 0, 0, dp(12)) })
            k.addView(divider())
            switchRow(k, th, "autosync", "Sync automatically", "Save your settings to your account when you leave this screen", true); k.addView(divider())
            k.addView(tv("Save settings to account now", 16f, 600, accentText(th)).apply { setPadding(0, dp(16), 0, dp(16)); setOnClickListener {
                CloudSync.upload(this@SettingsActivity) { err -> Toast.makeText(this@SettingsActivity, err ?: "Saved to your account", Toast.LENGTH_SHORT).show() }
            } })
            k.addView(divider())
            k.addView(tv("Restore settings from account", 16f, 600, accentText(th)).apply { setPadding(0, dp(16), 0, dp(16)); setOnClickListener {
                CloudSync.download(this@SettingsActivity) { n, err ->
                    Toast.makeText(this@SettingsActivity, err ?: if (n > 0) "Settings restored" else "Nothing saved in your account yet", Toast.LENGTH_SHORT).show(); if (n > 0) show(tab)
                }
            } })
            k.addView(divider())
            k.addView(tv("Sign out", 16f, 600, accentText(th)).apply { setPadding(0, dp(16), 0, dp(16)); setOnClickListener { CloudSync.signOut(this@SettingsActivity); show(tab) } })
        }
        c.addView(k)
        c.addView(tv("Only your keyboard settings (theme, colors, switches) are saved to your account. What you type, your clipboard and pinned clips never leave the phone.", 13f, 400, soft).apply { setPadding(dp(4), dp(10), dp(4), dp(4)) })
        c.addView(label("Backup & restore"))
        k = card()
        k.addView(tv("Save settings to a file", 16f, 600, accentText(th)).apply { setPadding(0, dp(16), 0, dp(16)); setOnClickListener { saveSettings() } })
        k.addView(divider())
        k.addView(tv("Restore settings from a file", 16f, 600, accentText(th)).apply { setPadding(0, dp(16), 0, dp(16)); setOnClickListener { loadSettings() } })
        c.addView(k)
        c.addView(tv("Themes, colors and all your settings. Choose Google Drive when the phone asks where to save, and you can restore them on any phone. Android also backs these up to your Google account automatically when phone backup is on.", 13f, 400, soft).apply { setPadding(dp(4), dp(10), dp(4), dp(4)) })
        c.addView(label("Reset"))
        k = card()
        k.addView(tv("Reset keyboard style", 16f, 600, accentText(th)).apply {
            setPadding(0, dp(16), 0, dp(16))
            setOnClickListener { for (n in listOf("keyshape", "keygap", "keytext", "onehand")) Prefs.setInt(this@SettingsActivity, n, if (n == "keygap" || n == "keytext") 1 else 0); Prefs.setHeightIdx(this@SettingsActivity, 1); show(2) }
        })
        c.addView(k)
        c.addView(label("About"))
        k = card(); k.setPadding(dp(20), dp(16), dp(20), dp(16))
        k.addView(tv("Typix 0.6.0", 17f, 600).apply { setPadding(0, 0, 0, dp(4)) })
        k.addView(tv("Typix has no internet permission. Nothing you type leaves this phone.", 14f, 400, soft).apply { setLineSpacing(0f, 1.1f) })
        c.addView(k)
    }
}
