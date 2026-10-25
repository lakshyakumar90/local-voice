package dev.localvoice.app.system

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.*
import android.widget.ImageButton
import dev.localvoice.app.R
import dev.localvoice.app.graph
import kotlinx.coroutines.launch
import kotlin.math.abs

class BubbleOverlay(private val context: Context,private val record: () -> Unit) {
    private val wm=context.getSystemService(WindowManager::class.java)
    private val density=context.resources.displayMetrics.density
    private val size=(56*density).toInt()
    private var attached=false
    private val button=ImageButton(context).apply {
        setImageResource(R.drawable.ic_mic); imageTintList=android.content.res.ColorStateList.valueOf(Color.rgb(0,107,93))
        background=GradientDrawable().apply { shape=GradientDrawable.OVAL; setColor(Color.rgb(216,239,232)); setStroke((density*1).toInt(),Color.rgb(105,127,119)) }
        contentDescription="Start local dictation. Drag to move. Long press to hide."
        elevation=4*density
        setOnClickListener { record() }
        setOnLongClickListener { context.graph.scope.launch { context.graph.settings.bubble(false) }; true }
    }
    private val params=WindowManager.LayoutParams(size,size,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,android.graphics.PixelFormat.TRANSLUCENT).apply { gravity=Gravity.TOP or Gravity.START }
    init {
        var downX=0f;var downY=0f;var baseX=0;var baseY=0;var moved=false
        button.setOnTouchListener { _,event ->
            when(event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX=event.rawX;downY=event.rawY;baseX=params.x;baseY=params.y;moved=false;false }
                MotionEvent.ACTION_MOVE -> {
                    if(abs(event.rawX-downX)+abs(event.rawY-downY)>ViewConfiguration.get(context).scaledTouchSlop) moved=true
                    if(moved) {
                        button.cancelLongPress(); button.isPressed=false
                        val screen=context.resources.displayMetrics
                        params.x=(baseX+event.rawX-downX).toInt().coerceIn(0,(screen.widthPixels-size).coerceAtLeast(0))
                        params.y=(baseY+event.rawY-downY).toInt().coerceIn((48*density).toInt(),(screen.heightPixels-size-80*density).toInt().coerceAtLeast((48*density).toInt()))
                        if(attached) wm.updateViewLayout(button,params)
                    }; moved
                }
                MotionEvent.ACTION_UP -> { if(moved) { val screen=context.resources.displayMetrics; context.graph.scope.launch { context.graph.settings.bubblePosition(params.x.toFloat()/(screen.widthPixels-size).coerceAtLeast(1),params.y.toFloat()/screen.heightPixels) } }; moved }
                MotionEvent.ACTION_CANCEL -> { moved=false; true }
                else -> false
            }
        }
    }
    fun visible(show: Boolean) {
        if(show && !attached) {
            val screen=context.resources.displayMetrics; val prefs=context.graph.preferences.value
            params.x=((screen.widthPixels-size-8*density)*prefs.bubbleX).toInt().coerceAtLeast(0)
            params.y=(screen.heightPixels*prefs.bubbleY).toInt().coerceIn((48*density).toInt(),(screen.heightPixels-size-80*density).toInt().coerceAtLeast((48*density).toInt()))
            runCatching { wm.addView(button,params); attached=true }
        } else if(!show && attached) { runCatching { wm.removeView(button) };attached=false }
    }
    fun destroy() = visible(false)
}
