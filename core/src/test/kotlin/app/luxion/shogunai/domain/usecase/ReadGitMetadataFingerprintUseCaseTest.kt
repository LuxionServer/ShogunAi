package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.model.ProjectConfig
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ReadGitMetadataFingerprintUseCaseTest {

    private val config = ProjectConfig(
        baseRepositoryPath = "/home/dev/projects/main-repo",
        worktreesRoot = "/home/dev/projects",
        secretFiles = emptyList(),
    )
    private val gitDir = "/home/dev/projects/main-repo/.git"

    private fun fileManager() = FakeFileManager(directories = setOf(gitDir)).apply {
        stamps["$gitDir/HEAD"] = 1
        stamps["$gitDir/index"] = 1
        stamps["$gitDir/refs/heads/main"] = 1
        stamps["$gitDir/worktrees/task/HEAD"] = 1
        stamps["$gitDir/objects/ab/cdef"] = 1
    }

    private fun TestScope.useCase(fileManager: FakeFileManager) =
        ReadGitMetadataFingerprintUseCase(config, fileManager, StandardTestDispatcher(testScheduler))

    @Test
    fun `the fingerprint lists the watched metadata relative to the git directory`() = runTest {
        val fingerprint = useCase(fileManager())().getOrThrow()

        assertEquals(
            listOf("HEAD" to 1L, "index" to 1L, "refs/heads/main" to 1L, "worktrees/task/HEAD" to 1L),
            fingerprint,
        )
    }

    @Test
    fun `it stays equal while nothing changes`() = runTest {
        val fileManager = fileManager()

        assertEquals(useCase(fileManager)().getOrThrow(), useCase(fileManager)().getOrThrow())
    }

    @Test
    fun `a moved ref, a new worktree record or a written index change it`() = runTest {
        val fileManager = fileManager()
        val before = useCase(fileManager)().getOrThrow()

        fileManager.stamps["$gitDir/refs/heads/main"] = 2
        val afterRef = useCase(fileManager)().getOrThrow()
        fileManager.stamps["$gitDir/worktrees/other/HEAD"] = 2
        val afterWorktree = useCase(fileManager)().getOrThrow()
        fileManager.stamps["$gitDir/index"] = 2
        val afterIndex = useCase(fileManager)().getOrThrow()

        assertNotEquals(before, afterRef)
        assertNotEquals(afterRef, afterWorktree)
        assertNotEquals(afterWorktree, afterIndex)
    }

    @Test
    fun `objects are not watched`() = runTest {
        val fileManager = fileManager()
        val before = useCase(fileManager)().getOrThrow()

        fileManager.stamps["$gitDir/objects/ab/cdef"] = 99

        assertEquals(before, useCase(fileManager)().getOrThrow())
    }

    @Test
    fun `it is empty when the git directory isn't a directory`() = runTest {
        val fileManager = FakeFileManager().apply { stamps["$gitDir/HEAD"] = 1 }

        assertTrue(useCase(fileManager)().getOrThrow().isEmpty())
    }
}
