package art.yniyniyni.cliptic.permission

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Image-read access on Android 14+ (minSdk 34) has three states. "Selected photos only"
 * (PARTIAL) breaks auto-copy because newly captured screenshots are not visible to the app,
 * so the UI must distinguish it from full access and prompt the user to upgrade.
 */
enum class MediaAccessLevel { FULL, PARTIAL, NONE }

object MediaAccess {
    /** Permissions to request so the system offers the "Selected photos only" option on 14+. */
    val requestPermissions: Array<String> = arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
    )

    fun level(context: Context): MediaAccessLevel {
        val full = isGranted(context, Manifest.permission.READ_MEDIA_IMAGES)
        if (full) return MediaAccessLevel.FULL
        val partial = isGranted(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        return if (partial) MediaAccessLevel.PARTIAL else MediaAccessLevel.NONE
    }

    fun hasFullAccess(context: Context): Boolean = level(context) == MediaAccessLevel.FULL

    /**
     * Partial access cannot be promoted to full via a runtime request, so send the user to the
     * app's settings page where "Allow all" is available.
     */
    fun openAppSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun isGranted(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED
    }
}
