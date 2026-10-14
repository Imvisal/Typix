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
    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(MeasureSpec.getSize(w), (rowH * 4 * 0.42f * d + 14 * d).toInt())
    override fun onDraw(c: Canvas) {
        p.color = th.bg; r.set(0f, 0f, width.toFloat(), height.toFloat()); c.drawRoundRect(r, 14 * d, 14 * d, p)
        val pad = 7 * d; val g = 3 * d; val rh = (height - 2 * pad) / 4f; val u = (width - 2 * pad) / 10f
        val rows = listOf(10 to 0f, 9 to 0.5f, 7 to 1.5f)
        fun key(x: Float, y: Float, w: Float, col: Int) {
            p.color = col; r.set(pad + x * u + g / 2, pad + y * rh + g / 2, pad + (x + w) * u - g / 2, pad + (y + 1) * rh - g / 2); c.drawRoundRect(r, 4 * d, 4 * d, p)
        }
        for ((i, row) in rows.withIndex()) for (k in 0 until row.first) key(row.second + k, i.toFloat(), 1f, th.key)
        key(2.5f, 3f, 5f, th.key); key(8.5f, 3f, 1.5f, th.accent)
    }
}

/** Home / Themes / Settings, built in code (no layouts, no libraries). */
class SettingsActivity : Activity() {
    private var tab = 0
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    override fun onResume() { super.onResume(); show(tab) }

    private val ink = 0xFF1A1F3A.toInt()
    private val soft = 0xFF5B6390.toInt()

    private fun tv(t: String, size: Float, bold: Boolean = false, color: Int = ink) =
        TextView(this).apply { text = t; textSize = size; setTextColor(color); setPadding(0, 0, 0, dp(10)); if (bold) setTypeface(typeface, Typeface.BOLD) }

