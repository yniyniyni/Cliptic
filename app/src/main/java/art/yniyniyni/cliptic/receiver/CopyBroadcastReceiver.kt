package art.yniyniyni.cliptic.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import art.yniyniyni.cliptic.AppActions
import art.yniyniyni.cliptic.R
import art.yniyniyni.cliptic.core.clipboard.ClipboardWriter
import art.yniyniyni.cliptic.core.screenshot.ScreenshotFileManager
import art.yniyniyni.cliptic.settings.ClipticSettings

class CopyBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AppActions.ACTION_COPY_SCREENSHOT) return
        val sourceUri = intent.getStringExtra(AppActions.EXTRA_SCREENSHOT_URI)?.let(Uri::parse) ?: return
        val secret = intent.getStringExtra(AppActions.EXTRA_SECRET) ?: return
        if (secret != ClipticSettings.xposedSecret(context)) return

        val pendingResult = goAsync()
        Thread {
            try {
                val fileManager = ScreenshotFileManager(context)
                val cachedUri = fileManager.cacheScreenshot(sourceUri)
                if (cachedUri != null) {
                    Handler(Looper.getMainLooper()).post {
                        ClipboardWriter.copyUriToClipboard(context, cachedUri)
                        Toast.makeText(context, R.string.screenshot_copied, Toast.LENGTH_SHORT).show()
                        fileManager.scheduleCleanup(cachedUri)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }.start()
    }
}
