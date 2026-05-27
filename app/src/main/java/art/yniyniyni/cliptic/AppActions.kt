package art.yniyniyni.cliptic

object AppActions {
    const val ACTION_COPY_SCREENSHOT = "art.yniyniyni.cliptic.ACTION_COPY_SCREENSHOT"
    const val ACTION_COPY_SCREENSHOT_ACK = "art.yniyniyni.cliptic.ACTION_COPY_SCREENSHOT_ACK"
    const val ACTION_STOP_SERVICE = "art.yniyniyni.cliptic.ACTION_STOP_SERVICE"
    const val ACTION_TRASH_ORIGINAL = "art.yniyniyni.cliptic.ACTION_TRASH_ORIGINAL"
    const val EXTRA_SCREENSHOT_URI = "uri"
    const val EXTRA_REQUEST_ID = "request_id"
    const val EXTRA_SECRET = "secret"
    const val SYSTEMUI_PACKAGE = "com.android.systemui"
    const val SECRET_PROVIDER_AUTHORITY = "art.yniyniyni.cliptic.secrets"
    const val SECRET_PROVIDER_URI = "content://$SECRET_PROVIDER_AUTHORITY/xposed_secret"
}
