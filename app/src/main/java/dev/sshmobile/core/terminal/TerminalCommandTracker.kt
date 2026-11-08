package dev.sshmobile.core.terminal

/** Conservatively tracks plain command-line input without taking over shell cursor/history keys. */
class TerminalCommandTracker {
    private val draft = StringBuilder()
    private var reliable = true

    fun type(text: String, enabled: Boolean = true): List<String> {
        if (!enabled) {
            invalidate()
            return emptyList()
        }

        val completed = mutableListOf<String>()
        val normalized = text.replace("\r\n", "\n").replace('\r', '\n')
        for (character in normalized) {
            if (!reliable) {
                if (character == '\n') reset()
                continue
            }
            when {
                character == '\n' -> finish()?.let(completed::add)
                character == '\b' || character == '\u007f' -> backspace()
                character.isISOControl() -> invalidate()
                else -> draft.append(character)
            }
        }
        return completed
    }

    /** Multiline pastes are deliberately excluded: they may be scripts or interactive input. */
    fun paste(text: String, enabled: Boolean = true): List<String> {
        if (!enabled || '\n' in text || '\r' in text) {
            invalidate()
            return emptyList()
        }
        return type(text)
    }

    fun enter(): String? {
        if (!reliable) {
            reset()
            return null
        }
        return finish()
    }

    fun backspace() {
        if (reliable && draft.isNotEmpty()) {
            val previousCodePoint = Character.offsetByCodePoints(draft, draft.length, -1)
            draft.delete(previousCodePoint, draft.length)
        }
    }

    /** Call when an untracked editing key or terminal mode makes the draft unreliable. */
    fun reset() {
        draft.setLength(0)
        reliable = true
    }

    fun invalidate() {
        draft.setLength(0)
        reliable = false
    }

    private fun finish(): String? {
        val command = draft.toString().trim()
        reset()
        return command.takeIf(String::isNotEmpty)
    }
}
