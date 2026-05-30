package art.yniyniyni.cliptic.cleanup

import android.content.ActivityNotFoundException
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
        addPending(appContext, originalUri)

        if (canTrashSilently(appContext)) {
            showCleanupInProgress(appContext)
            scheduleFastTrashRetries(appContext, originalUri)
            enqueuePendingTrashWork(appContext)
        } else {
            showPendingFallbackNotification(appContext)
        }
    }

    fun launchPendingPrompt(context: Context) {
        val originalUri = pendingOriginalUri(context)
        if (originalUri != null) {
            if (tryTrashOriginal(context.applicationContext, originalUri)) {
                removePending(context.applicationContext, originalUri)
                return
            }
            context.startActivity(promptIntent(context.applicationContext, originalUri))
        }
    }

    fun onTrashPromptResult(context: Context, requestId: String?, originalUri: Uri?, removed: Boolean) {
        if (originalUri == null) return
        if (removed) {
            removePending(context, originalUri)
        } else {
            addPending(context, originalUri)
            showPendingFallbackNotification(context)
        }
    }

    fun pendingOriginalUri(context: Context): Uri? {
        return pendingOriginalUris(context).firstOrNull()
    }

    fun pendingOriginalCount(context: Context): Int {
        return pendingOriginalUris(context).size
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
        var removedAny = false
        pendingOriginalUris(context).forEach { originalUri ->
            if (tryTrashOriginal(context.applicationContext, originalUri)) {
                removePending(context.applicationContext, originalUri)
                removedAny = true
            }
        }
        return removedAny
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
        return runCatching {
            val request = MediaStore.createTrashRequest(
                context.contentResolver,
                listOf(originalUri),
                true
            )
            request.send()
            Log.d(TAG, "trash request sent uri=$originalUri")
            true
        }.onFailure { throwable ->
            Log.w(TAG, "trash request failed uri=$originalUri", throwable)
        }.getOrDefault(false)
    }

    fun showPendingFallbackNotification(context: Context) {
        val originals = pendingOriginalUris(context)
        val originalUri = originals.firstOrNull()
        if (originalUri != null) {
            showFallbackNotification(context.applicationContext, originalUri, originals.size)
        }
    }

    private fun promptIntent(context: Context, originalUri: Uri): Intent {
        return Intent(context, RemoveOriginalActivity::class.java)
            .setAction(AppActions.ACTION_TRASH_ORIGINAL)
            .putExtra(AppActions.EXTRA_SCREENSHOT_URI, originalUri.toString())
            .putExtra(AppActions.EXTRA_REQUEST_ID, originalUri.toString())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun showFallbackNotification(context: Context, originalUri: Uri, pendingCount: Int) {
        createChannel(context)
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            promptIntent(context, originalUri),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile_cliptic)
            .setContentTitle(context.getString(R.string.trash_notification_title))
            .setContentText(
                if (pendingCount > 1) {
                    context.getString(R.string.trash_notification_text_pending, pendingCount)
                } else {
                    context.getString(R.string.trash_notification_text)
                }
            )
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

    private fun scheduleFastTrashRetries(context: Context, originalUri: Uri) {
        FAST_RETRY_DELAYS_MS.forEachIndexed { index, delayMs ->
            retryHandler.postDelayed({
                if (!pendingOriginalUris(context).contains(originalUri)) return@postDelayed
                if (tryTrashOriginal(context, originalUri)) {
                    removePending(context, originalUri)
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

    private fun addPending(context: Context, originalUri: Uri) {
        val current = pendingOriginalUris(context)
        if (current.contains(originalUri)) return
        savePendingQueue(context, current + originalUri)
    }

    private fun removePending(context: Context, originalUri: Uri) {
        val updated = pendingOriginalUris(context).filterNot { it == originalUri }
        savePendingQueue(context, updated)
        if (updated.isEmpty()) {
            context.getSystemService(NotificationManager::class.java)
                .cancel(NOTIFICATION_ID)
        } else {
            showPendingFallbackNotification(context)
        }
    }

    private fun pendingOriginalUris(context: Context): List<Uri> {
        val prefs = ClipticSettings.prefs(context)
        val queue = prefs.getString(ClipticSettings.KEY_PENDING_ORIGINAL_QUEUE, null)
        if (queue != null) {
            return queue.lineSequence()
                .mapNotNull { raw -> raw.takeIf { it.isNotBlank() }?.let(Uri::parse) }
                .distinct()
                .toList()
        }

        val legacyUri = prefs.getString(ClipticSettings.KEY_PENDING_ORIGINAL_URI, null)?.let(Uri::parse)
        if (legacyUri != null) {
            savePendingQueue(context, listOf(legacyUri))
            return listOf(legacyUri)
        }
        return emptyList()
    }

    private fun savePendingQueue(context: Context, originals: List<Uri>) {
        val editor = ClipticSettings.prefs(context).edit()
            .remove(ClipticSettings.KEY_PENDING_ORIGINAL_URI)
            .remove(ClipticSettings.KEY_PENDING_ORIGINAL_REQUEST_ID)
        if (originals.isEmpty()) {
            editor.remove(ClipticSettings.KEY_PENDING_ORIGINAL_QUEUE)
        } else {
            editor.putString(
                ClipticSettings.KEY_PENDING_ORIGINAL_QUEUE,
                originals.distinct().joinToString(separator = "\n") { it.toString() }
            )
        }
        editor.apply()
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
