package dev.sshmobile.ui.terminal

import androidx.compose.ui.graphics.Color
import dev.sshmobile.data.repository.TerminalColorScheme

data class TerminalPalette(
    val background: Color,
    val foreground: Color,
    val accent: Color,
    val selection: Color,
)

fun terminalPalette(scheme: TerminalColorScheme): TerminalPalette = when (scheme) {
    TerminalColorScheme.GREEN -> TerminalPalette(
        background = Color(0xFF070A08),
        foreground = Color(0xFFE8F2EC),
        accent = Color(0xFF00E676),
        selection = Color(0x5500E676),
    )
    TerminalColorScheme.AMBER -> TerminalPalette(
        background = Color(0xFF100B04),
        foreground = Color(0xFFFFE6B3),
        accent = Color(0xFFFFB300),
        selection = Color(0x55FFB300),
    )
    TerminalColorScheme.SOLARIZED_DARK -> TerminalPalette(
        background = Color(0xFF002B36),
        foreground = Color(0xFF93A1A1),
        accent = Color(0xFF2AA198),
        selection = Color(0x552AA198),
    )
    TerminalColorScheme.DRACULA -> TerminalPalette(
        background = Color(0xFF282A36),
        foreground = Color(0xFFF8F8F2),
        accent = Color(0xFFBD93F9),
        selection = Color(0x55BD93F9),
    )
}
