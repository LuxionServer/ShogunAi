package app.luxion.shogunai.ui.multirepo

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import app.luxion.shogunai.domain.model.AgentLaunchConfig
import app.luxion.shogunai.domain.model.Multirepo
import app.luxion.shogunai.domain.model.MultirepoDeclaration
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.WorkspaceSetting
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.usecase.AddedRepository
import app.luxion.shogunai.domain.usecase.GroupWorktree
import app.luxion.shogunai.domain.usecase.MultirepoMember
import app.luxion.shogunai.domain.usecase.WorktreeGroup
import app.luxion.shogunai.domain.usecase.WorktreesFolderProposal
import app.luxion.shogunai.ui.projectconfig.AgentDraft
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FakeMultirepoSetup(
    val repositories: Set<String> = emptySet(),
    val enclosing: Map<String, String> = emptyMap(),
    val folders: Map<String, List<String>> = emptyMap(),
    val worktrees: Map<String, List<String>> = emptyMap(),
    projects: List<Project> = emptyList(),
) : MultirepoSetup {
    val declarations = mutableMapOf<String, MultirepoDeclaration>()
    val savedProjects = projects.toMutableList()
    val deleted = mutableListOf<String>()

    override suspend fun inspect(folder: String) = FolderInspection(enclosing[folder], folders[folder].orEmpty().filter { it in repositories })
    override suspend fun isRepository(path: String) = path in repositories
    override suspend fun belongsToRepository(path: String) = path in repositories || path in enclosing
    override suspend fun detectGroups(members: List<MultirepoMember>): List<WorktreeGroup> =
        members.flatMap { member -> worktrees[member.config.baseRepositoryPath].orEmpty().map { member to it } }
            .groupBy { it.second }
            .filterValues { it.size >= 2 }
            .map { (branch, owners) -> WorktreeGroup(branch, owners.map { (member, _) -> GroupWorktree(member.alias, member.config, Worktree("/w/${member.alias}", branch)) }) }

    override fun projects() = savedProjects.toList()
    override fun read(folder: String) = declarations[folder]?.let { Multirepo(folder, it, folderIsRepository = folder in repositories) }
    override suspend fun init(folder: String): Result<Multirepo> {
        declarations[folder] = MultirepoDeclaration(worktreesRoot = "../${Multirepo(folder, MultirepoDeclaration()).name}-worktrees")
        return Result.success(read(folder)!!)
    }

    override suspend fun addRepository(multirepo: Multirepo, alias: String, path: String, useProposedFolder: Boolean): Result<AddedRepository> {
        val existing = savedProjects.firstOrNull { it.config.baseRepositoryPath == path }
        val folder = multirepo.proposedWorktreesFolder(alias)
        val project = when {
            existing == null -> Project(alias, path.substringAfterLast('/'), ProjectConfig(path, if (useProposedFolder) folder else "$path-worktrees", emptyList()))
                .also { savedProjects += it }
            useProposedFolder -> existing.copy(config = existing.config.copy(worktreesRoot = folder)).also { moved ->
                savedProjects.replaceAll { if (it.id == moved.id) moved else it }
            }
            else -> existing
        }
        declarations[multirepo.folder] = declarations.getValue(multirepo.folder).let { it.copy(repos = it.repos + (alias to path)) }
        val proposal = if (useProposedFolder) WorktreesFolderProposal.ACCEPTED else WorktreesFolderProposal.DECLINED
        return Result.success(AddedRepository(alias, path, project, existing == null, proposal))
    }

    private fun update(multirepo: Multirepo, change: (MultirepoDeclaration) -> MultirepoDeclaration): Result<Multirepo> {
        declarations[multirepo.folder] = change(declarations.getValue(multirepo.folder))
        return Result.success(read(multirepo.folder)!!)
    }

    override fun removeRepository(multirepo: Multirepo, alias: String) =
        update(multirepo) { it.copy(repos = it.repos - alias, defaultRepos = it.defaultRepos - alias) }

    override fun renameRepository(multirepo: Multirepo, alias: String, newAlias: String) =
        update(multirepo) { declaration ->
            declaration.copy(
                repos = declaration.repos.entries.associate { (key, path) -> (if (key == alias) newAlias else key) to path },
                defaultRepos = declaration.defaultRepos.map { if (it == alias) newAlias else it },
            )
        }

    override fun setDefaults(multirepo: Multirepo, aliases: List<String>) = update(multirepo) { declaration ->
        val workspace = multirepo.workspaceRepository?.takeIf { it.alias !in declaration.repos }
        val setting = when {
            workspace == null -> declaration.workspace
            workspace.alias in aliases -> WorkspaceSetting(alias = workspace.alias, default = true)
            else -> declaration.workspace?.copy(default = false)
        }
        declaration.copy(defaultRepos = aliases.filter { it in declaration.repos }, workspace = setting)
    }

    override fun setWorkspace(multirepo: Multirepo, alias: String, default: Boolean) =
        update(multirepo) { it.copy(workspace = WorkspaceSetting(alias = alias, default = default)) }

    override fun clearWorkspace(multirepo: Multirepo) = update(multirepo) { it.copy(workspace = WorkspaceSetting(enabled = false)) }
    override fun setAgent(multirepo: Multirepo, agent: AgentLaunchConfig) = update(multirepo) { it.copy(agent = agent) }
    override fun setWorktreesRoot(multirepo: Multirepo, folder: String) = update(multirepo) { it.copy(worktreesRoot = folder) }
    override fun delete(multirepo: Multirepo): Result<Unit> {
        declarations.remove(multirepo.folder)
        deleted += multirepo.folder
        return Result.success(Unit)
    }
}

