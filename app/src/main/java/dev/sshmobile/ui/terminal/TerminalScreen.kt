package dev.sshmobile.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.sshmobile.core.session.SessionStatus
import dev.sshmobile.core.terminal.ArrowKey
import dev.sshmobile.core.terminal.TerminalInput
import dev.sshmobile.data.repository.AppSettings
import dev.sshmobile.ui.LocalAppContainer
import dev.sshmobile.ui.SimpleViewModelFactory
import dev.sshmobile.ui.components.ConfirmDialog
import dev.sshmobile.ui.components.FingerprintBlock
import dev.sshmobile.ui.components.StatusDot
import dev.sshmobile.ui.components.statusLabel
import dev.sshmobile.ui.theme.ErrorRed
import dev.sshmobile.ui.theme.GreenAccent
import dev.sshmobile.ui.theme.GreenSoft
import dev.sshmobile.ui.theme.TerminalFontFamily
import dev.sshmobile.ui.theme.WarningAmber
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    sessionId: String,
    onBack: () -> Unit,
    onOpenSessions: () -> Unit,
) {
    val container = LocalAppContainer.current
    val viewModel: TerminalViewModel = viewModel(
        key = "terminal-$sessionId",
        factory = SimpleViewModelFactory {
            TerminalViewModel(container.sessionManager, sessionId)
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val settingsFlow = remember(container) { container.settingsRepository.settings }
    val settings by settingsFlow.collectAsStateWithLifecycle(initialValue = AppSettings())

    val clipboard = LocalClipboardManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val snackbarHost = remember { SnackbarHostState() }
    val view = LocalView.current

    var ctrl by remember { mutableStateOf(false) }
    var alt by remember { mutableStateOf(false) }
    var showExtraKeys by remember { mutableStateOf(true) }
    var showBuffer by remember { mutableStateOf(false) }
    var confirmClose by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var selection by remember { mutableStateOf<LineSelection?>(null) }
    var imeBuffer by remember { mutableStateOf("") }

    val focusRequester = remember { FocusRequester() }
    val rootFocusRequester = remember { FocusRequester() }
    val currentSnapshot by rememberUpdatedState(snapshot)

    DisposableEffect(settings.keepScreenOn) {
        view.keepScreenOn = settings.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    LaunchedEffect(Unit) {
        runCatching { rootFocusRequester.requestFocus() }
    }

    fun showKeyboard() {
        focusRequester.requestFocus()
        keyboard?.show()
    }

    fun sendTypedText(text: String) {
        if (text.isEmpty()) return
        if (ctrl || alt) {
            viewModel.sendText(text.take(1), ctrl, alt)
            if (text.length > 1) viewModel.sendText(text.substring(1))
            ctrl = false
            alt = false
        } else {
            viewModel.sendText(text)
        }
    }

    fun copySelection() {
        val current = selection ?: return
        val text = viewModel.textForLines(current.first, current.last)
        if (text.isNotEmpty()) {
            clipboard.setText(AnnotatedString(text))
            scope.launch { snackbarHost.showSnackbar("Copied ${current.last - current.first + 1} line(s)") }
        }
        selection = null
    }

    fun pasteFromClipboard() {
        val text = clipboard.getText()?.text
        if (text.isNullOrEmpty()) {
            scope.launch { snackbarHost.showSnackbar("Clipboard is empty") }
            return
        }
        viewModel.paste(text, snapshot.bracketedPaste)
        scope.launch { snackbarHost.showSnackbar("Pasted ${text.length} character(s)") }
    }

    fun closeSession() {
        viewModel.closeSession()
        onBack()
    }

    fun handleHardwareKey(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        val withCtrl = event.isCtrlPressed
        val withAlt = event.isAltPressed
        val appCursor = currentSnapshot.applicationCursorKeys
        val sequence: ByteArray? = when (event.key) {
            Key.Enter -> TerminalInput.enter()
            Key.Backspace -> TerminalInput.backspace()
            Key.Tab -> if (event.isShiftPressed) TerminalInput.backTab() else TerminalInput.tab()
            Key.Escape -> TerminalInput.escape()
            Key.DirectionUp -> TerminalInput.arrow(ArrowKey.UP, appCursor, withCtrl, withAlt)
            Key.DirectionDown -> TerminalInput.arrow(ArrowKey.DOWN, appCursor, withCtrl, withAlt)
            Key.DirectionLeft -> TerminalInput.arrow(ArrowKey.LEFT, appCursor, withCtrl, withAlt)
            Key.DirectionRight -> TerminalInput.arrow(ArrowKey.RIGHT, appCursor, withCtrl, withAlt)
            Key.MoveHome -> TerminalInput.home(appCursor, withCtrl, withAlt)
            Key.MoveEnd -> TerminalInput.end(appCursor, withCtrl, withAlt)
            Key.Insert -> TerminalInput.insert()
            Key.Delete -> TerminalInput.delete()
            Key.PageUp -> TerminalInput.pageUp()
            Key.PageDown -> TerminalInput.pageDown()
            Key.F1 -> TerminalInput.function(1)
            Key.F2 -> TerminalInput.function(2)
            Key.F3 -> TerminalInput.function(3)
            Key.F4 -> TerminalInput.function(4)
            Key.F5 -> TerminalInput.function(5)
            Key.F6 -> TerminalInput.function(6)
            Key.F7 -> TerminalInput.function(7)
            Key.F8 -> TerminalInput.function(8)
            Key.F9 -> TerminalInput.function(9)
            Key.F10 -> TerminalInput.function(10)
            Key.F11 -> TerminalInput.function(11)
            Key.F12 -> TerminalInput.function(12)
            else -> null
        }
        if (sequence != null) {
            viewModel.sendKey(sequence)
            return true
        }
        val codePoint = event.utf16CodePoint
        if (codePoint > 0 && codePoint != 0x7F) {
            val char = codePoint.toChar()
            if (!char.isISOControl()) {
                viewModel.sendText(char.toString(), withCtrl, withAlt)
                return true
            }
        }
        return false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = state.serverName,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusDot(state.status, size = 7)
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = statusLabel(state.status) +
                                    if (state.hostLabel.isNotBlank()) " · ${state.hostLabel}" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.status == SessionStatus.DISCONNECTED) {
                        IconButton(onClick = viewModel::reconnect) {
                            Icon(Icons.Outlined.Refresh, contentDescription = "Reconnect")
                        }
                    }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = "More")
                    }
                    IconButton(onClick = { confirmClose = true }) {
                        Icon(Icons.Outlined.Close, contentDescription = "Close session")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Copy terminal buffer") },
                            leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                showBuffer = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Paste") },
                            leadingIcon = { Icon(Icons.Outlined.ContentPaste, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                pasteFromClipboard()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(if (showExtraKeys) "Hide extra keys" else "Show extra keys") },
                            onClick = {
                                menuOpen = false
                                showExtraKeys = !showExtraKeys
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Sessions") },
                            onClick = {
                                menuOpen = false
                                onOpenSessions()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Close session") },
                            onClick = {
                                menuOpen = false
                                confirmClose = true
                            },
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
        containerColor = TerminalBackgroundColor,
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            if (!viewModel.available) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("Session unavailable", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        state.errorMessage ?: "Open the server again from the dashboard.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = onBack) { Text("Back to servers") }
                }
                return@Box
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .onPreviewKeyEvent { handleHardwareKey(it) }
                    .focusRequester(rootFocusRequester)
                    .focusable(),
            ) {
                Box(Modifier.weight(1f)) {
                    TerminalCanvas(
                        snapshot = snapshot,
                        fontSizeSp = settings.fontSizeSp,
                        selection = selection,
                        onResize = viewModel::resize,
                        onScroll = viewModel::scroll,
                        onSelectLines = { start, end ->
                            selection = LineSelection(start, end)
                        },
                        onTap = { showKeyboard() },
                        onClearSelection = { selection = null },
                        modifier = Modifier.fillMaxSize(),
                    )

                    if (
                        state.status == SessionStatus.CONNECTING ||
                        state.status == SessionStatus.VERIFYING_HOST_KEY ||
                        state.status == SessionStatus.RECONNECTING
                    ) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 8.dp),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                                tonalElevation = 3.dp,
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(13.dp),
                                        strokeWidth = 2.dp,
                                        color = GreenSoft,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = when (state.status) {
                                            SessionStatus.VERIFYING_HOST_KEY -> "Waiting for host key confirmation…"
                                            SessionStatus.RECONNECTING ->
                                                "Reconnecting (attempt ${state.reconnectAttempt})…"
                                            else -> "Connecting…"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                    }

                    if (state.status == SessionStatus.DISCONNECTED) {
                        Card(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                            ),
                        ) {
                            Column(Modifier.padding(18.dp)) {
                                Text(
                                    "Connection lost",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    state.errorMessage ?: "The SSH session is no longer connected.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(14.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = viewModel::reconnect,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = GreenAccent,
                                            contentColor = Color(0xFF00210F),
                                        ),
                                    ) { Text("Reconnect") }
                                    TextButton(onClick = onOpenSessions) { Text("Sessions") }
                                }
                            }
                        }
                    }

                    if (snapshot.scrollOffset > 0) {
                        FilledTonalButton(
                            onClick = viewModel::scrollToBottom,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(12.dp),
                        ) {
                            Icon(
                                Icons.Rounded.KeyboardArrowDown,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Latest")
                        }
                    }
                }

                if (showExtraKeys) {
                    ExtraKeysRow(
                        ctrl = ctrl,
                        alt = alt,
                        applicationCursorKeys = snapshot.applicationCursorKeys,
                        hasSelection = selection != null,
                        onToggleCtrl = { ctrl = !ctrl },
                        onToggleAlt = { alt = !alt },
                        onSend = viewModel::sendKey,
                        onCopySelection = ::copySelection,
                        onClearSelection = { selection = null },
                        onPaste = ::pasteFromClipboard,
                        onShowKeyboard = ::showKeyboard,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface),
                    )
                }
            }

            // Invisible field used to receive soft keyboard input.
            BasicTextField(
                value = imeBuffer,
                onValueChange = { newValue ->
                    when {
                        newValue.startsWith(imeBuffer) -> {
                            sendTypedText(newValue.substring(imeBuffer.length))
                        }
                        imeBuffer.startsWith(newValue) -> {
                            repeat(imeBuffer.length - newValue.length) {
                                viewModel.sendKey(TerminalInput.backspace())
                            }
                        }
                        else -> {
                            // The IME replaced the composing region; treat the new text as input.
                            sendTypedText(newValue)
                        }
                    }
                    imeBuffer = newValue
                    if (imeBuffer.length > 512) imeBuffer = ""
                },
                modifier = Modifier
                    .size(1.dp)
                    .align(Alignment.TopStart)
                    .focusRequester(focusRequester),
                textStyle = TextStyle(color = Color.Transparent, fontSize = 1.sp),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.Transparent),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Ascii,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.None,
                ),
            )
        }
    }

    state.hostKeyPrompt?.let { challenge ->
        val changed = challenge.changed
        AlertDialog(
            onDismissRequest = { viewModel.respondToHostKey(challenge.id, false) },
            icon = {
                Icon(
                    imageVector = if (changed) Icons.Outlined.WarningAmber else Icons.Outlined.Key,
                    contentDescription = null,
                    tint = if (changed) WarningAmber else GreenSoft,
                )
            },
            title = {
                Text(if (changed) "Host key has changed" else "Unknown host key")
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
                        text = "Key type: ${challenge.keyType}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "New fingerprint",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FingerprintBlock(challenge.fingerprint)
                    if (changed && challenge.previousFingerprint != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Previously trusted fingerprint",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FingerprintBlock(challenge.previousFingerprint, color = WarningAmber)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = if (changed) {
                            "The server presented a different key than the one saved on this device. " +
                                "This can happen after a server reinstall, but it may also indicate a " +
                                "man-in-the-middle attack. Verify the fingerprint with your server " +
                                "administrator before accepting."
                        } else {
                            "This host has not been seen before. Verify the fingerprint with the " +
                                "server administrator or the server console before accepting. " +
                                "Accepting an unverified key removes protection against interception."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (changed) ErrorRed else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.respondToHostKey(challenge.id, true) },
                ) {
                    Text(
                        text = if (changed) "Accept new key" else "Accept & connect",
                        color = if (changed) ErrorRed else GreenSoft,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.respondToHostKey(challenge.id, false) }) {
                    Text("Reject")
                }
            },
        )
    }

    if (confirmClose) {
        ConfirmDialog(
            title = "Close session?",
            message = "The SSH connection will be terminated. The terminal output will be lost.",
            confirmLabel = "Close session",
            destructive = true,
            onConfirm = ::closeSession,
            onDismiss = { confirmClose = false },
        )
    }

    if (showBuffer) {
        BufferDialog(
            text = viewModel.dumpText(),
            onCopy = {
                viewModel.dumpText().takeIf { it.isNotEmpty() }?.let {
                    clipboard.setText(AnnotatedString(it))
                    scope.launch { snackbarHost.showSnackbar("Terminal buffer copied") }
                }
            },
            onDismiss = { showBuffer = false },
        )
    }
}

@Composable
private fun BufferDialog(
    text: String,
    onCopy: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.8f),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Terminal buffer",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onCopy) { Text("Copy all") }
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    SelectionContainer {
                        Text(
                            text = text.ifEmpty { "The terminal buffer is empty." },
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

private val TerminalBackgroundColor = Color(0xFF070A08)
