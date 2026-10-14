package app.typix

import android.content.Context

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
        Theme("Slate", 0xFF1E2A32.toInt(), 0xFF2C3B46.toInt(), 0xFF7FD6C2.toInt(), 0xFFEAF0F4.toInt()),
        Theme("Ink", 0xFF0E1224.toInt(), 0xFF2A3262.toInt(), 0xFFC8F135.toInt(), 0xFFEEF1FF.toInt()),
        Theme("Paper", 0xFFE6E9F5.toInt(), 0xFFFFFFFF.toInt(), 0xFF2B3BFF.toInt(), 0xFF1A1F3A.toInt()),
        Theme("Ocean", 0xFF0B2540.toInt(), 0xFF14395F.toInt(), 0xFF4CC9F0.toInt(), 0xFFEEF1FF.toInt()),
        Theme("Ember", 0xFF1C1210.toInt(), 0xFF33201A.toInt(), 0xFFFF7A45.toInt(), 0xFFFFEFE8.toInt()),
        Theme("Mint", 0xFFDDF0E8.toInt(), 0xFFFFFFFF.toInt(), 0xFF12A37B.toInt(), 0xFF10281F.toInt()),
        Theme("Plum", 0xFF1E1030.toInt(), 0xFF35194F.toInt(), 0xFFE879F9.toInt(), 0xFFF6E8FF.toInt())
    )
    private const val SEP = "\u0001"
    val heights = listOf("Short" to 46, "Medium" to 54, "Tall" to 62, "Extra tall" to 70)
    fun appMode(c: Context) = sp(c).getInt("appmode", 0).coerceIn(0, 2)   // 0 auto, 1 light, 2 dark
    fun setAppMode(c: Context, m: Int) = sp(c).edit().putInt("appmode", m).apply()
    fun heightIdx(c: Context) = sp(c).getInt("height", 1).coerceIn(0, heights.size - 1)
    fun setHeightIdx(c: Context, i: Int) = sp(c).edit().putInt("height", i).apply()
    fun rowDp(c: Context) = heights[heightIdx(c)].second
    private fun sp(c: Context) = c.getSharedPreferences("typix", Context.MODE_PRIVATE)
    fun theme(c: Context): Theme { val n = sp(c).getString("theme", "Slate"); return themes.firstOrNull { it.name == n } ?: themes[0] }
    fun setTheme(c: Context, n: String) = sp(c).edit().putString("theme", n).apply()
    fun bool(c: Context, k: String, def: Boolean) = sp(c).getBoolean(k, def)
    fun setBool(c: Context, k: String, v: Boolean) = sp(c).edit().putBoolean(k, v).apply()
    fun pins(c: Context): List<String> = (sp(c).getString("pins", "") ?: "").split(SEP).filter { it.isNotEmpty() }
    fun setPins(c: Context, l: List<String>) = sp(c).edit().putString("pins", l.joinToString(SEP)).apply()
    fun recent(c: Context): List<String> = (sp(c).getString("recent_emoji", "") ?: "").split(' ').filter { it.isNotEmpty() }
    fun addRecent(c: Context, e: String) {
        val l = (listOf(e) + recent(c).filter { it != e }).take(21)
        sp(c).edit().putString("recent_emoji", l.joinToString(" ")).apply()
    }
}
