package dev.localvoice.app.ui

import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon as AndroidIcon
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.localvoice.app.AppGraph
import dev.localvoice.app.R
import dev.localvoice.app.data.UserSettings

@Composable fun SettingsScreen(graph: AppGraph,prefs: UserSettings,connected: Boolean,revision: Int,modifier: Modifier,action: (suspend () -> Unit) -> Unit,history: () -> Unit,message: (String) -> Unit) {
    val context=LocalContext.current
    var disclosure by remember { mutableStateOf(false) }
    var audioDisclosure by remember { mutableStateOf(false) }
    var excluded by remember { mutableStateOf("") }
    var privacy by remember { mutableStateOf(false) }
    fun open(action: String) { runCatching { context.startActivity(Intent(action)) }.onFailure { message("Open this app's settings in Android Settings.") } }
    Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
        Text("Dictation",style=MaterialTheme.typography.titleMedium)
        LanguageChips(prefs.language) { action {check(!graph.session.busy) {"Finish dictation before changing language."};graph.settings.language(it)} }
        Text("Appearance",style=MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {listOf("light","dark","system").forEach {mode -> FilterChip(selected=prefs.theme==mode,onClick={action {graph.settings.theme(mode)}},label={Text(mode.replaceFirstChar {it.uppercase()})})}}
        HorizontalDivider()
        Text("Cross-app dictation",style=MaterialTheme.typography.titleMedium)
        Text("The bubble opens a small visible recording window. Local Voice only inserts into supported fields, and never sends a message.",style=MaterialTheme.typography.bodyMedium)
        SettingRow("Display over other apps",if(Settings.canDrawOverlays(context)) "Allowed" else "Required for the bubble",Icons.Outlined.ChatBubbleOutline) {
            runCatching {context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:${context.packageName}")))}.onFailure {message("Find Display over other apps in Android Settings.")}
        }
        SettingRow("Accessibility access",if(connected) "Connected" else "Read the disclosure before enabling",Icons.Outlined.AccessibilityNew) {disclosure=true}
        SwitchRow("Floating microphone",if(prefs.bubble) "Enabled when a compatible field is focused. Long press the bubble to hide it." else "Off",prefs.bubble) {enabled ->
            if(enabled && (!connected || !prefs.accessibilityConsent || !Settings.canDrawOverlays(context))) message("Allow the overlay and accessibility access first.")
            else action {graph.settings.bubble(enabled)}
        }
        OutlinedButton(onClick={action {graph.settings.bubblePosition(1f,.45f)}}) {Text("Reset bubble position")}
        OutlinedTextField(value=excluded,onValueChange={excluded=it},label={Text("Exclude an app package")},supportingText={Text("For example: com.example.banking. That app's fields will never show the bubble.")},modifier=Modifier.fillMaxWidth(),singleLine=true)
        TextButton(onClick={val value=excluded.trim();if(value.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+"))) action {graph.settings.exclude(value);excluded=""} else message("Enter an Android package name.")}) {Text("Add exclusion")}
        prefs.excludedApps.sorted().forEach { pkg -> Row(verticalAlignment=Alignment.CenterVertically) {Text(pkg,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium);TextButton(onClick={action {graph.settings.include(pkg)}}) {Text("Remove")}} }
        HorizontalDivider()
        Text("History and recordings",style=MaterialTheme.typography.titleMedium)
        SwitchRow("Save transcript history","Encrypted on this phone. Automatically deleted after seven days. Manual Save remains available when this is off.",prefs.autoHistory) {action {graph.settings.history(it)}}
        SwitchRow("Keep recording audio","Off by default. Optional encrypted recordings are saved with history and can be exported as WAV.",prefs.retainAudio) {if(it) audioDisclosure=true else action {graph.settings.audio(false)}}
        SettingRow("Saved transcripts","Copy, export or delete your local text and recordings",Icons.Outlined.History,history)
        HorizontalDivider()
        Text("Shortcuts",style=MaterialTheme.typography.titleMedium)
        SettingRow("Add dictation shortcut","Launcher shortcut. Quick Settings tile can also be added from the system tile editor.",Icons.Outlined.AddToHomeScreen) {
            val shortcuts=context.getSystemService(ShortcutManager::class.java)
            if(shortcuts.isRequestPinShortcutSupported) shortcuts.requestPinShortcut(ShortcutInfo.Builder(context,"pinned-dictate").setShortLabel("Dictate locally").setIcon(AndroidIcon.createWithResource(context,R.drawable.ic_mic)).setIntent(Intent(context,MainActivity::class.java).setAction("dev.localvoice.DICTATE")).build(),null)
            else message("Long press the app icon to use its Dictate locally shortcut.")
        }
        Text("With a physical keyboard: Ctrl + Shift + Space while this app is open. Global keyboard interception is not enabled.",style=MaterialTheme.typography.bodyMedium)
        HorizontalDivider()
        SettingRow("Android app permissions","Microphone and notification access",Icons.Outlined.AdminPanelSettings) {context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${context.packageName}")))}
        SettingRow("Privacy and open-source notices","Local processing, downloads, retention and licenses",Icons.Outlined.Lock) {privacy=true}
        Text("Local Voice · development build\nNo account, ads, transcript analytics or cloud recognition.",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
    }
    if(disclosure) AlertDialog(onDismissRequest={disclosure=false},title={Text("Accessibility access")},text={Column(Modifier.verticalScroll(rememberScrollState())) {
        Text("To show the microphone near supported text fields and insert text you dictate, Local Voice uses Android Accessibility. It can access information about the active screen, including editable fields. When you start dictation and when its result is inserted, it briefly reads the field's text and selection to check that they have not changed and preserve existing text. Screen contents are not saved or sent to a server. This access is not used to monitor messages, send messages, or automate other actions. Protected and unsupported editors use manual Copy instead. You can turn this access off in Android Settings.")
    }},confirmButton={TextButton(onClick={action {graph.settings.consent();disclosure=false;open(Settings.ACTION_ACCESSIBILITY_SETTINGS)}}) {Text("Agree and open settings")}},dismissButton={TextButton(onClick={disclosure=false}) {Text("Not now")}})
    if(audioDisclosure) AlertDialog(onDismissRequest={audioDisclosure=false},title={Text("Keep local recordings?")},text={Text("This enables transcript history and saves your voice recordings, encrypted on this phone, for seven days. WAV exports are unencrypted files in a location you choose. Cancelled recordings are discarded. You can delete saved recordings in History or disable this setting anytime.")},confirmButton={TextButton(onClick={action {graph.settings.audio(true);audioDisclosure=false}}) {Text("Enable recordings")}},dismissButton={TextButton(onClick={audioDisclosure=false}) {Text("Keep audio off")}})
    if(privacy) AlertDialog(onDismissRequest={privacy=false},title={Text("Privacy and licenses")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("Audio and text are recognized locally. Internet access is used only for model downloads you request; download providers receive normal request metadata. No audio or transcripts are sent to them. No account or analytics SDK is included. History is off by default and encrypted when enabled. Optional saved audio is encrypted. All saved items expire after seven days. Backups are disabled; uninstalling removes app data. Exports are written only to locations you choose.")
        Text("Whisper/whisper.cpp, Moonshine Voice, Silero VAD, GGML and ONNX Runtime use permissive licenses with their own notices. AndroidX uses Apache-2.0. This is an independent project, unaffiliated with Wispr Flow, Handy or model providers.")
        Text(LocalContext.current.assets.open("THIRD_PARTY_NOTICES.txt").bufferedReader().use {it.readText()},style=MaterialTheme.typography.bodySmall)
    }},confirmButton={TextButton(onClick={privacy=false}) {Text("Done")}})
}

@Composable private fun SwitchRow(title: String,subtitle: String,checked: Boolean,onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f)) {Text(title,style=MaterialTheme.typography.titleMedium);Text(subtitle,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        Switch(checked=checked,onCheckedChange=onChange)
    }
}
