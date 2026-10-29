package dev.sshmobile.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.sshmobile.BuildConfig
import dev.sshmobile.data.repository.ThemeMode
import dev.sshmobile.ui.LocalAppContainer
import dev.sshmobile.ui.SimpleViewModelFactory
import dev.sshmobile.ui.components.ConfirmDialog
import dev.sshmobile.ui.components.SectionLabel
import dev.sshmobile.ui.theme.GreenAccent
import dev.sshmobile.ui.theme.GreenSoft
import dev.sshmobile.ui.theme.TerminalFontFamily
import kotlinx.coroutines.launch

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
                wipeKeystoreKey = container::wipeCredentials,
            )
        },
    )
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val knownHostCount by viewModel.knownHostCount.collectAsStateWithLifecycle()

    var fontPreview by remember(settings.fontSizeSp) { mutableFloatStateOf(settings.fontSizeSp) }
    var confirmWipe by remember { mutableStateOf(false) }
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
            SectionLabel("Terminal")
            SettingsCard {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Font size", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
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
                        text = "user@host:~$ htop --sort-key PERCENT_CPU",
                        fontFamily = FontFamily.Monospace,
                        fontSize = fontPreview.sp,
                        color = GreenSoft,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF070A08))
                            .padding(10.dp),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            SettingsToggle(
                title = "Keep screen on",
                subtitle = "Prevent the display from sleeping while a terminal is open.",
                checked = settings.keepScreenOn,
                onCheckedChange = viewModel::setKeepScreenOn,
            )
            SettingsToggle(
                title = "Block screenshots",
                subtitle = "Hide terminal contents in the recent-apps preview and block screenshots.",
                checked = settings.blockScreenshots,
                onCheckedChange = viewModel::setBlockScreenshots,
            )
            SettingsToggle(
                title = "Automatic reconnect",
                subtitle = "Retry with backoff when an established connection drops unexpectedly.",
                checked = settings.autoReconnect,
                onCheckedChange = viewModel::setAutoReconnect,
            )

            SectionLabel("Scrollback")
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
                text = "Applies to new sessions. Current sessions keep their buffer size.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )

            SectionLabel("Appearance")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = settings.themeMode == ThemeMode.DARK,
                    onClick = { viewModel.setThemeMode(ThemeMode.DARK) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) { Text("Dark") }
                SegmentedButton(
                    selected = settings.themeMode == ThemeMode.LIGHT,
                    onClick = { viewModel.setThemeMode(ThemeMode.LIGHT) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) { Text("Light") }
            }

            SectionLabel("Security")
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
                            Text("Known hosts", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = if (knownHostCount == 0) {
                                    "No trusted host keys yet"
                                } else {
                                    "$knownHostCount trusted host key${if (knownHostCount == 1) "" else "s"}"
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
                                "Wipe all app data",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Text(
                                "Deletes servers, stored credentials and trusted host keys.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            SectionLabel("File manager")
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
                        Text("SFTP", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Browsing, transfers and remote editing arrive in v0.3.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(Icons.Outlined.ChevronRight, contentDescription = null)
                }
            }

            SectionLabel("About")
            SettingsCard {
                Column(Modifier.padding(14.dp)) {
                    Text("SSH Mobile ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "A native Android SSH client built with Kotlin, Jetpack Compose and SSHJ. " +
                            "Secrets are encrypted with the Android Keystore; host keys are verified " +
                            "on every connection and changes require explicit confirmation.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Terminal emulator: VT100/xterm implementation included in this project " +
                            "(no GPL terminal code). SSH transport: SSHJ 0.41 (Apache-2.0).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(28.dp))
            Text(
                text = "Remember: automatic reconnect never bypasses host-key verification.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 24.dp),
            )
        }
    }

    if (confirmWipe) {
        ConfirmDialog(
            title = "Wipe all app data?",
            message = "Every saved server, credential and trusted host key will be deleted. " +
                "This cannot be undone and open sessions may stop working.",
            confirmLabel = "Wipe everything",
            destructive = true,
            onConfirm = {
                viewModel.wipeAllData {
                    scope.launch { snackbarHost.showSnackbar("All local data wiped") }
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
