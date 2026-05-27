package art.yniyniyni.cliptic.core.screenshot

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException

class ScreenshotFileManager(private val context: Context) {
    private val screenshotsDir: File
        get() = File(context.cacheDir, CLIPBOARD_CACHE_DIR)

    fun cacheScreenshot(sourceUri: Uri): Uri? {
        return try {
            val outputDir = screenshotsDir.apply {
                mkdirs()
                File(this, ".nomedia").createNewFile()
            }
            val outputFile = File(outputDir, "${System.currentTimeMillis()}.png")
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                outputFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return null

            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                outputFile
            )
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    fun scheduleCleanup(uri: Uri, delayMs: Long = DEFAULT_CACHE_DURATION_MS) {
        Handler(Looper.getMainLooper()).postDelayed({
            uri.lastPathSegment
                ?.let { File(screenshotsDir, it).takeIf(File::exists) }
                ?.delete()
            cleanupExpired(delayMs)
        }, delayMs)
    }

    fun cleanupExpired(maxAgeMs: Long = DEFAULT_CACHE_DURATION_MS) {
        val cutoff = System.currentTimeMillis() - maxAgeMs
        screenshotsDir.listFiles()
            ?.filter { it.isFile && it.name != ".nomedia" && it.lastModified() < cutoff }
            ?.forEach { it.delete() }
    }

    private companion object {
        const val CLIPBOARD_CACHE_DIR = "cliptic_clipboard"
        const val DEFAULT_CACHE_DURATION_MS = 3_600_000L
    }
}
