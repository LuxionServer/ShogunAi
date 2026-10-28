package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.model.ClaudeSession
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class FileClaudeSessionRegistryTest {

    private val directory: Path = Files.createTempDirectory("claude-sessions")
    private val registry = FileClaudeSessionRegistry(directory.toString())

    @AfterTest
    fun cleanUp() {
        directory.toFile().deleteRecursively()
    }

    @Test
    fun `a valid session file is read`() {
        directory.resolve("4242.json").writeText(
            """{"pid":4242,"sessionId":"s1","cwd":"/home/dev/projects/main-repo","name":"main-repo","startedAt":1700000000000}""",
        )

        assertEquals(listOf(ClaudeSession(4242, "/home/dev/projects/main-repo", "main-repo", 1700000000000)), registry.list())
    }

    @Test
    fun `unknown fields are ignored and missing optional fields are null`() {
        directory.resolve("4242.json").writeText(
            """{"pid":4242,"cwd":"/home/dev/projects/main-repo","peerProtocol":1,"peerFeatures":["a"],"status":{"kind":"waiting"}}""",
        )

        assertEquals(listOf(ClaudeSession(4242, "/home/dev/projects/main-repo", null, null)), registry.list())
    }

    @Test
    fun `files without a pid or a folder, invalid json and other files are skipped`() {
        directory.resolve("1.json").writeText("""{"cwd":"/home/dev/projects/main-repo","name":"a"}""")
        directory.resolve("2.json").writeText("""{"pid":2,"name":"b"}""")
        directory.resolve("3.json").writeText("not json {")
        directory.resolve("4.json").writeText("""[1, 2]""")
        directory.resolve("5.json").writeText("""{"pid":"five","cwd":"/home/dev/projects/main-repo"}""")
        directory.resolve("6.abc123.key").writeText("""{"pid":6,"cwd":"/home/dev/projects/main-repo"}""")
        directory.resolve("7.json").writeText("""{"pid":7,"cwd":"/home/dev/projects/other","name":"other"}""")

        assertEquals(listOf(ClaudeSession(7, "/home/dev/projects/other", "other")), registry.list())
    }

    @Test
    fun `reading never writes to the registry`() {
        val file = directory.resolve("4242.json")
        val content = """{"pid":4242,"cwd":"/home/dev/projects/main-repo","name":"main-repo"}"""
        file.writeText(content)

        registry.list()

        assertEquals(listOf(file), directory.listDirectoryEntries())
        assertEquals(content, file.readText())
    }

    @Test
    fun `a missing directory has no sessions`() {
        assertEquals(emptyList(), FileClaudeSessionRegistry(directory.resolve("missing").toString()).list())
    }
}
