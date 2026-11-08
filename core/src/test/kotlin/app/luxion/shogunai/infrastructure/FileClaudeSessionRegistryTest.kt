package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.model.ClaudeSession
import app.luxion.shogunai.domain.model.ClaudeSessionStatus
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
    fun `each known status and its time are read`() {
        directory.resolve("1.json").writeText("""{"pid":1,"cwd":"/home/dev/a","status":"busy","startedAt":10,"statusUpdatedAt":20}""")
        directory.resolve("2.json").writeText("""{"pid":2,"cwd":"/home/dev/b","status":"waiting","waitingFor":"approve Bash"}""")
        directory.resolve("3.json").writeText("""{"pid":3,"cwd":"/home/dev/c","status":"idle"}""")

        assertEquals(
            listOf(
                ClaudeSession(1, "/home/dev/a", null, 10, ClaudeSessionStatus.BUSY, 20),
                ClaudeSession(2, "/home/dev/b", null, null, ClaudeSessionStatus.WAITING),
                ClaudeSession(3, "/home/dev/c", null, null, ClaudeSessionStatus.IDLE),
            ),
            registry.list().sortedBy { it.pid },
        )
    }

    @Test
    fun `an unknown or missing status leaves the session without a status`() {
        directory.resolve("1.json").writeText("""{"pid":1,"cwd":"/home/dev/a","name":"a","status":"thinking"}""")
        directory.resolve("2.json").writeText("""{"pid":2,"cwd":"/home/dev/b","name":"b","status":7}""")
        directory.resolve("3.json").writeText("""{"pid":3,"cwd":"/home/dev/c","name":"c"}""")

        val sessions = registry.list().sortedBy { it.pid }

        assertEquals(listOf("a", "b", "c"), sessions.map { it.name })
        assertEquals(listOf<ClaudeSessionStatus?>(null, null, null), sessions.map { it.status })
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

    @Test
    fun `a blank folder, a blank name and a status that isn't text are left out`() {
        directory.resolve("1.json").writeText("""{"pid":1,"cwd":" "}""")
        directory.resolve("2.json").writeText("""{"pid":2,"cwd":"/w","name":" ","status":3}""")
        directory.resolve("3.json").writeText("""{"pid":3,"cwd":"/w","status":{"state":"busy"}}""")

        assertEquals(listOf(ClaudeSession(2, "/w", null), ClaudeSession(3, "/w", null)), registry.list().sortedBy { it.pid })
    }
}
