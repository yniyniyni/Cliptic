package art.yniyniyni.cliptic.core.screenshot

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore

class ScreenshotDetector(
    private val context: Context,
    private val onScreenshotDetected: (uri: Uri) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var observer: ContentObserver? = null
    private var lastProcessedUri: Uri? = null
    private var lastProcessedAtMs: Long = 0L

    fun start() {
        if (observer != null) return
        observer = object : ContentObserver(mainHandler) {
            override fun onChange(selfChange: Boolean) {
                handleChange()
            }

            override fun onChange(selfChange: Boolean, uri: Uri?) {
                handleChange()
            }
        }.also {
            context.contentResolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                it
            )
        }
    }

    fun stop() {
        observer?.let(context.contentResolver::unregisterContentObserver)
        observer = null
        mainHandler.removeCallbacksAndMessages(null)
    }

    private fun handleChange() {
        val screenshotUri = queryLatestScreenshot() ?: return
        val now = System.currentTimeMillis()
        if (lastProcessedUri == screenshotUri && now - lastProcessedAtMs < DEDUPE_WINDOW_MS) {
            return
        }
        lastProcessedUri = screenshotUri
        lastProcessedAtMs = now
        mainHandler.postDelayed({ onScreenshotDetected(screenshotUri) }, PROCESSING_DELAY_MS)
    }

    private fun queryLatestScreenshot(): Uri? {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media.DATE_ADDED
        )
        val selection = "${MediaStore.Images.Media.DATE_ADDED} >= ?"
        val cutoffSeconds = (System.currentTimeMillis() / 1000L) - RECENT_WINDOW_SECONDS
        val sort = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            arrayOf(cutoffSeconds.toString()),
            sort
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            while (cursor.moveToNext()) {
                val relativePath = cursor.getString(pathColumn).orEmpty()
                val dateAdded = cursor.getLong(dateColumn)
                if (
                    relativePath.contains("Screenshots", ignoreCase = true) &&
                    (System.currentTimeMillis() / 1000L) - dateAdded <= RECENT_WINDOW_SECONDS
                ) {
                    val id = cursor.getLong(idColumn)
                    return ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id
                    )
                }
            }
        }
        return null
    }

    private companion object {
        const val PROCESSING_DELAY_MS = 500L
        const val DEDUPE_WINDOW_MS = 2_000L
        const val RECENT_WINDOW_SECONDS = 5L
    }
}
