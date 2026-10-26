@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package dev.localvoice.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dev.localvoice.app.graph
import dev.localvoice.app.system.DictationAccessibilityService
import dev.localvoice.core.SessionPhase
import dev.localvoice.core.SessionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RecordingActivity : ComponentActivity() {
    private var attempted=false
    private var leavingForDelivery=false
    private var leavingForOverlay=false
    private var overlayHandoffFailed by mutableStateOf(false)
    private var exportText=""
    private val microphone=registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if(granted) startWhenVisible() else graph.session.fail("Microphone permission is needed to record. Allow it in Android Settings, then retry.")
    }
    private val notifications=registerForActivityResult(ActivityResultContracts.RequestPermission()) { startWhenVisible() }
    private val export=registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) {uri ->
        if(uri!=null) lifecycleScope.launch {
            runCatching { withContext(Dispatchers.IO) { contentResolver.openOutputStream(uri)?.use {it.write(exportText.toByteArray(Charsets.UTF_8))} ?: error("Could not open the export file.") } }
                .onSuccess { graph.session.resultMessage("Transcript exported to the file you chose.") }
                .onFailure { graph.session.resultMessage("Export failed. Your transcript is still available here.") }
            exportText=""
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if(intent.getStringExtra("overlayFlow")!=null) {
            window.setGravity(android.view.Gravity.BOTTOM)
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.attributes=window.attributes.apply {dimAmount=0f}
        }
        attempted=savedInstanceState?.getBoolean("attempted") ?: false
        setContent {
            val prefs by graph.preferences.collectAsStateWithLifecycle()
            val state by graph.session.state.collectAsStateWithLifecycle()
            var confirmCancel by remember {mutableStateOf(false)}
            LocalVoiceTheme(prefs.theme) {
                BackHandler(enabled=graph.session.busy) {confirmCancel=true}
                if(intent.getStringExtra("overlayFlow")!=null && !overlayHandoffFailed && state.phase in listOf(SessionPhase.IDLE,SessionPhase.PREPARING,SessionPhase.RECORDING)) {
                    Surface(shape=androidx.compose.foundation.shape.RoundedCornerShape(20.dp)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                            Text(if(state.phase==SessionPhase.RECORDING) "Returning to your editor…" else "Starting microphone…",style=MaterialTheme.typography.titleMedium)
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                            TextButton(onClick={graph.session.cancel();finish()}) {Text("Cancel")}
                        }
                    }
                } else RecordingContent(state,graph.catalog.find(prefs.modelId)?.name.orEmpty(),prefs.language.label,
                    stop={graph.session.stop()},cancel={graph.session.cancel();finish()},
                    retry={graph.session.reset();intent.removeExtra("showResult");intent.removeExtra("overlayFlow");intent.removeExtra("target");attempted=false;requestStart()},
                    copy={runCatching {copyPrivate(this,state.transcript)}.onSuccess {graph.session.resultMessage("Copied. Paste your text into the field you choose.")}.onFailure {graph.session.resultMessage("Clipboard unavailable. Try Export instead.")}},
                    save={lifecycleScope.launch {runCatching {graph.session.saveResult()}.onFailure {graph.session.resultMessage("Could not save history. Copy or export the text before closing.")}}},
                    export={exportText=state.transcript;export.launch("local-transcript-${System.currentTimeMillis()}.txt")},
                    done={graph.session.reset();finish()},settings={startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:$packageName")))})
                if(confirmCancel) AlertDialog(onDismissRequest={confirmCancel=false},title={Text("Cancel dictation?")},text={Text("Unsaved recording and transcription work will be discarded.")},confirmButton={TextButton(onClick={graph.session.cancel();finish()}) {Text("Discard")}},dismissButton={TextButton(onClick={confirmCancel=false}) {Text("Keep going")}})
                LaunchedEffect(state.phase) {
                    val flow=intent.getStringExtra("overlayFlow")
                    if(state.phase==SessionPhase.RECORDING && flow!=null && !leavingForOverlay) {
                        if(DictationAccessibilityService.connected?.detachRecording(flow)==true) {leavingForOverlay=true;finish()}
                        else overlayHandoffFailed=true
                    }
                    if(state.phase==SessionPhase.RESULT && (flow==null || overlayHandoffFailed) && intent.getStringExtra("target")!=null && !intent.getBooleanExtra("showResult",false) && !leavingForDelivery) {
                        val token=intent.getStringExtra("target")!!
                        val service=DictationAccessibilityService.connected
                        if(service!=null) {
                            leavingForDelivery=true
                            finish()
                            service.deliver(token,state.transcript) {failure ->
                                if(failure==null) graph.session.reset()
                                else {
                                    graph.session.resultMessage(failure)
                                    runCatching {service.startActivity(Intent(service,RecordingActivity::class.java).putExtra("showResult",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}
                                }
                            }
                        } else graph.session.resultMessage("Accessibility access disconnected. Copy your text instead.")
                    }
                }
            }
        }
    }
    override fun onPostResume() {super.onPostResume();requestStart()}
    private fun requestStart() {
        if(attempted || intent.getBooleanExtra("showResult",false) || graph.session.state.value.phase!=SessionPhase.IDLE) return
        attempted=true
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) microphone.launch(Manifest.permission.RECORD_AUDIO)
        else startWhenVisible()
    }
    private fun startWhenVisible() {
        val prompts=getSharedPreferences("permission_prompts",MODE_PRIVATE)
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED && !prompts.getBoolean("notifications_asked",false)) {
            prompts.edit().putBoolean("notifications_asked",true).apply()
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        lifecycleScope.launch {
            repeat(20) {
                delay(100)
                if(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    graph.session.start()
                    return@launch
                }
            }
        }
    }
    override fun onSaveInstanceState(outState: Bundle) {outState.putBoolean("attempted",attempted);super.onSaveInstanceState(outState)}
    override fun onStop() {
        super.onStop()
        if(!isChangingConfigurations && !leavingForDelivery && !leavingForOverlay && graph.session.state.value.phase==SessionPhase.PROCESSING) graph.session.cancel()
    }
    override fun onDestroy() {if(isFinishing && !leavingForDelivery && !leavingForOverlay && graph.session.busy) graph.session.cancel();super.onDestroy()}
}

@Composable private fun RecordingContent(state: SessionState,model: String,language: String,stop: () -> Unit,cancel: () -> Unit,retry: () -> Unit,copy: () -> Unit,save: () -> Unit,export: () -> Unit,done: () -> Unit,settings: () -> Unit) {
    val colors=MaterialTheme.colorScheme
    val label=when(state.phase) {SessionPhase.PREPARING -> "Getting ready";SessionPhase.RECORDING -> "Recording";SessionPhase.PROCESSING -> "Transcribing";SessionPhase.RESULT -> "Your transcript";SessionPhase.ERROR -> "Dictation needs attention";else -> "Local dictation"}
    Surface(color=colors.surface,shape=androidx.compose.foundation.shape.RoundedCornerShape(28.dp)) {
        Column(Modifier.fillMaxWidth().heightIn(max=850.dp).verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
            Text(label,style=MaterialTheme.typography.headlineMedium)
            Text("Audio stays on this phone",color=colors.onSurfaceVariant,style=MaterialTheme.typography.bodyMedium)
            when(state.phase) {
                SessionPhase.RECORDING -> {
                    Surface(color=colors.secondaryContainer,shape=androidx.compose.foundation.shape.RoundedCornerShape(12.dp)) {Text("${time(state.elapsedMs)} / 02:00",Modifier.padding(16.dp),style=MaterialTheme.typography.titleLarge)}
                    Canvas(Modifier.fillMaxWidth().height(110.dp)) {
                        val heights=listOf(.45f,.72f,1f,.78f,.5f)
                        val width=12.dp.toPx();val gap=10.dp.toPx();val total=5*width+4*gap
                        heights.forEachIndexed {i,factor -> val h=(12.dp.toPx()+size.height*.8f*state.level*factor);drawRoundRect(colors.primary,Offset((size.width-total)/2+i*(width+gap),(size.height-h)/2),Size(width,h),CornerRadius(width/2))}
                    }
                    Text(if(state.elapsedMs>=110_000) "Recording will stop at two minutes" else "Listening",style=MaterialTheme.typography.titleMedium)
                    if(state.partial.isNotBlank()) SelectionContainer { Text(state.partial,style=MaterialTheme.typography.bodyLarge) }
                    Text("$model · $language",style=MaterialTheme.typography.bodyMedium,color=colors.onSurfaceVariant)
                    Button(onClick=stop,modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)) {Icon(Icons.Outlined.Stop,null);Spacer(Modifier.width(8.dp));Text("Stop and transcribe")}
                    TextButton(onClick=cancel,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {Text("Cancel recording")}
                }
                SessionPhase.PREPARING,SessionPhase.PROCESSING -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(state.message.ifEmpty {"Preparing local recording"})
                    if(state.partial.isNotBlank()) SelectionContainer {Text(state.partial)}
                    TextButton(onClick=cancel,modifier=Modifier.fillMaxWidth()) {Text("Cancel")}
                }
                SessionPhase.RESULT -> {
                    if(state.message.isNotBlank()) Text(state.message,style=MaterialTheme.typography.bodyMedium)
                    SelectionContainer {Text(state.transcript,style=MaterialTheme.typography.bodyLarge)}
                    Button(onClick=copy,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) {Icon(Icons.Outlined.ContentCopy,null);Spacer(Modifier.width(8.dp));Text("Copy text")}
                    OutlinedButton(onClick=save,enabled=state.savedId==null,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {Icon(Icons.Outlined.Save,null);Spacer(Modifier.width(8.dp));Text(if(state.savedId==null) "Save transcript" else "Saved")}
                    OutlinedButton(onClick=export,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {Text("Export text")}
                    Text("Unsaved text is held in memory for up to five minutes. Use Save to keep it locally, or export it to a file.",style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
                    TextButton(onClick=done,modifier=Modifier.fillMaxWidth()) {Text("Done")}
                }
                SessionPhase.ERROR -> {
                    Icon(Icons.Outlined.ErrorOutline,null,tint=colors.error)
                    Text(state.message)
                    OutlinedButton(onClick=retry,modifier=Modifier.fillMaxWidth()) {Text("Try again")}
                    TextButton(onClick=settings) {Text("Android app permissions")}
                    TextButton(onClick=cancel) {Text("Close")}
                }
                else -> {Text("Ready for a new dictation. Previous unsaved text may have expired.");Button(onClick=retry) {Text("Record again")};TextButton(onClick=cancel) {Text("Close")}}
            }
        }
    }
}
private fun time(ms: Long)="%02d:%02d".format(ms/60_000,ms/1000%60)
