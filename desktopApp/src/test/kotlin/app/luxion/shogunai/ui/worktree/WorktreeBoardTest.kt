package app.luxion.shogunai.ui.worktree

import app.luxion.shogunai.domain.model.GroupRunnerOpening
import app.luxion.shogunai.domain.model.RunnerId
import app.luxion.shogunai.domain.model.RunnerOpening
import app.luxion.shogunai.domain.model.RunnerOutcome
import app.luxion.shogunai.domain.usecase.toRunnerOpening
import app.luxion.shogunai.ui.actions.ActionId
import app.luxion.shogunai.ui.actions.menuBarEntries
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
import app.luxion.shogunai.domain.model.SortedView
import app.luxion.shogunai.domain.model.WorktreeSort
import app.luxion.shogunai.domain.model.Grouping
import app.luxion.shogunai.domain.model.GradleProvider
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.RunningAgent
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.usecase.AgentSearch
import app.luxion.shogunai.ui.components.TextInputFocus
import app.luxion.shogunai.domain.usecase.CleanUpWorktreesUseCase
import app.luxion.shogunai.domain.usecase.CopyWorktreeLaunchCommandUseCase
import app.luxion.shogunai.domain.usecase.CreateWorktreeFromBranchUseCase
import app.luxion.shogunai.domain.usecase.CreateWorktreeUseCase
import app.luxion.shogunai.domain.usecase.DeleteOrphanedDerivedDataUseCase
import app.luxion.shogunai.domain.usecase.DetectRunningAgentsUseCase
import app.luxion.shogunai.domain.usecase.ListDerivedDataUseCase
import app.luxion.shogunai.domain.usecase.ListGradleDaemonsUseCase
import app.luxion.shogunai.domain.usecase.FetchRemoteUseCase
import app.luxion.shogunai.domain.usecase.ListBranchesUseCase
import app.luxion.shogunai.domain.usecase.ResolveStartPointUseCase
import app.luxion.shogunai.domain.usecase.ListWorktreesUseCase
import app.luxion.shogunai.domain.usecase.MeasureWorktreeSizeUseCase
import app.luxion.shogunai.domain.usecase.ReadGitMetadataFingerprintUseCase
import app.luxion.shogunai.domain.usecase.ReadWorktreeStatusUseCase
import app.luxion.shogunai.domain.usecase.DeleteBranchUseCase
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
import app.luxion.shogunai.HerdrUseCases
import app.luxion.shogunai.OrcaUseCases
import app.luxion.shogunai.domain.model.AgentState
import app.luxion.shogunai.domain.model.HerdrOpening
import app.luxion.shogunai.domain.model.HerdrOutcome
import app.luxion.shogunai.domain.model.RecentWorktree
import app.luxion.shogunai.domain.usecase.Activity
import app.luxion.shogunai.domain.usecase.OrcaSyncItem
import app.luxion.shogunai.ui.i18n.EnglishStrings
import app.luxion.shogunai.ui.i18n.describe
import app.luxion.shogunai.ui.tray.trayWorktreeLabel
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import app.luxion.shogunai.domain.model.BranchType
import app.luxion.shogunai.domain.model.Multirepo
import app.luxion.shogunai.domain.model.MultirepoDeclaration
import app.luxion.shogunai.domain.model.OrcaOpening
import app.luxion.shogunai.domain.model.OrcaOutcome
import app.luxion.shogunai.domain.usecase.GroupCreation
import app.luxion.shogunai.domain.usecase.GroupOpening
import app.luxion.shogunai.domain.usecase.GroupOrcaOpening
import app.luxion.shogunai.domain.usecase.GroupRepositoryOutcome
import app.luxion.shogunai.domain.usecase.GroupRepositoryResult
import app.luxion.shogunai.domain.usecase.MultirepoMember
import app.luxion.shogunai.domain.usecase.WorktreeGroup as BranchGroup
import app.luxion.shogunai.ui.multirepo.MultirepoActions
import app.luxion.shogunai.ui.multirepo.MultirepoNotice
import app.luxion.shogunai.ui.multirepo.multirepoMemberships
import app.luxion.shogunai.ui.multirepo.multirepoNoticeMessage

class WorktreeBoardTest {

