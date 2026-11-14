package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.model.NotifiedWorktree
import app.luxion.shogunai.domain.model.PendingSession
import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class JsonPendingNotificationStoreTest {
    private val folder = Files.createTempDirectory("pending-notifications")
    private val path = folder.resolve("notifications/pending.json")
    private val session = PendingSession("s1", NotifiedWorktree("api", "api", "/home/dev/projects/TASK-1", "feature/login"), 1_000)

    @AfterTest
    fun cleanUp() {
        folder.toFile().deleteRecursively()
    }

    @Test
    fun `pending sessions are kept across stores and the block's result is returned`() {
        val added = JsonPendingNotificationStore(path).update { (it + session) to it.size }

        assertEquals(0, added)
        assertEquals(listOf(session), JsonPendingNotificationStore(path).update { it to it })
    }

    @Test
    fun `entries missing a field, of the wrong type or not objects are skipped`() {
        Files.createDirectories(path.parent)
        val good = """{"sessionId":"s1","projectId":"api","projectName":"api","worktreePath":"/home/dev/projects/TASK-1","worktreeName":"feature/login","sinceMillis":1000}"""
        val broken = listOf("sessionId", "projectId", "projectName", "worktreePath", "worktreeName").map { good.replace("\"$it\"", "\"x$it\"") } +
            good.replace("1000", "\"1000\"") + good.replace(":1000", ":true") + good.replace("\"sinceMillis\"", "\"since\"") +
            good.replace("\"api\",\"projectName\"", "7,\"projectName\"") + good.replace("\"api\",\"projectName\"", "{},\"projectName\"") + "3"
        path.writeText("""{"version":1,"sessions":[$good,${broken.joinToString(",")}]}""")

        assertEquals(listOf(session), JsonPendingNotificationStore(path).update { it to it })
    }

    @Test
    fun `a file without sessions starts empty`() {
        Files.createDirectories(path.parent)
        path.writeText("""{"version":1,"sessions":3}""")

        assertEquals(emptyList(), JsonPendingNotificationStore(path).update { it to it })
        path.writeText("[1]")
        assertEquals(emptyList(), JsonPendingNotificationStore(path).update { it to it })
    }

    @Test
    fun `an unreadable file starts empty`() {
        Files.createDirectories(path.parent)
        path.writeText("not json")

        assertEquals(emptyList(), JsonPendingNotificationStore(path).update { it to it })
    }
}