@OptIn(ExperimentalTestApi::class)
class MultirepoSetupTest {

    private val product = "/home/dev/projects/product"
    private val app = "$product/product-app"
    private val api = "$product/product-api"
    private val docs = "$product/product-docs"
    private val appProject = Project("app-id", "product-app", ProjectConfig(app, "/home/dev/projects/product-app-worktrees", emptyList()))

    private fun productSetup() = FakeMultirepoSetup(
        repositories = setOf(app, api, docs, "/home/dev/elsewhere/tools"),
        folders = mapOf(product to listOf(api, app, docs, "$product/notes")),
        worktrees = mapOf(app to listOf("feature/TASK-7"), api to listOf("feature/TASK-7")),
        projects = listOf(appProject),
    )

    private fun TestScope.chosen(setup: FakeMultirepoSetup): AddMultirepoState =
        AddMultirepoState(setup, this).also {
            it.choose(product)
            advanceUntilIdle()
        }

    @Test
    fun `the dialog lists the repositories of the folder with proposed aliases and groups`() = runTest {
        val state = chosen(productSetup())

        assertEquals(listOf("api", "app", "docs"), state.candidates.map { it.alias })
        assertEquals(appProject, state.candidates.single { it.alias == "app" }.project)
        assertFalse(state.candidates.single { it.alias == "app" }.useProposedFolder)
        assertTrue(state.candidates.single { it.alias == "api" }.useProposedFolder)
        assertEquals("/home/dev/projects/product-worktrees/api", state.proposedFolder(state.candidates.single { it.alias == "api" }))
        assertEquals(listOf(DetectedGroup("feature/TASK-7", listOf("api", "app"))), state.groups)
        assertTrue(state.canConfirm)
    }

    @Test
    fun `confirming declares the kept repositories and leaves an existing project unchanged`() = runTest {
        val setup = productSetup()
        val state = chosen(setup)
        state.setIncluded(docs, false)
        var added: String? = null

        state.confirm { added = it }
        advanceUntilIdle()

        assertEquals(product, added)
        val declaration = setup.declarations.getValue(product)
        assertEquals(mapOf("api" to api, "app" to app), declaration.repos)
        assertEquals(listOf("api", "app"), declaration.defaultRepos)
        assertEquals(appProject, setup.savedProjects.single { it.config.baseRepositoryPath == app })
        assertEquals("/home/dev/projects/product-worktrees/api", setup.savedProjects.single { it.config.baseRepositoryPath == api }.config.worktreesRoot)
    }

