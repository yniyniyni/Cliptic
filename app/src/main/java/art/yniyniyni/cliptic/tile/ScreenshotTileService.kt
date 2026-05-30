package art.yniyniyni.cliptic.tile

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import art.yniyniyni.cliptic.R
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
        // Reflect whether the service will actually run, not just the auto-copy pref: in
        // LSPosed-only mode auto-copy can be on while no service runs.
        val active = ClipticSettings.shouldRunScreenshotService(this)
        qsTile?.apply {
            state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = getString(R.string.app_name)
            updateTile()
        }
    }
}
