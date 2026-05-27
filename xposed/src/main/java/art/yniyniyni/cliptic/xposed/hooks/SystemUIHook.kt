package art.yniyniyni.cliptic.xposed.hooks

import android.content.Context
import android.content.IntentFilter
import art.yniyniyni.cliptic.xposed.AppProtocol
import art.yniyniyni.cliptic.xposed.ipc.CopyAckReceiver

object SystemUIHook {
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
    ) {
        runCatching {
            context.registerReceiver(
                CopyAckReceiver(expectedSecretProvider, log),
                IntentFilter(AppProtocol.ACTION_COPY_SCREENSHOT_ACK),
                Context.RECEIVER_NOT_EXPORTED
            )
            log("copy ACK receiver registered in SystemUI")
        }.onFailure { throwable ->
            log("copy ACK receiver registration failed: ${throwable.javaClass.simpleName}: ${throwable.message}")
        }
    }

    private const val SCREENSHOT_VIEW_CLASS = "com.android.systemui.screenshot.ScreenshotView"
    private const val MAX_LOGGED_MEMBERS = 24
}
