package app.typix

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*

/** Miniature keyboard drawn with a theme's colours; its height follows the chosen key height. */
class PreviewView(ctx: Context, private val th: Theme, private val rowH: Float) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()
    private val d = ctx.resources.displayMetrics.density
    private val dim: Int get() = (th.text and 0x00FFFFFF) or 0x88000000.toInt()
    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(MeasureSpec.getSize(w), (rowH * 4 * 0.42f * d + 44 * d).toInt())
    override fun onDraw(c: Canvas) {
        p.style = Paint.Style.FILL
        p.color = th.bg; r.set(0f, 0f, width.toFloat(), height.toFloat()); c.drawRoundRect(r, 16 * d, 16 * d, p)
        val pad = 12 * d; val tb = 22 * d
        p.color = th.accent; c.drawRoundRect(pad, pad, pad + tb, pad + tb, 7 * d, 7 * d, p)
        p.color = th.key; c.drawRoundRect(pad + tb + 8 * d, pad, pad + tb + 46 * d, pad + tb, 9 * d, 9 * d, p)
        p.color = dim
        for (i in 0 until 4) c.drawCircle(width - pad - (9 + i * 22) * d, pad + tb / 2, 5 * d, p)
        val top = pad + tb + 10 * d
        val rh = (height - top - 6 * d) / 4f
        val u = (width - 2 * pad) / 10f
        val rows = listOf(10 to 0f, 9 to 0.5f, 7 to 1.5f)
        for ((i, row) in rows.withIndex()) for (k in 0 until row.first) {
            val cx = pad + (row.second + k + 0.5f) * u; val cy = top + (i + 0.5f) * rh
            c.drawRoundRect(cx - 5 * d, cy - 2.5f * d, cx + 5 * d, cy + 2.5f * d, 2.5f * d, 2.5f * d, p)
        }
        val cy = top + 3.5f * rh
        p.color = th.key; c.drawRoundRect(pad + 3.5f * u, cy - rh * 0.26f, pad + 7.5f * u, cy + rh * 0.26f, rh * 0.26f, rh * 0.26f, p)
        p.color = th.accent; c.drawCircle(pad + 9.25f * u, cy, minOf(rh, 1.5f * u) * 0.38f, p)
    }
}

/** Home / Themes / Settings, built in code (no layouts, no libraries). */
class SettingsActivity : Activity() {
    private var tab = 0
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    override fun onResume() { super.onResume(); show(tab) }

    private var dark = false
    private val white = 0xFFFFFFFF.toInt()
    private val navy = 0xFF0E1224.toInt()
    private val pageBg: Int get() = if (dark) 0xFF0B0F1A.toInt() else 0xFFF3F5F9.toInt()
    private val cardBg: Int get() = if (dark) 0xFF151B2B.toInt() else white
    private val line: Int get() = if (dark) 0xFF252D44.toInt() else 0xFFE3E7EF.toInt()
    private val ink: Int get() = if (dark) 0xFFE8ECF8.toInt() else 0xFF0F172A.toInt()
    private val soft: Int get() = if (dark) 0xFF98A2C3.toInt() else 0xFF64748B.toInt()
    private val chip: Int get() = if (dark) 0xFF232B44.toInt() else 0xFFEEF1F6.toInt()
    private val heroBg: Int get() = if (dark) 0xFF141B34.toInt() else navy
    private val badgeBg: Int get() = if (dark) 0xFF2A3262.toInt() else navy

    private fun lp(w: Int = -1, h: Int = -2, top: Int = 0, bottom: Int = 0) =
        LinearLayout.LayoutParams(w, h).apply { topMargin = dp(top); bottomMargin = dp(bottom) }

    private fun tv(t: String, size: Float, bold: Boolean = false, color: Int = ink) =
        TextView(this).apply { text = t; textSize = size; setTextColor(color); if (bold) setTypeface(typeface, Typeface.BOLD) }

    private fun head(t: String) = tv(t.uppercase(), 11.5f, true, soft).apply { letterSpacing = 0.1f; setPadding(dp(6), 0, 0, dp(8)) }

