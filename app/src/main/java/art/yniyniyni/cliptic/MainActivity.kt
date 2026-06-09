package art.yniyniyni.cliptic

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import art.yniyniyni.cliptic.cleanup.OriginalScreenshotCleanup
import art.yniyniyni.cliptic.permission.MediaAccess
import art.yniyniyni.cliptic.permission.MediaAccessLevel
import art.yniyniyni.cliptic.service.ScreenshotService
import art.yniyniyni.cliptic.settings.AppLanguages
import art.yniyniyni.cliptic.settings.ClipticSettings
import art.yniyniyni.cliptic.ui.theme.ClipticTheme
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

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
    var autoCopyEnabled by remember { mutableStateOf(prefs.getBoolean(ClipticSettings.KEY_AUTO_COPY_ENABLED, false)) }
    var shareSheetEnabled by remember { mutableStateOf(prefs.getBoolean(ClipticSettings.KEY_SHARE_SHEET_ENABLED, true)) }
    var removeOriginalAfterCopy by remember { mutableStateOf(prefs.getBoolean(ClipticSettings.KEY_REMOVE_ORIGINAL_AFTER_COPY, true)) }
    var startOnBoot by remember { mutableStateOf(prefs.getBoolean(ClipticSettings.KEY_START_ON_BOOT, true)) }
    var serviceRunning by remember { mutableStateOf(prefs.getBoolean(ClipticSettings.KEY_SERVICE_RUNNING, false)) }
    var mediaAccess by remember { mutableStateOf(MediaAccess.level(context)) }
    var copyMode by remember { mutableStateOf(prefs.getString(ClipticSettings.KEY_COPY_MODE, ClipticSettings.COPY_MODE_AUTO) ?: ClipticSettings.COPY_MODE_AUTO) }
    var pendingOriginalCount by remember { mutableStateOf(OriginalScreenshotCleanup.pendingOriginalCount(context)) }
    var mediaManagementGranted by remember { mutableStateOf(OriginalScreenshotCleanup.canTrashSilently(context)) }
    var copyCountToday by remember { mutableIntStateOf(ClipticSettings.copyCountToday(context)) }
    var lastCopyAt by remember { mutableLongStateOf(ClipticSettings.lastCopyAt(context)) }
    var onboardingVisible by remember { mutableStateOf(!prefs.getBoolean(ClipticSettings.KEY_ONBOARDING_DONE, false)) }

    val permissionRequest = remember { MediaAccess.requestPermissions + Manifest.permission.POST_NOTIFICATIONS }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        val level = MediaAccess.level(context)
        mediaAccess = level
        // Only consider onboarding complete once the user has granted at least some image
        // access. A flat denial leaves it incomplete so the prompt returns next launch.
        if (level != MediaAccessLevel.NONE) {
            prefs.edit().putBoolean(ClipticSettings.KEY_ONBOARDING_DONE, true).apply()
        }
        onboardingVisible = false
    }

    LaunchedEffect(Unit) {
        ClipticSettings.ensureDefaults(context)
        OriginalScreenshotCleanup.attemptPendingTrash(context)
        pendingOriginalCount = OriginalScreenshotCleanup.pendingOriginalCount(context)
        mediaManagementGranted = OriginalScreenshotCleanup.canTrashSilently(context)
        mediaAccess = MediaAccess.level(context)
        copyCountToday = ClipticSettings.copyCountToday(context)
        lastCopyAt = ClipticSettings.lastCopyAt(context)
    }

    DisposableEffect(context) {
        val activity = context as? ComponentActivity
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                OriginalScreenshotCleanup.attemptPendingTrash(context)
                pendingOriginalCount = OriginalScreenshotCleanup.pendingOriginalCount(context)
                mediaManagementGranted = OriginalScreenshotCleanup.canTrashSilently(context)
                mediaAccess = MediaAccess.level(context)
                serviceRunning = prefs.getBoolean(ClipticSettings.KEY_SERVICE_RUNNING, false)
                copyCountToday = ClipticSettings.copyCountToday(context)
                lastCopyAt = ClipticSettings.lastCopyAt(context)
            }
        }
        activity?.lifecycle?.addObserver(observer)
        onDispose { activity?.lifecycle?.removeObserver(observer) }
    }

    val colorScheme = MaterialTheme.colorScheme
    val backdrop = Brush.verticalGradient(
        listOf(colorScheme.primaryContainer.copy(alpha = 0.55f), colorScheme.background)
    )

    // Settings is an overlay stacked over Home; `settingsOpen` (0 = home, 1 = settings) drives
    // its slide so the back gesture can scrub it and reveal Home underneath (predictive back).
    val scope = rememberCoroutineScope()
    var settingsRendered by remember { mutableStateOf(false) }
    val settingsOpen = remember { Animatable(0f) }
    fun openSettings() {
        settingsRendered = true
        scope.launch { settingsOpen.animateTo(1f, tween(durationMillis = 340)) }
    }
    fun closeSettings() {
        scope.launch {
            settingsOpen.animateTo(0f, tween(durationMillis = 280))
            settingsRendered = false
        }
    }

    if (settingsRendered) {
        PredictiveBackHandler(enabled = true) { progress ->
            try {
                progress.collect { event -> settingsOpen.snapTo(1f - event.progress) }
                // Gesture committed — animate the rest of the way out from where the finger
                // lifted (don't snap), then drop the overlay.
                settingsOpen.animateTo(0f, tween(durationMillis = 220))
                settingsRendered = false
            } catch (cancellation: CancellationException) {
                // Gesture cancelled — spring Settings back to fully open.
                settingsOpen.animateTo(1f, tween(durationMillis = 220))
                throw cancellation
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backdrop)
    ) {
        Scaffold(containerColor = Color.Transparent) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                HomeScreen(
                    serviceRunning = serviceRunning,
                    copyCountToday = copyCountToday,
                    lastCopyAt = lastCopyAt,
                    retentionMs = ClipticSettings.cacheDurationMs(context),
                    pendingOriginalCount = pendingOriginalCount,
                    autoCopyEnabled = autoCopyEnabled,
                    shareSheetEnabled = shareSheetEnabled,
                    removeOriginalAfterCopy = removeOriginalAfterCopy,
                    mediaAccess = mediaAccess,
                    onOpenSettings = { openSettings() },
                    onFixMediaAccess = {
                        if (mediaAccess == MediaAccessLevel.PARTIAL) {
                            MediaAccess.openAppSettings(context)
                        } else {
                            permissionLauncher.launch(permissionRequest)
                        }
                    },
                    onStart = {
                        autoCopyEnabled = true
                        ClipticSettings.setAutoCopyEnabled(context, true)
                        serviceRunning = ClipticSettings.shouldRunScreenshotService(context)
                    },
                    onPause = {
                        autoCopyEnabled = false
                        ClipticSettings.setAutoCopyEnabled(context, false)
                        serviceRunning = false
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
                    }
                )

                if (settingsRendered) {
                    SettingsScreen(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                // p: 0 = fully open, 1 = fully dismissed off the right edge.
                                val p = (1f - settingsOpen.value).coerceIn(0f, 1f)
                                translationX = size.width * p
                                scaleX = 1f - 0.04f * p
                                scaleY = 1f - 0.04f * p
                                shape = RoundedCornerShape(28.dp.toPx() * p)
                                clip = true
                            }
                            // Opaque base under the translucent gradient so Home doesn't bleed
                            // through the overlay — it's only revealed by the back-gesture slide.
                            .background(colorScheme.background)
                            .background(backdrop),
                        onBack = { closeSettings() },
                        startOnBoot = startOnBoot,
                        mediaManagementGranted = mediaManagementGranted,
                        pendingOriginalCount = pendingOriginalCount,
                        copyMode = copyMode,
                        onStartOnBootChange = {
                            startOnBoot = it
                            prefs.edit().putBoolean(ClipticSettings.KEY_START_ON_BOOT, it).apply()
                        },
                        onOpenMediaSettings = { OriginalScreenshotCleanup.openMediaManagementSettings(context) },
                        onOpenNotificationSettings = { openServiceNotificationSettings(context) },
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
                }
            }
        }
    }

    if (onboardingVisible) {
        // Dismissing without granting leaves onboarding incomplete, so it returns next launch
        // rather than silently leaving the app unable to read screenshots.
        ModalBottomSheet(onDismissRequest = { onboardingVisible = false }) {
            OnboardingContent(onGrant = { permissionLauncher.launch(permissionRequest) })
        }
    }
}

