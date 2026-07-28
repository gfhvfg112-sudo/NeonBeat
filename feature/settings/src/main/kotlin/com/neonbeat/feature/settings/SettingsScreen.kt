package com.neonbeat.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neonbeat.core.datastore.ReplayGainMode
import com.neonbeat.core.datastore.ThemeMode
import com.neonbeat.core.datastore.UserSettings

/**
 * Settings screen.
 *
 * Rows are declarative and driven straight off [UserSettings]; every change is
 * written to DataStore immediately, so there is no save button and no partially
 * applied state if the process dies. Sections mirror the settings groups in the
 * datastore so new options only need a row here.
 */
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        item { SectionHeader("Appearance") }
        item {
            ChoiceRow(
                title = "Theme",
                subtitle = settings.themeMode.label(),
                onClick = { viewModel.cycleThemeMode() },
            )
        }
        item {
            SwitchRow(
                title = "Material You",
                subtitle = "Use colors from your wallpaper",
                checked = settings.useDynamicColor,
                onCheckedChange = { viewModel.setDynamicColor(it) },
            )
        }
        item {
            SwitchRow(
                title = "Glass effects",
                subtitle = "Translucent surfaces and blurred backdrops",
                checked = settings.useGlassmorphism,
                onCheckedChange = { viewModel.setGlassEffects(it) },
            )
        }
        item {
            SliderRow(
                title = "Corner roundness",
                value = settings.cornerRadiusDp.toFloat(),
                valueRange = 0f..36f,
                onValueChange = { viewModel.setCornerRadius(it.toInt()) },
                valueLabel = "${settings.cornerRadiusDp} dp",
            )
        }
        item {
            SliderRow(
                title = "Background blur",
                value = settings.blurRadiusDp.toFloat(),
                valueRange = 0f..64f,
                onValueChange = { viewModel.setBlurRadius(it.toInt()) },
                valueLabel = "${settings.blurRadiusDp} dp",
            )
        }
        item {
            SliderRow(
                title = "Grid columns",
                value = settings.gridColumns.toFloat(),
                valueRange = 1f..6f,
                onValueChange = { viewModel.setGridColumns(it.toInt()) },
                valueLabel = settings.gridColumns.toString(),
            )
        }

        item { SectionHeader("Playback") }
        item {
            SwitchRow(
                title = "Gapless playback",
                subtitle = "No silence between consecutive tracks",
                checked = settings.gaplessPlayback,
                onCheckedChange = { viewModel.setGapless(it) },
            )
        }
        item {
            SliderRow(
                title = "Crossfade",
                value = settings.crossfadeSeconds.toFloat(),
                valueRange = 0f..12f,
                onValueChange = { viewModel.setCrossfade(it.toInt()) },
                valueLabel = if (settings.crossfadeSeconds == 0) "Off" else "${settings.crossfadeSeconds}s",
            )
        }
        item {
            SwitchRow(
                title = "ReplayGain",
                subtitle = "Normalize volume across tracks",
                checked = settings.replayGainMode != ReplayGainMode.OFF,
                onCheckedChange = { viewModel.setReplayGain(it) },
            )
        }
        item {
            SwitchRow(
                title = "Mono audio",
                subtitle = "Mix both channels together",
                checked = settings.monoOutput,
                onCheckedChange = { viewModel.setMonoAudio(it) },
            )
        }
        item {
            SwitchRow(
                title = "Skip silence",
                subtitle = "Trim silent passages during playback",
                checked = settings.skipSilence,
                onCheckedChange = { viewModel.setSkipSilence(it) },
            )
        }
        item {
            SwitchRow(
                title = "Bit-perfect output",
                subtitle = "Bypass processing for external DACs",
                checked = settings.bitPerfect,
                onCheckedChange = { viewModel.setBitPerfect(it) },
            )
        }

        item { SectionHeader("Effects") }
        item {
            SwitchRow(
                title = "Equalizer",
                subtitle = "Ten-band equalizer with presets",
                checked = settings.equalizerEnabled,
                onCheckedChange = { viewModel.setEqualizerEnabled(it) },
            )
        }
        item {
            SliderRow(
                title = "Bass boost",
                value = settings.bassBoostStrength.toFloat(),
                valueRange = 0f..1000f,
                onValueChange = { viewModel.setBassBoost(it.toInt()) },
                valueLabel = "${settings.bassBoostStrength / 10}%",
            )
        }
        item {
            SliderRow(
                title = "Virtualizer",
                value = settings.virtualizerStrength.toFloat(),
                valueRange = 0f..1000f,
                onValueChange = { viewModel.setVirtualizer(it.toInt()) },
                valueLabel = "${settings.virtualizerStrength / 10}%",
            )
        }
        item {
            SliderRow(
                title = "Loudness gain",
                value = settings.loudnessGainMb.toFloat(),
                valueRange = 0f..2000f,
                onValueChange = { viewModel.setLoudnessGain(it.toInt()) },
                valueLabel = "${settings.loudnessGainMb / 100} dB",
            )
        }

        item { SectionHeader("Library") }
        item {
            ChoiceRow(
                title = "Rescan library",
                subtitle = "Look for new and removed files",
                onClick = { viewModel.rescanLibrary() },
            )
        }

        item { SectionHeader("Backup") }
        item {
            ChoiceRow(
                title = "Export backup",
                subtitle = "Settings, playlists, favorites and play counts",
                onClick = { viewModel.exportBackup() },
            )
        }
        item {
            ChoiceRow(
                title = "Import backup",
                subtitle = "Restore from a previously exported file",
                onClick = { viewModel.importBackup() },
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Switch) { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // The row itself handles the toggle, so the switch stays decorative for a11y.
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun ChoiceRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SliderRow(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    valueLabel: String,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(
                valueLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = valueRange)
    }
}

private fun ThemeMode.label(): String = when (this) {
    ThemeMode.SYSTEM -> "Follow system"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
    ThemeMode.AMOLED -> "AMOLED black"
}
