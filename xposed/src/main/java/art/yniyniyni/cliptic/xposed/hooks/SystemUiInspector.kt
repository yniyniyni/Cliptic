package art.yniyniyni.cliptic.xposed.hooks

import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * Diagnostics-only discovery harness for the SystemUI screenshot UI.
 *
 * The screenshot toolbar on Android 16 Pixel SystemUI is no longer the legacy
 * `com.android.systemui.screenshot.ScreenshotView`. Since the modern class names are not
 * known up front, this inspector:
 *  1. probes a seed list of candidate FQCNs and logs their members (see [dumpClass]),
 *  2. spiders from any resolved class through field/return/parameter types that stay inside
 *     the `com.android.systemui.screenshot` package, to discover the real graph,
 *  3. installs broad, defensive diagnostic hooks on plausible methods so that — on a real
 *     screenshot — it can dump the live view hierarchy and locate the screenshot [Uri].
 *
 * Nothing here mutates SystemUI behaviour; every probe/hook is wrapped in [runCatching] and
 * only logs. This is intended to be iterated on-device: read the log, refine [SEED_CANDIDATES]
 * or [HOOK_NAME_KEYWORDS], rebuild.
 */
object SystemUiInspector {
    @Volatile
    private var logSink: ((String) -> Unit)? = null

    @Volatile
    private var hierarchyDumpsRemaining = MAX_HIERARCHY_DUMPS

    fun run(module: XposedInterface, classLoader: ClassLoader, log: (String) -> Unit) {
        logSink = log
        val resolved = probeCandidates(classLoader, log)
        val discovered = spider(resolved, log)
        installDiagnosticHooks(module, discovered, log)
        log("inspector ready: probed=${resolved.size} discovered=${discovered.size}")
    }

    // --- Phase 1+2: static probing & spidering -----------------------------------------

    private fun probeCandidates(classLoader: ClassLoader, log: (String) -> Unit): List<Class<*>> {
        return SEED_CANDIDATES.mapNotNull { name ->
            runCatching { Class.forName(name, false, classLoader) }
                .onSuccess { dumpClass(it, log) }
                .getOrNull()
        }
    }

    /**
     * Walks outward from [seeds] following field types and method parameter/return types that
     * stay inside [SCREENSHOT_PACKAGE_PREFIX], dumping each newly found class. Bounded by
     * [SPIDER_MAX_CLASSES] and [SPIDER_MAX_DEPTH] so the log cannot run away.
     */
    private fun spider(seeds: List<Class<*>>, log: (String) -> Unit): List<Class<*>> {
        val visited = LinkedHashSet<Class<*>>(seeds)
        var frontier = seeds
        var depth = 0
        while (frontier.isNotEmpty() && depth < SPIDER_MAX_DEPTH && visited.size < SPIDER_MAX_CLASSES) {
            val next = LinkedHashSet<Class<*>>()
            for (clazz in frontier) {
                relatedScreenshotTypes(clazz).forEach { related ->
                    if (visited.size >= SPIDER_MAX_CLASSES) return@forEach
                    if (visited.add(related)) {
                        next.add(related)
                        dumpClass(related, log)
                    }
                }
            }
            frontier = next.toList()
            depth++
        }
        return visited.toList()
    }

    private fun relatedScreenshotTypes(clazz: Class<*>): Set<Class<*>> {
        val types = LinkedHashSet<Class<*>>()
        runCatching {
            clazz.declaredFields.forEach { field -> types.addType(field.type) }
            clazz.declaredMethods.forEach { method ->
                types.addType(method.returnType)
                method.parameterTypes.forEach { types.addType(it) }
            }
        }
        return types.filterTo(LinkedHashSet()) { it.name.startsWith(SCREENSHOT_PACKAGE_PREFIX) }
    }

    private fun MutableSet<Class<*>>.addType(type: Class<*>) {
        val component = if (type.isArray) type.componentType else type
        if (component != null && !component.isPrimitive) add(component)
    }

    private fun dumpClass(clazz: Class<*>, log: (String) -> Unit) {
        runCatching {
            log("CLASS ${clazz.name} extends ${clazz.superclass?.name} implements ${clazz.interfaces.joinToString { it.simpleName }}")
            clazz.declaredMethods
                .map { method -> "${method.returnType.simpleName} ${method.name}(${method.parameterTypes.joinToString { it.simpleName }})" }
                .sorted()
                .take(MAX_MEMBERS)
                .forEach { log("  m: $it") }
            clazz.declaredFields
                .map { field -> "${field.type.simpleName} ${field.name}" }
                .sorted()
                .take(MAX_MEMBERS)
                .forEach { log("  f: $it") }
        }.onFailure { log("dumpClass ${clazz.name} failed: ${it.javaClass.simpleName}: ${it.message}") }
    }

    // --- Phase 3: dynamic diagnostic hooks ---------------------------------------------

    private fun installDiagnosticHooks(
        module: XposedInterface,
        classes: List<Class<*>>,
        log: (String) -> Unit
    ) {
        var hooks = 0
        for (clazz in classes) {
            if (hooks >= MAX_HOOKS) break
            val methods = runCatching { clazz.declaredMethods.toList() }.getOrDefault(emptyList())
            for (method in methods) {
                if (hooks >= MAX_HOOKS) break
                if (!isInteresting(method)) continue
                val hooked = runCatching {
                    module.hook(method).intercept(DiagHooker())
                    true
                }.onFailure {
                    log("hook ${clazz.simpleName}#${method.name} failed: ${it.javaClass.simpleName}: ${it.message}")
                }.getOrDefault(false)
                if (hooked) {
                    hooks++
                    log("hooked ${clazz.name}#${method.name}(${method.parameterTypes.joinToString { it.simpleName }})")
                }
            }
        }
        log("diagnostic hooks installed=$hooks")
    }

