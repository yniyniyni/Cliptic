package art.yniyniyni.cliptic.xposed.ipc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
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
            val deleted = context.contentResolver.delete(originalUri, null, null)
            log("silent original delete result=$deleted uri=$originalUri")
        }.onFailure { throwable ->
            log("silent original delete failed: ${throwable.javaClass.simpleName}: ${throwable.message}")
        }
    }
}
