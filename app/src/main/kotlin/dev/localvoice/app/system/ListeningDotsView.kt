package dev.localvoice.app.system

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.view.View
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

/** Audio-level feedback, not a simulated listening loop. Idle/silence draws five still dots. */
internal class ListeningDotsView(context: Context): View(context) {
    private val density=resources.displayMetrics.density
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect=RectF()
    private val weights=floatArrayOf(.5f,.8f,1f,.8f,.5f)
    private var target=0f
    private var shownLevel=0f
    private var listening=false
    private var lastFrame=0L
    private var motion=true

    init {importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO}

    fun update(level: Float,active: Boolean,color: Int) {
        listening=active
        target=if(active && level.isFinite() && level>.008f) level.coerceIn(0f,1f) else 0f
        motion=ValueAnimator.areAnimatorsEnabled()
        paint.color=color
        if(!active || !motion) {shownLevel=target;lastFrame=0L}
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now=SystemClock.uptimeMillis()
        val dt=if(lastFrame==0L) 16f else (now-lastFrame).coerceIn(1,100).toFloat()
        lastFrame=now
        if(motion) shownLevel+=(target-shownLevel)*(1f-exp(-dt/75f))
        if(shownLevel<.001f && target==0f) shownLevel=0f
        val radius=3f*density
        val stride=12f*density
        val left=width/2f-2*stride
        for(i in weights.indices) {
            val wave=if(motion) .75f+.25f*sin(now/115.0+i*.85).toFloat() else 0f
            val halfHeight=radius+if(motion) shownLevel*weights[i]*wave*9f*density else 0f
            paint.alpha=if(motion) 255 else if(target>0f) 255 else 140
            rect.set(left+i*stride-radius,height/2f-halfHeight,left+i*stride+radius,height/2f+halfHeight)
            canvas.drawRoundRect(rect,radius,radius,paint)
        }
        if(listening && motion && isShown && isAttachedToWindow && (target>0f || abs(shownLevel-target)>.001f)) postInvalidateOnAnimation()
    }

    override fun onDetachedFromWindow() {
        listening=false;target=0f;shownLevel=0f;lastFrame=0L
        super.onDetachedFromWindow()
    }
}