    private fun card(pad: Int = 16) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(pad), dp(pad - 4), dp(pad), dp(pad - 4))
        background = GradientDrawable().apply { cornerRadius = dp(20).toFloat(); setColor(cardBg); setStroke(dp(1), line) }
        layoutParams = lp(bottom = 16)
    }

    private fun divider() = View(this).apply { setBackgroundColor(chip); layoutParams = LinearLayout.LayoutParams(-1, dp(1)) }

    private fun pill(label: String, fill: Int, color: Int, onClick: () -> Unit) = tv(label, 13f, true, color).apply {
        gravity = Gravity.CENTER; setPadding(dp(16), dp(9), dp(16), dp(9))
        background = GradientDrawable().apply { cornerRadius = dp(20).toFloat(); setColor(fill) }
        setOnClickListener { onClick() }
    }

    private fun button(th: Theme, label: String, filled: Boolean, onClick: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; textSize = 15f; stateListAnimator = null
        setTextColor(if (filled) th.onAccent else ink)
        background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(if (filled) th.accent else chip) }
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(-1, dp(50)).apply { bottomMargin = dp(4) }
    }

    private fun switchRow(c: LinearLayout, th: Theme, k: String, title: String, sub: String?, def: Boolean) {
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(10), 0, dp(10)) }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(tv(title, 15.5f))
        if (sub != null) col.addView(tv(sub, 12.5f, false, soft))
        row.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(Switch(this).apply {
            isChecked = Prefs.bool(this@SettingsActivity, k, def)
            val st = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
            thumbTintList = ColorStateList(st, intArrayOf(th.onAccent, white))
            trackTintList = ColorStateList(st, intArrayOf(th.accent, if (dark) 0xFF3A4363.toInt() else 0xFFCBD2E1.toInt()))
            setOnCheckedChangeListener { _, v -> Prefs.setBool(this@SettingsActivity, k, v) }
        })
        c.addView(row)
    }

    private fun seg(th: Theme, opts: List<String>, sel: Int, onPick: (Int) -> Unit) = LinearLayout(this).apply {
        background = GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(chip) }
        setPadding(dp(3), dp(3), dp(3), dp(3))
        opts.forEachIndexed { i, o ->
            addView(TextView(this@SettingsActivity).apply {
                text = o; gravity = Gravity.CENTER; textSize = 13f; setPadding(0, dp(10), 0, dp(10))
                setTextColor(if (i == sel) th.onAccent else soft)
                if (i == sel) { setTypeface(typeface, Typeface.BOLD); background = GradientDrawable().apply { cornerRadius = dp(10).toFloat(); setColor(th.accent) } }
                setOnClickListener { onPick(i) }
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
    }

    private fun step(th: Theme, n: Int, title: String, sub: String, done: Boolean, action: String, onClick: () -> Unit) = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(12), 0, dp(12))
        addView(tv(if (done) "✓" else n.toString(), 14f, true, white).apply {
            gravity = Gravity.CENTER
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(if (done) 0xFF16A34A.toInt() else badgeBg) }
        }, LinearLayout.LayoutParams(dp(30), dp(30)).apply { rightMargin = dp(12) })
        val col = LinearLayout(this@SettingsActivity).apply { orientation = LinearLayout.VERTICAL }
        col.addView(tv(title, 15.5f, true)); col.addView(tv(sub, 12.5f, false, soft))
        addView(col, LinearLayout.LayoutParams(0, -2, 1f))
        addView(pill(action, if (done) chip else th.accent, if (done) soft else th.onAccent, onClick))
    }

    private fun show(t: Int) {
        tab = t; val th = Prefs.theme(this)
        dark = when (Prefs.appMode(this)) { 1 -> false; 2 -> true; else -> (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES }
        window.statusBarColor = pageBg; window.navigationBarColor = cardBg
        var flags = 0
        if (!dark) flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or (if (android.os.Build.VERSION.SDK_INT >= 26) 0x10 else 0)
        window.decorView.systemUiVisibility = flags
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(pageBg); fitsSystemWindows = true }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(10), dp(18), dp(16)) }
        when (t) { 0 -> home(col, th); 1 -> themes(col); else -> settings(col, th) }
        root.addView(ScrollView(this).apply { isVerticalScrollBarEnabled = false; addView(col) }, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(View(this).apply { setBackgroundColor(line) }, LinearLayout.LayoutParams(-1, dp(1)))
        val bar = LinearLayout(this).apply { setBackgroundColor(cardBg); setPadding(0, dp(8), 0, dp(8)) }
        listOf(Triple("Home", IconView.HOME, 0), Triple("Themes", IconView.PALETTE, 1), Triple("Settings", IconView.TUNE, 2)).forEach { (n, k, i) ->
            val on = i == t
            val item = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setOnClickListener { show(i) } }
            val box = FrameLayout(this).apply { if (on) background = GradientDrawable().apply { cornerRadius = dp(16).toFloat(); setColor(th.accent) } }
            box.addView(IconView(this, k, if (on) th.onAccent else soft), FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER))
            item.addView(box, LinearLayout.LayoutParams(dp(60), dp(32)))
            item.addView(tv(n, 11.5f, on, if (on) ink else soft).apply { gravity = Gravity.CENTER; setPadding(0, dp(3), 0, 0) })
            bar.addView(item, LinearLayout.LayoutParams(0, -2, 1f))
        }
        root.addView(bar, LinearLayout.LayoutParams(-1, -2))
        setContentView(root)
    }

    private fun home(c: LinearLayout, th: Theme) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val on = imm.enabledInputMethodList.any { it.packageName == packageName }
        val cur = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)?.startsWith(packageName) == true
        val ready = on && cur
        val muted = 0xFF8A93C0.toInt(); val light = 0xFFEEF1FF.toInt()

        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(20), dp(20), dp(20))
            background = GradientDrawable().apply { cornerRadius = dp(28).toFloat(); setColor(heroBg) }
            layoutParams = lp(bottom = 16)
        }
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        top.addView(LogoView(this, th.accent), LinearLayout.LayoutParams(dp(56), dp(56)).apply { rightMargin = dp(14) })
        val name = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        name.addView(tv("typix", 28f, true, light)); name.addView(tv("Singlish to සිංහල keyboard", 13f, false, muted))
        top.addView(name, LinearLayout.LayoutParams(0, -2, 1f))
        hero.addView(top)

        val demo = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(14), dp(16), dp(14))
            background = GradientDrawable().apply { cornerRadius = dp(18).toFloat(); setColor(0xFF1B2140.toInt()) }
        }
        demo.addView(tv("YOU TYPE", 10.5f, true, muted).apply { letterSpacing = 0.1f })
        demo.addView(tv("mama yanawaa", 17f, false, light).apply { setPadding(0, dp(2), 0, dp(10)) })
        demo.addView(tv("YOU GET", 10.5f, true, muted).apply { letterSpacing = 0.1f })
        demo.addView(tv("මම යනවා", 24f, true, th.accent).apply { setPadding(0, dp(2), 0, 0) })
        hero.addView(demo, lp(top = 18))

        val status = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        status.addView(View(this).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(if (ready) 0xFF22C55E.toInt() else 0xFFFFA726.toInt()) } },
            LinearLayout.LayoutParams(dp(9), dp(9)).apply { rightMargin = dp(8) })
        status.addView(tv(if (ready) "Active · කීබෝඩ් එක සූදානම්" else if (on) "Enabled, not selected yet" else "Not enabled yet", 13.5f, true, light))
        hero.addView(status, lp(top = 16))
        c.addView(hero)

        c.addView(head("Get started"))
        val setup = card()
        setup.addView(step(th, 1, "Enable keyboard", "Turn on Typix in system settings", on, if (on) "Done" else "Enable") { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) })
        setup.addView(divider())
        setup.addView(step(th, 2, "Switch to Typix", "Pick it as your active keyboard", cur, if (cur) "Done" else "Switch") { imm.showInputMethodPicker() })
        c.addView(setup)

        c.addView(head("Try it"))
        val t = card(); t.addView(EditText(this).apply {
            hint = "Type mama yanawaa here"; textSize = 16f; setTextColor(ink); setHintTextColor(0xFF94A3B8.toInt()); background = null
            setPadding(dp(2), dp(12), dp(2), dp(12))
        }); c.addView(t)

        c.addView(head("Quick settings"))
        val q = card()
        switchRow(q, th, "hints", "Sinhala key hints", "Show the Sinhala letter on each key", true); q.addView(divider())
        switchRow(q, th, "autocorrect", "Auto-correct Singlish", "Fix common words as you type", true)
        c.addView(q)
    }

    private fun themes(c: LinearLayout) {
        c.addView(tv("Themes", 28f, true).apply { setPadding(dp(4), 0, 0, 0) })
        c.addView(tv("Pick how your keyboard looks.", 14f, false, soft).apply { setPadding(dp(4), dp(2), 0, dp(16)) })
        val sel = Prefs.theme(this).name
        for (t in Prefs.themes) {
            val k = card(12)
            if (t.name == sel) (k.background as GradientDrawable).setStroke(dp(2), t.accent)
            k.addView(PreviewView(this, t, 54f), LinearLayout.LayoutParams(-1, -2))
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(4), dp(10), dp(4), dp(2)) }
            row.addView(View(this).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(t.accent) } },
                LinearLayout.LayoutParams(dp(12), dp(12)).apply { rightMargin = dp(8) })
            row.addView(tv(t.name, 16f, true), LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(if (t.name == sel) pill("Selected", t.accent, t.onAccent) {} else pill("Apply", chip, ink) { Prefs.setTheme(this, t.name); show(1) })
            k.addView(row)
            k.setOnClickListener { Prefs.setTheme(this, t.name); show(1) }
            c.addView(k)
        }
    }

    private fun settings(c: LinearLayout, th: Theme) {
        c.addView(tv("Settings", 28f, true).apply { setPadding(dp(4), 0, 0, dp(16)) })
        c.addView(head("Appearance"))
        var k = card()
        k.addView(tv("App theme", 15.5f).apply { setPadding(0, dp(8), 0, dp(8)) })
        k.addView(seg(th, listOf("Auto", "Light", "Dark"), Prefs.appMode(this)) { Prefs.setAppMode(this, it); show(2) }, lp(bottom = 8))
        c.addView(k)
        c.addView(head("Typing"))
        k = card()
        switchRow(k, th, "borders", "Key borders", "Draw a border around every key", true); k.addView(divider())
        switchRow(k, th, "hints", "Sinhala key hints", "Show the Sinhala letter on each key", true); k.addView(divider())
        switchRow(k, th, "autocorrect", "Auto-correct Singlish", "Fix common words as you type", true)
        c.addView(k)
        c.addView(head("Feedback"))
        k = card()
        switchRow(k, th, "sound", "Key sound", null, false); k.addView(divider())
        switchRow(k, th, "vibrate", "Vibrate on key press", null, true)
        c.addView(k)
        c.addView(head("Keyboard height"))
        k = card()
        val idx = Prefs.heightIdx(this)
        k.addView(seg(th, Prefs.heights.map { it.first }, idx) { Prefs.setHeightIdx(this, it); show(2) }, lp(top = 8))
        k.addView(PreviewView(this, th, Prefs.heights[idx].second.toFloat()), lp(top = 12, bottom = 8))
        c.addView(k)
        c.addView(head("Clipboard"))
        k = card()
        switchRow(k, th, "clipboard", "Clipboard history", "Keep recent copies in the clipboard panel", true)
        k.addView(button(th, "Clear pinned clips", false) { Prefs.setPins(this, emptyList()); ClipStore.clearRecent(); Toast.makeText(this, "Cleared", Toast.LENGTH_SHORT).show() })
        c.addView(k)
        c.addView(head("About"))
        k = card()
        k.addView(tv("Typix 0.3.0", 15.5f, true).apply { setPadding(0, dp(6), 0, dp(2)) })
        k.addView(tv("Typix has no internet permission. Nothing you type leaves this phone.", 13f, false, soft).apply { setPadding(0, 0, 0, dp(6)) })
        c.addView(k)
    }
}
