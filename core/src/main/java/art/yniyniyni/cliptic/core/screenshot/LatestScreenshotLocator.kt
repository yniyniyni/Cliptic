package art.yniyniyni.cliptic.core.screenshot

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore

/** Locates the single most recent screenshot in MediaStore for on-demand copying. */
class LatestScreenshotLocator(private val context: Context) {

    /**
     * The most recent image whose RELATIVE_PATH is a Screenshots folder, or null.
     * Performs a synchronous MediaStore query — call off the main thread.
     */
    fun latest(): Uri? {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media.DATE_ADDED
        )
        val sort = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sort
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)
            while (cursor.moveToNext()) {
                val relativePath = cursor.getString(pathColumn).orEmpty()
                if (isScreenshotPath(relativePath)) {
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

    companion object {
        fun isScreenshotPath(relativePath: String): Boolean =
            relativePath.contains("Screenshots", ignoreCase = true)
    }
}
