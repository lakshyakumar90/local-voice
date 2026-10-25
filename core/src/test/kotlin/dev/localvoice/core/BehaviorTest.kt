package dev.localvoice.core

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class BehaviorTest {
    @Test fun insertionPreservesPrefixSuffixAndSelection() {
        assertEquals("Hello brave world",TextInsertion.replacement("Hello world",6,6,"brave"))
        assertEquals("Hello new",TextInsertion.replacement("Hello world",6,11,"new"))
        assertEquals("new world",TextInsertion.replacement("old world",0,3,"new"))
    }
    @Test fun unknownSelectionNeverOverwritesText() {
        assertNull(TextInsertion.replacement("private",-1,-1,"hello"))
        assertNull(TextInsertion.replacement("private",4,2,"hello"))
        assertNull(TextInsertion.replacement("private",0,99,"hello"))
        assertNull(TextInsertion.replacement("private",0,0,"   "))
    }
    @Test fun emojiSurrogatesAreNotSplit() {
        assertNull(TextInsertion.replacement("a😀b",2,2,"test"))
        assertEquals("a😀!b",TextInsertion.replacement("a😀b",3,3,"!"))
    }
    @Test fun hindiTextIsNotTranslatedOrStripped() {
        val text="कल 3 बजे project update भेज देना।"
        assertEquals(text,TranscriptText.normalize("\u0000$text\n"))
        assertEquals("क्या होगा।",TextInsertion.replacement("क्या।",4,4,"होगा"))
    }
    @Test fun runtimeLanguageCapabilitiesAreEnforced() {
        val english=ModelDescriptor("m","M","",EngineId.MOONSHINE,emptyList(),true)
        assertTrue(english.supports(LanguageMode.ENGLISH));assertFalse(english.supports(LanguageMode.HINDI));assertFalse(english.supports(LanguageMode.MIXED))
    }
    @Test fun rangeResumeCannotAppendWrongBytes() {
        assertTrue(DownloadResume.mayAppend(10,206,"bytes 10-19/20"))
        assertFalse(DownloadResume.mayAppend(10,200,null))
        assertFalse(DownloadResume.mayAppend(10,206,"bytes 0-19/20"))
        assertFalse(DownloadResume.mayAppend(10,416,"bytes */20"))
    }
    @Test fun antiAliasingRejectsOutOfBandAudioAndPreservesSpeech() {
        fun signal(hz: Int)=ShortArray(48_000) {(sin(2*PI*hz*it/48_000)*16_000).toInt().toShort()}
        fun rms(audio: FloatArray)=sqrt(audio.drop(100).dropLast(100).sumOf {it.toDouble()*it}/(audio.size-200))
        val speech=PcmResampler.resample(signal(1000),48_000,48_000)
        val aboveNyquist=PcmResampler.resample(signal(12000),48_000,48_000)
        assertEquals(16_000,speech.size)
        assertTrue(rms(speech)>.3)
        assertTrue(rms(aboveNyquist)<.02)
    }
    @Test fun signedPcmConversionDoesNotClipOrAmplifySilence() {
        assertArrayEquals(floatArrayOf(-1f,0f,32767/32768f),PcmResampler.resample(shortArrayOf(-32768,0,32767),3,16000),0.00001f)
    }
}
