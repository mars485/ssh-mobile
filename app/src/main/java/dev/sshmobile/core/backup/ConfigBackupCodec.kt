package dev.sshmobile.core.backup

/**
 * Portable configuration backup. It deliberately carries only server metadata: passwords,
 * private keys and passphrases are never part of this model, so an exported file cannot leak
 * credentials.
 */
data class ServerConfigEntry(
    val name: String,
    val host: String,
    val port: Int,
    val username: String,
    val authType: String,
    val groupName: String? = null,
    val favorite: Boolean = false,
    val keySummary: String? = null,
    /** Referenced gateway server by name; ids are not portable between installations. */
    val jumpHostName: String? = null,
)

data class ConfigBackup(
    val version: Int = ConfigBackupCodec.CURRENT_VERSION,
    val exportedAt: Long = 0L,
    val servers: List<ServerConfigEntry> = emptyList(),
)

class ConfigBackupFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Self-contained JSON codec with no Android dependencies, so the exact same code runs in the app
 * and in plain JVM tests. The format is intentionally small and readable.
 */
object ConfigBackupCodec {
    const val CURRENT_VERSION = 1
    private const val FORMAT = "sshmobile-config"

    fun encode(backup: ConfigBackup): String = buildString {
        append("{\n")
        append("  \"format\": ").append(quote(FORMAT)).append(",\n")
        append("  \"version\": ").append(backup.version).append(",\n")
        append("  \"exportedAt\": ").append(backup.exportedAt).append(",\n")
        append("  \"servers\": [")
        backup.servers.forEachIndexed { index, entry ->
            if (index > 0) append(',')
            append("\n    ")
            appendEntry(entry)
        }
        if (backup.servers.isNotEmpty()) append("\n  ")
        append("]\n}\n")
    }

    fun decode(text: String): ConfigBackup {
        val root = try {
            JsonParser(text).parse()
        } catch (t: Throwable) {
            throw ConfigBackupFormatException("The backup file is not valid JSON", t)
        }
        val map = root as? Map<*, *>
            ?: throw ConfigBackupFormatException("The backup file must contain a JSON object")
        if ((map["format"] as? String) != FORMAT) {
            throw ConfigBackupFormatException("This file is not an SSH Mobile configuration backup")
        }
        val version = (map["version"] as? Number)?.toInt() ?: CURRENT_VERSION
        if (version > CURRENT_VERSION) {
            throw ConfigBackupFormatException(
                "The backup was created by a newer app version (format $version)",
            )
        }
        val exportedAt = (map["exportedAt"] as? Number)?.toLong() ?: 0L
        val rawServers = map["servers"] as? List<*>
            ?: throw ConfigBackupFormatException("The backup has no servers list")
        val servers = rawServers.mapIndexed { index, raw ->
            val entry = raw as? Map<*, *>
                ?: throw ConfigBackupFormatException("Server #${index + 1} is not an object")
            ServerConfigEntry(
                name = (entry["name"] as? String)?.trim().orEmpty(),
                host = (entry["host"] as? String)?.trim().orEmpty(),
                port = (entry["port"] as? Number)?.toInt() ?: 0,
                username = (entry["username"] as? String)?.trim().orEmpty(),
                authType = (entry["authType"] as? String)?.trim().orEmpty(),
                groupName = (entry["groupName"] as? String)?.trim()?.takeIf { it.isNotEmpty() },
                favorite = entry["favorite"] as? Boolean ?: false,
                keySummary = (entry["keySummary"] as? String)?.takeIf { it.isNotBlank() },
                jumpHostName = (entry["jumpHostName"] as? String)?.trim()?.takeIf { it.isNotEmpty() },
            )
        }
        return ConfigBackup(version = version, exportedAt = exportedAt, servers = servers)
    }

    private fun StringBuilder.appendEntry(entry: ServerConfigEntry) {
        append('{')
        append("\"name\":").append(quote(entry.name))
        append(",\"host\":").append(quote(entry.host))
        append(",\"port\":").append(entry.port)
        append(",\"username\":").append(quote(entry.username))
        append(",\"authType\":").append(quote(entry.authType))
        entry.groupName?.let { append(",\"groupName\":").append(quote(it)) }
        if (entry.favorite) append(",\"favorite\":true")
        entry.keySummary?.let { append(",\"keySummary\":").append(quote(it)) }
        entry.jumpHostName?.let { append(",\"jumpHostName\":").append(quote(it)) }
        append('}')
    }

