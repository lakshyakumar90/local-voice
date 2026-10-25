package dev.localvoice.core

import org.junit.Assert.*
import org.junit.Test

class DictationOutputTest {
    @Test fun mixedModeCannotAutoDetectUrduOrEnglish() {
        assertEquals("hi",LanguageMode.MIXED.whisperCode)
        assertEquals("hi",LanguageMode.HINDI.whisperCode)
        assertEquals("en",LanguageMode.ENGLISH.whisperCode)
    }
    @Test fun finalDeliveryRomanizesHindiButLeavesEnglishIntact() {
        assertEquals("meeting kal hai",DictationOutput.finish("meeting कल है",LanguageMode.MIXED))
        assertEquals("meeting कल है",DictationOutput.finish("meeting कल है",LanguageMode.HINDI))
    }
    @Test fun urduOutputIsRejectedAsAWholeRatherThanPartiallyInserted() {
        val failure=runCatching {DictationOutput.finish("meeting کل ہے",LanguageMode.MIXED)}.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure!!.message!!.contains("Nothing was inserted"))
    }
}
