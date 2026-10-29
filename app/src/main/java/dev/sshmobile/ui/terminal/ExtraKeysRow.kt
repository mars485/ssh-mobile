package dev.sshmobile.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.sshmobile.core.terminal.ArrowKey
import dev.sshmobile.core.terminal.TerminalInput
import dev.sshmobile.ui.theme.GreenAccent
import dev.sshmobile.ui.theme.GreenSoft

@Composable
fun ExtraKeysRow(
    ctrl: Boolean,
    alt: Boolean,
    applicationCursorKeys: Boolean,
    hasSelection: Boolean,
    onToggleCtrl: () -> Unit,
    onToggleAlt: () -> Unit,
    onSend: (ByteArray) -> Unit,
    onCopySelection: () -> Unit,
    onClearSelection: () -> Unit,
    onPaste: () -> Unit,
    onShowKeyboard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KeyChip("keyboard", onClick = onShowKeyboard)
        KeyChip("esc", onClick = { onSend(TerminalInput.escape()) })
        KeyChip("tab", onClick = { onSend(TerminalInput.tab()) })
        KeyChip("ctrl", active = ctrl, onClick = onToggleCtrl)
        KeyChip("alt", active = alt, onClick = onToggleAlt)

        KeyChip("←", onClick = {
            onSend(TerminalInput.arrow(ArrowKey.LEFT, applicationCursorKeys, ctrl, alt))
        })
        KeyChip("↑", onClick = {
            onSend(TerminalInput.arrow(ArrowKey.UP, applicationCursorKeys, ctrl, alt))
        })
        KeyChip("↓", onClick = {
            onSend(TerminalInput.arrow(ArrowKey.DOWN, applicationCursorKeys, ctrl, alt))
        })
        KeyChip("→", onClick = {
            onSend(TerminalInput.arrow(ArrowKey.RIGHT, applicationCursorKeys, ctrl, alt))
        })

        KeyChip("home", onClick = { onSend(TerminalInput.home(applicationCursorKeys, ctrl, alt)) })
        KeyChip("end", onClick = { onSend(TerminalInput.end(applicationCursorKeys, ctrl, alt)) })
        KeyChip("pgup", onClick = { onSend(TerminalInput.pageUp()) })
        KeyChip("pgdn", onClick = { onSend(TerminalInput.pageDown()) })

        KeyChip("^C", onClick = { onSend(TerminalInput.ctrlC()) })
        KeyChip("^D", onClick = { onSend(TerminalInput.ctrlD()) })
        KeyChip("^Z", onClick = { onSend(TerminalInput.ctrlZ()) })
        KeyChip("paste", onClick = onPaste)

        if (hasSelection) {
            KeyChip("copy", active = true, onClick = onCopySelection)
            KeyChip("cancel", onClick = onClearSelection)
        }
    }
}

@Composable
private fun KeyChip(
    label: String,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    val background = if (active) GreenAccent.copy(alpha = 0.20f) else MaterialTheme.colorScheme.surfaceVariant
    val content = if (active) GreenSoft else MaterialTheme.colorScheme.onSurface
    Box(
        modifier = Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            color = content,
        )
    }
}
