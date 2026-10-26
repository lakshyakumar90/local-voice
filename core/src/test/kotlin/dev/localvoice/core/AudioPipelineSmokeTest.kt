package dev.localvoice.core

import java.io.File
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Captured real desktop ASR output; -PaudioSmokeDir can substitute a fresh inference run. */
class AudioPipelineSmokeTest {
    private fun directory(): File {
        val path=System.getProperty("localvoice.audioSmokeDir")
        return if(path!=null) File(path) else File(requireNotNull(javaClass.classLoader!!.getResource("audio-smoke")).toURI())
    }
    private fun output(dir: File,name: String,language: LanguageMode): String {
        val raw=dir.resolve("$name.raw.txt").readText()
        val text=DictationOutput.finish(raw,language,FormattingMode.SMART)
        dir.resolve("$name.${language.name.lowercase()}.formatted.txt").writeText(text)
        return text
    }
    @Test fun englishPublicSpeechAndLongAudioFlowThroughFinalFormatting() {
        val dir=directory()
        assertTrue(output(dir,"jfk",LanguageMode.ENGLISH).contains("country",ignoreCase=true))
        val long=output(dir,"english-long",LanguageMode.ENGLISH)
        assertTrue(long.contains("\n\n"))
        assertTrue(long.length>dir.resolve("jfk.raw.txt").readText().length*10)
        assertEquals(dir.resolve("english-long.raw.txt").readText().replace(Regex("\\s+")," ").trim(),long.replace(Regex("\\s+")," ").trim())
    }
    @Test fun hindiPublicSpeechKeepsItsLanguageInBothOutputModes() {
        val dir=directory()
        repeat(2) {index ->
            val hindi=output(dir,"hindi-$index",LanguageMode.HINDI)
            assertTrue(hindi.any {it in '\u0900'..'\u097f'})
            val roman=output(dir,"hindi-$index",LanguageMode.MIXED)
            assertFalse(roman.any {it in '\u0900'..'\u097f'})
            assertFalse(roman.any {it in '\u0600'..'\u06ff'})
            assertTrue(roman.length>40)
        }
    }
    @Test fun actualUnreliableBaseHypothesesAreRejected() {
        val dir=directory()
        for(name in listOf("base-prompt-loop","base-mixed-alphabet","base-roman-only")) {
            val failure=runCatching {DictationOutput.finish(dir.resolve("$name.raw.txt").readText(),LanguageMode.HINDI,FormattingMode.SMART)}.exceptionOrNull()
            assertTrue("$name should not be inserted as Hindi script",failure is IllegalArgumentException)
            assertTrue(failure!!.message!!.contains("Nothing was inserted"))
        }
    }
    @Test fun controlledSpeechProducesListAndParagraph() {
        val dir=directory()
        assumeTrue("Optional locally synthesized formatting utterance",dir.resolve("formatting.raw.txt").isFile)
        val text=output(dir,"formatting",LanguageMode.ENGLISH)
        assertTrue(text.startsWith("1. "))
        assertTrue(text.contains("\n2. "))
        assertTrue(text.contains("\n3. "))
        assertTrue(text.contains("\n\n"))
        assertFalse(text.contains("new paragraph",ignoreCase=true))
    }
}
