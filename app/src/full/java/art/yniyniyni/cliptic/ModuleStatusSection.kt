package art.yniyniyni.cliptic

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import art.yniyniyni.cliptic.ipc.XposedBridge
import art.yniyniyni.cliptic.settings.ClipticSettings

@Composable
internal fun ModuleStatusSection(copyMode: String, onCopyModeChange: (String) -> Unit) {
    val xposedActive = remember { XposedBridge.isModuleActive() }
    if (!xposedActive) return
    val colorScheme = MaterialTheme.colorScheme
    SectionLabel(stringResource(R.string.settings_section_lsposed))
    GlassCard {
        Surface(
            shape = RoundedCornerShape(50),
            color = colorScheme.primaryContainer.copy(alpha = 0.55f),
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
                        .background(colorScheme.primary)
                )
                Text(
                    text = stringResource(R.string.lsposed_module_active),
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.primary
                )
            }
        }
        CopyModePicker(copyMode = copyMode, onCopyModeChange = onCopyModeChange)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CopyModePicker(
    copyMode: String,
    onCopyModeChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CopyModeChip(stringResource(R.string.copy_mode_auto), ClipticSettings.COPY_MODE_AUTO, copyMode, onCopyModeChange)
            CopyModeChip(stringResource(R.string.copy_mode_xposed), ClipticSettings.COPY_MODE_XPOSED, copyMode, onCopyModeChange)
            CopyModeChip(stringResource(R.string.copy_mode_both), ClipticSettings.COPY_MODE_BOTH, copyMode, onCopyModeChange)
        }
    }
}

/** A selectable pill chip with a radio dot — tints + cross-fades its colors when chosen. */
@Composable
private fun CopyModeChip(
    label: String,
    value: String,
    selected: String,
    onCopyModeChange: (String) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val isSelected = selected == value
    val background by animateColorAsState(
        if (isSelected) colorScheme.primaryContainer.copy(alpha = 0.7f) else colorScheme.surface.copy(alpha = 0.4f),
        label = "chipBackground"
    )
    val borderColor by animateColorAsState(
        if (isSelected) colorScheme.primary.copy(alpha = 0.55f) else colorScheme.outlineVariant.copy(alpha = 0.45f),
        label = "chipBorder"
    )
    val contentColor by animateColorAsState(
        if (isSelected) colorScheme.primary else colorScheme.onSurfaceVariant,
        label = "chipContent"
    )
    Surface(
        onClick = { onCopyModeChange(value) },
        shape = RoundedCornerShape(50),
        color = background,
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val dotScale by animateFloatAsState(if (isSelected) 1f else 0f, label = "chipDot")
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .border(2.dp, if (isSelected) colorScheme.primary else colorScheme.outline, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .scale(dotScale)
                        .clip(CircleShape)
                        .background(colorScheme.primary)
                )
            }
            Text(label, style = MaterialTheme.typography.titleSmall, color = contentColor)
        }
    }
}
