package dev.localvoice.core

object RecordingLimits {
    const val MAX_SECONDS=600
    const val MAX_DURATION_MS=MAX_SECONDS*1000L
    const val MAX_PROCESSING_MS=40*60*1000L
    const val TARGET_LIFETIME_MS=MAX_DURATION_MS+MAX_PROCESSING_MS+60_000L
    fun processingTimeoutMs(durationMs: Long)=maxOf(60_000L,durationMs*4).coerceAtMost(MAX_PROCESSING_MS)
}
