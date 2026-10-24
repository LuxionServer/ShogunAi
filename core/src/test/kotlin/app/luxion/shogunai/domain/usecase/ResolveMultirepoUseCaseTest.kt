package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.FakeGitRepositories
import app.luxion.shogunai.domain.FakeMultirepoSource
import app.luxion.shogunai.domain.model.AgentLaunchConfig
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
        world.source.remember("${world.appBase}/sub")
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

    @Test
    fun `a declaration nobody registered is ignored at its folder and below`() = runTest {
        val cloned = "/home/dev/clones/evil"
        world.git.repository(cloned)
        world.source.declarations[cloned] = MultirepoDeclaration(
            worktreesRoot = "../wt",
            agent = AgentLaunchConfig(name = "Claude", agentCommand = "touch /tmp/marker; claude", useHeadroom = false),
        )
        world.source.declarations["/home/dev/downloads"] = MultirepoDeclaration(repos = mapOf("app" to world.appBase), worktreesRoot = "/tmp/w")

        assertNull(world.resolver(cloned).getOrThrow())
        assertNull(world.resolver("$cloned/src").getOrThrow())
        assertNull(world.resolver("/home/dev/downloads/some-repo").getOrThrow())
    }

    @Test
    fun `a registered multirepo that isn't approved doesn't apply`() = runTest {
        world.source.approveAll = false

        assertNull(world.resolver(world.folder).getOrThrow())
        assertNull(world.resolver(world.appBase).getOrThrow())
        assertNull(world.resolver.declaring(world.appBase))
        assertFalse(world.resolver.known().single().approved)
    }

    @Test
    fun `an approved multirepo stops applying when a pull redirects a repository`() = runTest {
        world.source.approveAll = false
        world.source.approve(world.folder, world.resolver.build(world.folder, world.declaration).approvalSubject)
        assertEquals(world.folder, world.resolver(world.folder).getOrThrow()!!.multirepo.folder)

        world.git.repository("/home/dev/other-repo")
        world.fileManager.writeText("/home/dev/other-repo", "")
        world.source.declarations[world.folder] = world.declaration.copy(repos = world.declaration.repos + ("api" to "/home/dev/other-repo"))

        assertNull(world.resolver(world.folder).getOrThrow())
        assertNull(world.resolver("/home/dev/other-repo").getOrThrow())
    }

    @Test
    fun `changing only the default repositories keeps the approval`() = runTest {
        world.source.approveAll = false
        world.source.approve(world.folder, world.resolver.build(world.folder, world.declaration).approvalSubject)

        world.source.declarations[world.folder] = world.declaration.copy(defaultRepos = listOf("docs"))

        assertEquals(listOf("docs"), world.resolver(world.folder).getOrThrow()!!.multirepo.defaultRepos)
    }

    @Test
    fun `the nearest declaration is found whether or not it's registered`() = runTest {
        world.source.declarations["/home/dev/downloads"] = MultirepoDeclaration(worktreesRoot = "../w")

        val nearest = world.resolver.nearestDeclaration("/home/dev/downloads/some-repo/src")!!

        assertEquals("/home/dev/downloads", nearest.folder)
        assertFalse(nearest.approved)
        assertEquals(world.folder, world.resolver.nearestDeclaration("${world.appBase}/src")!!.folder)
        assertNull(world.resolver.nearestDeclaration("/home/dev/elsewhere"))
    }

    @Test
    fun `the agent of a resolved multirepo is the local one`() = runTest {
        world.source.declarations[world.folder] = world.declaration.copy(agent = AgentLaunchConfig(agentCommand = "touch /tmp/marker; claude"))
        assertEquals("headroom wrap claude", world.resolver(world.folder).getOrThrow()!!.multirepo.agent.resolvedCommand())

        world.source.setAgent(world.folder, AgentLaunchConfig(name = "Codex", agentCommand = "codex"))

        assertEquals("Codex", world.resolver(world.folder).getOrThrow()!!.multirepo.agent.displayName)
    }

    @Test
    fun `control characters in a path or alias are rejected without echoing them`() = runTest {
        listOf(
            world.declaration.copy(repos = world.declaration.repos + ("api" to "product-api\u001b[2K\r")),
            world.declaration.copy(repos = world.declaration.repos + ("api" to "product-api\u0000")),
            world.declaration.copy(worktreesRoot = "../w\u0000"),
            world.declaration.copy(worktreesRoot = "../w\u202E"),
            world.declaration.copy(repos = mapOf("app\u001b" to "product-app"), defaultRepos = emptyList()),
        ).forEach { declaration ->
            world.source.declarations[world.folder] = declaration

            val error = assertIs<WorktreeError.InvalidMultirepo>(world.resolver(world.folder).exceptionOrNull())

            assertTrue(error.detail.contains("control characters"))
            assertFalse(error.detail.any { it == '\u001b' || it == '\u202E' || it == '\u0000' })
        }
    }

    @Test
    fun `a path the file system can't represent fails only as an invalid multirepo`() = runTest {
        listOf(
            world.declaration.copy(repos = world.declaration.repos + ("api" to "product-api<>|?*")),
            world.declaration.copy(worktreesRoot = "../w<>|?*"),
        ).forEach { declaration ->
            world.source.declarations[world.folder] = declaration

            val failure = world.resolver(world.folder).exceptionOrNull()

            assertTrue(failure == null || failure is WorktreeError.InvalidMultirepo, failure.toString())
        }
    }
}
