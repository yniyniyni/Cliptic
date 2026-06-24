package art.yniyniyni.cliptic.xposed

object AppProtocol {
    const val APP_PACKAGE = "art.yniyniyni.cliptic"
    const val SYSTEMUI_PACKAGE = "com.android.systemui"
    const val MARKUP_PACKAGE = "com.google.android.markup"
    const val ACTION_COPY_SCREENSHOT = "art.yniyniyni.cliptic.ACTION_COPY_SCREENSHOT"
    const val ACTION_COPY_SCREENSHOT_ACK = "art.yniyniyni.cliptic.ACTION_COPY_SCREENSHOT_ACK"
    const val EXTRA_SCREENSHOT_URI = "uri"
    const val EXTRA_SECRET = "secret"
    const val SECRET_PROVIDER_AUTHORITY = "art.yniyniyni.cliptic.secrets"
    const val SECRET_PROVIDER_URI = "content://art.yniyniyni.cliptic.secrets/xposed_secret"

    // Liveness signal: the module runs inside SystemUI but cannot hook the app's own process
    // (LSPosed never injects a module into its own package), so it cannot flip an in-app flag.
    // Instead the SystemUI hook calls this provider method on startup; the app records the
    // timestamp and uses it to show an honest "module active" state in settings.
    const val PROVIDER_METHOD_RECORD_ACTIVE = "record_module_active"
}
