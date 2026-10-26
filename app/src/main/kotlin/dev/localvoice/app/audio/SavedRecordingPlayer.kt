package dev.localvoice.app.audio

import android.content.Context
import android.media.*
import dev.localvoice.core.RecordingBytes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlaybackState(val ready: Boolean=false,val playing: Boolean=false,val position: Int=0,val duration: Int=0,val error: String?=null)

/** Main-thread controller; Android decodes the decrypted in-memory WAV through MediaDataSource. */
class SavedRecordingPlayer(context: Context) : AutoCloseable {
    private val manager=context.getSystemService(AudioManager::class.java)
    private val attributes=AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    private val focus=AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener {if(it<=0) pause()}.build()
    private val mutable=MutableStateFlow(PlaybackState())
    val state=mutable.asStateFlow()
    private var player: MediaPlayer?=null
    private var source: MediaDataSource?=null
    fun prepare(wav: ByteArray) {
        close()
        val owned=RecordingBytes(wav)
        source=object: MediaDataSource() {
            override fun readAt(position: Long,buffer: ByteArray,offset: Int,size: Int)=owned.read(position,buffer,offset,size)
            override fun getSize()=owned.size()
            override fun close()=owned.close()
        }
        mutable.value=PlaybackState()
        try {
            val opened=MediaPlayer()
            player=opened
            opened.apply {
                setAudioAttributes(attributes);setDataSource(source!!)
                setOnPreparedListener {mutable.value=PlaybackState(ready=true,duration=it.duration)}
                setOnCompletionListener {pause();seek(0)}
                setOnErrorListener {_,_,_ -> close();mutable.value=PlaybackState(error="This recording could not be played. Try exporting it.");true}
                prepareAsync()
            }
        } catch(error: Exception) {close();mutable.value=PlaybackState(error="This recording could not be opened.")}
    }
    fun play() {
        if(!mutable.value.ready) return
        if(manager.requestAudioFocus(focus)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            mutable.value=mutable.value.copy(error="Audio is in use by another app. Try again.");return
        }
        runCatching {player?.start();mutable.value=mutable.value.copy(playing=true,error=null)}.onFailure {pause()}
    }
    fun pause() {runCatching {player?.takeIf {mutable.value.playing && it.isPlaying}?.pause()};manager.abandonAudioFocusRequest(focus);mutable.value=mutable.value.copy(playing=false)}
    fun seek(position: Int) {if(mutable.value.ready) {val value=position.coerceIn(0,mutable.value.duration);runCatching {player?.seekTo(value)};mutable.value=mutable.value.copy(position=value)}}
    fun updatePosition() {if(mutable.value.ready) runCatching {mutable.value=mutable.value.copy(position=player?.currentPosition ?: 0)}}
    override fun close() {manager.abandonAudioFocusRequest(focus);runCatching {player?.release()};player=null;source?.close();source=null;mutable.value=PlaybackState()}
}
