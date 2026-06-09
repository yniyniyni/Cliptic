# Privacy Policy for Cliptic

**Last updated: June 10, 2026**

## Overview

Cliptic ("the App") is designed with privacy as a foundation. The entire operation happens **exclusively on your device**. No data is collected, transmitted, or shared with any third party — including the developer.

## Data Access and Usage

### Screenshots (Photos and Videos)

The App reads newly captured screenshots from your device's media storage (`MediaStore`) for the sole purpose of copying them to the system clipboard. Specifically:

- **What is accessed**: Image files located in the `Screenshots` folder of your device.
- **How it's accessed**: Through Android's `READ_MEDIA_IMAGES` permission, which you explicitly grant.
- **Why**: To detect new screenshots as they are saved and make them available for pasting — this is the App's core function.
- **Storage duration**: Screenshot files are temporarily cached in the App's private cache directory and are automatically deleted after 1 hour, or immediately when Android reclaims cache storage.

### Images Shared via the Share Sheet

If you explicitly share an image to Cliptic from another app (via Android's Share Sheet), the App copies that image to the clipboard the same way: it is cached in the App's private cache directory and deleted after 1 hour. This applies only to images you actively choose to share — the App never browses or accesses other images on its own.

### Original Screenshot Removal (Optional)

If you enable the "Remove original after copy" setting:

- The App may trash the original screenshot from your gallery using Android's `MediaStore.createTrashRequest()` API.
- On some devices this requires the `MANAGE_MEDIA` permission, which you must explicitly grant through system settings.
- **No file contents are read beyond what is needed for clipboard copying.**

### Clipboard

The App writes a reference (`content://` URI) to the copied screenshot onto your system clipboard. The clipboard content is managed entirely by the Android system; the App does not read clipboard contents or monitor clipboard activity.

## Permissions Explained

| Permission | Purpose |
|---|---|
| `READ_MEDIA_IMAGES` | Detect new screenshots so they can be copied to clipboard |
| `READ_MEDIA_VISUAL_USER_SELECTED` | Fallback for partial photo access (Android 14+) |
| `MANAGE_MEDIA` | Optional — silently remove original screenshots after copy |
| `FOREGROUND_SERVICE` | Keep the screenshot monitor running reliably in the background |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Required by Android 14+ to run the screenshot monitor as a special-use foreground service |
| `POST_NOTIFICATIONS` | Show a persistent notification while the service is active |
| `RECEIVE_BOOT_COMPLETED` | Optionally restart monitoring after device reboot |

## Data Collection and Sharing

**The App does not collect, store, or transmit any personal data.** Specifically:

- No analytics, crash reporting, or tracking frameworks are embedded.
- No data is sent to any server — the App has no network communication.
- No advertising identifiers are accessed.
- Screenshots are never uploaded, backed up to cloud services, or shared externally by the App.
- No account, login, or registration is required.

## Data Retention and Deletion

- **Cached screenshots**: Automatically deleted after 1 hour from the App's private cache directory. Android may also clear this cache at any time.
- **App settings**: Stored locally in Android's `SharedPreferences`. Uninstalling the App removes all settings.
- **No cloud backup**: The App opts out of Android's cloud backup and device-to-device transfer (`android:allowBackup="false"`), so neither settings nor cached files ever leave the device.
- **Pending original queue** (if enabled): A list of screenshot URIs queued for removal, stored locally and cleared when originals are trashed or settings are changed.
- **No personal data**: Since no personal data is collected, there is nothing to retain or delete beyond what's described above.

## Children's Privacy

The App does not address anyone under the age of 13. No personally identifiable information is collected from any user, including children.

## Changes to This Privacy Policy

Updates to this policy will be posted on this page. Continued use of the App after changes constitutes acceptance of the updated policy.

## Contact

For questions about this privacy policy, create an issue on the project repository:
https://github.com/yniyniyni/Cliptic/issues

---

*Cliptic — copy screenshots, automagically. Nothing leaves your device.*
