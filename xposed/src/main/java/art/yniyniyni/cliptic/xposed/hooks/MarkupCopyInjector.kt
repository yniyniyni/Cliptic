package art.yniyniyni.cliptic.xposed.hooks

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.Toast
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method

/**
 * Adds a "Copy" button to the Pixel screenshot **markup/edit** screen
 * (`com.google.android.markup` → `AnnotateActivity`, the editor opened by the screenshot "Edit"
 * action). This is a different surface from the floating screenshot shelf handled by
 * [CopyButtonInjector].
 *
 * Unlike the shelf, this needs **no IPC to the Cliptic app**: the Markup app already has a built-in
 * copy-to-clipboard export path (discovered by decompiling `apy`/`AnnotateActivity`). The export
 * entry point `AnnotateActivity.I(int mode)` renders the *annotated* bitmap via the ink engine,
 * writes it to Markup's own `FileProvider`, and for `mode = 2` calls
 * `ClipboardManager.setPrimaryClip(ClipData.newUri(…))` followed by `finishAndRemoveTask()`. So our
 * button simply invokes that pipeline — full-resolution edited PNG, no MediaStore write, no trash.
 *
 * The only adjustment is suppressing the trailing `finishAndRemoveTask()` so a Copy does not close
 * the editor (the user asked for a non-disruptive "just copy"). The clipboard write happens *before*
 * the finish in Markup's code, so skipping the finish keeps the editor fully intact.
 *
 * The action-bar / button view ids (`action_bar`, `share`, `delete`) are **non-obfuscated resource
 * names** and stay stable across Markup updates, so view injection is robust here (the top bar is a
 * `RelativeLayout` built once in `onCreate`, not an index-recycled list — unlike the shelf). The
 * obfuscated bits (`AnnotateActivity.I`, `mode = 2`) are version-specific and resolved defensively;
 * if they ever stop resolving, the button just no-ops with a toast — Markup never crashes.
 */
object MarkupCopyInjector {
    @Volatile
    private var logSink: ((String) -> Unit)? = null

    @Volatile
    private var exportMethod: Method? = null

    /** Arms one-shot suppression of [Activity.finishAndRemoveTask] after a Copy tap (uptime millis). */
    @Volatile
    private var suppressFinishUntilMs: Long = 0L

    fun install(module: XposedInterface, classLoader: ClassLoader, log: (String) -> Unit) {
        logSink = log
        runCatching {
            val annotate = Class.forName(ANNOTATE_ACTIVITY, false, classLoader)
            exportMethod = annotate.getDeclaredMethod(EXPORT_METHOD, Int::class.javaPrimitiveType)
                .apply { isAccessible = true }

            val onCreate = annotate.getDeclaredMethod("onCreate", Bundle::class.java)
            module.hook(onCreate, InjectHooker::class.java)

            val finish = Activity::class.java.getDeclaredMethod("finishAndRemoveTask")
            module.hook(finish, SuppressFinishHooker::class.java)

            log("markup copy injector ready: export=${exportMethod != null}")
        }.onFailure { log("markup copy injector install failed: ${it.javaClass.simpleName}: ${it.message}") }
    }

    // --- button injection (InjectHooker.after on AnnotateActivity.onCreate) -------------

