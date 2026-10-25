package dev.localvoice.app.audio

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import dev.localvoice.app.R
import dev.localvoice.app.graph
import dev.localvoice.app.ui.RecordingActivity
import kotlinx.coroutines.*

class RecordingService : Service() {
    companion object { const val START="start"; const val STOP="stop"; const val CANCEL="cancel"; private const val ID=41 }
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private lateinit var recorder: LocalRecorder
    private var capturing=false
    private var stopping=false
    private val screenReceiver=object : BroadcastReceiver() { override fun onReceive(context: Context?,intent: Intent?) { graph.session.cancel() } }
    override fun onCreate() {
        super.onCreate(); recorder=LocalRecorder(this)
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("recording","Active recording",NotificationManager.IMPORTANCE_LOW))
        registerReceiver(screenReceiver,IntentFilter(Intent.ACTION_SCREEN_OFF))
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?,flags: Int,startId: Int): Int {
        when(intent?.action) { START -> begin(); STOP -> finish(); CANCEL -> graph.session.cancel(); else -> stopSelf() }
        return START_NOT_STICKY
    }
    private fun begin() {
        if(capturing || stopping) return
        try {
            val open=PendingIntent.getActivity(this,0,Intent(this,RecordingActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            fun action(name: String,request: Int)=PendingIntent.getService(this,request,Intent(this,RecordingService::class.java).setAction(name),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val notification=NotificationCompat.Builder(this,"recording").setSmallIcon(R.drawable.ic_mic).setContentTitle("Recording on this phone")
                .setContentText("Tap Stop to transcribe locally").setOngoing(true).setContentIntent(open)
                .addAction(0,"Stop",action(STOP,1)).addAction(0,"Cancel",action(CANCEL,2)).build()
            ServiceCompat.startForeground(this,ID,notification,if(android.os.Build.VERSION.SDK_INT>=30) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0)
            recorder.start(scope,{elapsed,level -> graph.scope.launch { graph.session.recording(); graph.session.level(elapsed,level) } },{ graph.scope.launch { finish() } },{ message -> graph.scope.launch {graph.session.cancel();graph.session.fail(message)} },graph.session::audioFrame)
            capturing=true
        } catch(e: Exception) { graph.session.cancel(); graph.session.fail(e.message ?: "Microphone access is unavailable."); stopSelf() }
    }
    private fun finish() {
        if(!capturing || stopping) return
        stopping=true
        scope.launch {
            try { val clip=recorder.stop(); capturing=false; ServiceCompat.stopForeground(this@RecordingService,ServiceCompat.STOP_FOREGROUND_REMOVE); graph.session.process(clip) }
            catch(e: Exception) { graph.session.fail(e.message ?: "Recording was interrupted.") }
            finally { stopSelf() }
        }
    }
    override fun onDestroy() {
        runCatching { unregisterReceiver(screenReceiver) }
        if(capturing && !stopping) graph.scope.launch { recorder.stop(discard=true) }
        ServiceCompat.stopForeground(this,ServiceCompat.STOP_FOREGROUND_REMOVE)
        scope.cancel(); super.onDestroy()
    }
}
