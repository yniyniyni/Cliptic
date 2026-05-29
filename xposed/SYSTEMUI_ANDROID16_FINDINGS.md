# SystemUI screenshot internals — Android 16 Pixel (Vector / libxposed API 100)

On-device diagnostics captured by the `SystemUiInspector` harness (Pixel 8, Android 16,
framework: Vector v2.0 (3021) by JingMatrix). This is the factual basis for implementing the
LSPosed "Copy" button. Re-run the harness (take a screenshot, read logcat tag `ClipticXposed`)
to refresh if the SystemUI build changes.

## Framework / API contract (critical)

The device framework implements the **original libxposed API 100** (interface-based params,
`before`/`after` Hooker callbacks), NOT the Maven `io.github.libxposed:api:101` (which is
Chain-based and incompatible). Key signatures recovered from
`/data/adb/modules/zygisk_vector/framework/lspd.dex`:

- `XposedModule(XposedInterface base, ModuleLoadedParam param)` — 2-arg constructor.
- `XposedInterface.hook(Method, Class<? extends Hooker>) : MethodUnhooker` (and a priority
  overload). There is **no** `hook(Executable).intercept(...)`. The framework reflectively
  invokes static `before(BeforeHookCallback)` / `after(AfterHookCallback)` on the Hooker class.
- `XposedModuleInterface$ModuleLoadedParam` / `$PackageLoadedParam` are **interfaces**
  (`getProcessName`, `getPackageName`, `getClassLoader`/`getDefaultClassLoader`, …).

The vendored `xposed/libs/libxposed-api-100.jar` is a hand-built compile-only stub matching this
exact contract (sources in `xposed/libs/api-100-stub/`).

## Screenshot view hierarchy (the action toolbar)

Action chips are added to `LinearLayout id=screenshot_actions`:

```
com.android.systemui.screenshot.ui.ScreenshotShelfView            id=no-id
  androidx.constraintlayout.widget.ConstraintLayout               id=screenshot_static
    android.view.View                                             id=screenshot_saving_live_region
    android.view.View                                             id=screenshot_preview_border
    android.widget.ImageView                                      id=screenshot_preview
    android.widget.ImageView                                      id=screenshot_preview_blur
    android.widget.FrameLayout                                    id=actions_container_background
      android.widget.HorizontalScrollView                        id=actions_container
        android.widget.LinearLayout                              id=screenshot_actions   <-- chips
    android.widget.ImageView                                      id=screenshot_badge
    android.widget.ImageView                                      id=screenshot_scrollable_preview
    androidx.constraintlayout.widget.Guideline                   id=guideline
    android.widget.FrameLayout                                    id=screenshot_message_container
      android.widget.LinearLayout                                id=work_profile_first_run
      android.widget.FrameLayout                                 id=screenshot_detection_notice
  android.widget.ImageView                                        id=screenshot_scrolling_scrim
  android.widget.ImageView                                        id=screenshot_flash
```

## Key classes / methods (hooked & confirmed firing on a real screenshot)

- `com.android.systemui.screenshot.ScreenshotController implements ScreenshotHandler`
  - `handleScreenshot(ScreenshotData, …)`, `saveScreenshotInBackground(ScreenshotData, UUID, …, Consumer)`
  - `access$logScreenshotResultStatus(ScreenshotController, Uri, UserHandle)` — has final Uri
  - fields: `actionExecutor: ActionExecutor`, `actionIntentCreator: ActionIntentCreator`, …
- `com.android.systemui.screenshot.ui.binder.ScreenshotShelfViewBinder`
  - `bind(ScreenshotShelfView, ScreenshotViewModel, ScreenshotAnimationController, LayoutInflater, …)`
  - `access$updateActions(binder, List<ActionButtonViewModel>, …)` — populates the chip row
- `com.android.systemui.screenshot.ui.binder.ActionButtonViewBinder`
  - `bind(LinearLayout buttonView, ActionButtonViewModel)` — binds one chip
- `com.android.systemui.screenshot.ui.viewmodel.ActionButtonViewModel` — chip model (icon/label/onClick)
- `com.android.systemui.screenshot.ActionIntentCreator`
  - `createShare(Uri, String mime, String)`, `createEdit(Uri, …)` — Uri-bearing
- `com.android.systemui.screenshot.ImageExporter`
  - `writeImage(ContentResolver, Bitmap, CompressFormat, int, Uri)`, `publishEntry(ContentResolver, Uri)`

## Screenshot Uri

Form: `content://0@media/external/images/media/<id>` (a continuation field also held the
prefix-less `content://media/external/images/media/<id>`). Reliably available at
`ActionIntentCreator.createShare/createEdit`, `ImageExporter.publishEntry/writeImage`, and
`ScreenshotController.access$logScreenshotResultStatus`.

## Copy button — implemented (`CopyButtonInjector`)

Verified working on-device (Pixel 8, Android 16). The implementation took the **fallback**
(programmatic view injection) path because it needs no knowledge of the `ActionButtonViewModel`
constructor and survives SystemUI churn:

- One shared after-hook ([`CopyHooker`]) is installed on two method sets:
  - **View binders** (`ScreenshotShelfViewBinder#bind`/`access$updateActions`,
    `ActionButtonViewBinder#bind`) — give the live `ScreenshotShelfView`. The screenshot UI runs
    in a **separate process** `com.android.systemui:screenshot`, so the module loads/hooks there.
  - **Uri sources** (`ImageExporter#publishEntry/writeImage/createEntry`,
    `ActionIntentCreator#createShare/createEdit`, `ScreenshotController#access$logScreenshotResultStatus`)
    — the latest screenshot `Uri` is stashed in `latestUri`.
- On each toolbar (re)build we `post` an idempotent re-insert of a tagged Copy chip into
  `LinearLayout id=screenshot_actions` (resolved via `getIdentifier("screenshot_actions","id","com.android.systemui")`).
  `updateActions` clears the row each pass, so re-inserting on every call keeps exactly one chip.
- The chip mimics a sibling chip's `LayoutParams` / background / text styling so it looks native.
- The Uri is normalized (`<n>@media` → `media`) so the app process can resolve it.
- On tap: read the shared secret from `XposedSecretProvider`, then broadcast
  `ACTION_COPY_SCREENSHOT` to `art.yniyniyni.cliptic` with Uri + secret. The app caches, copies to
  the clipboard, toasts, and ACKs back → `CopyAckReceiver` trashes the original (`result=1`).

`isModuleActive` is hooked in the app process (`AppHook`) to return `true`, so the standalone UI
shows "LSPosed active".

`SystemUiInspector` is retained (no longer invoked) for re-running discovery if a SystemUI build
changes the class/view map above.