    private fun isInteresting(method: Method): Boolean {
        if (Modifier.isAbstract(method.modifiers)) return false
        val params = method.parameterTypes
        if (params.any { it == Uri::class.java || View::class.java.isAssignableFrom(it) }) return true
        val name = method.name.lowercase()
        return HOOK_NAME_KEYWORDS.any { name.contains(it) }
    }

    /** Called from [DiagHooker.after] on every hooked invocation. */
    fun onHookedCall(member: java.lang.reflect.Member?, thisObject: Any?, args: Array<Any?>?, result: Any?) {
        val log = logSink ?: return
        runCatching {
            val owner = (member as? Method)?.declaringClass?.simpleName ?: member?.declaringClass?.simpleName
            log("CALL $owner#${member?.name} args=${args?.size ?: 0}")

            val candidates = buildList {
                thisObject?.let(::add)
                args?.filterNotNull()?.let(::addAll)
                result?.let(::add)
            }

            // Locate the screenshot Uri across this/args/result and their fields.
            candidates.forEach { scanForUri(it, depth = 0, visited = HashSet(), log = log) }

            // Dump the live view hierarchy once we can reach a View (the action toolbar lives here).
            val view = candidates.firstNotNullOfOrNull { it as? View }
            if (view != null && hierarchyDumpsRemaining > 0) {
                hierarchyDumpsRemaining--
                log("VIEW TREE from ${view.javaClass.simpleName} (root):")
                dumpView(view.rootView ?: view, indent = 0, count = intArrayOf(0), log = log)
            }
        }.onFailure { log("onHookedCall failed: ${it.javaClass.simpleName}: ${it.message}") }
    }

    private fun dumpView(view: View, indent: Int, count: IntArray, log: (String) -> Unit) {
        if (count[0] >= MAX_VIEW_NODES || indent >= MAX_VIEW_DEPTH) return
        count[0]++
        val id = runCatching {
            if (view.id != View.NO_ID) view.resources.getResourceEntryName(view.id) else "no-id"
        }.getOrDefault("?")
        val text = (view as? TextView)?.text?.toString()?.take(40)
        log("  ".repeat(indent) + "- ${view.javaClass.name} id=$id vis=${view.visibility}" + (text?.let { " text=\"$it\"" } ?: ""))
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                if (count[0] >= MAX_VIEW_NODES) break
                view.getChildAt(i)?.let { dumpView(it, indent + 1, count, log) }
            }
        }
    }

    private fun scanForUri(obj: Any?, depth: Int, visited: MutableSet<Any>, log: (String) -> Unit) {
        if (obj == null || depth > URI_SCAN_DEPTH) return
        if (!visited.add(obj)) return
        when (obj) {
            is Uri -> {
                log("  URI found at depth=$depth: $obj")
                return
            }
            is View, is CharSequence -> return
        }
        val clazz = obj.javaClass
        if (clazz.isArray || clazz.name.startsWith("android.") || clazz.name.startsWith("java.")) return
        runCatching {
            clazz.declaredFields.forEach { field ->
                if (Modifier.isStatic(field.modifiers)) return@forEach
                runCatching {
                    field.isAccessible = true
                    val value = field.get(obj) ?: return@forEach
                    when {
                        value is Uri -> log("  URI field: ${clazz.simpleName}.${field.name} = $value")
                        field.type.name.startsWith(SCREENSHOT_PACKAGE_PREFIX) ->
                            scanForUri(value, depth + 1, visited, log)
                    }
                }
            }
        }
    }

    class DiagHooker : XposedInterface.Hooker {
        override fun intercept(chain: XposedInterface.Chain): Any? {
            val result = chain.proceed()
            runCatching {
                onHookedCall(chain.executable, chain.thisObject, chain.args.toTypedArray(), result)
            }
            return result
        }
    }

    private const val SCREENSHOT_PACKAGE_PREFIX = "com.android.systemui.screenshot"

    private val SEED_CANDIDATES = listOf(
        "com.android.systemui.screenshot.ScreenshotController",
        "com.android.systemui.screenshot.ScreenshotShelfViewProxy",
        "com.android.systemui.screenshot.ui.ScreenshotShelfView",
        "com.android.systemui.screenshot.ui.viewmodel.ScreenshotViewModel",
        "com.android.systemui.screenshot.ui.binder.ScreenshotShelfViewBinder",
        "com.android.systemui.screenshot.ui.binder.ActionButtonViewBinder",
        "com.android.systemui.screenshot.ScreenshotData",
        "com.android.systemui.screenshot.SaveImageInBackgroundData",
        "com.android.systemui.screenshot.ActionIntentCreator",
        "com.android.systemui.screenshot.ScreenshotView" // legacy fallback
    )

    private val HOOK_NAME_KEYWORDS = listOf(
        "save", "show", "bind", "setscreenshot", "setdata", "present", "addaction", "addchip"
    )

    private const val MAX_MEMBERS = 60
    private const val SPIDER_MAX_CLASSES = 40
    private const val SPIDER_MAX_DEPTH = 2
    private const val MAX_HOOKS = 48
    private const val MAX_HIERARCHY_DUMPS = 8
    private const val MAX_VIEW_NODES = 400
    private const val MAX_VIEW_DEPTH = 16
    private const val URI_SCAN_DEPTH = 3
}
