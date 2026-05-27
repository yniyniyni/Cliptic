package art.yniyniyni.cliptic.tile

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import art.yniyniyni.cliptic.settings.ClipticSettings

class ScreenshotTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        val prefs = ClipticSettings.prefs(this)
        val enabled = !prefs.getBoolean(ClipticSettings.KEY_AUTO_COPY_ENABLED, true)
        ClipticSettings.setAutoCopyEnabled(this, enabled)
        updateTile()
    }

    private fun updateTile() {
        val enabled = ClipticSettings.prefs(this).getBoolean(ClipticSettings.KEY_AUTO_COPY_ENABLED, true)
        qsTile?.apply {
            state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = "Cliptic"
            updateTile()
        }
    }
}
