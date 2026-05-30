# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

Cliptic (`clip + automatic`) is an Android app that copies new screenshots to the clipboard automatically, so they can be pasted without opening the gallery. `screenshot-clip-spec.md` is the product/architecture source of truth; `AGENTS.md` holds the locked decisions and working rules — read both before non-trivial changes. User-facing naming is always `Cliptic`, never the older spec name `ScreenshotClip`.

## Build & Test

JDK 17, Android SDK 36, `minSdk = 34`. All commands use the Gradle wrapper.

```sh
./gradlew assembleDebug              # all debug APKs
./gradlew :app:assembleDebug         # standalone app only
./gradlew :xposed:assembleDebug      # LSPosed module only
./gradlew :app:installDebug          # install standalone app
./gradlew test                       # all local unit tests
./gradlew :app:testDebugUnitTest --tests "art.yniyniyni.cliptic.ExampleUnitTest"
```

Use `rg` for searching the tree.

## Module Architecture

Three Gradle modules, all under package root `art.yniyniyni.cliptic`:

- **`:core`** — pure Android library, no Compose. Owns the reusable primitives: `ClipboardWriter` (writes a `content://` URI onto the clipboard), `ScreenshotDetector` (MediaStore `ContentObserver`), `ScreenshotFileManager` (cache + expiry), `util/XposedBridge` (active-module stub).
- **`:app`** — the primary, fully-usable standalone APK (`art.yniyniyni.cliptic`). Compose/Material 3 UI, foreground service, receivers, QS tile, IPC provider, cleanup flow. Depends on `:core`.
- **`:xposed`** — separate LSPosed module APK (`art.yniyniyni.cliptic.xposed`). Depends on `:core`. Targets Android 16 Pixel SystemUI. Contains four active hooks (see below) plus the retained `SystemUiInspector` discovery harness.

## Standalone copy flow

`ScreenshotService` (`:app`, foreground `specialUse` service) creates a `ScreenshotDetector` (`:core`) → detector observes `MediaStore.Images.Media.EXTERNAL_CONTENT_URI`, filters recent images whose `RELATIVE_PATH` contains `Screenshots`, dedupes, and waits ~500 ms for the file to finish writing → `ScreenshotService.copyScreenshotWithRetry` caches the image via `ScreenshotFileManager` into `cacheDir`, exposed through `FileProvider`, and puts that cached URI on the clipboard via `ClipboardWriter`. Cached files expire after `cache_duration_ms` (default 1h). The Share Sheet path runs the same cache→copy through `ShareReceiverActivity`.

## Original-screenshot cleanup

`OriginalScreenshotCleanup` (invoked when `remove_original_after_copy` is on) uses `MediaStore.createTrashRequest`. If `MediaStore.canManageMedia` is true it trashes silently with a fast retry ladder (`scheduleFastTrashRetries`) plus a WorkManager job (`PendingOriginalTrashWorker`) for durability; otherwise it queues the URI and posts a confirmation notification (`RemoveOriginalActivity` prompt). Pending originals are persisted as a newline-joined queue in `KEY_PENDING_ORIGINAL_QUEUE` (with migration from the legacy single-URI key).

## Xposed hooks

### CopyButtonInjector (screenshot shelf)

Injects a "Copy" chip into the Android 16 Pixel SystemUI screenshot shelf toolbar. Uses **model injection**: a `before` hook on `ScreenshotShelfViewBinder.access$updateActions` prepends a `ActionButtonViewModel` (with a `CopyIconDrawable`) to the action list so the framework renders, styles, and recycles the chip exactly like Share/Edit. The screenshot URI is captured separately via `after` hooks on Uri-bearing methods in `ImageExporter`, `ActionIntentCreator`, and `ScreenshotController`. On tap, broadcasts `ACTION_COPY_SCREENSHOT` (Uri + secret) to the Cliptic app.

### MarkupCopyInjector (Markup / screenshot editor)

Adds a "Copy" button to the Pixel Markup editor (`com.google.android.markup` → `AnnotateActivity`). Hooks `AnnotateActivity.onCreate` to inject the button and invokes the editor's built-in clipboard-export path (mode 2 of `AnnotateActivity.I(int)`), suppressing the trailing `finishAndRemoveTask()` so the editor stays open after a copy.

### SystemUIHook

Hooks `Application.attach` in the `com.android.systemui` process to capture the SystemUI `Context` and register `CopyAckReceiver`.

### AppHook

Hooks `XposedBridge.isModuleActive` → `true` in the app process so the UI shows "LSPosed active" and exposes the copy-mode picker.

