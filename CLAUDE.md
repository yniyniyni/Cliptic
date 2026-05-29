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
./gradlew :app:testDebugUnitTest --tests "art.yniyniyni.cliptic.ExampleUnitTest"   # single test class
```

Use `rg` for searching the tree.

## Module Architecture

Three Gradle modules, all under package root `art.yniyniyni.cliptic`:

- **`:core`** — pure Android library, no Compose. Owns the reusable primitives: `ClipboardWriter` (writes a `content://` URI onto the clipboard), `ScreenshotDetector` (MediaStore `ContentObserver`), `ScreenshotFileManager` (cache + expiry), `util/XposedBridge` (active-module stub).
- **`:app`** — the primary, fully-usable standalone APK (`art.yniyniyni.cliptic`). Compose/Material 3 UI, foreground service, receivers, QS tile, IPC provider, cleanup flow. Depends on `:core`.
- **`:xposed`** — separate LSPosed module APK (`art.yniyniyni.cliptic.xposed`). Depends on `:core`. **Diagnostics phase** — `SystemUiInspector` probes a seed list of `com.android.systemui.screenshot.*` FQCNs, spiders related types, and installs defensive after-hooks to dump live view hierarchies and locate screenshot `Uri`s on-device. `SystemUIHook` hooks `Application.attach` to capture the SystemUI `Context` and registers `CopyAckReceiver`. The final hook that injects a visible Copy action into the screenshot toolbar has not been written yet.

## Standalone copy flow (the working path)

`ScreenshotService` (`:app`, foreground `specialUse` service) creates a `ScreenshotDetector` (`:core`) → detector observes `MediaStore.Images.Media.EXTERNAL_CONTENT_URI`, filters recent images whose `RELATIVE_PATH` contains `Screenshots`, dedupes, and waits ~500ms for the file to finish writing → `ScreenshotService.copyScreenshotWithRetry` caches the image via `ScreenshotFileManager` into `cacheDir`, exposed through `FileProvider`, and puts that cached URI on the clipboard via `ClipboardWriter`. Cached files expire after `cache_duration_ms` (default 1h). The Share Sheet path runs the same cache→copy through `ShareReceiverActivity`.

## Original-screenshot cleanup

`OriginalScreenshotCleanup` (invoked when `remove_original_after_copy` is on) uses `MediaStore.createTrashRequest`. If `MediaStore.canManageMedia` is true it trashes silently with a fast retry ladder (`scheduleFastTrashRetries`) plus a WorkManager job (`PendingOriginalTrashWorker`) for durability; otherwise it queues the URI and posts a confirmation notification (`RemoveOriginalActivity` prompt). Pending originals are persisted as a newline-joined queue in `KEY_PENDING_ORIGINAL_QUEUE` (with migration from the legacy single-URI key).

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

## Scope constraints

Intentionally small. Do not add screenshot history, gallery browsing, editing, cloud sync, widgets, or Wear/TV support. The first Xposed target is Android 16 Pixel SystemUI only — do not generalize it.
