package art.yniyniyni.cliptic.cleanup

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import art.yniyniyni.cliptic.AppActions
import art.yniyniyni.cliptic.R
import art.yniyniyni.cliptic.settings.ClipticSettings

object OriginalScreenshotCleanup {
    private const val CHANNEL_ID = "screenshot_cleanup"
    private const val NOTIFICATION_BASE_ID = 10_000

    fun requestTrashPrompt(context: Context, originalUri: Uri) {
        val requestId = System.currentTimeMillis().toString()
        val appContext = context.applicationContext
        savePending(appContext, originalUri, requestId)
        showFallbackNotification(appContext, originalUri, requestId)
    }

    fun launchPendingPrompt(context: Context) {
        val prefs = ClipticSettings.prefs(context)
        val originalUri = prefs.getString(ClipticSettings.KEY_PENDING_ORIGINAL_URI, null)?.let(Uri::parse)
        val requestId = prefs.getString(ClipticSettings.KEY_PENDING_ORIGINAL_REQUEST_ID, null)
        if (originalUri != null && requestId != null) {
            context.startActivity(promptIntent(context.applicationContext, originalUri, requestId))
        }
    }

    fun onTrashPromptResult(context: Context, requestId: String?, originalUri: Uri?, removed: Boolean) {
        if (requestId == null || originalUri == null) return
        if (removed) {
            clearPending(context, requestId)
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
            notificationId(requestId),
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
            .notify(notificationId(requestId), notification)
    }

    private fun savePending(context: Context, originalUri: Uri, requestId: String) {
        ClipticSettings.prefs(context).edit()
            .putString(ClipticSettings.KEY_PENDING_ORIGINAL_URI, originalUri.toString())
            .putString(ClipticSettings.KEY_PENDING_ORIGINAL_REQUEST_ID, requestId)
            .apply()
    }

    private fun clearPending(context: Context, requestId: String) {
        ClipticSettings.prefs(context).edit()
            .remove(ClipticSettings.KEY_PENDING_ORIGINAL_URI)
            .remove(ClipticSettings.KEY_PENDING_ORIGINAL_REQUEST_ID)
            .apply()
        context.getSystemService(NotificationManager::class.java)
            .cancel(notificationId(requestId))
    }

    private fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.trash_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        )
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun notificationId(requestId: String): Int {
        return NOTIFICATION_BASE_ID + requestId.hashCode().mod(20_000)
    }
}
