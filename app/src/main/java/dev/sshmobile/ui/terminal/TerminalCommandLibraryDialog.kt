package dev.sshmobile.ui.terminal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.sshmobile.R
import dev.sshmobile.data.repository.TerminalCommandLibrary
import dev.sshmobile.data.repository.TerminalCommandSnippet
import dev.sshmobile.ui.theme.GreenSoft

@Composable
fun TerminalCommandLibraryDialog(
    library: TerminalCommandLibrary,
    onDismiss: () -> Unit,
    onRunCommand: (String) -> Unit,
    onSaveSnippet: (name: String, command: String, id: String?) -> Unit,
    onDeleteSnippet: (String) -> Unit,
    onClearHistory: () -> Unit,
    onHistoryEnabledChange: (Boolean) -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var editorOpen by remember { mutableStateOf(false) }
    var editingSnippet by remember { mutableStateOf<TerminalCommandSnippet?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.86f),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.terminal_commands_title),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.action_close))
                    }
                }
                Text(
                    text = stringResource(R.string.terminal_commands_encrypted_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    ) { Text(stringResource(R.string.terminal_commands_history)) }
                    SegmentedButton(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    ) { Text(stringResource(R.string.terminal_commands_snippets)) }
                }
                Spacer(Modifier.height(12.dp))

                if (selectedTab == 0) {
                    HistoryContent(
                        modifier = Modifier.weight(1f),
                        history = library.history,
                        historyEnabled = library.historyEnabled,
                        onRunCommand = onRunCommand,
                        onClearHistory = onClearHistory,
                        onHistoryEnabledChange = onHistoryEnabledChange,
                    )
                } else {
                    SnippetsContent(
                        modifier = Modifier.weight(1f),
                        snippets = library.snippets,
                        onAdd = {
                            editingSnippet = null
                            editorOpen = true
                        },
                        onEdit = { snippet ->
                            editingSnippet = snippet
                            editorOpen = true
                        },
                        onRunCommand = onRunCommand,
                        onDeleteSnippet = onDeleteSnippet,
                    )
                }
            }
        }
    }

    if (editorOpen) {
        SnippetEditorDialog(
            initial = editingSnippet,
            onDismiss = { editorOpen = false },
            onSave = { name, command, id ->
                onSaveSnippet(name, command, id)
                editorOpen = false
            },
        )
    }
}

@Composable
private fun HistoryContent(
    modifier: Modifier,
    history: List<String>,
    historyEnabled: Boolean,
    onRunCommand: (String) -> Unit,
    onClearHistory: () -> Unit,
    onHistoryEnabledChange: (Boolean) -> Unit,
) {
    Column(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.terminal_commands_history_record),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringResource(R.string.terminal_commands_history_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = historyEnabled,
                onCheckedChange = onHistoryEnabledChange,
            )
        }
        if (!historyEnabled || history.isEmpty()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(
                        if (historyEnabled) R.string.terminal_commands_history_empty
                        else R.string.terminal_commands_history_disabled,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(history.asReversed()) { command ->
                    CommandCard(command = command, onRun = { onRunCommand(command) })
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        TextButton(
            onClick = onClearHistory,
            enabled = history.isNotEmpty(),
            modifier = Modifier.align(Alignment.End),
        ) {
            Text(stringResource(R.string.terminal_commands_clear_history))
        }
    }
}

@Composable
private fun SnippetsContent(
    modifier: Modifier,
    snippets: List<TerminalCommandSnippet>,
    onAdd: () -> Unit,
    onEdit: (TerminalCommandSnippet) -> Unit,
    onRunCommand: (String) -> Unit,
    onDeleteSnippet: (String) -> Unit,
) {
    Column(modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onAdd) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.terminal_commands_add_snippet))
            }
        }
        if (snippets.isEmpty()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.terminal_commands_snippets_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(snippets, key = TerminalCommandSnippet::id) { snippet ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onRunCommand(snippet.command) }
                                    .padding(vertical = 4.dp),
                            ) {
                                Text(snippet.name, style = MaterialTheme.typography.titleSmall)
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = snippet.command,
                                    maxLines = 3,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { onRunCommand(snippet.command) }) {
                                Icon(
                                    Icons.Outlined.PlayArrow,
                                    contentDescription = stringResource(R.string.terminal_commands_run),
                                    tint = GreenSoft,
                                )
                            }
                            IconButton(onClick = { onEdit(snippet) }) {
                                Icon(
                                    Icons.Outlined.Edit,
                                    contentDescription = stringResource(R.string.action_edit),
                                )
                            }
                            IconButton(onClick = { onDeleteSnippet(snippet.id) }) {
                                Icon(
                                    Icons.Outlined.DeleteForever,
                                    contentDescription = stringResource(R.string.action_delete),
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommandCard(command: String, onRun: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = command,
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onRun)
                    .padding(vertical = 8.dp),
                maxLines = 3,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
            IconButton(onClick = onRun) {
                Icon(
                    Icons.Outlined.PlayArrow,
                    contentDescription = stringResource(R.string.terminal_commands_run),
                    tint = GreenSoft,
                )
            }
        }
    }
}

@Composable
private fun SnippetEditorDialog(
    initial: TerminalCommandSnippet?,
    onDismiss: () -> Unit,
    onSave: (name: String, command: String, id: String?) -> Unit,
) {
    var name by remember(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var command by remember(initial?.id) { mutableStateOf(initial?.command.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (initial == null) R.string.terminal_commands_add_snippet
                    else R.string.terminal_commands_edit_snippet,
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(80) },
                    label = { Text(stringResource(R.string.terminal_commands_snippet_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = command,
                    onValueChange = { command = it.take(8_000) },
                    label = { Text(stringResource(R.string.terminal_commands_snippet_command)) },
                    supportingText = { Text(stringResource(R.string.terminal_commands_snippet_help)) },
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                    minLines = 3,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && command.isNotBlank(),
                onClick = { onSave(name, command, initial?.id) },
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
