package app.typix

import android.content.Context

/** Tiny on-device word list (assets/words.txt, "roman|sinhala"). ~100 lines, loaded once. */
object Dict {
    private var entries: List<Pair<String, String>> = emptyList()
    private val exact = HashMap<String, String>()

    fun load(c: Context) {
        if (entries.isNotEmpty()) return
        try {
            entries = c.assets.open("words.txt").bufferedReader(Charsets.UTF_8).useLines { seq ->
                seq.mapNotNull { l -> l.split('|').takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toList()
            }
            for ((r, s) in entries) exact[r] = s
        } catch (e: Exception) { }
    }

    fun exact(roman: String): String? = exact[roman.lowercase()]

    fun suggest(buf: String): List<String> {
        val lc = buf.lowercase()
        val out = LinkedHashSet<String>()
        out.add(Transliterator.transliterate(buf))
        exact[lc]?.let { out.add(it) }
        if (lc.endsWith("a")) out.add(Transliterator.transliterate(buf + "a"))
        for ((r, s) in entries) { if (r.startsWith(lc) && r != lc) { out.add(s); if (out.size >= 4) break } }
        return out.take(3)
    }
}
