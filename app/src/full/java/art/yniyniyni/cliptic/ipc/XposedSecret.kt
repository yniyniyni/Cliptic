package art.yniyniyni.cliptic.ipc

import android.content.Context
import art.yniyniyni.cliptic.settings.ClipticSettings
import java.util.UUID

object XposedSecret {
    private const val KEY = "xposed_secret"

    /** Per-install IPC secret shared with the SystemUI hook; generated on first use. */
    fun get(context: Context): String {
        val prefs = ClipticSettings.prefs(context)
        return prefs.getString(KEY, null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString(KEY, it).apply()
        }
    }
}
