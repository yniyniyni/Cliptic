package art.yniyniyni.cliptic.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import art.yniyniyni.cliptic.settings.ClipticSettings

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (
            intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) {
            ClipticSettings.ensureDefaults(context)
            val prefs = ClipticSettings.prefs(context)
            if (prefs.getBoolean(ClipticSettings.KEY_START_ON_BOOT, true)) {
                ClipticSettings.startScreenshotService(context)
            }
        }
    }
}