    private fun head(t: String) = TextView(this).apply {
        text = t.uppercase(); textSize = 12f; setTextColor(soft); letterSpacing = 0.08f; setPadding(dp(4), dp(6), 0, dp(8))
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(14), dp(16), dp(14))
        background = GradientDrawable().apply { cornerRadius = dp(18).toFloat(); setColor(0xFFFFFFFF.toInt()) }
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) }
    }

    private fun button(th: Theme, label: String, filled: Boolean, onClick: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; textSize = 15f
        setTextColor(if (filled) th.onAccent else ink)
        background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(if (filled) th.accent else 0xFFE8EBF7.toInt()) }
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(-1, dp(50)).apply { bottomMargin = dp(8) }
    }

    private fun sw(c: LinearLayout, th: Theme, k: String, label: String, def: Boolean) = c.addView(Switch(this).apply {
        text = label; textSize = 16f; setTextColor(ink); isChecked = Prefs.bool(this@SettingsActivity, k, def); setPadding(0, dp(10), 0, dp(10))
        val st = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        thumbTintList = ColorStateList(st, intArrayOf(th.onAccent, 0xFFFFFFFF.toInt()))
        trackTintList = ColorStateList(st, intArrayOf(th.accent, 0xFFB9BED6.toInt()))
        setOnCheckedChangeListener { _, v -> Prefs.setBool(this@SettingsActivity, k, v) }
    })

    private fun seg(th: Theme, opts: List<String>, sel: Int, onPick: (Int) -> Unit) = LinearLayout(this).apply {
        background = GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(0xFFE8EBF7.toInt()) }
        setPadding(dp(3), dp(3), dp(3), dp(3))
        opts.forEachIndexed { i, o ->
            addView(TextView(this@SettingsActivity).apply {
                text = o; gravity = Gravity.CENTER; textSize = 13f; setPadding(0, dp(10), 0, dp(10))
                setTextColor(if (i == sel) th.onAccent else soft)
                if (i == sel) background = GradientDrawable().apply { cornerRadius = dp(10).toFloat(); setColor(th.accent) }
                setOnClickListener { onPick(i) }
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
    }

    private fun show(t: Int) {
        tab = t; val th = Prefs.theme(this)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(0xFFF2F4FB.toInt()) }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(44), dp(18), dp(12)) }
        when (t) { 0 -> home(col, th); 1 -> themes(col); else -> settings(col, th) }
        root.addView(ScrollView(this).apply { addView(col) }, LinearLayout.LayoutParams(-1, 0, 1f))
        val bar = LinearLayout(this).apply { setBackgroundColor(0xFF0E1224.toInt()); setPadding(0, dp(8), 0, dp(8)) }
        listOf(Triple("Home", IconView.HOME, 0), Triple("Themes", IconView.PALETTE, 1), Triple("Settings", IconView.TUNE, 2)).forEach { (n, k, i) ->
            val c = if (i == t) th.accent else 0xFF8A93C0.toInt()
            val item = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setOnClickListener { show(i) } }
            item.addView(IconView(this, k, c), LinearLayout.LayoutParams(dp(26), dp(26)))
            item.addView(TextView(this).apply { text = n; textSize = 12f; setTextColor(c); gravity = Gravity.CENTER })
            bar.addView(item, LinearLayout.LayoutParams(0, -2, 1f))
        }
        root.addView(bar, LinearLayout.LayoutParams(-1, -2))
        setContentView(root)
    }

    private fun home(c: LinearLayout, th: Theme) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val on = imm.enabledInputMethodList.any { it.packageName == packageName }
        val cur = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)?.startsWith(packageName) == true
        val hero = card()
        hero.addView(LogoView(this, th.accent), LinearLayout.LayoutParams(dp(64), dp(64)))
        hero.addView(tv("typix", 30f, true).apply { setPadding(0, dp(10), 0, dp(2)) })
        val status = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        status.addView(View(this).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(if (on && cur) 0xFF1DB954.toInt() else 0xFFFFA726.toInt()) } },
            LinearLayout.LayoutParams(dp(10), dp(10)).apply { rightMargin = dp(8) })
        status.addView(tv(if (on && cur) "Active · කීබෝඩ් එක සූදානම්" else if (on) "Enabled, not selected yet" else "Not enabled yet", 15f, true).apply { setPadding(0, 0, 0, 0) })
        hero.addView(status)
        hero.addView(tv("Type Sinhala the way you speak it. Write mama and get මම.", 14f, false, soft).apply { setPadding(0, dp(8), 0, 0) })
        c.addView(hero)
        c.addView(head("Setup"))
        val setup = card()
        setup.addView(button(th, (if (on) "✓  " else "") + "Enable Typix keyboard", !on) { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) })
        setup.addView(button(th, (if (cur) "✓  " else "") + "Switch to Typix", on && !cur) { imm.showInputMethodPicker() })
        c.addView(setup)
        c.addView(head("Quick settings"))
        val q = card(); sw(q, th, "hints", "Sinhala key hints", true); sw(q, th, "autocorrect", "Auto-correct Singlish", true); sw(q, th, "sound", "Key sound", false)
        c.addView(q)
        c.addView(head("Try it here"))
        val t = card(); t.addView(EditText(this).apply { hint = "mama yanawaa" }); c.addView(t)
    }

    private fun themes(c: LinearLayout) {
        c.addView(tv("Themes", 28f, true)); c.addView(tv("Pick how your keyboard looks.", 14f, false, soft))
        val sel = Prefs.theme(this).name
        for (t in Prefs.themes) {
            val k = card()
            if (t.name == sel) (k.background as GradientDrawable).setStroke(dp(2), t.accent)
            k.addView(PreviewView(this, t, 54f), LinearLayout.LayoutParams(-1, -2))
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(10), 0, 0) }
            row.addView(tv(t.name, 17f, true).apply { setPadding(0, 0, 0, 0) }, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(tv(if (t.name == sel) "✓ Selected" else "Tap to apply", 13f, false, if (t.name == sel) 0xFF12A37B.toInt() else soft).apply { setPadding(0, 0, 0, 0) })
            k.addView(row)
            k.setOnClickListener { Prefs.setTheme(this, t.name); show(1) }
            c.addView(k)
        }
    }

    private fun settings(c: LinearLayout, th: Theme) {
        c.addView(tv("Settings", 28f, true))
        c.addView(head("Typing"))
        var k = card(); sw(k, th, "hints", "Sinhala key hints", true); sw(k, th, "autocorrect", "Auto-correct Singlish", true); c.addView(k)
        c.addView(head("Feedback"))
        k = card(); sw(k, th, "sound", "Key sound", false); sw(k, th, "vibrate", "Vibrate on key press", true); c.addView(k)
        c.addView(head("Keyboard height"))
        k = card()
        val idx = Prefs.heightIdx(this)
        k.addView(seg(th, Prefs.heights.map { it.first }, idx) { Prefs.setHeightIdx(this, it); show(2) })
        k.addView(PreviewView(this, th, Prefs.heights[idx].second.toFloat()), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        c.addView(k)
        c.addView(head("Clipboard"))
        k = card(); sw(k, th, "clipboard", "Clipboard history", true)
        k.addView(button(th, "Clear pinned clips", false) { Prefs.setPins(this, emptyList()); ClipStore.clearRecent(); Toast.makeText(this, "Cleared", Toast.LENGTH_SHORT).show() })
        c.addView(k)
        c.addView(tv("Typix has no internet permission. Nothing you type leaves this phone.", 13f, false, soft).apply { setPadding(dp(4), dp(4), 0, dp(8)) })
    }
}