// ── Screens ───────────────────────────────────────────────────────────────────

@Composable
private fun HomeScreen(
    serviceRunning: Boolean,
    copyCountToday: Int,
    lastCopyAt: Long,
    retentionMs: Long,
    pendingOriginalCount: Int,
    autoCopyEnabled: Boolean,
    shareSheetEnabled: Boolean,
    removeOriginalAfterCopy: Boolean,
    mediaAccess: MediaAccessLevel,
    onOpenSettings: () -> Unit,
    onFixMediaAccess: () -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onRemoveOriginal: () -> Unit,
    onAutoCopyChange: (Boolean) -> Unit,
    onShareSheetChange: (Boolean) -> Unit,
    onRemoveOriginalChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        HomeTopBar(onOpenSettings = onOpenSettings)

        HeroStatusCard(
            serviceRunning = serviceRunning,
            copyCountToday = copyCountToday,
            lastCopyAt = lastCopyAt,
            retentionMs = retentionMs,
            onStart = onStart,
            onPause = onPause
        )

        if (mediaAccess != MediaAccessLevel.FULL) {
            MediaAccessWarning(level = mediaAccess, onFix = onFixMediaAccess)
        }

        if (pendingOriginalCount > 0) {
            PendingOriginalsBanner(count = pendingOriginalCount, onRemove = onRemoveOriginal)
        }

        SectionLabel(stringResource(R.string.home_section_behavior))
        SettingsCard {
            SettingRow(
                icon = Icons.Outlined.ContentCopy,
                title = stringResource(R.string.setting_auto_copy_title),
                summary = stringResource(R.string.setting_auto_copy_summary)
            ) { Switch(checked = autoCopyEnabled, onCheckedChange = onAutoCopyChange) }
            SettingRow(
                icon = Icons.Outlined.IosShare,
                title = stringResource(R.string.setting_share_sheet_title),
                summary = stringResource(R.string.setting_share_sheet_summary),
                divider = true
            ) { Switch(checked = shareSheetEnabled, onCheckedChange = onShareSheetChange) }
            SettingRow(
                icon = Icons.Outlined.CleaningServices,
                title = stringResource(R.string.setting_remove_original_title),
                summary = stringResource(R.string.setting_remove_original_summary),
                divider = true
            ) { Switch(checked = removeOriginalAfterCopy, onCheckedChange = onRemoveOriginalChange) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    onBack: () -> Unit,
    startOnBoot: Boolean,
    mediaManagementGranted: Boolean,
    pendingOriginalCount: Int,
    copyMode: String,
    onStartOnBootChange: (Boolean) -> Unit,
    onOpenMediaSettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onCopyModeChange: (String) -> Unit,
    onOpenSourceCode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    var languageSheetVisible by remember { mutableStateOf(false) }
    val currentLanguageTag = remember { AppLanguages.current(context) }
    val currentLanguageLabel = if (currentLanguageTag == AppLanguages.SYSTEM_DEFAULT) {
        stringResource(R.string.language_system_default)
    } else {
        AppLanguages.autonyms[currentLanguageTag] ?: currentLanguageTag
    }
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconPillButton(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.action_back), onBack)
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineMedium,
                color = colorScheme.onBackground
            )
        }

        SectionLabel(stringResource(R.string.settings_section_general))
        SettingsCard {
            SettingRow(
                icon = Icons.Outlined.RestartAlt,
                title = stringResource(R.string.setting_start_on_boot_title),
                summary = stringResource(R.string.setting_start_on_boot_summary)
            ) { Switch(checked = startOnBoot, onCheckedChange = onStartOnBootChange) }
            SettingRow(
                icon = Icons.Outlined.AdminPanelSettings,
                title = stringResource(R.string.setting_media_management_title),
                summary = when {
                    mediaManagementGranted -> stringResource(R.string.setting_media_management_summary_granted)
                    pendingOriginalCount > 0 -> stringResource(R.string.setting_media_management_summary_pending)
                    else -> stringResource(R.string.setting_media_management_summary_default)
                },
                divider = true
            ) {
                PillButton(
                    label = if (mediaManagementGranted) stringResource(R.string.action_granted) else stringResource(R.string.action_grant),
                    onClick = onOpenMediaSettings,
                    enabled = !mediaManagementGranted
                )
            }
        }

        SectionLabel(stringResource(R.string.settings_section_language))
        SettingsCard {
            SettingRow(
                icon = Icons.Outlined.Language,
                title = stringResource(R.string.language_picker_title),
                summary = stringResource(R.string.setting_app_language_summary)
            ) {
                PillButton(
                    label = currentLanguageLabel,
                    onClick = { languageSheetVisible = true },
                    outlined = true
                )
            }
        }

        SectionLabel(stringResource(R.string.settings_section_notification))
        SettingsCard {
            SettingRow(
                icon = Icons.Outlined.Notifications,
                title = stringResource(R.string.setting_service_notification_title),
                summary = stringResource(R.string.setting_service_notification_summary)
            ) { PillButton(label = stringResource(R.string.action_open), onClick = onOpenNotificationSettings, outlined = true) }
        }

        ModuleStatusSection(copyMode = copyMode, onCopyModeChange = onCopyModeChange)

        SectionLabel(stringResource(R.string.settings_section_about))
        GlassCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    modifier = Modifier.size(38.dp),
                    shape = RoundedCornerShape(11.dp),
                    color = colorScheme.primaryContainer.copy(alpha = 0.5f),
                    shadowElevation = 0.dp,
                    tonalElevation = 0.dp
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            painter = painterResource(R.drawable.ic_tile_cliptic),
                            contentDescription = null,
                            tint = colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleSmall, color = colorScheme.onSurface)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant
                    )
                }
            }
            OutlinedButton(
                onClick = onOpenSourceCode,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = colorScheme.surface.copy(alpha = 0.6f)
                ),
                border = BorderStroke(1.dp, colorScheme.outlineVariant)
            ) {
                Icon(Icons.Outlined.Code, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.action_view_source))
            }
        }
    }

    if (languageSheetVisible) {
        ModalBottomSheet(onDismissRequest = { languageSheetVisible = false }) {
            LanguagePickerContent(
                currentTag = currentLanguageTag,
                onSelect = { tag ->
                    languageSheetVisible = false
                    // Persists the choice and recreates the activity in the new locale.
                    AppLanguages.set(context, tag)
                }
            )
        }
    }
}

