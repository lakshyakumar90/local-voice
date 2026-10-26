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
import dev.localvoice.core.SessionPhase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.security.MessageDigest
import java.util.UUID

private data class Target(val token: String,val pkg: String,val window: Int,val viewId: String?,val node: AccessibilityNodeInfo,val klass: String,val digest: ByteArray,val salt: String,val start: Int,val end: Int,val generation: Long,val createdAt: Long = android.os.SystemClock.elapsedRealtime())
class DictationAccessibilityService : AccessibilityService() {
    companion object {
        var connected: DictationAccessibilityService? = null; private set
        val isConnected = MutableStateFlow(false)
    }
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private lateinit var bubble: BubbleOverlay
    private lateinit var controls: RecordingOverlay
    private var overlayFlow: String?=null
    private var detached=false
    private var overlayStarted=false
    private var delivering=false
    private var target: Target?=null
    private var targetExpiry: Job?=null
    private var generation=0L
    private var lastExternal: String?=null
    private var refresh: Job?=null
    override fun onServiceConnected() {
        connected=this; isConnected.value=true; bubble=BubbleOverlay(this,::record)
        controls=RecordingOverlay(this,{graph.session.stop()},{graph.session.cancel()})
        scope.launch { graph.preferences.collect { updateBubble() } }
        scope.launch { graph.session.state.collect { state ->
            if(overlayFlow!=null && state.phase in listOf(SessionPhase.PREPARING,SessionPhase.RECORDING,SessionPhase.PROCESSING)) overlayStarted=true
            updateBubble()
            if(detached && state.phase==SessionPhase.RESULT && !delivering) {
                detached=false;overlayStarted=false;overlayFlow=null;delivering=true;controls.hide();bubble.visible(false)
                deliver(target?.token.orEmpty(),state.transcript) {failure ->
                    delivering=false
                    if(failure==null) {
                        if(graph.session.state.value===state) graph.session.reset()
                        android.widget.Toast.makeText(this@DictationAccessibilityService,"Text inserted",android.widget.Toast.LENGTH_SHORT).show()
                    } else {graph.session.resultMessage(failure);showFallback()}
                    updateBubble()
                }
            } else if(detached && state.phase==SessionPhase.ERROR) {
                detached=false;overlayStarted=false;overlayFlow=null;controls.hide();clearTarget();showFallback()
            } else if(state.phase==SessionPhase.IDLE && overlayStarted) {detached=false;overlayStarted=false;overlayFlow=null;controls.hide();clearTarget()}
        } }
        updateBubble()
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg=event?.packageName?.toString()
        if(pkg != null && pkg != packageName && event.eventType in listOf(AccessibilityEvent.TYPE_VIEW_FOCUSED,AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)) {
            val node=focus()
            val key=node?.let { "$pkg:${it.windowId}:${it.viewIdResourceName}:${it.hashCode()}" }
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
        if(detached && graph.session.busy && !Settings.canDrawOverlays(this)) {
            graph.session.cancel();graph.session.fail("Overlay access was removed. Recording stopped; enable it before trying again.")
            detached=false;controls.hide();clearTarget();showFallback();return
        }
        val active=detached && graph.session.busy && !getSystemService(KeyguardManager::class.java).isKeyguardLocked
        if(active) controls.show(graph.session.state.value) else controls.hide()
        bubble.visible(!active && !delivering && prefs.bubble && prefs.accessibilityConsent && Settings.canDrawOverlays(this) && eligible(focus()) && !graph.session.busy)
    }
    fun detachRecording(token: String): Boolean {
        if(token!=overlayFlow || graph.session.state.value.phase!=SessionPhase.RECORDING || !Settings.canDrawOverlays(this)) return false
        if(!controls.show(graph.session.state.value)) return false
        detached=true;updateBubble();return true
    }
    private fun showFallback() {runCatching {startActivity(Intent(this,RecordingActivity::class.java).putExtra("showResult",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}}
    @Suppress("DEPRECATION") private fun clearTarget() {targetExpiry?.cancel();targetExpiry=null;target?.node?.recycle();target=null}
    private fun digest(text: String,salt: String)=MessageDigest.getInstance("SHA-256").digest((salt+text).toByteArray(Charsets.UTF_8))
    @Suppress("DEPRECATION") private fun record() {
        if(delivering || graph.session.busy) return
        val node=focus()
        if(!eligible(node)) { updateBubble(); return }
        clearTarget();overlayFlow=null;detached=false;overlayStarted=false
        graph.session.reset() // A new mic gesture must not reopen an earlier result/error.
        val n=node!!; val text=n.text?.toString()
        lastExternal="${n.packageName}:${n.windowId}:${n.viewIdResourceName}:${n.hashCode()}"
        val salt=UUID.randomUUID().toString(); val token=UUID.randomUUID().toString()
        clearTarget()
        target=if(text != null && n.textSelectionStart>=0 && n.textSelectionEnd>=n.textSelectionStart) Target(token,n.packageName.toString(),n.windowId,n.viewIdResourceName,AccessibilityNodeInfo.obtain(n),n.className.toString(),digest(text,salt),salt,n.textSelectionStart,n.textSelectionEnd,generation) else null
        overlayFlow=token;detached=false
        targetExpiry=scope.launch {delay(300_000);if(target?.token==token) clearTarget()}
        bubble.visible(false)
        runCatching { startActivity(Intent(this,RecordingActivity::class.java).putExtra("target",target?.token).putExtra("overlayFlow",token).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { overlayFlow=null;clearTarget();graph.session.fail("Open Local Voice to start recording. Android blocked the floating entry point.") }
    }
    fun deliver(token: String,text: String,onResult: (String?) -> Unit) {
        val saved=target
        val expectedResult=graph.session.state.value
        overlayFlow=null
        target=null // A target is single use; no duplicate action after an uncertain acknowledgement.
        scope.launch {try {
            if(saved==null || saved.token!=token || android.os.SystemClock.elapsedRealtime()-saved.createdAt>300_000) { onResult("The original field is no longer available. Copy your text instead."); return@launch }
            repeat(20) {
                delay(100)
                if(graph.session.state.value!==expectedResult) {onResult("This dictation changed before insertion. Copy the current result instead.");return@launch}
                val node=focus()
                if(node?.packageName?.toString()==packageName || node==null) return@repeat
                if(!eligible(node) || generation!=saved.generation || node.packageName.toString()!=saved.pkg || node.windowId!=saved.window || node.viewIdResourceName!=saved.viewId || node!=saved.node || node.className.toString()!=saved.klass) {
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
        } finally {@Suppress("DEPRECATION") saved?.node?.recycle()} }
    }
    fun currentPackage(): String? = focus()?.packageName?.toString()?.takeIf { it != packageName }
    override fun onInterrupt() {clearTarget();generation++;if(::bubble.isInitialized) bubble.visible(false);if(::controls.isInitialized) controls.hide();graph.session.cancel()}
    override fun onDestroy() {if(detached && graph.session.busy) graph.session.cancel();clearTarget();if(::controls.isInitialized) controls.hide();if(::bubble.isInitialized) bubble.destroy();connected=null;isConnected.value=false;scope.cancel();super.onDestroy()}
}
