package art.yniyniyni.cliptic

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import art.yniyniyni.cliptic.cleanup.OriginalScreenshotCleanup
import art.yniyniyni.cliptic.core.util.XposedBridge
import art.yniyniyni.cliptic.settings.ClipticSettings

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ClipticSettings.ensureDefaults(this)
        setContent {
            ClipticTheme {
                ClipticApp()
            }
        }
    }
}

@Composable
private fun ClipticTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colorScheme = if (
        (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
        android.content.res.Configuration.UI_MODE_NIGHT_YES
    ) {
        dynamicDarkColorScheme(context)
    } else {
        dynamicLightColorScheme(context)
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClipticApp() {
    val context = LocalContext.current
    val prefs = remember { ClipticSettings.prefs(context) }
    var autoCopyEnabled by remember { mutableStateOf(prefs.getBoolean(ClipticSettings.KEY_AUTO_COPY_ENABLED, true)) }
    var shareSheetEnabled by remember { mutableStateOf(prefs.getBoolean(ClipticSettings.KEY_SHARE_SHEET_ENABLED, true)) }
    var removeOriginalAfterCopy by remember { mutableStateOf(prefs.getBoolean(ClipticSettings.KEY_REMOVE_ORIGINAL_AFTER_COPY, true)) }
    var startOnBoot by remember { mutableStateOf(prefs.getBoolean(ClipticSettings.KEY_START_ON_BOOT, true)) }
    var serviceRunning by remember { mutableStateOf(prefs.getBoolean(ClipticSettings.KEY_SERVICE_RUNNING, false)) }
    var copyMode by remember { mutableStateOf(prefs.getString(ClipticSettings.KEY_COPY_MODE, ClipticSettings.COPY_MODE_AUTO) ?: ClipticSettings.COPY_MODE_AUTO) }
    var pendingOriginalUri by remember { mutableStateOf(OriginalScreenshotCleanup.pendingOriginalUri(context)) }
    val xposedActive = remember { XposedBridge.isModuleActive() }
    var onboardingVisible by remember { mutableStateOf(!prefs.getBoolean(ClipticSettings.KEY_ONBOARDING_DONE, false)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        prefs.edit().putBoolean(ClipticSettings.KEY_ONBOARDING_DONE, true).apply()
        onboardingVisible = false
    }

    LaunchedEffect(Unit) {
        ClipticSettings.ensureDefaults(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Cliptic")
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StatusCard(
                serviceRunning = serviceRunning,
                hasPendingOriginal = pendingOriginalUri != null,
                xposedActive = xposedActive,
                onStart = {
                    ClipticSettings.startScreenshotService(context)
                    serviceRunning = true
                },
                onRemoveOriginal = {
                    OriginalScreenshotCleanup.launchPendingPrompt(context)
                    pendingOriginalUri = OriginalScreenshotCleanup.pendingOriginalUri(context)
                }
            )

            SettingsSection(title = "Behavior") {
                SettingSwitch(
                    title = "Auto-copy screenshots",
                    checked = autoCopyEnabled,
                    onCheckedChange = {
                        autoCopyEnabled = it
                        ClipticSettings.setAutoCopyEnabled(context, it)
                        serviceRunning = it && ClipticSettings.shouldRunScreenshotService(context)
                    }
                )
                SettingSwitch(
                    title = "Appear in Share Sheet",
                    checked = shareSheetEnabled,
                    onCheckedChange = {
                        shareSheetEnabled = it
                        ClipticSettings.setShareSheetEnabled(context, it)
                    }
                )
                SettingSwitch(
                    title = "Remove original after copy",
                    summary = "Ask Android to remove the gallery copy after Cliptic caches it",
                    checked = removeOriginalAfterCopy,
                    onCheckedChange = {
                        removeOriginalAfterCopy = it
                        prefs.edit().putBoolean(ClipticSettings.KEY_REMOVE_ORIGINAL_AFTER_COPY, it).apply()
                    }
                )
                SettingSwitch(
                    title = "Start on boot",
                    checked = startOnBoot,
                    onCheckedChange = {
                        startOnBoot = it
                        prefs.edit().putBoolean(ClipticSettings.KEY_START_ON_BOOT, it).apply()
                    }
                )
            }

            SettingsSection(title = "Notification") {
                SettingSwitch(
                    title = "Show service notification",
                    summary = "Manage visibility in Android notification settings",
                    checked = prefs.getBoolean(ClipticSettings.KEY_SHOW_SERVICE_NOTIFICATION, true),
                    onCheckedChange = {
                        prefs.edit().putBoolean(ClipticSettings.KEY_SHOW_SERVICE_NOTIFICATION, it).apply()
                    }
                )
            }

            if (xposedActive) {
                SettingsSection(title = "LSPosed") {
                    Text(
                        text = "Module active",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    CopyModePicker(
                        copyMode = copyMode,
                        onCopyModeChange = {
                            copyMode = it
                            ClipticSettings.setCopyMode(context, it)
                            serviceRunning = ClipticSettings.shouldRunScreenshotService(context)
                        }
                    )
                }
            }

            SettingsSection(title = "About") {
                Text("Version ${BuildConfig.VERSION_NAME}")
                TextButton(
                    onClick = {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://github.com/yniyniyni/Cliptic")
                            )
                        )
                    }
                ) {
                    Text("Source code")
                }
            }
        }
    }

    if (onboardingVisible) {
        ModalBottomSheet(onDismissRequest = {
            prefs.edit().putBoolean(ClipticSettings.KEY_ONBOARDING_DONE, true).apply()
            onboardingVisible = false
        }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Copy screenshots automatically",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text("Cliptic watches for new screenshots and places them on the clipboard immediately.")
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.READ_MEDIA_IMAGES,
                                Manifest.permission.POST_NOTIFICATIONS
                            )
                        )
                    }
                ) {
                    Text("Grant & Continue")
                }
            }
        }
    }
}

@Composable
private fun StatusCard(
    serviceRunning: Boolean,
    hasPendingOriginal: Boolean,
    xposedActive: Boolean,
    onStart: () -> Unit,
    onRemoveOriginal: () -> Unit
) {
    val colors = if (serviceRunning) {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    } else {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    }
    Card(colors = colors) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = if (serviceRunning) "Active" else "Inactive",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = if (serviceRunning) {
                    "Screenshots are being copied."
                } else {
                    "Auto-copy is not running."
                }
            )
            if (xposedActive) {
                Text(
                    text = "LSPosed module active",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
            if (!serviceRunning) {
                Button(onClick = onStart) {
                    Text("Start")
                }
            }
            if (hasPendingOriginal) {
                Button(onClick = onRemoveOriginal) {
                    Text("Remove original")
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
        Surface(
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 1.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content
            )
        }
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    summary: String? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            if (summary != null) {
                Spacer(Modifier.height(2.dp))
                Text(summary, style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.width(16.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CopyModePicker(
    copyMode: String,
    onCopyModeChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider()
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            CopyModeOption("Auto only", ClipticSettings.COPY_MODE_AUTO, copyMode, onCopyModeChange)
            CopyModeOption("LSPosed only", ClipticSettings.COPY_MODE_XPOSED, copyMode, onCopyModeChange)
            CopyModeOption("Both", ClipticSettings.COPY_MODE_BOTH, copyMode, onCopyModeChange)
        }
    }
}

@Composable
private fun CopyModeOption(
    label: String,
    value: String,
    selected: String,
    onCopyModeChange: (String) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected == value,
            onClick = { onCopyModeChange(value) }
        )
        Text(label)
    }
}
