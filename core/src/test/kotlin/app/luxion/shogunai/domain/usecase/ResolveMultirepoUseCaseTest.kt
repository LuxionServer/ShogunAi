package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.FakeGitRepositories
import app.luxion.shogunai.domain.FakeMultirepoSource
import app.luxion.shogunai.domain.model.MultirepoDeclaration
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ResolveMultirepoUseCaseTest {

    private val world = MultirepoTestWorld()

    @Test
    fun `the multirepo folder resolves to the multirepo at its folder`() = runTest {
        val context = world.resolver(world.folder).getOrThrow()!!

        assertEquals(world.folder, context.multirepo.folder)
        assertEquals(world.worktreesRoot, context.multirepo.worktreesRoot)
        assertTrue(context.atFolder)
        assertNull(context.alias)
    }

    @Test
    fun `a worktree of a repository outside the folder resolves through the known multirepos`() = runTest {
        with(world.git) { world.api.worktree("/home/dev/old/api-task-7", "feature/TASK-7") }

        val context = world.resolver("/home/dev/old/api-task-7/src").getOrThrow()!!

        assertEquals(world.folder, context.multirepo.folder)
        assertEquals("api", context.alias)
        assertFalse(context.atFolder)
    }

    @Test
    fun `repositories in different places are found through the registry`() = runTest {
        val git = FakeGitRepositories()
        git.repository("/src/web-client")
        git.repository("/home/dev/code/backend")
        val source = FakeMultirepoSource(
            mapOf("/home/dev/work/platform" to MultirepoDeclaration(mapOf("web" to "/src/web-client", "backend" to "/home/dev/code/backend"), "../platform-worktrees")),
        )
        val resolver = ResolveMultirepoUseCase(source, git, FakeFileManager(listOf("/src/web-client", "/home/dev/code/backend")))

        val context = resolver("/home/dev/code/backend").getOrThrow()!!

        assertEquals("backend", context.alias)
    }

    @Test
    fun `a stale registry entry is ignored`() = runTest {
        val source = FakeMultirepoSource(known = listOf("/home/dev/gone"))
        val resolver = ResolveMultirepoUseCase(source, world.git, world.fileManager)

        assertNull(resolver(world.appBase).getOrThrow())
    }

    @Test
    fun `without any known multirepo it doesn't ask git`() = runTest {
        val git = FakeGitRepositories()
        val resolver = ResolveMultirepoUseCase(FakeMultirepoSource(), git, FakeFileManager())

        assertNull(resolver("/home/dev/projects/main-repo").getOrThrow())
        assertTrue(git.calls.isEmpty())
    }

    @Test
    fun `an unknown default alias is rejected naming it and the file`() = runTest {
        world.source.declarations[world.folder] = world.declaration.copy(defaultRepos = listOf("web"))

        val error = world.resolver(world.folder).exceptionOrNull()

        assertIs<WorktreeError.InvalidMultirepo>(error)
        assertTrue(error.detail.contains("'web'"))
        assertTrue(error.path.endsWith("product/.shogunai-multirepo.json"))
    }

    @Test
    fun `a missing repository is rejected naming its alias and path`() = runTest {
        world.source.declarations[world.folder] = world.declaration.copy(repos = world.declaration.repos + ("web" to "product-web"))

        val error = assertIs<WorktreeError.InvalidMultirepo>(world.resolver(world.folder).exceptionOrNull())

        assertTrue(error.detail.contains("'web'") && error.detail.contains("product-web"))
    }

    @Test
    fun `an invalid alias is rejected`() = runTest {
        world.source.declarations[world.folder] = world.declaration.copy(repos = mapOf("App" to "product-app"), defaultRepos = emptyList())

        val error = assertIs<WorktreeError.InvalidMultirepo>(world.resolver(world.folder).exceptionOrNull())

        assertTrue(error.detail.contains("lowercase"))
    }

    @Test
    fun `a declaration inside a repository is rejected but one at a repository's top level is valid`() = runTest {
        world.source.declarations["${world.appBase}/sub"] = MultirepoDeclaration(worktreesRoot = "../w")
        assertIs<WorktreeError.InvalidMultirepo>(world.resolver("${world.appBase}/sub").exceptionOrNull())

        world.git.repository(world.folder)
        val context = world.resolver(world.folder).getOrThrow()!!
        assertEquals(world.folder, context.repositoryPath)
        assertTrue(context.atFolder)
    }

    @Test
    fun `a linked worktree of the workspace repository isn't the multirepo folder`() = runTest {
        val workspace = world.git.repository(world.folder)
        with(world.git) { workspace.worktree("${world.worktreesRoot}/workspace/TASK-1", "feature/TASK-1") }

        assertNull(world.resolver("${world.worktreesRoot}/workspace/TASK-1").getOrThrow())

        world.source.declarations[world.folder] = world.declaration.copy(repos = world.declaration.repos + ("workspace" to "."))
        val declared = world.resolver("${world.worktreesRoot}/workspace/TASK-1").getOrThrow()!!
        assertEquals("workspace", declared.alias)
        assertFalse(declared.atFolder)
    }

    @Test
    fun `walking up reaches the filesystem root without reading a relative path`() = runTest {
        val source = FakeMultirepoSource(mapOf("" to world.declaration))
        val resolver = ResolveMultirepoUseCase(source, world.git, world.fileManager)

        assertNull(resolver("/home/dev/elsewhere").getOrThrow())
    }
}
