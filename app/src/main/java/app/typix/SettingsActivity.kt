package app.typix

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*

/** Home / Themes / Settings, built in code (no layouts, no libraries). */
class SettingsActivity : Activity() {
    private var tab = 0
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    override fun onResume() { super.onResume(); show(tab) }

    private fun tv(t: String, size: Float, bold: Boolean = false, color: Int = 0xFF1A1F3A.toInt()) =
        TextView(this).apply { text = t; textSize = size; setTextColor(color); setPadding(0, 0, 0, dp(12)); if (bold) setTypeface(typeface, Typeface.BOLD) }

    private fun btn(t: String, th: Theme, onClick: () -> Unit) = Button(this).apply {
        text = t; isAllCaps = false; setTextColor(0xFF0E1224.toInt())
        background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(th.accent) }
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(-1, dp(52)).apply { bottomMargin = dp(10) }
    }

    private fun sw(c: LinearLayout, k: String, label: String, def: Boolean) = c.addView(Switch(this).apply {
        text = label; textSize = 17f; isChecked = Prefs.bool(this@SettingsActivity, k, def); setPadding(0, dp(12), 0, dp(12))
        setOnCheckedChangeListener { _, v -> Prefs.setBool(this@SettingsActivity, k, v) }
    })

    private fun show(t: Int) {
        tab = t; val th = Prefs.theme(this)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(0xFFF2F4FB.toInt()) }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(44), dp(20), dp(20)) }
        when (t) { 0 -> home(col, th); 1 -> themes(col); else -> settings(col, th) }
        root.addView(ScrollView(this).apply { addView(col) }, LinearLayout.LayoutParams(-1, 0, 1f))
        val bar = LinearLayout(this).apply { setBackgroundColor(0xFF0E1224.toInt()) }
        listOf("Home", "Themes", "Settings").forEachIndexed { i, n ->
            bar.addView(TextView(this).apply {
                text = n; gravity = Gravity.CENTER; textSize = 15f; setPadding(0, dp(16), 0, dp(16))
                setTextColor(if (i == t) th.accent else 0xFF8A93C0.toInt()); setOnClickListener { show(i) }
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        root.addView(bar, LinearLayout.LayoutParams(-1, -2))
        setContentView(root)
    }

    private fun home(c: LinearLayout, th: Theme) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val on = imm.enabledInputMethodList.any { it.packageName == packageName }
        val cur = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)?.startsWith(packageName) == true
        c.addView(LogoView(this, th.accent), LinearLayout.LayoutParams(dp(72), dp(72)).apply { bottomMargin = dp(12) })
        c.addView(tv("typix", 32f, true))
        c.addView(tv(if (on && cur) "Active · කීබෝඩ් එක සූදානම්" else if (on) "Enabled, but not selected yet" else "Not enabled yet", 17f, true))
        c.addView(tv("Type Sinhala the way you speak it. Write mama and get මම.", 14f))
        c.addView(btn("1. Enable Typix keyboard", th) { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) })
        c.addView(btn("2. Switch to Typix", th) { imm.showInputMethodPicker() })
        sw(c, "hints", "Sinhala key hints", true); sw(c, "autocorrect", "Auto-correct Singlish", true); sw(c, "sound", "Key sound", false)
        c.addView(tv("Try it here", 14f, true).apply { setPadding(0, dp(16), 0, dp(4)) })
        c.addView(EditText(this).apply { hint = "mama yanawaa" })
    }

    private fun themes(c: LinearLayout) {
        c.addView(tv("Themes", 28f, true)); c.addView(tv("Pick how your keyboard looks.", 14f))
        val sel = Prefs.theme(this).name
        for (t in Prefs.themes) {
            val row = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL; setPadding(dp(14), dp(10), dp(14), dp(10))
                background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(0xFFFFFFFF.toInt()); if (t.name == sel) setStroke(dp(2), t.accent) }
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }
                setOnClickListener { Prefs.setTheme(this@SettingsActivity, t.name); show(1) }
            }
            row.addView(View(this).apply { background = GradientDrawable().apply { cornerRadius = dp(10).toFloat(); setColor(t.bg); setStroke(dp(3), t.accent) } },
                LinearLayout.LayoutParams(dp(48), dp(48)).apply { rightMargin = dp(14) })
            row.addView(tv(t.name, 17f, true).apply { setPadding(0, 0, 0, 0) }, LinearLayout.LayoutParams(0, -2, 1f))
            if (t.name == sel) row.addView(tv("✓", 20f, true, t.accent))
            c.addView(row)
        }
    }

    private fun settings(c: LinearLayout, th: Theme) {
        c.addView(tv("Settings", 28f, true))
        sw(c, "hints", "Sinhala key hints", true); sw(c, "autocorrect", "Auto-correct Singlish", true)
        sw(c, "sound", "Key sound", false); sw(c, "vibrate", "Vibrate on key press", true); sw(c, "clipboard", "Clipboard history", true)
        c.addView(btn("Clear pinned clips", th) { Prefs.setPins(this, emptyList()); ClipStore.clearRecent(); Toast.makeText(this, "Cleared", Toast.LENGTH_SHORT).show() }.apply { (layoutParams as LinearLayout.LayoutParams).topMargin = dp(16) })
        c.addView(tv("Changes apply the next time the keyboard opens. Typix has no internet permission, nothing you type leaves this phone.", 13f, false, 0xFF5B6390.toInt()).apply { setPadding(0, dp(12), 0, 0) })
    }
}
