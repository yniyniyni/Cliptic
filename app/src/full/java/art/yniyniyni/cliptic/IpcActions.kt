package art.yniyniyni.cliptic

/**
 * IPC constants used only by the full (sideload) build to talk to the LSPosed module
 * running inside com.android.systemui. Never present in the Play APK.
 */
object IpcActions {
    const val ACTION_COPY_SCREENSHOT = "art.yniyniyni.cliptic.ACTION_COPY_SCREENSHOT"
    const val ACTION_COPY_SCREENSHOT_ACK = "art.yniyniyni.cliptic.ACTION_COPY_SCREENSHOT_ACK"
    const val EXTRA_SECRET = "secret"
    const val SYSTEMUI_PACKAGE = "com.android.systemui"
    const val SECRET_PROVIDER_AUTHORITY = "art.yniyniyni.cliptic.secrets"
    const val SECRET_PROVIDER_URI = "content://$SECRET_PROVIDER_AUTHORITY/xposed_secret"

    // Mirrors AppProtocol.PROVIDER_METHOD_RECORD_ACTIVE — the SystemUI hook calls this on the
    // secret provider so the app can record that the embedded module is live (it cannot hook
    // its own process to flip an in-app flag). Keep in sync with the xposed side.
    const val PROVIDER_METHOD_RECORD_ACTIVE = "record_module_active"
}
