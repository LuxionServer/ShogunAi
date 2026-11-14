package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.model.BuildSlot
import app.luxion.shogunai.domain.model.ProcessIdentity
import java.nio.file.Files
import kotlin.io.path.exists
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NioBuildTurnStoreTest {

    private val folder = Files.createTempDirectory("build-turns").resolve(".shogunai")
    private val store = NioBuildTurnStore(folder)
    private val file = folder.resolve(NioBuildTurnStore.SLOTS_FILE_NAME)

    private fun slot(id: String, startedAt: Long?) = BuildSlot(
        id,
        "/projects/main-repo/.git/worktrees/TASK-1",
        "/worktrees/TASK-1",
        "./gradlew test",
        1_000L,
        ProcessIdentity(42, startedAt),
    )

    @Test
    fun `slots are written and read back`() {
        val slots = listOf(slot("a", 7L), slot("b", null))

        store.write(slots)

        assertEquals(slots, store.read())
    }

    @Test
    fun `no slots removes the file`() {
        store.write(listOf(slot("a", 7L)))

        store.write(emptyList())

        assertFalse(file.exists())
        assertEquals(emptyList(), store.read())
    }

    @Test
    fun `a missing or unreadable file reads as no slots`() {
        assertEquals(emptyList(), store.read())
        Files.createDirectories(folder)
        file.writeText("not json")
        assertEquals(emptyList(), store.read())
        file.writeText("""{"slots":[{"id":"a"}]}""")
        assertEquals(emptyList(), store.read())
        file.writeText("{}")
        assertEquals(emptyList(), store.read())
        assertTrue(file.exists())
    }

    @Test
    fun `unknown fields are ignored`() {
        Files.createDirectories(folder)
        file.writeText(
            """{"slots":[{"id":"a","adminDirectory":"/a","worktreePath":"/w","command":"mvn","since":1,"pid":2,"processStartedAt":null,"extra":1}]}""",
        )

        assertEquals(listOf(BuildSlot("a", "/a", "/w", "mvn", 1, ProcessIdentity(2, null))), store.read())
    }
}
