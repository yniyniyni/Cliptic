package art.yniyniyni.cliptic.xposed

object AppProtocol {
    const val APP_PACKAGE = "art.yniyniyni.cliptic"
    const val SYSTEMUI_PACKAGE = "com.android.systemui"
    const val ACTION_COPY_SCREENSHOT_ACK = "art.yniyniyni.cliptic.ACTION_COPY_SCREENSHOT_ACK"
    const val EXTRA_SCREENSHOT_URI = "uri"
    const val EXTRA_SECRET = "secret"
}
