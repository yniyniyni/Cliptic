package art.yniyniyni.cliptic.cleanup

import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import art.yniyniyni.cliptic.AppActions
import art.yniyniyni.cliptic.R
import art.yniyniyni.cliptic.settings.ClipticSettings
import java.util.concurrent.TimeUnit

object OriginalScreenshotCleanup {
    private const val TAG = "ClipticCleanup"
    private const val CHANNEL_ID = "screenshot_cleanup"
    private const val NOTIFICATION_ID = 10_000
    private const val UNIQUE_WORK_NAME = "pending_original_trash"
    private val FAST_RETRY_DELAYS_MS = longArrayOf(0L, 300L, 900L, 2_000L, 5_000L)
    private val retryHandler by lazy {
        Handler(HandlerThread("ClipticOriginalTrash").apply { start() }.looper)
    }

    fun requestTrashPrompt(context: Context, originalUri: Uri) {
        val appContext = context.applicationContext
        val requestId = System.currentTimeMillis().toString()
        savePending(appContext, originalUri, requestId)

        if (canTrashSilently(appContext)) {
            showCleanupInProgress(appContext)
            scheduleFastTrashRetries(appContext, originalUri, requestId)
            enqueuePendingTrashWork(appContext)
        } else {
            showFallbackNotification(appContext, originalUri, requestId)
        }
    }

    fun launchPendingPrompt(context: Context) {
        val prefs = ClipticSettings.prefs(context)
        val originalUri = prefs.getString(ClipticSettings.KEY_PENDING_ORIGINAL_URI, null)?.let(Uri::parse)
        val requestId = prefs.getString(ClipticSettings.KEY_PENDING_ORIGINAL_REQUEST_ID, null)
        if (originalUri != null && requestId != null) {
            if (tryTrashOriginal(context.applicationContext, originalUri)) {
                clearPending(context.applicationContext)
                return
            }
            context.startActivity(promptIntent(context.applicationContext, originalUri, requestId))
        }
    }

    fun onTrashPromptResult(context: Context, requestId: String?, originalUri: Uri?, removed: Boolean) {
        if (requestId == null || originalUri == null) return
        if (removed) {
            clearPending(context)
        } else {
            savePending(context, originalUri, requestId)
            showFallbackNotification(context, originalUri, requestId)
        }
    }

    fun pendingOriginalUri(context: Context): Uri? {
        return ClipticSettings.prefs(context)
            .getString(ClipticSettings.KEY_PENDING_ORIGINAL_URI, null)
            ?.let(Uri::parse)
    }

    fun isLikelyScreenshotOriginal(context: Context, originalUri: Uri): Boolean {
        if (originalUri.authority != MediaStore.AUTHORITY) return false
        val projection = arrayOf(
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.DISPLAY_NAME
        )
        return runCatching {
            context.contentResolver.query(originalUri, projection, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use false
                val relativePath = cursor.getString(
                    cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
                ).orEmpty()
                val displayName = cursor.getString(
                    cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                ).orEmpty()
                relativePath.contains("Screenshots", ignoreCase = true) ||
                    displayName.contains("Screenshot", ignoreCase = true)
            } ?: false
        }.getOrDefault(false)
    }

    fun canTrashSilently(context: Context): Boolean {
        return runCatching { MediaStore.canManageMedia(context) }.getOrDefault(false)
    }

    fun attemptPendingTrash(context: Context): Boolean {
        val originalUri = pendingOriginalUri(context) ?: return false
        val removed = tryTrashOriginal(context.applicationContext, originalUri)
        if (removed) {
            clearPending(context.applicationContext)
        }
        return removed
    }