    @Test
    fun `a folder inside a repository offers no confirmation`() = runTest {
        val state = chosen(FakeMultirepoSetup(enclosing = mapOf(product to "/home/dev/projects")))

        assertEquals("/home/dev/projects", state.enclosingRepository)
        assertFalse(state.canConfirm)
    }

    @Test
    fun `duplicated or invalid aliases block the confirmation`() = runTest {
        val state = chosen(productSetup())

        state.setAlias(api, "app")
        assertEquals(AliasProblem.DUPLICATED, state.aliasProblems[api])
        state.setAlias(api, "Back End")
        assertEquals(AliasProblem.INVALID, state.aliasProblems[api])
        assertFalse(state.canConfirm)
    }

    @Test
    fun `a repository from elsewhere can be added and a plain folder can't`() = runTest {
        val state = chosen(productSetup())

        state.addFromElsewhere("/home/dev/elsewhere/tools")
        state.addFromElsewhere("/home/dev/elsewhere/notes")
        advanceUntilIdle()

        assertTrue(state.candidates.any { it.alias == "tools" })
        assertEquals("/home/dev/elsewhere/notes", state.notRepository)
    }

    @Test
    fun `the add dialog explains a folder inside a repository`() = runComposeUiTest {
        val state = AddMultirepoState(FakeMultirepoSetup(enclosing = mapOf(product to "/home/dev/projects")), CoroutineScope(Dispatchers.Unconfined))
        state.choose(product)
        setContent { MaterialTheme { AddMultirepoDialog(state, onDismiss = {}, onAdded = {}, pickFolder = { _, _ -> null }) } }

        onNode(hasText("can't be declared inside a repository", substring = true)).assertIsDisplayed()
        assertEquals(2, onAllNodesWithText("Add multirepo").fetchSemanticsNodes().size)
    }

    private fun settings(setup: FakeMultirepoSetup, scope: TestScope, changes: MutableList<Unit> = mutableListOf()) =
        MultirepoSettingsState(product, setup, scope, onChanged = { changes += Unit })

    private fun declaredSetup() = productSetup().apply {
        declarations[product] = MultirepoDeclaration(mapOf("app" to app, "api" to api), "../product-worktrees", listOf("app", "api"))
    }

    @Test
    fun `unchecking a default repository and saving updates the declaration`() = runTest {
        val setup = declaredSetup()
        val changes = mutableListOf<Unit>()
        val state = settings(setup, this, changes)

        state.toggleDefault("api")
        assertTrue(state.canSave)
        state.save()

        assertEquals(listOf("app"), setup.declarations.getValue(product).defaultRepos)
        assertFalse(state.hasChanges)
        assertEquals(1, changes.size)
    }

    @Test
    fun `the multirepo agent is edited with the agent fields`() = runTest {
        val setup = declaredSetup()
        val state = settings(setup, this)

        state.agent = AgentDraft(name = "Codex", command = "codex --full-auto", useHeadroom = false)
        state.save()

        assertEquals(AgentLaunchConfig("Codex", "codex --full-auto", false), setup.declarations.getValue(product).agent)
    }

    @Test
    fun `repositories are added, renamed and removed from the settings`() = runTest {
        val setup = declaredSetup()
        val state = settings(setup, this)

        state.chooseNewPath(docs)
        assertEquals("docs", state.newAlias)
        state.addRepository()
        advanceUntilIdle()
        state.renameRepository("api", "backend")
        state.removeRepository("app")

        assertEquals(mapOf("backend" to api, "docs" to docs), setup.declarations.getValue(product).repos)
        assertEquals(listOf("backend", "docs"), state.aliases)
    }