### SystemUiInspector (retained, not invoked in production)

Discovery harness for re-mapping SystemUI internals if a build changes them. See `xposed/SYSTEMUI_ANDROID16_FINDINGS.md` for the confirmed class/view map.

## App ↔ Xposed IPC bridge (security-critical)

The Xposed module runs inside `com.android.systemui`; clipboard writes happen in the app process. IPC constants are mirrored in two files: `app/.../AppActions.kt` (app process) and `xposed/.../AppProtocol.kt` (SystemUI process) — keep them in sync. Two checks gate the bridge — keep both:

1. `XposedSecretProvider` (exported `ContentProvider` at `${applicationId}.secrets`) returns the per-install UUID secret **only** to callers whose UID is the app itself or maps to `com.android.systemui` (`isAuthorizedCaller`).
2. `CopyBroadcastReceiver` validates that secret before accepting any screenshot URI.

After a successful copy the app sends `ACTION_COPY_SCREENSHOT_ACK` back; `CopyAckReceiver` (in the SystemUI process) validates the secret and silently trashes the original via `MediaStore.MediaColumns.IS_TRASHED = 1`.

All SystemUI hook code must stay defensive: wrap reflection in `runCatching`, log failures, fail closed, and never let a hook exception crash `com.android.systemui`. Use modern libxposed API 100 only — metadata lives in `xposed/src/main/resources/META-INF/xposed/` (`module.prop`, `java_init.list`, `scope.list`); the API jar is vendored at `xposed/libs/libxposed-api-100.jar` and referenced via `compileOnly(files(...))`. Do **not** use legacy `de.robv.android.xposed` entrypoints or `assets/xposed_init`.

## Settings

All settings are app-private `SharedPreferences`, centralized in `ClipticSettings` (use its `KEY_*` constants, never raw strings). Note the side-effecting setters:

- `setShareSheetEnabled` toggles the `ShareReceiverAlias` `activity-alias` via `setComponentEnabledSetting` — that is how Cliptic adds/removes itself from the Share Sheet. Editing the pref alone is not enough.
- `setAutoCopyEnabled` / `setCopyMode` start or stop `ScreenshotService`. `copy_mode` (`auto`/`xposed`/`both`) gates whether the service runs: `shouldRunScreenshotService` returns false when mode is `xposed`-only.
- `recordCopy` tracks the daily copy count and last-copy timestamp shown in the hero status card.

## Localization

All user-facing `:app` strings live in `app/src/main/res/values/strings.xml` (the default, `en-US`). Code never hardcodes display text — Compose uses `stringResource`/`pluralStringResource`, non-Compose code uses `context.getString`. Counts go through `<plurals>` (e.g. `pending_originals_waiting`); anything with a number uses a positional format arg (`%1$d`, `%1$s`).

Per-app language support is **auto-generated**: `androidResources { generateLocaleConfig = true }` in `app/build.gradle.kts` builds `locale_config.xml` from the present `values-*` folders and injects `android:localeConfig` into the merged manifest, so Cliptic shows up in Settings → System → Languages → App languages. The default locale is declared in `app/src/main/res/resources.properties` (`unqualifiedResLocale=en-US`) — note that AGP reads this file from the **res source set**, not the module root, despite what the docs imply.

An in-app language picker lives in Settings (the "Language" section). `AppLanguages` (`settings/AppLanguages.kt`) wraps the platform `LocaleManager` (API 33+): `set()` applies a BCP-47 tag — or the empty list for system default — which persists the per-app locale and recreates the activity in the new language. The picker list and autonyms (each language shown in its own script, not translated) must stay in sync with the shipped `values-*` folders.

Ten locales ship: `en-US` (default) plus `ar`, `de`, `es`, `fr`, `hi`, `ja`, `pt-rBR`, `ru`, `zh-rCN`. Each carries the full key set with locale-correct `<plurals>` (Arabic has all six categories; es/fr/pt include `many` for lint). To add a language: create `values-<lang>/strings.xml` with the same keys, or use Android Studio's Translations Editor. No build-file change needed; the locale list regenerates. Keep plural categories valid for the locale or `lint` flags `MissingQuantity`. The `:xposed` module's two strings (`app_name`, `xposed_description`) are not yet localized.

## Scope constraints

Intentionally small. Do not add screenshot history, gallery browsing, editing, cloud sync, widgets, or Wear/TV support. The Xposed target is Android 16 Pixel SystemUI only — do not generalize it.
