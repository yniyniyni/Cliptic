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

## Implications for the Copy button (next iteration)

- **Preferred:** inject a Copy `ActionButtonViewModel` into the list consumed by
  `ScreenshotShelfViewBinder.updateActions(...)` (hook it, append our model), reusing the
  framework's chip styling/binding. Source the Uri from the same `ScreenshotData` /
  `ActionIntentCreator` path.
- **Fallback:** programmatically add a chip view into `LinearLayout id=screenshot_actions`
  (resolve via `ScreenshotShelfView` → `screenshot_static` → `actions_container` → `screenshot_actions`).
- On click, send `ACTION_COPY_SCREENSHOT` (to be added to `AppProtocol`) to `art.yniyniyni.cliptic`
  with the Uri + shared secret; the app already handles copy + ACK.
