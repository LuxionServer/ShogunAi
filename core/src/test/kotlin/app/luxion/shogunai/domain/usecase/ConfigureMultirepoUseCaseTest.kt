package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.model.AgentLaunchConfig
import app.luxion.shogunai.domain.model.Multirepo
import app.luxion.shogunai.domain.model.MultirepoDeclaration
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConfigureMultirepoUseCaseTest {

    private val world = MultirepoTestWorld()
    private val configure = ConfigureMultirepoUseCase(world.source, world.resolver, world.projects, world.git, newId = { "new-id" })
    private val empty = Multirepo(world.folder, MultirepoDeclaration(worktreesRoot = "../product-worktrees"))
    private val asked = mutableListOf<Triple<String, String?, String>>()
    private fun answering(answer: Boolean) = WorktreesFolderDecision { project, current, proposed ->
        asked += Triple(project, current, proposed)
        answer
    }

    @Test
    fun `init writes an empty declaration with the default worktrees root and remembers it`() = runTest {
        world.source.declarations.clear()
        world.source.known.clear()

        val multirepo = configure.init(world.folder, null).getOrThrow()

        assertEquals(MultirepoDeclaration(worktreesRoot = "../product-worktrees"), world.source.declarations[world.folder])
        assertEquals(listOf(world.folder), world.source.known)
        assertEquals(world.worktreesRoot, multirepo.worktreesRoot)
    }

    @Test
    fun `init refuses a folder inside a repository and an existing multirepo`() = runTest {
        assertIs<WorktreeError.InvalidMultirepo>(configure.init("${world.appBase}/docs", null).exceptionOrNull())
        assertIs<WorktreeError.InvalidMultirepo>(configure.init(world.folder, null).exceptionOrNull())
    }

    @Test
    fun `init accepts the top level of a workspace repository`() = runTest {
        world.source.declarations.clear()
        world.git.repository(world.folder)

        assertTrue(configure.init(world.folder, "/home/dev/projects/elsewhere").isSuccess)
        assertEquals("../elsewhere", world.source.declarations[world.folder]?.worktreesRoot)
    }

    @Test
    fun `a registered project enters unchanged when the proposal is declined`() = runTest {
        val before = world.appProject.copy(config = world.appProject.config.copy(worktreesRoot = "/home/dev/projects/product-app-worktrees", mainBranch = "develop", secretFiles = listOf("local.properties")))
        world.projects.save(before)

        val added = configure.addRepository(empty, "app", world.appBase, answering(false)).getOrThrow()

        assertEquals(before, world.projects.list().first { it.id == "app" })
        assertEquals(WorktreesFolderProposal.DECLINED, added.worktreesFolder)
        assertFalse(added.registered)
        assertEquals(listOf(Triple<String, String?, String>("product-app", "/home/dev/projects/product-app-worktrees", "${world.worktreesRoot}/app")), asked.toList())
        assertEquals(mapOf("app" to "product-app"), world.source.declarations[world.folder]?.repos)
    }

    @Test
    fun `accepting the proposal only changes the project's worktrees folder`() = runTest {
        world.projects.save(world.appProject.copy(config = world.appProject.config.copy(worktreesRoot = "/home/dev/old")))

        configure.addRepository(empty, "app", world.appBase, answering(true)).getOrThrow()

        assertEquals(world.appProject.config.copy(worktreesRoot = "${world.worktreesRoot}/app"), world.projects.list().first { it.id == "app" }.config)
    }

    @Test
    fun `a folder that already matches isn't asked about`() = runTest {
        val added = configure.addRepository(empty, "app", world.appBase, answering(true)).getOrThrow()

        assertEquals(WorktreesFolderProposal.ALREADY_MATCHES, added.worktreesFolder)
        assertTrue(asked.isEmpty())
    }

    @Test
    fun `a repository without a project is registered with the app's defaults or the proposal`() = runTest {
        world.projects.delete("api")

        val declined = configure.addRepository(empty, "api", world.apiBase, answering(false)).getOrThrow()

        assertTrue(declined.registered)
        assertEquals("product-api", declined.project.name)
        assertEquals("${world.folder}/product-api-worktrees", declined.project.config.worktreesRoot)
        assertEquals("new-id", declined.project.id)

        world.projects.delete("new-id")
        val accepted = configure.addRepository(empty, "api2", world.apiBase, answering(true)).getOrThrow()
        assertEquals("${world.worktreesRoot}/api2", accepted.project.config.worktreesRoot)
    }

    @Test
    fun `an outside repository is stored with an absolute path`() = runTest {
        world.git.repository("/src/backend")

        configure.addRepository(empty, "backend", "/src/backend", answering(false)).getOrThrow()

        assertEquals("/src/backend", world.source.declarations[world.folder]?.repos?.get("backend"))
    }

    @Test
    fun `invalid additions change nothing`() = runTest {
        val existing = Multirepo(world.folder, world.declaration)
        val before = world.source.declarations[world.folder]

        assertIs<WorktreeError.InvalidMultirepo>(configure.addRepository(existing, "app", world.appBase, answering(false)).exceptionOrNull())
        assertIs<WorktreeError.InvalidMultirepo>(configure.addRepository(empty, "App", world.appBase, answering(false)).exceptionOrNull())
        assertIs<WorktreeError.InvalidMultirepo>(configure.addRepository(empty, "x", "/home/dev/not-a-repo", answering(false)).exceptionOrNull())
        assertEquals(before, world.source.declarations[world.folder])
    }

    @Test
    fun `a repository of another multirepo is refused`() = runTest {
        world.source.declarations["/home/dev/other"] = MultirepoDeclaration(mapOf("app" to world.appBase), "../w")
        world.source.known += "/home/dev/other"

        val error = assertIs<WorktreeError.InvalidMultirepo>(configure.addRepository(empty, "app", world.appBase, answering(false)).exceptionOrNull())

        assertTrue(error.detail.contains("/home/dev/other"))
    }

    @Test
    fun `removing a repository drops it from the defaults and keeps its project`() = runTest {
        val updated = configure.removeRepository(world.multirepo, "api").getOrThrow()

        assertEquals(listOf("app", "docs"), updated.aliases)
        assertEquals(listOf("app"), updated.declaration.defaultRepos)
        assertTrue(world.projects.list().any { it.id == "api" })
    }

    @Test
    fun `renaming a repository keeps its path and its place in the defaults`() = runTest {
        val path = world.multirepo.declaration.repos.getValue("api")

        val renamed = configure.renameRepository(world.multirepo, "api", "backend").getOrThrow()

        assertEquals(listOf("app", "backend", "docs"), renamed.aliases)
        assertEquals(path, renamed.declaration.repos.getValue("backend"))
        assertEquals(listOf("app", "backend"), renamed.declaration.defaultRepos)
        assertEquals(renamed.declaration, world.source.declarations.getValue(world.folder))
        assertIs<WorktreeError.InvalidMultirepo>(configure.renameRepository(renamed, "backend", "app").exceptionOrNull())
        assertIs<WorktreeError.InvalidMultirepo>(configure.renameRepository(renamed, "backend", "Back End").exceptionOrNull())
        assertIs<UnknownMultirepoAlias>(configure.renameRepository(renamed, "api", "web").exceptionOrNull())
    }

    @Test
    fun `defaults and agent are validated and saved`() = runTest {
        assertIs<UnknownMultirepoAlias>(configure.setDefaults(world.multirepo, listOf("app", "web")).exceptionOrNull())

        configure.setDefaults(world.multirepo, listOf("docs")).getOrThrow()
        val withAgent = configure.setAgent(Multirepo(world.folder, world.source.declarations.getValue(world.folder)), AgentLaunchConfig("Codex", "codex --full-auto")).getOrThrow()

        assertEquals(listOf("docs"), withAgent.declaration.defaultRepos)
        assertEquals("codex --full-auto", withAgent.agent.resolvedCommand())
        assertEquals("Codex", withAgent.agent.displayName)
    }

    @Test
    fun `delete forgets the multirepo and leaves the projects`() = runTest {
        configure.delete(world.multirepo).getOrThrow()

        assertNull(world.source.declarations[world.folder])
        assertTrue(world.source.known.isEmpty())
        assertEquals(3, world.projects.list().size)
    }
}
