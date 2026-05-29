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
  - `access$updateActions(ScreenshotShelfViewBinder, List<ActionButtonViewModel>, AnimationState, ScreenshotShelfView, LayoutInflater)`
    — **this is the injection point**: the `List` arg is the full chip set the row will render.
- `com.android.systemui.screenshot.ui.binder.ActionButtonViewBinder`
  - `static bind(View buttonView, ActionButtonViewModel)` — binds one chip
- `com.android.systemui.screenshot.ui.viewmodel.ActionButtonViewModel` — chip model. Ctor (verbatim):
  `ActionButtonViewModel(ActionButtonAppearance appearance, int id, boolean visible, kotlin.jvm.functions.Function0 onClicked)`.
  Fields: `appearance, id, nextId, onClicked, visible`. Native `id`s come from a small incrementing
  counter, so a large constant id (we use `0x436F7079`) won't collide and stays diff-stable.
- `com.android.systemui.screenshot.ui.viewmodel.ActionButtonAppearance` — chip appearance. Ctor:
  `ActionButtonAppearance(Drawable icon, CharSequence label, CharSequence description, boolean tint)`.
  Empty `label` ⇒ icon-only chip; `description` becomes the content-description; `tint=true` lets the
  framework recolour the icon to the theme (a custom `Drawable` must honour `setTint`/`setTintList`).
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

Verified working on-device (Pixel 8, Android 16). The chip is added by **model injection**, NOT by
mutating the view tree:

- **Why not view injection:** an earlier version added a raw `View` at index 0 of the
  framework-managed `LinearLayout id=screenshot_actions`. `updateActions` reconciles that row's
  children **by index** (reuse child[i] for action[i], add/remove the remainder), so an extra child
  shifts every index and the binder ends up **appending duplicate native chips**. It also blinked:
  re-adding on a `post{}` lands one frame after the framework's clear+rebuild, so each of the ~3
  `updateActions` passes briefly drew the row without our chip. Don't inject views into that row.
- **What works:** a *before* hook on `ScreenshotShelfViewBinder.access$updateActions(…)` prepends a
  Copy `ActionButtonViewModel` to the `List` argument (`callback.args[1]`, replaced with
  `[ours] + original`). The framework then builds, styles, recycles, and animates our chip exactly
  like Share/Edit — no duplicates, no blink, native circular styling for free. The stable `id`
  keeps it diff-stable across the repeated `updateActions` calls.
- **Building the model** (reflection against SystemUI's classloader):
  `ActionButtonAppearance(CopyIconDrawable, "" /*label*/, "Copy screenshot" /*desc*/, true /*tint*/)`
  → `ActionButtonViewModel(appearance, 0x436F7079, true, onClicked)`. `onClicked` is a
  `kotlin.jvm.functions.Function0` created via `java.lang.reflect.Proxy` over **SystemUI's**
  `Function0` interface — a Kotlin lambda compiled in our APK implements *our* `Function0` and would
  not be type-compatible with the framework ctor. The proxy's `invoke` returns SystemUI's
  `kotlin.Unit.INSTANCE`.
- **Icon:** `CopyIconDrawable` draws the content-copy glyph and honours `setTint`/`setTintList`/
  `setColorFilter`, so `tint=true` recolours it to the theme (matches the native icon colour).
- **Uri capture:** a separate after-hook ([`CopyHooker`]) on the Uri-bearing methods of
  `ImageExporter` / `ActionIntentCreator` / `ScreenshotController` stashes the latest screenshot
  `Uri` in `latestUri`, normalized (`<n>@media` → `media`) so the app process can resolve it. The
  screenshot UI runs in a **separate process** `com.android.systemui:screenshot`; reinstalling the
  module requires killing *both* `com.android.systemui` and `com.android.systemui:screenshot`.
- **On tap:** read the shared secret from `XposedSecretProvider`, broadcast `ACTION_COPY_SCREENSHOT`
  to `art.yniyniyni.cliptic` with Uri + secret. The app caches, copies to the clipboard, toasts,
  and ACKs back → `CopyAckReceiver` trashes the original (`result=1`).

`isModuleActive` is hooked in the app process (`AppHook`) to return `true`, so the standalone UI
shows "LSPosed active".

`SystemUiInspector` is retained (no longer invoked) for re-running discovery if a SystemUI build
changes the class/view map above.
