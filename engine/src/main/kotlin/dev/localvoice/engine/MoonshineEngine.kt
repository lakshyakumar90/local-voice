package dev.localvoice.engine

import ai.moonshine.voice.JNI
import dev.localvoice.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/** Uses the published C/JNI API directly: no library recorder, downloader, or listener executor. */
class MoonshineEngine : LiveAsrEngine {
    private val serial = Mutex()
    private val cancelled = AtomicBoolean(false)
    private var handle = -1
    private var liveStream = -1
    private var liveSamples = 0
    private var onLivePartial: (String) -> Unit = {}
    override suspend fun load(model: InstalledModel) = withContext(Dispatchers.Default) {
        serial.withLock {
            JNI.ensureLibraryLoaded()
            if(handle >= 0) JNI.moonshineFreeTranscriber(handle)
            handle = -1
            handle = JNI.moonshineLoadTranscriberFromFiles(model.directory.absolutePath, JNI.MOONSHINE_MODEL_ARCH_TINY_STREAMING, emptyArray())
            check(handle >= 0) { "The English model could not be loaded. Verify its files or use Whisper." }
            EngineCapabilities(streaming = true)
        }
    }
    override suspend fun transcribe(audio: AudioClip, language: LanguageMode, onPartial: (String) -> Unit): String = withContext(Dispatchers.Default) {
        serial.withLock {
            require(language == LanguageMode.ENGLISH) { "Moonshine Tiny supports English only. Choose Whisper for Hindi." }
            check(handle >= 0)
            cancelled.set(false)
            val stream = JNI.moonshineCreateStream(handle, 0)
            check(stream >= 0) { "Could not create a local recognition stream." }
            try {
                check(JNI.moonshineStartStream(handle, stream) == 0)
                for(offset in audio.pcm.indices step 8000) {
                    check(!cancelled.get()) { "Transcription cancelled." }
                    val chunk = audio.pcm.copyOfRange(offset, minOf(offset+8000, audio.pcm.size))
                    try {
                        check(JNI.moonshineAddAudioToStream(handle, stream, chunk, audio.sampleRate, 0) == 0)
                        JNI.moonshineTranscribeStream(handle, stream, 0)?.text()?.let(onPartial)
                    } finally { chunk.fill(0f) }
                }
                check(!cancelled.get()) { "Transcription cancelled." }
                check(JNI.moonshineStopStream(handle, stream) == 0)
                JNI.moonshineTranscribeStream(handle, stream, JNI.MOONSHINE_FLAG_FORCE_UPDATE)?.text().orEmpty()
            } finally { JNI.moonshineFreeStream(handle, stream) }
        }
    }
    override fun cancel() { cancelled.set(true) }
    override suspend fun beginLive(language: LanguageMode,onPartial: (String) -> Unit) = withContext(Dispatchers.Default) {
        serial.withLock {
            require(language==LanguageMode.ENGLISH);check(handle>=0 && liveStream<0)
            cancelled.set(false);liveSamples=0;onLivePartial=onPartial
            liveStream=JNI.moonshineCreateStream(handle,0)
            check(liveStream>=0)
            check(JNI.moonshineStartStream(handle,liveStream)==0)
        }
    }
    override suspend fun feedLive(samples: FloatArray) = withContext(Dispatchers.Default) {
        serial.withLock {
            check(!cancelled.get() && liveStream>=0) {"Live transcription cancelled."}
            check(JNI.moonshineAddAudioToStream(handle,liveStream,samples,16000,0)==0)
            liveSamples+=samples.size
            if(liveSamples>=8000) {liveSamples=0;JNI.moonshineTranscribeStream(handle,liveStream,0)?.text()?.let(onLivePartial)}
        }
    }
    override suspend fun finishLive(): String = withContext(Dispatchers.Default) {
        serial.withLock {
            check(liveStream>=0)
            val stream=liveStream;liveStream=-1
            try {
                check(!cancelled.get()) {"Transcription cancelled."}
                check(JNI.moonshineStopStream(handle,stream)==0)
                JNI.moonshineTranscribeStream(handle,stream,JNI.MOONSHINE_FLAG_FORCE_UPDATE)?.text().orEmpty()
            } finally {JNI.moonshineFreeStream(handle,stream);onLivePartial={}}
        }
    }
    override suspend fun discardLive() = withContext(Dispatchers.Default) { serial.withLock {
        if(liveStream>=0 && handle>=0) JNI.moonshineFreeStream(handle,liveStream)
        liveStream=-1;onLivePartial={}
    } }
    override suspend fun unload() = withContext(Dispatchers.Default) { serial.withLock {
        if(liveStream>=0 && handle>=0) JNI.moonshineFreeStream(handle,liveStream)
        liveStream=-1;onLivePartial={}
        if(handle >= 0) JNI.moonshineFreeTranscriber(handle); handle = -1
    } }
}
