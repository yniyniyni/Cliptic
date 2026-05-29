package art.yniyniyni.cliptic

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import art.yniyniyni.cliptic.cleanup.OriginalScreenshotCleanup
import art.yniyniyni.cliptic.core.util.XposedBridge
import art.yniyniyni.cliptic.settings.ClipticSettings
import art.yniyniyni.cliptic.ui.theme.ClipticTheme

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClipticApp() {
    val context = LocalContext.current
    val prefs = remember { ClipticSettings.prefs(context) }
    var autoCopyEnabled by remember { mutableStateOf(prefs.getBoolean(ClipticSettings.KEY_AUTO_COPY_ENABLED, true)) }
    var shareSheetEnabled by remember { mutableStateOf(prefs.getBoolean(ClipticSettings.KEY_SHARE_SHEET_ENABLED, true)) }
    var removeOriginalAfterCopy by remember { mutableStateOf(prefs.getBoolean(ClipticSettings.KEY_REMOVE_ORIGINAL_AFTER_COPY, true)) }
    var startOnBoot by remember { mutableStateOf(prefs.getBoolean(ClipticSettings.KEY_START_ON_BOOT, true)) }
    var showServiceNotification by remember { mutableStateOf(prefs.getBoolean(ClipticSettings.KEY_SHOW_SERVICE_NOTIFICATION, true)) }
    var serviceRunning by remember { mutableStateOf(prefs.getBoolean(ClipticSettings.KEY_SERVICE_RUNNING, false)) }
    var copyMode by remember { mutableStateOf(prefs.getString(ClipticSettings.KEY_COPY_MODE, ClipticSettings.COPY_MODE_AUTO) ?: ClipticSettings.COPY_MODE_AUTO) }
    var pendingOriginalCount by remember { mutableStateOf(OriginalScreenshotCleanup.pendingOriginalCount(context)) }
    var mediaManagementGranted by remember { mutableStateOf(OriginalScreenshotCleanup.canTrashSilently(context)) }
    val xposedActive = remember { XposedBridge.isModuleActive() }
    var onboardingVisible by remember { mutableStateOf(!prefs.getBoolean(ClipticSettings.KEY_ONBOARDING_DONE, false)) }
    var selectedTab by remember { mutableIntStateOf(0) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        prefs.edit().putBoolean(ClipticSettings.KEY_ONBOARDING_DONE, true).apply()
        onboardingVisible = false
    }

    LaunchedEffect(Unit) {
        ClipticSettings.ensureDefaults(context)
        OriginalScreenshotCleanup.attemptPendingTrash(context)
        pendingOriginalCount = OriginalScreenshotCleanup.pendingOriginalCount(context)
        mediaManagementGranted = OriginalScreenshotCleanup.canTrashSilently(context)
    }

    DisposableEffect(context) {
        val activity = context as? ComponentActivity
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                OriginalScreenshotCleanup.attemptPendingTrash(context)
                pendingOriginalCount = OriginalScreenshotCleanup.pendingOriginalCount(context)
                mediaManagementGranted = OriginalScreenshotCleanup.canTrashSilently(context)
            }
        }
        activity?.lifecycle?.addObserver(observer)
        onDispose { activity?.lifecycle?.removeObserver(observer) }
    }

    val colorScheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        colorScheme.primaryContainer.copy(alpha = 0.55f),
                        colorScheme.background
                    )
                )
            )
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                ClipticBottomNav(selectedTab = selectedTab, onTabSelected = { selectedTab = it })
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(top = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Cliptic",
                    style = MaterialTheme.typography.headlineMedium,
                    color = colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                when (selectedTab) {
                    0 -> DashboardContent(
                        serviceRunning = serviceRunning,
                        pendingOriginalCount = pendingOriginalCount,
                        xposedActive = xposedActive,
                        autoCopyEnabled = autoCopyEnabled,
                        shareSheetEnabled = shareSheetEnabled,
                        removeOriginalAfterCopy = removeOriginalAfterCopy,
                        mediaManagementGranted = mediaManagementGranted,
                        onPause = {
                            autoCopyEnabled = false
                            ClipticSettings.setAutoCopyEnabled(context, false)
                            serviceRunning = false
                        },
                        onStart = {
                            autoCopyEnabled = true
                            ClipticSettings.startScreenshotService(context)
                            serviceRunning = true
                        },
                        onRemoveOriginal = {
                            OriginalScreenshotCleanup.launchPendingPrompt(context)
                            pendingOriginalCount = OriginalScreenshotCleanup.pendingOriginalCount(context)
                            mediaManagementGranted = OriginalScreenshotCleanup.canTrashSilently(context)
                        },
                        onAutoCopyChange = {
                            autoCopyEnabled = it
                            ClipticSettings.setAutoCopyEnabled(context, it)
                            serviceRunning = it && ClipticSettings.shouldRunScreenshotService(context)
                        },
                        onShareSheetChange = {
                            shareSheetEnabled = it
                            ClipticSettings.setShareSheetEnabled(context, it)
                        },
                        onRemoveOriginalChange = {
                            removeOriginalAfterCopy = it
                            prefs.edit().putBoolean(ClipticSettings.KEY_REMOVE_ORIGINAL_AFTER_COPY, it).apply()
                        },
                        onOpenMediaSettings = {
                            OriginalScreenshotCleanup.openMediaManagementSettings(context)
                        }
                    )
                    1 -> SettingsTabContent(
                        startOnBoot = startOnBoot,
                        showServiceNotification = showServiceNotification,
                        xposedActive = xposedActive,
                        copyMode = copyMode,
                        onStartOnBootChange = {
                            startOnBoot = it
                            prefs.edit().putBoolean(ClipticSettings.KEY_START_ON_BOOT, it).apply()
                        },
                        onShowNotificationChange = {
                            showServiceNotification = it
                            prefs.edit().putBoolean(ClipticSettings.KEY_SHOW_SERVICE_NOTIFICATION, it).apply()
                        },
                        onCopyModeChange = {
                            copyMode = it
                            ClipticSettings.setCopyMode(context, it)
                            serviceRunning = ClipticSettings.shouldRunScreenshotService(context)
                        },
                        onOpenSourceCode = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/yniyniyni/Cliptic"))
                            )
                        }
                    )
                    2 -> HistoryTabContent()
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
                    style = MaterialTheme.typography.headlineMedium
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

