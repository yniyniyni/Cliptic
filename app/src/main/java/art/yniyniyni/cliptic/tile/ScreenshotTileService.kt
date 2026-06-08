package art.yniyniyni.cliptic.tile

import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import art.yniyniyni.cliptic.R
import art.yniyniyni.cliptic.cleanup.OriginalScreenshotCleanup
import art.yniyniyni.cliptic.core.clipboard.ClipboardWriter
import art.yniyniyni.cliptic.core.screenshot.LatestScreenshotLocator
import art.yniyniyni.cliptic.core.screenshot.ScreenshotFileManager
import art.yniyniyni.cliptic.settings.ClipticSettings

/**
 * One-tap action tile: copy the most recent screenshot to the clipboard and, when
 * remove_original_after_copy is on, remove the original. The auto-copy on/off toggle
 * lives in the in-app settings, not here.
 */
class ScreenshotTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = getString(R.string.app_name)
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        if (isLocked) {
            unlockAndRun { copyLatestScreenshot() }
        } else {
            copyLatestScreenshot()
        }
    }

    private fun copyLatestScreenshot() {
        val appContext = applicationContext
        val main = Handler(Looper.getMainLooper())
        Thread {
            val sourceUri = LatestScreenshotLocator(appContext).latest()
            if (sourceUri == null) {
                main.post { toast(R.string.tile_no_screenshot) }
                return@Thread
            }
            val fileManager = ScreenshotFileManager(appContext)
            val cachedUri = fileManager.cacheScreenshot(sourceUri)
            val shouldRemoveOriginal =
                ClipticSettings.prefs(appContext)
                    .getBoolean(ClipticSettings.KEY_REMOVE_ORIGINAL_AFTER_COPY, true) &&
                    OriginalScreenshotCleanup.isLikelyScreenshotOriginal(appContext, sourceUri)
            main.post {
                if (cachedUri == null) {
                    toast(R.string.tile_no_screenshot)
                    return@post
                }
                ClipboardWriter.copyUriToClipboard(appContext, cachedUri)
                ClipticSettings.recordCopy(appContext)
                toast(R.string.screenshot_copied)
                fileManager.scheduleCleanup(cachedUri, ClipticSettings.cacheDurationMs(appContext))
                if (shouldRemoveOriginal) {
                    OriginalScreenshotCleanup.requestTrashPrompt(appContext, sourceUri)
                }
            }
        }.start()
    }

    private fun toast(resId: Int) {
        Toast.makeText(applicationContext, resId, Toast.LENGTH_SHORT).show()
    }
}
