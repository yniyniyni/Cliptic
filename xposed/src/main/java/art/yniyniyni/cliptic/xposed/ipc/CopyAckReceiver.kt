package art.yniyniyni.cliptic.xposed.ipc

import android.content.ContentValues
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import art.yniyniyni.cliptic.xposed.AppProtocol

class CopyAckReceiver(
    private val expectedSecretProvider: () -> String?,
    private val log: (String) -> Unit
) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AppProtocol.ACTION_COPY_SCREENSHOT_ACK) return
        val originalUri = intent.getStringExtra(AppProtocol.EXTRA_SCREENSHOT_URI)?.let(Uri::parse) ?: return
        val secret = intent.getStringExtra(AppProtocol.EXTRA_SECRET) ?: return
        val expectedSecret = expectedSecretProvider()
        if (expectedSecret == null || secret != expectedSecret) {
            log("copy ACK ignored: secret mismatch")
            return
        }

        runCatching {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_TRASHED, 1)
            }
            val updated = context.contentResolver.update(originalUri, values, null, null)
            log("silent original trash result=$updated uri=$originalUri")
        }.onFailure { throwable ->
            log("silent original trash failed: ${throwable.javaClass.simpleName}: ${throwable.message}")
        }
    }
}
