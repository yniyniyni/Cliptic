package art.yniyniyni.cliptic.xposed.hooks

import io.github.libxposed.api.XposedInterface

/**
 * Hooks installed inside the Cliptic app process (`art.yniyniyni.cliptic`).
 *
 * The only job here is to flip [art.yniyniyni.cliptic.core.util.XposedBridge.isModuleActive] to
 * `true` so the standalone UI can reveal its LSPosed section. The method is a plain static
 * (`@JvmStatic` on a Kotlin `object`) that normally returns `false`.
 */
object AppHook {
    private const val BRIDGE_CLASS = "art.yniyniyni.cliptic.core.util.XposedBridge"

    fun install(module: XposedInterface, classLoader: ClassLoader, log: (String) -> Unit) {
        runCatching {
            val bridge = classLoader.loadClass(BRIDGE_CLASS)
            val isActive = bridge.getDeclaredMethod("isModuleActive")
            module.hook(isActive, ModuleActiveHooker::class.java)
            log("XposedBridge.isModuleActive hook installed")
        }.onFailure { throwable ->
            log("XposedBridge.isModuleActive hook failed: ${throwable.javaClass.simpleName}: ${throwable.message}")
        }
    }

    class ModuleActiveHooker : XposedInterface.Hooker {
        companion object {
            @JvmStatic
            fun before(callback: XposedInterface.BeforeHookCallback) {
                runCatching { callback.returnAndSkip(true) }
            }
        }
    }
}
