package app.typix

import android.content.Context

/** Tiny on-device Sinhala word list (assets/words.txt, "roman|sinhala"), loaded once on a background thread. */
object Dict {
    @Volatile private var entries: List<Pair<String, String>> = emptyList()
    @Volatile private var exactMap: Map<String, String> = emptyMap()

    fun load(c: Context) {
        if (entries.isNotEmpty()) return
        try {
            val list = c.applicationContext.assets.open("words.txt").bufferedReader(Charsets.UTF_8).useLines { seq ->
                seq.mapNotNull { l -> l.split('|').takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toList()
            }
            val m = HashMap<String, String>()
            for ((r, s) in list) m[r] = s
            exactMap = m; entries = list
        } catch (e: Exception) { }
    }

    fun exact(roman: String): String? = if (roman.any { it.isUpperCase() }) null else exactMap[roman]

    fun suggest(buf: String): List<String> {
        val lc = buf.lowercase()
        val out = LinkedHashSet<String>()
        val typed = Transliterator.transliterate(buf)
        val hasCapital = buf.any { it.isUpperCase() }
        // Prefer a known whole-word spelling over a mechanically transliterated near-match.
        if (!hasCapital) exactMap[lc]?.let { out.add(it) }
        out.add(typed)
        if (hasCapital) return out.take(3)   // capitals are deliberate: no word-list guessing
        if (lc.endsWith("a")) out.add(Transliterator.transliterate(buf + "a"))
        for ((r, s) in entries) { if (r.startsWith(lc) && r != lc) { out.add(s); if (out.size >= 4) break } }
        return out.take(3)
    }
}

/** English word list (assets/en_words.txt, most common first) for completions as you type. */
object EnDict {
    @Volatile private var sorted: Array<String> = emptyArray()
    @Volatile private var rankOf: IntArray = IntArray(0)
    @Volatile var ready = false; private set

    fun load(c: Context) {
        if (ready) return
        try {
            val l = c.applicationContext.assets.open("en_words.txt").bufferedReader(Charsets.UTF_8).readLines().filter { it.isNotEmpty() }
            val idx = l.indices.sortedBy { l[it] }
            val s = Array(l.size) { l[idx[it]] }
            val r = IntArray(l.size) { idx[it] }
            sorted = s; rankOf = r; ready = true
        } catch (e: Exception) { }
    }

    fun has(w: String): Boolean {
        if (!ready) return false
        val p = w.lowercase()
        return java.util.Arrays.binarySearch(sorted, p) >= 0
    }

    /** Up to [n] completions of [prefix], most common first, never the prefix itself. Keeps the typed capitalisation. */
    fun suggest(prefix: String, n: Int = 3): List<String> {
        val s = sorted; val r = rankOf
        if (!ready || prefix.isEmpty()) return emptyList()
        val p = prefix.lowercase()
        var lo = 0; var hi = s.size
        while (lo < hi) { val m = (lo + hi) ushr 1; if (s[m] < p) lo = m + 1 else hi = m }
        val best = IntArray(n) { -1 }            // sorted-array indices of the n best ranks so far
        var i = lo
        while (i < s.size && s[i].startsWith(p)) {
            if (s[i].length > p.length) {
                var j = n - 1
                if (best[j] < 0 || r[i] < r[best[j]]) {
                    best[j] = i
                    while (j > 0 && (best[j - 1] < 0 || r[best[j]] < r[best[j - 1]])) { val t = best[j]; best[j] = best[j - 1]; best[j - 1] = t; j-- }
                }
            }
            i++
        }
        val out = ArrayList<String>(n)
        val allCaps = prefix.length > 1 && prefix.all { it.isUpperCase() }
        val cap = prefix[0].isUpperCase()
        for (b in best) if (b >= 0) out.add(if (allCaps) s[b].uppercase() else if (cap) s[b].replaceFirstChar { it.uppercaseChar() } else s[b])
        return out
    }
}
