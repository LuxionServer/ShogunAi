package app.luxion.shogunai.ui.worktree

import app.luxion.shogunai.ComposeUseCases
import app.luxion.shogunai.EmulatorUseCases
import app.luxion.shogunai.GradleUseCases
import app.luxion.shogunai.HeadlessAppUseCases
import app.luxion.shogunai.MavenUseCases
import app.luxion.shogunai.OrcaUseCases
import app.luxion.shogunai.ProvisioningUseCases
import app.luxion.shogunai.SimulatorUseCases
import app.luxion.shogunai.WorktreeUseCases
import app.luxion.shogunai.XcodeUseCases
import app.luxion.shogunai.domain.FakeClipboardWriter
import app.luxion.shogunai.domain.FakeDerivedDataSource
import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.FakeGradleDaemonSource
import app.luxion.shogunai.domain.FakeRepositoryProvisioningSource
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.failure
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.success
import app.luxion.shogunai.domain.executor.CommandResult
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.DerivedDataSource
import app.luxion.shogunai.domain.io.FileManager
import app.luxion.shogunai.domain.model.DerivedDataFolder
import app.luxion.shogunai.domain.model.DockerComposeProvider
import app.luxion.shogunai.domain.model.EmulatorState
import app.luxion.shogunai.domain.model.HeadlessAppProcess
import app.luxion.shogunai.domain.model.HeadlessAppState
import app.luxion.shogunai.domain.model.MavenProvider
import app.luxion.shogunai.domain.model.ObservedPort
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.ReleaseRecord
import app.luxion.shogunai.domain.model.SimulatorDevice
import app.luxion.shogunai.domain.model.SimulatorState
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeComposeState
import app.luxion.shogunai.domain.model.WorktreeEmulator
import app.luxion.shogunai.domain.model.WorktreeSimulator
import app.luxion.shogunai.domain.model.XcodeProvider
import app.luxion.shogunai.domain.usecase.CleanUpWorktreesUseCase
import app.luxion.shogunai.domain.usecase.CopyWorktreeLaunchCommandUseCase
import app.luxion.shogunai.domain.usecase.CreateWorktreeFromBranchUseCase
import app.luxion.shogunai.domain.usecase.CreateWorktreeUseCase
import app.luxion.shogunai.domain.usecase.DeleteBranchUseCase
import app.luxion.shogunai.domain.usecase.DeleteOrphanedDerivedDataUseCase
import app.luxion.shogunai.domain.usecase.DetectRunningAgentsUseCase
import app.luxion.shogunai.domain.usecase.FetchRemoteUseCase
import app.luxion.shogunai.domain.usecase.ListBranchesUseCase
import app.luxion.shogunai.domain.usecase.ListDerivedDataUseCase
import app.luxion.shogunai.domain.usecase.ListGradleDaemonsUseCase
import app.luxion.shogunai.domain.usecase.ListWorktreesUseCase
import app.luxion.shogunai.domain.usecase.MeasureWorktreeSizeUseCase
import app.luxion.shogunai.domain.usecase.ReadGitMetadataFingerprintUseCase
import app.luxion.shogunai.domain.usecase.ReadWorktreeStatusUseCase
import app.luxion.shogunai.domain.usecase.RemoveWorktreeUseCase
import app.luxion.shogunai.domain.usecase.RepairWorktreesUseCase
import app.luxion.shogunai.domain.usecase.ResolveMainBranchUseCase
import app.luxion.shogunai.domain.usecase.ResolveStartPointUseCase
import app.luxion.shogunai.domain.usecase.StopGradleDaemonsUseCase
import app.luxion.shogunai.ui.machine.MachineResource
import app.luxion.shogunai.domain.model.BranchType
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.model.RepositoryProvisioning
import app.luxion.shogunai.domain.model.RepositoryApprovalSubject
import app.luxion.shogunai.domain.FakeRepositoryTrust
import app.luxion.shogunai.domain.usecase.DeviceStart
import app.luxion.shogunai.domain.usecase.ResourceAcquisition
import app.luxion.shogunai.ToolUseCases
import app.luxion.shogunai.HerdrUseCases
import app.luxion.shogunai.AgentUseCases
import app.luxion.shogunai.ui.shell.AgentChoices
import app.luxion.shogunai.domain.FakeToolDetector
import app.luxion.shogunai.domain.FakeToolPreferenceRepository
import app.luxion.shogunai.domain.model.DetectedTools
import app.luxion.shogunai.domain.model.TerminalId
import app.luxion.shogunai.domain.model.EditorId
import app.luxion.shogunai.domain.model.AgentState
import app.luxion.shogunai.domain.model.RunningAgent
import app.luxion.shogunai.domain.model.AgentLaunchConfig
import app.luxion.shogunai.domain.model.HerdrOpening
import app.luxion.shogunai.domain.model.HerdrOutcome
import app.luxion.shogunai.domain.model.OrcaOpening
import app.luxion.shogunai.domain.model.OrcaOutcome
import app.luxion.shogunai.domain.model.GradleProvider
import app.luxion.shogunai.ui.shell.ToolsPreferences
import app.luxion.shogunai.ui.actions.ActionId
import app.luxion.shogunai.ui.actions.OpenTarget
import app.luxion.shogunai.ui.i18n.EnglishStrings
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.io.IOException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProjectWorktreesStatesTest {

    private val dispatcher = StandardTestDispatcher()
    private val base = "/home/dev/projects/main-repo"
    private val path = "/home/dev/projects/TASK-123"
    private val config = ProjectConfig(baseRepositoryPath = base, worktreesRoot = "/home/dev/projects", secretFiles = emptyList())
    private val listCommand = listOf("git", "worktree", "list", "--porcelain")
    private val statusCommand = listOf("git", "--no-optional-locks", "status", "--porcelain")
    private val porcelain = "worktree $base\nHEAD abc\nbranch refs/heads/main\n\nworktree $path\nHEAD abc\nbranch refs/heads/feature/TASK-123"

    private var gate: CompletableDeferred<Unit>? = null
    private var statusGate: CompletableDeferred<Unit>? = null
    private var respond: (List<String>) -> CommandResult? = { null }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun pass() {
        gate?.await()
    }

    private val executor = object : ShellCommandExecutor {
        override suspend fun execute(command: List<String>, workingDirectory: String?): CommandResult {
            respond(command)?.let { return it }
            return when (command) {
                listCommand -> success(command, porcelain)
                statusCommand -> {
                    statusGate?.await()
                    success(command)
                }
                else -> success(command)
            }
        }
    }

    private val gitFiles = FakeFileManager(existing = listOf(base), directories = setOf("$base/.git")).apply {
        stamps["$base/.git/HEAD"] = 1
    }

    private class GatedDerivedData(private val source: DerivedDataSource, private val before: suspend () -> Unit) : DerivedDataSource by source {
        override suspend fun folders(): List<DerivedDataFolder> {
            before()
            return source.folders()
        }
    }

    private fun useCases(files: FileManager = gitFiles, sizes: FileManager = FakeFileManager()): WorktreeUseCases = WorktreeUseCases(
        list = ListWorktreesUseCase(config, executor),
        create = CreateWorktreeUseCase(config, executor, FakeFileManager(existing = listOf(base)), FakeRepositoryProvisioningSource()),
        createFromBranch = CreateWorktreeFromBranchUseCase(config, executor, FakeFileManager(existing = listOf(base)), FakeRepositoryProvisioningSource()),
        listBranches = ListBranchesUseCase(config, executor),
        fetchRemote = FetchRemoteUseCase(config, executor),
        resolveStartPoint = { ref -> ResolveStartPointUseCase(executor)(base, ref) },
        remove = RemoveWorktreeUseCase(config, executor),
        deleteBranch = DeleteBranchUseCase(config, executor),
        copyLaunchCommand = CopyWorktreeLaunchCommandUseCase(config, FakeClipboardWriter()),
        repair = RepairWorktreesUseCase(config, executor, FakeFileManager(existing = listOf(base))),
        resolveMainBranch = ResolveMainBranchUseCase(config, executor),
        readStatus = ReadWorktreeStatusUseCase(executor, FakeFileManager()),
        cleanUp = CleanUpWorktreesUseCase(config, executor),
        detectAgents = { worktrees -> DetectRunningAgentsUseCase(executor)(config, worktrees) },
        measureSize = MeasureWorktreeSizeUseCase(sizes, dispatcher),
        readFingerprint = ReadGitMetadataFingerprintUseCase(config, files, dispatcher),
        readPorts = { Result.success(emptyMap()) },
        emulator = EmulatorUseCases.NONE,
        simulator = SimulatorUseCases.NONE,
        gradle = GradleUseCases(
            provider = { null },
            listDaemons = ListGradleDaemonsUseCase(FakeGradleDaemonSource(), FakeFileManager()),
            stopDaemons = StopGradleDaemonsUseCase(FakeGradleDaemonSource(), executor),
            isBuildCacheEnabled = { true },
        ),
        xcode = XcodeUseCases(
            provider = { null },
            supported = { false },
            list = ListDerivedDataUseCase(FakeDerivedDataSource()),
            deleteOrphans = DeleteOrphanedDerivedDataUseCase(FakeDerivedDataSource()),
        ),
    )

    private fun holder(useCases: WorktreeUseCases = useCases()): ProjectWorktrees =
        ProjectWorktrees(useCases, CoroutineScope(SupervisorJob() + dispatcher), ioDispatcher = dispatcher).also { it.refresh() }

    private val ProjectWorktrees.task: Worktree
        get() = worktrees.first { it.path == path }

    private val devicesDeclared: WorktreeUseCases
        get() = useCases().copy(
            readPorts = { pass(); Result.success(mapOf("PORT" to 4123)) },
            detectAgents = { pass(); Result.success(emptyMap()) },
            resolveRecordedBases = { pass(); emptyMap() },
            emulator = EmulatorUseCases.NONE.copy(
                declared = { true },
                readStates = { pass(); Result.success(mapOf(path to WorktreeEmulator("emulator-5554", EmulatorState.RUNNING))) },
            ),
            simulator = SimulatorUseCases.NONE.copy(
                declared = { true },
                readStates = { pass(); Result.success(emptyMap()) },
                orphans = { pass(); Result.success(listOf(SimulatorDevice("SIM-9", "ShogunAi · gone", SimulatorState.SHUTDOWN))) },
                readRelease = { ReleaseRecord(1_000L, "main-repo · TASK-9") },
            ),
            headlessApp = HeadlessAppUseCases.NONE.copy(
                declared = { true },
                read = { pass(); Result.success(HeadlessAppProcess(HeadlessAppState.RUNNING, 4242, "/home/dev/state/launch.log")) },
            ),
            maven = MavenUseCases(provider = { MavenProvider() }),
            compose = ComposeUseCases(
                provider = { DockerComposeProvider() },
                readStates = { pass(); Result.success(mapOf(path to WorktreeComposeState("main-repo-task-123", "running"))) },
            ),
            xcode = XcodeUseCases(
                provider = { XcodeProvider() },
                supported = { true },
                list = ListDerivedDataUseCase(GatedDerivedData(FakeDerivedDataSource()) { pass() }),
                deleteOrphans = DeleteOrphanedDerivedDataUseCase(FakeDerivedDataSource()),
            ),
        )

    @Test
    fun `reads that finish after a newer reload are dropped and the newer ones kept`() = runTest(dispatcher) {
        gate = CompletableDeferred()
        statusGate = CompletableDeferred()
        val holder = holder(devicesDeclared)
        runCurrent()
        holder.refresh()
        runCurrent()

        gate!!.complete(Unit)
        runCurrent()
        statusGate!!.complete(Unit)
        advanceUntilIdle()

        assertEquals(mapOf("PORT" to 4123), holder.ports[path])
        assertEquals(setOf(path), holder.emulators.keys)
        assertEquals(1, holder.orphanedSimulators.size)
        assertTrue(holder.headlessApps.isNotEmpty())
        assertTrue(holder.composeStates.isNotEmpty())
        assertIs<WorktreeStatusState.Loaded>(holder.statuses[path])
        assertEquals(
            setOf(
                MachineResource.ANDROID_EMULATORS,
                MachineResource.IOS_SIMULATORS,
                MachineResource.XCODE_DERIVED_DATA,
                MachineResource.MAVEN_DAEMONS,
                MachineResource.COMPOSE_PROJECTS,
            ),
            holder.declaredResources,
        )
        assertTrue(holder.canCleanUp)
        assertEquals(setOf(base, path), holder.simulatorReleases.keys)
    }

    @Test
    fun `a status read after a creation is dropped when a reload comes first`() = runTest(dispatcher) {
        val holder = holder()
        advanceUntilIdle()
        gate = CompletableDeferred()
        val gated = holder(useCases().copy(resolveRecordedBases = { pass(); emptyMap() }))
        advanceUntilIdle()

        gated.create("TASK-9", BranchType.FEATURE)
        runCurrent()
        gated.refresh()
        gate!!.complete(Unit)
        advanceUntilIdle()

        assertTrue(holder.hasLoaded)
        assertTrue(gated.worktrees.isNotEmpty())
    }

    @Test
    fun `failing device, port and section reads leave their previous state`() = runTest(dispatcher) {
        val failure = IOException("unreadable")
        val holder = holder(
            useCases().copy(
                readPorts = { Result.failure(failure) },
                emulator = EmulatorUseCases.NONE.copy(declared = { true }, readStates = { Result.failure(failure) }),
                simulator = SimulatorUseCases.NONE.copy(
                    declared = { true },
                    readStates = { Result.failure(failure) },
                    orphans = { Result.failure(failure) },
                ),
                headlessApp = HeadlessAppUseCases.NONE.copy(declared = { true }, read = { Result.failure(failure) }),
                xcode = XcodeUseCases(
                    provider = { XcodeProvider() },
                    supported = { true },
                    list = ListDerivedDataUseCase(GatedDerivedData(FakeDerivedDataSource()) { throw failure }),
                    deleteOrphans = DeleteOrphanedDerivedDataUseCase(FakeDerivedDataSource()),
                ),
                orca = OrcaUseCases.NONE.copy(ports = { mapOf(path to listOf(ObservedPort(4123))) }),
            ),
        )
        advanceUntilIdle()

        assertTrue(holder.ports.isEmpty())
        assertTrue(holder.emulators.isEmpty())
        assertTrue(holder.simulators.isEmpty())
        assertTrue(holder.orphanedSimulators.isEmpty())
        assertTrue(holder.headlessApps.isEmpty())
        assertTrue(holder.derivedDataOrphans.isEmpty())
        assertEquals(listOf(ObservedPort(4123)), holder.observedPorts[path])

        holder.applyAgents(null)
        assertNull(holder.agents)
        assertEquals(listOf(ObservedPort(4123)), holder.observedPorts[path])
    }

    @Test
    fun `refreshing devices reads the simulators and headless apps of repositories that declare them`() = runTest(dispatcher) {
        var simulatorReads = 0
        var appReads = 0
        val holder = holder(
            useCases().copy(
                simulator = SimulatorUseCases.NONE.copy(declared = { true }, readStates = { simulatorReads++; Result.success(emptyMap()) }),
                headlessApp = HeadlessAppUseCases.NONE.copy(declared = { true }, read = { appReads++; Result.success(null) }),
            ),
        )
        advanceUntilIdle()
        val before = simulatorReads to appReads

        holder.refreshDevices()

        assertEquals(before.first + 1, simulatorReads)
        assertTrue(appReads > before.second)
    }

    @Test
    fun `stopping a headless app reports a failure and stops once at a time`() = runTest(dispatcher) {
        val failure = IOException("can't stop")
        var stops = 0
        gate = CompletableDeferred()
        val holder = holder(
            useCases().copy(
                headlessApp = HeadlessAppUseCases.NONE.copy(declared = { true }, stop = { stops++; pass(); Result.failure(failure) }),
            ),
        )
        advanceUntilIdle()

        holder.stopHeadlessApp(holder.task)
        holder.stopHeadlessApp(holder.task)
        runCurrent()
        gate!!.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, stops)
        assertEquals(failure, holder.error)
    }

    @Test
    fun `a size that can't be measured is left out`() = runTest(dispatcher) {
        val unreadable = object : FileManager by FakeFileManager(existing = listOf(base, path)) {
            override fun directorySize(path: String, excluding: Set<String>): Long = throw IOException("unreadable")
        }
        val holder = holder(useCases(sizes = unreadable))
        advanceUntilIdle()

        assertTrue(holder.sizes.isEmpty())
    }

    @Test
    fun `an unreadable fingerprint keeps the previous one and reads as unreadable`() = runTest(dispatcher) {
        var readable = false
        val files = object : FileManager by gitFiles {
            override fun modificationStamps(path: String): List<Pair<String, Long>> =
                if (readable) gitFiles.modificationStamps(path) else throw IOException("unreadable")
        }
        val holder = holder(useCases(files = files))
        advanceUntilIdle()

        assertEquals(FingerprintCheck.UNREADABLE, holder.checkFingerprint())
        readable = true
        assertEquals(FingerprintCheck.UNCHANGED, holder.checkFingerprint())
        readable = false
        holder.refresh()
        advanceUntilIdle()
        readable = true
        assertEquals(FingerprintCheck.UNCHANGED, holder.checkFingerprint())
    }

    @Test
    fun `an index outside a linked worktree's folder reloads the list`() = runTest(dispatcher) {
        val holder = holder()
        advanceUntilIdle()
        holder.checkFingerprint()

        gitFiles.stamps["$base/.git/refs/heads/index"] = 1
        assertEquals(FingerprintCheck.RELOADED, holder.checkFingerprint())
        advanceUntilIdle()

        gitFiles.stamps["$base/.git/worktrees/TASK-123/index"] = 1
        assertEquals(FingerprintCheck.UNCHANGED, holder.checkFingerprint())
        gitFiles.stamps["$base/.git/index.lock"] = 1
        assertEquals(FingerprintCheck.UNCHANGED, holder.checkFingerprint())
        advanceUntilIdle()
    }

    @Test
    fun `nothing is pending removal until a removal is requested`() = runTest(dispatcher) {
        val holder = holder()
        advanceUntilIdle()

        holder.setPendingRemovalDeleteBranch(true)

        assertNull(holder.worktreePendingRemoval)
        assertFalse(holder.pendingRemovalDeleteBranch)
        assertFalse(holder.pendingRemovalRequiresForce)
        assertNull(holder.pendingRemovalLock)
        assertFalse(holder.pendingRemovalOpenInHerdr)
        assertFalse(holder.pendingRemovalHasOrcaTerminals)
        holder.confirmPendingRemoval()
        holder.copyLastCreatedLaunchCommand()
        holder.copyCreatedLaunchCommand()
        advanceUntilIdle()
        assertTrue(holder.actionErrors.isEmpty())
    }

    @Test
    fun `details aren't read for a missing folder and an unreadable environment or provisioning is reported`() = runTest(dispatcher) {
        val failure = IOException("unreadable")
        val holder = holder(
            useCases().copy(
                readEnvironment = { Result.failure(failure) },
                provisioning = ProvisioningUseCases(check = { Result.failure(failure) }, provision = { Result.failure(failure) }),
            ),
        )
        advanceUntilIdle()

        holder.loadDetail(Worktree(path = "/home/dev/projects/gone", branch = "feature/gone", isPrunable = true))
        holder.loadDetail(holder.task)
        advanceUntilIdle()
        assertTrue(holder.environments.isEmpty())
        assertEquals(ProvisioningState.Unavailable, holder.provisioning[path])

        gate = CompletableDeferred()
        val gated = holder(
            useCases().copy(
                readEnvironment = { Result.failure(failure) },
                provisioning = ProvisioningUseCases(check = { Result.failure(failure) }, provision = { pass(); Result.failure(failure) }),
            ),
        )
        advanceUntilIdle()
        gated.provision(gated.task)
        gated.provision(gated.task)
        runCurrent()
        gate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(failure, gated.actionErrors[path])
        assertTrue(gated.provisioningInProgress.isEmpty())
    }

    @Test
    fun `a status read finishing after a reload is dropped`() = runTest(dispatcher) {
        statusGate = CompletableDeferred()
        val holder = holder()
        runCurrent()
        holder.refresh()
        runCurrent()

        statusGate!!.complete(Unit)
        advanceUntilIdle()

        assertIs<WorktreeStatusState.Loaded>(holder.statuses[path])
    }

    @Test
    fun `a size measured for a worktree no longer listed is dropped`() = runTest(dispatcher) {
        val holder = holder(useCases(sizes = FakeFileManager(existing = listOf(base, path), sizes = mapOf(path to 10L))))
        runCurrent()
        respond = { command -> if (command == listCommand) success(command, "worktree $base\nHEAD abc\nbranch refs/heads/main") else null }
        holder.refresh()
        advanceUntilIdle()

        assertFalse(path in holder.sizes)
    }

    @Test
    fun `starting and stopping a simulator report their outcome and run once at a time`() = runTest(dispatcher) {
        val failure = IOException("simctl failed")
        var starts = 0
        var startResult: Result<DeviceStart<WorktreeSimulator>> = Result.failure(failure)
        var stopResult: Result<WorktreeSimulator?> = Result.failure(failure)
        gate = CompletableDeferred()
        val simulator = WorktreeSimulator("SIM-1", "ShogunAi · main-repo · TASK-123", SimulatorState.BOOTED)
        val holder = holder(
            useCases().copy(
                simulator = SimulatorUseCases.NONE.copy(
                    declared = { true },
                    start = { starts++; pass(); startResult },
                    stop = { stopResult },
                ),
            ),
        )
        advanceUntilIdle()

        holder.startSimulator(holder.task)
        holder.startSimulator(holder.task)
        runCurrent()
        gate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, starts)
        assertEquals(failure, holder.actionErrors[path])

        startResult = Result.success(DeviceStart(simulator, ResourceAcquisition(false, emptyList())))
        holder.startSimulator(holder.task)
        advanceUntilIdle()
        assertNull(holder.actionErrors[path])

        holder.stopSimulator(holder.task)
        holder.stopSimulator(holder.task)
        advanceUntilIdle()
        assertEquals(failure, holder.actionErrors[path])
        stopResult = Result.success(null)
        holder.stopSimulator(holder.task)
        advanceUntilIdle()
        assertNull(holder.actionErrors[path])
    }

    @Test
    fun `starting and stopping an emulator run once at a time and report their outcome`() = runTest(dispatcher) {
        val failure = IOException("adb failed")
        var starts = 0
        var stopResult: Result<WorktreeEmulator?> = Result.failure(failure)
        gate = CompletableDeferred()
        val holder = holder(
            useCases().copy(
                emulator = EmulatorUseCases.NONE.copy(declared = { true }, start = { starts++; pass(); Result.failure(failure) }, stop = { stopResult }),
            ),
        )
        advanceUntilIdle()

        holder.startEmulator(holder.task)
        holder.startEmulator(holder.task)
        runCurrent()
        gate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, starts)

        holder.stopEmulator(holder.task)
        advanceUntilIdle()
        assertEquals(failure, holder.actionErrors[path])
        stopResult = Result.success(null)
        holder.stopEmulator(holder.task)
        advanceUntilIdle()
        assertNull(holder.actionErrors[path])
    }

    @Test
    fun `compose services can be built and taken down`() = runTest(dispatcher) {
        val actions = mutableListOf<String>()
        val holder = holder(
            useCases().copy(
                compose = ComposeUseCases(
                    provider = { DockerComposeProvider() },
                    run = { _, action -> actions += action::class.simpleName.orEmpty(); Result.success("") },
                    down = { actions += "Down"; Result.success(Unit) },
                ),
            ),
        )
        advanceUntilIdle()

        holder.buildComposeServices(holder.task)
        advanceUntilIdle()
        holder.takeDownComposeServices(holder.task)
        advanceUntilIdle()

        assertEquals(listOf("Build", "Down"), actions)
    }

    @Test
    fun `the launch command of a worktree created with errors can be copied, and a copy failure is reported`() = runTest(dispatcher) {
        val failure = IOException("clipboard busy")
        var clipboardWorks = true
        val provisioned = useCases().copy(
            create = CreateWorktreeUseCase(
                config,
                executor,
                FakeFileManager(existing = listOf(base)),
                FakeRepositoryProvisioningSource(byRepository = mapOf(base to RepositoryProvisioning(postCreateCommands = listOf(listOf("npm", "install"))))),
                trust = FakeRepositoryTrust(mapOf(base to RepositoryApprovalSubject(listOf(listOf("npm", "install"))))),
            ),
            copyLaunchCommand = CopyWorktreeLaunchCommandUseCase(config, FakeClipboardWriter { if (clipboardWorks) Result.success(Unit) else Result.failure(failure) }),
        )
        respond = { command -> if (command.firstOrNull() == "npm") failure(command, exitCode = 1, stderr = "ENOTFOUND") else null }
        val holder = holder(provisioned)
        advanceUntilIdle()

        holder.create("TASK-9", BranchType.FEATURE)
        advanceUntilIdle()
        val created = holder.createdWithErrorPath
        assertTrue(created != null)

        holder.copyCreatedLaunchCommand()
        advanceUntilIdle()
        assertNull(holder.actionErrors[created])
        clipboardWorks = false
        holder.copyCreatedLaunchCommand()
        holder.copyLaunchCommand(holder.task)
        advanceUntilIdle()
        assertIs<WorktreeError.ClipboardWriteFailed>(holder.actionErrors[created])
        assertIs<WorktreeError.ClipboardWriteFailed>(holder.actionErrors[path])
    }

    @Test
    fun `a creation from a start point runs once at a time and a failing main branch read leaves no default start`() = runTest(dispatcher) {
        respond = { command -> if (command.getOrNull(1) == "symbolic-ref" || command.getOrNull(1) == "rev-parse") failure(command, stderr = "no main") else null }
        val holder = holder()
        advanceUntilIdle()
        holder.loadBranches()
        advanceUntilIdle()
        assertNull(holder.defaultStartRef)

        respond = { command -> if (command.take(2) == listOf("git", "rev-parse")) success(command, "abc123\n") else null }
        gate = CompletableDeferred()
        val gated = holder(useCases().copy(resolveBaseBranch = { pass(); null }))
        advanceUntilIdle()
        gated.create("TASK-9", BranchType.FEATURE, startRef = "main")
        gated.create("TASK-10", BranchType.FEATURE)
        runCurrent()
        assertTrue(gated.isBusy)
        gate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, gated.worktrees.count { it.path.endsWith("TASK-9") || it.path.endsWith("TASK-10") })
    }

    @Test
    fun `a forced removal that still fails reports it and a plain git failure isn't offered a forced retry`() = runTest(dispatcher) {
        var stderr = "contains modified or untracked files, use --force to delete it"
        respond = { command -> if (command.take(3) == listOf("git", "worktree", "remove")) failure(command, stderr = stderr) else null }
        val holder = holder()
        advanceUntilIdle()

        holder.remove(holder.task, branchToDelete = null, force = true)
        advanceUntilIdle()
        assertIs<WorktreeError.GitCommandFailed>(holder.removeError)
        assertNull(holder.worktreePendingRemoval)

        stderr = "fatal: something else"
        holder.remove(holder.task, branchToDelete = null)
        advanceUntilIdle()
        assertIs<WorktreeError.GitCommandFailed>(holder.removeError)
        assertNull(holder.worktreePendingRemoval)
    }

    @Test
    fun `removing keeps a branch it couldn't delete, with the reason, and the forced deletion can fail`() = runTest(dispatcher) {
        var deleteWorks = false
        respond = { command ->
            when {
                command.take(2) == listOf("git", "merge-base") -> success(command)
                command.take(3) == listOf("git", "branch", "-D") && !deleteWorks -> failure(command, stderr = "branch is checked out")
                else -> null
            }
        }
        val holder = holder()
        advanceUntilIdle()

        holder.remove(holder.task, branchToDelete = "feature/TASK-123")
        advanceUntilIdle()
        assertEquals(ProjectWorktrees.KeptBranchNotice("feature/TASK-123", "branch is checked out"), holder.keptBranchNotice)

        holder.requestForcedBranchDeletion()
        holder.confirmForcedBranchDeletion()
        advanceUntilIdle()
        assertFalse(holder.isConfirmingForcedBranchDeletion)
        assertEquals(ProjectWorktrees.KeptBranchNotice("feature/TASK-123", "branch is checked out"), holder.keptBranchNotice)
        assertNull(holder.success)
    }

    @Test
    fun `the forced deletion can't be dismissed, cancelled or repeated while it runs, and its failure is reported`() = runTest(dispatcher) {
        val failure = IOException("git crashed")
        respond = { command -> if (command.take(2) == listOf("git", "merge-base")) failure(command, exitCode = 1, stderr = "") else null }
        gate = CompletableDeferred()
        val failing = useCases().copy(deleteBranch = DeleteBranchUseCase(config, object : ShellCommandExecutor {
            override suspend fun execute(command: List<String>, workingDirectory: String?): CommandResult {
                pass()
                throw failure
            }
        }))
        val holder = holder(failing)
        advanceUntilIdle()
        holder.confirmForcedBranchDeletion()
        holder.requestForcedBranchDeletion()
        assertFalse(holder.isConfirmingForcedBranchDeletion)

        holder.remove(holder.task, branchToDelete = "feature/TASK-123")
        advanceUntilIdle()
        holder.requestForcedBranchDeletion()
        holder.confirmForcedBranchDeletion()
        runCurrent()
        holder.confirmForcedBranchDeletion()
        holder.cancelForcedBranchDeletion()
        holder.dismissKeptBranchNotice()
        assertTrue(holder.isConfirmingForcedBranchDeletion)
        gate!!.complete(Unit)
        advanceUntilIdle()

        assertFalse(holder.isConfirmingForcedBranchDeletion)
        assertEquals(failure, holder.error)
    }

    @Test
    fun `a detached worktree keeps no branch notice`() = runTest(dispatcher) {
        respond = { command ->
            when {
                command == listCommand -> success(command, "worktree $base\nHEAD abc\nbranch refs/heads/main\n\nworktree $path\nHEAD abc\ndetached")
                command.take(2) == listOf("git", "merge-base") -> failure(command, exitCode = 1, stderr = "")
                else -> null
            }
        }
        val holder = holder()
        advanceUntilIdle()

        holder.remove(holder.task, branchToDelete = "feature/TASK-123")
        advanceUntilIdle()

        assertNull(holder.keptBranchNotice)
    }

    private val gatedExecutor = object : ShellCommandExecutor {
        override suspend fun execute(command: List<String>, workingDirectory: String?): CommandResult {
            pass()
            return executor.execute(command, workingDirectory)
        }
    }

    private val merged: (List<String>) -> CommandResult? = { command ->
        when {
            command.getOrNull(1) == "symbolic-ref" -> success(command, "origin/main\n")
            command == listOf("git", "rev-list", "--left-right", "--count", "origin/main...HEAD") -> success(command, "2\t0\n")
            command.getOrNull(1) == "rev-list" -> failure(command, stderr = "no upstream")
            else -> null
        }
    }

    private fun toolsWith(detected: DetectedTools, terminal: TerminalId? = null): ToolUseCases {
        val preferences = ToolsPreferences(FakeToolPreferenceRepository(terminal = terminal), FakeToolDetector(detected))
        runBlocking { preferences.refresh() }
        return ToolUseCases.NONE.copy(
            preferences = preferences,
            openInTerminal = { _, _, _ -> Result.success(Unit) },
            openInEditor = { _, _ -> Result.success(Unit) },
            openFolder = { Result.success(Unit) },
        )
    }

    @Test
    fun `a simulator repository without orphans and without candidates has nothing to clean up`() = runTest(dispatcher) {
        respond = { command -> if (command == statusCommand) failure(command, stderr = "unreadable") else null }
        val holder = holder(useCases().copy(simulator = SimulatorUseCases.NONE.copy(declared = { true })))
        advanceUntilIdle()

        assertTrue(holder.isSimulatorRepository)
        assertFalse(holder.canCleanUp)
    }

    @Test
    fun `removing and cleaning up keep the holder busy`() = runTest(dispatcher) {
        respond = merged
        gate = CompletableDeferred()
        val holder = holder(useCases().copy(remove = RemoveWorktreeUseCase(config, gatedExecutor), cleanUp = CleanUpWorktreesUseCase(config, gatedExecutor)))
        advanceUntilIdle()
        assertFalse(holder.isBusy)

        holder.remove(holder.task, branchToDelete = null)
        runCurrent()
        assertTrue(holder.isRemoving)
        assertTrue(holder.isBusy)
        gate!!.complete(Unit)
        advanceUntilIdle()

        gate = CompletableDeferred()
        holder.refresh()
        advanceUntilIdle()
        gate!!.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun `a cleanup keeps the holder busy while it runs`() = runTest(dispatcher) {
        respond = merged
        val holder = holder(useCases().copy(cleanUp = CleanUpWorktreesUseCase(config, gatedExecutor)))
        advanceUntilIdle()
        gate = CompletableDeferred()

        backgroundScope.launch { holder.cleanUp(CleanupOptions()) }
        runCurrent()
        assertTrue(holder.isCleaningUp)
        assertTrue(holder.isBusy)
        gate!!.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun `an index inside refs reloads the list`() = runTest(dispatcher) {
        val holder = holder()
        advanceUntilIdle()
        holder.checkFingerprint()

        gitFiles.stamps["$base/.git/refs/index"] = 1

        assertEquals(FingerprintCheck.RELOADED, holder.checkFingerprint())
        advanceUntilIdle()
    }

    @Test
    fun `a size delivered after its worktree left the list is dropped`() = runTest(dispatcher) {
        val sizeScheduler = TestCoroutineScheduler()
        respond = { command -> if (command == listCommand) success(command, "worktree $path\nHEAD abc\nbranch refs/heads/feature/TASK-123\n\nworktree $base\nHEAD abc\nbranch refs/heads/main") else null }
        val sized = useCases(sizes = FakeFileManager(existing = listOf(base, path), sizes = mapOf(path to 10L, base to 20L)))
            .copy(measureSize = MeasureWorktreeSizeUseCase(FakeFileManager(existing = listOf(base, path), sizes = mapOf(path to 10L, base to 20L)), StandardTestDispatcher(sizeScheduler)))
        val holder = holder(sized)
        advanceUntilIdle()

        respond = { command -> if (command == listCommand) success(command, "worktree $base\nHEAD abc\nbranch refs/heads/main") else null }
        holder.refresh()
        advanceUntilIdle()
        sizeScheduler.advanceUntilIdle()
        advanceUntilIdle()
        sizeScheduler.advanceUntilIdle()
        advanceUntilIdle()

        assertFalse(path in holder.sizes)
        assertTrue(base in holder.sizes)
    }

    @Test
    fun `a failing compose build is reported`() = runTest(dispatcher) {
        val failure = IOException("docker down")
        val holder = holder(useCases().copy(compose = ComposeUseCases(provider = { DockerComposeProvider() }, run = { _, _ -> Result.failure(failure) })))
        advanceUntilIdle()

        holder.buildComposeServices(holder.task)
        advanceUntilIdle()

        assertEquals(failure, holder.actionErrors[path])
    }

    @Test
    fun `opening in Herdr or Orca runs once at a time and focuses the agent waiting for the user`() = runTest(dispatcher) {
        val opened = mutableListOf<String>()
        val focused = mutableListOf<String>()
        gate = CompletableDeferred()
        val holder = holder(
            useCases().copy(
                herdr = HerdrUseCases.NONE.copy(
                    open = { worktree, _ -> opened += worktree.path; pass(); Result.success(HerdrOpening(HerdrOutcome.OPENED, "w1:p1", "Claude")) },
                    focusAgent = { pane -> focused += pane; Result.success(Unit) },
                ),
                orca = OrcaUseCases.NONE.copy(open = { worktree, _ -> opened += "orca:${worktree.path}"; pass(); Result.success(OrcaOpening(OrcaOutcome.FOCUSED, "term_1", "claude")) }),
            ),
        )
        advanceUntilIdle()

        holder.openInHerdr(holder.task)
        holder.openInHerdr(holder.task)
        holder.openInOrca(holder.task)
        holder.openInOrca(holder.task)
        assertEquals(setOf(OpenTarget.HERDR, OpenTarget.ORCA), holder.actionCatalog(holder.task, EnglishStrings).opens.mapNotNull { it.id as? ActionId.Open }.map { it.target }.toSet().intersect(setOf(OpenTarget.HERDR, OpenTarget.ORCA)))
        runCurrent()
        gate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf(path, "orca:$path"), opened)

        holder.applyAgents(
            mapOf(
                path to listOf(
                    RunningAgent("codex", AgentState.WORKING),
                    RunningAgent("claude", AgentState.WORKING, herdrPaneId = "w1:p2"),
                    RunningAgent("claude", AgentState.BLOCKED, herdrPaneId = "w1:p3"),
                ),
            ),
        )
        holder.openInHerdr(holder.task)
        advanceUntilIdle()
        holder.applyAgents(mapOf(path to listOf(RunningAgent("claude", AgentState.WORKING, herdrPaneId = "w1:p2"))))
        holder.openInHerdr(holder.task)
        advanceUntilIdle()
        assertEquals(listOf("w1:p3", "w1:p2"), focused)
    }

    @Test
    fun `focusing the agent needs an agent to focus and doesn't refocus while opening`() = runTest(dispatcher) {
        gate = CompletableDeferred()
        var focuses = 0
        val holder = holder(
            useCases().copy(
                herdr = HerdrUseCases.NONE.copy(focusAgent = { focuses++; pass(); Result.success(Unit) }),
            ),
        )
        advanceUntilIdle()
        assertFalse(holder.focusAgent(holder.task))

        holder.applyAgents(mapOf(path to listOf(RunningAgent("claude", AgentState.BLOCKED, herdrPaneId = "w1:p3"))))
        assertTrue(holder.focusAgent(holder.task))
        assertTrue(holder.focusAgent(holder.task))
        runCurrent()
        gate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, focuses)
    }

    @Test
    fun `actions open every tool, choose agents and ignore what doesn't apply`() = runTest(dispatcher) {
        val codex = AgentLaunchConfig(name = "Codex", agentCommand = "codex", useHeadroom = false)
        val opened = mutableListOf<String>()
        val tools = toolsWith(DetectedTools(terminals = listOf(TerminalId.TERMINAL), editors = listOf(EditorId.VS_CODE)), terminal = TerminalId.TERMINAL).copy(
            openInTerminal = { _, _, _ -> opened += "terminal"; Result.success(Unit) },
            openInEditor = { _, _ -> opened += "editor"; Result.success(Unit) },
            openFolder = { opened += "folder"; Result.success(Unit) },
        )
        val holder = holder(useCases().copy(tools = tools, agents = AgentUseCases(config.copy(additionalAgents = listOf(codex)), AgentChoices())))
        advanceUntilIdle()
        val main = holder.worktrees.first { it.isMain }

        holder.perform(holder.task, ActionId.Open(OpenTarget.TERMINAL))
        holder.perform(holder.task, ActionId.Open(OpenTarget.EDITOR))
        holder.perform(holder.task, ActionId.Open(OpenTarget.FOLDER))
        holder.perform(holder.task, ActionId.Open(OpenTarget.CLAUDE_DESKTOP))
        holder.perform(holder.task, ActionId.CopyLaunchCommand, agent = "Codex")
        holder.perform(holder.task, ActionId.ChooseAgent("Unknown"))
        holder.perform(holder.task, ActionId.ChooseAgent("Codex"))
        holder.perform(main, ActionId.RemoveWorktree)
        holder.perform(holder.task, ActionId.CopyPath)
        advanceUntilIdle()

        assertEquals(listOf("terminal", "editor", "folder"), opened)
        assertEquals("Codex", holder.agentFor(holder.task).displayName)
        assertNull(holder.worktreePendingRemoval)
        assertEquals("Terminal", holder.actionTools(EnglishStrings).terminal)
    }

    @Test
    fun `tools that aren't available don't open and a terminal shown without a choice reads generically`() = runTest(dispatcher) {
        val holder = holder(useCases().copy(tools = toolsWith(DetectedTools())))
        advanceUntilIdle()

        holder.openInTerminal(holder.task)
        holder.chooseTerminal(TerminalId.TERMINAL)
        holder.openInEditor(holder.task)
        holder.openInClaudeDesktop(holder.task)
        advanceUntilIdle()
        assertNull(holder.terminalChoiceFor)
        assertNull(holder.actionTools(EnglishStrings).terminal)

        val choosing = holder(useCases().copy(tools = toolsWith(DetectedTools(terminals = listOf(TerminalId.TERMINAL)))))
        advanceUntilIdle()
        assertEquals(EnglishStrings.terminalToolLabel.lowercase(), choosing.actionTools(EnglishStrings).terminal)
    }

    @Test
    fun `a removal failing outside git isn't offered a forced retry`() = runTest(dispatcher) {
        val failure = IOException("disk gone")
        val throwing = object : ShellCommandExecutor {
            override suspend fun execute(command: List<String>, workingDirectory: String?): CommandResult = throw failure
        }
        val holder = holder(useCases().copy(remove = RemoveWorktreeUseCase(config, throwing)))
        advanceUntilIdle()

        holder.remove(holder.task, branchToDelete = null)
        advanceUntilIdle()

        assertEquals(failure, holder.removeError)
        assertNull(holder.worktreePendingRemoval)
    }

    @Test
    fun `a cleanup that can't read agents or daemons still removes, and stopping removed emulators can fail`() = runTest(dispatcher) {
        respond = merged
        val failure = IOException("unreadable")
        val holder = holder(
            useCases().copy(
                detectAgents = { Result.failure(failure) },
                emulator = EmulatorUseCases.NONE.copy(
                    declared = { true },
                    readStates = { Result.success(mapOf(path to WorktreeEmulator("emulator-5554", EmulatorState.RUNNING), base to WorktreeEmulator("emulator-5556", EmulatorState.STOPPED))) },
                    stopSerials = { Result.failure(failure) },
                ),
                gradle = GradleUseCases(
                    provider = { GradleProvider() },
                    listDaemons = ListGradleDaemonsUseCase(FakeGradleDaemonSource(failure = WorktreeError.ProcessListingFailed(listOf("jps"), 1)), FakeFileManager()),
                    stopDaemons = StopGradleDaemonsUseCase(FakeGradleDaemonSource(), executor),
                    isBuildCacheEnabled = { true },
                ),
                maven = MavenUseCases(provider = { MavenProvider() }, listDaemons = { Result.failure(failure) }),
            ),
        )
        advanceUntilIdle()

        val cleaned = holder.cleanUp(CleanupOptions())
        advanceUntilIdle()

        assertEquals(1, cleaned?.removedCount)
        assertEquals(0, cleaned?.stoppedEmulators)
    }

    @Test
    fun `a cleanup of chosen worktrees deletes only their simulators and no derived data`() = runTest(dispatcher) {
        respond = merged
        val failure = IOException("simctl failed")
        val deleted = mutableListOf<List<String>>()
        var orphansWork = true
        val holder = holder(
            useCases().copy(
                simulator = SimulatorUseCases.NONE.copy(
                    declared = { true },
                    readStates = { Result.success(mapOf(path to WorktreeSimulator("SIM-1", "ShogunAi · main-repo · TASK-123", SimulatorState.SHUTDOWN))) },
                    orphans = { if (orphansWork) Result.success(emptyList()) else Result.failure(failure) },
                    delete = { udids -> deleted += udids; Result.failure(failure) },
                ),
                xcode = XcodeUseCases(
                    provider = { XcodeProvider() },
                    supported = { true },
                    list = ListDerivedDataUseCase(FakeDerivedDataSource()),
                    deleteOrphans = DeleteOrphanedDerivedDataUseCase(FakeDerivedDataSource()),
                ),
            ),
        )
        advanceUntilIdle()
        orphansWork = false

        val cleaned = holder.cleanUp(CleanupOptions(), only = setOf(path))
        advanceUntilIdle()

        assertEquals(listOf(listOf("SIM-1")), deleted)
        assertEquals(0, cleaned?.deletedSimulators)
        assertEquals(0, cleaned?.deletedDerivedDataFolders)
    }

    @Test
    fun `a cleanup with nothing to delete deletes no simulators`() = runTest(dispatcher) {
        respond = merged
        val deleted = mutableListOf<List<String>>()
        val holder = holder(
            useCases().copy(
                simulator = SimulatorUseCases.NONE.copy(declared = { true }, delete = { udids -> deleted += udids; Result.success(udids) }),
            ),
        )
        advanceUntilIdle()

        holder.cleanUp(CleanupOptions())
        advanceUntilIdle()

        assertTrue(deleted.isEmpty())
    }

    @Test
    fun `a failing cleanup is reported`() = runTest(dispatcher) {
        respond = merged
        val failure = IOException("disk gone")
        val throwing = object : ShellCommandExecutor {
            override suspend fun execute(command: List<String>, workingDirectory: String?): CommandResult = throw failure
        }
        val holder = holder(useCases().copy(cleanUp = CleanUpWorktreesUseCase(config, throwing)))
        advanceUntilIdle()

        val cleaned = holder.cleanUp(CleanupOptions())
        advanceUntilIdle()

        assertNull(cleaned)
        assertTrue(holder.error != null)
    }

    @Test
    fun `opening or focusing before the agents could be read opens a new session`() = runTest(dispatcher) {
        val opened = mutableListOf<String>()
        val throwing = object : ShellCommandExecutor {
            override suspend fun execute(command: List<String>, workingDirectory: String?): CommandResult = throw IOException("git gone")
        }
        val holder = holder(
            useCases().copy(
                detectAgents = { Result.failure(IOException("ps failed")) },
                resolveMainBranch = ResolveMainBranchUseCase(config, throwing),
                herdr = HerdrUseCases.NONE.copy(open = { worktree, _ -> opened += worktree.path; Result.success(HerdrOpening(HerdrOutcome.OPENED, "w1:p1", "Claude")) }),
            ),
        )
        advanceUntilIdle()
        holder.loadBranches()
        advanceUntilIdle()

        assertNull(holder.agents)
        assertNull(holder.defaultStartRef)
        assertFalse(holder.focusAgent(holder.task))
        holder.openInHerdr(holder.task)
        advanceUntilIdle()
        assertEquals(listOf(path), opened)
    }
}