@Composable
private fun LanguagePickerContent(currentTag: String, onSelect: (String) -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    Text(
        text = stringResource(R.string.language_picker_title),
        style = MaterialTheme.typography.titleMedium,
        color = colorScheme.onSurface,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
    )
    Column(modifier = Modifier.padding(bottom = 24.dp)) {
        AppLanguages.tags.forEach { tag ->
            val label = if (tag == AppLanguages.SYSTEM_DEFAULT) {
                stringResource(R.string.language_system_default)
            } else {
                AppLanguages.autonyms[tag] ?: tag
            }
            LanguageRow(label = label, selected = tag == currentTag) { onSelect(tag) }
        }
    }
}

@Composable
private fun LanguageRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) colorScheme.primary else colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Icon(
                Icons.Outlined.Check,
                contentDescription = null,
                tint = colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ── Top bars ──────────────────────────────────────────────────────────────────

@Composable
private fun HomeTopBar(onOpenSettings: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Icon(
                painter = painterResource(R.drawable.ic_tile_cliptic),
                contentDescription = null,
                tint = colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = colorScheme.primary
            )
        }
        IconPillButton(Icons.Outlined.Settings, stringResource(R.string.action_settings), onOpenSettings)
    }
}

@Composable
private fun IconPillButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = Modifier.size(42.dp),
        shape = CircleShape,
        // Soft theme-tinted chip (peach fill + terracotta glyph) rather than a high-contrast dark icon.
        color = colorScheme.primaryContainer.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.4f)),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Icon(icon, contentDescription = contentDescription, tint = colorScheme.primary, modifier = Modifier.size(22.dp))
        }
    }
}

