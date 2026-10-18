package app.typix

/** Rule-based Singlish -> Sinhala. Lowercase = normal, Capital (A K N L S T D B G C J) = special letters (A = ඇ, N = ණ ...). */
object Transliterator {
    private const val HAL = "්"
    private const val ZWJ = "\u200D"
    private val vowels = listOf(
        Triple("aae", "ෑ", "ඈ"), Triple("aa", "ා", "ආ"), Triple("ae", "ැ", "ඇ"),
        Triple("ai", "ෛ", "ඓ"), Triple("au", "ෞ", "ඖ"), Triple("ii", "ී", "ඊ"),
        Triple("ee", "ේ", "ඒ"), Triple("oo", "ෝ", "ඕ"), Triple("uu", "ූ", "ඌ"),
        Triple("a", "", "අ"), Triple("i", "ි", "ඉ"), Triple("u", "ු", "උ"),
        Triple("e", "ෙ", "එ"), Triple("o", "ො", "ඔ")
    )
    private val consonants = mapOf(
        "kh" to "ඛ", "gh" to "ඝ", "ch" to "ච", "th" to "ත", "dh" to "ද", "sh" to "ශ",
        "k" to "ක", "q" to "ද", "g" to "ග", "c" to "ච", "j" to "ජ", "t" to "ට", "d" to "ඩ", "n" to "න",
        "p" to "ප", "b" to "බ", "m" to "ම", "y" to "ය", "r" to "ර", "l" to "ල", "w" to "ව",
        "v" to "ව", "s" to "ස", "h" to "හ", "f" to "ෆ",
        "K" to "ඛ", "Q" to "ධ", "N" to "ණ", "L" to "ළ", "S" to "ෂ", "T" to "ඨ", "D" to "ඪ", "B" to "භ", "G" to "ඝ", "C" to "ඡ", "J" to "ඣ"
    )

    private fun vowelAt(lc: String, i: Int) = vowels.firstOrNull { lc.startsWith(it.first, i) }
    private fun consonantAt(raw: String, lc: String, i: Int): Pair<Int, String>? {
        for (len in 2 downTo 1) {
            if (i + len > lc.length) continue
            val r = raw.substring(i, i + len)
            val k = if (consonants.containsKey(r)) r else lc.substring(i, i + len)
            consonants[k]?.let { return len to it }
        }
        return null
    }

    fun transliterate(raw: String): String {
        val input = raw.replace("Aa", "aae").replace("A", "ae")   // capital A = ඇ (shift + a); A + a = ඈ / ෑ  (nAa = නෑ, bAa = බෑ)
        val lc = input.lowercase()
        val out = StringBuilder()
        var i = 0
        while (i < lc.length) {
            if (lc[i] == 'x') { out.append("ං"); i++; continue }   // x = anusvara
            val v = vowelAt(lc, i)
            if (v != null) { out.append(v.third); i += v.first.length; continue }
            val c = consonantAt(input, lc, i)
            if (c == null) { out.append(input[i]); i++; continue }
            i += c.first
            val v2 = vowelAt(lc, i)
            if (v2 != null) { out.append(c.second).append(v2.second); i += v2.first.length }
            else {
                // consonant + r / y + vowel = rakaransaya / yansaya:  prashna = ප්‍රශ්න,  vidhyaa = විද්‍යා
                val nx = if (i < lc.length) lc[i] else ' '
                val vv = if (nx == 'r' || nx == 'y') vowelAt(lc, i + 1) else null
                if (vv != null) { out.append(c.second).append(HAL).append(ZWJ).append(if (nx == 'r') "ර" else "ය").append(vv.second); i += 1 + vv.first.length }
                else out.append(c.second).append(HAL)
            }
        }
        return out.toString()
    }
}
