package art.yniyniyni.cliptic.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationCompat
import art.yniyniyni.cliptic.AppActions
import art.yniyniyni.cliptic.MainActivity
import art.yniyniyni.cliptic.R
import art.yniyniyni.cliptic.cleanup.OriginalScreenshotCleanup
import art.yniyniyni.cliptic.core.clipboard.ClipboardWriter
import art.yniyniyni.cliptic.core.screenshot.ScreenshotDetector
import art.yniyniyni.cliptic.core.screenshot.ScreenshotFileManager
import art.yniyniyni.cliptic.settings.ClipticSettings

class ScreenshotService : Service() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var fileManager: ScreenshotFileManager
    private var detector: ScreenshotDetector? = null

    override fun onCreate() {
        super.onCreate()
        ClipticSettings.ensureDefaults(this)
        fileManager = ScreenshotFileManager(this)
        fileManager.cleanupExpired(ClipticSettings.cacheDurationMs(this))
        detector = ScreenshotDetector(this) { uri -> copyScreenshotWithRetry(uri) }
        ClipticSettings.prefs(this).edit().putBoolean(ClipticSettings.KEY_SERVICE_RUNNING, true).apply()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == AppActions.ACTION_STOP_SERVICE) {
            stopSelf()
            return START_NOT_STICKY
        }

        createNotificationChannel()
        startForeground(
            NOTIFICATION_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )
        detector?.start()
        return START_STICKY
    }

    override fun onDestroy() {
        detector?.stop()
        ClipticSettings.prefs(this).edit().putBoolean(ClipticSettings.KEY_SERVICE_RUNNING, false).apply()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun copyScreenshotWithRetry(sourceUri: Uri) {
        val cachedUri = fileManager.cacheScreenshot(sourceUri)
        if (cachedUri == null) {
            mainHandler.postDelayed({
                fileManager.cacheScreenshot(sourceUri)?.let { copyCachedScreenshot(it, sourceUri) }
            }, RETRY_DELAY_MS)
            return
        }
        copyCachedScreenshot(cachedUri, sourceUri)
    }

    private fun copyCachedScreenshot(cachedUri: Uri, originalUri: Uri) {
        mainHandler.post {
            ClipboardWriter.copyUriToClipboard(this, cachedUri)
            Toast.makeText(this, R.string.screenshot_copied, Toast.LENGTH_SHORT).show()
            fileManager.scheduleCleanup(cachedUri, ClipticSettings.cacheDurationMs(this))
            if (
                ClipticSettings.prefs(this)
                    .getBoolean(ClipticSettings.KEY_REMOVE_ORIGINAL_AFTER_COPY, true)
            ) {
                OriginalScreenshotCleanup.requestTrashPrompt(this, originalUri)
            }
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.service_channel_name),
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, ScreenshotService::class.java).setAction(AppActions.ACTION_STOP_SERVICE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile_cliptic)
            .setContentTitle(getString(R.string.service_notification_title))
            .setContentText(getString(R.string.service_notification_text))
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .addAction(0, getString(R.string.service_stop), stopIntent)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "screenshot_service"
        private const val NOTIFICATION_ID = 42
        private const val RETRY_DELAY_MS = 500L
    }
}
