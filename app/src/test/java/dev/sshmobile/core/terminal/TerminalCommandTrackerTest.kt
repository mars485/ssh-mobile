package dev.sshmobile.core.terminal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TerminalCommandTrackerTest {
    @Test
    fun `records completed command lines and trims surrounding whitespace`() {
        val tracker = TerminalCommandTracker()

        assertEquals(emptyList(), tracker.type("  echo hello  "))
        assertEquals(listOf("echo hello"), tracker.type("\n"))
        assertNull(tracker.enter())
    }

    @Test
    fun `backspace removes a full unicode code point`() {
        val tracker = TerminalCommandTracker()

        tracker.type("echo 😀x")
        tracker.backspace()

        assertEquals("echo 😀", tracker.enter())
    }

    @Test
    fun `multiline paste and disabled tracking are ignored`() {
        val tracker = TerminalCommandTracker()

        tracker.type("partial")
        assertEquals(emptyList(), tracker.paste("echo a\necho b"))
        assertNull(tracker.enter())

        tracker.type("vim")
        assertEquals(emptyList(), tracker.type("x", enabled = false))
        assertNull(tracker.enter())
    }

    @Test
    fun `control input clears draft instead of saving a corrupted command`() {
        val tracker = TerminalCommandTracker()

        tracker.type("password")
        tracker.type("\u0003")

        assertNull(tracker.enter())
    }

    @Test
    fun `untracked cursor edits discard the whole line until enter`() {
        val tracker = TerminalCommandTracker()

        tracker.type("ech")
        tracker.invalidate()
        tracker.type("o hello")
        assertNull(tracker.enter())

        tracker.type("echo next")
        assertEquals("echo next", tracker.enter())
    }
}
