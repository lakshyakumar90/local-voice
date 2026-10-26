package dev.localvoice.app.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.localvoice.app.AppGraph
import dev.localvoice.app.audio.SavedRecordingPlayer
import dev.localvoice.app.data.HistoryEntry
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

@Composable fun TranscriptDetail(graph: AppGraph,entry: HistoryEntry,modifier: Modifier,back: () -> Unit,recordingSettings: () -> Unit,copy: (String) -> Unit,exportText: () -> Unit,exportAudio: () -> Unit,delete: () -> Unit) {
    BackHandler(onBack=back)
    var confirmDelete by remember(entry.id) {mutableStateOf(false)}
    var loading by remember(entry.id) {mutableStateOf(true)}
    var text by remember(entry.id) {mutableStateOf<String?>(null)}
    LaunchedEffect(entry.id) {try {text=graph.history.text(entry)} catch(_: Exception) {text=null} finally {loading=false}}
    Column(modifier.verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(24.dp)) {
        Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT).format(Date(entry.createdAt)),style=MaterialTheme.typography.titleMedium)
        Text("${entry.modelName} · ${entry.language}",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(entry.audioFile!=null) SavedAudio(graph,entry) else Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("No recording saved",style=MaterialTheme.typography.titleMedium)
            Text("This transcript contains text only. Enable recording storage for future dictations; audio cannot be recovered for this entry.",style=MaterialTheme.typography.bodyMedium)
            TextButton(onClick=recordingSettings) {Text("Recording storage settings")}
        }
        HorizontalDivider()
        Text("Transcript",style=MaterialTheme.typography.titleLarge)
        SelectionContainer {Text(text ?: if(loading) "Opening transcript…" else "This transcript could not be decrypted.",style=MaterialTheme.typography.bodyLarge)}
        Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Button(onClick={text?.let(copy)},enabled=text!=null,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) {Icon(Icons.Outlined.ContentCopy,null);Spacer(Modifier.width(8.dp));Text("Copy text")}
            OutlinedButton(onClick=exportText,enabled=text!=null,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) {Text("Export text")}
            if(entry.audioFile!=null) OutlinedButton(onClick=exportAudio,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) {Text("Export recording")}
        }
        HorizontalDivider()
        TextButton(onClick={confirmDelete=true},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),colors=ButtonDefaults.textButtonColors(contentColor=MaterialTheme.colorScheme.error)) {Icon(Icons.Outlined.DeleteOutline,null);Spacer(Modifier.width(8.dp));Text("Delete transcript${if(entry.audioFile!=null) " and recording" else ""}")}
    }
    if(confirmDelete) AlertDialog(onDismissRequest={confirmDelete=false},title={Text("Delete this transcript?")},text={Text(if(entry.audioFile!=null) "The saved text and recording will be removed from this app. Exported files are kept." else "The saved text will be removed from this app.")},confirmButton={TextButton(onClick={confirmDelete=false;delete()}) {Text("Delete")}},dismissButton={TextButton(onClick={confirmDelete=false}) {Text("Cancel")}})
}

@Composable private fun SavedAudio(graph: AppGraph,entry: HistoryEntry) {
    val context=LocalContext.current
    val player=remember(entry.id) {SavedRecordingPlayer(context)}
    val state by player.state.collectAsStateWithLifecycle()
    var loadingError by remember(entry.id) {mutableStateOf<String?>(null)}
    val lifecycle=LocalLifecycleOwner.current
    LaunchedEffect(entry.id) {
        try {graph.history.audio(entry)?.let(player::prepare) ?: run {loadingError="The saved recording is missing."}}
        catch(_: Exception) {loadingError="The recording could not be decrypted."}
    }
    DisposableEffect(player,lifecycle) {
        val observer=LifecycleEventObserver {_,event -> if(event==Lifecycle.Event.ON_STOP) player.pause()}
        lifecycle.lifecycle.addObserver(observer)
        onDispose {lifecycle.lifecycle.removeObserver(observer);player.close()}
    }
    LaunchedEffect(state.playing) {while(state.playing) {player.updatePosition();delay(250)}}
    Surface(color=MaterialTheme.colorScheme.surfaceContainer,shape=androidx.compose.foundation.shape.RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("Recording",style=MaterialTheme.typography.titleMedium)
            if(loadingError!=null || state.error!=null) Text(loadingError ?: state.error.orEmpty(),color=MaterialTheme.colorScheme.error)
            else if(!state.ready) LinearProgressIndicator(Modifier.fillMaxWidth())
            var seeking by remember {mutableStateOf<Float?>(null)}
            Slider(value=seeking ?: state.position.toFloat(),onValueChange={seeking=it},onValueChangeFinished={seeking?.let {player.seek(it.toInt())};seeking=null},valueRange=0f..state.duration.coerceAtLeast(1).toFloat(),enabled=state.ready,modifier=Modifier.semantics {contentDescription="Recording position"})
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                FilledTonalButton(onClick={if(state.playing) player.pause() else player.play()},enabled=state.ready && loadingError==null,modifier=Modifier.heightIn(min=48.dp)) {Icon(if(state.playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,null);Spacer(Modifier.width(8.dp));Text(if(state.playing) "Pause" else "Play")}
                Text("${playbackTime(state.position)} / ${playbackTime(if(state.ready) state.duration else entry.durationMs.toInt())}",style=MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
fun playbackTime(ms: Int)="%d:%02d".format(ms.coerceAtLeast(0)/60_000,ms.coerceAtLeast(0)/1000%60)
