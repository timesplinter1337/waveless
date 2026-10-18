package dev.waveless.ui.settings

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import dev.waveless.BuildConfig
import dev.waveless.R
import dev.waveless.data.AppSettings
import dev.waveless.data.ThemeMode
import dev.waveless.ui.servers.TextValueDialog
import dev.waveless.ui.theme.Spacing

private enum class EditField { DNS, TEST_URL }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    xrayVersion: String,
    onUpdate: (reconnect: Boolean, transform: (AppSettings) -> AppSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var editing by rememberSaveable { mutableStateOf<EditField?>(null) }

    Scaffold(
        modifier = modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(title = { Text(stringResource(R.string.nav_settings)) }, scrollBehavior = scroll)
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.sm)
                .padding(bottom = Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = Spacing.maxContentWidth)) {
                Section(stringResource(R.string.settings_appearance)) {
                    ThemeSelector(settings.themeMode) { mode -> onUpdate(false) { it.copy(themeMode = mode) } }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        SwitchRow(
                            Icons.Rounded.Palette,
                            stringResource(R.string.settings_dynamic_color),
                            stringResource(R.string.settings_dynamic_color_summary),
                            settings.dynamicColor, 0, 1,
                        ) { v -> onUpdate(false) { it.copy(dynamicColor = v) } }
                    }
                }

                Section(stringResource(R.string.settings_connection)) {
                    SettingRow(Icons.Rounded.Dns, stringResource(R.string.settings_dns), settings.remoteDns, 0, 4) {
                        editing = EditField.DNS
                    }
                    SwitchRow(
                        Icons.Rounded.Language,
                        stringResource(R.string.settings_ipv6),
                        stringResource(R.string.settings_ipv6_summary),
                        settings.ipv6, 1, 4,
                    ) { v -> onUpdate(true) { it.copy(ipv6 = v) } }
                    SwitchRow(
                        Icons.Rounded.Block,
                        stringResource(R.string.settings_block_ads),
                        stringResource(R.string.settings_block_ads_summary),
                        settings.blockAds, 2, 4,
                    ) { v -> onUpdate(true) { it.copy(blockAds = v) } }
                    SettingRow(Icons.Rounded.Timer, stringResource(R.string.settings_test_url), settings.testUrl, 3, 4) {
                        editing = EditField.TEST_URL
                    }
                }

                Section(stringResource(R.string.settings_about)) {
                    SettingRow(
                        Icons.Rounded.Info,
                        "${stringResource(R.string.app_name)} ${BuildConfig.VERSION_NAME}",
                        xrayVersion,
                        0, 1,
                    ) {}
                }
            }
        }
    }

    when (editing) {
        EditField.DNS -> TextValueDialog(
            title = stringResource(R.string.settings_dns),
            initial = settings.remoteDns,
            onDismiss = { editing = null },
            onConfirm = { v -> editing = null; onUpdate(true) { it.copy(remoteDns = v) } },
        )

        EditField.TEST_URL -> TextValueDialog(
            title = stringResource(R.string.settings_test_url),
            initial = settings.testUrl,
            keyboardType = KeyboardType.Uri,
            onDismiss = { editing = null },
            onConfirm = { v -> editing = null; onUpdate(false) { it.copy(testUrl = v) } },
        )

        null -> Unit
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = Spacing.xs, top = Spacing.md, bottom = Spacing.xs),
    )
    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) { content() }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    summary: String?,
    index: Int,
    count: Int,
    onClick: () -> Unit,
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index, count),
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        leadingContent = { Icon(icon, contentDescription = null) },
        supportingContent = summary?.let { { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) } },
    ) {
        Text(title)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SwitchRow(
    icon: ImageVector,
    title: String,
    summary: String,
    checked: Boolean,
    index: Int,
    count: Int,
    onChange: (Boolean) -> Unit,
) {
    SegmentedListItem(
        onClick = { onChange(!checked) },
        shapes = ListItemDefaults.segmentedShapes(index, count),
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        leadingContent = { Icon(icon, contentDescription = null) },
        supportingContent = { Text(summary) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
    ) {
        Text(title)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ThemeSelector(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val options = listOf(
        ThemeMode.SYSTEM to R.string.theme_system,
        ThemeMode.LIGHT to R.string.theme_light,
        ThemeMode.DARK to R.string.theme_dark,
    )
    Row(
        Modifier.fillMaxWidth().padding(bottom = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, (mode, label) ->
            ToggleButton(
                checked = selected == mode,
                onCheckedChange = { onSelect(mode) },
                modifier = Modifier.weight(1f),
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
            ) {
                Text(stringResource(label))
            }
        }
    }
}
