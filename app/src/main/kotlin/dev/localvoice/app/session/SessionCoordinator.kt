package dev.localvoice.app.session

import android.content.Intent
import androidx.core.content.ContextCompat
import dev.localvoice.app.AppGraph
import dev.localvoice.app.audio.RecordingService
import dev.localvoice.core.*
import dev.localvoice.engine.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class SessionCoordinator(private val graph: AppGraph) {
    private val mutable = MutableStateFlow(SessionState())
    val state = mutable.asStateFlow()
    private var engine: AsrEngine? = null
    private var loadedId: String? = null
    private var job: Job? = null
    private var idleUnload: Job? = null
    private var generation = 0L
    private var sessionId = ""
    private var model: ModelDescriptor? = null
    private var language = LanguageMode.MIXED
    private var formatting = FormattingMode.SMART
    private var resultDuration = 0L
    private var changingModel = false
    private class LiveRun {
        val frames=Channel<FloatArray>(64)
        val failed=AtomicBoolean(false)
        val samples=AtomicInteger(0)
        val result=CompletableDeferred<String?>()
        var job: Job?=null
    }
    @Volatile private var live: LiveRun?=null
    val busy get() = changingModel || state.value.phase in listOf(SessionPhase.PREPARING,SessionPhase.RECORDING,SessionPhase.PROCESSING)
    val quarantined get() = (job?.isCompleted == false || live?.job?.isCompleted == false) && state.value.phase !in listOf(SessionPhase.PREPARING,SessionPhase.RECORDING,SessionPhase.PROCESSING)
    suspend fun activate(descriptor: ModelDescriptor) {
        check(!busy && !quarantined) { "Finish the current dictation first." }
        require(descriptor.supports(graph.preferences.value.language)) { "Choose English before using this model." }
        changingModel=true
        try {ensureEngine(descriptor);graph.settings.model(descriptor.id)}
        finally {changingModel=false;scheduleUnload()}
    }
    suspend fun removeModel(descriptor: ModelDescriptor) {
        check(!busy && !quarantined) { "Finish the current dictation first." }
        changingModel=true
        try {
            idleUnload?.cancel()
            if(loadedId==descriptor.id) { engine?.unload();engine=null;loadedId=null }
            graph.models.delete(descriptor)
        } finally {changingModel=false;scheduleUnload()}
    }
    fun resultMessage(message: String) { if(state.value.phase==SessionPhase.RESULT) mutable.value=state.value.copy(message=message) }
    fun start() {
        if(busy) return
        if(quarantined) { fail("The previous local engine is still stopping. Wait before recording again."); return }
        idleUnload?.cancel()
        val prefs = graph.preferences.value
        val selected = graph.catalog.find(prefs.modelId) ?: graph.catalog.models.first()
        if(!selected.supports(prefs.language)) { fail("This model supports English only. Change the language or choose Whisper."); return }
        if(!graph.models.installed(selected)) { fail("Download ${selected.name} from Models before recording."); return }
        model=selected; language=prefs.language; formatting=prefs.formatting; sessionId=UUID.randomUUID().toString()
        val token = ++generation
        mutable.value=SessionState(SessionPhase.PREPARING,message="Loading ${selected.name}")
        job=graph.scope.launch {
            try {
                withTimeout(30_000) { ensureEngine(selected) }
                if(token != generation) return@launch
                (engine as? LiveAsrEngine)?.let { beginLive(it,token) }
                if(token != generation) return@launch
                ContextCompat.startForegroundService(graph.app,Intent(graph.app,RecordingService::class.java).setAction(RecordingService.START))
            } catch(e: CancellationException) { if(token==generation) {cancel();fail("Model loading was cancelled or took too long.")} }
            catch(e: Exception) { if(token==generation) {cancel();fail(e.message ?: "Could not prepare local recording.")} }
        }
    }
    private suspend fun ensureEngine(descriptor: ModelDescriptor) {
        if(loadedId==descriptor.id && engine != null) return
        engine?.unload(); engine=null; loadedId=null
        val candidate: AsrEngine = when(descriptor.engine) { EngineId.WHISPER -> WhisperEngine(); EngineId.MOONSHINE -> MoonshineEngine() }
        engine=candidate
        try { candidate.load(graph.models.installedModel(descriptor)); loadedId=descriptor.id }
        catch(e: Exception) { withContext(NonCancellable) {candidate.unload()}; engine=null; throw e }
    }
    fun recording() { if(state.value.phase==SessionPhase.PREPARING) mutable.value=SessionState(SessionPhase.RECORDING) }
    fun level(elapsed: Long, level: Float) { if(state.value.phase==SessionPhase.RECORDING) mutable.value=state.value.copy(elapsedMs=elapsed,level=level) }
    fun audioFrame(frame: FloatArray) {
        val run=live
        if(run==null || run.failed.get()) {frame.fill(0f);return}
        if(run.frames.trySend(frame).isFailure) {frame.fill(0f);run.failed.set(true);run.frames.close()}
    }
    private suspend fun beginLive(adapter: LiveAsrEngine,token: Long) {
        val run=LiveRun();val ready=CompletableDeferred<Unit>();live=run
        run.job=graph.scope.launch(Dispatchers.Default) {
            try {
                adapter.beginLive(language) {partial -> graph.scope.launch {if(token==generation) mutable.value=state.value.copy(partial=partial)} }
                ready.complete(Unit)
                for(frame in run.frames) {
                    try {
                        if(run.failed.get() || token!=generation) break
                        adapter.feedLive(frame);run.samples.addAndGet(frame.size)
                    } finally {frame.fill(0f)}
                }
                if(!run.failed.get() && token==generation) run.result.complete(adapter.finishLive())
                else run.result.complete(null)
            } catch(e: Exception) {run.failed.set(true);ready.complete(Unit);run.result.complete(null)}
            finally {
                withContext(NonCancellable) {adapter.discardLive()}
                while(true) {val frame=run.frames.tryReceive().getOrNull() ?: break;frame.fill(0f)}
            }
        }
        ready.await()
    }
    fun stop() { if(state.value.phase==SessionPhase.RECORDING) graph.app.startService(Intent(graph.app,RecordingService::class.java).setAction(RecordingService.STOP)) }
    fun process(audio: AudioClip) {
        val descriptor=model ?: return
        val token=generation
        val liveRun=live
        liveRun?.frames?.close()
        mutable.value=state.value.copy(phase=SessionPhase.PROCESSING,level=0f,message="Transcribing on your phone")
        resultDuration=audio.durationMs
        job=graph.scope.launch {
            var crop: AudioClip? = null
            try {
                require(audio.durationMs >= 200 && audio.pcm.any { it != 0f }) { "No speech recorded. Try speaking again." }
                val result=withTimeout(RecordingLimits.processingTimeoutMs(audio.durationMs)) {
                    val vad=graph.catalog.vad.takeIf(graph.models::installed)?.let { graph.models.directory(it).resolve(it.files.first().name).absolutePath }
                    val liveText=liveRun?.result?.await()
                    liveRun?.job?.join()
                    if(!liveText.isNullOrBlank() && liveRun.samples.get()==audio.pcm.size && !liveRun.failed.get()) return@withTimeout liveText
                    crop=if(descriptor.engine==EngineId.WHISPER) SileroVad.crop(audio,vad) else audio
                    val raw=engine!!.transcribe(crop!!,language) { partial -> if(token==generation) graph.scope.launch { if(token==generation) mutable.value=mutable.value.copy(partial=partial) } }
                    if(raw.isBlank() && crop !== audio) engine!!.transcribe(audio,language) else raw
                }
                if(token != generation) return@launch
                val text=withContext(Dispatchers.Default) {DictationOutput.finish(result,language,formatting)}
                require(text.isNotBlank() || text=="\n" || text=="\n\n") { "No speech detected. Try recording again." }
                var saved: String?=null
                var warning=if(text.isBlank()) "Line break ready." else ""
                val prefs=graph.preferences.value
                if(prefs.autoHistory && text.isNotBlank()) {
                    try {saved=graph.history.save(text,descriptor.name,language.label,audio.durationMs,if(prefs.retainAudio) audio else null,sessionId)}
                    catch(cancel: CancellationException) {throw cancel}
                    catch(e: Exception) {warning="Your text is ready, but history could not be saved. Copy or export it before closing."}
                }
                if(token==generation) {
                    mutable.value=SessionState(SessionPhase.RESULT,elapsedMs=audio.durationMs,transcript=text,savedId=saved,message=warning)
                    graph.scope.launch { delay(300_000); if(token==generation && state.value.phase==SessionPhase.RESULT) reset() }
                }
            } catch(e: CancellationException) { engine?.cancel(); if(token==generation) fail("Transcription stopped or took too long. Try a shorter recording.") }
            catch(e: Exception) { if(token==generation) fail(e.message ?: "Local transcription failed.") }
            finally { audio.pcm.fill(0f); if(crop !== audio) crop?.pcm?.fill(0f); if(live===liveRun && liveRun?.job?.isCompleted!=false) live=null; scheduleUnload() }
        }
    }
    suspend fun saveResult(): String {
        val current=state.value
        val token=generation
        check(current.phase==SessionPhase.RESULT && current.transcript.isNotBlank())
        val id=current.savedId ?: graph.history.save(current.transcript,model?.name.orEmpty(),language.label,resultDuration,id=sessionId)
        if(token==generation && state.value.phase==SessionPhase.RESULT) mutable.value=state.value.copy(savedId=id)
        return id
    }
    fun cancel() {
        ++generation
        live?.apply {failed.set(true);frames.close()}
        engine?.cancel(); job?.cancel()
        graph.app.stopService(Intent(graph.app,RecordingService::class.java))
        mutable.value=SessionState()
        scheduleUnload()
    }
    fun reset() { if(!busy) { ++generation; mutable.value=SessionState() } }
    fun fail(message: String) { mutable.value=SessionState(SessionPhase.ERROR,message=message) }
    private fun scheduleUnload() {
        idleUnload?.cancel()
        idleUnload=graph.scope.launch {
            delay(60_000)
            if(!busy) {
                changingModel=true
                try {withContext(NonCancellable) {engine?.unload();engine=null;loadedId=null}}
                finally {changingModel=false}
            }
        }
    }
}
