@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package dev.localvoice.app.ui

import android.app.Activity
import android.app.NotificationManager
import android.content.*
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon as AndroidIcon
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import dev.localvoice.app.AppGraph
import dev.localvoice.app.R
import dev.localvoice.app.data.*
import dev.localvoice.app.system.DictationAccessibilityService
import dev.localvoice.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@Composable fun AppScreen(graph: AppGraph,record: () -> Unit) {
    val context=LocalContext.current
    val prefs by graph.preferences.collectAsStateWithLifecycle()
    val session by graph.session.state.collectAsStateWithLifecycle()
    val connected by DictationAccessibilityService.isConnected.collectAsStateWithLifecycle()
    val jobs by graph.models.work.getWorkInfosByTagFlow("model-download").collectAsStateWithLifecycle(initialValue=emptyList())
    val history by graph.history.entries.collectAsStateWithLifecycle(initialValue=emptyList())
    val scope=rememberCoroutineScope()
    var screen by rememberSaveable { mutableStateOf("Home") }
    var revision by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    val snack=remember { SnackbarHostState() }
    val lifecycle=LocalLifecycleOwner.current
    DisposableEffect(lifecycle) {
        val observer=LifecycleEventObserver { _,e -> if(e==Lifecycle.Event.ON_RESUME) revision++ }
        lifecycle.lifecycle.addObserver(observer);onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(message) { message?.let { snack.showSnackbar(it); message=null } }
    LaunchedEffect(jobs) { revision++ }
    val installed=remember(jobs,revision) { graph.catalog.models.associate { it.id to graph.models.installed(it) } }
    val action: (suspend () -> Unit) -> Unit = { block -> scope.launch { try { block();revision++ } catch(e: Exception) { message=e.message ?: "This action could not be completed." } } }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide=maxWidth>=600.dp
        val destinations=listOf("Home" to Icons.Outlined.Home,"Models" to Icons.Outlined.Memory,"Settings" to Icons.Outlined.Settings)
        Row {
            if(wide) NavigationRail { Spacer(Modifier.height(24.dp)); destinations.forEach { (label,icon) -> NavigationRailItem(selected=screen==label || screen=="History" && label=="Settings",onClick={screen=label},icon={Icon(icon,label)},label={Text(label)}) } }
            Scaffold(modifier=Modifier.weight(1f),containerColor=MaterialTheme.colorScheme.background,
                topBar={ TopAppBar(title={Text(if(screen=="Home") "Voice typing" else screen)},navigationIcon={if(screen=="History") IconButton(onClick={screen="Settings"}) {Icon(Icons.AutoMirrored.Outlined.ArrowBack,"Back")}}) },
                snackbarHost={SnackbarHost(snack)},
                bottomBar={if(!wide) NavigationBar {destinations.forEach { (label,icon) -> NavigationBarItem(selected=screen==label || screen=="History" && label=="Settings",onClick={screen=label},icon={Icon(icon,label)},label={Text(label)})}} },
            ) { padding ->
                val body=Modifier.padding(padding).fillMaxSize()
                when(screen) {
                    "Home" -> HomeScreen(graph,prefs,installed,connected,body,record,{screen="Models"},{screen="Settings"},{screen="History"})
                    "Models" -> ModelScreen(graph,prefs,jobs,installed,body,action,{message=it})
                    "History" -> HistoryScreen(graph,history,body,action,{message=it})
                    else -> SettingsScreen(graph,prefs,connected,revision,body,action,{screen="History"},{message=it})
                }
            }
        }
    }
}

