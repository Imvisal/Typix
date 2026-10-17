package app.typix
import org.junit.Assert.assertEquals
import org.junit.Test

class TransliteratorTest {
    @Test fun basic() {
        assertEquals("මම", Transliterator.transliterate("mama"))
        assertEquals("යනවා", Transliterator.transliterate("yanawaa"))
        assertEquals("අම්ම", Transliterator.transliterate("amma"))
        assertEquals("ලන්ක", Transliterator.transliterate("lanka"))
        assertEquals("කෙම", Transliterator.transliterate("kema"))
        assertEquals("අයුබොවන්", Transliterator.transliterate("ayubowan"))
        assertEquals("ණම", Transliterator.transliterate("Nama"))
        assertEquals("ඇ", Transliterator.transliterate("A"))
        assertEquals("ඇම්ම", Transliterator.transliterate("Amma"))
        assertEquals("කැම", Transliterator.transliterate("kAma"))
        assertEquals("ඇ", Transliterator.transliterate("ae"))
        assertEquals("ද", Transliterator.transliterate("qa"))
        assertEquals("ද්", Transliterator.transliterate("q"))
        assertEquals("ධ", Transliterator.transliterate("Qa"))
        assertEquals("නෑ", Transliterator.transliterate("nAa"))
        assertEquals("බෑ", Transliterator.transliterate("bAa"))
        assertEquals("ඈ", Transliterator.transliterate("Aa"))
        assertEquals("කෑම", Transliterator.transliterate("kAama"))
    }
    @Test fun anusvara() { assertEquals("සං", Transliterator.transliterate("sax")); assertEquals("ං", Transliterator.transliterate("x")) }
}
