package dev.sshmobile.core.terminal

import java.nio.charset.StandardCharsets
import java.util.ArrayDeque

/**
 * Immutable description of one rendered terminal cell.
 *
 * [fg] / [bg] are ARGB colors; 0 means "use the theme default". [width] is 0 for the
 * trailing half of a double-width character and 1 or 2 otherwise.
 */
data class RenderCell(
    val text: String = "",
    val fg: Int = 0,
    val bg: Int = 0,
    val attrs: Int = 0,
    val width: Int = 1,
)

data class TerminalSize(val cols: Int, val rows: Int)

/** Snapshot of the visible terminal grid. Safe to hand to the UI thread. */
data class TerminalSnapshot(
    val rows: List<List<RenderCell>>,
    val cols: Int,
    val screenRows: Int,
    val firstLine: Int,
    val totalLines: Int,
    val cursorRow: Int,
    val cursorCol: Int,
    val cursorVisible: Boolean,
    val scrollOffset: Int,
    val maxScrollOffset: Int,
    val altScreen: Boolean,
    val bracketedPaste: Boolean,
    val applicationCursorKeys: Boolean,
    val title: String,
    val version: Long,
) {
    companion object {
        fun empty(cols: Int = 80, rows: Int = 24): TerminalSnapshot = TerminalSnapshot(
            rows = List(rows) { List(cols) { RenderCell() } },
            cols = cols,
            screenRows = rows,
            firstLine = 0,
            totalLines = rows,
            cursorRow = 0,
            cursorCol = 0,
            cursorVisible = true,
            scrollOffset = 0,
            maxScrollOffset = 0,
            altScreen = false,
            bracketedPaste = false,
            applicationCursorKeys = false,
            title = "",
            version = 0,
        )
    }
}

object TerminalAttr {
    const val BOLD = 1
    const val DIM = 1 shl 1
    const val ITALIC = 1 shl 2
    const val UNDERLINE = 1 shl 3
    const val BLINK = 1 shl 4
    const val REVERSE = 1 shl 5
    const val HIDDEN = 1 shl 6
    const val STRIKE = 1 shl 7
}

/** xterm 256 colour palette. Values are ARGB and are chosen to be readable on a dark UI. */
object TerminalPalette {
    const val DEFAULT = 0

    private val base = intArrayOf(
        0xFF1C1C1C.toInt(), 0xFFCD3131.toInt(), 0xFF0DBC79.toInt(), 0xFFE5E510.toInt(),
        0xFF2472C8.toInt(), 0xFFBC3FBC.toInt(), 0xFF11A8CD.toInt(), 0xFFE5E5E5.toInt(),
        0xFF808080.toInt(), 0xFFF14C4C.toInt(), 0xFF23D18B.toInt(), 0xFFF5F543.toInt(),
        0xFF3B8EEA.toInt(), 0xFFD670D6.toInt(), 0xFF29B8DB.toInt(), 0xFFF5F5F5.toInt(),
    )

    private val cubeLevels = intArrayOf(0, 95, 135, 175, 215, 255)