// ── Hero status card ──────────────────────────────────────────────────────────

@Composable
private fun HeroStatusCard(
    serviceRunning: Boolean,
    copyCountToday: Int,
    lastCopyAt: Long,
    retentionMs: Long,
    onStart: () -> Unit,
    onPause: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        colorScheme.primaryContainer.copy(alpha = 0.5f),
                        colorScheme.surface.copy(alpha = 0.28f)
                    )
                )
            )
            .border(1.dp, colorScheme.outlineVariant.copy(alpha = 0.45f), RoundedCornerShape(24.dp))
            .padding(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
                            text = stringResource(if (serviceRunning) R.string.hero_service_active else R.string.hero_service_inactive),
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    modifier = Modifier.size(46.dp),
                    shape = RoundedCornerShape(15.dp),
                    color = colorScheme.primaryContainer.copy(alpha = 0.7f),
                    border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    shadowElevation = 0.dp,
                    tonalElevation = 0.dp
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            imageVector = Icons.Outlined.Bolt,
                            contentDescription = null,
                            tint = colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(if (serviceRunning) R.string.hero_title_on else R.string.hero_title_off),
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 34.sp, lineHeight = 40.sp),
                    color = colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(if (serviceRunning) R.string.hero_subtitle_on else R.string.hero_subtitle_off),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant
                )
            }

            // Action area as a single child so the gap above it stays 16dp in both states.
            // The stats expand/collapse animates the hero's height (the "box expansion").
            Column {
                AnimatedVisibility(
                    visible = serviceRunning,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            StatChip(value = copyCountToday.toString(), label = stringResource(R.string.stat_copied_today))
                            StatChip(value = formatRelativeTime(context, lastCopyAt), label = stringResource(R.string.stat_last_copy))
                            StatChip(value = formatRetention(context, retentionMs), label = stringResource(R.string.stat_on_clipboard))
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                }

                Crossfade(targetState = serviceRunning, label = "heroAction") { running ->
                    if (running) {
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
                            Text(stringResource(R.string.action_pause_monitoring))
                        }
                    } else {
                        Button(
                            onClick = onStart,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(50)
                        ) {
                            Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.action_start_monitoring))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.StatChip(value: String, label: String) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight(),
        shape = RoundedCornerShape(14.dp),
        color = colorScheme.surface.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.4f)),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp)) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 18.sp),
                color = colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
            )
        }
    }
}

