package dev.localvoice.app.ui

import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon as AndroidIcon
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.localvoice.app.AppGraph
import dev.localvoice.app.R
import dev.localvoice.app.data.UserSettings

@Composable fun SettingsScreen(graph: AppGraph,prefs: UserSettings,connected: Boolean,revision: Int,modifier: Modifier,action: (suspend () -> Unit) -> Unit,history: () -> Unit,message: (String) -> Unit,initialSection: String?=null) {
    val context=LocalContext.current
    var disclosure by remember { mutableStateOf(false) }
    var audioDisclosure by remember { mutableStateOf(false) }
    var excluded by remember { mutableStateOf("") }
    var privacy by remember { mutableStateOf(false) }
    var permissionHelp by remember { mutableStateOf(false) }
    var section by rememberSaveable(initialSection) {mutableStateOf(initialSection)}
    BackHandler(enabled=section!=null) {section=null}
    val scroll=rememberScrollState()
    LaunchedEffect(section) {scroll.scrollTo(0)}
    fun open(action: String) { runCatching { context.startActivity(Intent(action)) }.onFailure { message("Open this app's settings in Android Settings.") } }
    Column(modifier.verticalScroll(scroll).padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
        if(section!=null) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                IconButton(onClick={section=null}) {Icon(Icons.AutoMirrored.Outlined.ArrowBack,contentDescription="Back to settings")}
                Text("Settings",style=MaterialTheme.typography.titleMedium)
            }
            Text(when(section) {"Microphone"->"Microphone in other apps";"Storage"->"History & recordings";"Excluded"->"Excluded apps";"Privacy"->"Privacy & about";else->section.orEmpty()},style=MaterialTheme.typography.titleLarge)
        }
        when(section) {
        null -> {
            SettingRow("Dictation",prefs.language.label,Icons.Outlined.Mic) {section="Dictation"}
            SettingRow("Appearance",prefs.theme.replaceFirstChar {it.uppercase()},Icons.Outlined.Settings) {section="Appearance"}
            SettingRow("Microphone in other apps",if(connected && Settings.canDrawOverlays(context)) "Permissions ready" else "Set up the input-field microphone",Icons.Outlined.ChatBubbleOutline) {section="Microphone"}
            SettingRow("History & recordings",if(prefs.retainAudio) "Text and audio saved" else if(prefs.autoHistory) "Text history on · audio off" else "Automatic saving off",Icons.Outlined.History) {section="Storage"}
            SettingRow("Shortcuts","Launcher, keyboard and Android shortcut help",Icons.Outlined.AddToHomeScreen) {section="Shortcuts"}
            SettingRow("Privacy & about","Permissions, local storage and licenses",Icons.Outlined.Lock) {section="Privacy"}
        }
        "Dictation" -> {
        LanguageChips(prefs.language) { action {check(!graph.session.busy) {"Finish dictation before changing language."};graph.settings.language(it)} }
        Text(when(prefs.language) {
            dev.localvoice.core.LanguageMode.ENGLISH -> "English speech, written in English."
            dev.localvoice.core.LanguageMode.HINDI -> "Hindi speech, written in देवनागरी. English words can stay in English."
            dev.localvoice.core.LanguageMode.MIXED -> "Hindi and English speech, with Hindi in Roman letters: कल मिलते हैं → kal milte hain."
        },style=MaterialTheme.typography.bodyMedium)
        HorizontalDivider()
        Text("Formatting",style=MaterialTheme.typography.titleMedium)
        dev.localvoice.core.FormattingMode.entries.forEach {mode ->
            Row(Modifier.fillMaxWidth().heightIn(min=48.dp).selectable(selected=prefs.formatting==mode,role=Role.RadioButton,onClick={action {check(!graph.session.busy) {"Finish dictation before changing formatting."};graph.settings.formatting(mode)}}),verticalAlignment=Alignment.CenterVertically) {
                RadioButton(selected=prefs.formatting==mode,onClick=null)
                Text(mode.label)
            }
        }
        Text(if(prefs.formatting==dev.localvoice.core.FormattingMode.SMART) "Local lists, paragraphs and punctuation. Say ‘new paragraph’, ‘bullet point’, or ‘first… second…’. Removes um/uh and clear numeric corrections; keeps your language." else "Keeps the model’s wording and punctuation, without cleanup or spoken formatting commands.",style=MaterialTheme.typography.bodyMedium)
        Text("Up to 10 minutes per recording. Longer recordings take more time to transcribe locally.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        "Appearance" -> {
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {listOf("light","dark","system").forEach {mode -> FilterChip(selected=prefs.theme==mode,onClick={action {graph.settings.theme(mode)}},label={Text(mode.replaceFirstChar {it.uppercase()})})}}
        }
        "Microphone" -> {
        Text("Focus an input field, tap the mic, then stop when you finish. Recording controls stay over the editor and final text returns to the original field. Unsupported editors use Copy.",style=MaterialTheme.typography.bodyMedium)
        SettingRow("Display over other apps",if(Settings.canDrawOverlays(context)) "Allowed" else "Required for the bubble",Icons.Outlined.ChatBubbleOutline) {
            runCatching {context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:${context.packageName}")))}.onFailure {
                runCatching {context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))}.onFailure {permissionHelp=true}
            }
        }
        SettingRow("Accessibility access",if(connected) "Connected" else "Read the disclosure before enabling",Icons.Outlined.AccessibilityNew) {disclosure=true}
        OutlinedButton(onClick={permissionHelp=true},modifier=Modifier.fillMaxWidth()) {Text("Permission disabled or restricted?")}
        SwitchRow("Input-field microphone",if(prefs.bubble) "Visible near compatible focused fields. Long press to hide." else "Off",prefs.bubble) {enabled ->
            if(enabled && (!connected || !prefs.accessibilityConsent || !Settings.canDrawOverlays(context))) message("Allow the overlay and accessibility access first.")
            else action {graph.settings.bubble(enabled)}
        }
        OutlinedButton(onClick={action {graph.settings.bubblePosition(1f,.45f)}}) {Text("Reset bubble position")}
        SettingRow("Excluded apps","${prefs.excludedApps.size} apps excluded",Icons.Outlined.AdminPanelSettings) {section="Excluded"}
        }
        "Excluded" -> {
        OutlinedTextField(value=excluded,onValueChange={excluded=it},label={Text("Exclude an app package")},supportingText={Text("For example: com.example.banking. That app's fields will never show the bubble.")},modifier=Modifier.fillMaxWidth(),singleLine=true)
        TextButton(onClick={val value=excluded.trim();if(value.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+"))) action {graph.settings.exclude(value);excluded=""} else message("Enter an Android package name.")}) {Text("Add exclusion")}
        prefs.excludedApps.sorted().forEach { pkg -> Row(verticalAlignment=Alignment.CenterVertically) {Text(pkg,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium);TextButton(onClick={action {graph.settings.include(pkg)}}) {Text("Remove")}} }
        }
        "Storage" -> {
        SettingRow("Open history","Read transcripts and listen to saved recordings",Icons.Outlined.History,history)
        HorizontalDivider()
        SwitchRow("Save new transcripts automatically","Encrypted on this phone. Items older than seven days are removed when the app starts.",prefs.autoHistory) {action {graph.settings.history(it)}}
        SwitchRow("Keep recordings with history","Save encrypted audio for playback and WAV export. This applies to future dictations.",prefs.retainAudio) {if(it) audioDisclosure=true else action {graph.settings.audio(false)}}
        Text("Existing text-only entries do not contain audio. Manual Save remains available when automatic history is off.",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        "Shortcuts" -> {
        Text("If Android shows a separate Accessibility shortcut button on the edge of the screen: open Android Accessibility settings → Local Voice and switch off only Local Voice shortcut / Accessibility button. Keep Use Local Voice enabled for the input-field microphone. This system shortcut is managed by Android, not by the app.",style=MaterialTheme.typography.bodyMedium)
        TextButton(onClick={open(Settings.ACTION_ACCESSIBILITY_SETTINGS)}) {Text("Manage Android accessibility shortcut")}
        SettingRow("Add dictation shortcut","Launcher shortcut. Quick Settings tile can also be added from the system tile editor.",Icons.Outlined.AddToHomeScreen) {
            val shortcuts=context.getSystemService(ShortcutManager::class.java)
            if(shortcuts.isRequestPinShortcutSupported) shortcuts.requestPinShortcut(ShortcutInfo.Builder(context,"pinned-dictate").setShortLabel("Dictate locally").setIcon(AndroidIcon.createWithResource(context,R.drawable.ic_mic)).setIntent(Intent(context,MainActivity::class.java).setAction("dev.localvoice.DICTATE")).build(),null)
            else message("Long press the app icon to use its Dictate locally shortcut.")
        }
        Text("With a physical keyboard: Ctrl + Shift + Space while this app is open. Global keyboard interception is not enabled.",style=MaterialTheme.typography.bodyMedium)
        }
        "Privacy" -> {
        SettingRow("Android app permissions","Microphone and notification access",Icons.Outlined.AdminPanelSettings) {context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${context.packageName}")))}
        SettingRow("Privacy and open-source notices","Local processing, downloads, retention and licenses",Icons.Outlined.Lock) {privacy=true}
        Text("Local Voice · development build\nNo account, ads, transcript analytics or cloud recognition.",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        }
        Spacer(Modifier.height(20.dp))
    }
    if(permissionHelp) AlertDialog(onDismissRequest={permissionHelp=false},title={Text("Android restricted settings")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("Android can block sensitive access for apps installed from an APK. The warning about personal and financial information is Android's permission warning; Local Voice cannot dismiss it or enable access itself.")
        Text("If you trust this build: open App info below → the top-right ⋮ menu → Allow restricted settings, then complete Android's confirmation. Return here, read the accessibility disclosure, and enable Local Voice in Accessibility → downloaded/installed apps. Menu names vary by phone.")
        Text("Display over other apps is a separate setting. Return to that row and select Local Voice in Android's list. Allowing restricted settings does not grant overlay or accessibility access automatically.")
        Text("If the option is missing or still disabled, a device administrator, work profile, parental controls or your phone's security policy may be preventing it. Those restrictions must be resolved in device settings; reinstalling or changing the app cannot safely override them. In-app recording, Copy and export work without these two special permissions.")
    }},confirmButton={TextButton(onClick={runCatching {context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${context.packageName}")))}.onFailure {message("Open Android Settings → Apps → Local Voice.")}}) {Text("Open app info")}},dismissButton={TextButton(onClick={permissionHelp=false}) {Text("Close")}})
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
