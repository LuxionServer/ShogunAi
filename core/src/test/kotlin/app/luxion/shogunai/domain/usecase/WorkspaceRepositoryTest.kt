package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeRepositoryProvisioningSource
import app.luxion.shogunai.domain.model.BranchType
import app.luxion.shogunai.domain.model.Multirepo
import app.luxion.shogunai.domain.model.MultirepoDeclaration
import app.luxion.shogunai.domain.model.SharedVariablesDeclaration
import app.luxion.shogunai.domain.model.WorkspaceSetting
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkspaceRepositoryTest {

    private val world = MultirepoTestWorld()

    @Test
    fun `a folder that is a repository is detected as workspace and stays out of the default repositories`() {
        world.makeFolderARepository()

        val multirepo = world.resolved()

        assertEquals("workspace", multirepo.workspaceRepository?.alias)
        assertEquals(world.folder, multirepo.workspaceRepository?.path)
        assertTrue(multirepo.workspaceDetected)
        assertEquals("workspace", multirepo.aliases.last())
        assertEquals(listOf("app", "api"), multirepo.defaultRepos)
        assertEquals("${world.worktreesRoot}/workspace", multirepo.proposedWorktreesFolder("workspace"))
    }

    @Test
    fun `a declared alias and default put the workspace in the default repositories`() {
        world.makeFolderARepository()

        val multirepo = world.resolved(WorkspaceSetting(alias = "docs-root", default = true))

        assertEquals("docs-root", multirepo.workspaceRepository?.alias)
        assertFalse(multirepo.workspaceDetected)
        assertEquals(listOf("app", "api", "docs-root"), multirepo.defaultRepos)
    }

    @Test
    fun `turning detection off removes the workspace`() {
        world.makeFolderARepository()

        val multirepo = world.resolved(WorkspaceSetting(enabled = false))

        assertNull(multirepo.workspaceRepository)
        assertFalse("workspace" in multirepo.aliases)
    }

    @Test
    fun `a folder that is not a repository has no workspace whatever the field says`() {
        val multirepo = world.resolved(WorkspaceSetting(alias = "docs-root"))

        assertNull(multirepo.workspaceRepository)
    }

    @Test
    fun `a repos entry with the path dot is the workspace and is not added twice`() {
        world.makeFolderARepository()
        val declaration = world.declaration.copy(repos = world.declaration.repos + ("root" to "."))

        val multirepo = world.resolver.build(world.folder, declaration)

        assertEquals("root", multirepo.workspaceRepository?.alias)
        assertFalse(multirepo.workspaceDetected)
        assertEquals(1, multirepo.repositories.count { it.path == world.folder })
    }

    @Test
    fun `an invalid or colliding workspace alias is rejected with the file named`() = runTest {
        world.makeFolderARepository()

        listOf("Bad_Alias", "app").forEach { alias ->
            val error = runCatching { world.resolver.validate(world.resolved(WorkspaceSetting(alias = alias))) }.exceptionOrNull()
            assertIs<WorktreeError.InvalidMultirepo>(error)
            assertTrue(error.path.endsWith(".shogunai-multirepo.json"))
        }
    }

    @Test
    fun `a detected workspace validates and creates its worktree in the aliased folder`() = runTest {
        val workspace = world.makeFolderARepository()
        val multirepo = world.resolved()
        world.resolver.validate(multirepo)
        val members = multirepoMembers(multirepo, world.projects.list())

        val creation = CreateGroupUseCase(world.git, world.fileManager, FakeRepositoryProvisioningSource())(
            members.filter { it.alias in listOf("workspace", "app") },
            "TASK-12",
            BranchType.FEATURE,
        ).getOrThrow()

        assertEquals(listOf("${world.worktreesRoot}/app/TASK-12", "${world.worktreesRoot}/workspace/TASK-12"), creation.repositories.map { it.path })
        assertTrue(world.git.ranIn(workspace.base, "git", "worktree", "add", "--no-track", "${world.worktreesRoot}/workspace/TASK-12", "-b", "feature/TASK-12", "origin/develop"))
    }

    @Test
    fun `setWorkspace and clearWorkspace only change the workspace field`() = runTest {
        world.makeFolderARepository()
        val configure = ConfigureMultirepoUseCase(world.source, world.resolver, world.projects, world.git)

        val declared = configure.setWorkspace(world.resolved(), null, default = true).getOrThrow()
        assertEquals(world.declaration.copy(workspace = WorkspaceSetting(default = true)), world.source.declarations[world.folder])
        assertFalse(declared.workspaceDetected)

        configure.clearWorkspace(declared).getOrThrow()
        assertEquals(world.declaration.copy(workspace = WorkspaceSetting(enabled = false)), world.source.declarations[world.folder])
    }

    @Test
    fun `setWorkspace refuses a folder that is not a repository and changes nothing`() = runTest {
        val configure = ConfigureMultirepoUseCase(world.source, world.resolver, world.projects, world.git)

        val error = configure.setWorkspace(world.resolved(), null, default = false).exceptionOrNull()

        assertIs<WorktreeError.InvalidMultirepo>(error)
        assertEquals(world.declaration, world.source.declarations[world.folder])
    }

    @Test
    fun `an invalid alias is refused when declaring`() = runTest {
        world.makeFolderARepository()
        val configure = ConfigureMultirepoUseCase(world.source, world.resolver, world.projects, world.git)

        assertIs<WorktreeError.InvalidMultirepo>(configure.setWorkspace(world.resolved(), "app", default = false).exceptionOrNull())
        assertIs<WorktreeError.InvalidMultirepo>(configure.setWorkspace(world.resolved(), "No Good", default = false).exceptionOrNull())
    }

    @Test
    fun `registering the folder of a multirepo with a workspace keeps it under the multirepo's worktrees folder`() = runTest {
        world.makeFolderARepository()
        val projects = app.luxion.shogunai.domain.FakeProjectRepository()
        val register = RegisterProjectUseCase(projects, ResolveRepositoryUseCase(world.git), { "ws" }, listOf(world.resolved())::withWorkspaceAt)

        val first = register(world.folder).getOrThrow()
        val second = register(world.folder).getOrThrow()

        assertIs<ProjectRegistration.Added>(first)
        assertIs<ProjectRegistration.AlreadyRegistered>(second)
        assertEquals("${world.worktreesRoot}/workspace", first.project.config.worktreesRoot)
        assertEquals(1, projects.list().size)
    }

    @Test
    fun `workspace false registers the folder as an ordinary project`() = runTest {
        world.makeFolderARepository()
        val projects = app.luxion.shogunai.domain.FakeProjectRepository()
        val multirepo = world.resolved(WorkspaceSetting(enabled = false))
        val register = RegisterProjectUseCase(projects, ResolveRepositoryUseCase(world.git), { "ws" }, listOf(multirepo)::withWorkspaceAt)

        val added = register(world.folder).getOrThrow()

        assertEquals("/home/dev/projects/product-worktrees", added.project.config.worktreesRoot)
    }

    @Test
    fun `defaults naming the workspace alias set its default flag and keep the ordinary ones`() = runTest {
        world.makeFolderARepository()
        val configure = ConfigureMultirepoUseCase(world.source, world.resolver, world.projects, world.git)

        val updated = configure.setDefaults(world.resolved(), listOf("workspace", "app")).getOrThrow()

        assertEquals(listOf("app"), world.source.declarations[world.folder]!!.defaultRepos)
        assertEquals(WorkspaceSetting(default = true), world.source.declarations[world.folder]!!.workspace)
        assertEquals(listOf("app", "workspace"), updated.defaultRepos)

        configure.setDefaults(updated, listOf("app")).getOrThrow()

        assertEquals(WorkspaceSetting(default = false), world.source.declarations[world.folder]!!.workspace)
    }

    @Test
    fun `defaults leave a detected workspace undeclared when it isn't named`() = runTest {
        world.makeFolderARepository()
        val configure = ConfigureMultirepoUseCase(world.source, world.resolver, world.projects, world.git)

        configure.setDefaults(world.resolved(), listOf("api")).getOrThrow()

        assertNull(world.source.declarations[world.folder]!!.workspace)
    }

    private val configure = ConfigureMultirepoUseCase(world.source, world.resolver, world.projects, world.git)

    private fun storing(declaration: MultirepoDeclaration): Multirepo {
        world.source.declarations[world.folder] = declaration
        return world.resolver.build(world.folder, declaration)
    }

    private fun stored() = world.source.declarations.getValue(world.folder)

    @Test
    fun `detect removes a turned off workspace field and keeps the multirepo approved`() = runTest {
        world.makeFolderARepository()
        world.source.approveAll = false
        val off = storing(world.declaration.copy(workspace = WorkspaceSetting(enabled = false)))
        world.source.approve(world.folder, off.approvalSubject)

        val detected = configure.detectWorkspace(world.resolver.build(world.folder, stored())).getOrThrow()

        assertEquals(world.declaration, stored())
        assertTrue(detected.workspaceDetected)
        assertEquals("workspace", detected.workspaceRepository?.alias)
        assertTrue(world.resolver.build(world.folder, stored()).approved)
    }

    @Test
    fun `detect from a declared alias leaves the defaults and renames its shared variables`() = runTest {
        world.makeFolderARepository()
        val declared = storing(
            world.declaration.copy(
                workspace = WorkspaceSetting(alias = "docs-root", default = true),
                env = SharedVariablesDeclaration(mapOf("app" to mapOf("DOCS" to "${'$'}{port:docs-root.http}"), "docs-root" to mapOf("A" to "x"))),
            ),
        )
        assertTrue("docs-root" in declared.defaultRepos)

        val detected = configure.detectWorkspace(declared).getOrThrow()

        assertNull(stored().workspace)
        assertFalse("workspace" in detected.defaultRepos)
        assertEquals(mapOf("app" to mapOf("DOCS" to "${'$'}{port:workspace.http}"), "workspace" to mapOf("A" to "x")), stored().env?.byAlias)
    }

    @Test
    fun `detect when already detected changes nothing`() = runTest {
        world.makeFolderARepository()
        val detected = storing(world.declaration)

        assertEquals(detected, configure.detectWorkspace(detected).getOrThrow())
        assertEquals(world.declaration, stored())
    }

    @Test
    fun `detect is refused without a repository, with the folder in repos or with the alias taken`() = runTest {
        val notRepository = storing(world.declaration.copy(workspace = WorkspaceSetting(enabled = false)))
        assertIs<WorktreeError.InvalidMultirepo>(configure.detectWorkspace(notRepository).exceptionOrNull())

        world.makeFolderARepository()
        val inRepos = storing(world.declaration.copy(repos = world.declaration.repos + ("root" to "."), workspace = WorkspaceSetting(enabled = false)))
        assertIs<WorktreeError.InvalidMultirepo>(configure.detectWorkspace(inRepos).exceptionOrNull())

        val taken = storing(world.declaration.copy(repos = world.declaration.repos + ("workspace" to "product-docs"), workspace = WorkspaceSetting(alias = "root")))
        val error = configure.detectWorkspace(taken).exceptionOrNull()
        assertIs<WorktreeError.InvalidMultirepo>(error)
        assertTrue(error.detail.contains("'workspace' is already taken"))
        assertEquals(WorkspaceSetting(alias = "root"), stored().workspace)
    }

    @Test
    fun `declaring the workspace under another alias renames its shared variables`() = runTest {
        world.makeFolderARepository()
        val detected = storing(world.declaration.copy(env = SharedVariablesDeclaration(mapOf("app" to mapOf("DOCS" to "${'$'}{port:workspace.http}")))))

        configure.setWorkspace(detected, "docs-root", default = false).getOrThrow()

        assertEquals(mapOf("app" to mapOf("DOCS" to "${'$'}{port:docs-root.http}")), stored().env?.byAlias)
    }

    @Test
    fun `turning off a referenced workspace is refused and an unreferenced one drops its variables`() = runTest {
        world.makeFolderARepository()
        val referenced = storing(world.declaration.copy(env = SharedVariablesDeclaration(mapOf("app" to mapOf("DOCS" to "${'$'}{port:workspace.http}")))))

        val error = configure.clearWorkspace(referenced).exceptionOrNull()
        assertIs<WorktreeError.InvalidMultirepo>(error)
        assertTrue(error.detail.contains("app.DOCS"))

        val own = storing(world.declaration.copy(env = SharedVariablesDeclaration(mapOf("workspace" to mapOf("A" to "x")))))
        configure.clearWorkspace(own).getOrThrow()
        assertNull(stored().env)
        assertEquals(WorkspaceSetting(enabled = false), stored().workspace)
    }
}