// ── Tab content ───────────────────────────────────────────────────────────────

@Composable
private fun DashboardContent(
    serviceRunning: Boolean,
    pendingOriginalCount: Int,
    xposedActive: Boolean,
    autoCopyEnabled: Boolean,
    shareSheetEnabled: Boolean,
    removeOriginalAfterCopy: Boolean,
    mediaManagementGranted: Boolean,
    onPause: () -> Unit,
    onStart: () -> Unit,
    onRemoveOriginal: () -> Unit,
    onAutoCopyChange: (Boolean) -> Unit,
    onShareSheetChange: (Boolean) -> Unit,
    onRemoveOriginalChange: (Boolean) -> Unit,
    onOpenMediaSettings: () -> Unit
) {
    HeroStatusCard(
        serviceRunning = serviceRunning,
        pendingOriginalCount = pendingOriginalCount,
        xposedActive = xposedActive,
        onPause = onPause,
        onStart = onStart,
        onRemoveOriginal = onRemoveOriginal
    )
    SettingsSection(title = "Behavior") {
        SettingSwitch(
            title = "Auto-copy screenshots",
            summary = "Instantly add new screenshots to your Cliptic board.",
            checked = autoCopyEnabled,
            onCheckedChange = onAutoCopyChange
        )
        SettingSwitch(
            title = "Appear in Share Sheet",
            summary = "Quickly send links and images from other apps.",
            checked = shareSheetEnabled,
            onCheckedChange = onShareSheetChange
        )
        SettingSwitch(
            title = "Remove original after copy",
            summary = "Keep your camera roll clean by deleting the source file.",
            checked = removeOriginalAfterCopy,
            onCheckedChange = onRemoveOriginalChange
        )
        MediaManagementStatus(
            granted = mediaManagementGranted,
            pendingOriginalCount = pendingOriginalCount,
            onOpenSettings = onOpenMediaSettings
        )
    }
}

