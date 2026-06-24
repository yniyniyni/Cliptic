package art.yniyniyni.cliptic.xposed

import android.util.Log
import art.yniyniyni.cliptic.xposed.hooks.MarkupCopyInjector
import art.yniyniyni.cliptic.xposed.hooks.SystemUIHook
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

class XposedEntry : XposedModule() {

    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        super.onModuleLoaded(param)
        logSafe("module loaded in process=${param.processName}")
    }

    override fun onPackageLoaded(param: XposedModuleInterface.PackageLoadedParam) {
        super.onPackageLoaded(param)
        val classLoader = param.defaultClassLoader
        // Note: the app's own package is intentionally not handled — LSPosed never injects a
        // module into its own process, so the app learns the module is live via the SystemUI
        // hook's provider ping (see SystemUIHook.pingModuleActive) instead of a self-hook.
        when (param.packageName) {
            AppProtocol.SYSTEMUI_PACKAGE -> SystemUIHook.install(this, classLoader, ::logSafe)
            AppProtocol.MARKUP_PACKAGE -> MarkupCopyInjector.install(this, classLoader, ::logSafe)
        }
    }

    private fun logSafe(message: String) {
        runCatching { log(Log.INFO, TAG, message) }
    }

    companion object {
        private const val TAG = "ClipticXposed"
    }
}
