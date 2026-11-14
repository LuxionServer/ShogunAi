package app.luxion.shogunai.ui.shell

import app.luxion.shogunai.domain.FakeShellPreferenceRepository
import app.luxion.shogunai.domain.FakeProjectRepository
import app.luxion.shogunai.domain.FakeToolDetector
import app.luxion.shogunai.domain.FakeToolPreferenceRepository
import app.luxion.shogunai.domain.model.DetectedTools
import app.luxion.shogunai.domain.model.KnownMultirepos
import app.luxion.shogunai.domain.model.Multirepo
import app.luxion.shogunai.domain.model.MultirepoDeclaration
import app.luxion.shogunai.domain.model.MultirepoUnavailability
import app.luxion.shogunai.domain.model.UnavailableMultirepo
import app.luxion.shogunai.domain.model.RunnerId
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.ui.actions.ActionId
import app.luxion.shogunai.ui.machine.MachineProject
import app.luxion.shogunai.ui.machine.MachineResource
import app.luxion.shogunai.ui.machine.MachineResource.ANDROID_EMULATORS
import app.luxion.shogunai.ui.machine.MachineResource.GRADLE_DAEMONS
import app.luxion.shogunai.ui.machine.MachineResource.IOS_SIMULATORS
import app.luxion.shogunai.ui.multirepo.MultirepoCatalog
import app.luxion.shogunai.ui.palette.PaletteAction
import app.luxion.shogunai.ui.palette.PaletteCommand
import app.luxion.shogunai.ui.worktree.FakeWorktreeRepository
import app.luxion.shogunai.ui.worktree.WorktreeRef
import app.luxion.shogunai.ui.worktree.uiBoard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShellActionsTest {

    private val repository = FakeWorktreeRepository()
    private val project = repository.project
    private val board by lazy { uiBoard(listOf(repository)) }
    private val viewModel = ShellViewModel(FakeProjectRepository(listOf(project)), FakeShellPreferenceRepository())
    private val ref = WorktreeRef(project.id, "/home/dev/projects/TASK-123")
    private val calls = mutableListOf<String>()

    @BeforeTest
    fun loadOnTheSpot() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun restoreMain() {
        Dispatchers.resetMain()
    }

    private fun run(action: PaletteAction) =
        runPaletteAction(action, viewModel, board, onOpenPreferences = { calls += "preferences" }, copySelectedPath = { calls += "copy path" }, onCopied = { calls += "copied" })

    private fun machineProject(declares: Set<MachineResource>, worktrees: List<Worktree>? = null) = MachineProject(project, worktrees, declares)

    @Test
    fun `the runner in use is the default one, or Herdr when none is installed`() {
        val orcaOnly = ToolsPreferences(FakeToolPreferenceRepository(), FakeToolDetector(DetectedTools(runners = setOf(RunnerId.ORCA))))
        val none = ToolsPreferences(FakeToolPreferenceRepository(), FakeToolDetector(DetectedTools(runners = emptySet())))
        runBlocking {
            orcaOnly.refresh()
            none.refresh()
        }

        assertEquals(RunnerId.ORCA, runnerInUse(orcaOnly))
        assertEquals(RunnerId.HERDR, runnerInUse(none))
    }

    @Test
    fun `mobile providers are declared by a project with emulators or simulators`() {
        assertTrue(declaresMobileProviders(listOf(machineProject(setOf(ANDROID_EMULATORS)))))
        assertTrue(declaresMobileProviders(listOf(machineProject(setOf(IOS_SIMULATORS)))))
        assertFalse(declaresMobileProviders(listOf(machineProject(setOf(GRADLE_DAEMONS)))))
        assertFalse(declaresMobileProviders(emptyList()))
    }

    @Test
    fun `the loaded branches skip projects not loaded and detached worktrees`() {
        val loaded = machineProject(emptySet(), listOf(Worktree("/home/dev/projects/TASK-123", "feature/TASK-123"), Worktree("/home/dev/projects/detached", null)))

        assertEquals(listOf("feature/TASK-123"), loadedBranches(listOf(loaded, machineProject(emptySet()))))
    }

    @Test
    fun `the tour banner shows only on the view of the tour's project`() {
        val isTour = { candidate: app.luxion.shogunai.domain.model.Project -> candidate.id == project.id }

        assertEquals(project, tourProjectShown(ShellDestination.All(project.id), listOf(project), isTour))
        assertNull(tourProjectShown(ShellDestination.All(project.id), listOf(project)) { false })
        assertNull(tourProjectShown(ShellDestination.All("unknown"), listOf(project), isTour))
        assertNull(tourProjectShown(ShellDestination.All(), listOf(project), isTour))
        assertNull(tourProjectShown(ShellDestination.Active, listOf(project), isTour))
        assertNull(tourProjectShown(null, listOf(project), isTour))
    }

    @Test
    fun `the palette opens destinations and shows a worktree in all worktrees`() {
        run(PaletteAction.Open(ShellDestination.MachineOverview))
        assertEquals(ShellDestination.MachineOverview, viewModel.destination)

        run(PaletteAction.SelectWorktree(ref))

        assertEquals(ShellDestination.All(), viewModel.destination)
        assertEquals(ref.path, board.selectedWorktree?.worktree?.path)
    }

    @Test
    fun `the palette copies the launch command and the selected worktree's path`() {
        run(PaletteAction.CopyLaunchCommand(ref))
        run(PaletteAction.RunSelected(ActionId.CopyPath, agent = null))

        assertEquals(listOf("copied", "copy path"), calls)
    }

    @Test
    fun `the palette runs other actions on the selected worktree`() {
        board.select(ref)

        run(PaletteAction.RunSelected(ActionId.CopyLaunchCommand, agent = null))

        assertTrue(calls.isEmpty())
    }

    @Test
    fun `the palette opens the creation of a worktree in its project`() {
        run(PaletteAction.NewWorktreeIn(project.id))

        assertEquals(ShellDestination.All(projectFilter = project.id), viewModel.destination)
        assertTrue(board.isCreateDialogOpen)
        assertEquals(project.id, board.createProjectId)
    }

    @Test
    fun `the palette's commands add a project, clean up, reload, toggle the sidebar and open the preferences`() {
        run(PaletteAction.Run(PaletteCommand.CLEAN_UP))
        assertEquals(ShellDestination.All(), viewModel.destination)
        run(PaletteAction.Run(PaletteCommand.RELOAD))
        assertEquals(1, viewModel.reloadRequests)
        run(PaletteAction.Run(PaletteCommand.TOGGLE_SIDEBAR))
        assertTrue(viewModel.sidebarCollapsed)
        run(PaletteAction.Run(PaletteCommand.OPEN_PREFERENCES))
        assertEquals(listOf("preferences"), calls)

        run(PaletteAction.Run(PaletteCommand.ADD_PROJECT))

        assertEquals(ShellDestination.AddProject(), viewModel.destination)
    }

    @Test
    fun `the sidebar lists each multirepo with its member projects, then the unavailable ones`() {
        val multirepo = Multirepo("/home/dev/projects", MultirepoDeclaration(repos = mapOf("main" to "main-repo"), worktreesRoot = "../w"), approved = true)
        val lost = UnavailableMultirepo("/home/dev/lost", MultirepoUnavailability.FolderUnreachable)
        val viewModel = ShellViewModel(
            FakeProjectRepository(listOf(project)),
            FakeShellPreferenceRepository(),
            multirepoCatalog = MultirepoCatalog { KnownMultirepos(listOf(multirepo), listOf(lost)) },
        )
        viewModel.toggleMultirepoCollapsed(lost.folder)

        val folders = sidebarMultirepos(viewModel, board)

        assertEquals(listOf("/home/dev/projects", "/home/dev/lost"), folders.map { it.folder })
        assertEquals(listOf(listOf(project), emptyList()), folders.map { it.projects })
        assertEquals(listOf(false, true), folders.map { it.collapsed })
        assertEquals(listOf(false, false), folders.map { it.needsReview })
        assertEquals(0, folders.first().activity.waitingCount)
    }

    @Test
    fun `a dropped repository is added as a project and any other folder as a multirepo`() = runBlocking<Unit> {
        openDroppedFolder("/home/dev/projects/sample-app", { true }, viewModel)
        assertEquals(ShellDestination.AddProject("/home/dev/projects/sample-app"), viewModel.destination)

        openDroppedFolder("/home/dev/projects/product", { false }, viewModel)
        assertEquals(AddMultirepoRequest("/home/dev/projects/product"), viewModel.addMultirepoRequest)
    }

    @Test
    fun `fixing an error opens the settings of its project while it exists`() {
        openProjectSettings(viewModel, "unknown")
        assertEquals(ShellDestination.Active, viewModel.destination)

        openProjectSettings(viewModel, project.id)

        assertEquals(ShellDestination.ProjectSettings(project.id), viewModel.destination)
    }
}