// ── Pending originals banner ───────────────────────────────────────────────────

@Composable
private fun PendingOriginalsBanner(count: Int, onRemove: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = colorScheme.surface.copy(alpha = 0.30f),
        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.25f)),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            IconTile(Icons.Outlined.DeleteSweep)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = pluralStringResource(R.plurals.pending_originals_waiting, count, count),
                    style = MaterialTheme.typography.titleSmall,
                    color = colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.pending_originals_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant
                )
            }
            PillButton(label = stringResource(R.string.action_remove), onClick = onRemove)
        }
    }
}

// ── Shared components ─────────────────────────────────────────────────────────

@Composable
internal fun GlassCard(
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

/** A glass card sized for stacked [SettingRow]s — rows own their vertical padding + dividers. */
@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.30f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp), content = content)
    }
}

@Composable
internal fun SectionLabel(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 2.dp)
    )
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    summary: String,
    divider: Boolean = false,
    trailing: @Composable () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    if (divider) {
        HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.3f))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        IconTile(icon)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = colorScheme.onSurface)
            Spacer(Modifier.height(3.dp))
            Text(summary, style = MaterialTheme.typography.bodySmall, color = colorScheme.onSurfaceVariant)
        }
        trailing()
    }
}

@Composable
private fun IconTile(icon: ImageVector) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.size(38.dp),
        shape = RoundedCornerShape(11.dp),
        color = colorScheme.primaryContainer.copy(alpha = 0.5f),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Icon(icon, contentDescription = null, tint = colorScheme.primary, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun PillButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    outlined: Boolean = false
) {
    val colorScheme = MaterialTheme.colorScheme
    if (outlined) {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = colorScheme.surface.copy(alpha = 0.6f)
            ),
            border = BorderStroke(1.dp, colorScheme.outlineVariant)
        ) { Text(label) }
    } else {
        Button(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(50)) {
            Text(label)
        }
    }
}

