package dev.localvoice.core

import org.junit.Assert.*
import org.junit.Test

class DictationOutputTest {
    @Test fun selectedModeRoutesToExplicitRecognitionLanguage() {
        assertEquals("hi",LanguageMode.MIXED.whisperCode)
        assertEquals("hi",LanguageMode.HINDI.whisperCode)
        assertEquals("en",LanguageMode.ENGLISH.whisperCode)
    }
    @Test fun englishUtterancesArePreservedInEnglishAndMixedModes() {
        val text="Can you send the GitHub repo?"
        assertEquals(text,DictationOutput.finish(text,LanguageMode.ENGLISH))
        assertEquals(text,DictationOutput.finish(text,LanguageMode.MIXED))
    }
    @Test fun hindiScriptModeDoesNotDeliverEnglishOnlyModelOutput() {
        val failure=runCatching {DictationOutput.finish("Do you know me?",LanguageMode.HINDI)}.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure!!.message!!.contains("Nothing was inserted"))
        assertEquals("क्या तुम मुझे जानते हो",DictationOutput.finish("क्या तुम मुझे जानते हो",LanguageMode.HINDI))
        assertEquals("kya tum mujhe jaante ho",DictationOutput.finish("क्या तुम मुझे जानते हो",LanguageMode.MIXED))
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
