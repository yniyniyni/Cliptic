package art.yniyniyni.cliptic.cleanup

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import art.yniyniyni.cliptic.AppActions

class RemoveOriginalActivity : ComponentActivity() {
    private var originalUri: Uri? = null
    private var requestId: String? = null

    private val launcher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        OriginalScreenshotCleanup.onTrashPromptResult(
            this,
            requestId,
            originalUri,
            result.resultCode == Activity.RESULT_OK
        )
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestId = intent.getStringExtra(AppActions.EXTRA_REQUEST_ID)
        originalUri = intent.getStringExtra(AppActions.EXTRA_SCREENSHOT_URI)?.let(Uri::parse)
        val uri = originalUri
        if (uri == null) {
            finish()
            return
        }

        runCatching {
            val sender = MediaStore.createTrashRequest(contentResolver, listOf(uri), true)
            launcher.launch(IntentSenderRequest.Builder(sender.intentSender).build())
        }.onFailure {
            OriginalScreenshotCleanup.onTrashPromptResult(this, requestId, uri, removed = false)
            finish()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}
