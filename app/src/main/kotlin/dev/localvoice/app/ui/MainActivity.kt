package dev.localvoice.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.localvoice.app.graph

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);enableEdgeToEdge()
        setContent { val prefs=graph.preferences.collectAsStateWithLifecycle().value; LocalVoiceTheme(prefs.theme) { AppScreen(graph,::record) } }
        if(intent.action=="dev.localvoice.DICTATE" && savedInstanceState==null) window.decorView.post { record() }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); if(intent.action=="dev.localvoice.DICTATE") record() }
    private fun record() { startActivity(Intent(this,RecordingActivity::class.java)) }
    override fun onKeyDown(keyCode: Int,event: KeyEvent): Boolean {
        if(keyCode==KeyEvent.KEYCODE_SPACE && event.isCtrlPressed && event.isShiftPressed) {
            if(event.repeatCount==0) record()
            return true
        }
        return super.onKeyDown(keyCode,event)
    }
    override fun onKeyShortcut(keyCode: Int,event: KeyEvent): Boolean {
        if(keyCode==KeyEvent.KEYCODE_SPACE && event.isCtrlPressed && event.isShiftPressed) { if(event.repeatCount==0) record();return true }
        return super.onKeyShortcut(keyCode,event)
    }
}
