package dev.sshmobile.ui.sftp

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.sshmobile.R
import dev.sshmobile.core.session.SftpOperation
import dev.sshmobile.core.session.SftpStatus
import dev.sshmobile.core.session.SftpTransfer
import dev.sshmobile.core.session.SftpTransferDirection
import dev.sshmobile.core.session.SftpTransferStatus
import dev.sshmobile.core.ssh.SftpEntry
import dev.sshmobile.domain.model.HostKeyChallenge
import dev.sshmobile.ui.LocalAppContainer
import dev.sshmobile.ui.SimpleViewModelFactory
import dev.sshmobile.ui.components.ConfirmDialog
import dev.sshmobile.ui.components.FingerprintBlock
import dev.sshmobile.ui.components.sessionErrorRes
import dev.sshmobile.ui.theme.GreenSoft
import dev.sshmobile.ui.theme.TerminalFontFamily
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SftpScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val viewModel: SftpViewModel = viewModel(
        factory = SimpleViewModelFactory {
            SftpViewModel(container.sftpSessionManager, container.serverRepository)
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val servers by viewModel.servers.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHost = remember { SnackbarHostState() }

    var selectedServerId by remember { mutableStateOf<Long?>(null) }
    var serverMenuOpen by remember { mutableStateOf(false) }
    var newFolderOpen by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<SftpEntry?>(null) }
    var deleteTarget by remember { mutableStateOf<SftpEntry?>(null) }
    var permissionsTarget by remember { mutableStateOf<SftpEntry?>(null) }
    var downloadTarget by remember { mutableStateOf<SftpEntry?>(null) }
    var actionMenuFor by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(servers, state.serverId) {
        if (selectedServerId == null) {
            selectedServerId = state.serverId ?: servers.firstOrNull()?.id
        }
    }

    val uploadLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val resolver = context.contentResolver
        val name = resolver.displayName(uri) ?: "upload.bin"
        val size = resolver.sizeOf(uri)
        val stream = runCatching { resolver.openInputStream(uri) }.getOrNull()
        if (stream == null) {
            scope.launch {
                snackbarHost.showSnackbar(context.getString(R.string.sftp_open_file_failed))
            }
        } else {
            viewModel.upload(name, stream, size)
        }
    }

    val downloadLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        val entry = downloadTarget
        downloadTarget = null
        if (uri == null || entry == null) return@rememberLauncherForActivityResult
        val stream = runCatching { context.contentResolver.openOutputStream(uri) }.getOrNull()
        if (stream == null) {
            scope.launch {
                snackbarHost.showSnackbar(context.getString(R.string.sftp_open_file_failed))
            }
        } else {
            viewModel.download(entry, stream)
        }
    }

    LaunchedEffect(state.operationError?.id) {
        val error = state.operationError ?: return@LaunchedEffect
        snackbarHost.showSnackbar(
            context.getString(
                R.string.sftp_operation_failed,
                context.getString(operationLabelRes(error.operation)),
            ),
        )
        viewModel.clearOperationError()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sftp_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    if (state.status == SftpStatus.CONNECTED) {
                        IconButton(onClick = viewModel::disconnect) {
                            Icon(
                                Icons.Outlined.LinkOff,
                                contentDescription = stringResource(R.string.sftp_disconnect),
                            )
                        }
                    }
                },
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
                .fillMaxSize(),
        ) {
            if (state.status == SftpStatus.CONNECTED) {
                ConnectedContent(
                    state = state,
                    onUp = viewModel::up,
                    onRefresh = viewModel::refresh,
                    onNewFolder = { newFolderOpen = true },
                    onUpload = { uploadLauncher.launch(arrayOf("*/*")) },
                    onOpenEntry = { entry ->
                        if (entry.isDirectory) {
                            viewModel.open(entry.path)
                        } else {
                            downloadTarget = entry
                            downloadLauncher.launch(entry.name)
                        }
                    },
                    menuFor = actionMenuFor,
                    onMenu = { path, open -> actionMenuFor = if (open) path else null },
                    onDownload = { entry ->
                        downloadTarget = entry
                        downloadLauncher.launch(entry.name)
                    },
                    onRename = { renameTarget = it },
                    onPermissions = { permissionsTarget = it },
                    onDelete = { deleteTarget = it },
                )
            } else {
                ConnectionContent(
                    servers = servers,
                    selectedServerId = selectedServerId,
                    onSelectServer = { selectedServerId = it },
                    serverMenuOpen = serverMenuOpen,
                    onMenuOpen = { serverMenuOpen = true },
                    onMenuDismiss = { serverMenuOpen = false },
                    status = state.status,
                    error = state.error,
                    onConnect = { selectedServerId?.let(viewModel::connect) },
                )
            }
        }
    }

    if (newFolderOpen) {
        TextInputDialog(
            title = stringResource(R.string.sftp_new_folder),
            label = stringResource(R.string.sftp_name_label),
            initial = "",
            confirmLabel = stringResource(R.string.action_create),
            onConfirm = {
                newFolderOpen = false
                viewModel.mkdir(it)
            },
            onDismiss = { newFolderOpen = false },
        )
    }

    renameTarget?.let { target ->
        TextInputDialog(
            title = stringResource(R.string.sftp_rename),
            label = stringResource(R.string.sftp_name_label),
            initial = target.name,
            confirmLabel = stringResource(R.string.action_save),
            onConfirm = {
                renameTarget = null
                viewModel.rename(target, it)
            },
            onDismiss = { renameTarget = null },
        )
    }

    permissionsTarget?.let { target ->
        TextInputDialog(
            title = stringResource(R.string.sftp_permissions),
            label = stringResource(R.string.sftp_permissions_label),
            initial = Integer.toOctalString(target.permissions),
            confirmLabel = stringResource(R.string.action_save),
            onConfirm = { value ->
                permissionsTarget = null
                value.trim().toIntOrNull(8)?.let { viewModel.chmod(target, it) }
            },
            onDismiss = { permissionsTarget = null },
        )
    }

    deleteTarget?.let { target ->
        ConfirmDialog(
            title = stringResource(R.string.sftp_delete_title),
            message = stringResource(R.string.sftp_delete_message, target.name),
            confirmLabel = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = { viewModel.delete(target) },
            onDismiss = { deleteTarget = null },
        )
    }

    state.hostKeyPrompt?.let { challenge ->
        HostKeyPromptDialog(
            challenge = challenge,
            onAccept = { viewModel.respondToHostKey(challenge.id, true) },
            onReject = { viewModel.respondToHostKey(challenge.id, false) },
        )
    }
}

