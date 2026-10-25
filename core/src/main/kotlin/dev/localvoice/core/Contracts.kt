package dev.localvoice.core

import java.io.File

enum class LanguageMode(val label: String, val whisperCode: String) {
    ENGLISH("English", "en"), HINDI("Hindi script", "hi"), MIXED("English + Roman Hindi", "hi")
}

enum class EngineId { WHISPER, MOONSHINE }
data class ModelFile(val name: String, val url: String, val bytes: Long, val sha256: String)
data class ModelDescriptor(
    val id: String, val name: String, val description: String, val engine: EngineId,
    val files: List<ModelFile>, val englishOnly: Boolean = false,
    val recommendedRamGb: Int = 4, val weightLicense: String = "MIT",
    val sourceUrl: String = "https://github.com/openai/whisper",
) {
    val bytes: Long get() = files.sumOf { it.bytes }
    fun supports(language: LanguageMode) = !englishOnly || language == LanguageMode.ENGLISH
}

data class InstalledModel(val descriptor: ModelDescriptor, val directory: File)
data class AudioClip(val pcm: FloatArray, val sampleRate: Int = 16_000) {
    val durationMs: Long get() = pcm.size * 1000L / sampleRate
}
data class EngineCapabilities(val streaming: Boolean, val sampleRate: Int = 16_000)
interface AsrEngine {
    suspend fun load(model: InstalledModel): EngineCapabilities
    suspend fun transcribe(audio: AudioClip, language: LanguageMode, onPartial: (String) -> Unit = {}): String
    fun cancel()
    suspend fun unload()
}
interface LiveAsrEngine : AsrEngine {
    suspend fun beginLive(language: LanguageMode,onPartial: (String) -> Unit)
    suspend fun feedLive(samples: FloatArray)
    suspend fun finishLive(): String
    suspend fun discardLive()
}

enum class SessionPhase { IDLE, PREPARING, RECORDING, PROCESSING, RESULT, ERROR }
data class SessionState(
    val phase: SessionPhase = SessionPhase.IDLE,
    val elapsedMs: Long = 0, val level: Float = 0f,
    val transcript: String = "", val partial: String = "", val message: String = "",
    val savedId: String? = null,
)

/** UTF-16 indices must not split a surrogate pair. Never infer an unknown caret. */
object TextInsertion {
    fun replacement(current: String, start: Int, end: Int, dictated: String): String? {
        if (start !in 0..current.length || end !in start..current.length || dictated.isBlank()) return null
        fun splits(index: Int) = index > 0 && index < current.length && current[index - 1].isHighSurrogate() && current[index].isLowSurrogate()
        if (splits(start) || splits(end)) return null
        val prefix = current.substring(0, start)
        val suffix = current.substring(end)
        val text = dictated.trim()
        fun word(c: Char?) = c != null && (c.isLetterOrDigit() || Character.getType(c) in listOf(Character.NON_SPACING_MARK.toInt(),Character.COMBINING_SPACING_MARK.toInt(),Character.ENCLOSING_MARK.toInt()))
        val before = if (word(prefix.lastOrNull()) && word(text.firstOrNull())) " " else ""
        val after = if (word(suffix.firstOrNull()) && word(text.lastOrNull())) " " else ""
        return prefix + before + text + after + suffix
    }
}

object TranscriptText {
    fun normalize(text: String) = text.filter { !it.isISOControl() || it == '\n' || it == '\t' }.trim()
}

object DownloadResume {
    fun mayAppend(offset: Long, status: Int, range: String?): Boolean = offset > 0 && status == 206 &&
        range?.substringBefore('/')?.substringAfter("bytes ")?.substringBefore('-')?.toLongOrNull() == offset
}
