package dev.sshmobile.ui.servers

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.sshmobile.R
import dev.sshmobile.domain.model.AuthType
import dev.sshmobile.domain.model.ValidationError
import dev.sshmobile.ui.LocalAppContainer
import dev.sshmobile.ui.SimpleViewModelFactory
import dev.sshmobile.ui.components.ConfirmDialog
import dev.sshmobile.ui.components.SectionLabel
import dev.sshmobile.ui.theme.GreenSoft

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerEditScreen(
    serverId: Long,
    onDone: () -> Unit,
) {
    val container = LocalAppContainer.current
    val viewModel: ServerEditViewModel = viewModel(
        key = "server-edit-$serverId",
        factory = SimpleViewModelFactory {
            ServerEditViewModel(serverId, container.serverRepository)
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showPassword by remember { mutableStateOf(false) }
    var showKey by remember { mutableStateOf(false) }
    var showPassphrase by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val keyPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importKey(context, uri)
    }

    LaunchedEffect(state.saved, state.deleted) {
        if (state.saved || state.deleted) onDone()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (state.isNew) R.string.edit_title_new else R.string.edit_title_edit,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = viewModel::save,
                        enabled = !state.saving && !state.loading,
                    ) {
                        Text(
                            text = if (state.saving) {
                                stringResource(R.string.edit_saving)
                            } else {
                                stringResource(R.string.action_save)
                            },
                            color = if (state.saving) MaterialTheme.colorScheme.onSurfaceVariant else GreenSoft,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (state.loading) {
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = GreenSoft)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .imePadding()
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            SectionLabel(stringResource(R.string.edit_connection_section))
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::setName,
                label = { Text(stringResource(R.string.edit_display_name_label)) },
                placeholder = { Text(stringResource(R.string.edit_display_name_placeholder)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = state.host,
                onValueChange = viewModel::setHost,
                label = { Text(stringResource(R.string.edit_host_label)) },
                placeholder = { Text(stringResource(R.string.edit_host_placeholder)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = state.port,
                    onValueChange = viewModel::setPort,
                    label = { Text(stringResource(R.string.edit_port_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(110.dp),
                )
                OutlinedTextField(
                    value = state.username,
                    onValueChange = viewModel::setUsername,
                    label = { Text(stringResource(R.string.edit_username_label)) },
                    placeholder = { Text(stringResource(R.string.edit_username_placeholder)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                    modifier = Modifier.weight(1f),
                )
            }

            SectionLabel(stringResource(R.string.edit_auth_section))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = state.authType == AuthType.PASSWORD,
                    onClick = { viewModel.setAuthType(AuthType.PASSWORD) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.edit_auth_password))
                    }
                }
                SegmentedButton(
                    selected = state.authType == AuthType.PRIVATE_KEY,
                    onClick = { viewModel.setAuthType(AuthType.PRIVATE_KEY) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.edit_auth_key))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            if (state.authType == AuthType.PASSWORD) {
                OutlinedTextField(
                    value = state.password,
                    onValueChange = viewModel::setPassword,
                    label = {
                        Text(
                            stringResource(
                                if (state.canKeepSecret) {
                                    R.string.edit_password_keep_label
                                } else {
                                    R.string.edit_password_label
                                },
                            ),
                        )
                    },
                    singleLine = true,
                    visualTransformation = if (showPassword) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) {
                                    Icons.Outlined.VisibilityOff
                                } else {
                                    Icons.Outlined.Visibility
                                },
                                contentDescription = stringResource(
                                    if (showPassword) R.string.edit_hide_password else R.string.edit_show_password,
                                ),
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                state.storedKeySummary?.let { storedKeySummary ->
                    if (state.canKeepSecret) {
                        Text(
                            text = stringResource(R.string.edit_stored_key, storedKeySummary),
                            style = MaterialTheme.typography.bodySmall,
                            color = GreenSoft,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                }
                OutlinedTextField(
                    value = state.privateKey,
                    onValueChange = viewModel::setPrivateKey,
                    label = {
                        Text(
                            stringResource(
                                if (state.canKeepSecret) {
                                    R.string.edit_key_keep_label
                                } else {
                                    R.string.edit_key_label
                                },
                            ),
                        )
                    },
                    placeholder = {
                        Text(stringResource(R.string.edit_key_placeholder))
                    },
                    minLines = 4,
                    maxLines = 8,
                    visualTransformation = if (showKey) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(
                                imageVector = if (showKey) {
                                    Icons.Outlined.VisibilityOff
                                } else {
                                    Icons.Outlined.Visibility
                                },
                                contentDescription = stringResource(
                                    if (showKey) R.string.edit_hide_key else R.string.edit_show_key,
                                ),
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { keyPicker.launch(arrayOf("*/*")) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.edit_import_key))
                }
                state.importedKeyName?.let { importedKeyName ->
                    Text(
                        text = stringResource(R.string.edit_imported_key, importedKeyName),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = state.passphrase,
                    onValueChange = viewModel::setPassphrase,
                    label = { Text(stringResource(R.string.edit_key_passphrase_label)) },
                    singleLine = true,
                    visualTransformation = if (showPassphrase) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                    trailingIcon = {
                        IconButton(onClick = { showPassphrase = !showPassphrase }) {
                            Icon(
                                imageVector = if (showPassphrase) {
                                    Icons.Outlined.VisibilityOff
                                } else {
                                    Icons.Outlined.Visibility
                                },
                                contentDescription = stringResource(
                                    if (showPassphrase) R.string.edit_hide_passphrase
                                    else R.string.edit_show_passphrase,
                                ),
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(12.dp))
            InfoNote(stringResource(R.string.edit_security_note))

            SectionLabel(stringResource(R.string.edit_organization_section))
            OutlinedTextField(
                value = state.groupName,
                onValueChange = viewModel::setGroup,
                label = { Text(stringResource(R.string.edit_group_label)) },
                placeholder = { Text(stringResource(R.string.edit_group_placeholder)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.edit_favorite_title), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.edit_favorite_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = state.favorite, onCheckedChange = viewModel::setFavorite)
            }

            if (state.validationErrors.isNotEmpty() || state.errorRes != null) {
                Spacer(Modifier.height(16.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(12.dp),
                ) {
                    state.errorRes?.let {
                        Text(
                            text = stringResource(it),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                    state.validationErrors.forEach {
                        Text(
                            text = "• ${stringResource(validationErrorRes(it))}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = viewModel::save,
                enabled = !state.saving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
            ) {
                Text(
                    when {
                        state.saving -> stringResource(R.string.edit_saving)
                        state.isNew -> stringResource(R.string.edit_add_button)
                        else -> stringResource(R.string.edit_save_button)
                    },
                )
            }

            if (!state.isNew) {
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.edit_delete_button))
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.edit_delete_title),
            message = stringResource(R.string.edit_delete_message),
            confirmLabel = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = viewModel::delete,
            onDismiss = { confirmDelete = false },
        )
    }
}

@StringRes
private fun validationErrorRes(error: ValidationError): Int = when (error) {
    ValidationError.NAME_REQUIRED -> R.string.validation_name_required
    ValidationError.HOST_REQUIRED -> R.string.validation_host_required
    ValidationError.HOST_SPACES -> R.string.validation_host_spaces
    ValidationError.PORT_INVALID -> R.string.validation_port_invalid
    ValidationError.USERNAME_REQUIRED -> R.string.validation_username_required
    ValidationError.PASSWORD_REQUIRED -> R.string.validation_password_required
    ValidationError.PRIVATE_KEY_REQUIRED -> R.string.validation_key_required
}

@Composable
private fun InfoNote(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            Icons.Outlined.Lock,
            contentDescription = null,
            tint = GreenSoft,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
