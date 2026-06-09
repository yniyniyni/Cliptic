package art.yniyniyni.cliptic.xposed.hooks

import io.github.libxposed.api.XposedInterface

/**
 * Hooks installed inside the Cliptic app process (`art.yniyniyni.cliptic`).
 *
 * The only job here is to flip [art.yniyniyni.cliptic.ipc.XposedBridge.isModuleActive] to
 * `true` so the standalone UI can reveal its LSPosed section. The method is a plain static
 * (`@JvmStatic` on a Kotlin `object`) that normally returns `false`. The stub lives in the
 * app's full source set (`art.yniyniyni.cliptic.ipc.XposedBridge`); the play flavor contains
 * no LSPosed UI or IPC secret.
 */
object AppHook {
    private const val BRIDGE_CLASS = "art.yniyniyni.cliptic.ipc.XposedBridge"

    fun install(module: XposedInterface, classLoader: ClassLoader, log: (String) -> Unit) {
        runCatching {
            val bridge = classLoader.loadClass(BRIDGE_CLASS)
            val isActive = bridge.getDeclaredMethod("isModuleActive")
            module.hook(isActive).intercept(ModuleActiveHooker())
            log("XposedBridge.isModuleActive hook installed")
        }.onFailure { throwable ->
            log("XposedBridge.isModuleActive hook failed: ${throwable.javaClass.simpleName}: ${throwable.message}")
        }
    }

    class ModuleActiveHooker : XposedInterface.Hooker {
        // isModuleActive() normally returns false; force true by returning without calling proceed().
        override fun intercept(chain: XposedInterface.Chain): Any = true
    }
}
