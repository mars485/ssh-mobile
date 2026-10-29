package dev.sshmobile.core.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

class TerminalEmulatorTest {

    private fun emulator(cols: Int = 20, rows: Int = 5): TerminalEmulator =
        TerminalEmulator(cols = cols, rows = rows, maxScrollback = 50)

    private fun TerminalEmulator.line(row: Int): String =
        snapshot().rows[row].joinToString("") { cell ->
            if (cell.width == 0) "" else cell.text.ifEmpty { " " }
        }.trimEnd()

    private fun TerminalEmulator.feedAscii(text: String) = feedText(text)

    @Test
    fun `prints plain text and moves the cursor`() {
        val term = emulator()
        term.feedAscii("hello")
        val snapshot = term.snapshot()
        assertEquals("hello", term.line(0))
        assertEquals(0, snapshot.cursorRow)
        assertEquals(5, snapshot.cursorCol)
        assertTrue(snapshot.cursorVisible)
    }

    @Test
    fun `wraps at the right margin using deferred wrap`() {
        val term = emulator(cols = 5, rows = 3)
        term.feedAscii("abcdef")
        assertEquals("abcde", term.line(0))
        assertEquals("f", term.line(1))
        assertEquals(1, term.snapshot().cursorRow)
        assertEquals(1, term.snapshot().cursorCol)
    }

    @Test
    fun `carriage return and line feed`() {
        val term = emulator()
        term.feedAscii("ab\r\ncd")
        assertEquals("ab", term.line(0))
        assertEquals("cd", term.line(1))
    }

    @Test
    fun `cursor addressing works`() {
        val term = emulator()
        term.feedAscii("\u001B[2;3HX")
        assertEquals("  X", term.line(1))
        assertEquals(1, term.snapshot().cursorRow)
        assertEquals(3, term.snapshot().cursorCol)
    }

    @Test
    fun `erase in line and display`() {
        val term = emulator(cols = 10, rows = 3)
        term.feedAscii("hello")
        term.feedAscii("\r\u001B[K")
        assertEquals("", term.line(0))

        term.feedAscii("abc\u001B[1K")
        assertEquals("", term.line(0))

        term.feedAscii("one\r\ntwo\r\nthree\u001B[2J")
        assertEquals("", term.line(0))
        assertEquals("", term.line(1))
        assertEquals("", term.line(2))
    }

    @Test
    fun `sgr colours and attributes`() {
        val term = emulator()
        term.feedAscii("\u001B[31mR\u001B[0mN\u001B[1;4;7mB")
        val row = term.snapshot().rows[0]
        assertEquals("R", row[0].text)
        assertEquals(TerminalPalette.color(1), row[0].fg)
        assertEquals("N", row[1].text)
        assertEquals(0, row[1].fg)
        assertEquals("B", row[2].text)
        assertTrue(row[2].attrs and TerminalAttr.BOLD != 0)
        assertTrue(row[2].attrs and TerminalAttr.UNDERLINE != 0)
        assertTrue(row[2].attrs and TerminalAttr.REVERSE != 0)
    }

    @Test
    fun `256 and true colour`() {
        val term = emulator()
        term.feedAscii("\u001B[38;5;196mA\u001B[38;2;1;2;3mB")
        val row = term.snapshot().rows[0]
        assertEquals(TerminalPalette.color(196), row[0].fg)
        assertEquals(0xFF010203.toInt(), row[1].fg)
    }

    @Test
    fun `scrollback keeps old lines and can be viewed`() {
        val term = emulator(cols = 10, rows = 3)
        term.feedAscii("1\r\n2\r\n3\r\n4")
        assertEquals(1, term.maxScrollOffset())
        assertEquals("2", term.line(0))
        assertEquals("4", term.line(2))

        term.scrollBy(1)
        val snapshot = term.snapshot()
        assertEquals(1, snapshot.scrollOffset)
        assertEquals("1", snapshot.rows[0].joinToString("") { it.text })
        assertEquals("3", snapshot.rows[2].joinToString("") { it.text })

        term.scrollToBottom()
        assertEquals(0, term.snapshot().scrollOffset)
    }

    @Test
    fun `alternate screen is isolated from the primary buffer`() {
        val term = emulator()
        term.feedAscii("main")
        term.feedAscii("\u001B[?1049h")
        assertTrue(term.isAlternateScreen())
        assertEquals("", term.line(0))
        term.feedAscii("alt")
        assertEquals("alt", term.line(0))
        term.feedAscii("\u001B[?1049l")
        assertFalse(term.isAlternateScreen())
        assertEquals("main", term.line(0))
        assertEquals(4, term.snapshot().cursorCol)
    }