    private fun quote(value: String): String = buildString {
        append('"')
        value.forEach { c ->
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (c < ' ') {
                    append("\\u")
                    append(c.code.toString(16).padStart(4, '0'))
                } else {
                    append(c)
                }
            }
        }
        append('"')
    }

    /** Minimal recursive-descent JSON reader: objects, arrays, strings, numbers, booleans, null. */
    private class JsonParser(private val text: String) {
        private var position = 0

        fun parse(): Any? {
            val value = readValue()
            skipWhitespace()
            if (position != text.length) fail("Unexpected trailing content")
            return value
        }

        private fun readValue(): Any? {
            skipWhitespace()
            if (position >= text.length) fail("Unexpected end of file")
            return when (val c = text[position]) {
                '{' -> readObject()
                '[' -> readArray()
                '"' -> readString()
                't' -> readLiteral("true", true)
                'f' -> readLiteral("false", false)
                'n' -> readLiteral("null", null)
                else -> if (c == '-' || c.isDigit()) readNumber() else fail("Unexpected character '$c'")
            }
        }

        private fun readObject(): Map<String, Any?> {
            expect('{')
            val result = LinkedHashMap<String, Any?>()
            skipWhitespace()
            if (peek() == '}') {
                position++
                return result
            }
            while (true) {
                skipWhitespace()
                val key = readString()
                skipWhitespace()
                expect(':')
                result[key] = readValue()
                skipWhitespace()
                when (peek()) {
                    ',' -> position++
                    '}' -> {
                        position++
                        return result
                    }
                    else -> fail("Expected ',' or '}'")
                }
            }
        }

        private fun readArray(): List<Any?> {
            expect('[')
            val result = ArrayList<Any?>()
            skipWhitespace()
            if (peek() == ']') {
                position++
                return result
            }
            while (true) {
                result += readValue()
                skipWhitespace()
                when (peek()) {
                    ',' -> position++
                    ']' -> {
                        position++
                        return result
                    }
                    else -> fail("Expected ',' or ']'")
                }
            }
        }

        private fun readString(): String {
            expect('"')
            val builder = StringBuilder()
            while (true) {
                if (position >= text.length) fail("Unterminated string")
                when (val c = text[position++]) {
                    '"' -> return builder.toString()
                    '\\' -> {
                        if (position >= text.length) fail("Unterminated escape")
                        when (val escape = text[position++]) {
                            '"' -> builder.append('"')
                            '\\' -> builder.append('\\')
                            '/' -> builder.append('/')
                            'b' -> builder.append('\b')
                            'f' -> builder.append('\u000C')
                            'n' -> builder.append('\n')
                            'r' -> builder.append('\r')
                            't' -> builder.append('\t')
                            'u' -> {
                                if (position + 4 > text.length) fail("Invalid unicode escape")
                                val code = text.substring(position, position + 4).toIntOrNull(16)
                                    ?: fail("Invalid unicode escape")
                                builder.append(code.toChar())
                                position += 4
                            }
                            else -> fail("Invalid escape '\\$escape'")
                        }
                    }
                    else -> builder.append(c)
                }
            }
        }

        private fun readNumber(): Number {
            val start = position
            if (peek() == '-') position++
            while (position < text.length && text[position].isDigit()) position++
            var fractional = false
            if (position < text.length && text[position] == '.') {
                fractional = true
                position++
                while (position < text.length && text[position].isDigit()) position++
            }
            if (position < text.length && (text[position] == 'e' || text[position] == 'E')) {
                fractional = true
                position++
                if (position < text.length && (text[position] == '+' || text[position] == '-')) position++
                while (position < text.length && text[position].isDigit()) position++
            }
            val raw = text.substring(start, position)
            return if (fractional) {
                raw.toDoubleOrNull() ?: fail("Invalid number")
            } else {
                raw.toLongOrNull() ?: fail("Invalid number")
            }
        }

        private fun readLiteral(literal: String, value: Any?): Any? {
            if (!text.startsWith(literal, position)) fail("Invalid literal")
            position += literal.length
            return value
        }

        private fun expect(c: Char) {
            if (peek() != c) fail("Expected '$c'")
            position++
        }

        private fun peek(): Char {
            if (position >= text.length) fail("Unexpected end of file")
            return text[position]
        }

        private fun skipWhitespace() {
            while (position < text.length && text[position].isWhitespace()) position++
        }

        private fun fail(message: String): Nothing = throw ConfigBackupFormatException(message)
    }
}
