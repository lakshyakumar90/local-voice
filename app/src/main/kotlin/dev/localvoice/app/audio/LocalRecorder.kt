package dev.localvoice.app.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import dev.localvoice.core.AudioClip
import dev.localvoice.core.PcmResampler
import dev.localvoice.core.RecordingLimits
import kotlinx.coroutines.*
import kotlin.math.sqrt

class LocalRecorder(private val context: Context) {
    private var recorder: AudioRecord? = null
    private var capture: Deferred<AudioClip>? = null
    @Volatile private var running = false
    @Volatile private var discarded = false
    fun start(scope: CoroutineScope, onLevel: (Long,Float) -> Unit, onLimit: () -> Unit, onError: (String) -> Unit,onFrame: (FloatArray) -> Unit) {
        check(ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) { "Microphone permission is required." }
        check(!running)
        val pair = listOf(16_000,48_000).firstNotNullOfOrNull { rate ->
            val min = AudioRecord.getMinBufferSize(rate,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT)
            if(min <= 0) null else runCatching {
                val audio = AudioRecord.Builder().setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                    .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build())
                    .setBufferSizeInBytes(maxOf(min*2,rate/5)).build()
                if(audio.state != AudioRecord.STATE_INITIALIZED) { audio.release(); null } else rate to audio
            }.getOrNull()
        } ?: error("This microphone could not be opened. Close other recording apps and retry.")
        val (rate,audio) = pair
        recorder = audio; discarded = false; running = true
        try { audio.startRecording(); check(audio.recordingState == AudioRecord.RECORDSTATE_RECORDING) } catch(e: Exception) { running=false; audio.release(); recorder=null; throw e }
        capture = scope.async(Dispatchers.IO) {
            val samples = ShortArray(rate*RecordingLimits.MAX_SECONDS)
            val buffer = ShortArray(rate/25)
            var offset = 0
            try {
                while(running && isActive && offset < samples.size) {
                    val count = audio.read(buffer,0,minOf(buffer.size,samples.size-offset),AudioRecord.READ_BLOCKING)
                    if(count < 0) { if(running) error("Microphone was interrupted. Start a new recording."); break }
                    if(count == 0) continue
                    buffer.copyInto(samples,offset,0,count); offset += count
                    if(rate==16000) onFrame(FloatArray(count) {buffer[it]/32768f})
                    var energy=0.0
                    for(i in 0 until count) { val v=buffer[i]/32768.0; energy+=v*v }
                    onLevel(offset*1000L/rate,(sqrt(energy/count)*6).toFloat().coerceIn(0f,1f))
                }
                if(offset == samples.size && !discarded) onLimit()
                if(discarded) AudioClip(floatArrayOf()) else AudioClip(PcmResampler.resample(samples,offset,rate))
            } catch(e: Exception) {
                if(running && e !is CancellationException) onError(e.message ?: "Microphone was interrupted.")
                AudioClip(floatArrayOf())
            } finally { running=false; samples.fill(0); buffer.fill(0) }
        }
    }
    suspend fun stop(discard: Boolean = false): AudioClip {
        discarded = discard; running = false
        val active = recorder; recorder=null
        runCatching { active?.stop() }
        return try { capture?.await() ?: AudioClip(floatArrayOf()) }
        finally { active?.release(); capture=null }
    }
}