    fun openMediaManagementSettings(context: Context) {
        val packageUri = Uri.parse("package:${context.packageName}")
        val requestIntent = Intent(Settings.ACTION_REQUEST_MANAGE_MEDIA)
            .setData(packageUri)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(requestIntent)
        } catch (_: ActivityNotFoundException) {
            val fallbackIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(packageUri)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(fallbackIntent)
        }
    }

    fun tryTrashOriginal(context: Context, originalUri: Uri): Boolean {
        if (!canTrashSilently(context)) return false
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.IS_TRASHED, 1)
        }
        return runCatching {
            val updated = context.contentResolver.update(originalUri, values, null, null)
            Log.d(TAG, "trash update result=$updated uri=$originalUri")
            updated > 0
        }.onFailure { throwable ->
            Log.w(TAG, "trash update failed uri=$originalUri", throwable)
        }.getOrDefault(false)
    }

    fun showPendingFallbackNotification(context: Context) {
        val prefs = ClipticSettings.prefs(context)
        val originalUri = prefs.getString(ClipticSettings.KEY_PENDING_ORIGINAL_URI, null)?.let(Uri::parse)
        val requestId = prefs.getString(ClipticSettings.KEY_PENDING_ORIGINAL_REQUEST_ID, null)
        if (originalUri != null && requestId != null) {
            showFallbackNotification(context.applicationContext, originalUri, requestId)
        }
    }

    private fun promptIntent(context: Context, originalUri: Uri, requestId: String): Intent {
        return Intent(context, RemoveOriginalActivity::class.java)
            .setAction(AppActions.ACTION_TRASH_ORIGINAL)
            .putExtra(AppActions.EXTRA_SCREENSHOT_URI, originalUri.toString())
            .putExtra(AppActions.EXTRA_REQUEST_ID, requestId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun showFallbackNotification(context: Context, originalUri: Uri, requestId: String) {
        createChannel(context)
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            promptIntent(context, originalUri, requestId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile_cliptic)
            .setContentTitle(context.getString(R.string.trash_notification_title))
            .setContentText(context.getString(R.string.trash_notification_text))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .addAction(0, context.getString(R.string.trash_notification_action), pendingIntent)
            .build()
        context.getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, notification)
    }

    private fun showCleanupInProgress(context: Context) {
        context.getSystemService(NotificationManager::class.java)
            .cancel(NOTIFICATION_ID)
    }

    private fun savePending(context: Context, originalUri: Uri, requestId: String) {
        ClipticSettings.prefs(context).edit()
            .putString(ClipticSettings.KEY_PENDING_ORIGINAL_URI, originalUri.toString())
            .putString(ClipticSettings.KEY_PENDING_ORIGINAL_REQUEST_ID, requestId)
            .apply()
    }

    private fun clearPending(context: Context) {
        ClipticSettings.prefs(context).edit()
            .remove(ClipticSettings.KEY_PENDING_ORIGINAL_URI)
            .remove(ClipticSettings.KEY_PENDING_ORIGINAL_REQUEST_ID)
            .apply()
        context.getSystemService(NotificationManager::class.java)
            .cancel(NOTIFICATION_ID)
    }

    private fun pendingRequestId(context: Context): String? {
        return ClipticSettings.prefs(context)
            .getString(ClipticSettings.KEY_PENDING_ORIGINAL_REQUEST_ID, null)
    }

    private fun scheduleFastTrashRetries(context: Context, originalUri: Uri, requestId: String) {
        FAST_RETRY_DELAYS_MS.forEachIndexed { index, delayMs ->
            retryHandler.postDelayed({
                if (pendingRequestId(context) != requestId) return@postDelayed
                if (tryTrashOriginal(context, originalUri)) {
                    clearPending(context)
                } else {
                    Log.d(TAG, "fast trash retry failed attempt=${index + 1} uri=$originalUri")
                }
            }, delayMs)
        }
    }

    private fun enqueuePendingTrashWork(context: Context) {
        val request = OneTimeWorkRequest.Builder(PendingOriginalTrashWorker::class.java)
            .setInitialDelay(10, TimeUnit.SECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    private fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.trash_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        )
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

}
