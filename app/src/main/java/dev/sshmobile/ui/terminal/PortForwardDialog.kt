package dev.sshmobile.ui.terminal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.sshmobile.R
import dev.sshmobile.core.session.ActivePortForward
import dev.sshmobile.core.ssh.PortForwardSpec
import dev.sshmobile.core.ssh.PortForwardType
import kotlinx.coroutines.launch

@Composable
fun PortForwardDialog(
    activeForwards: List<ActivePortForward>,
    onDismiss: () -> Unit,
    onStart: suspend (PortForwardSpec) -> Unit,
    onStop: (String) -> Unit,
) {
    var type by remember { mutableStateOf(PortForwardType.LOCAL) }
    var bindHost by remember { mutableStateOf("127.0.0.1") }
    var bindPort by remember { mutableStateOf("8080") }
    var destinationHost by remember { mutableStateOf("127.0.0.1") }
    var destinationPort by remember { mutableStateOf("80") }
    var starting by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val startFailed = stringResource(R.string.terminal_forward_start_failed)
    val bindPortValue = bindPort.toIntOrNull()
    val destinationPortValue = destinationPort.toIntOrNull()
    val valid = bindHost.isNotBlank() && destinationHost.isNotBlank() &&
        bindPortValue in 1..65535 && destinationPortValue in 1..65535

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.92f),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.terminal_forward_title),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.action_close))
                    }
                }
                Text(
                    text = stringResource(R.string.terminal_forward_security_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = type == PortForwardType.LOCAL,
                        onClick = { type = PortForwardType.LOCAL },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    ) { Text(stringResource(R.string.terminal_forward_local)) }
                    SegmentedButton(
                        selected = type == PortForwardType.REMOTE,
                        onClick = { type = PortForwardType.REMOTE },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    ) { Text(stringResource(R.string.terminal_forward_remote)) }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = bindHost,
                    onValueChange = { bindHost = it.take(255) },
                    label = {
                        Text(
                            stringResource(
                                if (type == PortForwardType.LOCAL) R.string.terminal_forward_bind_local_host
                                else R.string.terminal_forward_bind_remote_host,
                            ),
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = bindPort,
                    onValueChange = { bindPort = it.filter(Char::isDigit).take(5) },
                    label = {
                        Text(
                            stringResource(
                                if (type == PortForwardType.LOCAL) R.string.terminal_forward_bind_local_port
                                else R.string.terminal_forward_bind_remote_port,
                            ),
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = destinationHost,
                    onValueChange = { destinationHost = it.take(255) },
                    label = {
                        Text(
                            stringResource(
                                if (type == PortForwardType.LOCAL) R.string.terminal_forward_destination_remote
                                else R.string.terminal_forward_destination_device,
                            ),
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = destinationPort,
                    onValueChange = { destinationPort = it.filter(Char::isDigit).take(5) },
                    label = { Text(stringResource(R.string.terminal_forward_destination_port)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (showError) {
                    Text(
                        text = startFailed,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                Button(
                    onClick = {
                        val spec = PortForwardSpec(
                            type = type,
                            bindHost = bindHost.trim(),
                            bindPort = bindPortValue ?: 0,
                            destinationHost = destinationHost.trim(),
                            destinationPort = destinationPortValue ?: 0,
                        )
                        scope.launch {
                            starting = true
                            showError = false
                            try {
                                onStart(spec)
                            } catch (_: Throwable) {
                                showError = true
                            } finally {
                                starting = false
                            }
                        }
                    },
                    enabled = valid && !starting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                ) {
                    Text(
                        stringResource(
                            if (starting) R.string.terminal_forward_starting
                            else R.string.terminal_forward_start,
                        ),
                    )
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.terminal_forward_active),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(6.dp))
                if (activeForwards.isEmpty()) {
                    Text(
                        stringResource(R.string.terminal_forward_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    activeForwards.forEach { forward ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                        ) {
                            Row(
                                modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(
                                            if (forward.spec.type == PortForwardType.LOCAL) R.string.terminal_forward_local
                                            else R.string.terminal_forward_remote,
                                        ),
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                    Text(
                                        text = "${forward.spec.bindHost}:${forward.boundPort} → " +
                                            "${forward.spec.destinationHost}:${forward.spec.destinationPort}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                TextButton(onClick = { onStop(forward.id) }) {
                                    Text(stringResource(R.string.terminal_forward_stop))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
