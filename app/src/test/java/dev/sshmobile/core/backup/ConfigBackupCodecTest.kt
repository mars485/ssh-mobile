package dev.sshmobile.core.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigBackupCodecTest {

    private fun sampleBackup() = ConfigBackup(
        exportedAt = 1_700_000_000_000L,
        servers = listOf(
            ServerConfigEntry(
                name = "Gateway \"quoted\"",
                host = "gw.example.com",
                port = 2222,
                username = "ops",
                authType = "PASSWORD",
                groupName = "Work",
                favorite = true,
            ),
            ServerConfigEntry(
                name = "Target",
                host = "10.0.0.5",
                port = 22,
                username = "root",
                authType = "PRIVATE_KEY",
                keySummary = "ED25519 · SHA256:abc",
                jumpHostName = "Gateway \"quoted\"",
            ),
        ),
    )

    @Test
    fun `round trip preserves metadata`() {
        val original = sampleBackup()
        val decoded = ConfigBackupCodec.decode(ConfigBackupCodec.encode(original))
        assertEquals(original.servers, decoded.servers)
        assertEquals(original.exportedAt, decoded.exportedAt)
        assertEquals(ConfigBackupCodec.CURRENT_VERSION, decoded.version)
    }

    @Test
    fun `encoded backup never contains credential fields`() {
        val text = ConfigBackupCodec.encode(sampleBackup())
        assertFalse(text.contains("\"password\""))
        assertFalse(text.contains("\"passphrase\""))
        assertFalse(text.contains("\"privateKey\""))
        assertFalse(text.contains("\"secret\""))
    }

    @Test
    fun `foreign json is rejected`() {
        val error = runCatching {
            ConfigBackupCodec.decode("""{"format":"something-else","servers":[]}""")
        }.exceptionOrNull()
        assertTrue(error is ConfigBackupFormatException)
    }

    @Test
    fun `malformed json is rejected`() {
        val error = runCatching { ConfigBackupCodec.decode("not json at all") }.exceptionOrNull()
        assertTrue(error is ConfigBackupFormatException)
    }

    @Test
    fun `newer format version is rejected`() {
        val text = """{"format":"sshmobile-config","version":99,"servers":[]}"""
        val error = runCatching { ConfigBackupCodec.decode(text) }.exceptionOrNull()
        assertTrue(error is ConfigBackupFormatException)
    }

    @Test
    fun `empty server list round trips`() {
        val decoded = ConfigBackupCodec.decode(ConfigBackupCodec.encode(ConfigBackup(exportedAt = 5L)))
        assertTrue(decoded.servers.isEmpty())
        assertEquals(5L, decoded.exportedAt)
    }
}
