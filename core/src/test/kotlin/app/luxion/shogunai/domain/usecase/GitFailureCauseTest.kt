package app.luxion.shogunai.domain.usecase

import kotlin.test.assertNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GitFailureCauseTest {

    @Test
    fun `reconoce un index lock existente y su ruta`() {
        val stderr = """
            fatal: Unable to create '~/projects/main-repo/.git/index.lock': File exists.

            Another git process seems to be running in this repository, e.g.
            an editor opened by 'git commit'.
        """.trimIndent()

        assertEquals(GitFailureCause.LockFileExists("~/projects/main-repo/.git/index.lock"), classifyGitFailure(stderr))
    }

    @Test
    fun `reconoce otro proceso de Git sin ruta`() {
        val stderr = "fatal: Another git process seems to be running in this repository"

        assertEquals(GitFailureCause.LockFileExists(null), classifyGitFailure(stderr))
    }

    @Test
    fun `reconoce permiso denegado y su ruta`() {
        val stderr = "fatal: could not create leading directories of '~/projects/task-1': Permission denied"

        assertEquals(GitFailureCause.PermissionDenied("~/projects/task-1"), classifyGitFailure(stderr))
    }

    @Test
    fun `reconoce permiso denegado sin ruta`() {
        assertEquals(GitFailureCause.PermissionDenied(null), classifyGitFailure("error: Permission denied"))
    }

    @Test
    fun `reconoce una carpeta que no es un repositorio`() {
        val stderr = "fatal: not a git repository (or any of the parent directories): .git"

        assertEquals(GitFailureCause.NotARepository, classifyGitFailure(stderr))
    }

    @Test
    fun `reconoce una rama existente y su nombre`() {
        assertEquals(
            GitFailureCause.BranchExists("feature/TASK-123"),
            classifyGitFailure("fatal: a branch named 'feature/TASK-123' already exists"),
        )
        assertEquals(
            GitFailureCause.BranchExists("TASK-123"),
            classifyGitFailure("fatal: A branch named 'TASK-123' already exists."),
        )
    }

    @Test
    fun `reconoce un worktree con cambios locales`() {
        val stderr = "fatal: '~/projects/task-1' contains modified or untracked files, use --force to delete it"

        assertEquals(GitFailureCause.LocalChanges, classifyGitFailure(stderr))
    }

    @Test
    fun `un mensaje desconocido es Unknown`() {
        assertEquals(GitFailureCause.Unknown, classifyGitFailure("fatal: something nobody expected"))
        assertEquals(GitFailureCause.Unknown, classifyGitFailure(""))
    }

    @Test
    fun `every recognized cause has a hint and an unknown one has none`() {
        assertEquals(
            "The branch feature/TASK-1 already exists. Use another task id, or run 'shogun new --branch feature/TASK-1' to use it.",
            GitFailureCause.BranchExists("feature/TASK-1").hint(),
        )
        assertEquals(null, GitFailureCause.Unknown.hint())
        listOf(
            GitFailureCause.LockFileExists(null),
            GitFailureCause.PermissionDenied("~/projects"),
            GitFailureCause.NotARepository,
            GitFailureCause.LocalChanges,
        ).forEach { assertTrue(it.hint() != null) }
    }
    @Test
    fun `hints name the path only when it is known`() {
        assertTrue(GitFailureCause.LockFileExists(null).hint().orEmpty().contains("remove the lock file"))
        assertTrue(GitFailureCause.LockFileExists("/repo/.git/index.lock").hint().orEmpty().contains("remove /repo/.git/index.lock"))
        assertTrue(GitFailureCause.PermissionDenied(null).hint().orEmpty().startsWith("Git was denied permission. "))
        assertTrue(GitFailureCause.PermissionDenied("/repo").hint().orEmpty().startsWith("Git was denied permission on /repo. "))
    }

    @Test
    fun `the lock reason is read only when Git gives one`() {
        assertEquals("on a USB drive", lockReason("fatal: cannot remove a locked working tree, lock reason: on a USB drive \n"))
        assertNull(lockReason("fatal: cannot remove a locked working tree, lock reason:  "))
        assertNull(lockReason("fatal: cannot remove a locked working tree"))
    }
}