    @Test
    fun `resize preserves the visible content`() {
        val term = emulator(cols = 10, rows = 4)
        term.feedAscii("abcdef")
        term.resize(5, 2)
        assertEquals(5, term.size.cols)
        assertEquals(2, term.size.rows)
        assertEquals("abcde", term.line(0))
        assertEquals(4, term.snapshot().cursorCol)
    }

    @Test
    fun `utf8 sequences split across reads are decoded`() {
        val term = emulator(cols = 20, rows = 2)
        val bytes = "Привет".toByteArray(StandardCharsets.UTF_8)
        term.feed(bytes.copyOfRange(0, 3))
        term.feed(bytes.copyOfRange(3, bytes.size))
        assertEquals("Привет", term.line(0))
    }

    @Test
    fun `wide characters occupy two cells`() {
        val term = emulator(cols = 10, rows = 2)
        term.feedAscii("a你好b")
        val row = term.snapshot().rows[0]
        assertEquals("你", row[1].text)
        assertEquals(2, row[1].width)
        assertEquals(0, row[2].width)
        assertEquals("好", row[3].text)
        assertEquals("b", row[5].text)
        assertEquals("a你好b", term.dumpText())
    }

    @Test
    fun `insert and delete characters`() {
        val insertTerm = emulator(cols = 12, rows = 2)
        insertTerm.feedAscii("abc\u001B[2G\u001B[2@")
        assertEquals("a  bc", insertTerm.line(0))

        val deleteTerm = emulator(cols = 12, rows = 2)
        deleteTerm.feedAscii("abcdef\u001B[H\u001B[2P")
        assertEquals("cdef", deleteTerm.line(0))
    }

    @Test
    fun `scroll region confines scrolling`() {
        val term = emulator(cols = 10, rows = 5)
        term.feedAscii("top\u001B[2;4r\u001B[4;1Hx\n")
        val snapshot = term.snapshot()
        assertEquals("top", snapshot.rows[0].joinToString("") { it.text })
        assertEquals("x", snapshot.rows[2].joinToString("") { it.text })
        assertEquals("", snapshot.rows[3].joinToString("") { it.text })
        assertEquals("", snapshot.rows[4].joinToString("") { it.text })
    }

    @Test
    fun `dec special graphics charset`() {
        val term = emulator()
        term.feedAscii("\u001B(0q\u001B(Bx")
        assertEquals("─x", term.line(0))
    }

    @Test
    fun `osc title is captured`() {
        val term = emulator()
        term.feedAscii("\u001B]0;my session\u0007")
        assertEquals("my session", term.osTitle)
        term.feedAscii("\u001B]2;second title\u001B\\")
        assertEquals("second title", term.osTitle)
    }

    @Test
    fun `bracketed paste and application cursor modes`() {
        val term = emulator()
        term.feedAscii("\u001B[?2004h")
        assertTrue(term.isBracketedPasteEnabled())
        term.feedAscii("\u001B[?2004l")
        assertFalse(term.isBracketedPasteEnabled())
        term.feedAscii("\u001B[?1h")
        assertTrue(term.usesApplicationCursorKeys())
    }

    @Test
    fun `saves and restores cursor`() {
        val term = emulator()
        term.feedAscii("a\u001B7b\u001B8c")
        assertEquals("ac", term.line(0))
    }

    @Test
    fun `answers cursor position queries`() {
        val term = emulator()
        val responses = mutableListOf<String>()
        term.onResponse = { bytes -> responses += String(bytes, StandardCharsets.UTF_8) }
        term.feedAscii("\u001B[2;3H\u001B[6n")
        assertEquals(listOf("\u001B[2;3R"), responses)
    }

    @Test
    fun `dump text includes scrollback`() {
        val term = emulator(cols = 10, rows = 2)
        term.feedAscii("first\r\nsecond\r\nthird")
        assertEquals("first\nsecond\nthird", term.dumpText())
    }

    @Test
    fun `erase clears trailing half of wide character`() {
        val term = emulator(cols = 6, rows = 2)
        term.feedAscii("你好")
        term.feedAscii("\u001B[1;2H\u001B[K")
        assertEquals("你", term.dumpText())
    }

    @Test
    fun `line feed at the bottom scrolls into scrollback`() {
        val term = emulator(cols = 10, rows = 2)
        term.feedAscii("a\r\nb\r\nc")
        assertEquals("b", term.line(0))
        assertEquals("c", term.line(1))
        assertEquals(1, term.maxScrollOffset())
    }
}
