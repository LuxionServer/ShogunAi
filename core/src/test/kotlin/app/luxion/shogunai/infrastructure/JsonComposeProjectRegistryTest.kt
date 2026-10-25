package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.io.ComposeRegistryEntry
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JsonComposeProjectRegistryTest {

    private val home: Path = Files.createTempDirectory("compose-registry-home")
    private val storage = home.resolve(".shogunai").resolve("compose-projects.json")
    private val registry = JsonComposeProjectRegistry(storage)
    private val repository = home.resolve("main-repo").also { it.createDirectories() }.toString()
    private val sibling = home.resolve("main-repo-api").also { it.createDirectories() }.toString()
    private val task9 = ComposeRegistryEntry("main-repo-task-9", "$repository-worktrees/TASK-9")

    @Test
    fun `nothing is recorded before the first record`() {
        assertEquals(emptyList(), registry.entries(repository))
    }

    @Test
    fun `recorded names are kept per repository and survive a new instance`() {
        registry.record(repository, task9)
        registry.record(repository, task9)

        assertEquals(listOf(task9), JsonComposeProjectRegistry(storage).entries(repository))
        assertEquals(emptyList(), registry.entries(sibling))
    }

    @Test
    fun `recording a project again replaces its worktree path`() {
        registry.record(repository, task9)
        registry.record(repository, task9.copy(worktreePath = "/elsewhere/TASK-9"))

        assertEquals(listOf(task9.copy(worktreePath = "/elsewhere/TASK-9")), registry.entries(repository))
    }

    @Test
    fun `dropped names disappear and an empty repository is removed`() {
        val task1 = ComposeRegistryEntry("main-repo-task-1", "$repository-worktrees/TASK-1")
        registry.record(repository, task9)
        registry.record(repository, task1)

        registry.drop(repository, setOf("main-repo-task-9"))
        assertEquals(listOf(task1), registry.entries(repository))

        registry.drop(repository, setOf("main-repo-task-1"))
        assertTrue(!storage.readText().contains("main-repo-task"))
    }

    @Test
    fun `a broken file reads as empty and is replaced by the next write`() {
        storage.parent.createDirectories()
        storage.writeText("{ not json")

        assertEquals(emptyList(), registry.entries(repository))
        registry.record(repository, task9)

        assertEquals(listOf(task9), JsonComposeProjectRegistry(storage).entries(repository))
    }

    @Test
    fun `names recorded by other repositories are reported as recorded elsewhere`() {
        registry.record(repository, task9)
        registry.record(sibling, ComposeRegistryEntry("main-repo-api-task-1", "$sibling-worktrees/TASK-1"))

        assertEquals(setOf("main-repo-api-task-1"), registry.recordedElsewhere(repository))
        assertEquals(setOf("main-repo-task-9"), registry.recordedElsewhere(sibling))
    }
}