@Composable private fun HomeScreen(graph: AppGraph,prefs: UserSettings,installed: Map<String,Boolean>,connected: Boolean,modifier: Modifier,record: () -> Unit,models: () -> Unit,settings: () -> Unit,history: () -> Unit) {
    val context=LocalContext.current
    val model=graph.catalog.find(prefs.modelId) ?: graph.catalog.models.first()
    val ready=installed[model.id]==true && model.supports(prefs.language)
    Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(24.dp)) {
        Text("Your voice. Your phone.",style=MaterialTheme.typography.bodyLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.primaryContainer) {
            Column(Modifier.fillMaxWidth().padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Icon(if(ready) Icons.Outlined.OfflineBolt else Icons.Outlined.Download,null,Modifier.size(28.dp),tint=MaterialTheme.colorScheme.onPrimaryContainer)
                Text(if(ready) "Ready to dictate" else "Make room for your voice",style=MaterialTheme.typography.titleLarge)
                Text(if(ready) "${prefs.language.label}\nTranscription stays on this phone." else "Choose a local model to get started. Download only the one you need.",style=MaterialTheme.typography.bodyLarge)
                Button(onClick=if(ready) record else models,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) { Icon(if(ready) Icons.Outlined.Mic else Icons.Outlined.Download,null);Spacer(Modifier.width(8.dp));Text(if(ready) "Try dictation" else "Choose a model") }
            }
        }
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Current model",style=MaterialTheme.typography.titleMedium)
            SettingRow(model.name,if(ready) "${prefs.language.label} · Downloaded" else "Not ready offline",Icons.Outlined.Memory,models)
        }
        Column {
            SettingRow("Cross-app dictation",if(prefs.bubble && connected && Settings.canDrawOverlays(context)) "Bubble enabled" else "Set up the floating microphone",Icons.Outlined.ChatBubbleOutline,settings)
            HorizontalDivider()
            SettingRow("Saved transcripts","History and local exports",Icons.Outlined.History,history)
        }
        Surface(color=MaterialTheme.colorScheme.surfaceContainer,shape=RoundedCornerShape(12.dp)) {
            Row(Modifier.padding(16.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {Icon(Icons.Outlined.Lock,null);Text("No account. No cloud transcription. Models need a download; recognition works offline afterward.",style=MaterialTheme.typography.bodyMedium)}
        }
        Text("Shortcut: long press the launcher icon, add the Quick Settings tile, or use Ctrl + Shift + Space while this app is open.",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
    }
}

@Composable private fun ModelScreen(graph: AppGraph,prefs: UserSettings,jobs: List<WorkInfo>,installed: Map<String,Boolean>,modifier: Modifier,action: (suspend () -> Unit) -> Unit,message: (String) -> Unit) {
    var detail by remember { mutableStateOf<ModelDescriptor?>(null) }
    var download by remember { mutableStateOf<ModelDescriptor?>(null) }
    var deleting by remember { mutableStateOf<ModelDescriptor?>(null) }
    var activating by remember { mutableStateOf<String?>(null) }
    LazyColumn(modifier,contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item { Text("Download only what you need",style=MaterialTheme.typography.bodyLarge,color=MaterialTheme.colorScheme.onSurfaceVariant) }
        item { LanguageChips(prefs.language) { language -> action { check(!graph.session.busy) { "Finish dictation before changing language." };graph.settings.language(language) } } }
        items(graph.catalog.models,key={it.id}) { model ->
            val active=model.id==prefs.modelId && installed[model.id]==true
            ModelRow(graph,model,prefs,active,installed[model.id]==true,activating==model.id,{detail=model},{download=model},{deleting=model},action) {
                action { activating=model.id;try {graph.session.activate(model);message("${model.name} is active")} finally {activating=null} }
            }
        }
        item { HorizontalDivider() }
        item { Text("Speech detection",style=MaterialTheme.typography.titleMedium) }
        item {
            val vad=graph.catalog.vad
            Text("Optional Silero VAD finds speech boundaries. It is not a transcription model.",style=MaterialTheme.typography.bodyMedium)
            TextButton(onClick={if(graph.models.installed(vad)) message("Silero VAD is downloaded") else download=vad}) {Text(if(graph.models.installed(vad)) "Speech detection downloaded" else "Download VAD · 0.9 MB")}
        }
        item { Text("Phone speed and accuracy have not been benchmarked yet. More Handy models need separate Android validation.",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    download?.let { model -> AlertDialog(onDismissRequest={download=null},title={Text("Download ${model.name}?")},text={Text("${size(model.bytes)} of model files. Keep this app's data to use them offline. These are data files, not a cloud service.")},confirmButton={TextButton(onClick={graph.models.download(model,true);download=null}) {Text("Wi-Fi only")}},dismissButton={Row {TextButton(onClick={graph.models.download(model,false);download=null}) {Text("Use any network")};TextButton(onClick={download=null}) {Text("Cancel")}}}) }
    detail?.let { model -> val context=LocalContext.current; AlertDialog(onDismissRequest={detail=null},title={Text(model.name)},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(model.description);Text(if(model.englishOnly) "English only" else "Multilingual, including English and Hindi. Hinglish quality needs device testing.")
        Text("Download: ${size(model.bytes)}\nSuggested RAM: ${model.recommendedRamGb} GB (proposed)\nEngine: ${model.engine}\nWeight license: ${model.weightLicense}")
        Text("Phone benchmarks not yet available.")
        model.files.forEach { Text("${it.name}\nSHA-256: ${it.sha256}",style=MaterialTheme.typography.bodySmall) }
        TextButton(onClick={context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(model.sourceUrl)))}) {Text("Model source and license")}
    }},confirmButton={TextButton(onClick={detail=null}) {Text("Done")}}) }
    deleting?.let { model -> AlertDialog(onDismissRequest={deleting=null},title={Text("Delete ${model.name}?")},text={Text("The model can be downloaded again. If it is active, dictation will need a model before it can start.")},confirmButton={TextButton(onClick={action {graph.session.removeModel(model);deleting=null}}) {Text("Delete")}},dismissButton={TextButton(onClick={deleting=null}) {Text("Keep model")}}) }
}

