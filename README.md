# Cliptic

Cliptic is an Android app that copies screenshots to the clipboard automatically. The name comes from `clip + automatic`: take a screenshot, then paste it somewhere else without opening the gallery first.

The project currently has two delivery paths:

- `:app`: the main standalone APK. It watches MediaStore for new screenshots, supports the Android Share Sheet, exposes a Quick Settings tile, and can optionally request removal of the original gallery screenshot after it has cached a clipboard copy.
- `:xposed`: a separate LSPosed/Vector module APK targeting Pixel SystemUI. It uses the modern libxposed API 100 metadata format and contains the defensive SystemUI integration scaffolding.

The source of truth for product and architecture decisions is [screenshot-clip-spec.md](screenshot-clip-spec.md). User-facing naming should use `Cliptic`, not the older `ScreenshotClip` name from the original spec.

## Status

This repository is an Android Studio project with three Gradle modules:

- `core`: shared clipboard, screenshot cache, screenshot detection, and Xposed active-state stub code.
- `app`: Jetpack Compose UI, foreground screenshot observer service, share receiver, boot receiver, Quick Settings tile, IPC receiver, secret provider, and original-screenshot cleanup flow.
- `xposed`: modern LSPosed module package using API 100 resources and a vendored `libxposed-api-100.jar`. Contains `SystemUiInspector` — a diagnostics-only harness that probes known screenshot FQCN candidates, spiders related types inside `com.android.systemui.screenshot`, and installs defensive after-hooks to dump live view hierarchies and locate screenshot `Uri`s on a real device.

The standalone app path is the primary usable path right now. The Xposed module is intentionally in a diagnostics phase — the final hook that injects a visible Copy action into the Android 16 Pixel screenshot toolbar has not been written yet; `SystemUiInspector` is the tool for discovering the right attachment points on a real device.

## Requirements

- Android Studio with Android Gradle Plugin support.
- JDK 17.
- Android SDK 36.
- Android 14+ device or emulator for the app (`minSdk = 34`).
- For the Xposed module path: a rooted Pixel/Android 16 setup with LSPosed or Vector exposing modern libxposed API 100.

## Build

Build all debug APKs:

```sh
./gradlew assembleDebug
```

Build only the standalone app:

```sh
./gradlew :app:assembleDebug
```

Build only the LSPosed module APK:

```sh
./gradlew :xposed:assembleDebug
```

Run local unit tests:

```sh
./gradlew test
```

## Install

Standalone app:

```sh
./gradlew :app:installDebug
```

After first launch, grant the requested media and notification permissions. Auto-copy can then be controlled from the app UI or the Quick Settings tile.

Xposed module:

1. Build `:xposed:assembleDebug`.
2. Install the generated Xposed APK on the rooted device.
3. Enable the module in LSPosed/Vector.
4. Scope it to `com.android.systemui` and `art.yniyniyni.cliptic`.
5. Restart SystemUI or reboot the device.

## How It Works

In standalone mode, `ScreenshotService` runs as a foreground service and owns a `ScreenshotDetector`. The detector observes `MediaStore.Images.Media.EXTERNAL_CONTENT_URI`, filters for recent images in a screenshot path, waits briefly for the file to finish writing, and passes the `content://` URI to the app.

The app then copies the image into `cacheDir/cliptic_clipboard`, exposes it through `FileProvider`, and puts that URI on the clipboard via `ClipboardWriter`. Cached files are cleaned up after about one hour.

The Share Sheet path uses `ShareReceiverActivity`. When Cliptic receives an `image/*` share intent, it caches the shared URI, copies the cached URI to the clipboard, shows a short confirmation toast, and finishes without showing UI.

The optional original cleanup path uses `MediaStore.createTrashRequest`. If Android allows silent media management, Cliptic attempts cleanup automatically; otherwise it keeps a pending queue and shows a notification or prompt so the user can approve removal.

## Settings

Current app settings are stored in app-private `SharedPreferences`:

