package app.typix

/** Rule-based Singlish -> Sinhala. Pure Kotlin, no Android deps, easy to unit test. */
object Transliterator {
    private const val HAL = "්"
    // key, vowel sign (after consonant), independent letter. Longest first.
    private val vowels = listOf(
        Triple("aae", "ෑ", "ඈ"), Triple("aa", "ා", "ආ"), Triple("ae", "ැ", "ඇ"),
        Triple("ai", "ෛ", "ඓ"), Triple("au", "ෞ", "ඖ"), Triple("ii", "ී", "ඊ"),
        Triple("ee", "ේ", "ඒ"), Triple("oo", "ෝ", "ඕ"), Triple("uu", "ූ", "ඌ"),
        Triple("a", "", "අ"), Triple("i", "ි", "ඉ"), Triple("u", "ු", "උ"),
        Triple("e", "ෙ", "එ"), Triple("o", "ො", "ඔ")
    )
    private val consonants = mapOf(
        "kh" to "ඛ", "gh" to "ඝ", "ch" to "ච", "th" to "ත", "dh" to "ද", "sh" to "ශ",
        "k" to "ක", "g" to "ග", "c" to "ච", "j" to "ජ", "t" to "ට", "d" to "ඩ", "n" to "න",
        "p" to "ප", "b" to "බ", "m" to "ම", "y" to "ය", "r" to "ර", "l" to "ල", "w" to "ව",
        "v" to "ව", "s" to "ස", "h" to "හ", "f" to "ෆ"
    )

    private fun vowelAt(s: String, i: Int) = vowels.firstOrNull { s.startsWith(it.first, i) }
    private fun consonantAt(s: String, i: Int): Pair<String, String>? {
        for (len in 2 downTo 1) {
            if (i + len > s.length) continue
            val k = s.substring(i, i + len)
            consonants[k]?.let { return k to it }
        }
        return null
    }

    fun transliterate(input: String): String {
        val s = input.lowercase()
        val out = StringBuilder()
        var i = 0
        while (i < s.length) {
            val v = vowelAt(s, i)
            if (v != null) { out.append(v.third); i += v.first.length; continue }
            val c = consonantAt(s, i)
            if (c == null) { out.append(input[i]); i++; continue }
            i += c.first.length
            val v2 = vowelAt(s, i)
            if (v2 != null) { out.append(c.second).append(v2.second); i += v2.first.length }
            else out.append(c.second).append(HAL)
        }
        return out.toString()
    }
}
