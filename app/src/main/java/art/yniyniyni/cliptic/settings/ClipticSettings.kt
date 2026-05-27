package art.yniyniyni.cliptic.settings

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import art.yniyniyni.cliptic.service.ScreenshotService
import java.util.UUID

object ClipticSettings {
    const val KEY_AUTO_COPY_ENABLED = "auto_copy_enabled"
    const val KEY_SHARE_SHEET_ENABLED = "share_sheet_enabled"
    const val KEY_START_ON_BOOT = "start_on_boot"
    const val KEY_SHOW_SERVICE_NOTIFICATION = "show_service_notification"
    const val KEY_REMOVE_ORIGINAL_AFTER_COPY = "remove_original_after_copy"
    const val KEY_COPY_MODE = "copy_mode"
    const val KEY_XPOSED_SECRET = "xposed_secret"
    const val KEY_CACHE_DURATION_MS = "cache_duration_ms"
    const val KEY_ONBOARDING_DONE = "onboarding_done"
    const val KEY_PENDING_ORIGINAL_URI = "pending_original_uri"
    const val KEY_PENDING_ORIGINAL_REQUEST_ID = "pending_original_request_id"
    const val KEY_SERVICE_RUNNING = "service_running"

    const val COPY_MODE_AUTO = "auto"
    const val COPY_MODE_XPOSED = "xposed"
    const val COPY_MODE_BOTH = "both"

    const val DEFAULT_CACHE_DURATION_MS = 3_600_000L

    fun prefs(context: Context): SharedPreferences {
        return context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)
    }

    fun ensureDefaults(context: Context) {
        val prefs = prefs(context)
        if (!prefs.contains(KEY_XPOSED_SECRET)) {
            prefs.edit()
                .putBoolean(KEY_AUTO_COPY_ENABLED, prefs.getBoolean(KEY_AUTO_COPY_ENABLED, true))
                .putBoolean(KEY_SHARE_SHEET_ENABLED, prefs.getBoolean(KEY_SHARE_SHEET_ENABLED, true))
                .putBoolean(KEY_START_ON_BOOT, prefs.getBoolean(KEY_START_ON_BOOT, true))
                .putBoolean(KEY_SHOW_SERVICE_NOTIFICATION, prefs.getBoolean(KEY_SHOW_SERVICE_NOTIFICATION, true))
                .putBoolean(KEY_REMOVE_ORIGINAL_AFTER_COPY, prefs.getBoolean(KEY_REMOVE_ORIGINAL_AFTER_COPY, true))
                .putString(KEY_COPY_MODE, prefs.getString(KEY_COPY_MODE, COPY_MODE_AUTO))
                .putString(KEY_XPOSED_SECRET, UUID.randomUUID().toString())
                .putLong(KEY_CACHE_DURATION_MS, DEFAULT_CACHE_DURATION_MS)
                .apply()
        }
        setShareSheetEnabled(context, prefs.getBoolean(KEY_SHARE_SHEET_ENABLED, true))
    }

    fun xposedSecret(context: Context): String {
        ensureDefaults(context)
        return prefs(context).getString(KEY_XPOSED_SECRET, null) ?: UUID.randomUUID().toString().also {
            prefs(context).edit().putString(KEY_XPOSED_SECRET, it).apply()
        }
    }

    fun shouldRunScreenshotService(context: Context): Boolean {
        val prefs = prefs(context)
        val copyMode = prefs.getString(KEY_COPY_MODE, COPY_MODE_AUTO) ?: COPY_MODE_AUTO
        return prefs.getBoolean(KEY_AUTO_COPY_ENABLED, true) && copyMode != COPY_MODE_XPOSED
    }

    fun startScreenshotService(context: Context) {
        if (!shouldRunScreenshotService(context)) return
        val intent = Intent(context, ScreenshotService::class.java)
        ContextCompat.startForegroundService(context, intent)
    }

    fun stopScreenshotService(context: Context) {
        context.stopService(Intent(context, ScreenshotService::class.java))
        prefs(context).edit().putBoolean(KEY_SERVICE_RUNNING, false).apply()
    }

    fun setAutoCopyEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_COPY_ENABLED, enabled).apply()
        if (enabled) startScreenshotService(context) else stopScreenshotService(context)
    }

    fun setShareSheetEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_SHARE_SHEET_ENABLED, enabled).apply()
        val component = ComponentName(context, "${context.packageName}.share.ShareReceiverAlias")
        val state = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        context.packageManager.setComponentEnabledSetting(
            component,
            state,
            PackageManager.DONT_KILL_APP
        )
    }

    fun setCopyMode(context: Context, mode: String) {
        prefs(context).edit().putString(KEY_COPY_MODE, mode).apply()
        if (shouldRunScreenshotService(context)) startScreenshotService(context) else stopScreenshotService(context)
    }
}
