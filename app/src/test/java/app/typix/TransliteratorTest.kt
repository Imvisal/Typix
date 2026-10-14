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
    }
}