    @Test
    fun `deleting the multirepo removes only its declaration`() = runTest {
        val setup = declaredSetup()
        val state = settings(setup, this)
        var deleted = false

        state.requestDelete()
        state.confirmDelete { deleted = true }

        assertTrue(deleted)
        assertNull(setup.declarations[product])
        assertEquals(listOf(appProject), setup.savedProjects)
    }

    @Test
    fun `the add dialog lists repositories and detected groups`() = runComposeUiTest {
        val state = AddMultirepoState(productSetup(), CoroutineScope(Dispatchers.Unconfined))
        state.choose(product)
        setContent { MaterialTheme { AddMultirepoDialog(state, onDismiss = {}, onAdded = {}, pickFolder = { _, _ -> null }) } }

        onNode(hasText("product-app · already a project")).assertIsDisplayed()
        onNode(hasText("Worktrees in /home/dev/projects/product-worktrees/api")).assertIsDisplayed()
        onNode(hasText("feature/TASK-7 · api, app")).assertIsDisplayed()
        assertTrue(onAllNodesWithText("notes").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `nothing can be confirmed before choosing a folder`() = runComposeUiTest {
        val state = AddMultirepoState(productSetup(), CoroutineScope(Dispatchers.Unconfined))
        setContent { MaterialTheme { AddMultirepoDialog(state, onDismiss = {}, onAdded = {}, pickFolder = { _, _ -> null }) } }

        onNodeWithText("No folder chosen").assertIsDisplayed()
        onAllNodesWithText("Add multirepo")[1].assertIsNotEnabled()
    }

    @Test
    fun `a detected workspace is marked and declaring it saves the alias`() = runTest {
        val setup = FakeMultirepoSetup(repositories = setOf(app, api, product), projects = listOf(appProject)).apply {
            declarations[product] = MultirepoDeclaration(mapOf("app" to app, "api" to api), "../product-worktrees", listOf("app", "api"))
        }
        val state = settings(setup, this)

        assertTrue(state.hasWorkspaceSection)
        assertTrue(state.workspaceDetected)
        assertFalse(state.canSave)

        state.workspaceDeclared = true
        state.workspaceAlias = "docs-root"
        state.toggleDefault("docs-root")
        assertTrue(state.canSave)
        state.save()

        assertEquals(WorkspaceSetting(alias = "docs-root", default = true), setup.declarations.getValue(product).workspace)
        assertFalse(state.workspaceDetected)
        assertFalse(state.hasChanges)
    }

    @Test
    fun `no workspace section when the folder is not a repository`() = runTest {
        val state = settings(declaredSetup(), this)

        assertFalse(state.hasWorkspaceSection)
    }

    @Test
    fun `turning the workspace off clears detection`() = runTest {
        val setup = FakeMultirepoSetup(repositories = setOf(app, api, product), projects = listOf(appProject)).apply {
            declarations[product] = MultirepoDeclaration(mapOf("app" to app, "api" to api), "../product-worktrees", listOf("app", "api"))
        }
        val state = settings(setup, this)

        state.turnOffWorkspace()

        assertEquals(WorkspaceSetting(enabled = false), setup.declarations.getValue(product).workspace)
        assertTrue(state.workspaceOff)
        assertFalse("workspace" in state.aliases)
    }

    @Test
    fun `the new change lists the workspace repository checked when it is a default`() {
        val multirepo = Multirepo(
            product,
            MultirepoDeclaration(mapOf("app" to app), "../product-worktrees", listOf("app"), workspace = WorkspaceSetting(default = true)),
            folderIsRepository = true,
        )
        val workspaceProject = Project("workspace-id", "product", ProjectConfig(product, "/w", emptyList()))

        val membership = multirepoMemberships(listOf(multirepo), listOf(appProject, workspaceProject)).single()
        val change = NewChange(membership)

        assertEquals(listOf("app", "workspace"), change.aliases)
        assertEquals(setOf("app", "workspace"), change.selected)
    }
}