    private val dispatcher = StandardTestDispatcher()
    private val listCommand = listOf("git", "worktree", "list", "--porcelain")
    private val forEachRefCommand = listOf("git", "for-each-ref", "--format=%(refname)", "refs/heads", "refs/remotes/origin/")
    private val statusCommand = listOf("git", "--no-optional-locks", "status", "--porcelain")
    private var now = Instant.parse("2026-10-19T10:00:00Z")

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
    private val openedInHerdr = mutableListOf<String>()
    private val openedInOrca = mutableListOf<String>()
    private var divergenceOutput = "2\t0\n"
    private val unmergedPaths = mutableSetOf<String>()
    private var upstreamOutput: String? = null
    private var lastCommitOutput = ""

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
        val declaresEmulator: Boolean = false,
    ) {
        var emulatorReads = 0
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
        var statusGate: CompletableDeferred<Unit>? = null
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
                    command == forEachRefCommand -> success(command, stdout = "refs/heads/main\nrefs/heads/hotfix/fix\n")
                    command == statusCommand -> {
                        statusGate?.await()
                        success(command)
                    }
                    command.getOrNull(1) == "symbolic-ref" -> success(command, stdout = "origin/main\n")
                    command == listOf("git", "rev-list", "--left-right", "--count", "origin/main...HEAD") ->
                        success(command, stdout = if (workingDirectory in unmergedPaths) "0\t1\n" else divergenceOutput)
                    command == listOf("git", "rev-list", "--left-right", "--count", "@{upstream}...HEAD") && upstreamOutput != null ->
                        success(command, stdout = upstreamOutput!!)
                    command == listOf("git", "log", "-1", "--format=%ct") -> success(command, stdout = lastCommitOutput)
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
            listBranches = ListBranchesUseCase(config, executor),
            fetchRemote = FetchRemoteUseCase(config, executor),
            resolveStartPoint = { ref -> ResolveStartPointUseCase(executor)(config.baseRepositoryPath, ref) },
            remove = RemoveWorktreeUseCase(config, executor),
            deleteBranch = DeleteBranchUseCase(config, executor),
            copyLaunchCommand = CopyWorktreeLaunchCommandUseCase(config, FakeClipboardWriter()),
            repair = RepairWorktreesUseCase(config, executor, files),
            resolveMainBranch = ResolveMainBranchUseCase(config, executor),
            readStatus = ReadWorktreeStatusUseCase(executor, FakeFileManager()),
            cleanUp = CleanUpWorktreesUseCase(config, executor),
            detectAgents = { worktrees -> DetectRunningAgentsUseCase(executor)(config, worktrees) },
            measureSize = MeasureWorktreeSizeUseCase(files, dispatcher),
            readFingerprint = ReadGitMetadataFingerprintUseCase(config, files, dispatcher),
            readPorts = { Result.success(emptyMap()) },
            emulator = if (declaresEmulator) {
                EmulatorUseCases.NONE.copy(
                    declared = { true },
                    readStates = {
                        emulatorReads++
                        Result.success(emptyMap())
                    },
                )
            } else {
                EmulatorUseCases.NONE
            },
            simulator = SimulatorUseCases.NONE,
            herdr = HerdrUseCases.NONE.copy(open = { target, _ -> openedInHerdr += target.path; Result.success(HerdrOpening(HerdrOutcome.OPENED, "w1:p1", "Claude")) }),
            orca = OrcaUseCases.NONE.copy(
                open = { target, _ -> openedInOrca += target.path; Result.success(OrcaOpening(OrcaOutcome.STARTED_AGENT, "term_1", "Claude")) },
                withTerminals = { paths -> paths.filter { it in orcaTerminalPaths }.toSet() },
            ),
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

    private var repositories = listOf(api, mainRepo)
    private var orcaTerminalPaths = emptySet<String>()

    private fun board(
        projects: List<Project> = repositories.map { it.project },
        clock: () -> Instant = Instant::now,
        onWorktreeUsed: (WorktreeRef) -> Unit = {},
        multirepoActions: MultirepoActions = MultirepoActions.NONE,
        runnerInUse: RunnerId = RunnerId.HERDR,
        syncOrca: suspend (List<OrcaSyncItem>) -> Unit = {},
    ): WorktreeBoard = WorktreeBoard(
        useCasesFor = { config -> repositories.first { it.base == config.baseRepositoryPath }.useCases() },
        detectAgents = { searches ->
            agentSearches += searches
            agentGate?.await()
            agentResult
        },
        syncOrca = syncOrca,
        preferences = preferences,
        clock = clock,
        ioDispatcher = dispatcher,
        onWorktreeUsed = onWorktreeUsed,
        multirepoActions = multirepoActions,
        runnerInUse = { runnerInUse },
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
    fun `active lists the new worktrees of every project without the clean main worktrees`() = runTest(dispatcher) {
        divergenceOutput = "0\t0\n"
        val board = board()
        advanceUntilIdle()

        board.show(null, GroupedView.ACTIVE)

        val paths = board.stateGroups.flatMap { group -> group.items.map { it.worktree.path } }
        assertEquals(
            setOf("/home/dev/projects/api-TASK-12", "/home/dev/projects/TASK-123", "/home/dev/projects/task-456"),
            paths.toSet(),
        )
        assertEquals(listOf(StateGroupKind.NEW), board.stateGroups.map { it.kind })
        assertEquals(3, board.activeCount)
        assertEquals(1, board.activeCountOf("api"))
        assertEquals(2, board.activeCountOf("main-repo"))
    }

    @Test
    fun `each view keeps its own sort and remembers it across restarts`() = runTest(dispatcher) {
        val first = board()
        advanceUntilIdle()

        first.show(null, GroupedView.ACTIVE)
        assertEquals(WorktreeSort.NAME, first.sort)
        first.updateSort(WorktreeSort.ACTIVITY)
        first.show(null, GroupedView.ALL)
        assertEquals(WorktreeSort.NAME, first.sort)
        first.show("api", GroupedView.ALL)
        assertEquals(WorktreeSort.NAME, first.sort)
        first.updateSort(WorktreeSort.ACTIVITY)

        assertEquals(WorktreeSort.ACTIVITY, preferences.loadSort(SortedView.ACTIVE))
        assertEquals(WorktreeSort.ACTIVITY, preferences.loadSort(SortedView.PROJECT))
        assertEquals(WorktreeSort.NAME, preferences.loadSort(SortedView.ALL))
        val restarted = board()
        restarted.show(null, GroupedView.ACTIVE)
        assertEquals(WorktreeSort.ACTIVITY, restarted.sort)
        restarted.show(null, GroupedView.ALL)
        assertEquals(WorktreeSort.NAME, restarted.sort)
    }

    @Test
    fun `worktrees are listed in natural name order inside a group`() = runTest(dispatcher) {
        divergenceOutput = "0\t0\n"
        mainRepo.worktrees = listOf(
            "/home/dev/projects/TASK-10" to "feature/TASK-10",
            "/home/dev/projects/TASK-9" to "feature/TASK-9",
            "/home/dev/projects/task-11" to "feature/task-11",
        )
        repositories = listOf(mainRepo)
        val board = board()
        advanceUntilIdle()

        board.show(null, GroupedView.ACTIVE)

        val branches = board.stateGroups.single().items.map { it.worktree.branch }
        assertEquals(listOf("feature/TASK-9", "feature/TASK-10", "feature/task-11"), branches)
    }

    @Test
    fun `a longer inactivity threshold moves worktrees out of inactive without reloading`() = runTest(dispatcher) {
        divergenceOutput = "0\t2\n"
        upstreamOutput = "0\t0\n"
        lastCommitOutput = "${now.minusSeconds(5 * 24 * 3600L).epochSecond}\n"
        repositories = listOf(api)
        val board = board(clock = { now })
        advanceUntilIdle()
        board.updateGrouping(Grouping.BY_STATE)
        val listsBefore = api.lists

        assertEquals(listOf(StateGroupKind.INACTIVE, StateGroupKind.OTHER), board.stateGroups.map { it.kind })

        board.inactivity.change(7)

        assertEquals(listOf(StateGroupKind.STOPPED, StateGroupKind.OTHER), board.stateGroups.map { it.kind })
        assertEquals(listsBefore, api.lists)
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

        assertEquals(listOf(StateGroupKind.AGENT_WORKING, StateGroupKind.NEW), board.stateGroups.map { it.kind })
        assertEquals(listOf("codex"), board.agentNamesOf("api"))
        assertEquals(emptyList(), board.agentNamesOf("main-repo"))
        board.stopWatching()
    }

    @Test
    fun `the tray lists the loaded recent worktrees and drops the removed ones`() = runTest(dispatcher) {
        val board = board()
        advanceUntilIdle()
        val recents = listOf(
            RecentWorktree("main-repo", "/home/dev/projects/TASK-123"),
            RecentWorktree("main-repo", "/home/dev/projects/removed"),
            RecentWorktree("deleted-project", "/home/dev/projects/other"),
            RecentWorktree("api", "/home/dev/projects/api-TASK-12"),
        )

        val listed = board.trayWorktrees(recents, EnglishStrings)

        assertEquals(listOf("feature/TASK-123", "feature/TASK-12"), listed.map { it.name })
        assertEquals(listOf("main-repo", "api"), listed.map { it.projectName })
        assertTrue(listed.all { it.isLoaded })
    }

    @Test
    fun `the tray shows a recent worktree by its folder until its project loads`() = runTest(dispatcher) {
        val board = board()

        val listed = board.trayWorktrees(listOf(RecentWorktree("main-repo", "/home/dev/projects/TASK-123")), EnglishStrings)

        assertEquals("TASK-123", listed.single().name)
        assertEquals(Activity.Unknown, listed.single().activity)
        assertFalse(listed.single().isLoaded)
        assertNull(listed.single().actions)
    }

    @Test
    fun `the tray marks a worktree whose agent is blocked as needing the user`() = runTest(dispatcher) {
        agentResult = Result.success(
            mapOf("/home/dev/projects/api-TASK-12" to listOf(RunningAgent("codex", state = AgentState.BLOCKED))),
        )
        val board = board()
        advanceUntilIdle()
        board.startWatching()
        tick()

        val listed = board.trayWorktrees(listOf(RecentWorktree("api", "/home/dev/projects/api-TASK-12")), EnglishStrings)

        board.stopWatching()
        assertEquals(Activity.NeedsYou, listed.single().activity)
        assertEquals("! feature/TASK-12 — api", trayWorktreeLabel(listed.single()))
    }

    @Test
    fun `each check hands the loaded worktrees' states to the Orca sync`() = runTest(dispatcher) {
        agentResult = Result.success(
            mapOf("/home/dev/projects/api-TASK-12" to listOf(RunningAgent("codex", state = AgentState.WORKING))),
        )
        val synced = mutableListOf<List<OrcaSyncItem>>()
        val board = board(syncOrca = { synced += it })
        advanceUntilIdle()
        board.startWatching()
        tick()
        board.stopWatching()

        val items = synced.single().associateBy { it.worktree.path }
        assertEquals(Activity.AgentWorking, items.getValue("/home/dev/projects/api-TASK-12").activity)
        assertEquals(listOf(RunningAgent("codex", state = AgentState.WORKING)), items.getValue("/home/dev/projects/api-TASK-12").agents)
        assertTrue(items.values.all { it.status != null })
    }

    @Test
    fun `the tray marks a worktree whose agent is working`() = runTest(dispatcher) {
        agentResult = Result.success(
            mapOf("/home/dev/projects/api-TASK-12" to listOf(RunningAgent("codex", state = AgentState.WORKING))),
        )
        val board = board()
        advanceUntilIdle()
        board.startWatching()
        tick()

        val listed = board.trayWorktrees(listOf(RecentWorktree("api", "/home/dev/projects/api-TASK-12")), EnglishStrings)

        board.stopWatching()
        assertEquals("● feature/TASK-12 — api", trayWorktreeLabel(listed.single()))
    }

    @Test
    fun `selecting a worktree reports it as used`() = runTest(dispatcher) {
        val used = mutableListOf<WorktreeRef>()
        val board = board(onWorktreeUsed = used::add)
        advanceUntilIdle()
        val main = WorktreeRef("api", "/home/dev/projects/api")

        board.select(main)

        assertEquals(listOf(main), used)
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
    fun `a project's view groups by state whatever the stored choice is`() = runTest(dispatcher) {
        val board = board(listOf(api.project))
        advanceUntilIdle()
        board.show(null, GroupedView.ALL)
        board.updateGrouping(Grouping.BY_PROJECT)

        board.show(api.project.id)

        assertTrue(board.isProjectView)
        assertEquals(Grouping.BY_STATE, board.grouping)
        assertTrue(board.isGroupedByState)
    }

    @Test
    fun `returning to all worktrees keeps its stored grouping`() = runTest(dispatcher) {
        val board = board(listOf(api.project))
        advanceUntilIdle()
        board.show(null, GroupedView.ALL)
        board.updateGrouping(Grouping.BY_PROJECT)
        board.show(api.project.id)

        board.show(null, GroupedView.ALL)

        assertFalse(board.isProjectView)
        assertEquals(Grouping.BY_PROJECT, board.grouping)
        assertEquals(Grouping.BY_PROJECT, preferences.loadGrouping(GroupedView.ALL))
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
    fun `the local filter narrows all worktrees to one project`() = runTest(dispatcher) {
        val board = board()
        advanceUntilIdle()

        board.updateViewProjectFilter("api")

        assertEquals(listOf("api"), board.groups.map { it.project.id })
        assertTrue(board.isFiltered)
        assertFalse(board.isProjectView)

        board.updateViewProjectFilter(null)

        assertEquals(listOf("api", "main-repo"), board.groups.map { it.project.id })
    }

    @Test
    fun `the local filter narrows active to one project and each view keeps its own`() = runTest(dispatcher) {
        divergenceOutput = "0\t0\n"
        val board = board()
        advanceUntilIdle()
        board.show(null, GroupedView.ACTIVE)

        board.updateViewProjectFilter("main-repo")

        val paths = board.stateGroups.flatMap { group -> group.items.map { it.worktree.path } }
        assertEquals(setOf("/home/dev/projects/TASK-123", "/home/dev/projects/task-456"), paths.toSet())

        board.show(null, GroupedView.ALL)
        assertNull(board.viewProjectFilter)
        assertEquals(listOf("api", "main-repo"), board.groups.map { it.project.id })

        board.show(null, GroupedView.ACTIVE)
        assertEquals("main-repo", board.viewProjectFilter)
    }

    @Test
    fun `a project view ignores the local filter`() = runTest(dispatcher) {
        val board = board()
        advanceUntilIdle()
        board.updateViewProjectFilter("api")

        board.show("main-repo")

        assertEquals(listOf("main-repo"), board.groups.map { it.project.id })
        assertTrue(board.isProjectView)
    }

    @Test
    fun `the local filter is cleared when its project is removed`() = runTest(dispatcher) {
        val board = board()
        advanceUntilIdle()
        board.updateViewProjectFilter("api")

        board.syncCatalog(listOf(mainRepo.project))

        assertNull(board.viewProjectFilter)
        assertEquals(listOf("main-repo"), board.groups.map { it.project.id })
    }

    @Test
    fun `revealing a worktree of another project clears the local filter of all worktrees`() = runTest(dispatcher) {
        val board = board()
        advanceUntilIdle()
        board.updateViewProjectFilter("api")
        board.show(null, GroupedView.ACTIVE)

        board.revealInAllWorktrees(WorktreeRef("main-repo", "/home/dev/projects/TASK-123"))
        board.show(null, GroupedView.ALL)

        assertNull(board.viewProjectFilter)
        assertEquals("/home/dev/projects/TASK-123", board.selectedWorktree?.worktree?.path)
    }

    @Test
    fun `revealing a worktree of the filtered project keeps the local filter`() = runTest(dispatcher) {
        val board = board()
        advanceUntilIdle()
        board.updateViewProjectFilter("api")

        board.revealInAllWorktrees(WorktreeRef("api", "/home/dev/projects/api-TASK-12"))

        assertEquals("api", board.viewProjectFilter)
        assertEquals("/home/dev/projects/api-TASK-12", board.selectedWorktree?.worktree?.path)
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
    fun `remembering the fetch option keeps the project's holder`() = runTest(dispatcher) {
        val board = board()
        advanceUntilIdle()
        val before = board.holder("api")

        val remembered = api.project.copy(config = api.config.copy(fetchBeforeCreate = true))
        board.syncCatalog(listOf(remembered, mainRepo.project))

        assertSame(before, board.holder("api"))
        assertTrue(board.projects.first().config.fetchBeforeCreate)
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
    fun `device states are refreshed at most every ten seconds while agents are detected on every check`() = runTest(dispatcher) {
        val emulatorProject = FakeRepository("emulator-app", emptyList(), declaresEmulator = true)
        repositories = listOf(emulatorProject)
        val board = board(listOf(emulatorProject.project), clock = { now })
        advanceUntilIdle()
        val baseline = emulatorProject.emulatorReads
        board.startWatching()

        repeat(4) {
            now = now.plusSeconds(3)
            tick()
        }
        assertEquals(baseline + 1, emulatorProject.emulatorReads)
        assertEquals(4, agentSearches.size)

        now = now.plusSeconds(3)
        tick()
        assertEquals(baseline + 2, emulatorProject.emulatorReads)
        board.stopWatching()
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
        assertEquals(listOf(listOf("codex"), listOf("claude")), searches.map { it.agentCommands })
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
    fun `only shown projects are checked while agents are detected for every project`() = runTest(dispatcher) {
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

        assertEquals(1, mainRepo.lists)
        assertEquals(listOf(listOf("codex"), listOf("claude")), agentSearches.first().map { it.agentCommands })
    }

    @Test
    fun `no check runs while the window is minimized or hidden and showing it resumes them`() = runTest(dispatcher) {
        val board = board()
        advanceUntilIdle()
        board.startWatching()
        board.setWindowActive(false)

        repeat(5) { tick() }
        assertTrue(agentSearches.isEmpty())

        board.setWindowActive(true)
        runCurrent()
        tick()
        board.stopWatching()

        assertEquals(1, agentSearches.size)
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
    fun `a disabled cleanup says there is nothing to clean up`() = runTest(dispatcher) {
        api.worktrees = emptyList()
        val board = board()
        advanceUntilIdle()

        assertEquals(CleanupBlock.NothingToCleanUp, board.cleanUpBlock("api"))
        assertNull(board.cleanUpBlock())
        assertEquals(EnglishStrings.cleanUpBlockedNothing, CleanupBlock.NothingToCleanUp.describe(EnglishStrings))
        assertEquals(EnglishStrings.cleanUpBlockedRunning, CleanupBlock.Running.describe(EnglishStrings))
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

    @Test
    fun `a reload keeps active worktrees in their group while their statuses are read again`() = runTest(dispatcher) {
        divergenceOutput = "0\t0\n"
        val board = board()
        advanceUntilIdle()
        board.show(null, GroupedView.ACTIVE)
        val activePaths = board.stateGroups.flatMap { group -> group.items.map { it.worktree.path } }
        assertEquals(3, activePaths.size)

        mainRepo.statusGate = CompletableDeferred()
        board.refreshShown()
        advanceUntilIdle()

        assertFalse(board.isReadingActivity)
        assertEquals(activePaths, board.stateGroups.flatMap { group -> group.items.map { it.worktree.path } })
        mainRepo.statusGate?.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun `focus reads the statuses of shown projects again and detects agents once`() = runTest(dispatcher) {
        val board = board(clock = { now })
        advanceUntilIdle()
        val statusReads = executed.count { it == statusCommand }
        val sizeRequests = api.files.sizeRequests.size + mainRepo.files.sizeRequests.size

        now = now.plusSeconds(10)
        board.refreshOnFocus()
        advanceUntilIdle()

        assertEquals(1, api.lists)
        assertEquals(1, mainRepo.lists)
        assertEquals(statusReads + 5, executed.count { it == statusCommand })
        assertEquals(listOf(listOf("codex"), listOf("claude")), agentSearches.single().map { it.agentCommands })
        assertEquals(sizeRequests, api.files.sizeRequests.size + mainRepo.files.sizeRequests.size)
    }

    @Test
    fun `focus skips projects read in the last five seconds`() = runTest(dispatcher) {
        val board = board(clock = { now })
        advanceUntilIdle()
        val statusReads = executed.count { it == statusCommand }

        now = now.plusSeconds(2)
        board.refreshOnFocus()
        advanceUntilIdle()
        assertEquals(statusReads, executed.count { it == statusCommand })
        assertTrue(agentSearches.isEmpty())

        now = now.plusSeconds(10)
        board.refreshOnFocus()
        now = now.plusSeconds(2)
        board.refreshOnFocus()
        advanceUntilIdle()

        assertEquals(statusReads + 5, executed.count { it == statusCommand })
        assertEquals(1, agentSearches.size)
    }

    @Test
    fun `focus skips a busy project and refreshes the others`() = runTest(dispatcher) {
        val board = board(clock = { now })
        advanceUntilIdle()
        api.removeGate = CompletableDeferred()
        val apiHolder = board.holder("api")!!
        apiHolder.remove(apiHolder.worktrees.first { !it.isMain }, branchToDelete = null)
        runCurrent()

        now = now.plusSeconds(10)
        board.refreshOnFocus()
        advanceUntilIdle()

        assertTrue(apiHolder.isRemoving)
        assertEquals(listOf(listOf("claude")), agentSearches.single().map { it.agentCommands })
        api.removeGate?.complete(Unit)
        runCurrent()
    }

    @Test
    fun `a failed project is listed above the state groups and is not nothing active`() = runTest(dispatcher) {
        api.exists = false
        val board = board(listOf(api.project))
        board.show(null, GroupedView.ACTIVE)
        board.updateGrouping(Grouping.BY_STATE)
        advanceUntilIdle()

        val items = listItems(board)

        assertEquals(listOf("api"), items.filterIsInstance<ListItem.GroupState>().map { it.group.project.id })
        assertTrue(items.filterIsInstance<ListItem.GroupState>().single().showsProjectName)
        assertFalse(board.isNothingActive)
    }

    @Test
    fun `a failed project keeps its group in the active view grouped by project`() = runTest(dispatcher) {
        api.exists = false
        val board = board(listOf(api.project))
        board.show(null, GroupedView.ACTIVE)
        board.updateGrouping(Grouping.BY_PROJECT)
        advanceUntilIdle()

        val items = listItems(board)

        assertTrue(items.any { it is ListItem.Header })
        assertTrue(items.any { it is ListItem.GroupState })
    }

    @Test
    fun `a collapsed group still shows its project's load failure`() = runTest(dispatcher) {
        api.exists = false
        val board = board()
        board.updateGrouping(Grouping.BY_PROJECT)
        advanceUntilIdle()
        board.toggleCollapsed("api")

        val notices = listItems(board).filterIsInstance<ListItem.GroupState>()

        assertEquals(listOf("api"), notices.map { it.group.project.id })
        assertTrue(notices.single().showsProjectName)
    }

    @Test
    fun `removing the selected worktree clears the selection`() = runTest(dispatcher) {
        val board = board()
        advanceUntilIdle()
        val holder = board.holder("api")!!
        val worktree = holder.worktrees.first { !it.isMain }
        board.select(WorktreeRef("api", worktree.path))

        holder.remove(worktree, branchToDelete = null)
        advanceUntilIdle()

        assertNull(board.selected)
    }

    @Test
    fun `removing another worktree keeps the selection`() = runTest(dispatcher) {
        val board = board()
        advanceUntilIdle()
        val apiHolder = board.holder("api")!!
        val selection = WorktreeRef("main-repo", "/home/dev/projects/TASK-123")
        board.select(selection)

        apiHolder.remove(apiHolder.worktrees.first { !it.isMain }, branchToDelete = null)
        advanceUntilIdle()

        assertEquals(selection, board.selected)
    }

    @Test
    fun `deleting the selected worktree does nothing while a text field has focus`() = runTest(dispatcher) {
        val board = board()
        advanceUntilIdle()
        board.select(WorktreeRef("main-repo", "/home/dev/projects/TASK-123"))

        TextInputFocus.enter()
        try {
            board.deleteSelectedWorktree()
            assertNull(board.pendingRemoval)
        } finally {
            TextInputFocus.leave()
        }

        board.deleteSelectedWorktree()
        assertEquals("/home/dev/projects/TASK-123", board.pendingRemoval?.worktreePendingRemoval?.path)
    }

    @Test
    fun `opening the selected worktree in Herdr opens it`() = runTest(dispatcher) {
        val board = board()
        advanceUntilIdle()
        board.select(WorktreeRef("main-repo", "/home/dev/projects/TASK-123"))

        board.runFromMenu(board.menuBarCatalog(EnglishStrings).primary!!.id)
        advanceUntilIdle()

        assertEquals(listOf("/home/dev/projects/TASK-123"), openedInHerdr)
    }

    @Test
    fun `open with is requested only with a selection and outside text fields`() = runTest(dispatcher) {
        val board = board()
        advanceUntilIdle()

        board.runFromMenu(ActionId.OpenWith)
        assertEquals(0, board.openWithRequests)

        board.select(WorktreeRef("main-repo", "/home/dev/projects/TASK-123"))
        board.runFromMenu(ActionId.OpenWith)
        assertEquals(1, board.openWithRequests)

        TextInputFocus.enter()
        try {
            board.runFromMenu(ActionId.OpenWith)
            assertEquals(1, board.openWithRequests)
        } finally {
            TextInputFocus.leave()
        }
    }

    @Test
    fun `the worktree menu names the default runner even without a selection`() = runTest(dispatcher) {
        val board = board()
        advanceUntilIdle()

        val items = board.menuBarCatalog(EnglishStrings).menuBarEntries().map { it.label }

        assertEquals(listOf("Open in Herdr", "Open with…", "Copy launch command", "Delete worktree…"), items)
    }

    @Test
    fun `opening in Herdr requires a selection`() = runTest(dispatcher) {
        val board = board()
        advanceUntilIdle()

        board.runFromMenu(board.menuBarCatalog(EnglishStrings).primary!!.id)
        advanceUntilIdle()

        assertTrue(openedInHerdr.isEmpty())
    }

    @Test
    fun `opening the selected worktree in Herdr does nothing while a text field has focus`() = runTest(dispatcher) {
        val board = board()
        advanceUntilIdle()
        board.select(WorktreeRef("main-repo", "/home/dev/projects/TASK-123"))

        TextInputFocus.enter()
        try {
            board.runFromMenu(board.menuBarCatalog(EnglishStrings).primary!!.id)
            advanceUntilIdle()
            assertTrue(openedInHerdr.isEmpty())
        } finally {
            TextInputFocus.leave()
        }
    }

    private val productApp = FakeRepository(
        id = "app",
        worktrees = listOf(
            "/home/dev/projects/app-TASK-1" to "feature/TASK-1",
            "/home/dev/projects/app-TASK-2" to "feature/TASK-2",
        ),
    )
    private val productApi = FakeRepository(
        id = "api",
        worktrees = listOf(
            "/home/dev/projects/api-TASK-1" to "feature/TASK-1",
            "/home/dev/projects/api-loose" to "feature/loose",
        ),
    )
    private val productDocs = FakeRepository(id = "docs", worktrees = listOf("/home/dev/projects/docs-TASK-2" to "feature/TASK-2"))
    private val tools = FakeRepository(id = "tools", worktrees = listOf("/home/dev/projects/tools-TASK-1" to "feature/TASK-1"))
    private val product = Multirepo(
        "/home/dev/projects/product",
        MultirepoDeclaration(
            repos = mapOf("app" to productApp.base, "api" to productApi.base, "docs" to productDocs.base),
            worktreesRoot = "../product-worktrees",
            defaultRepos = listOf("app", "api"),
        ),
        approved = true,
    )

    private fun TestScope.productBoard(multirepoActions: MultirepoActions = MultirepoActions.NONE, runnerInUse: RunnerId = RunnerId.HERDR): WorktreeBoard {
        repositories = listOf(productApp, productApi, productDocs, tools)
        val board = board(multirepoActions = multirepoActions, runnerInUse = runnerInUse)
        board.syncMultirepos(multirepoMemberships(listOf(product), board.projects))
        advanceUntilIdle()
        board.showMultirepo(product.folder)
        return board
    }

    @Test
    fun `the multirepo view groups its worktrees by change with the loose ones ungrouped`() = runTest(dispatcher) {
        val board = productBoard()

        val view = board.changeView
        assertTrue(board.isGroupedByChange)
        assertEquals(listOf("TASK-1", "TASK-2"), view.groups.map { it.title })
        assertEquals(listOf(listOf("api", "app"), listOf("app", "docs")), view.groups.map { group -> group.entries.map { it.alias } })
        assertEquals(listOf("/home/dev/projects/api-loose"), view.ungrouped.map { it.entry.worktree.path })
        assertEquals(listOf("app", "api", "docs"), board.shownProjects.map { it.id })
        assertEquals("api", board.aliasOf("api"))
    }

    @Test
    fun `the multirepo view can be grouped by state and remembers it`() = runTest(dispatcher) {
        val board = productBoard()

        assertEquals(listOf(Grouping.BY_CHANGE, Grouping.BY_STATE), board.groupingOptions)
        board.updateGrouping(Grouping.BY_STATE)

        assertTrue(board.isGroupedByState)
        assertEquals(5, board.stateGroups.sumOf { it.items.size })
        assertTrue(board.stateGroups.flatMap { it.items }.none { it.worktree.isMain })
        assertEquals(Grouping.BY_STATE, preferences.loadGrouping(GroupedView.MULTIREPO))
        board.show(null, GroupedView.ALL)
        assertEquals(Grouping.BY_PROJECT, board.grouping)
    }

    @Test
    fun `a group reads as merged only when every worktree of it is merged`() = runTest(dispatcher) {
        unmergedPaths += "/home/dev/projects/docs-TASK-2"
        val board = productBoard()

        val states = board.changeView.groups.associate { it.title to it.state }
        assertEquals(StateBadgeKind.MERGED, states["TASK-1"])
        assertTrue(states["TASK-2"] != StateBadgeKind.MERGED)
    }

    @Test
    fun `rows of other views carry the group tag and the project header the multirepo name`() = runTest(dispatcher) {
        val board = productBoard()
        board.show(null, GroupedView.ALL)

        assertEquals("TASK-1 · +api", board.groupTagOf(WorktreeRef("app", "/home/dev/projects/app-TASK-1"))?.label)
        assertEquals("TASK-2 · +app", board.groupTagOf(WorktreeRef("docs", "/home/dev/projects/docs-TASK-2"))?.label)
        assertNull(board.groupTagOf(WorktreeRef("api", "/home/dev/projects/api-loose")))
        assertNull(board.groupTagOf(WorktreeRef("tools", "/home/dev/projects/tools-TASK-1")))
        assertEquals("product", board.multirepoNameOf("app"))
        assertNull(board.multirepoNameOf("tools"))

        board.showMultirepo(product.folder)
        assertNull(board.groupTagOf(WorktreeRef("app", "/home/dev/projects/app-TASK-1")))
    }

    @Test
    fun `revealing a group expands it and selects one of its worktrees`() = runTest(dispatcher) {
        val board = productBoard()
        board.toggleChangeCollapsed("feature/TASK-2")
        assertTrue(board.changeView.groups.single { it.title == "TASK-2" }.collapsed)

        board.revealGroup("feature/TASK-2")

        assertFalse(board.changeView.groups.single { it.title == "TASK-2" }.collapsed)
        assertEquals(WorktreeRef("app", "/home/dev/projects/app-TASK-2"), board.selected)
        assertTrue(board.visibleRefs().contains(WorktreeRef("docs", "/home/dev/projects/docs-TASK-2")))
    }

    @Test
    fun `a group with an unmerged worktree can't be cleaned up and says which repository`() = runTest(dispatcher) {
        unmergedPaths += "/home/dev/projects/api-TASK-1"
        val board = productBoard()
        val group = board.changeView.groups.single { it.title == "TASK-1" }

        val blocker = board.groupCleanupBlocker(group)

        assertEquals("api", blocker?.alias)
        assertEquals("api isn't merged", blocker?.describe(EnglishStrings))
        assertFalse(board.canCleanUpGroup(group))
    }

    @Test
    fun `cleaning up a group removes only its worktrees`() = runTest(dispatcher) {
        val board = productBoard()
        val group = board.changeView.groups.single { it.title == "TASK-1" }

        board.openGroupCleanup(group)
        assertEquals(listOf("api", "app"), board.cleanupProjects.map { it.project.id })
        assertEquals(listOf(1, 1), board.cleanupProjects.map { it.worktrees.size })
        board.confirmCleanup()
        advanceUntilIdle()

        assertEquals(
            setOf("/home/dev/projects/app-TASK-1", "/home/dev/projects/api-TASK-1"),
            executed.filter { it.take(3) == listOf("git", "worktree", "remove") }.map { it[3] }.toSet(),
        )
        assertNull(board.cleanupOnly)
    }

    @Test
    fun `open all in Herdr opens every worktree of the group`() = runTest(dispatcher) {
        val board = productBoard()

        board.openAllInHerdr(board.changeView.groups.single { it.title == "TASK-2" })
        advanceUntilIdle()

        assertEquals(setOf("/home/dev/projects/app-TASK-2", "/home/dev/projects/docs-TASK-2"), openedInHerdr.toSet())
    }

    @Test
    fun `adding a repository to a group creates its worktree on the group's branch`() = runTest(dispatcher) {
        val calls = mutableListOf<Triple<List<String>, String, BranchType>>()
        val board = productBoard(
            MultirepoActions(
                createGroup = { members, taskId, type, _, _ ->
                    calls += Triple(members.map { it.alias }, taskId, type)
                    Result.success(GroupCreation("feature/$taskId", members.map { GroupRepositoryResult(it.alias, GroupRepositoryOutcome.CREATED) }))
                },
                openAgent = { _, _ -> Result.success(HerdrOpening(HerdrOutcome.OPENED, "w1:p1", "Claude").toRunnerOpening()) },
            ),
        )
        val group = board.changeView.groups.single { it.title == "TASK-1" }

        assertEquals(listOf("docs"), board.missingAliases(group))
        board.addRepositoryToGroup(group, "docs")
        advanceUntilIdle()

        assertEquals(listOf(Triple(listOf("docs"), "TASK-1", BranchType.FEATURE)), calls)
        assertEquals(MultirepoNotice.AddedToGroup("docs", "TASK-1"), board.multirepoNotice)
        assertTrue(board.addingToGroup.isEmpty())
    }

    @Test
    fun `a new change creates the group in the checked repositories and closes`() = runTest(dispatcher) {
        val created = mutableListOf<List<String>>()
        val board = productBoard(
            MultirepoActions(
                createGroup = { members, taskId, _, _, _ ->
                    created += members.map { it.alias }
                    Result.success(GroupCreation("feature/$taskId", members.map { GroupRepositoryResult(it.alias, GroupRepositoryOutcome.CREATED) }))
                },
                openAgent = { _, _ -> Result.success(HerdrOpening(HerdrOutcome.OPENED, "w1:p1", "Claude").toRunnerOpening()) },
            ),
        )

        board.openNewChange()
        val change = board.newChange!!
        assertEquals(setOf("app", "api"), change.selected)
        assertFalse(change.canConfirm)
        change.updateTaskId("TASK-20")
        change.toggle("docs")
        board.confirmNewChange()
        advanceUntilIdle()

        assertEquals(listOf(listOf("api", "app", "docs")), created)
        assertNull(board.newChange)
        assertEquals(MultirepoNotice.ChangeCreated("TASK-20", listOf("api", "app", "docs")), board.multirepoNotice)
    }

    @Test
    fun `a new change keeps the dialog open with each repository's result when one fails`() = runTest(dispatcher) {
        val failure = IllegalStateException("post-create failed")
        val board = productBoard(
            MultirepoActions(
                createGroup = { members, taskId, _, _, _ ->
                    Result.success(
                        GroupCreation(
                            "feature/$taskId",
                            members.map {
                                if (it.alias == "api") GroupRepositoryResult("api", GroupRepositoryOutcome.FAILED, error = failure)
                                else GroupRepositoryResult(it.alias, GroupRepositoryOutcome.CREATED)
                            },
                        ),
                    )
                },
                openAgent = { _, _ -> Result.success(HerdrOpening(HerdrOutcome.OPENED, "w1:p1", "Claude").toRunnerOpening()) },
            ),
        )

        board.openNewChange()
        board.newChange!!.updateTaskId("TASK-20")
        board.confirmNewChange()
        advanceUntilIdle()

        val change = board.newChange!!
        assertEquals(
            mapOf("api" to GroupRepositoryOutcome.FAILED, "app" to GroupRepositoryOutcome.CREATED),
            change.results!!.associate { it.alias to it.outcome },
        )
        assertFalse(change.canConfirm)
        board.dismissNewChange()
        assertNull(board.newChange)
    }

    @Test
    fun `opening the multirepo agent reports its outcome`() = runTest(dispatcher) {
        val opened = mutableListOf<String>()
        val board = productBoard(
            MultirepoActions(
                createGroup = { _, _, _, _, _ -> Result.failure(UnsupportedOperationException()) },
                openAgent = { _, multirepo ->
                    opened += multirepo.folder
                    Result.success(HerdrOpening(HerdrOutcome.FOCUSED, "w1:p1", "claude").toRunnerOpening())
                },
            ),
        )

        board.openMultirepoAgent()
        advanceUntilIdle()

        assertEquals(listOf(product.folder), opened)
        assertEquals(MultirepoNotice.AgentOpened(RunnerId.HERDR, RunnerOutcome.FOCUSED), board.multirepoNotice)
    }

    @Test
    fun `opening the multirepo agent reports an agent started in its open workspace and a failed start`() = runTest(dispatcher) {
        var result: Result<HerdrOpening> = Result.success(HerdrOpening(HerdrOutcome.STARTED_AGENT, "w5:p2", "Claude"))
        val board = productBoard(
            MultirepoActions(
                createGroup = { _, _, _, _, _ -> Result.failure(UnsupportedOperationException()) },
                openAgent = { _, _ -> result.map { it.toRunnerOpening() } },
            ),
        )

        board.openMultirepoAgent()
        advanceUntilIdle()
        assertEquals(MultirepoNotice.AgentOpened(RunnerId.HERDR, RunnerOutcome.STARTED_AGENT), board.multirepoNotice)

        val failure = WorktreeError.HerdrAgentNotStarted("w5:p2", "claude")
        result = Result.failure(failure)
        board.openMultirepoAgent()
        advanceUntilIdle()
        assertEquals(MultirepoNotice.Failed(failure), board.multirepoNotice)
    }

    private fun orcaActions(
        openGroupInOrca: suspend (BranchGroup) -> Result<List<GroupOrcaOpening>> = { Result.failure(UnsupportedOperationException()) },
        openAgentInOrca: suspend (Multirepo) -> Result<OrcaOpening> = { Result.failure(UnsupportedOperationException()) },
        openGroupInHerdr: suspend (BranchGroup) -> List<GroupOpening> = { emptyList() },
        createGroup: suspend (List<MultirepoMember>, String) -> Result<GroupCreation> = { members, taskId ->
            Result.success(GroupCreation("feature/$taskId", members.map { GroupRepositoryResult(it.alias, GroupRepositoryOutcome.CREATED, "/home/dev/projects/${it.alias}-$taskId") }))
        },
        herdrInstalled: Boolean = true,
        orcaInstalled: Boolean = true,
    ) = MultirepoActions(
        createGroup = { members, taskId, _, _, _ -> createGroup(members, taskId) },
        openAgent = { runner, multirepo ->
            if (runner == RunnerId.ORCA) openAgentInOrca(multirepo).map { it.toRunnerOpening() } else Result.failure(UnsupportedOperationException())
        },
        openGroup = { runner, group ->
            when (runner) {
                RunnerId.HERDR -> Result.success(openGroupInHerdr(group).map { GroupRunnerOpening(it.alias, it.path, it.result.map { opening -> opening.toRunnerOpening() }) })
                RunnerId.ORCA -> openGroupInOrca(group).map { openings ->
                    openings.map { GroupRunnerOpening(it.alias, it.path, it.result.map { opening -> opening.toRunnerOpening() }) }
                }
            }
        },
        installedRunners = setOfNotNull(RunnerId.HERDR.takeIf { herdrInstalled }, RunnerId.ORCA.takeIf { orcaInstalled }),
    )

    private fun orcaOpened(group: BranchGroup, outcome: (String) -> Result<OrcaOpening>) =
        Result.success(group.worktrees.map { GroupOrcaOpening(it.alias, it.worktree.path, outcome(it.alias)) })

    @Test
    fun `open all in Orca opens the group's worktrees and says per repository whether the agent started or was focused`() = runTest(dispatcher) {
        val groups = mutableListOf<BranchGroup>()
        val board = productBoard(
            orcaActions(openGroupInOrca = { group ->
                groups += group
                orcaOpened(group) { alias ->
                    Result.success(OrcaOpening(if (alias == "app") OrcaOutcome.STARTED_AGENT else OrcaOutcome.FOCUSED, "term_$alias", "claude"))
                }
            }),
        )

        assertTrue(board.isOrcaInstalled)
        board.openAllInOrca(board.changeView.groups.single { it.title == "TASK-1" })
        advanceUntilIdle()

        assertEquals("feature/TASK-1", groups.single().branch)
        assertEquals(setOf("/home/dev/projects/app-TASK-1", "/home/dev/projects/api-TASK-1"), groups.single().worktrees.map { it.worktree.path }.toSet())
        val notice = board.multirepoNotice as MultirepoNotice.GroupOpenedInOrca
        assertEquals("TASK-1", notice.title)
        assertEquals(setOf("app" to RunnerOutcome.STARTED_AGENT, "api" to RunnerOutcome.FOCUSED), notice.outcomes.toSet())
        assertTrue(board.openingGroupsInOrca.isEmpty())
        EnglishStrings.multirepoNoticeMessage(notice).let { assertTrue(it.contains("agent started in app") && it.contains("running agent focused in api"), it) }
    }

    @Test
    fun `open all in Orca with a failure still opens the others and shows the failure on its worktree`() = runTest(dispatcher) {
        val failure = WorktreeError.OrcaAgentNotStarted("term_api", "claude -n api-task-1")
        val board = productBoard(
            orcaActions(openGroupInOrca = { group ->
                orcaOpened(group) { alias -> if (alias == "api") Result.failure(failure) else Result.success(OrcaOpening(OrcaOutcome.STARTED_AGENT, "term_app", "claude")) }
            }),
        )

        board.openAllInOrca(board.changeView.groups.single { it.title == "TASK-1" })
        advanceUntilIdle()

        assertEquals(MultirepoNotice.GroupOpenedInOrca("TASK-1", listOf("app" to RunnerOutcome.STARTED_AGENT)), board.multirepoNotice)
        assertEquals(failure, board.holder("api")!!.actionErrors["/home/dev/projects/api-TASK-1"])
        assertNull(board.holder("app")!!.actionErrors["/home/dev/projects/app-TASK-1"])
        val details = EnglishStrings.describe(failure).details?.output.orEmpty()
        assertTrue(details.contains("term_api") && details.contains("claude -n api-task-1"), details)
    }

    @Test
    fun `the Orca actions are only offered when Orca is installed`() = runTest(dispatcher) {
        assertFalse(productBoard().isOrcaInstalled)
    }

    @Test
    fun `opening the multirepo agent in Orca reports a started agent, a focused one and its errors`() = runTest(dispatcher) {
        val opened = mutableListOf<String>()
        var result: Result<OrcaOpening> = Result.success(OrcaOpening(OrcaOutcome.STARTED_AGENT, "term_1", "Claude"))
        val board = productBoard(orcaActions(openAgentInOrca = { multirepo -> opened += multirepo.folder; result }))

        board.openMultirepoAgentInOrca()
        advanceUntilIdle()
        assertEquals(listOf(product.folder), opened)
        assertEquals(MultirepoNotice.AgentOpened(RunnerId.ORCA, RunnerOutcome.STARTED_AGENT), board.multirepoNotice)

        result = Result.success(OrcaOpening(OrcaOutcome.FOCUSED, "term_1", "claude"))
        board.openMultirepoAgentInOrca()
        advanceUntilIdle()
        assertEquals(MultirepoNotice.AgentOpened(RunnerId.ORCA, RunnerOutcome.FOCUSED), board.multirepoNotice)
        assertNotEquals(
            EnglishStrings.multirepoNoticeMessage(MultirepoNotice.AgentOpened(RunnerId.ORCA, RunnerOutcome.STARTED_AGENT)),
            EnglishStrings.multirepoNoticeMessage(MultirepoNotice.AgentOpened(RunnerId.ORCA, RunnerOutcome.FOCUSED)),
        )

        val notOpen = WorktreeError.OrcaMultirepoFolderNotOpen(product.folder)
        result = Result.failure(notOpen)
        board.openMultirepoAgentInOrca()
        advanceUntilIdle()
        assertEquals(MultirepoNotice.Failed(notOpen), board.multirepoNotice)
        assertTrue(EnglishStrings.describe(notOpen).nextStep.contains("Open as Folder"))

        val notStarted = WorktreeError.OrcaAgentNotStarted("term_2", "claude -n product")
        result = Result.failure(notStarted)
        board.openMultirepoAgentInOrca()
        advanceUntilIdle()
        assertEquals(MultirepoNotice.Failed(notStarted), board.multirepoNotice)
        assertTrue(EnglishStrings.describe(notStarted).details?.output.orEmpty().contains("term_2"))
        assertFalse(board.isOpeningMultirepoAgent)
    }

    @Test
    fun `cleaning up a group open in Orca marks its worktrees with Orca terminals`() = runTest(dispatcher) {
        orcaTerminalPaths = setOf("/home/dev/projects/app-TASK-1")
        val board = productBoard()
        val group = board.changeView.groups.single { it.title == "TASK-1" }

        board.openGroupCleanup(group)
        advanceUntilIdle()

        assertEquals(setOf("/home/dev/projects/app-TASK-1"), board.holder("app")!!.orcaTerminalPaths)
        assertEquals(emptySet(), board.holder("api")!!.orcaTerminalPaths)
    }

    @Test
    fun `cleaning up a group with Orca not running marks nothing`() = runTest(dispatcher) {
        val board = productBoard()

        board.openGroupCleanup(board.changeView.groups.single { it.title == "TASK-1" })
        advanceUntilIdle()

        assertEquals(emptySet(), board.holder("app")!!.orcaTerminalPaths)
        assertEquals(listOf("api", "app"), board.cleanupProjects.map { it.project.id })
    }

    @Test
    fun `the new change selector offers each installed runner and starts on the runner in use`() = runTest(dispatcher) {
        val both = productBoard(orcaActions())
        both.openNewChange()
        assertEquals(listOf(null, RunnerId.HERDR, RunnerId.ORCA), both.newChange!!.runners)
        assertEquals(RunnerId.HERDR, both.newChange!!.runner)

        val herdrOnly = productBoard(orcaActions(orcaInstalled = false))
        herdrOnly.openNewChange()
        assertEquals(listOf(null, RunnerId.HERDR), herdrOnly.newChange!!.runners)

        val neither = productBoard(orcaActions(herdrInstalled = false, orcaInstalled = false))
        neither.openNewChange()
        assertEquals(emptyList(), neither.newChange!!.runners)
        assertNull(neither.newChange!!.runner)

        val orcaInUse = productBoard(orcaActions(), runnerInUse = RunnerId.ORCA)
        orcaInUse.openNewChange()
        assertEquals(RunnerId.ORCA, orcaInUse.newChange!!.runner)

        val orcaMissing = productBoard(orcaActions(orcaInstalled = false), runnerInUse = RunnerId.ORCA)
        orcaMissing.openNewChange()
        assertNull(orcaMissing.newChange!!.runner)
    }

    @Test
    fun `a new change left on not opening creates the group and opens nothing`() = runTest(dispatcher) {
        var opened = 0
        val board = productBoard(orcaActions(openGroupInOrca = { opened++; Result.success(emptyList()) }, openGroupInHerdr = { opened++; emptyList() }))

        board.openNewChange()
        board.newChange!!.updateTaskId("TASK-20")
        board.newChange!!.runner = null
        board.confirmNewChange()
        advanceUntilIdle()

        assertEquals(0, opened)
        assertNull(board.newChange)
        assertEquals(MultirepoNotice.ChangeCreated("TASK-20", listOf("api", "app")), board.multirepoNotice)
    }

    @Test
    fun `a new change opened in Orca reports each repository's creation and then its opening`() = runTest(dispatcher) {
        val groups = mutableListOf<BranchGroup>()
        val board = productBoard(
            orcaActions(openGroupInOrca = { group ->
                groups += group
                orcaOpened(group) { Result.success(OrcaOpening(OrcaOutcome.STARTED_AGENT, "term_$it", "Claude")) }
            }),
        )

        board.openNewChange()
        val change = board.newChange!!
        change.updateTaskId("TASK-20")
        change.runner = RunnerId.ORCA
        board.confirmNewChange()
        advanceUntilIdle()

        assertEquals("feature/TASK-20", groups.single().branch)
        assertEquals(listOf("/home/dev/projects/api-TASK-20", "/home/dev/projects/app-TASK-20"), groups.single().worktrees.map { it.worktree.path })
        assertEquals(change, board.newChange)
        assertEquals(setOf("api", "app"), change.results!!.map { it.alias }.toSet())
        assertEquals(
            mapOf("api" to RunnerOutcome.STARTED_AGENT, "app" to RunnerOutcome.STARTED_AGENT),
            change.openings!!.mapValues { it.value.getOrThrow().outcome },
        )
    }

    @Test
    fun `a new change opened in Herdr opens the created worktrees as open all does`() = runTest(dispatcher) {
        val groups = mutableListOf<BranchGroup>()
        val board = productBoard(
            orcaActions(openGroupInHerdr = { group ->
                groups += group
                group.worktrees.map { GroupOpening(it.alias, it.worktree.path, Result.success(HerdrOpening(HerdrOutcome.OPENED, "w1:p1", "Claude"))) }
            }),
        )

        board.openNewChange()
        board.newChange!!.updateTaskId("TASK-20")
        board.newChange!!.runner = RunnerId.HERDR
        board.confirmNewChange()
        advanceUntilIdle()

        assertEquals(listOf("api", "app"), groups.single().worktrees.map { it.alias })
        assertEquals(RunnerOpening(RunnerId.HERDR, RunnerOutcome.OPENED, "w1:p1", "Claude"), board.newChange!!.openings!!["app"]!!.getOrThrow())
    }

    @Test
    fun `a new change whose opening fails stays created and shows the opening error`() = runTest(dispatcher) {
        val failure = WorktreeError.OrcaAgentNotStarted("term_api", "claude")
        val board = productBoard(
            orcaActions(openGroupInOrca = { group ->
                orcaOpened(group) { alias -> if (alias == "api") Result.failure(failure) else Result.success(OrcaOpening(OrcaOutcome.STARTED_AGENT, "term_app", "Claude")) }
            }),
        )

        board.openNewChange()
        board.newChange!!.updateTaskId("TASK-20")
        board.newChange!!.runner = RunnerId.ORCA
        board.confirmNewChange()
        advanceUntilIdle()

        val change = board.newChange!!
        assertTrue(change.results!!.all { it.outcome == GroupRepositoryOutcome.CREATED })
        assertEquals(failure, change.openings!!["api"]!!.exceptionOrNull())
        assertTrue(change.openings!!["app"]!!.isSuccess)
        assertFalse(change.canConfirm)
    }

    @Test
    fun `a new change opens only the worktrees that exist when a repository fails`() = runTest(dispatcher) {
        val groups = mutableListOf<BranchGroup>()
        val board = productBoard(
            orcaActions(
                createGroup = { _, taskId ->
                    Result.success(
                        GroupCreation(
                            "feature/$taskId",
                            listOf(
                                GroupRepositoryResult("api", GroupRepositoryOutcome.FAILED, "/home/dev/projects/api-$taskId", error = IllegalStateException("post-create failed")),
                                GroupRepositoryResult("app", GroupRepositoryOutcome.CREATED, "/home/dev/projects/app-$taskId"),
                                GroupRepositoryResult("docs", GroupRepositoryOutcome.FAILED, error = IllegalStateException("couldn't create")),
                            ),
                        ),
                    )
                },
                openGroupInOrca = { group ->
                    groups += group
                    orcaOpened(group) { Result.success(OrcaOpening(OrcaOutcome.STARTED_AGENT, "term_$it", "Claude")) }
                },
            ),
        )

        board.openNewChange()
        board.newChange!!.updateTaskId("TASK-20")
        board.newChange!!.toggle("docs")
        board.newChange!!.runner = RunnerId.ORCA
        board.confirmNewChange()
        advanceUntilIdle()

        assertEquals(listOf("api", "app"), groups.single().worktrees.map { it.alias })
        assertEquals(setOf("api", "app"), board.newChange!!.openings!!.keys)
    }

    @Test
    fun `a multirepo that needs review groups nothing and can't open its agent or a new change`() = runTest(dispatcher) {
        val opened = mutableListOf<String>()
        repositories = listOf(productApp, productApi, productDocs, tools)
        val board = board(
            multirepoActions = MultirepoActions(
                createGroup = { _, _, _, _, _ -> Result.failure(UnsupportedOperationException()) },
                openAgent = { _, multirepo -> opened += multirepo.folder; Result.success(HerdrOpening(HerdrOutcome.OPENED, "w1:p1", "Claude").toRunnerOpening()) },
            ),
        )
        board.syncMultirepos(multirepoMemberships(listOf(product.copy(approved = false)), board.projects))
        advanceUntilIdle()
        board.showMultirepo(product.folder)
        advanceUntilIdle()

        board.openMultirepoAgent()
        board.openNewChange()
        advanceUntilIdle()

        assertTrue(board.shownProjects.isEmpty())
        assertTrue(opened.isEmpty())
        assertEquals(null, board.newChange)
        assertTrue(agentSearches.flatten().none { it.exact })
    }

    @Test
    fun `trusting the shown multirepo approves it and reports back`() = runTest(dispatcher) {
        val trusted = mutableListOf<String>()
        var reported = 0
        repositories = listOf(productApp, productApi, productDocs, tools)
        val board = board(
            multirepoActions = MultirepoActions(
                createGroup = { _, _, _, _, _ -> Result.failure(UnsupportedOperationException()) },
                openAgent = { _, _ -> Result.failure(UnsupportedOperationException()) },
                trust = { multirepo -> trusted += multirepo.folder; Result.success(multirepo.copy(approved = true)) },
            ),
        )
        board.syncMultirepos(multirepoMemberships(listOf(product.copy(approved = false)), board.projects))
        board.showMultirepo(product.folder)

        board.trustShownMultirepo { reported++ }
        advanceUntilIdle()

        assertEquals(listOf(product.folder), trusted)
        assertEquals(1, reported)
        assertFalse(board.isTrustingMultirepo)
    }

    @Test
    fun `the multirepo agent is searched in the multirepo folder with its command and shown with its state`() = runTest(dispatcher) {
        val board = productBoard()
        agentResult = Result.success(mapOf(product.folder to listOf(RunningAgent("claude", AgentState.WORKING))))

        board.showMultirepo(product.folder)
        advanceUntilIdle()

        val search = agentSearches.flatten().last { it.exact }
        assertEquals(listOf("claude"), search.agentCommands)
        assertEquals(listOf(product.folder), search.worktrees.map { it.path })
        assertEquals(listOf(RunningAgent("claude", AgentState.WORKING)), board.multirepoAgentsOf(product.folder))
        assertEquals(1, board.multirepoActivityCountOf(product.folder))
    }

    @Test
    fun `the multirepo agent is searched by the command set on this machine`() = runTest(dispatcher) {
        repositories = listOf(productApp, productApi, productDocs, tools)
        val board = board()
        val local = product.copy(
            declaration = product.declaration.copy(agent = AgentLaunchConfig(agentCommand = "touch /tmp/marker; claude")),
            localAgent = AgentLaunchConfig(agentCommand = "codex --full-auto"),
        )
        board.syncMultirepos(multirepoMemberships(listOf(local), board.projects))
        board.showMultirepo(product.folder)
        advanceUntilIdle()

        assertEquals(listOf("codex --full-auto"), agentSearches.flatten().last { it.exact }.agentCommands)
    }

    @Test
    fun `an idle multirepo agent shows but does not count as activity`() = runTest(dispatcher) {
        val board = productBoard()
        agentResult = Result.success(mapOf(product.folder to listOf(RunningAgent("claude", AgentState.DONE))))

        board.showMultirepo(product.folder)
        advanceUntilIdle()

        assertEquals(1, board.multirepoAgentsOf(product.folder).size)
        assertEquals(0, board.multirepoActivityCountOf(product.folder))
    }
}
