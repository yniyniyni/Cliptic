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
        inspect(classLoader, log)
        secretProvider = { context -> readExpectedSecret(context) }
        logSink = log
        runCatching {
            val attach = Application::class.java.getDeclaredMethod("attach", Context::class.java)
            module.hook(attach).intercept(ApplicationAttachHooker())
            log("Application.attach hook installed for SystemUI context capture")
        }.onFailure { throwable ->
            log("Application.attach hook failed: ${throwable.javaClass.simpleName}: ${throwable.message}")
        }
    }

    fun inspect(classLoader: ClassLoader, log: (String) -> Unit) {
        runCatching {
            val screenshotView = Class.forName(SCREENSHOT_VIEW_CLASS, false, classLoader)
            val methods = screenshotView.declaredMethods
                .map { method -> "${method.name}(${method.parameterTypes.joinToString { it.simpleName }})" }
                .sorted()
                .take(MAX_LOGGED_MEMBERS)
            val fields = screenshotView.declaredFields
                .map { field -> "${field.type.simpleName} ${field.name}" }
                .sorted()
                .take(MAX_LOGGED_MEMBERS)

            log("found $SCREENSHOT_VIEW_CLASS")
            log("candidate methods=${methods.joinToString()}")
            log("candidate fields=${fields.joinToString()}")
        }.onFailure { throwable ->
            log("SystemUI screenshot inspection failed: ${throwable.javaClass.simpleName}: ${throwable.message}")
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

    private const val SCREENSHOT_VIEW_CLASS = "com.android.systemui.screenshot.ScreenshotView"
    private const val MAX_LOGGED_MEMBERS = 24
}
