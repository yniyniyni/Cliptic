package art.yniyniyni.cliptic.xposed.hooks

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import art.yniyniyni.cliptic.xposed.AppProtocol
import io.github.libxposed.api.XposedInterface
import java.lang.ref.WeakReference
import java.lang.reflect.Member
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * Injects a "Copy" action chip into the Android 16 Pixel SystemUI screenshot toolbar and, on tap,
 * broadcasts the screenshot [Uri] to the Cliptic app (which caches it, writes it to the clipboard,
 * and ACKs back to silently trash the original).
 *
 * Class/method names and the view hierarchy are documented in
 * `xposed/SYSTEMUI_ANDROID16_FINDINGS.md`. Two concerns are wired with one shared after-hook
 * ([CopyHooker] → [onHookedCall]):
 *  - **Uri capture:** [ImageExporter] / [ActionIntentCreator] / [ScreenshotController] methods that
 *    carry the screenshot [Uri] fire during save; we stash the latest one ([latestUri]).
 *  - **Chip injection:** the shelf binder's `bind`/`updateActions` give us the live
 *    `ScreenshotShelfView`; we (re)insert our chip into `LinearLayout id=screenshot_actions` after
 *    the framework repopulates it.
 *
 * Everything is defensive: every probe/hook/inject step is wrapped in [runCatching] so a SystemUI
 * change can only make Copy silently absent — never crash `com.android.systemui`.
 */
object CopyButtonInjector {
    @Volatile
    private var logSink: ((String) -> Unit)? = null

    @Volatile
    private var latestUri: Uri? = null

    @Volatile
    private var shelfRef: WeakReference<View>? = null

    fun install(module: XposedInterface, classLoader: ClassLoader, log: (String) -> Unit) {
        logSink = log

        val viewClasses = resolve(classLoader, VIEW_BINDER_CLASSES, log)
        val uriClasses = resolve(classLoader, URI_SOURCE_CLASSES, log)

        var hooks = 0
        hooks += hookMethods(module, viewClasses, ::isViewMethod, log)
        hooks += hookMethods(module, uriClasses, ::isUriMethod, log)
        log("copy injector ready: hooks=$hooks")
    }

