package app.luxion.shogunai.domain.usecase

import kotlin.io.path.createDirectory
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WorktreeEnvironmentCacheTest {

    private val directories = CachedGitDirectories(admin = "/projects/main-repo/.git", common = "/projects/main-repo/.git")

    @Test
    fun `a main checkout with a git directory is cached`() {
        val worktree = createTempDirectory("main-checkout").apply { resolve(".git").createDirectory() }
        val cache = WorktreeEnvironmentCache()

        cache.remember(worktree.toString(), directories)

        assertEquals(directories, cache.directoriesOf(worktree.toString()))
    }

    @Test
    fun `a linked worktree is cached while its git file is unchanged`() {
        val worktree = createTempDirectory("linked-worktree")
        val gitFile = worktree.resolve(".git").apply { writeText("gitdir: /projects/main-repo/.git/worktrees/TASK-1") }
        val cache = WorktreeEnvironmentCache()

        cache.remember(worktree.toString(), directories)
        assertEquals(directories, cache.directoriesOf(worktree.toString()))

        gitFile.writeText("gitdir: /projects/main-repo/.git/worktrees/TASK-11")
        assertNull(cache.directoriesOf(worktree.toString()))
    }

    @Test
    fun `a folder without a git entry is not cached`() {
        val folder = createTempDirectory("not-a-worktree")
        val cache = WorktreeEnvironmentCache()

        cache.remember(folder.toString(), directories)

        assertNull(cache.directoriesOf(folder.toString()))
    }

    @Test
    fun `an unreadable git file is not cached`() {
        val worktree = createTempDirectory("unreadable-worktree")
        assumeTrue(worktree.fileSystem.supportedFileAttributeViews().contains("posix"))
        val gitFile = worktree.resolve(".git").apply { writeText("gitdir: /projects/main-repo/.git/worktrees/TASK-1") }
        val cache = WorktreeEnvironmentCache()
        cache.remember(worktree.toString(), directories)

        gitFile.toFile().setReadable(false)
        try {
            assertNull(cache.directoriesOf(worktree.toString()))
        } finally {
            gitFile.toFile().setReadable(true)
        }
    }
}
