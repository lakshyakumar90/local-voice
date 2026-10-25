package dev.localvoice.engine

import dev.localvoice.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal object WhisperNative {
    init { System.loadLibrary("localvoice") }
    external fun load(path: String): Long
    external fun transcribe(handle: Long, pcm: FloatArray, language: String, threads: Int): String
    external fun cancel(handle: Long)
    external fun resetCancel(handle: Long)
    external fun free(handle: Long)
    external fun speechRange(path: String, pcm: FloatArray): IntArray?
}

class WhisperEngine : AsrEngine {
    private val serial = Mutex()
    private val lifetime = Any()
    private var handle = 0L
    override suspend fun load(model: InstalledModel) = withContext(Dispatchers.Default) {
        serial.withLock {
            val previous=synchronized(lifetime) {handle.also {handle=0L}}
            if(previous!=0L) WhisperNative.free(previous)
            // Loading is a blocking native call. Never hold the cancellation lock across it.
            val loaded=WhisperNative.load(model.directory.resolve(model.descriptor.files.first().name).absolutePath)
            synchronized(lifetime) {handle=loaded}
            EngineCapabilities(streaming = false)
        }
    }
    override suspend fun transcribe(audio: AudioClip, language: LanguageMode, onPartial: (String) -> Unit): String = withContext(Dispatchers.Default) {
        serial.withLock {
            val current = synchronized(lifetime) { check(handle != 0L); WhisperNative.resetCancel(handle); handle }
            WhisperNative.transcribe(current, audio.pcm, language.whisperCode, Runtime.getRuntime().availableProcessors().coerceIn(1, 4))
        }
    }
    override fun cancel() { synchronized(lifetime) { if(handle != 0L) WhisperNative.cancel(handle) } }
    override suspend fun unload() = withContext(Dispatchers.Default) { serial.withLock {
        val previous=synchronized(lifetime) {handle.also {handle=0L}}
        if(previous!=0L) WhisperNative.free(previous)
    } }
}

object SileroVad {
    /** Keep internal pauses; if VAD is inconclusive, retain the full recording. */
    suspend fun crop(audio: AudioClip, modelPath: String?): AudioClip = withContext(Dispatchers.Default) {
        if(modelPath == null) return@withContext audio
        val range = runCatching { WhisperNative.speechRange(modelPath, audio.pcm) }.getOrNull()
        if(range == null || range[0] < 0 || range[1] <= range[0]) audio
        else AudioClip(audio.pcm.copyOfRange(range[0], range[1]), audio.sampleRate)
    }
}
