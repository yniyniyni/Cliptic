package art.yniyniyni.cliptic.xposed.hooks

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.widget.Toast
import art.yniyniyni.cliptic.xposed.AppProtocol
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Constructor
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Member
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy

/**
 * Injects a "Copy" action chip into the Android 16 Pixel SystemUI screenshot toolbar and, on tap,
 * broadcasts the screenshot [Uri] to the Cliptic app (which caches it, writes it to the clipboard,
 * and ACKs back to silently trash the original). Class/method names and the view hierarchy are in
 * `xposed/SYSTEMUI_ANDROID16_FINDINGS.md`.
 *
 * The chip is added by **model injection**, not by mutating the view tree: a *before* hook on
 * `ScreenshotShelfViewBinder.access$updateActions(binder, List<ActionButtonViewModel>, …)` prepends
 * our own `ActionButtonViewModel` to the action list, so the framework builds, styles, and recycles
 * our chip exactly like Share/Edit. (An earlier version added a raw View into the framework-managed
 * `screenshot_actions` LinearLayout; that corrupted the binder's index-based view recycling and
 * produced duplicate native chips + a one-frame blink — hence this approach.)
 *
 * The screenshot Uri is captured separately from the Uri-bearing methods of [ImageExporter] /
 * [ActionIntentCreator] / [ScreenshotController] (see [captureUri]).
 *
 * Everything is defensive: every probe/hook step is wrapped in [runCatching] so a SystemUI change
 * can only make Copy silently absent — never crash `com.android.systemui`.
 */
object CopyButtonInjector {
    @Volatile
    private var logSink: ((String) -> Unit)? = null

    @Volatile
    private var latestUri: Uri? = null

    // Reflective handles into SystemUI's action view-model graph, resolved once at install.
    @Volatile
    private var appearanceCtor: Constructor<*>? = null

    @Volatile
    private var viewModelCtor: Constructor<*>? = null

    @Volatile
    private var function0Class: Class<*>? = null

    @Volatile
    private var unitInstance: Any? = null

    fun install(module: XposedInterface, classLoader: ClassLoader, log: (String) -> Unit) {
        logSink = log
        resolveModelTypes(classLoader, log)

        val uriClasses = resolve(classLoader, URI_SOURCE_CLASSES, log)
        var hooks = hookMethods(module, uriClasses, ::isUriMethod, log)
        hooks += hookUpdateActions(module, classLoader, log)
        log("copy injector ready: hooks=$hooks model=${viewModelCtor != null}")
    }

    // --- type resolution & hook wiring -------------------------------------------------

    private fun resolveModelTypes(classLoader: ClassLoader, log: (String) -> Unit) {
        runCatching {
            val appearance = Class.forName(APPEARANCE_CLASS, false, classLoader)
            val viewModel = Class.forName(VIEWMODEL_CLASS, false, classLoader)
            val function0 = Class.forName("kotlin.jvm.functions.Function0", false, classLoader)
            appearanceCtor = appearance.getDeclaredConstructor(
                Drawable::class.java, CharSequence::class.java, CharSequence::class.java,
                Boolean::class.javaPrimitiveType
            )
            viewModelCtor = viewModel.getDeclaredConstructor(
                appearance, Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType, function0
            )
            function0Class = function0
            unitInstance = runCatching {
                Class.forName("kotlin.Unit", false, classLoader).getField("INSTANCE").get(null)
            }.getOrNull()
            log("model types resolved")
        }.onFailure { log("model types failed: ${it.javaClass.simpleName}: ${it.message}") }
    }

    private fun resolve(
        classLoader: ClassLoader,
        names: List<String>,
        log: (String) -> Unit
    ): List<Class<*>> = names.mapNotNull { name ->
        runCatching { Class.forName(name, false, classLoader) }
            .onFailure { log("copy injector: missing $name (${it.javaClass.simpleName})") }
            .getOrNull()
    }

