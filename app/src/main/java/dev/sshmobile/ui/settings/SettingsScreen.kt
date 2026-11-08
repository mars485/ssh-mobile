package dev.sshmobile.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.sshmobile.BuildConfig
import dev.sshmobile.R
import dev.sshmobile.data.repository.AppLanguage
import dev.sshmobile.data.repository.TerminalColorScheme
import dev.sshmobile.data.repository.TerminalFont
import dev.sshmobile.data.repository.ThemeMode
import dev.sshmobile.ui.terminal.terminalPalette
import dev.sshmobile.ui.LocalAppContainer
import dev.sshmobile.ui.SimpleViewModelFactory
import dev.sshmobile.ui.components.ConfirmDialog
import dev.sshmobile.ui.components.SectionLabel
import dev.sshmobile.ui.theme.GreenAccent
import dev.sshmobile.ui.theme.GreenSoft
import dev.sshmobile.ui.theme.TerminalFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenKnownHosts: () -> Unit,
    onOpenSftp: () -> Unit,
) {
    val container = LocalAppContainer.current
    val viewModel: SettingsViewModel = viewModel(
        factory = SimpleViewModelFactory {
            SettingsViewModel(
                settingsRepository = container.settingsRepository,
                knownHostRepository = container.knownHostRepository,
                serverRepository = container.serverRepository,
                terminalCommandRepository = container.terminalCommandRepository,
                configBackupRepository = container.configBackupRepository,
                wipeKeystoreKey = container::wipeCredentials,
            )
        },
    )
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val knownHostCount by viewModel.knownHostCount.collectAsStateWithLifecycle()
    val previewPalette = terminalPalette(settings.terminalColorScheme)

    var fontPreview by remember(settings.fontSizeSp) { mutableFloatStateOf(settings.fontSizeSp) }
    var confirmWipe by remember { mutableStateOf(false) }
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val wipedMessage = stringResource(R.string.settings_wiped)
    val context = LocalContext.current

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        viewModel.exportConfig { result ->
            result.onSuccess { text ->
                scope.launch {
                    val saved = withContext(Dispatchers.IO) {
                        runCatching {
                            context.contentResolver.openOutputStream(uri)?.use { stream ->
                                stream.write(text.toByteArray(Charsets.UTF_8))
                            } ?: error("Could not open the selected file")
                        }.isSuccess
                    }
                    snackbarHost.showSnackbar(
                        context.getString(
                            if (saved) R.string.settings_backup_export_done
                            else R.string.settings_backup_export_failed,
                        ),
                    )
                }
            }.onFailure {
                scope.launch { snackbarHost.showSnackbar(context.getString(R.string.settings_backup_export_failed)) }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                }.getOrNull()
            }
            if (text.isNullOrBlank()) {
                snackbarHost.showSnackbar(context.getString(R.string.settings_backup_import_failed))
                return@launch
            }
            viewModel.importConfig(text) { result ->
                result.onSuccess { outcome ->
                    val message = if (outcome.errors.isEmpty()) {
                        context.getString(
                            R.string.settings_backup_import_done,
                            outcome.imported,
                            outcome.skipped,
                        )
                    } else {
                        context.getString(
                            R.string.settings_backup_import_done_errors,
                            outcome.imported,
                            outcome.skipped,
                            outcome.errors.first(),
                        )
                    }
                    scope.launch { snackbarHost.showSnackbar(message) }
                }.onFailure {
                    scope.launch {
                        snackbarHost.showSnackbar(context.getString(R.string.settings_backup_import_failed))
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            SectionLabel(stringResource(R.string.settings_terminal_section))
            SettingsCard {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.settings_font_size),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "${fontPreview.toInt()} sp",
                            style = MaterialTheme.typography.bodySmall,
                            color = GreenSoft,
                            fontFamily = TerminalFontFamily,
                        )
                    }
                    Slider(
                        value = fontPreview,
                        onValueChange = { fontPreview = it },
                        valueRange = 10f..24f,
                        steps = 13,
                        onValueChangeFinished = { viewModel.setFontSize(fontPreview) },
                    )
                    Text(
                        text = stringResource(R.string.settings_terminal_font_family),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(
                            TerminalFont.SYSTEM_MONO to R.string.settings_terminal_font_system,
                            TerminalFont.SANS_MONO to R.string.settings_terminal_font_sans,
                        ).forEach { (font, label) ->
                            FilterChip(
                                selected = settings.terminalFont == font,
                                onClick = { viewModel.setTerminalFont(font) },
                                label = { Text(stringResource(label)) },
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.settings_terminal_color_scheme),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(
                            TerminalColorScheme.GREEN to R.string.settings_terminal_palette_green,
                            TerminalColorScheme.AMBER to R.string.settings_terminal_palette_amber,
                            TerminalColorScheme.SOLARIZED_DARK to R.string.settings_terminal_palette_solarized,
                            TerminalColorScheme.DRACULA to R.string.settings_terminal_palette_dracula,
                        ).forEach { (scheme, label) ->
                            FilterChip(
                                selected = settings.terminalColorScheme == scheme,
                                onClick = { viewModel.setTerminalColorScheme(scheme) },
                                label = { Text(stringResource(label)) },
                            )
                        }
                    }
                    Text(
                        text = "user@host:~$ htop --sort-key PERCENT_CPU",
                        fontFamily = FontFamily.Monospace,
                        fontSize = fontPreview.sp,
                        color = previewPalette.foreground,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(previewPalette.background)
                            .padding(10.dp),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            SettingsToggle(
                title = stringResource(R.string.settings_keep_screen_on_title),
                subtitle = stringResource(R.string.settings_keep_screen_on_subtitle),
                checked = settings.keepScreenOn,
                onCheckedChange = viewModel::setKeepScreenOn,
            )
            SettingsToggle(
                title = stringResource(R.string.settings_block_screenshots_title),
                subtitle = stringResource(R.string.settings_block_screenshots_subtitle),
                checked = settings.blockScreenshots,
                onCheckedChange = viewModel::setBlockScreenshots,
            )
            SettingsToggle(
                title = stringResource(R.string.settings_auto_reconnect_title),
                subtitle = stringResource(R.string.settings_auto_reconnect_subtitle),
                checked = settings.autoReconnect,
                onCheckedChange = viewModel::setAutoReconnect,
            )

            SectionLabel(stringResource(R.string.settings_scrollback_section))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1_000, 5_000, 10_000, 25_000).forEach { lines ->
                    FilterChip(
                        selected = settings.scrollbackLines == lines,
                        onClick = { viewModel.setScrollbackLines(lines) },
                        label = { Text(if (lines >= 1000) "${lines / 1000}k" else "$lines") },
                    )
                }
            }
            Text(
                text = stringResource(R.string.settings_scrollback_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )

            SectionLabel(stringResource(R.string.settings_appearance_section))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = settings.themeMode == ThemeMode.DARK,
                    onClick = { viewModel.setThemeMode(ThemeMode.DARK) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) { Text(stringResource(R.string.settings_theme_dark)) }
                SegmentedButton(
                    selected = settings.themeMode == ThemeMode.LIGHT,
                    onClick = { viewModel.setThemeMode(ThemeMode.LIGHT) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) { Text(stringResource(R.string.settings_theme_light)) }
            }

            SectionLabel(stringResource(R.string.settings_language_section))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = settings.language == AppLanguage.SYSTEM,
                    onClick = { viewModel.setLanguage(AppLanguage.SYSTEM) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                ) { Text(stringResource(R.string.settings_language_system)) }
                SegmentedButton(
                    selected = settings.language == AppLanguage.ENGLISH,
                    onClick = { viewModel.setLanguage(AppLanguage.ENGLISH) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                ) { Text(stringResource(R.string.settings_language_english)) }
                SegmentedButton(
                    selected = settings.language == AppLanguage.RUSSIAN,
                    onClick = { viewModel.setLanguage(AppLanguage.RUSSIAN) },
                    shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                ) { Text(stringResource(R.string.settings_language_russian)) }
            }
            Text(
                text = stringResource(R.string.settings_language_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )

            SectionLabel(stringResource(R.string.settings_security_section))
            SettingsCard {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenKnownHosts)
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Security, contentDescription = null, tint = GreenSoft)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.settings_known_hosts),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = if (knownHostCount == 0) {
                                    stringResource(R.string.settings_known_hosts_empty)
                                } else {
                                    pluralStringResource(
                                        R.plurals.settings_known_hosts_count,
                                        knownHostCount,
                                        knownHostCount,
                                    )
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(Icons.Outlined.ChevronRight, contentDescription = null)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { confirmWipe = true }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Outlined.DeleteForever,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.settings_wipe_title),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Text(
                                stringResource(R.string.settings_wipe_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            SectionLabel(stringResource(R.string.settings_file_manager_section))
            SettingsCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenSftp)
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Folder, contentDescription = null, tint = GreenSoft)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.settings_sftp_title),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            stringResource(R.string.settings_sftp_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(Icons.Outlined.ChevronRight, contentDescription = null)
                }
            }

            SectionLabel(stringResource(R.string.settings_backup_section))
            SettingsCard {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { exportLauncher.launch("sshmobile-config.json") }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.FileUpload, contentDescription = null, tint = GreenSoft)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.settings_backup_export_title),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                stringResource(R.string.settings_backup_export_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(Icons.Outlined.ChevronRight, contentDescription = null)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                importLauncher.launch(arrayOf("application/json", "text/plain"))
                            }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.FileDownload, contentDescription = null, tint = GreenSoft)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.settings_backup_import_title),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                stringResource(R.string.settings_backup_import_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(Icons.Outlined.ChevronRight, contentDescription = null)
                    }
                }
            }

            SectionLabel(stringResource(R.string.settings_about_section))
            SettingsCard {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        stringResource(R.string.settings_about_version, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.settings_about_text),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        stringResource(R.string.settings_about_licenses),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(28.dp))
            Text(
                text = stringResource(R.string.settings_security_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 24.dp),
            )
        }
    }

    if (confirmWipe) {
        ConfirmDialog(
            title = stringResource(R.string.settings_wipe_dialog_title),
            message = stringResource(R.string.settings_wipe_dialog_message),
            confirmLabel = stringResource(R.string.settings_wipe_confirm),
            destructive = true,
            onConfirm = {
                viewModel.wipeAllData {
                    scope.launch { snackbarHost.showSnackbar(wipedMessage) }
                }
            },
            onDismiss = { confirmWipe = false },
        )
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        content()
    }
}

@Composable
private fun SettingsToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
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
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
