package dev.sshmobile.core.terminal

import java.nio.charset.StandardCharsets

enum class ArrowKey { UP, DOWN, RIGHT, LEFT }

/**
 * Mapping from user input to the byte sequences expected by a VT100/xterm PTY.
 * Kept dependency-free so it can be covered by unit tests.
 */
object TerminalInput {

    fun encodeText(text: String, ctrl: Boolean = false, alt: Boolean = false): ByteArray {
        if (text.isEmpty()) return ByteArray(0)
        val normalized = text.replace("\r\n", "\r").replace('\n', '\r')
        val output = ArrayList<Byte>(normalized.length + 2)
        if (alt) output.add(0x1B)
        for (ch in normalized) {
            if (ctrl) {
                val code = controlCode(ch)
                if (code != null) {
                    output.add(code)
                    continue
                }
            }
            val bytes = ch.toString().toByteArray(StandardCharsets.UTF_8)
            bytes.forEach { output.add(it) }
        }
        return output.toByteArray()
    }

    fun controlCode(ch: Char): Byte? {
        val upper = ch.uppercaseChar()
        return when {
            upper in 'A'..'Z' -> (upper.code - 'A'.code + 1).toByte()
            ch == '@' || ch == ' ' -> 0
            ch == '[' -> 27
            ch == '\\' -> 28
            ch == ']' -> 29
            ch == '^' -> 30
            ch == '_' -> 31
            ch == '?' -> 127
            else -> null
        }
    }

    fun ctrlC(): ByteArray = byteArrayOf(0x03)
    fun ctrlD(): ByteArray = byteArrayOf(0x04)
    fun ctrlZ(): ByteArray = byteArrayOf(0x1A)
    fun escape(): ByteArray = byteArrayOf(0x1B)
    fun tab(): ByteArray = byteArrayOf(0x09)
    fun backTab(): ByteArray = byteArrayOf(0x1B, '['.code.toByte(), 'Z'.code.toByte())
    fun enter(): ByteArray = byteArrayOf(0x0D)
    fun backspace(): ByteArray = byteArrayOf(0x7F)

    fun arrow(
        direction: ArrowKey,
        applicationCursorKeys: Boolean,
        ctrl: Boolean = false,
        alt: Boolean = false,
    ): ByteArray {
        val modifier = modifierCode(ctrl, alt, shift = false)
        if (modifier == 1) {
            return if (applicationCursorKeys) {
                byteArrayOf(0x1B, 'O'.code.toByte(), direction.letter().code.toByte())
            } else {
                byteArrayOf(0x1B, '['.code.toByte(), direction.letter().code.toByte())
            }
        }
        return csiWithModifier(direction.letter(), modifier)
    }

    fun home(applicationCursorKeys: Boolean, ctrl: Boolean = false, alt: Boolean = false): ByteArray {
        val modifier = modifierCode(ctrl, alt, shift = false)
        if (modifier == 1) {
            return if (applicationCursorKeys) {
                "\u001BOH".toByteArray(StandardCharsets.US_ASCII)
            } else {
                "\u001B[H".toByteArray(StandardCharsets.US_ASCII)
            }
        }
        return csiWithModifier('H', modifier)
    }

    fun end(applicationCursorKeys: Boolean, ctrl: Boolean = false, alt: Boolean = false): ByteArray {
        val modifier = modifierCode(ctrl, alt, shift = false)
        if (modifier == 1) {
            return if (applicationCursorKeys) {
                "\u001BOF".toByteArray(StandardCharsets.US_ASCII)
            } else {
                "\u001B[F".toByteArray(StandardCharsets.US_ASCII)
            }
        }
        return csiWithModifier('F', modifier)
    }

    fun insert(): ByteArray = "\u001B[2~".toByteArray(StandardCharsets.US_ASCII)
    fun delete(): ByteArray = "\u001B[3~".toByteArray(StandardCharsets.US_ASCII)
    fun pageUp(): ByteArray = "\u001B[5~".toByteArray(StandardCharsets.US_ASCII)
    fun pageDown(): ByteArray = "\u001B[6~".toByteArray(StandardCharsets.US_ASCII)

    fun function(number: Int): ByteArray {
        return when (number) {
            1 -> "\u001BOP"
            2 -> "\u001BOQ"
            3 -> "\u001BOR"
            4 -> "\u001BOS"
            5 -> "\u001B[15~"
            6 -> "\u001B[17~"
            7 -> "\u001B[18~"
            8 -> "\u001B[19~"
            9 -> "\u001B[20~"
            10 -> "\u001B[21~"
            11 -> "\u001B[23~"
            12 -> "\u001B[24~"
            else -> ""
        }.toByteArray(StandardCharsets.US_ASCII)
    }

    fun paste(text: String, bracketedPaste: Boolean): ByteArray {
        val payload = text.replace("\r\n", "\r")
        return if (bracketedPaste) {
            ("\u001B[200~" + payload + "\u001B[201~").toByteArray(StandardCharsets.UTF_8)
        } else {
            payload.toByteArray(StandardCharsets.UTF_8)
        }
    }

    private fun modifierCode(ctrl: Boolean, alt: Boolean, shift: Boolean): Int {
        var code = 1
        if (shift) code += 1
        if (alt) code += 2
        if (ctrl) code += 4
        return code
    }

    private fun csiWithModifier(letter: Char, modifier: Int): ByteArray =
        "\u001B[1;${modifier}${letter}".toByteArray(StandardCharsets.US_ASCII)

    private fun ArrowKey.letter(): Char = when (this) {
        ArrowKey.UP -> 'A'
        ArrowKey.DOWN -> 'B'
        ArrowKey.RIGHT -> 'C'
        ArrowKey.LEFT -> 'D'
    }
}