    private fun hookMethods(
        module: XposedInterface,
        classes: List<Class<*>>,
        predicate: (Method) -> Boolean,
        log: (String) -> Unit
    ): Int {
        var hooks = 0
        for (clazz in classes) {
            if (hooks >= MAX_HOOKS) break
            val methods = runCatching { clazz.declaredMethods.toList() }.getOrDefault(emptyList())
            for (method in methods) {
                if (hooks >= MAX_HOOKS) break
                if (!predicate(method)) continue
                val ok = runCatching {
                    module.hook(method, CopyHooker::class.java)
                    true
                }.onFailure {
                    log("copy hook ${clazz.simpleName}#${method.name} failed: ${it.javaClass.simpleName}")
                }.getOrDefault(false)
                if (ok) hooks++
            }
        }
        return hooks
    }

    private fun hookUpdateActions(
        module: XposedInterface,
        classLoader: ClassLoader,
        log: (String) -> Unit
    ): Int = runCatching {
        val binder = Class.forName(SHELF_BINDER_CLASS, false, classLoader)
        val method = binder.declaredMethods.first { it.name == "access\$updateActions" }
        module.hook(method, UpdateActionsHooker::class.java)
        log("hooked $SHELF_BINDER_CLASS#access\$updateActions for model injection")
        1
    }.onFailure { log("hook updateActions failed: ${it.javaClass.simpleName}: ${it.message}") }
        .getOrDefault(0)

    private fun isUriMethod(method: Method): Boolean {
        if (Modifier.isAbstract(method.modifiers)) return false
        if (method.returnType == Uri::class.java) return true
        return method.parameterTypes.any { it == Uri::class.java }
    }

    // --- model injection (UpdateActionsHooker.before) ----------------------------------

    fun onUpdateActionsBefore(args: Array<Any?>?) {
        val log = logSink ?: return
        runCatching {
            val a = args ?: return
            val list = a.getOrNull(1) as? List<*> ?: return
            val context = (a.getOrNull(3) as? android.view.View)?.context ?: return
            val model = buildCopyModel(context) ?: return
            // Prepend so Copy is the leftmost chip; the framework renders the whole list itself.
            a[1] = ArrayList<Any?>(list.size + 1).apply {
                add(model)
                addAll(list)
            }
        }.onFailure { log("updateActions inject failed: ${it.javaClass.simpleName}: ${it.message}") }
    }

    private fun buildCopyModel(context: Context): Any? {
        val apCtor = appearanceCtor ?: return null
        val vmCtor = viewModelCtor ?: return null
        val icon = CopyIconDrawable(DEFAULT_ICON_COLOR, dp(context, 24f))
        // (icon, label, description, tint=true). Empty label → icon-only chip like Share/Edit;
        // tint=true lets the framework recolour our glyph to the theme (handled in CopyIconDrawable).
        val appearance = apCtor.newInstance(icon, "", "Copy screenshot", true)
        val onClicked = buildOnClicked(context) ?: return null
        return vmCtor.newInstance(appearance, COPY_ACTION_ID, true, onClicked)
    }

    /** A `Function0` implemented in SystemUI's classloader (a Kotlin lambda from our APK wouldn't be type-compatible). */
    private fun buildOnClicked(context: Context): Any? {
        val function0 = function0Class ?: return null
        val handler = InvocationHandler { _, method, _ ->
            when (method.name) {
                "invoke" -> {
                    onCopyClicked(context)
                    unitInstance
                }
                "equals" -> false
                "hashCode" -> COPY_ACTION_ID
                "toString" -> "ClipticCopyOnClicked"
                else -> null
            }
        }
        return Proxy.newProxyInstance(function0.classLoader, arrayOf(function0), handler)
    }

    // --- screenshot Uri capture (CopyHooker.after) -------------------------------------

    fun onHookedCall(args: Array<Any?>?, result: Any?) {
        val log = logSink ?: return
        runCatching { captureUri(args, result, log) }
            .onFailure { log("copy onHookedCall failed: ${it.javaClass.simpleName}: ${it.message}") }
    }

