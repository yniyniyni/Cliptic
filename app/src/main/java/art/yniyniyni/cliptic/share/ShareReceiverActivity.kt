package art.yniyniyni.cliptic.share

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import art.yniyniyni.cliptic.R
import art.yniyniyni.cliptic.cleanup.OriginalScreenshotCleanup
import art.yniyniyni.cliptic.core.clipboard.ClipboardWriter
import art.yniyniyni.cliptic.core.screenshot.ScreenshotFileManager
import art.yniyniyni.cliptic.settings.ClipticSettings

class ShareReceiverActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ClipticSettings.ensureDefaults(this)
        val streamUri = intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        if (streamUri != null) {
            val fileManager = ScreenshotFileManager(this)
            val cachedUri = fileManager.cacheScreenshot(streamUri)
            if (cachedUri != null) {
                ClipboardWriter.copyUriToClipboard(this, cachedUri)
                Toast.makeText(this, R.string.screenshot_copied, Toast.LENGTH_SHORT).show()
                fileManager.scheduleCleanup(cachedUri)
                if (
                    ClipticSettings.prefs(this)
                        .getBoolean(ClipticSettings.KEY_REMOVE_ORIGINAL_AFTER_COPY, true) &&
                    OriginalScreenshotCleanup.isLikelyScreenshotOriginal(this, streamUri)
                ) {
                    OriginalScreenshotCleanup.requestTrashPrompt(this, streamUri)
                }
            }
        }
        finish()
    }
}
