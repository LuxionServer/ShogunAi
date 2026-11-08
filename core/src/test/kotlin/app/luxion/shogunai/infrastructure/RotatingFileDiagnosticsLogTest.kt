package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.model.DiagnosticEntry
import app.luxion.shogunai.domain.model.DiagnosticKind
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RotatingFileDiagnosticsLogTest {

    private val directory: Path = Files.createTempDirectory("diagnostics-log")

    @AfterTest
    fun cleanUp() {
        directory.toFile().deleteRecursively()
    }

    private fun entry(message: String, stackTrace: String? = null, second: Long = 0) =
        DiagnosticEntry(Instant.parse("2026-01-01T00:00:00Z").plusSeconds(second), DiagnosticKind.UNHANDLED_ERROR, message, stackTrace)

    private fun log(maxFileBytes: Long = 1_000, redact: (String) -> String = { it }) =
        RotatingFileDiagnosticsLog(directory, maxFileBytes = maxFileBytes, redact = redact)

    private fun files() = Files.list(directory).use { stream -> stream.map { it.fileName.toString() }.sorted().toList() }

    @Test
    fun `appends entries and reads them back with their stack trace`() {
        val log = log()

        log.record(entry("first", "java.lang.IllegalStateException: boom\n\tat app.Foo.bar(Foo.kt:1)"))
        log.record(entry("second\twith tab", second = 1))

        assertEquals(listOf("first", "second\twith tab"), log.recent(10).map { it.message })
        assertEquals("java.lang.IllegalStateException: boom\n\tat app.Foo.bar(Foo.kt:1)", log.recent(10).first().stackTrace)
        assertEquals(listOf("diagnostics.log"), files())
    }

    @Test
    fun `rotates when the current file would exceed its size and keeps at most three files`() {
        val log = log(maxFileBytes = 300)

        repeat(30) { log.record(entry("entry-$it ".padEnd(100, 'x'), second = it.toLong())) }

        assertEquals(listOf("diagnostics.1.log", "diagnostics.2.log", "diagnostics.log"), files())
        assertTrue(files().all { Files.size(directory.resolve(it)) <= 300 })
        assertTrue(log.recent(100).last().message.startsWith("entry-29"))
    }

    @Test
    fun `recent returns the latest entries across rotated files`() {
        val log = log(maxFileBytes = 300)
        repeat(10) { log.record(entry("entry-$it ".padEnd(100, 'x'), second = it.toLong())) }

        val recent = log.recent(4)

        assertEquals(listOf("entry-6", "entry-7", "entry-8", "entry-9"), recent.map { it.message.substringBefore(' ') })
    }

    @Test
    fun `redacts entries before writing them`() {
        val log = log(redact = { it.replace("secret", "<x>") })

        log.record(entry("a secret message", "secret trace"))

        assertTrue(!Files.readString(directory.resolve("diagnostics.log")).contains("secret"))
        assertEquals("a <x> message", log.recent(1).single().message)
    }

    @Test
    fun `an unwritable folder doesn't throw`() {
        val blocker = Files.createFile(directory.resolve("not-a-folder"))
        val log = RotatingFileDiagnosticsLog(blocker.resolve("logs"))

        log.record(entry("lost"))

        assertTrue(log.recent(10).isEmpty())
    }

    @Test
    fun `carriage returns and backslashes survive the round trip`() {
        val log = log()

        log.record(entry("line\r\nC:\\Users\\dev"))

        assertEquals("line\r\nC:\\Users\\dev", log.recent(1).single().message)
    }

    @Test
    fun `lines that aren't entries, entries that can't be parsed and a trailing backslash are read leniently`() {
        Files.writeString(
            directory.resolve("diagnostics.log"),
            "only\tthree\tfields\n" +
                "not-a-time\tFREEZE\tm\t\n" +
                "2026-01-01T00:00:00Z\tFREEZE\tends with \\\t\n",
        )

        assertEquals(listOf("ends with \\"), log().recent(10).map { it.message })
    }

    @Test
    fun `a rotated file that can't be read is skipped`() {
        Files.write(directory.resolve("diagnostics.1.log"), byteArrayOf(0xC3.toByte(), 0x28))

        assertEquals(emptyList(), log().recent(10))
    }
}