    private fun captureUri(args: Array<Any?>?, result: Any?, log: (String) -> Unit) {
        val found = sequence {
            args?.forEach { yield(it) }
            yield(result)
        }.filterIsInstance<Uri>().firstOrNull { isScreenshotUri(it) } ?: return
        val normalized = normalizeMediaUri(found)
        if (normalized != latestUri) {
            latestUri = normalized
            log("copy injector: captured uri=$normalized")
        }
    }

    private fun isScreenshotUri(uri: Uri): Boolean = uri.toString().contains("images/media")

    /**
     * SystemUI sometimes carries a cross-user authority like `0@media`; the app process resolves
     * the plain `media` authority. Strip any `<n>@` prefix so the cached read succeeds.
     */
    private fun normalizeMediaUri(uri: Uri): Uri {
        val authority = uri.authority ?: return uri
        if (!authority.contains('@')) return uri
        return uri.buildUpon().authority(authority.substringAfterLast('@')).build()
    }

    // --- click → broadcast -------------------------------------------------------------

    private fun onCopyClicked(context: Context) {
        val log = logSink ?: {}
        runCatching {
            val uri = latestUri
            if (uri == null) {
                log("copy click: no screenshot uri captured")
                Toast.makeText(context, "Cliptic: no screenshot", Toast.LENGTH_SHORT).show()
                return
            }
            val secret = readSecret(context)
            if (secret == null) {
                log("copy click: secret unavailable")
                return
            }
            val intent = Intent(AppProtocol.ACTION_COPY_SCREENSHOT)
                .setPackage(AppProtocol.APP_PACKAGE)
                .putExtra(AppProtocol.EXTRA_SCREENSHOT_URI, uri.toString())
                .putExtra(AppProtocol.EXTRA_SECRET, secret)
            context.sendBroadcast(intent)
            log("copy broadcast sent uri=$uri")
        }.onFailure { log("copy click failed: ${it.javaClass.simpleName}: ${it.message}") }
    }

    private fun readSecret(context: Context): String? = runCatching {
        context.contentResolver.query(
            Uri.parse(AppProtocol.SECRET_PROVIDER_URI),
            arrayOf("secret"), null, null, null
        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }.getOrNull()

    private fun dp(context: Context, value: Float): Int =
        (value * context.resources.displayMetrics.density).toInt()

    class CopyHooker : XposedInterface.Hooker {
        companion object {
            @JvmStatic
            fun after(callback: XposedInterface.AfterHookCallback) {
                runCatching { onHookedCall(callback.args, callback.result) }
            }
        }
    }

    class UpdateActionsHooker : XposedInterface.Hooker {
        companion object {
            @JvmStatic
            fun before(callback: XposedInterface.BeforeHookCallback) {
                runCatching { onUpdateActionsBefore(callback.args) }
            }
        }
    }

    private const val SHELF_BINDER_CLASS =
        "com.android.systemui.screenshot.ui.binder.ScreenshotShelfViewBinder"
    private const val APPEARANCE_CLASS =
        "com.android.systemui.screenshot.ui.viewmodel.ActionButtonAppearance"
    private const val VIEWMODEL_CLASS =
        "com.android.systemui.screenshot.ui.viewmodel.ActionButtonViewModel"

    private val URI_SOURCE_CLASSES = listOf(
        "com.android.systemui.screenshot.ImageExporter",
        "com.android.systemui.screenshot.ActionIntentCreator",
        "com.android.systemui.screenshot.ScreenshotController"
    )

    // Stable, unlikely-to-collide id so the framework diffs our chip as the same item across the
    // repeated updateActions calls (native ids come from a small incrementing counter).
    private const val COPY_ACTION_ID = 0x436F7079 // "Copy"
    private const val DEFAULT_ICON_COLOR = 0xFF1F1F1F.toInt()
    private const val MAX_HOOKS = 32
}