    private fun injectButton(activity: Activity) {
        val log = logSink ?: {}
        runCatching {
            val res = activity.resources
            val pkg = activity.packageName
            fun id(name: String) = res.getIdentifier(name, "id", pkg)

            val actionBarId = id("action_bar")
            val shareId = id("share")
            if (actionBarId == 0 || shareId == 0) {
                log("markup copy: action_bar/share id not found")
                return
            }
            val actionBar = activity.findViewById<View>(actionBarId) as? RelativeLayout ?: return
            val share = activity.findViewById<ImageButton>(shareId) ?: return
            if (actionBar.findViewWithTag<View>(COPY_TAG) != null) return // idempotent across recreates

            val tint = share.imageTintList
            val copy = ImageButton(activity).apply {
                id = View.generateViewId()
                tag = COPY_TAG
                background = share.background?.constantState?.newDrawable(res)?.mutate()
                scaleType = ImageView.ScaleType.CENTER
                setPadding(share.paddingLeft, share.paddingTop, share.paddingRight, share.paddingBottom)
                setImageDrawable(CopyIconDrawable(tint?.defaultColor ?: DEFAULT_ICON_COLOR, dp(activity, 24f)))
                imageTintList = tint
                contentDescription = "Copy"
                tooltipText = "Copy"
                setOnClickListener { onCopyClicked(activity) }
            }
            // Insert Copy between Delete and Share: anchor it to Share's start, then re-point Delete
            // (currently START_OF share) to Copy. Align top/bottom to Share so it lines up exactly
            // with the native icons (the action bar carries a status-bar top inset, so a plain
            // CENTER_VERTICAL would float too high).
            val lp = RelativeLayout.LayoutParams(share.layoutParams.width, share.layoutParams.height).apply {
                addRule(RelativeLayout.START_OF, shareId)
                addRule(RelativeLayout.ALIGN_TOP, shareId)
                addRule(RelativeLayout.ALIGN_BOTTOM, shareId)
            }
            actionBar.addView(copy, lp)

            val deleteId = id("delete")
            val delete = if (deleteId != 0) activity.findViewById<View>(deleteId) else null
            val deleteLp = delete?.layoutParams as? RelativeLayout.LayoutParams
            if (deleteLp != null) {
                deleteLp.addRule(RelativeLayout.START_OF, copy.id) // was START_OF share
                delete.layoutParams = deleteLp
            }
            log("markup copy button injected (re-anchored delete=${deleteLp != null})")
        }.onFailure { log("markup copy inject failed: ${it.javaClass.simpleName}: ${it.message}") }
    }

    private fun onCopyClicked(activity: Activity) {
        val log = logSink ?: {}
        runCatching {
            val export = exportMethod
            if (export == null) {
                log("markup copy: export method unresolved")
                Toast.makeText(activity, "Cliptic: copy unavailable", Toast.LENGTH_SHORT).show()
                return
            }
            // Arm finish suppression so Markup's native copy mode keeps the editor open, then drive
            // its render → FileProvider → clipboard pipeline.
            suppressFinishUntilMs = SystemClock.uptimeMillis() + FINISH_SUPPRESS_WINDOW_MS
            export.invoke(activity, COPY_MODE)
            Toast.makeText(activity, "Copied", Toast.LENGTH_SHORT).show()
            log("markup copy invoked mode=$COPY_MODE")
        }.onFailure {
            suppressFinishUntilMs = 0L
            log("markup copy failed: ${it.javaClass.simpleName}: ${it.message}")
        }
    }

    private fun onFinishAndRemoveTask(callback: XposedInterface.BeforeHookCallback) {
        if (suppressFinishUntilMs == 0L) return
        if (SystemClock.uptimeMillis() > suppressFinishUntilMs) {
            suppressFinishUntilMs = 0L
            return
        }
        suppressFinishUntilMs = 0L
        runCatching { callback.returnAndSkip(null) }
        logSink?.invoke("markup copy: suppressed finishAndRemoveTask (editor kept open)")
    }

    private fun dp(context: Context, value: Float): Int =
        (value * context.resources.displayMetrics.density).toInt()

    class InjectHooker : XposedInterface.Hooker {
        companion object {
            @JvmStatic
            fun after(callback: XposedInterface.AfterHookCallback) {
                runCatching { (callback.thisObject as? Activity)?.let { injectButton(it) } }
            }
        }
    }

    class SuppressFinishHooker : XposedInterface.Hooker {
        companion object {
            @JvmStatic
            fun before(callback: XposedInterface.BeforeHookCallback) {
                runCatching { onFinishAndRemoveTask(callback) }
            }
        }
    }

    private const val ANNOTATE_ACTIVITY = "com.google.android.markup.AnnotateActivity"

    // Obfuscated export entry point + its copy-mode argument (version-specific; see class kdoc).
    private const val EXPORT_METHOD = "I"
    private const val COPY_MODE = 2

    private const val COPY_TAG = "cliptic_copy_button"
    private const val DEFAULT_ICON_COLOR = 0xFF1F1F1F.toInt()
    private const val FINISH_SUPPRESS_WINDOW_MS = 4_000L
}
