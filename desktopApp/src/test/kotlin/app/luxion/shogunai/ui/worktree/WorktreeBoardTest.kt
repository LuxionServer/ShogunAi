package app.luxion.shogunai.ui.worktree

import app.luxion.shogunai.EmulatorUseCases
import app.luxion.shogunai.GradleUseCases
import app.luxion.shogunai.SimulatorUseCases
import app.luxion.shogunai.WorktreeUseCases
import app.luxion.shogunai.XcodeUseCases
import app.luxion.shogunai.domain.FakeClipboardWriter
import app.luxion.shogunai.domain.FakeDerivedDataSource
import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.FakeGradleDaemonSource
import app.luxion.shogunai.domain.FakeGradleDaemonSource.Companion.gradleDaemon
import app.luxion.shogunai.domain.FakeRepositoryProvisioningSource
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.failure
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.success
import app.luxion.shogunai.domain.FakeShellPreferenceRepository
import app.luxion.shogunai.domain.executor.CommandResult
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.model.AgentLaunchConfig
import app.luxion.shogunai.domain.model.DaemonState
import app.luxion.shogunai.domain.model.GroupedView
import app.luxion.shogunai.domain.model.Grouping
import app.luxion.shogunai.domain.model.GradleProvider
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.RunningAgent
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.usecase.AgentSearch
import app.luxion.shogunai.domain.usecase.CleanUpWorktreesUseCase
import app.luxion.shogunai.domain.usecase.CopyWorktreeLaunchCommandUseCase
import app.luxion.shogunai.domain.usecase.CreateWorktreeFromBranchUseCase
import app.luxion.shogunai.domain.usecase.CreateWorktreeUseCase
import app.luxion.shogunai.domain.usecase.DeleteOrphanedDerivedDataUseCase
import app.luxion.shogunai.domain.usecase.DetectRunningAgentsUseCase
import app.luxion.shogunai.domain.usecase.ListDerivedDataUseCase
import app.luxion.shogunai.domain.usecase.ListGradleDaemonsUseCase
import app.luxion.shogunai.domain.usecase.ListLocalBranchesUseCase
import app.luxion.shogunai.domain.usecase.ListWorktreesUseCase
import app.luxion.shogunai.domain.usecase.MeasureWorktreeSizeUseCase
import app.luxion.shogunai.domain.usecase.ReadGitMetadataFingerprintUseCase
import app.luxion.shogunai.domain.usecase.ReadWorktreeStatusUseCase
import app.luxion.shogunai.domain.usecase.RemoveWorktreeUseCase
import app.luxion.shogunai.domain.usecase.RepairWorktreesUseCase
import app.luxion.shogunai.domain.usecase.ResolveMainBranchUseCase
import app.luxion.shogunai.domain.usecase.StopGradleDaemonsUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class WorktreeBoardTest {

    private val dispatcher = StandardTestDispatcher()
    private val listCommand = listOf("git", "worktree", "list", "--porcelain")
    private val forEachRefCommand = listOf("git", "for-each-ref", "--format=%(refname:short)", "refs/heads")

    private val api = FakeRepository(
        id = "api",
        worktrees = listOf("/home/dev/projects/api-TASK-12" to "feature/TASK-12"),
        agentCommand = "codex",
    )
    private val mainRepo = FakeRepository(
        id = "main-repo",
        worktrees = listOf(
            "/home/dev/projects/TASK-123" to "feature/TASK-123",
            "/home/dev/projects/task-456" to "bugfix/other",
        ),
    )
    private val preferences = FakeShellPreferenceRepository()
    private var gradleDeclaredFor: Set<String> = emptySet()
    private val gradleDaemons = FakeGradleDaemonSource()
    private val agentSearches = mutableListOf<List<AgentSearch>>()
    private var agentResult: Result<Map<String, List<RunningAgent>>> = Result.success(emptyMap())
    private var agentGate: CompletableDeferred<Unit>? = null
    private val executed = mutableListOf<List<String>>()
    private var divergenceOutput = "2\t0\n"

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private inner class FakeRepository(
        val id: String,
        var worktrees: List<Pair<String, String>>,
        agentCommand: String = "claude",
        var exists: Boolean = true,
    ) {
        val base = "/home/dev/projects/$id"
        val config = ProjectConfig(
            baseRepositoryPath = base,
            worktreesRoot = "/home/dev/projects",
            secretFiles = emptyList(),
            agentLaunchConfig = AgentLaunchConfig(agentCommand = agentCommand),
        )
        val project = Project(id = id, name = id, config = config)
        var lists = 0
        var listGate: CompletableDeferred<Unit>? = null
        var removeGate: CompletableDeferred<Unit>? = null
        val files = FakeFileManager(existing = listOf(base), directories = setOf("$base/.git")).apply {
            stamps["$base/.git/HEAD"] = 1
        }

        val porcelain: String
            get() = (listOf(base to "main") + worktrees).joinToString("\n\n") { (path, branch) ->
                "worktree $path\nHEAD abc\nbranch refs/heads/$branch"
            }

        fun touch() {
            files.stamps["$base/.git/HEAD"] = (files.stamps["$base/.git/HEAD"] ?: 0) + 1
        }

        val executor = object : ShellCommandExecutor {
            override suspend fun execute(command: List<String>, workingDirectory: String?): CommandResult {
                executed += command
                return when {
                    command == listCommand -> {
                        lists++
                        listGate?.await()
                        success(command, porcelain)
                    }
                    command == forEachRefCommand -> success(command, stdout = "main\nhotfix/fix\n")
                    command.getOrNull(1) == "symbolic-ref" -> success(command, stdout = "origin/main\n")
                    command == listOf("git", "rev-list", "--left-right", "--count", "origin/main...HEAD") ->
                        success(command, stdout = divergenceOutput)
                    command.getOrNull(1) == "rev-list" -> failure(command, stderr = "no upstream")
                    command.take(3) == listOf("git", "worktree", "remove") -> {
                        removeGate?.await()
                        success(command)
                    }
                    else -> success(command)
                }
            }
        }

        fun useCases(): WorktreeUseCases = WorktreeUseCases(
            list = ListWorktreesUseCase(config, executor, FakeFileManager(existing = if (exists) listOf(base) else emptyList())),
            create = CreateWorktreeUseCase(config, executor, files, FakeRepositoryProvisioningSource()),
            createFromBranch = CreateWorktreeFromBranchUseCase(config, executor, files, FakeRepositoryProvisioningSource()),
            listLocalBranches = ListLocalBranchesUseCase(config, executor),
            remove = RemoveWorktreeUseCase(config, executor),
            copyLaunchCommand = CopyWorktreeLaunchCommandUseCase(config, FakeClipboardWriter()),
            repair = RepairWorktreesUseCase(config, executor, files),
            resolveMainBranch = ResolveMainBranchUseCase(config, executor),
            readStatus = ReadWorktreeStatusUseCase(executor),
            cleanUp = CleanUpWorktreesUseCase(config, executor),
            detectAgents = { worktrees -> DetectRunningAgentsUseCase(executor)(config, worktrees) },
            measureSize = MeasureWorktreeSizeUseCase(files, dispatcher),
            readFingerprint = ReadGitMetadataFingerprintUseCase(config, files, dispatcher),
            readPorts = { Result.success(emptyMap()) },
            emulator = EmulatorUseCases.NONE,
            simulator = SimulatorUseCases.NONE,
            gradle = GradleUseCases(
                provider = { GradleProvider().takeIf { id in gradleDeclaredFor } },
                listDaemons = ListGradleDaemonsUseCase(gradleDaemons, FakeFileManager(existing = listOf(base) + worktrees.map { it.first })),
                stopDaemons = StopGradleDaemonsUseCase(gradleDaemons, executor),
                isBuildCacheEnabled = { true },
            ),
            xcode = XcodeUseCases(
                provider = { null },
                supported = { false },
                list = ListDerivedDataUseCase(FakeDerivedDataSource()),
                deleteOrphans = DeleteOrphanedDerivedDataUseCase(FakeDerivedDataSource()),
            ),
        )
    }

    private val repositories = listOf(api, mainRepo)

    private fun board(projects: List<Project> = repositories.map { it.project }): WorktreeBoard = WorktreeBoard(
        useCasesFor = { config -> repositories.first { it.base == config.baseRepositoryPath }.useCases() },
        detectAgents = { searches ->
            agentSearches += searches
            agentGate?.await()
            agentResult
        },
        preferences = preferences,
    ).also {
        it.syncCatalog(projects)
        it.show(null)
    }

    private fun TestScope.tick() {
        advanceTimeBy(3.seconds)
        runCurrent()
    }

    @Test
    fun `all worktrees groups every project's worktrees in catalog order`() = runTest(dispatcher) {
        val board = board()
        advanceUntilIdle()

        assertEquals(listOf("api", "main-repo"), board.groups.map { it.project.id })
        assertEquals(2, board.groups[0].worktrees.size)
        assertEquals(3, board.groups[1].worktrees.size)
        assertEquals(5, board.totalWorktreeCount)
    }

    @Test
    fun `active lists the in progress worktrees of every project without the clean main worktrees`() = runTest(dispatcher) {
        divergenceOutput = "0\t0\n"
        val board = board()
        advanceUntilIdle()

        board.show(null, GroupedView.ACTIVE)

        val paths = board.stateGroups.flatMap { group -> group.items.map { it.worktree.path } }
        assertEquals(
            setOf("/home/dev/projects/api-TASK-12", "/home/dev/projects/TASK-123", "/home/dev/projects/task-456"),
            paths.toSet(),
        )
        assertEquals(listOf(StateGroupKind.IN_PROGRESS), board.stateGroups.map { it.kind })
        assertEquals(3, board.activeCount)
        assertEquals(1, board.activeCountOf("api"))
        assertEquals(2, board.activeCountOf("main-repo"))
    }

    @Test
    fun `an agent moves its worktree to the agent working group and shows in the project's agents`() = runTest(dispatcher) {
        divergenceOutput = "0\t0\n"
        agentResult = Result.success(mapOf("/home/dev/projects/api-TASK-12" to listOf(RunningAgent("codex"))))
        val board = board()
        advanceUntilIdle()
        board.startWatching()
        tick()
        board.show(null, GroupedView.ACTIVE)

        assertEquals(listOf(StateGroupKind.AGENT_WORKING, StateGroupKind.IN_PROGRESS), board.stateGroups.map { it.kind })
        assertEquals(listOf("codex"), board.agentNamesOf("api"))
        assertEquals(emptyList(), board.agentNamesOf("main-repo"))
        board.stopWatching()
    }

    @Test
    fun `a selected worktree that isn't active stays listed until it's deselected`() = runTest(dispatcher) {
        val board = board()
        advanceUntilIdle()
        board.show(null, GroupedView.ACTIVE)
        val main = WorktreeRef("api", "/home/dev/projects/api")

        board.select(main)

        assertEquals(StateGroupKind.OTHER, board.stateGroups.last().kind)
        assertTrue(main in board.visibleRefs())

        board.clearSelection()

        assertFalse(main in board.visibleRefs())
    }

    @Test
    fun `all worktrees by state keeps every worktree listed`() = runTest(dispatcher) {
        val board = board()
        advanceUntilIdle()

        board.updateGrouping(Grouping.BY_STATE)

        assertEquals(5, board.stateGroups.sumOf { it.items.size })
    }

    @Test
    fun `each view remembers its own grouping`() = runTest(dispatcher) {
        val board = board()
        advanceUntilIdle()

        board.show(null, GroupedView.ACTIVE)
        assertEquals(Grouping.BY_STATE, board.grouping)
        board.updateGrouping(Grouping.BY_PROJECT)
        board.show(null, GroupedView.ALL)

        assertEquals(Grouping.BY_PROJECT, board.grouping)
        assertEquals(Grouping.BY_PROJECT, preferences.loadGrouping(GroupedView.ACTIVE))
        board.updateGrouping(Grouping.BY_STATE)
        board.show(null, GroupedView.ACTIVE)
        assertEquals(Grouping.BY_PROJECT, board.grouping)
    }

    @Test
    fun `active shows the empty state once nothing is active`() = runTest(dispatcher) {
        val board = board(listOf(api.project))
        api.worktrees = emptyList()
        board.show(null, GroupedView.ACTIVE)
        advanceUntilIdle()

        assertTrue(board.isNothingActive)
    }

    @Test
    fun `the project filter shows a single group`() = runTest(dispatcher) {
        val board = board()
        advanceUntilIdle()

        board.show("api")

        assertEquals(listOf("api"), board.groups.map { it.project.id })
        assertTrue(board.isFiltered)

        board.show(null)

        assertEquals(listOf("api", "main-repo"), board.groups.map { it.project.id })
    }

    @Test
    fun `projects are added and removed with the catalog`() = runTest(dispatcher) {
        val board = board(listOf(api.project))
        advanceUntilIdle()
        val apiHolder = board.holder("api")

        board.syncCatalog(listOf(api.project, mainRepo.project))
        advanceUntilIdle()

        assertSame(apiHolder, board.holder("api"))
        assertEquals(3, board.holder("main-repo")?.worktrees?.size)

        board.syncCatalog(listOf(mainRepo.project))

        assertNull(board.holder("api"))
        assertEquals(listOf("main-repo"), board.groups.map { it.project.id })
    }

    @Test
    fun `a project whose configuration changed is recreated`() = runTest(dispatcher) {
        val board = board()
        advanceUntilIdle()
        val before = board.holder("api")

        val renamed = api.project.copy(name = "API")
        board.syncCatalog(listOf(renamed, mainRepo.project))
        assertSame(before, board.holder("api"))
        assertEquals("API", board.groups.first().project.name)

        val changed = api.project.copy(config = api.config.copy(secretFiles = listOf(".env")))
        board.syncCatalog(listOf(changed, mainRepo.project))
        advanceUntilIdle()

        assertNotSame(before, board.holder("api"))
        assertEquals(2, board.holder("api")?.worktrees?.size)
    }

    @Test
    fun `the selection is cleared when its project is removed`() = runTest(dispatcher) {
        val board = board()
        advanceUntilIdle()
        board.select(WorktreeRef("api", "/home/dev/projects/api-TASK-12"))
        assertEquals("/home/dev/projects/api-TASK-12", board.selectedWorktree?.worktree?.path)

        board.syncCatalog(listOf(mainRepo.project))

        assertNull(board.selected)
        assertNull(board.selectedWorktree)
    }

    @Test
    fun `a missing repository fails only its own group`() = runTest(dispatcher) {
        api.exists = false
        val board = board()
        advanceUntilIdle()

        val failed = assertIs<ProjectLoadState.Failed>(board.holder("api")?.loadState)
        assertIs<WorktreeError.BaseRepositoryNotFound>(failed.error)
        assertEquals(0, api.lists)
        assertEquals(ProjectLoadState.Loaded, board.holder("main-repo")?.loadState)
        assertEquals(3, board.holder("main-repo")?.worktrees?.size)
    }

    @Test
    fun `at most two projects list their worktrees at the same time`() = runTest(dispatcher) {
        val web = FakeRepository(id = "web", worktrees = emptyList())
        val gates = listOf(api, mainRepo, web).onEach { it.listGate = CompletableDeferred() }
        val board = WorktreeBoard(
            useCasesFor = { config -> listOf(api, mainRepo, web).first { it.base == config.baseRepositoryPath }.useCases() },
            detectAgents = { Result.success(emptyMap()) },
            preferences = preferences,
        )
        board.syncCatalog(listOf(api.project, mainRepo.project, web.project))
        runCurrent()

        assertEquals(2, gates.count { it.lists == 1 })
        assertEquals(ProjectLoadState.Loading, board.holder("web")?.loadState)

        api.listGate?.complete(Unit)
        runCurrent()

        assertEquals(3, gates.count { it.lists == 1 })
        gates.forEach { it.listGate?.complete(Unit) }
        advanceUntilIdle()
        assertEquals(ProjectLoadState.Loaded, board.holder("web")?.loadState)
    }

    @Test
    fun `a check reloads only the project whose metadata changed`() = runTest(dispatcher) {
        val board = board()
        advanceUntilIdle()
        board.startWatching()

        tick()
        mainRepo.touch()
        tick()
        advanceTimeBy(1.seconds)
        runCurrent()
        board.stopWatching()

        assertEquals(1, api.lists)
        assertEquals(2, mainRepo.lists)
    }

    @Test
    fun `agents are detected once per check and attributed across projects`() = runTest(dispatcher) {
        agentResult = Result.success(
            mapOf(
                "/home/dev/projects/api-TASK-12" to listOf(RunningAgent("codex")),
                "/home/dev/projects/TASK-123" to listOf(RunningAgent("claude")),
            ),
        )
        val board = board()
        advanceUntilIdle()
        board.startWatching()

        tick()
        board.stopWatching()

        val searches = agentSearches.single()
        assertEquals(listOf("codex", "claude"), searches.map { it.agentCommand })
        assertEquals(listOf(2, 3), searches.map { it.worktrees.size })
        assertEquals(listOf(RunningAgent("codex")), board.holder("api")?.agents?.get("/home/dev/projects/api-TASK-12"))
        assertEquals(listOf(RunningAgent("claude")), board.holder("main-repo")?.agents?.get("/home/dev/projects/TASK-123"))
        assertNull(board.holder("api")?.agents?.get("/home/dev/projects/TASK-123"))
    }

    @Test
    fun `a busy project is skipped and the others are checked`() = runTest(dispatcher) {
        val board = board()
        advanceUntilIdle()
        api.removeGate = CompletableDeferred()
        val apiHolder = board.holder("api")!!
        apiHolder.remove(apiHolder.worktrees.first { !it.isMain }, branchToDelete = null)
        runCurrent()
        api.touch()
        board.startWatching()

        tick()
        board.stopWatching()

        assertTrue(apiHolder.isRemoving)
        assertEquals(1, api.lists)
        assertEquals(listOf("main-repo"), agentSearches.single().map { it.worktrees.first().path.substringAfterLast('/') })
        api.removeGate?.complete(Unit)
        runCurrent()
    }

    @Test
    fun `every project is checked even when the view is filtered`() = runTest(dispatcher) {
        val board = board()
        advanceUntilIdle()
        board.show("api")
        board.startWatching()

        tick()
        mainRepo.touch()
        tick()
        advanceTimeBy(1.seconds)
        runCurrent()
        board.stopWatching()

        assertEquals(2, mainRepo.lists)
        assertEquals(listOf("codex", "claude"), agentSearches.first().map { it.agentCommand })
    }

    @Test
    fun `a slow check skips the next one instead of piling up`() = runTest(dispatcher) {
        val board = board()
        advanceUntilIdle()
        agentGate = CompletableDeferred()
        board.startWatching()

        tick()
        tick()
        tick()

        assertEquals(1, agentSearches.size)
        agentGate?.complete(Unit)
        runCurrent()
        tick()
        board.stopWatching()

        assertEquals(2, agentSearches.size)
    }

    @Test
    fun `the text filter hides groups without matches`() = runTest(dispatcher) {
        val board = board()
        advanceUntilIdle()

        board.updateTextFilter("task-12")

        assertEquals(listOf("api", "main-repo"), board.groups.map { it.project.id })
        assertEquals(listOf("feature/TASK-12"), board.groups[0].worktrees.map { it.branch })
        assertEquals(listOf("feature/TASK-123"), board.groups[1].worktrees.map { it.branch })

        board.updateTextFilter("TASK-456")

        assertEquals(listOf("main-repo"), board.groups.map { it.project.id })
        assertEquals(listOf("bugfix/other"), board.groups.single().worktrees.map { it.branch })

        board.updateTextFilter("nothing")
        assertTrue(board.hasNoMatch)

        board.clearTextFilter()
        assertFalse(board.hasNoMatch)
        assertEquals(5, board.groups.sumOf { it.worktrees.size })
    }

    @Test
    fun `escape clears the text filter first and then the selection`() = runTest(dispatcher) {
        val board = board()
        advanceUntilIdle()
        board.select(WorktreeRef("api", api.base))
        board.updateTextFilter("main")

        board.escape()
        assertEquals("", board.textFilter)
        assertEquals(WorktreeRef("api", api.base), board.selected)

        board.escape()
        assertNull(board.selected)
    }

    @Test
    fun `collapsed groups are remembered and skipped by arrow navigation`() = runTest(dispatcher) {
        val board = board()
        advanceUntilIdle()

        board.toggleCollapsed("api")

        assertEquals(setOf("api"), preferences.collapsedProjects)
        assertTrue(board.groups.first { it.project.id == "api" }.collapsed)
        board.selectNext()
        assertEquals(WorktreeRef("main-repo", mainRepo.base), board.selected)

        val restarted = board()
        assertTrue(restarted.groups.first { it.project.id == "api" }.collapsed)
    }

    @Test
    fun `arrow navigation crosses project groups`() = runTest(dispatcher) {
        val board = board()
        advanceUntilIdle()

        repeat(3) { board.selectNext() }

        assertEquals(WorktreeRef("main-repo", mainRepo.base), board.selected)
        board.selectPrevious()
        assertEquals(WorktreeRef("api", "/home/dev/projects/api-TASK-12"), board.selected)
    }

    @Test
    fun `new worktree from all worktrees asks for the project, defaulting to the last one used`() = runTest(dispatcher) {
        preferences.lastCreateProject = "main-repo"
        val board = board()
        advanceUntilIdle()

        board.openCreateDialog()

        assertTrue(board.createAsksForProject)
        assertEquals("main-repo", board.createProjectId)

        board.selectCreateProject("api")
        board.updateTaskId("TASK-99")
        board.confirmCreate()
        advanceUntilIdle()

        assertEquals(WorktreeSuccess.Created("/home/dev/projects/TASK-99"), board.holder("api")?.success)
        assertEquals("api", preferences.lastCreateProject)
        assertFalse(board.isCreateDialogOpen)
    }

    @Test
    fun `new worktree defaults to the first project without a previous one`() = runTest(dispatcher) {
        val board = board()
        advanceUntilIdle()

        board.openCreateDialog()

        assertEquals("api", board.createProjectId)
    }

    @Test
    fun `new worktree in a filtered view doesn't ask for the project`() = runTest(dispatcher) {
        preferences.lastCreateProject = "api"
        val board = board()
        advanceUntilIdle()
        board.show("main-repo")

        board.openCreateDialog()

        assertFalse(board.createAsksForProject)
        assertEquals("main-repo", board.createProjectId)
    }

    @Test
    fun `new worktree from a group header opens it for that project`() = runTest(dispatcher) {
        val board = board()
        advanceUntilIdle()

        board.openCreateDialog("main-repo")

        assertFalse(board.createAsksForProject)
        assertEquals("main-repo", board.createProjectId)
    }

    @Test
    fun `cleanup from all worktrees groups the candidates of every project`() = runTest(dispatcher) {
        val board = board()
        advanceUntilIdle()

        board.openCleanup()

        assertEquals(listOf("api", "main-repo"), board.cleanupProjects.map { it.project.id })
        assertEquals(listOf(1, 2), board.cleanupProjects.map { it.worktrees.size })
        assertFalse(board.cleanupDeleteBranches)

        board.dismissCleanup()
        assertFalse(board.isCleanupDialogOpen)
        assertTrue(executed.none { it.take(3) == listOf("git", "worktree", "remove") })
    }

    @Test
    fun `cleanup from a group header only covers that project`() = runTest(dispatcher) {
        val board = board()
        advanceUntilIdle()

        board.openCleanup("api")
        board.confirmCleanup()
        advanceUntilIdle()

        assertEquals(
            listOf(listOf("git", "worktree", "remove", "/home/dev/projects/api-TASK-12")),
            executed.filter { it.take(3) == listOf("git", "worktree", "remove") },
        )
        assertEquals(WorktreeSuccess.CleanedUp(removedCount = 1), board.cleanupSuccess)
    }

    @Test
    fun `provider options apply only to the projects that declare them`() = runTest(dispatcher) {
        gradleDeclaredFor = setOf("api")
        val idle = DaemonState.Idle(Instant.parse("2026-10-16T10:50:00Z"))
        gradleDaemons.daemons = listOf(
            gradleDaemon(10, idle, lastBuildDirectory = "/home/dev/projects/api-TASK-12"),
            gradleDaemon(20, idle, lastBuildDirectory = "/home/dev/projects/TASK-123"),
        )
        val board = board()
        advanceUntilIdle()

        board.openCleanup()
        assertTrue(board.cleanupProjects.any { it.holder.isGradleRepository })
        assertFalse(board.cleanupProjects.first { it.project.id == "main-repo" }.holder.isGradleRepository)
        board.confirmCleanup()
        advanceUntilIdle()

        assertTrue(executed.contains(listOf("kill", "10")))
        assertFalse(executed.contains(listOf("kill", "20")))
        assertEquals(WorktreeSuccess.CleanedUp(removedCount = 3, stoppedDaemons = 1), board.cleanupSuccess)
    }

    @Test
    fun `cleanup is disabled when no project in scope has candidates`() = runTest(dispatcher) {
        api.worktrees = emptyList()
        val board = board()
        advanceUntilIdle()

        assertFalse(board.canCleanUp("api"))
        assertTrue(board.canCleanUp())
        board.show("api")
        assertFalse(board.canCleanUp())
    }

    @Test
    fun `refreshing the view reloads branches only for an open existing-branch dialog`() = runTest(dispatcher) {
        val board = board()
        advanceUntilIdle()
        board.openCreateDialog("api")
        board.updateCreateMode(CreateMode.EXISTING_BRANCH)
        advanceUntilIdle()
        val listings = executed.count { it == forEachRefCommand }
        val mainRepoLists = mainRepo.lists

        board.refreshShown()
        advanceUntilIdle()

        assertEquals(listings + 1, executed.count { it == forEachRefCommand })
        assertEquals(mainRepoLists + 1, mainRepo.lists)
    }

}
