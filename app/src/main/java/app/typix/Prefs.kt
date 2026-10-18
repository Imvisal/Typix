package app.typix

import android.content.Context
import android.graphics.Typeface

/** Blend colour [a] towards [b] by [t] (0..1); result is opaque. */
fun mixColor(a: Int, b: Int, t: Float): Int {
    fun ch(s: Int) = (((a shr s) and 255) * (1 - t) + ((b shr s) and 255) * t).toInt()
    return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
}

/** Perceived brightness 0..255. */
fun lumaOf(c: Int): Double = ((c shr 16) and 255) * 0.299 + ((c shr 8) and 255) * 0.587 + (c and 255) * 0.114

/** Outfit (bundled in assets/fonts) with a safe fallback to the system font. Sinhala glyphs fall back to the system Sinhala font. */
object Fonts {
    private val cache = HashMap<String, Typeface>()
    fun get(c: Context, weight: Int = 400): Typeface {
        val n = when { weight >= 700 -> "Bold"; weight >= 600 -> "SemiBold"; weight >= 500 -> "Medium"; else -> "Regular" }
        return cache.getOrPut(n) {
            try { Typeface.createFromAsset(c.applicationContext.assets, "fonts/Outfit-$n.ttf") } catch (e: Exception) { Typeface.DEFAULT }
        }
    }
}

class Theme(val name: String, val bg: Int, val key: Int, val accent: Int, val text: Int) {
    /** Text colour that stays readable on top of the accent colour. */
    val onAccent: Int get() {
        val r = (accent shr 16) and 255; val g = (accent shr 8) and 255; val b = accent and 255
        return if (r * 0.299 + g * 0.587 + b * 0.114 > 140) 0xFF0E1224.toInt() else 0xFFFFFFFF.toInt()
    }
}

/** App-private SharedPreferences (nothing leaves the phone). */
object Prefs {
    val themes = listOf(
        Theme("Ink", 0xFF0E1224.toInt(), 0xFF1B2140.toInt(), 0xFFC8F135.toInt(), 0xFFEEF1FF.toInt()),
        Theme("Paper", 0xFFE6E9F5.toInt(), 0xFFFFFFFF.toInt(), 0xFF2B3BFF.toInt(), 0xFF1A1F3A.toInt()),
        Theme("Ocean", 0xFF0B2540.toInt(), 0xFF14395F.toInt(), 0xFF4CC9F0.toInt(), 0xFFEEF1FF.toInt()),
        Theme("Ember", 0xFF1C1210.toInt(), 0xFF33201A.toInt(), 0xFFFF7A45.toInt(), 0xFFFFEFE8.toInt()),
        Theme("Mint", 0xFFDDF0E8.toInt(), 0xFFFFFFFF.toInt(), 0xFF12A37B.toInt(), 0xFF10281F.toInt()),
        Theme("Plum", 0xFF1E1030.toInt(), 0xFF35194F.toInt(), 0xFFE879F9.toInt(), 0xFFF6E8FF.toInt())
    )
    private const val SEP = "\u0001"
    val heights = listOf("Short" to 50, "Medium" to 58, "Tall" to 66, "Extra tall" to 74)
    fun themeByName(n: String?): Theme = themes.firstOrNull { it.name == n } ?: themes[0]
    fun appMode(c: Context) = sp(c).getInt("appmode", 0).coerceIn(0, 2)   // 0 auto, 1 light, 2 dark
    fun setAppMode(c: Context, m: Int) = sp(c).edit().putInt("appmode", m).apply()
    fun heightIdx(c: Context) = sp(c).getInt("height", 1).coerceIn(0, heights.size - 1)
    fun setHeightIdx(c: Context, i: Int) = sp(c).edit().putInt("height", i).apply()
    fun rowDp(c: Context): Int = (heights[heightIdx(c)].second * ov(c, theme(c).name, "hgt", 100).coerceIn(70, 160) / 100f).toInt().coerceIn(36, 96)
    private fun sp(c: Context) = c.getSharedPreferences("typix", Context.MODE_PRIVATE)
    fun theme(c: Context): Theme { val n = sp(c).getString("theme", "Ink"); return themes.firstOrNull { it.name == n } ?: themes[0] }
    fun setTheme(c: Context, n: String) = sp(c).edit().putString("theme", n).apply()
    // per-theme keyboard customising: field = key (key colour), text (letter colour), num (number colour), size (percent)
    fun ov(c: Context, theme: String, f: String, def: Int = 0) = sp(c).getInt("ov.$theme.$f", def)
    fun setOv(c: Context, theme: String, f: String, v: Int) = sp(c).edit().putInt("ov.$theme.$f", v).apply()
    fun clearOv(c: Context, theme: String) = sp(c).edit().apply { for (f in listOf("key", "text", "num", "size")) remove("ov.$theme.$f") }.apply()
    fun int(c: Context, k: String, def: Int) = sp(c).getInt(k, def)
    fun setInt(c: Context, k: String, v: Int) = sp(c).edit().putInt(k, v).apply()
    fun bool(c: Context, k: String, def: Boolean) = sp(c).getBoolean(k, def)
    fun setBool(c: Context, k: String, v: Boolean) = sp(c).edit().putBoolean(k, v).apply()
    // Pinned clips can hold private text, so they live in their own file that is never backed up or exported.
    fun pv(c: Context) = c.getSharedPreferences("typix_private", Context.MODE_PRIVATE)
    fun pins(c: Context): List<String> {
        val old = sp(c).getString("pins", null)
        if (old != null) { pv(c).edit().putString("pins", old).apply(); sp(c).edit().remove("pins").apply() }   // one-time move
        return (pv(c).getString("pins", "") ?: "").split(SEP).filter { it.isNotEmpty() }
    }
    fun setPins(c: Context, l: List<String>) = pv(c).edit().putString("pins", l.joinToString(SEP)).apply()

    /** All keyboard settings (theme, customizing, sounds, ...) as JSON text, for "Save settings to a file". */
    fun exportJson(c: Context): String {
        val o = org.json.JSONObject()
        for ((k, v) in sp(c).all) if (k != "pins" && (v is Int || v is Boolean || v is String)) o.put(k, v)
        return org.json.JSONObject().put("typix", 1).put("settings", o).toString(2)
    }
    /** Restore settings from [exportJson] text. Returns how many were restored (0 = not a Typix file). */
    fun importJson(c: Context, text: String): Int {
        val o = try { org.json.JSONObject(text).optJSONObject("settings") } catch (e: Exception) { null } ?: return 0
        val ed = sp(c).edit(); var n = 0
        val it = o.keys()
        while (it.hasNext()) {
            val k = it.next(); if (k == "pins" || k.length > 64) continue
            when (val v = o.get(k)) {
                is Boolean -> { ed.putBoolean(k, v); n++ }
                is Int -> { ed.putInt(k, v); n++ }
                is String -> if (v.length < 5000) { ed.putString(k, v); n++ }
            }
        }
        ed.apply(); return n
    }
    fun recent(c: Context): List<String> = (sp(c).getString("recent_emoji", "") ?: "").split(' ').filter { it.isNotEmpty() }
    fun addRecent(c: Context, e: String) {
        val l = (listOf(e) + recent(c).filter { it != e }).take(30)
        sp(c).edit().putString("recent_emoji", l.joinToString(" ")).apply()
    }
}