| Key | Default | Meaning |
| --- | --- | --- |
| `auto_copy_enabled` | `true` | Enables or disables automatic screenshot watching. |
| `share_sheet_enabled` | `true` | Enables or disables the Share Sheet activity alias. |
| `remove_original_after_copy` | `true` | Requests removal of the original gallery screenshot after copying. |
| `start_on_boot` | `true` | Starts the foreground service after device boot. |
| `copy_mode` | `auto` | Selects auto, LSPosed, or both when the module is active. |
| `xposed_secret` | random UUID | Shared secret used for app/Xposed IPC validation. |
| `cache_duration_ms` | `3600000` | Cache lifetime for copied screenshots. |

## IPC And Security

The Xposed module runs inside `com.android.systemui`, while clipboard writes happen in the app process. IPC constants are defined in two mirrors: `AppActions.kt` (app side) and `AppProtocol.kt` (xposed side). Cliptic uses two checks for that bridge:

- `XposedSecretProvider` is exported but only returns the IPC secret to the app itself or to callers whose UID maps to `com.android.systemui`.
- `CopyBroadcastReceiver` validates the shared secret before accepting any screenshot URI from SystemUI.

After a successful copy the app sends `ACTION_COPY_SCREENSHOT_ACK` back to SystemUI. `CopyAckReceiver` (running in the SystemUI process) validates the secret and silently trashes the original via `MediaStore.MediaColumns.IS_TRASHED`.

The target app package is hardcoded in the Xposed module as:

```kotlin
art.yniyniyni.cliptic
```

## Project Layout

```text
.
├── app/
│   └── src/main/java/art/yniyniyni/cliptic/
│       ├── AppActions.kt              # IPC action/extra constants (app side)
│       ├── MainActivity.kt
│       ├── cleanup/
│       │   ├── OriginalScreenshotCleanup.kt
│       │   ├── PendingOriginalTrashWorker.kt
│       │   └── RemoveOriginalActivity.kt
│       ├── ipc/XposedSecretProvider.kt
│       ├── receiver/BootReceiver.kt
│       ├── receiver/CopyBroadcastReceiver.kt
│       ├── service/ScreenshotService.kt
│       ├── settings/ClipticSettings.kt
│       ├── share/ShareReceiverActivity.kt
│       └── tile/ScreenshotTileService.kt
├── core/
│   └── src/main/java/art/yniyniyni/cliptic/core/
│       ├── clipboard/ClipboardWriter.kt
│       ├── screenshot/ScreenshotDetector.kt
│       ├── screenshot/ScreenshotFileManager.kt
│       └── util/XposedBridge.kt
├── xposed/
│   ├── libs/libxposed-api-100.jar
│   └── src/main/java/art/yniyniyni/cliptic/xposed/
│       ├── AppProtocol.kt             # IPC action/extra constants (xposed side)
│       ├── XposedEntry.kt
│       ├── hooks/SystemUIHook.kt      # Application.attach hook; registers CopyAckReceiver
│       ├── hooks/SystemUiInspector.kt # diagnostics harness; discovers screenshot classes/views
│       └── ipc/CopyAckReceiver.kt     # validates ACK secret, silently trashes original
├── screenshot-clip-spec.md
├── AGENTS.md
├── settings.gradle.kts
└── build.gradle.kts
```

## Locked Decisions

- Main application id: `art.yniyniyni.cliptic`.
- Xposed application id: `art.yniyniyni.cliptic.xposed`.
- Minimum SDK: 34.
- Compile SDK: 36.
- Target SDK: 36.
- Language/build: Kotlin with Gradle Kotlin DSL.
- UI: Jetpack Compose and Material 3.
- Xposed API: modern libxposed API 100, with metadata under `META-INF/xposed/`.
- Initial Xposed target: Android 16 Pixel SystemUI only.

## Out Of Scope

Cliptic is intentionally small. The current product direction excludes screenshot history, gallery browsing, editing, cloud sync, widgets, Wear OS, and TV support.
