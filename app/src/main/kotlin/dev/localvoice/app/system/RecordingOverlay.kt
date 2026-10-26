package dev.localvoice.app.system

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.*
import android.widget.*
import dev.localvoice.app.graph
import dev.localvoice.core.SessionPhase
import dev.localvoice.core.SessionState

/** A non-focusable control bar: the editor/keyboard retain input focus during capture. */
class RecordingOverlay(private val context: Context,stop: () -> Unit,cancel: () -> Unit,copy: () -> Unit) {
    private val wm=context.getSystemService(WindowManager::class.java)
    private val density=context.resources.displayMetrics.density
    private fun dp(value: Int)=(value*density).toInt()
    private var attached=false
    private var phase=SessionPhase.IDLE
    private val label=TextView(context).apply {textSize=14f;setTypeface(typeface,Typeface.BOLD);gravity=Gravity.CENTER_VERTICAL}
    private val dots=ListeningDotsView(context)
    private val status=LinearLayout(context).apply {
        orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER_VERTICAL;minimumHeight=dp(56);setPadding(dp(12),dp(4),dp(12),dp(4))
        addView(dots,LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,dp(24)))
        addView(label,LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT))
    }
    private val stopButton=Button(context).apply {text="Stop";isAllCaps=false;minimumWidth=dp(56);minimumHeight=dp(48);setOnClickListener {if(phase==SessionPhase.RESULT) copy() else stop()};contentDescription="Stop recording and insert text"}
    private val cancelButton=Button(context).apply {text="Cancel";isAllCaps=false;minimumWidth=dp(64);minimumHeight=dp(48);setOnClickListener {cancel()}}
    private val bar=LinearLayout(context).apply {
        orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(4),dp(4),dp(8),dp(4))
        addView(status,LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f));addView(stopButton);addView(cancelButton);elevation=dp(4).toFloat()
    }
    private val params=WindowManager.LayoutParams(dp(320).coerceAtMost(context.resources.displayMetrics.widthPixels-dp(16)),WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.TRANSLUCENT).apply {gravity=Gravity.TOP or Gravity.START}
    init {installDrag()}
    @SuppressLint("ClickableViewAccessibility") // Drag-only label; actual actions use native accessible buttons.
    private fun installDrag() {
        var x=0f;var y=0f;var baseX=0;var baseY=0
        status.setOnTouchListener {_,event ->
            when(event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {x=event.rawX;y=event.rawY;baseX=params.x;baseY=params.y;true}
                MotionEvent.ACTION_MOVE -> {
                    val screen=context.resources.displayMetrics
                    params.x=(baseX+event.rawX-x).toInt().coerceIn(0,(screen.widthPixels-params.width).coerceAtLeast(0))
                    params.y=(baseY+event.rawY-y).toInt().coerceIn(dp(32),(screen.heightPixels-dp(120)).coerceAtLeast(dp(32)))
                    if(attached) runCatching {wm.updateViewLayout(bar,params)}
                    true
                }
                else -> true
            }
        }
    }
    fun show(state: SessionState): Boolean {
        val prefs=context.graph.preferences.value
        val dark=prefs.theme=="dark" || prefs.theme=="system" && context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK==android.content.res.Configuration.UI_MODE_NIGHT_YES
        val foreground=if(dark) Color.rgb(228,239,233) else Color.rgb(24,39,36)
        label.setTextColor(foreground)
        stopButton.backgroundTintList=android.content.res.ColorStateList.valueOf(if(dark) Color.rgb(130,216,192) else Color.rgb(0,107,93))
        stopButton.setTextColor(if(dark) Color.rgb(0,56,45) else Color.WHITE)
        cancelButton.backgroundTintList=android.content.res.ColorStateList.valueOf(if(dark) Color.rgb(59,76,67) else Color.rgb(212,223,217))
        cancelButton.setTextColor(foreground)
        bar.background=GradientDrawable().apply {cornerRadius=dp(16).toFloat();setColor(if(dark) Color.rgb(33,48,41) else Color.rgb(238,244,241))}
        phase=state.phase
        val recording=phase==SessionPhase.RECORDING
        dots.visibility=if(recording) View.VISIBLE else View.GONE
        dots.update(state.level,recording,if(dark) Color.rgb(130,216,192) else Color.rgb(0,107,93))
        label.textSize=if(recording) 12f else 14f
        label.contentDescription=if(recording) "Recording audio. ${playTime(state.elapsedMs)} of 10 minutes." else null
        label.text=when(phase) {
            SessionPhase.PREPARING -> "Starting microphone…"
            SessionPhase.RECORDING -> "${playTime(state.elapsedMs)} / 10:00"
            SessionPhase.RESULT -> state.message.ifBlank {"Couldn’t insert text. Copy to paste."}
            SessionPhase.ERROR -> state.message
            else -> "Transcribing…"
        }
        stopButton.text=if(phase==SessionPhase.RESULT) "Copy" else "Stop"
        stopButton.contentDescription=if(phase==SessionPhase.RESULT) "Copy dictation" else "Stop recording and insert text"
        stopButton.visibility=if(phase in listOf(SessionPhase.RECORDING,SessionPhase.RESULT)) View.VISIBLE else View.GONE
        cancelButton.text=if(phase in listOf(SessionPhase.RESULT,SessionPhase.ERROR)) "Done" else "Cancel"
        if(!attached) {
            val screen=context.resources.displayMetrics
            params.x=((screen.widthPixels-params.width)*prefs.bubbleX).toInt().coerceAtLeast(0)
            params.y=(screen.heightPixels*prefs.bubbleY).toInt().coerceIn(dp(32),(screen.heightPixels-dp(120)).coerceAtLeast(dp(32)))
            attached=runCatching {wm.addView(bar,params);true}.getOrDefault(false)
        }
        return attached
    }
    fun hide() {dots.update(0f,false,Color.TRANSPARENT);if(attached) runCatching {wm.removeView(bar)};attached=false}
    private fun playTime(ms: Long)="%d:%02d".format(ms/60_000,ms/1000%60)
}
