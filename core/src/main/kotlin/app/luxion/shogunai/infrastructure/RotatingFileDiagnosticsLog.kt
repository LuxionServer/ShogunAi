package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.io.DiagnosticsLog
import app.luxion.shogunai.domain.model.DiagnosticEntry
import app.luxion.shogunai.domain.model.DiagnosticKind
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Instant

class RotatingFileDiagnosticsLog(
    private val directory: Path,
    private val maxFileBytes: Long = DEFAULT_MAX_FILE_BYTES,
    private val maxFiles: Int = DEFAULT_MAX_FILES,
    private val redact: (String) -> String = { it },
) : DiagnosticsLog {

    private val lock = Any()

    override fun record(entry: DiagnosticEntry) {
        runCatching {
            val line = encode(entry.copy(message = redact(entry.message), stackTrace = entry.stackTrace?.let(redact))) + "\n"
            synchronized(lock) {
                Files.createDirectories(directory)
                val current = file(0)
                val size = if (Files.exists(current)) Files.size(current) else 0L
                if (size > 0 && size + line.toByteArray().size > maxFileBytes) rotate()
                Files.writeString(current, line, Charsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
            }
        }
    }

    override fun recent(limit: Int): List<DiagnosticEntry> = synchronized(lock) {
        (maxFiles - 1 downTo 0).map(::file)
            .filter { Files.isRegularFile(it) }
            .flatMap { runCatching { Files.readAllLines(it, Charsets.UTF_8) }.getOrDefault(emptyList()) }
            .mapNotNull(::decode)
            .takeLast(limit)
    }

    private fun rotate() {
        Files.deleteIfExists(file(maxFiles - 1))
        for (index in maxFiles - 2 downTo 0) {
            val source = file(index)
            if (Files.exists(source)) Files.move(source, file(index + 1))
        }
    }

    private fun file(index: Int): Path = directory.resolve(if (index == 0) "diagnostics.log" else "diagnostics.$index.log")

    private fun encode(entry: DiagnosticEntry): String =
        listOf(entry.timestamp.toString(), entry.kind.name, escape(entry.message), escape(entry.stackTrace.orEmpty())).joinToString("\t")

    private fun decode(line: String): DiagnosticEntry? {
        val parts = line.split("\t")
        if (parts.size != FIELD_COUNT) return null
        return runCatching {
            DiagnosticEntry(
                timestamp = Instant.parse(parts[0]),
                kind = DiagnosticKind.valueOf(parts[1]),
                message = unescape(parts[2]),
                stackTrace = unescape(parts[3]).ifEmpty { null },
            )
        }.getOrNull()
    }

    private fun escape(text: String): String =
        text.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")

    private fun unescape(text: String): String {
        val result = StringBuilder()
        var index = 0
        while (index < text.length) {
            val char = text[index]
            if (char == '\\' && index + 1 < text.length) {
                index++
                result.append(
                    when (text[index]) {
                        'n' -> '\n'
                        'r' -> '\r'
                        't' -> '\t'
                        else -> text[index]
                    },
                )
            } else {
                result.append(char)
            }
            index++
        }
        return result.toString()
    }

    companion object {
        const val DEFAULT_MAX_FILE_BYTES = 1_048_576L
        const val DEFAULT_MAX_FILES = 3
        private const val FIELD_COUNT = 4
    }
}
