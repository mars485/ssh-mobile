package dev.sshmobile.data.repository

import kotlin.test.Test
import kotlin.test.assertEquals

class TerminalCommandLibraryCodecTest {
    @Test
    fun `history round trips utf8 values`() {
        val history = listOf("ls -la", "echo Привет 😀", "sudo systemctl status nginx")

        assertEquals(history, TerminalCommandLibraryCodec.decodeHistory(
            TerminalCommandLibraryCodec.encodeHistory(history),
        ))
    }

    @Test
    fun `snippets round trip delimiters and multiline commands`() {
        val snippets = listOf(
            TerminalCommandSnippet("id.1", "Логи: web", "journalctl -u nginx\n tail -n 100"),
            TerminalCommandSnippet("2", "Docker", "docker ps"),
        )

        assertEquals(snippets, TerminalCommandLibraryCodec.decodeSnippets(
            TerminalCommandLibraryCodec.encodeSnippets(snippets),
        ))
    }

    @Test
    fun `malformed entries are skipped`() {
        assertEquals(emptyList(), TerminalCommandLibraryCodec.decodeHistory("%%%"))
        assertEquals(emptyList(), TerminalCommandLibraryCodec.decodeSnippets("broken"))
    }
}