@Composable
private fun ConnectionContent(
    servers: List<dev.sshmobile.domain.model.Server>,
    selectedServerId: Long?,
    onSelectServer: (Long) -> Unit,
    serverMenuOpen: Boolean,
    onMenuOpen: () -> Unit,
    onMenuDismiss: () -> Unit,
    status: SftpStatus,
    error: dev.sshmobile.core.session.SessionError?,
    onConnect: () -> Unit,
) {
    val selected = servers.firstOrNull { it.id == selectedServerId }
    val busy = status == SftpStatus.CONNECTING || status == SftpStatus.VERIFYING_HOST_KEY

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant,
            ),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    stringResource(R.string.sftp_connect_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onMenuOpen,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = servers.isNotEmpty() && !busy,
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(
                            stringResource(R.string.sftp_server_label),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            selected?.let { "${it.name} · ${it.username}@${it.host}:${it.port}" }
                                ?: stringResource(R.string.sftp_server_none),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                DropdownMenu(
                    expanded = serverMenuOpen,
                    onDismissRequest = onMenuDismiss,
                ) {
                    servers.forEach { server ->
                        DropdownMenuItem(
                            text = {
                                Text("${server.name} · ${server.username}@${server.host}:${server.port}")
                            },
                            onClick = {
                                onSelectServer(server.id)
                                onMenuDismiss()
                            },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onConnect,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = selected != null && !busy,
                ) {
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(
                        stringResource(
                            if (busy) R.string.sftp_connecting else R.string.sftp_connect,
                        ),
                    )
                }
                if (status == SftpStatus.DISCONNECTED && error != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        stringResource(sessionErrorRes(error)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.sftp_intro),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ConnectedContent(
    state: dev.sshmobile.core.session.SftpState,
    onUp: () -> Unit,
    onRefresh: () -> Unit,
    onNewFolder: () -> Unit,
    onUpload: () -> Unit,
    onOpenEntry: (SftpEntry) -> Unit,
    menuFor: String?,
    onMenu: (String, Boolean) -> Unit,
    onDownload: (SftpEntry) -> Unit,
    onRename: (SftpEntry) -> Unit,
    onPermissions: (SftpEntry) -> Unit,
    onDelete: (SftpEntry) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onUp, enabled = state.path != "/") {
            Icon(
                Icons.Outlined.ArrowUpward,
                contentDescription = stringResource(R.string.sftp_up),
            )
        }
        Text(
            text = state.path,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = TerminalFontFamily,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        IconButton(onClick = onRefresh, enabled = !state.loading) {
            Icon(
                Icons.Outlined.Refresh,
                contentDescription = stringResource(R.string.sftp_refresh),
            )
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(onClick = onNewFolder, modifier = Modifier.weight(1f)) {
            Icon(
                Icons.Outlined.CreateNewFolder,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.sftp_new_folder))
        }
        OutlinedButton(onClick = onUpload, modifier = Modifier.weight(1f)) {
            Icon(
                Icons.Outlined.UploadFile,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.sftp_upload))
        }
    }

    if (state.transfers.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        state.transfers.take(3).forEach { transfer ->
            TransferRow(transfer)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }

    if (state.loading) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }

    if (state.entries.isEmpty() && !state.loading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(R.string.sftp_empty_folder),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(state.entries, key = { it.path }) { entry ->
                EntryRow(
                    entry = entry,
                    menuOpen = menuFor == entry.path,
                    onOpen = { onOpenEntry(entry) },
                    onMenu = { open -> onMenu(entry.path, open) },
                    onDownload = { onDownload(entry) },
                    onRename = { onRename(entry) },
                    onPermissions = { onPermissions(entry) },
                    onDelete = { onDelete(entry) },
                )
            }
        }
    }
}

@Composable
private fun TransferRow(transfer: SftpTransfer) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (transfer.direction == SftpTransferDirection.UPLOAD) {
                Icons.Outlined.UploadFile
            } else {
                Icons.Outlined.Download
            },
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = GreenSoft,
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = transfer.name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = transferProgressText(transfer),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when (transfer.status) {
                SftpTransferStatus.RUNNING -> {
                    val progress = transfer.progress
                    if (progress != null) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }

                SftpTransferStatus.DONE -> Unit
                SftpTransferStatus.FAILED -> Text(
                    text = transfer.error ?: stringResource(R.string.sftp_transfer_failed),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun EntryRow(
    entry: SftpEntry,
    menuOpen: Boolean,
    onOpen: () -> Unit,
    onMenu: (Boolean) -> Unit,
    onDownload: () -> Unit,
    onRename: () -> Unit,
    onPermissions: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (entry.isDirectory) {
                Icons.Outlined.Folder
            } else {
                Icons.AutoMirrored.Outlined.InsertDriveFile
            },
            contentDescription = null,
            tint = if (entry.isDirectory) GreenSoft else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${formatBytes(entry.size)} · ${permissionsText(entry.permissions)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            IconButton(onClick = { onMenu(true) }) {
                Icon(
                    Icons.Outlined.MoreVert,
                    contentDescription = stringResource(R.string.sftp_more_actions),
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { onMenu(false) }) {
                if (!entry.isDirectory) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.sftp_download)) },
                        leadingIcon = { Icon(Icons.Outlined.Download, contentDescription = null) },
                        onClick = {
                            onMenu(false)
                            onDownload()
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.sftp_rename)) },
                    leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                    onClick = {
                        onMenu(false)
                        onRename()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.sftp_permissions)) },
                    leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                    onClick = {
                        onMenu(false)
                        onPermissions()
                    },
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(R.string.sftp_delete),
                            color = MaterialTheme.colorScheme.error,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                    },
                    onClick = {
                        onMenu(false)
                        onDelete()
                    },
                )
            }
        }
    }
}