    // --- hook wiring -------------------------------------------------------------------

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
                if (ok) {
                    hooks++
                    log("copy hook ${clazz.name}#${method.name}")
                }
            }
        }
        return hooks
    }

    private fun isViewMethod(method: Method): Boolean {
        if (Modifier.isAbstract(method.modifiers)) return false
        val name = method.name.lowercase()
        if (name.contains("bind") || name.contains("updateaction")) return true
        return method.parameterTypes.any { View::class.java.isAssignableFrom(it) }
    }

    private fun isUriMethod(method: Method): Boolean {
        if (Modifier.isAbstract(method.modifiers)) return false
        if (method.returnType == Uri::class.java) return true
        return method.parameterTypes.any { it == Uri::class.java }
    }

    // --- runtime: invoked from CopyHooker.after ----------------------------------------

    fun onHookedCall(member: Member?, thisObject: Any?, args: Array<Any?>?, result: Any?) {
        val log = logSink ?: return
        runCatching {
            captureUri(args, result, log)

            val name = member?.name?.lowercase().orEmpty()
            val view = findView(thisObject, args)
            if (view != null) shelfRef = WeakReference(view)

            // (Re)inject only when the toolbar is (re)built or a fresh view surfaced; injection is
            // idempotent so extra posts are harmless.
            if (view != null || name.contains("bind") || name.contains("updateaction")) {
                val target = view ?: shelfRef?.get()
                target?.post { runCatching { ensureCopyChip(target, log) } }
            }
        }.onFailure { log("copy onHookedCall failed: ${it.javaClass.simpleName}: ${it.message}") }
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

    private fun isScreenshotUri(uri: Uri): Boolean =
        uri.toString().contains("images/media")

    /**
     * SystemUI sometimes carries a cross-user authority like `0@media`; the app process resolves
     * the plain `media` authority. Strip any `<n>@` prefix so the cached read succeeds.
     */
    private fun normalizeMediaUri(uri: Uri): Uri {
        val authority = uri.authority ?: return uri
        if (!authority.contains('@')) return uri
        return uri.buildUpon().authority(authority.substringAfterLast('@')).build()
    }

    private fun findView(thisObject: Any?, args: Array<Any?>?): View? {
        (thisObject as? View)?.let { return it }
        return args?.filterIsInstance<View>()?.firstOrNull()
    }

    // --- chip injection ----------------------------------------------------------------

    private fun ensureCopyChip(anchor: View, log: (String) -> Unit) {
        val root = anchor.rootView ?: anchor
        val actionsId = root.resources.getIdentifier(
            "screenshot_actions", "id", AppProtocol.SYSTEMUI_PACKAGE
        )
        if (actionsId == 0) {
            log("copy injector: screenshot_actions id not found")
            return
        }
        val container = root.findViewById<View>(actionsId) as? LinearLayout ?: return
        if (container.findViewWithTag<View>(CHIP_TAG) != null) return

        val chip = buildChip(container) ?: return
        container.addView(chip, 0)
        log("copy chip injected into screenshot_actions")
    }

    /** Builds a chip that mimics the styling of an existing sibling chip (background/text/sizing). */
    private fun buildChip(container: LinearLayout): View? {
        val context = container.context
        val template = firstChipTemplate(container)
        val templateLabel = template?.let { findTextView(it) }

        val chip = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            tag = CHIP_TAG
            layoutParams = cloneLayoutParams(template?.layoutParams)
            template?.background?.constantState?.newDrawable(context.resources)?.mutate()
                ?.let { background = it }
            if (template != null) {
                setPaddingRelative(
                    template.paddingStart, template.paddingTop,
                    template.paddingEnd, template.paddingBottom
                )
            } else {
                val pad = dp(context, 12f)
                setPaddingRelative(pad, dp(context, 8f), pad, dp(context, 8f))
            }
        }

        val label = TextView(context).apply {
            text = "Copy"
            isSingleLine = true
            gravity = Gravity.CENTER
            if (templateLabel != null) {
                setTextColor(templateLabel.currentTextColor)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, templateLabel.textSize)
                typeface = templateLabel.typeface
            }
        }
        chip.addView(label)
        chip.setOnClickListener { onCopyClicked(context) }
        return chip
    }

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

    // --- view helpers ------------------------------------------------------------------

    private fun firstChipTemplate(container: LinearLayout): ViewGroup? {
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            if (child is ViewGroup && child.tag != CHIP_TAG) return child
        }
        return null
    }

    private fun findTextView(view: View): TextView? = when (view) {
        is TextView -> view
        is ViewGroup -> (0 until view.childCount)
            .firstNotNullOfOrNull { findTextView(view.getChildAt(it)) }
        else -> null
    }

    private fun cloneLayoutParams(source: ViewGroup.LayoutParams?): LinearLayout.LayoutParams =
        when (source) {
            is ViewGroup.MarginLayoutParams -> LinearLayout.LayoutParams(source)
            is ViewGroup.LayoutParams -> LinearLayout.LayoutParams(source)
            else -> LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

    private fun dp(context: Context, value: Float): Int =
        (value * context.resources.displayMetrics.density).toInt()

    class CopyHooker : XposedInterface.Hooker {
        companion object {
            @JvmStatic
            fun after(callback: XposedInterface.AfterHookCallback) {
                runCatching {
                    onHookedCall(callback.member, callback.thisObject, callback.args, callback.result)
                }
            }
        }
    }

    private val VIEW_BINDER_CLASSES = listOf(
        "com.android.systemui.screenshot.ui.binder.ScreenshotShelfViewBinder",
        "com.android.systemui.screenshot.ui.binder.ActionButtonViewBinder"
    )

    private val URI_SOURCE_CLASSES = listOf(
        "com.android.systemui.screenshot.ImageExporter",
        "com.android.systemui.screenshot.ActionIntentCreator",
        "com.android.systemui.screenshot.ScreenshotController"
    )

    private const val CHIP_TAG = "cliptic_copy_chip"
    private const val MAX_HOOKS = 32
}
