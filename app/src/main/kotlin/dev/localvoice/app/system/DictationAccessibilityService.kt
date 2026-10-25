package dev.localvoice.app.system

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.text.Spanned
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import dev.localvoice.app.graph
import dev.localvoice.app.ui.RecordingActivity
import dev.localvoice.core.TextInsertion
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.security.MessageDigest
import java.util.UUID

private data class Target(val token: String,val pkg: String,val window: Int,val viewId: String?,val nodeKey: Int,val klass: String,val bounds: Rect,val digest: ByteArray,val salt: String,val start: Int,val end: Int,val generation: Long,val createdAt: Long = android.os.SystemClock.elapsedRealtime())
class DictationAccessibilityService : AccessibilityService() {
    companion object {
        var connected: DictationAccessibilityService? = null; private set
        val isConnected = MutableStateFlow(false)
    }
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private lateinit var bubble: BubbleOverlay
    private var target: Target?=null
    private var generation=0L
    private var lastExternal: String?=null
    private var refresh: Job?=null
    override fun onServiceConnected() {
        connected=this; isConnected.value=true; bubble=BubbleOverlay(this,::record)
        scope.launch { graph.preferences.collect { updateBubble() } }
        scope.launch { graph.session.state.collect { updateBubble() } }
        updateBubble()
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg=event?.packageName?.toString()
        if(pkg != null && pkg != packageName && event.eventType in listOf(AccessibilityEvent.TYPE_VIEW_FOCUSED,AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)) {
            val node=focus()
            val key=node?.let { "$pkg:${it.windowId}:${it.viewIdResourceName}:${bounds(it)}" }
            if(key != null && lastExternal != null && lastExternal != key) generation++
            if(key != null) lastExternal=key
        }
        refresh?.cancel(); refresh=scope.launch { delay(100); updateBubble() }
    }
    private fun focus(): AccessibilityNodeInfo? = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
    private fun bounds(n: AccessibilityNodeInfo)=Rect().also(n::getBoundsInScreen)
    private fun eligible(n: AccessibilityNodeInfo?): Boolean {
        if(n == null || !n.isEditable || !n.isFocused || !n.isEnabled || !n.isVisibleToUser || n.isPassword) return false
        val pkg=n.packageName?.toString() ?: return false
        if(pkg==packageName || pkg=="com.android.systemui" || pkg.contains("permissioncontroller") || pkg.contains("packageinstaller") || pkg in graph.preferences.value.excludedApps) return false
        val variation=n.inputType and InputType.TYPE_MASK_VARIATION
        if(variation in listOf(InputType.TYPE_TEXT_VARIATION_PASSWORD,InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD) || (n.inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_NUMBER && variation==InputType.TYPE_NUMBER_VARIATION_PASSWORD)) return false
        val text=n.text
        if(text is Spanned && text.getSpans(0,text.length,Any::class.java).any { text.getSpanFlags(it) and Spanned.SPAN_COMPOSING != 0 }) return false
        return !getSystemService(KeyguardManager::class.java).isKeyguardLocked
    }
    private fun updateBubble() {
        if(!::bubble.isInitialized) return
        val prefs=graph.preferences.value
        bubble.visible(prefs.bubble && prefs.accessibilityConsent && Settings.canDrawOverlays(this) && eligible(focus()) && !graph.session.busy)
    }
    private fun digest(text: String,salt: String)=MessageDigest.getInstance("SHA-256").digest((salt+text).toByteArray(Charsets.UTF_8))
    private fun record() {
        val node=focus()
        if(!eligible(node)) { updateBubble(); return }
        val n=node!!; val text=n.text?.toString()
        val salt=UUID.randomUUID().toString(); val token=UUID.randomUUID().toString()
        val identified=n.viewIdResourceName!=null || android.os.Build.VERSION.SDK_INT>=33 && n.uniqueId!=null
        target=if(identified && text != null && n.textSelectionStart>=0 && n.textSelectionEnd>=n.textSelectionStart) Target(token,n.packageName.toString(),n.windowId,n.viewIdResourceName,n.hashCode(),n.className.toString(),bounds(n),digest(text,salt),salt,n.textSelectionStart,n.textSelectionEnd,generation) else null
        bubble.visible(false)
        runCatching { startActivity(Intent(this,RecordingActivity::class.java).putExtra("target",target?.token).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { graph.session.fail("Open Local Voice to start recording. Android blocked the floating entry point.") }
    }
    fun deliver(token: String,text: String,onResult: (String?) -> Unit) {
        val saved=target
        target=null // A target is single use; no duplicate action after an uncertain acknowledgement.
        scope.launch {
            if(saved==null || saved.token!=token || android.os.SystemClock.elapsedRealtime()-saved.createdAt>300_000) { onResult("The original field is no longer available. Copy your text instead."); return@launch }
            repeat(20) {
                delay(100)
                val node=focus()
                if(node?.packageName?.toString()==packageName || node==null) return@repeat
                if(!eligible(node) || generation!=saved.generation || node.packageName.toString()!=saved.pkg || node.windowId!=saved.window || node.viewIdResourceName!=saved.viewId || node.hashCode()!=saved.nodeKey || node.className.toString()!=saved.klass || bounds(node)!=saved.bounds) {
                    onResult("The original field changed. Copy your text to paste it safely."); return@launch
                }
                val original=node.text?.toString()
                if(original==null || !digest(original,saved.salt).contentEquals(saved.digest) || node.textSelectionStart!=saved.start || node.textSelectionEnd!=saved.end) { onResult("The field's text or selection changed. Copy your text instead."); return@launch }
                val replacement=TextInsertion.replacement(original,saved.start,saved.end,text)
                if(replacement==null || node.actionList.none { it.id==AccessibilityNodeInfo.ACTION_SET_TEXT }) { onResult("This editor does not support safe insertion. Copy your text instead."); return@launch }
                // A real edit can still race this platform action: verify and never automatically retry.
                val ok=node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,replacement) })
                delay(100)
                if(ok && node.refresh() && node.text?.toString()==replacement) {
                    val cursor=(replacement.length-(original.length-saved.end)).coerceIn(0,replacement.length)
                    node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION,Bundle().apply {putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT,cursor);putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT,cursor)})
                    onResult(null)
                }
                else onResult("Check the field before pasting; your text may already be there.")
                return@launch
            }
            onResult("Could not return to the original field. Copy your text instead.")
        }
    }
    fun currentPackage(): String? = focus()?.packageName?.toString()?.takeIf { it != packageName }
    override fun onInterrupt() { target=null; generation++; if(::bubble.isInitialized) bubble.visible(false); graph.session.cancel() }
    override fun onDestroy() { if(::bubble.isInitialized) bubble.destroy(); connected=null; isConnected.value=false; scope.cancel(); super.onDestroy() }
}
