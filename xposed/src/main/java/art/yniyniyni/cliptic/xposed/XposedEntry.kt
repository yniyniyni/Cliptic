package art.yniyniyni.cliptic.xposed

import art.yniyniyni.cliptic.xposed.hooks.AppHook
import art.yniyniyni.cliptic.xposed.hooks.SystemUIHook
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

class XposedEntry(
    base: XposedInterface,
    param: XposedModuleInterface.ModuleLoadedParam
) : XposedModule(base, param) {
    init {
        logSafe("module loaded in process=${param.processName}")
    }

    override fun onPackageLoaded(param: XposedModuleInterface.PackageLoadedParam) {
        when (param.packageName) {
            AppProtocol.SYSTEMUI_PACKAGE -> SystemUIHook.install(this, param.classLoader, ::logSafe)
            AppProtocol.APP_PACKAGE -> AppHook.install(this, param.classLoader, ::logSafe)
        }
    }

    private fun logSafe(message: String) {
        runCatching {
            log("$TAG: $message")
        }
    }

    companion object {
        private const val TAG = "ClipticXposed"
    }
}
