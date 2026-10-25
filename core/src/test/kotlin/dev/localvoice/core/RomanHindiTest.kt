package dev.localvoice.core

import org.junit.Assert.assertEquals
import org.junit.Test

class RomanHindiTest {
    @Test fun userReportedPhraseUsesMedialSchwaDeletion() {
        assertEquals("kya tum mujhe jaante ho",RomanHindi.convert("क्या तुम मुझे जानते हो"))
        assertEquals("jaante",RomanHindi.convert("जानते"))
    }
    @Test fun mixedSpeechIsRomanizedWithoutTranslation() {
        assertEquals("Meet tomorrow, mujhe kal jaana hai.",RomanHindi.convert("Meet tomorrow, मुझे कल जाना है।"))
    }
    @Test fun englishUrlsCaseAndEmojiRemainUntouched() {
        val text="Email MyURL https://example.com/A?x=1 at 5 PM 🙂"
        assertEquals(text,RomanHindi.convert(text))
    }
    @Test fun matrasNuktaConjunctsAndNumbersAreHandled() {
        assertEquals("Hindi mein qalam 123",RomanHindi.convert("हिंदी में क़लम १२३"))
        assertEquals("namaste",RomanHindi.convert("नमस्ते"))
    }
}
