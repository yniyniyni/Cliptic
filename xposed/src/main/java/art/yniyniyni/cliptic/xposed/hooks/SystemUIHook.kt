package art.yniyniyni.cliptic.xposed.hooks

import android.app.Application
import android.content.Context
import android.content.IntentFilter
import android.net.Uri
import art.yniyniyni.cliptic.xposed.AppProtocol
import art.yniyniyni.cliptic.xposed.ipc.CopyAckReceiver
import io.github.libxposed.api.XposedInterface

object SystemUIHook {
    @Volatile
    private var receiverRegistered = false

    @Volatile
    private var secretProvider: ((Context) -> String?)? = null

    @Volatile
    private var logSink: ((String) -> Unit)? = null

    fun install(module: XposedInterface, classLoader: ClassLoader, log: (String) -> Unit) {
        SystemUiInspector.run(module, classLoader, log)
        secretProvider = { context -> readExpectedSecret(context) }
        logSink = log
        runCatching {
            val attach = Application::class.java.getDeclaredMethod("attach", Context::class.java)
            module.hook(attach, ApplicationAttachHooker::class.java)
            log("Application.attach hook installed for SystemUI context capture")
        }.onFailure { throwable ->
            log("Application.attach hook failed: ${throwable.javaClass.simpleName}: ${throwable.message}")
        }
    }

    fun registerAckReceiver(
        context: Context,
        expectedSecretProvider: () -> String?,
        log: (String) -> Unit
    ): Boolean {
        return runCatching {
            context.registerReceiver(
                CopyAckReceiver(expectedSecretProvider, log),
                IntentFilter(AppProtocol.ACTION_COPY_SCREENSHOT_ACK),
                Context.RECEIVER_EXPORTED
            )
            log("copy ACK receiver registered in SystemUI")
            true
        }.onFailure { throwable ->
            log("copy ACK receiver registration failed: ${throwable.javaClass.simpleName}: ${throwable.message}")
        }.getOrDefault(false)
    }

    private fun onApplicationAttach(context: Context) {
        if (context.packageName != AppProtocol.SYSTEMUI_PACKAGE || receiverRegistered) return
        val log = logSink ?: return
        receiverRegistered = registerAckReceiver(
            context,
            expectedSecretProvider = { secretProvider?.invoke(context) },
            log = log
        )
    }

    private fun readExpectedSecret(context: Context): String? {
        return runCatching {
            context.contentResolver.query(
                Uri.parse(AppProtocol.SECRET_PROVIDER_URI),
                arrayOf("secret"),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
    }

    class ApplicationAttachHooker : XposedInterface.Hooker {
        companion object {
            @JvmStatic
            fun before(callback: XposedInterface.BeforeHookCallback) {
                val context = callback.args.firstOrNull() as? Context ?: return
                onApplicationAttach(context)
            }
        }
    }

}
