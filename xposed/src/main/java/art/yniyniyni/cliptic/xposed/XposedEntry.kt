package art.yniyniyni.cliptic.xposed

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
            AppProtocol.SYSTEMUI_PACKAGE -> SystemUIHook.inspect(param.classLoader, ::logSafe)
            AppProtocol.APP_PACKAGE -> logSafe("app process loaded; active-module hook will be added after API 100 hook surface is verified")
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