    fun color(index: Int): Int {
        if (index < 0) return DEFAULT
        if (index < 16) return base[index]
        if (index < 232) {
            val i = index - 16
            val r = cubeLevels[i / 36]
            val g = cubeLevels[(i / 6) % 6]
            val b = cubeLevels[i % 6]
            return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        if (index < 256) {
            val v = 8 + (index - 232) * 10
            return (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        return DEFAULT
    }

    fun brighten(color: Int): Int {
        // Used for bold text when the base 8 colours are requested.
        val r = (color shr 16 and 0xFF)
        val g = (color shr 8 and 0xFF)
        val b = (color and 0xFF)
        val nr = (r + (255 - r) * 0.35).toInt()
        val ng = (g + (255 - g) * 0.35).toInt()
        val nb = (b + (255 - b) * 0.35).toInt()
        return (0xFF shl 24) or (nr shl 16) or (ng shl 8) or nb
    }
}

/**
 * A VT100/xterm compatible terminal emulator, written from scratch for this app.
 *
 * The emulator is intentionally independent from Android so it can be unit-tested on the JVM.
 * It supports the sequences needed by bash, zsh, vim, nano, htop, less and other curses
 * applications: cursor movement, erase, insert/delete, scroll regions, alternate screen,
 * SGR colours (16/256/truecolor), UTF-8, wide characters, bracketed paste and common
 * terminal queries.
 *
 * Thread-safety: [feed] and [snapshot] are synchronized; feed is expected to be called from a
 * single output coroutine.
 */
class TerminalEmulator(
    cols: Int = 80,
    rows: Int = 24,
    val maxScrollback: Int = 5000,
) {
    private class Line(cols: Int) {
        var text: Array<String?> = arrayOfNulls(cols)
        var fg: IntArray = IntArray(cols)
        var bg: IntArray = IntArray(cols)
        var attrs: IntArray = IntArray(cols)
        var width: ByteArray = ByteArray(cols) { 1 }

        fun resize(newCols: Int) {
            if (newCols == text.size) return
            val newText = arrayOfNulls<String>(newCols)
            val newFg = IntArray(newCols)
            val newBg = IntArray(newCols)
            val newAttrs = IntArray(newCols)
            val newWidth = ByteArray(newCols) { 1 }
            val n = minOf(newCols, text.size)
            for (i in 0 until n) {
                newText[i] = text[i]
                newFg[i] = fg[i]
                newBg[i] = bg[i]
                newAttrs[i] = attrs[i]
                newWidth[i] = width[i]
            }
            if (newCols > 0 && newWidth[newCols - 1] == 0.toByte()) {
                // The trailing half of a wide char was cut off.
                newText[newCols - 1] = null
                newWidth[newCols - 1] = 1
            }
            text = newText
            fg = newFg
            bg = newBg
            attrs = newAttrs
            width = newWidth
        }

        fun clear(from: Int, to: Int, bgColor: Int = 0, attr: Int = 0) {
            val start = from.coerceIn(0, text.size)
            val end = to.coerceIn(start, text.size)
            for (i in start until end) {
                text[i] = null
                fg[i] = 0
                bg[i] = bgColor
                attrs[i] = attr
                width[i] = 1
            }
        }

        fun clearAll() = clear(0, text.size)
    }

    private enum class State { GROUND, ESCAPE, CHARSET, HASH, CSI, OSC, OSC_ESC, STRING, STRING_ESC }

    private val lock = Any()

    private var cols: Int = cols.coerceAtLeast(2)
    private var rows: Int = rows.coerceAtLeast(1)

    private var primary: Array<Line> = Array(this.rows) { Line(this.cols) }
    private var alternate: Array<Line> = Array(this.rows) { Line(this.cols) }
    private val scrollback = ArrayDeque<Line>()
    private var usingAlt = false

    private var cursorRow = 0
    private var cursorCol = 0
    private var scrollTop = 0
    private var scrollBottom = this.rows - 1
    private var scrollOffset = 0
    private var cursorVisible = true
    private var autoWrap = true
    private var originMode = false
    private var insertMode = false
    private var newLineMode = false
    private var applicationCursorKeys = false
    private var bracketedPaste = false
    private var reverseVideo = false
    private var pendingWrap = false
    private var cursorStyle = 0

    private var fg = 0
    private var bg = 0
    private var attrs = 0

    private var charsetG0 = 0 // 0 = ASCII, 1 = DEC special graphics, 2 = UK
    private var charsetG1 = 0
    private var shiftOut = false

    private var tabStops = BooleanArray(this.cols)

    private var savedCursor = SavedCursor()
    private var savedPrimaryCursor = SavedCursor()
    private var title = ""
    private var version = 0L

    private var state = State.GROUND
    private var utf8Value = 0
    private var utf8Remaining = 0
    private var utf8Min = 0
    private var escIntermediate = -1
    private var osc = StringBuilder()

    private val params = IntArray(32) { -1 }
    private var paramCount = 0
    private var currentParam = -1
    private var hasCurrentParam = false
    private var csiPrefix = 0
    private var csiIntermediate = -1

    /** Called when the terminal must answer a query (DA, DSR). Wired to the SSH stdin. */
    var onResponse: ((ByteArray) -> Unit)? = null

    init {
        resetTabStops()
        ensurePrimaryCursorSaved()
    }

    private data class SavedCursor(
        var row: Int = 0,
        var col: Int = 0,
        var fg: Int = 0,
        var bg: Int = 0,
        var attrs: Int = 0,
        var originMode: Boolean = false,
        var g0: Int = 0,
        var g1: Int = 0,
        var shiftOut: Boolean = false,
    )

    val size: TerminalSize
        get() = synchronized(lock) { TerminalSize(cols, rows) }

    val osTitle: String
        get() = synchronized(lock) { title }

    fun isBracketedPasteEnabled(): Boolean = synchronized(lock) { bracketedPaste }

    // ------------------------------------------------------------------ feeding

    fun feed(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size) {
        synchronized(lock) {
            val end = (offset + length).coerceAtMost(bytes.size)
            var i = offset
            while (i < end) {
                val b = bytes[i].toInt() and 0xFF
                processByte(b)
                i++
            }
        }
    }

    fun feedText(text: String) = feed(text.toByteArray(StandardCharsets.UTF_8))

    private fun processByte(b: Int) {
        if (utf8Remaining > 0) {
            if (b and 0xC0 == 0x80) {
                utf8Value = (utf8Value shl 6) or (b and 0x3F)
                utf8Remaining--
                if (utf8Remaining == 0) {
                    val cp = utf8Value
                    if (cp < utf8Min || cp > 0x10FFFF || cp in 0xD800..0xDFFF) {
                        printCodePoint(0xFFFD)
                    } else {
                        printCodePoint(cp)
                    }
                }
                return
            }
            // Malformed sequence: emit replacement and reprocess this byte as ASCII/control.
            utf8Remaining = 0
            utf8Value = 0
            printCodePoint(0xFFFD)
        }
        if (b >= 0x80) {
            startUtf8(b)
            return
        }
        processAscii(b)
    }

    private fun startUtf8(b: Int) {
        when {
            b and 0xE0 == 0xC0 -> { utf8Value = b and 0x1F; utf8Remaining = 1; utf8Min = 0x80 }
            b and 0xF0 == 0xE0 -> { utf8Value = b and 0x0F; utf8Remaining = 2; utf8Min = 0x800 }
            b and 0xF8 == 0xF0 -> { utf8Value = b and 0x07; utf8Remaining = 3; utf8Min = 0x10000 }
            else -> printCodePoint(0xFFFD)
        }
    }

    private fun processAscii(b: Int) {
        when (state) {
            State.OSC -> {
                when (b) {
                    0x07 -> { finishOsc(); state = State.GROUND }
                    0x1B -> state = State.OSC_ESC
                    else -> if (osc.length < 4096) osc.append(b.toChar())
                }
                return
            }
            State.OSC_ESC -> {
                state = if (b == '\\'.code) { finishOsc(); State.GROUND } else State.OSC
                return
            }
            State.STRING -> {
                if (b == 0x1B) state = State.STRING_ESC
                return
            }
            State.STRING_ESC -> {
                state = if (b == '\\'.code) State.GROUND else State.STRING
                return
            }
            State.ESCAPE -> { handleEscape(b); return }
            State.CHARSET -> { applyCharset(b); state = State.GROUND; return }
            State.HASH -> {
                if (b == '8'.code) decaln()
                state = State.GROUND
                return
            }
            State.CSI -> { handleCsiByte(b); return }
            State.GROUND -> Unit
        }

        if (b < 0x20 || b == 0x7F) {
            executeControl(b)
            return
        }
        if (b in 0x20..0x7E) {
            printCodePoint(b)
        }
    }

    // ---------------------------------------------------------------- controls

    private fun executeControl(b: Int) {
        when (b) {
            0x00, 0x7F -> Unit
            0x07 -> Unit // bell handled by the UI layer if desired
            0x08 -> { if (cursorCol > 0) cursorCol--; pendingWrap = false }
            0x09 -> tabForward()
            0x0A, 0x0B, 0x0C -> { lineFeed(); if (newLineMode) cursorCol = 0 }
            0x0D -> { cursorCol = 0; pendingWrap = false }
            0x0E -> shiftOut = true
            0x0F -> shiftOut = false
            0x18, 0x1A -> Unit
            0x1B -> { state = State.ESCAPE; escIntermediate = -1 }
        }
    }

    private fun handleEscape(b: Int) {
        state = State.GROUND
        when (b) {
            '['.code -> { resetCsi(); state = State.CSI }
            ']'.code -> { osc = StringBuilder(); state = State.OSC }
            'P'.code, 'X'.code, '^'.code, '_'.code -> state = State.STRING
            '7'.code -> saveCursor()
            '8'.code -> restoreCursor()
            'D'.code -> index()
            'E'.code -> { cursorCol = 0; index() }
            'M'.code -> reverseIndex()
            'H'.code -> setTabStop(cursorCol)
            'c'.code -> reset()
            '('.code, ')'.code, '*'.code, '+'.code -> { escIntermediate = b; state = State.CHARSET }
            '#'.code -> state = State.HASH
            '\\'.code, '='.code, '>'.code -> Unit
            else -> Unit
        }
    }

    private fun applyCharset(b: Int) {
        val set = when (b) {
            '0'.code -> 1
            'A'.code -> 2
            else -> 0
        }
        when (escIntermediate) {
            '('.code, '*'.code -> charsetG0 = set
            ')'.code, '+'.code -> charsetG1 = set
        }
        escIntermediate = -1
    }

    // --------------------------------------------------------------------- CSI

    private fun resetCsi() {
        for (i in params.indices) params[i] = -1
        paramCount = 0
        currentParam = -1
        hasCurrentParam = false
        csiPrefix = 0
        csiIntermediate = -1
    }

    private fun handleCsiByte(b: Int) {
        when {
            b in '0'.code..'9'.code -> {
                val digit = b - '0'.code
                currentParam = if (currentParam < 0) digit else (currentParam * 10 + digit).coerceAtMost(999999)
                hasCurrentParam = true
            }
            b == ';'.code || b == ':'.code -> {
                pushParam()
            }
            b == '?'.code || b == '>'.code || b == '<'.code || b == '='.code -> {
                if (paramCount == 0 && !hasCurrentParam) csiPrefix = b
            }
            b in 0x20..0x2F -> csiIntermediate = b
            b in 0x40..0x7E -> {
                pushParam()
                dispatchCsi(b)
                state = State.GROUND
            }
            b == 0x18 || b == 0x1A -> { state = State.GROUND }
            b == 0x1B -> { state = State.ESCAPE; escIntermediate = -1 }
        }
    }

    private fun pushParam() {
        if (paramCount < params.size) {
            params[paramCount++] = if (hasCurrentParam) currentParam else -1
        }
        currentParam = -1
        hasCurrentParam = false
    }

    private fun param(index: Int, default: Int = 1): Int {
        if (index >= paramCount) return default
        val v = params[index]
        return if (v < 0) default else v
    }

    private fun paramOrZero(index: Int): Int {
        if (index >= paramCount) return 0
        val v = params[index]
        return if (v < 0) 0 else v
    }

    private fun dispatchCsi(final: Int) {
        if (csiPrefix == '?'.code) {
            handlePrivateMode(final)
            return
        }
        if (csiPrefix == '>'.code) {
            if (final == 'c'.code) respond("\u001B[>0;136;0c")
            return
        }
        if (csiIntermediate == '!'.code && final == 'p'.code) {
            softReset()
            return
        }
        if (csiIntermediate == ' '.code && final == 'q'.code) {
            cursorStyle = paramOrZero(0)
            return
        }
        when (final) {
            '@'.code -> insertChars(param(0))
            'A'.code -> moveCursor(0, -param(0))
            'B'.code, 'e'.code -> moveCursor(0, param(0))
            'C'.code, 'a'.code -> moveCursor(param(0), 0)
            'D'.code -> moveCursor(-param(0), 0)
            'E'.code -> moveCursor(0, param(0)).also { cursorCol = 0 }
            'F'.code -> moveCursor(0, -param(0)).also { cursorCol = 0 }
            'G'.code, '`'.code -> setCursorAbsoluteRow(cursorRow, param(0))
            'H'.code, 'f'.code -> setCursorAbsolute(param(0), param(1))
            'I'.code -> repeat(param(0)) { tabForward() }
            'J'.code -> eraseInDisplay(paramOrZero(0))
            'K'.code -> eraseInLine(paramOrZero(0))
            'L'.code -> insertLines(param(0))
            'M'.code -> deleteLines(param(0))
            'P'.code -> deleteChars(param(0))
            'S'.code -> scrollUp(param(0))
            'T'.code -> scrollDown(param(0))
            'X'.code -> eraseChars(param(0))
            'Z'.code -> repeat(param(0)) { tabBackward() }
            'b'.code -> repeat(param(0).coerceAtMost(512)) { printCodePoint(lastPrintedCodePoint) }
            'c'.code -> if (csiPrefix == 0) respond("\u001B[?1;2c")
            'd'.code -> setCursorAbsoluteRow(param(0) - 1, cursorCol + 1)
            'g'.code -> clearTabStops(paramOrZero(0))
            'h'.code -> setMode(final, true)
            'l'.code -> setMode(final, false)
            'm'.code -> applySgr()
            'n'.code -> deviceStatus(paramOrZero(0))
            'r'.code -> setScrollRegion(param(0), param(1, rows))
            's'.code -> saveCursor()
            'u'.code -> restoreCursor()
            't'.code -> Unit
            'q'.code -> if (csiIntermediate == ' '.code) cursorStyle = paramOrZero(0)
            '!'.code -> softReset()
        }
    }

    private fun handlePrivateMode(final: Int) {
        val enabled = final == 'h'.code
        if (final != 'h'.code && final != 'l'.code) return
        var i = 0
        while (i < paramCount) {
            when (params[i]) {
                1 -> applicationCursorKeys = enabled
                6 -> { originMode = enabled; cursorRow = if (enabled) scrollTop else 0; cursorCol = 0 }
                7 -> autoWrap = enabled
                12 -> Unit
                25 -> cursorVisible = enabled
                47, 1047, 1049 -> {
                    if (enabled) enterAltScreen() else exitAltScreen()
                }
                1000, 1002, 1003, 1006 -> Unit // mouse reporting is intentionally not implemented yet
                2004 -> bracketedPaste = enabled
                2026 -> Unit // synchronized output
            }
            i++
        }
    }

    private fun setMode(final: Int, enabled: Boolean) {
        if (final != 'h'.code && final != 'l'.code) return
        var i = 0
        while (i < paramCount) {
            when (params[i]) {
                4 -> insertMode = enabled
                20 -> newLineMode = enabled
            }
            i++
        }
    }

    // -------------------------------------------------------------------- SGR

    private fun applySgr() {
        if (paramCount == 0) {
            resetAttributes()
            return
        }
        var i = 0
        while (i < paramCount) {
            val p = if (params[i] < 0) 0 else params[i]
            when (p) {
                0 -> resetAttributes()
                1 -> attrs = attrs or TerminalAttr.BOLD
                2 -> attrs = attrs or TerminalAttr.DIM
                3 -> attrs = attrs or TerminalAttr.ITALIC
                4, 21 -> attrs = attrs or TerminalAttr.UNDERLINE
                5, 6 -> attrs = attrs or TerminalAttr.BLINK
                7 -> attrs = attrs or TerminalAttr.REVERSE
                8 -> attrs = attrs or TerminalAttr.HIDDEN
                9 -> attrs = attrs or TerminalAttr.STRIKE
                22 -> attrs = attrs and (TerminalAttr.BOLD or TerminalAttr.DIM).inv()
                23 -> attrs = attrs and TerminalAttr.ITALIC.inv()
                24 -> attrs = attrs and TerminalAttr.UNDERLINE.inv()
                25 -> attrs = attrs and TerminalAttr.BLINK.inv()
                27 -> attrs = attrs and TerminalAttr.REVERSE.inv()
                28 -> attrs = attrs and TerminalAttr.HIDDEN.inv()
                29 -> attrs = attrs and TerminalAttr.STRIKE.inv()
                in 30..37 -> fg = TerminalPalette.color(p - 30)
                38 -> {
                    i += parseExtendedColor(i) { color -> fg = color }
                    continue
                }
                39 -> fg = 0
                in 40..47 -> bg = TerminalPalette.color(p - 40)
                48 -> {
                    i += parseExtendedColor(i) { color -> bg = color }
                    continue
                }
                49 -> bg = 0
                in 90..97 -> fg = TerminalPalette.color(p - 90 + 8)
                in 100..107 -> bg = TerminalPalette.color(p - 100 + 8)
            }
            i++
        }
    }

    /** Returns the number of parameters consumed, including 38/48 itself. */
    private fun parseExtendedColor(index: Int, assign: (Int) -> Unit): Int {
        if (index + 1 >= paramCount) return 1
        return when (params[index + 1]) {
            5 -> {
                if (index + 2 >= paramCount) return 1
                assign(TerminalPalette.color(params[index + 2].coerceIn(0, 255)))
                3
            }
            2 -> {
                var j = index + 2
                // Skip an optional colour-space id (colon form: 38:2::r:g:b).
                if (j < paramCount && params[j] < 0) j++
                if (j + 2 >= paramCount) return paramCount - index
                val r = params[j].coerceIn(0, 255)
                val g = params[j + 1].coerceIn(0, 255)
                val b = params[j + 2].coerceIn(0, 255)
                assign((0xFF shl 24) or (r shl 16) or (g shl 8) or b)
                j + 3 - index
            }
            else -> 2
        }
    }

    private fun resetAttributes() {
        fg = 0
        bg = 0
        attrs = 0
        reverseVideo = false
    }

    // ------------------------------------------------------------------ cursor

    private fun moveCursor(dx: Int, dy: Int) {
        if (dx != 0) {
            cursorCol = (cursorCol + dx).coerceIn(0, cols - 1)
        }
        if (dy != 0) {
            val inRegion = cursorRow in scrollTop..scrollBottom
            val minRow = if (inRegion) scrollTop else 0
            val maxRow = if (inRegion) scrollBottom else rows - 1
            cursorRow = (cursorRow + dy).coerceIn(minRow, maxRow)
        }
        pendingWrap = false
    }

    private fun setCursorAbsolute(row1: Int, col1: Int) {
        val row = if (originMode) scrollTop + (row1 - 1) else row1 - 1
        val minRow = if (originMode) scrollTop else 0
        val maxRow = if (originMode) scrollBottom else rows - 1
        cursorRow = row.coerceIn(minRow, maxRow)
        cursorCol = (col1 - 1).coerceIn(0, cols - 1)
        pendingWrap = false
    }

    private fun setCursorAbsoluteRow(row0: Int, col1: Int) {
        val row = if (originMode) scrollTop + row0 else row0
        val minRow = if (originMode) scrollTop else 0
        val maxRow = if (originMode) scrollBottom else rows - 1
        cursorRow = row.coerceIn(minRow, maxRow)
        cursorCol = (col1 - 1).coerceIn(0, cols - 1)
        pendingWrap = false
    }

    private fun saveCursor() {
        savedCursor = SavedCursor(
            row = cursorRow,
            col = cursorCol,
            fg = fg,
            bg = bg,
            attrs = attrs,
            originMode = originMode,
            g0 = charsetG0,
            g1 = charsetG1,
            shiftOut = shiftOut,
        )
    }

    private fun restoreCursor() {
        val s = savedCursor
        cursorRow = s.row.coerceIn(0, rows - 1)
        cursorCol = s.col.coerceIn(0, cols - 1)
        fg = s.fg
        bg = s.bg
        attrs = s.attrs
        originMode = s.originMode
        charsetG0 = s.g0
        charsetG1 = s.g1
        shiftOut = s.shiftOut
        pendingWrap = false
    }

    private fun ensurePrimaryCursorSaved() {
        savedPrimaryCursor = SavedCursor()
    }

    // ---------------------------------------------------------------- printing

    private var lastPrintedCodePoint = ' '.code

    private fun printCodePoint(codePoint: Int) {
        var cp = codePoint
        if (cp == 0) return
        if (shiftOut) cp = mapCharset(cp, charsetG1) else cp = mapCharset(cp, charsetG0)
        val width = TextWidth.of(cp)
        if (width == 0) {
            appendCombining(cp)
            return
        }
        if (pendingWrap) {
            if (autoWrap) {
                cursorCol = 0
                lineFeed()
            }
            pendingWrap = false
        }
        if (width == 2 && cursorCol == cols - 1) {
            if (autoWrap) {
                cursorCol = 0
                lineFeed()
            } else {
                return
            }
        }
        val line = screen()[cursorRow]
        if (insertMode) shiftRight(line, cursorCol, width)
        clearWideAt(line, cursorCol)
        if (width == 2) clearWideAt(line, cursorCol + 1)

        line.text[cursorCol] = String(Character.toChars(cp))
        line.fg[cursorCol] = fg
        line.bg[cursorCol] = bg
        line.attrs[cursorCol] = attrs
        line.width[cursorCol] = width.toByte()
        if (width == 2) {
            line.text[cursorCol + 1] = ""
            line.fg[cursorCol + 1] = fg
            line.bg[cursorCol + 1] = bg
            line.attrs[cursorCol + 1] = attrs
            line.width[cursorCol + 1] = 0
        }
        lastPrintedCodePoint = cp
        cursorCol += width
        if (cursorCol >= cols) {
            cursorCol = cols - 1
            pendingWrap = true
        }
        version++
    }

    private fun appendCombining(cp: Int) {
        val line = screen()[cursorRow]
        var idx = cursorCol
        if (pendingWrap) idx = cols - 1
        if (idx > 0 && line.width[idx] == 0.toByte()) idx--
        if (idx > 0 && line.text[idx] == null) idx--
        if (idx >= 0 && line.text[idx] != null) {
            line.text[idx] = line.text[idx] + String(Character.toChars(cp))
        }
        version++
    }

    private fun clearWideAt(line: Line, index: Int) {
        if (index !in 0 until cols) return
        if (line.width[index] == 0.toByte()) {
            if (index > 0) {
                line.text[index - 1] = null
                line.width[index - 1] = 1
            }
        } else if (line.width[index] == 2.toByte()) {
            if (index + 1 < cols) {
                line.text[index + 1] = null
                line.width[index + 1] = 1
            }
        }
        line.text[index] = null
        line.fg[index] = 0
        line.bg[index] = 0
        line.attrs[index] = 0
        line.width[index] = 1
    }

    private fun shiftRight(line: Line, from: Int, count: Int) {
        if (count <= 0 || from >= cols) return
        val n = count.coerceAtMost(cols - from)
        var i = cols - 1
        while (i >= from + n) {
            line.text[i] = line.text[i - n]
            line.fg[i] = line.fg[i - n]
            line.bg[i] = line.bg[i - n]
            line.attrs[i] = line.attrs[i - n]
            line.width[i] = line.width[i - n]
            i--
        }
        line.clear(from, from + n)
        sanitizeWide(line, from, from + n)
    }

    private fun shiftLeft(line: Line, from: Int, count: Int) {
        if (count <= 0 || from >= cols) return
        val n = count.coerceAtMost(cols - from)
        var i = from
        while (i + n < cols) {
            line.text[i] = line.text[i + n]
            line.fg[i] = line.fg[i + n]
            line.bg[i] = line.bg[i + n]
            line.attrs[i] = line.attrs[i + n]
            line.width[i] = line.width[i + n]
            i++
        }
        line.clear(cols - n, cols)
        sanitizeWide(line, from.coerceAtLeast(0), cols)
    }

    private fun sanitizeWide(line: Line, from: Int, to: Int) {
        val start = from.coerceIn(0, cols - 1)
        val end = to.coerceIn(start + 1, cols)
        for (i in start until end) {
            if (line.width[i] == 0.toByte()) {
                if (i == 0 || line.width[i - 1] != 2.toByte()) {
                    line.text[i] = null
                    line.width[i] = 1
                }
            } else if (line.width[i] == 2.toByte()) {
                if (i + 1 >= cols || line.width[i + 1] != 0.toByte()) {
                    line.width[i] = 1
                }
            }
        }
    }

    // ------------------------------------------------------------------ scroll

    private fun screen(): Array<Line> = if (usingAlt) alternate else primary

    private fun scrollUp(count: Int) {
        val n = count.coerceAtLeast(0).coerceAtMost(scrollBottom - scrollTop + 1)
        if (n == 0) return
        val scr = screen()
        repeat(n) {
            if (!usingAlt) pushScrollback(scr[scrollTop])
            for (i in scrollTop until scrollBottom) scr[i] = scr[i + 1]
            scr[scrollBottom] = Line(cols)
        }
        pendingWrap = false
        version++
    }

    private fun scrollDown(count: Int) {
        val n = count.coerceAtLeast(0).coerceAtMost(scrollBottom - scrollTop + 1)
        if (n == 0) return
        val scr = screen()
        repeat(n) {
            for (i in scrollBottom downTo scrollTop + 1) scr[i] = scr[i - 1]
            scr[scrollTop] = Line(cols)
        }
        pendingWrap = false
        version++
    }

    private fun pushScrollback(line: Line) {
        if (maxScrollback <= 0) return
        scrollback.addLast(line)
        while (scrollback.size > maxScrollback) scrollback.removeFirst()
        if (scrollOffset > scrollback.size) scrollOffset = scrollback.size
    }

    private fun lineFeed() {
        pendingWrap = false
        if (cursorRow == scrollBottom) {
            scrollUp(1)
        } else if (cursorRow < rows - 1) {
            cursorRow++
        }
    }

    private fun index() {
        if (cursorRow == scrollBottom) scrollUp(1) else if (cursorRow < rows - 1) cursorRow++
    }

    private fun reverseIndex() {
        if (cursorRow == scrollTop) scrollDown(1) else if (cursorRow > 0) cursorRow--
        pendingWrap = false
    }

    private fun enterAltScreen() {
        if (usingAlt) return
        savedPrimaryCursor = SavedCursor(cursorRow, cursorCol, fg, bg, attrs, originMode, charsetG0, charsetG1, shiftOut)
        usingAlt = true
        alternate = Array(rows) { Line(cols) }
        cursorRow = 0
        cursorCol = 0
        scrollOffset = 0
        pendingWrap = false
        version++
    }

    private fun exitAltScreen() {
        if (!usingAlt) return
        usingAlt = false
        cursorRow = savedPrimaryCursor.row.coerceIn(0, rows - 1)
        cursorCol = savedPrimaryCursor.col.coerceIn(0, cols - 1)
        fg = savedPrimaryCursor.fg
        bg = savedPrimaryCursor.bg
        attrs = savedPrimaryCursor.attrs
        originMode = savedPrimaryCursor.originMode
        charsetG0 = savedPrimaryCursor.g0
        charsetG1 = savedPrimaryCursor.g1
        shiftOut = savedPrimaryCursor.shiftOut
        pendingWrap = false
        version++
    }

    // --------------------------------------------------------------- erase etc.

    private fun eraseInLine(mode: Int) {
        val line = screen()[cursorRow]
        when (mode) {
            0 -> line.clear(cursorCol, cols, bg, attrs)
            1 -> line.clear(0, cursorCol + 1, bg, attrs)
            2 -> line.clear(0, cols, bg, attrs)
        }
        pendingWrap = false
        version++
    }

    private fun eraseInDisplay(mode: Int) {
        val scr = screen()
        when (mode) {
            0 -> {
                scr[cursorRow].clear(cursorCol, cols, bg, attrs)
                for (r in cursorRow + 1 until rows) scr[r] = Line(cols)
            }
            1 -> {
                for (r in 0 until cursorRow) scr[r] = Line(cols)
                scr[cursorRow].clear(0, cursorCol + 1, bg, attrs)
            }
            2 -> {
                for (r in 0 until rows) scr[r] = Line(cols)
            }
            3 -> {
                scrollback.clear()
                scrollOffset = 0
            }
        }
        pendingWrap = false
        version++
    }

    private fun eraseChars(count: Int) {
        val line = screen()[cursorRow]
        line.clear(cursorCol, (cursorCol + count).coerceAtMost(cols), bg, attrs)
        version++
    }

    private fun insertChars(count: Int) {
        shiftRight(screen()[cursorRow], cursorCol, count)
        version++
    }

    private fun deleteChars(count: Int) {
        shiftLeft(screen()[cursorRow], cursorCol, count)
        version++
    }

    private fun insertLines(count: Int) {
        if (cursorRow !in scrollTop..scrollBottom) return
        val scr = screen()
        val n = count.coerceAtMost(scrollBottom - cursorRow + 1)
        var i = scrollBottom
        while (i >= cursorRow + n) {
            scr[i] = scr[i - n]
            i--
        }
        for (r in cursorRow until cursorRow + n) scr[r] = Line(cols)
        version++
    }

    private fun deleteLines(count: Int) {
        if (cursorRow !in scrollTop..scrollBottom) return
        val scr = screen()
        val n = count.coerceAtMost(scrollBottom - cursorRow + 1)
        var i = cursorRow
        while (i + n <= scrollBottom) {
            scr[i] = scr[i + n]
            i++
        }
        for (r in scrollBottom - n + 1..scrollBottom) {
            if (r >= 0) scr[r] = Line(cols)
        }
        version++
    }

    private fun setScrollRegion(top1: Int, bottom1: Int) {
        val top = (top1 - 1).coerceIn(0, rows - 1)
        val bottom = (bottom1 - 1).coerceIn(0, rows - 1)
        if (top < bottom) {
            scrollTop = top
            scrollBottom = bottom
            cursorRow = if (originMode) top else 0
            cursorCol = 0
            pendingWrap = false
            version++
        }
    }

    private fun decaln() {
        val scr = screen()
        for (r in 0 until rows) {
            scr[r] = Line(cols)
            for (c in 0 until cols) {
                scr[r].text[c] = "E"
                scr[r].width[c] = 1
            }
        }
        cursorRow = 0
        cursorCol = 0
        scrollTop = 0
        scrollBottom = rows - 1
        pendingWrap = false
        version++
    }

    // ------------------------------------------------------------------- tabs

    private fun resetTabStops() {
        tabStops = BooleanArray(cols)
        var i = 8
        while (i < cols) {
            tabStops[i] = true
            i += 8
        }
    }

    private fun setTabStop(col: Int) {
        if (col in tabStops.indices) tabStops[col] = true
    }

    private fun clearTabStops(mode: Int) {
        when (mode) {
            0 -> if (cursorCol in tabStops.indices) tabStops[cursorCol] = false
            3 -> tabStops.fill(false)
        }
    }

    private fun tabForward() {
        var c = cursorCol + 1
        while (c < cols - 1 && !tabStops[c]) c++
        cursorCol = c.coerceAtMost(cols - 1)
        pendingWrap = false
    }

    private fun tabBackward() {
        var c = cursorCol - 1
        while (c > 0 && !tabStops[c]) c--
        cursorCol = c.coerceAtLeast(0)
        pendingWrap = false
    }

    // --------------------------------------------------------------- responses

    private fun deviceStatus(mode: Int) {
        when (mode) {
            5 -> respond("\u001B[0n")
            6 -> {
                val r = if (originMode) cursorRow - scrollTop + 1 else cursorRow + 1
                respond("\u001B[${r};${cursorCol + 1}R")
            }
        }
    }

    private fun respond(text: String) {
        val callback = onResponse ?: return
        callback(text.toByteArray(StandardCharsets.UTF_8))
    }

    // ------------------------------------------------------------------- OSC

    private fun finishOsc() {
        val value = osc.toString()
        if (value.startsWith("0;") || value.startsWith("2;")) {
            val t = value.substring(2).replace(Regex("[\\x00-\\x1F\\x7F]"), "").take(256)
            title = t
        }
        // OSC 52 (clipboard), OSC 8 (hyperlinks), colour queries etc. are intentionally ignored.
        osc = StringBuilder()
    }

    // ---------------------------------------------------------------- reset

    fun reset() {
        cols = cols.coerceAtLeast(2)
        rows = rows.coerceAtLeast(1)
        primary = Array(rows) { Line(cols) }
        alternate = Array(rows) { Line(cols) }
        scrollback.clear()
        usingAlt = false
        cursorRow = 0
        cursorCol = 0
        scrollTop = 0
        scrollBottom = rows - 1
        scrollOffset = 0
        cursorVisible = true
        autoWrap = true
        originMode = false
        insertMode = false
        newLineMode = false
        applicationCursorKeys = false
        bracketedPaste = false
        reverseVideo = false
        pendingWrap = false
        cursorStyle = 0
        fg = 0
        bg = 0
        attrs = 0
        charsetG0 = 0
        charsetG1 = 0
        shiftOut = false
        title = ""
        resetTabStops()
        savedCursor = SavedCursor()
        savedPrimaryCursor = SavedCursor()
        state = State.GROUND
        utf8Remaining = 0
        utf8Value = 0
        version++
    }

    private fun softReset() {
        cursorVisible = true
        autoWrap = true
        originMode = false
        insertMode = false
        applicationCursorKeys = false
        newLineMode = false
        bracketedPaste = false
        cursorStyle = 0
        resetAttributes()
        scrollTop = 0
        scrollBottom = rows - 1
        pendingWrap = false
        charsetG0 = 0
        charsetG1 = 0
        shiftOut = false
        version++
    }

    fun resize(newCols: Int, newRows: Int) {
        synchronized(lock) {
            val c = newCols.coerceIn(2, 500)
            val r = newRows.coerceIn(1, 300)
            if (c == cols && r == rows) return
            primary.forEach { it.resize(c) }
            alternate.forEach { it.resize(c) }
            scrollback.forEach { it.resize(c) }
            primary = resizeArray(primary, r, c, keepBottom = false)
            alternate = resizeArray(alternate, r, c, keepBottom = false)
            cols = c
            rows = r
            scrollTop = 0
            scrollBottom = rows - 1
            cursorRow = cursorRow.coerceIn(0, rows - 1)
            cursorCol = cursorCol.coerceIn(0, cols - 1)
            scrollOffset = scrollOffset.coerceIn(0, maxScrollOffsetInternal())
            pendingWrap = false
            resetTabStops()
            version++
        }
    }

    private fun resizeArray(old: Array<Line>, newRows: Int, newCols: Int, keepBottom: Boolean): Array<Line> {
        val result = Array(newRows) { Line(newCols) }
        val copy = minOf(newRows, old.size)
        for (i in 0 until copy) {
            val src = old[i]
            val dst = result[i]
            val n = minOf(newCols, src.text.size)
            System.arraycopy(src.text, 0, dst.text, 0, n)
            System.arraycopy(src.fg, 0, dst.fg, 0, n)
            System.arraycopy(src.bg, 0, dst.bg, 0, n)
            System.arraycopy(src.attrs, 0, dst.attrs, 0, n)
            System.arraycopy(src.width, 0, dst.width, 0, n)
        }
        return result
    }

    // ------------------------------------------------------------- scrolling

    fun scrollBy(lines: Int) {
        synchronized(lock) {
            val max = maxScrollOffsetInternal()
            scrollOffset = (scrollOffset + lines).coerceIn(0, max)
            version++
        }
    }

    fun scrollToBottom() {
        synchronized(lock) {
            if (scrollOffset != 0) {
                scrollOffset = 0
                version++
            }
        }
    }

    fun scrollToTop() {
        synchronized(lock) {
            scrollOffset = maxScrollOffsetInternal()
            version++
        }
    }

    fun setScrollOffset(offset: Int) {
        synchronized(lock) {
            scrollOffset = offset.coerceIn(0, maxScrollOffsetInternal())
            version++
        }
    }

    fun scrollOffset(): Int = synchronized(lock) { scrollOffset }

    fun maxScrollOffset(): Int = synchronized(lock) { maxScrollOffsetInternal() }

    private fun maxScrollOffsetInternal(): Int = if (usingAlt) 0 else scrollback.size

    fun isAlternateScreen(): Boolean = synchronized(lock) { usingAlt }

    fun usesApplicationCursorKeys(): Boolean = synchronized(lock) { applicationCursorKeys }

    fun cursorStyle(): Int = synchronized(lock) { cursorStyle }

    // --------------------------------------------------------------- snapshots

    private fun totalLinesInternal(): Int = if (usingAlt) rows else scrollback.size + rows

    private fun lineAt(absolute: Int): Line {
        if (usingAlt) return alternate[absolute.coerceIn(0, rows - 1)]
        return if (absolute < scrollback.size) {
            scrollback.elementAt(absolute)
        } else {
            primary[(absolute - scrollback.size).coerceIn(0, rows - 1)]
        }
    }

    private fun renderLine(line: Line): List<RenderCell> {
        val out = ArrayList<RenderCell>(cols)
        for (c in 0 until cols) {
            val w = line.width[c].toInt()
            val t = line.text[c]
            out.add(
                RenderCell(
                    text = t ?: "",
                    fg = line.fg[c],
                    bg = line.bg[c],
                    attrs = line.attrs[c] or (if (reverseVideo) TerminalAttr.REVERSE else 0),
                    width = w,
                ),
            )
        }
        return out
    }

    fun snapshot(): TerminalSnapshot = synchronized(lock) {
        val total = totalLinesInternal()
        val maxOffset = maxScrollOffsetInternal()
        val offset = scrollOffset.coerceIn(0, maxOffset)
        val first = (total - rows - offset).coerceAtLeast(0)
        val out = ArrayList<List<RenderCell>>(rows)
        for (r in 0 until rows) {
            out.add(renderLine(lineAt(first + r)))
        }
        val cursorScreenRow = if (usingAlt) cursorRow else scrollback.size + cursorRow
        val cursorVisibleNow = cursorVisible && offset == 0
        TerminalSnapshot(
            rows = out,
            cols = cols,
            screenRows = rows,
            firstLine = first,
            totalLines = total,
            cursorRow = (cursorScreenRow - first).takeIf { it in 0 until rows } ?: 0,
            cursorCol = cursorCol,
            cursorVisible = cursorVisibleNow,
            scrollOffset = offset,
            maxScrollOffset = maxOffset,
            altScreen = usingAlt,
            bracketedPaste = bracketedPaste,
            applicationCursorKeys = applicationCursorKeys,
            title = title,
            version = version,
        )
    }

    fun lineText(absolute: Int): String = synchronized(lock) {
        if (absolute !in 0 until totalLinesInternal()) return ""
        val line = lineAt(absolute)
        val sb = StringBuilder(cols)
        var c = 0
        while (c < cols) {
            val w = line.width[c].toInt()
            if (w == 0) {
                c++
                continue
            }
            sb.append(line.text[c] ?: " ")
            c += if (w == 2) 2 else 1
        }
        sb.toString().trimEnd()
    }

    fun textForLines(startLine: Int, endLine: Int): String = synchronized(lock) {
        val from = minOf(startLine, endLine).coerceAtLeast(0)
        val to = maxOf(startLine, endLine).coerceAtMost(totalLinesInternal() - 1)
        val sb = StringBuilder()
        for (i in from..to) {
            if (i > from) sb.append('\n')
            sb.append(lineTextLocked(i))
        }
        sb.toString()
    }

    private fun lineTextLocked(absolute: Int): String {
        val line = lineAt(absolute)
        val sb = StringBuilder(cols)
        var c = 0
        while (c < cols) {
            val w = line.width[c].toInt()
            if (w == 0) { c++; continue }
            sb.append(line.text[c] ?: " ")
            c += if (w == 2) 2 else 1
        }
        return sb.toString().trimEnd()
    }

    /** Full buffer text, including scrollback (primary screen only). */
    fun dumpText(): String = synchronized(lock) {
        val total = totalLinesInternal()
        val sb = StringBuilder()
        for (i in 0 until total) {
            if (i > 0) sb.append('\n')
            sb.append(lineTextLocked(i))
        }
        var end = sb.length
        while (end > 0 && (sb[end - 1] == '\n' || sb[end - 1] == ' ')) end--
        sb.substring(0, end)
    }

    fun resetStateForSnapshot(): TerminalSnapshot = snapshot()
}