@Composable
private fun MediaAccessWarning(
    level: MediaAccessLevel,
    onFix: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val (message, action) = when (level) {
        MediaAccessLevel.PARTIAL ->
            stringResource(R.string.media_warning_partial_message) to stringResource(R.string.media_warning_partial_action)
        else ->
            stringResource(R.string.media_warning_none_message) to stringResource(R.string.media_warning_none_action)
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = colorScheme.errorContainer.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, colorScheme.error.copy(alpha = 0.35f)),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.PhotoLibrary,
                contentDescription = null,
                tint = colorScheme.onErrorContainer,
                modifier = Modifier.size(22.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(if (level == MediaAccessLevel.PARTIAL) R.string.media_warning_partial_title else R.string.media_warning_none_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = colorScheme.onErrorContainer
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onErrorContainer
                )
            }
            Button(onClick = onFix, shape = RoundedCornerShape(50)) {
                Text(action)
            }
        }
    }
}

@Composable
private fun OnboardingContent(onGrant: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(
            modifier = Modifier.size(64.dp),
            shape = RoundedCornerShape(20.dp),
            color = colorScheme.primaryContainer,
            shadowElevation = 0.dp,
            tonalElevation = 0.dp
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    painter = painterResource(R.drawable.ic_tile_cliptic),
                    contentDescription = null,
                    tint = colorScheme.primary,
                    modifier = Modifier.size(34.dp)
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(R.string.onboarding_title),
                style = MaterialTheme.typography.headlineMedium,
                color = colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.onboarding_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurfaceVariant
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OnboardingPermission(
                icon = Icons.Outlined.PhotoLibrary,
                title = stringResource(R.string.onboarding_perm_photos_title),
                summary = stringResource(R.string.onboarding_perm_photos_summary)
            )
            OnboardingPermission(
                icon = Icons.Outlined.Notifications,
                title = stringResource(R.string.onboarding_perm_notification_title),
                summary = stringResource(R.string.onboarding_perm_notification_summary)
            )
        }
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = onGrant,
            shape = RoundedCornerShape(50)
        ) {
            Text(stringResource(R.string.onboarding_grant))
        }
    }
}

@Composable
private fun OnboardingPermission(icon: ImageVector, title: String, summary: String) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        IconTile(icon)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = colorScheme.onSurface)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = colorScheme.onSurfaceVariant)
        }
    }
}

private fun openServiceNotificationSettings(context: android.content.Context) {
    val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .putExtra(Settings.EXTRA_CHANNEL_ID, ScreenshotService.CHANNEL_ID)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }.onFailure {
        val fallback = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(fallback) }
    }
}

private fun formatRelativeTime(context: android.content.Context, epochMillis: Long): String {
    if (epochMillis <= 0L) return context.getString(R.string.time_none)
    val diff = System.currentTimeMillis() - epochMillis
    return when {
        diff < 60_000L -> context.getString(R.string.time_just_now)
        diff < 3_600_000L -> context.getString(R.string.time_minutes_ago, (diff / 60_000L).toInt())
        diff < 86_400_000L -> context.getString(R.string.time_hours_ago, (diff / 3_600_000L).toInt())
        else -> context.getString(R.string.time_days_ago, (diff / 86_400_000L).toInt())
    }
}

private fun formatRetention(context: android.content.Context, ms: Long): String {
    val minutes = ms / 60_000L
    return if (minutes >= 60L) {
        context.getString(R.string.retention_hours, (minutes / 60L).toInt())
    } else {
        context.getString(R.string.retention_minutes, minutes.toInt())
    }
}