@Composable private fun ModelRow(graph: AppGraph,model: ModelDescriptor,prefs: UserSettings,active: Boolean,installed: Boolean,loading: Boolean,details: () -> Unit,download: () -> Unit,delete: () -> Unit,action: (suspend () -> Unit) -> Unit,activate: () -> Unit) {
    val jobs by graph.models.work.getWorkInfosForUniqueWorkFlow("model-${model.id}").collectAsStateWithLifecycle(initialValue=emptyList())
    val job=jobs.firstOrNull {!it.state.isFinished} ?: jobs.firstOrNull {it.state==WorkInfo.State.FAILED} ?: jobs.firstOrNull()
    val inProgress=job?.state in listOf(WorkInfo.State.RUNNING,WorkInfo.State.ENQUEUED,WorkInfo.State.BLOCKED)
    val compatible=model.supports(prefs.language)
    Surface(shape=RoundedCornerShape(16.dp),color=if(active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {Text(model.name,style=MaterialTheme.typography.titleMedium,modifier=Modifier.weight(1f));IconButton(onClick=details) {Icon(Icons.Outlined.Info,"Model details")}}
            Text(model.description,style=MaterialTheme.typography.bodyMedium)
            Text("${if(model.englishOnly) "English only" else "English, Hindi + more"} · ${size(model.bytes)}",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            when {
                loading -> {LinearProgressIndicator(Modifier.fillMaxWidth());Text("Loading model")}
                inProgress -> {
                    val done=job?.progress?.getLong("done",0) ?: 0;val total=model.bytes
                    LinearProgressIndicator(progress={ (done.toFloat()/total).coerceIn(0f,1f) },modifier=Modifier.fillMaxWidth())
                    Text(if(job?.state==WorkInfo.State.ENQUEUED) "Queued · waiting for connection" else "${job?.progress?.getString("phase") ?: "Downloading"} · ${size(done)} / ${size(total)}",style=MaterialTheme.typography.bodyMedium)
                    Row {TextButton(onClick={action {graph.models.pause(model)}}) {Text("Pause")};TextButton(onClick={action {graph.models.cancel(model)}}) {Text("Cancel")}}
                }
                active -> Row(verticalAlignment=Alignment.CenterVertically) {Icon(Icons.Outlined.CheckCircle,null);Spacer(Modifier.width(8.dp));Text(if(compatible) "Active · Downloaded" else "Downloaded · change language to use");Spacer(Modifier.weight(1f));IconButton(onClick=delete) {Icon(Icons.Outlined.DeleteOutline,"Delete model")}}
                installed -> Row {OutlinedButton(onClick=activate,enabled=compatible) {Text("Use model")};Spacer(Modifier.weight(1f));IconButton(onClick=delete) {Icon(Icons.Outlined.DeleteOutline,"Delete model")}}
                else -> {
                    if(job?.state==WorkInfo.State.FAILED) Text(job.outputData.getString("error") ?: "Download failed. Retry to resume.",color=MaterialTheme.colorScheme.error)
                    if(!compatible) Text("Choose English to use this model",style=MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick=download,enabled=compatible) {Icon(Icons.Outlined.Download,null);Spacer(Modifier.width(8.dp));Text(if(job?.state==WorkInfo.State.CANCELLED) "Resume download" else "Download")}
                }
            }
        }
    }
}

@Composable fun LanguageChips(selected: LanguageMode,onChange: (LanguageMode) -> Unit) {
    Column { Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {LanguageMode.entries.take(2).forEach {FilterChip(selected=selected==it,onClick={onChange(it)},label={Text(it.label)})}};FilterChip(selected=selected==LanguageMode.MIXED,onClick={onChange(LanguageMode.MIXED)},label={Text(LanguageMode.MIXED.label)}) }
}

@Composable fun SettingRow(title: String,subtitle: String,icon: ImageVector,onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick=onClick).padding(vertical=16.dp).heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
        Icon(icon,null,tint=MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {Text(title,style=MaterialTheme.typography.titleMedium);Text(subtitle,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight,null)
    }
}
private fun size(bytes: Long): String=if(bytes>=1_000_000_000) "%.1f GB".format(bytes/1e9) else "%.1f MB".format(bytes/1e6)