@Composable
private fun SettingsTabContent(
    startOnBoot: Boolean,
    showServiceNotification: Boolean,
    xposedActive: Boolean,
    copyMode: String,
    onStartOnBootChange: (Boolean) -> Unit,
    onShowNotificationChange: (Boolean) -> Unit,
    onCopyModeChange: (String) -> Unit,
    onOpenSourceCode: () -> Unit
) {
    SettingsSection(title = "General") {
        SettingSwitch(
            title = "Start on boot",
            summary = "Launch Cliptic automatically when the device starts.",
            checked = startOnBoot,
            onCheckedChange = onStartOnBootChange
        )
    }
    SettingsSection(title = "Notification") {
        SettingSwitch(
            title = "Show service notification",
            summary = "Manage visibility in Android notification settings.",
            checked = showServiceNotification,
            onCheckedChange = onShowNotificationChange
        )
    }
    if (xposedActive) {
        SettingsSection(title = "LSPosed") {
            Text(
                text = "Module active",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleSmall
            )
            CopyModePicker(copyMode = copyMode, onCopyModeChange = onCopyModeChange)
        }
    }
    SettingsSection(title = "About") {
        Text(
            text = "Version ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(onClick = onOpenSourceCode) {
            Text("Source code")
        }
    }
}

@Composable
private fun HistoryTabContent() {
    GlassCard {
        Text(
            text = "History",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Copy history will appear here in a future update.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ── Hero status card ──────────────────────────────────────────────────────────

@Composable
private fun HeroStatusCard(
    serviceRunning: Boolean,
    pendingOriginalCount: Int,
    xposedActive: Boolean,
    onPause: () -> Unit,
    onStart: () -> Unit,
    onRemoveOriginal: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = colorScheme.surfaceVariant.copy(alpha = 0.85f),
                border = BorderStroke(1.dp, colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (serviceRunning) colorScheme.primary else colorScheme.outline)
                    )
                    Text(
                        text = if (serviceRunning) "SERVICE ACTIVE" else "SERVICE INACTIVE",
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.onSurfaceVariant
                    )
                }
            }

            Surface(
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(14.dp),
                color = colorScheme.surface.copy(alpha = 0.30f),
                border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.25f)),
                shadowElevation = 0.dp,
                tonalElevation = 0.dp
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = Icons.Outlined.Bolt,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        Text(
            text = if (serviceRunning) "Automagic Sync" else "Automagic Off",
            style = MaterialTheme.typography.displayLarge,
            color = colorScheme.onBackground,
            maxLines = 1,
            softWrap = false
        )

        if (serviceRunning) {
            OutlinedButton(
                onClick = onPause,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = colorScheme.surface.copy(alpha = 0.6f)
                ),
                border = BorderStroke(1.dp, colorScheme.outlineVariant)
            ) {
                Icon(Icons.Outlined.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Pause Monitoring")
            }
        } else {
            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(50)
            ) {
                Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Start Monitoring")
            }
        }

        if (xposedActive) {
            Text(
                text = "LSPosed active",
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.primary
            )
        }

        if (pendingOriginalCount > 0) {
            FilledTonalButton(
                onClick = onRemoveOriginal,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(50)
            ) {
                Text(
                    if (pendingOriginalCount == 1) "Remove original"
                    else "Remove originals ($pendingOriginalCount)"
                )
            }
        }
    }
}

// ── Shared components ─────────────────────────────────────────────────────────

@Composable
private fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.30f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
        GlassCard(content = content)
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
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            if (summary != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun MediaManagementStatus(
    granted: Boolean,
    pendingOriginalCount: Int,
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Media management access", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(2.dp))
            Text(
                text = when {
                    granted -> "Original screenshots can be removed without a prompt."
                    pendingOriginalCount > 0 -> "Grant access to remove pending originals automatically."
                    else -> "Without this, Android will ask before removing originals."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(16.dp))
        Button(
            enabled = !granted,
            onClick = onOpenSettings,
            shape = RoundedCornerShape(50)
        ) {
            Text(if (granted) "Granted" else "Grant")
        }
    }
}

@Composable
private fun ClipticBottomNav(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    data class NavItem(val label: String, val icon: ImageVector)
    val items = listOf(
        NavItem("Dashboard", Icons.Outlined.GridView),
        NavItem("Settings", Icons.Outlined.Settings),
        NavItem("History", Icons.Outlined.History),
    )
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)) {
        items.forEachIndexed { index, item ->
            NavigationBarItem(
                selected = selectedTab == index,
                onClick = { onTabSelected(index) },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) }
            )
        }
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
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected == value, onClick = { onCopyModeChange(value) })
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