@Composable
private fun TextInputDialog(
    title: String,
    label: String,
    initial: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(value) },
                enabled = value.isNotBlank(),
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun HostKeyPromptDialog(
    challenge: HostKeyChallenge,
    onAccept: () -> Unit,
    onReject: () -> Unit,
) {
    val changed = challenge.changed
    AlertDialog(
        onDismissRequest = onReject,
        icon = {
            Icon(
                imageVector = if (changed) Icons.Outlined.WarningAmber else Icons.Outlined.Key,
                contentDescription = null,
                tint = if (changed) MaterialTheme.colorScheme.error else GreenSoft,
            )
        },
        title = {
            Text(
                stringResource(
                    if (changed) R.string.terminal_changed_host_title
                    else R.string.terminal_unknown_host_title,
                ),
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "${challenge.host}:${challenge.port}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = TerminalFontFamily,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.terminal_key_type, challenge.keyType),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.terminal_new_fingerprint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FingerprintBlock(challenge.fingerprint)
                if (changed && challenge.previousFingerprint != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.terminal_previous_fingerprint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FingerprintBlock(
                        challenge.previousFingerprint,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(
                        if (changed) R.string.terminal_changed_host_warning
                        else R.string.terminal_unknown_host_warning,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (changed) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onAccept) {
                Text(
                    text = stringResource(
                        if (changed) R.string.terminal_accept_new_key
                        else R.string.terminal_accept_connect,
                    ),
                    color = if (changed) MaterialTheme.colorScheme.error else GreenSoft,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onReject) { Text(stringResource(R.string.action_reject)) }
        },
    )
}

@StringRes
private fun operationLabelRes(operation: SftpOperation): Int = when (operation) {
    SftpOperation.LIST -> R.string.sftp_op_list
    SftpOperation.MKDIR -> R.string.sftp_op_mkdir
    SftpOperation.RENAME -> R.string.sftp_op_rename
    SftpOperation.DELETE -> R.string.sftp_op_delete
    SftpOperation.CHMOD -> R.string.sftp_op_chmod
    SftpOperation.UPLOAD -> R.string.sftp_op_upload
    SftpOperation.DOWNLOAD -> R.string.sftp_op_download
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
    bytes < 1024L * 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    else -> String.format(Locale.US, "%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
}

private fun transferProgressText(transfer: SftpTransfer): String {
    val total = transfer.bytesTotal
    return if (total != null && total > 0L) {
        "${formatBytes(transfer.bytesDone)} / ${formatBytes(total)}"
    } else {
        formatBytes(transfer.bytesDone)
    }
}

private fun permissionsText(mode: Int): String {
    val flags = intArrayOf(0x100, 0x80, 0x40, 0x20, 0x10, 0x8, 0x4, 0x2, 0x1)
    val letters = charArrayOf('r', 'w', 'x', 'r', 'w', 'x', 'r', 'w', 'x')
    return buildString {
        flags.forEachIndexed { index, bit ->
            append(if (mode and bit != 0) letters[index] else '-')
        }
    }
}

private fun ContentResolver.displayName(uri: Uri): String? =
    query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }

private fun ContentResolver.sizeOf(uri: Uri): Long? =
    query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
    }
