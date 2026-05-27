package art.yniyniyni.cliptic.core.util

object XposedBridge {
    /**
     * Returns false normally. The Xposed module can hook this method in the app
     * process to report active status.
     */
    @JvmStatic
    fun isModuleActive(): Boolean = false
}
