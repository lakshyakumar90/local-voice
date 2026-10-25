package dev.localvoice.app.system

import android.app.PendingIntent
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dev.localvoice.app.ui.RecordingActivity

class DictationTileService : TileService() {
    override fun onStartListening() { qsTile?.apply { state=Tile.STATE_INACTIVE; label="Dictate locally"; updateTile() } }
    override fun onClick() {
        if(isLocked) {unlockAndRun {launchDictation()};return}
        launchDictation()
    }
    private fun launchDictation() {
        val intent=Intent(this,RecordingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if(Build.VERSION.SDK_INT>=34) startActivityAndCollapse(PendingIntent.getActivity(this,55,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        else launchBeforeAndroid14(intent)
    }
    // The PendingIntent overload does not exist before API 34. The caller gates this fallback.
    @SuppressLint("StartActivityAndCollapseDeprecated")
    @Suppress("DEPRECATION")
    private fun launchBeforeAndroid14(intent: Intent) { startActivityAndCollapse(intent) }
}
