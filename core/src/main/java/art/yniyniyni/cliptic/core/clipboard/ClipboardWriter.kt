package art.yniyniyni.cliptic.core.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri

object ClipboardWriter {
    fun copyUriToClipboard(context: Context, uri: Uri, label: String = "Screenshot") {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        val clip = ClipData.newUri(context.contentResolver, label, uri)
        clipboard.setPrimaryClip(clip)
    }
}
