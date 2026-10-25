package dev.localvoice.app.ui

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.localvoice.app.AppGraph
import dev.localvoice.app.data.HistoryEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

fun copyPrivate(context: Context,text: String) {
    val clip=ClipData.newPlainText("Local transcript",text)
    clip.description.extras=PersistableBundle().apply {putBoolean("android.content.extra.IS_SENSITIVE",true)}
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
}

@Composable fun HistoryScreen(graph: AppGraph,entries: List<HistoryEntry>,modifier: Modifier,action: (suspend () -> Unit) -> Unit,message: (String) -> Unit) {
    val context=LocalContext.current
    var selected by remember { mutableStateOf<HistoryEntry?>(null) }
    var clear by remember { mutableStateOf(false) }
    var exportEntry by remember { mutableStateOf<HistoryEntry?>(null) }
    val textExport=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) {uri ->
        val entry=exportEntry
        if(uri!=null && entry!=null) action {withContext(Dispatchers.IO) {context.contentResolver.openOutputStream(uri)?.use {it.write(graph.history.text(entry).toByteArray(Charsets.UTF_8))} ?: error("Could not open the export file.")};message("Transcript exported")}
        exportEntry=null
    }
    val audioExport=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/wav")) {uri ->
        val entry=exportEntry
        if(uri!=null && entry!=null) action {withContext(Dispatchers.IO) {val audio=graph.history.audio(entry) ?: error("No recording is saved for this transcript.");try {context.contentResolver.openOutputStream(uri)?.use {it.write(audio)} ?: error("Could not open the export file.")} finally {audio.fill(0)}};message("Recording exported")}
        exportEntry=null
    }
    LazyColumn(modifier,contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item {Text("Saved only on this phone",style=MaterialTheme.typography.titleLarge);Text("Encrypted text and optional recordings. Items expire after seven days; export anything you want to keep.",style=MaterialTheme.typography.bodyMedium)}
        if(entries.isEmpty()) item {Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {Icon(Icons.Outlined.History,null,Modifier.size(40.dp));Text("Your saved words will appear here",style=MaterialTheme.typography.titleMedium);Text("Use Save after dictation, or enable automatic history in Settings. Nothing is saved until you choose.")}}
        items(entries,key={it.id}) {entry ->
            val text by produceState<String?>(null,entry.id) {value=runCatching {graph.history.text(entry)}.getOrElse {"This encrypted entry could not be opened. Delete it if the device key was reset."}}
            Surface(shape=androidx.compose.foundation.shape.RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surfaceContainerLow,onClick={selected=entry}) {
                Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(text ?: "Opening encrypted transcript…",maxLines=3,overflow=TextOverflow.Ellipsis)
                    Text("${DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(entry.createdAt))}\n${entry.modelName} · ${entry.language}${if(entry.audioFile!=null) " · Audio saved" else ""}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if(entries.isNotEmpty()) item {TextButton(onClick={clear=true}) {Icon(Icons.Outlined.DeleteOutline,null);Spacer(Modifier.width(8.dp));Text("Delete all history")}}
    }
    selected?.let {entry ->
        val text by produceState<String?>(null,entry.id) {value=runCatching {graph.history.text(entry)}.getOrNull()}
        AlertDialog(onDismissRequest={selected=null},title={Text("Saved transcript")},text={SelectionContainer {Text(text ?: "This entry cannot be decrypted. You can delete it.")}},confirmButton={Column {
            Row {TextButton(onClick={text?.let {copyPrivate(context,it);message("Copied")}},enabled=text!=null) {Text("Copy")};TextButton(onClick={exportEntry=entry;textExport.launch("transcript-${entry.id.take(8)}.txt")},enabled=text!=null) {Text("Export text")}}
            if(entry.audioFile!=null) TextButton(onClick={exportEntry=entry;audioExport.launch("recording-${entry.id.take(8)}.wav")}) {Text("Export recording")}
            TextButton(onClick={selected=null}) {Text("Done")}
        }},dismissButton={TextButton(onClick={action {graph.history.delete(entry);selected=null}}) {Text("Delete")}})
    }
    if(clear) AlertDialog(onDismissRequest={clear=false},title={Text("Delete all saved history?")},text={Text("All encrypted transcripts and recordings in this app will be removed. Files you previously exported are not deleted.")},confirmButton={TextButton(onClick={action {graph.history.clear();clear=false}}) {Text("Delete all")}},dismissButton={TextButton(onClick={clear=false}) {Text("Keep history")}})
}
