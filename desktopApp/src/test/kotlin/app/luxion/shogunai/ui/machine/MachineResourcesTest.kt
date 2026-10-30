package app.luxion.shogunai.ui.machine

import app.luxion.shogunai.domain.model.RefreshCadence
import app.luxion.shogunai.ui.WindowActivity
import app.luxion.shogunai.domain.model.PackageCacheSizes
import app.luxion.shogunai.domain.model.CapacityProfile
import app.luxion.shogunai.domain.model.DesktopTurn
import app.luxion.shogunai.domain.model.DesktopTurnRelease
import app.luxion.shogunai.domain.model.DesktopTurnState
import app.luxion.shogunai.domain.model.PoolHolder
import app.luxion.shogunai.domain.model.PoolHolderState
import app.luxion.shogunai.domain.model.PoolRelease
import app.luxion.shogunai.domain.model.PoolReleaseReason
import app.luxion.shogunai.domain.model.PoolUsage
import app.luxion.shogunai.domain.model.ProcessIdentity
import app.luxion.shogunai.domain.model.ResourceKind
import app.luxion.shogunai.domain.model.ResourceSettings
import app.luxion.shogunai.domain.model.WaitTicket
import app.luxion.shogunai.domain.usecase.DeviceStart
import app.luxion.shogunai.domain.usecase.GradleTrim
import app.luxion.shogunai.domain.usecase.ResourceAcquisition
import app.luxion.shogunai.MachineUseCases
import app.luxion.shogunai.domain.FakeGradleDaemonSource.Companion.gradleDaemon
import app.luxion.shogunai.domain.FakeGradleDaemonSource.Companion.kotlinDaemon
import app.luxion.shogunai.domain.model.DaemonKind
import app.luxion.shogunai.domain.model.DaemonKeptReason
import app.luxion.shogunai.domain.model.DaemonProcess
import app.luxion.shogunai.domain.model.DaemonState
import app.luxion.shogunai.domain.model.DaemonStopReport
import app.luxion.shogunai.domain.model.AttributedDerivedData
import app.luxion.shogunai.domain.model.DerivedDataDeletion
import app.luxion.shogunai.domain.model.DerivedDataFolder
import app.luxion.shogunai.domain.model.DerivedDataOwner
import app.luxion.shogunai.domain.model.DerivedDataQuota
import app.luxion.shogunai.domain.model.DerivedDataQuotaMode
import app.luxion.shogunai.domain.model.MeasuredSize
import app.luxion.shogunai.domain.model.DerivedDataTrim
import app.luxion.shogunai.domain.model.DerivedDataListing
import app.luxion.shogunai.domain.model.EmulatorState
import app.luxion.shogunai.domain.model.KeptDaemon
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.SimulatorDevice
import app.luxion.shogunai.domain.model.SimulatorState
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeEmulator
import app.luxion.shogunai.domain.model.WorktreeSimulator
import app.luxion.shogunai.domain.usecase.ProjectOwner
import app.luxion.shogunai.ui.i18n.EnglishStrings
import kotlinx.coroutines.CompletableDeferred
import kotlin.coroutines.ContinuationInterceptor
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class MachineResourcesTest {

    private val dispatcher = StandardTestDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private fun machineTest(body: suspend TestScope.() -> Unit) = runTest(dispatcher) {
        try {
            body()
        } finally {
            scope.coroutineContext[Job]?.cancelChildren()
        }
    }

    private val idle = DaemonState.Idle(Instant.parse("2026-01-01T00:00:00Z"))
    private val now = Instant.parse("2026-01-01T10:42:00Z")

    private fun project(id: String) = Project(
        id = id,
        name = id,
        config = ProjectConfig(
            baseRepositoryPath = "/home/dev/projects/$id",
            worktreesRoot = "/home/dev/projects",
            secretFiles = emptyList(),
        ),
    )

    private val apiWorktree = Worktree(path = "/home/dev/projects/api-TASK-9", branch = "feature/TASK-9")
    private val mainWorktree = Worktree(path = "/home/dev/projects/main-repo", branch = "develop", isMain = true)

    private fun machineProject(
        id: String,
        worktrees: List<Worktree>?,
        vararg declares: MachineResource,
        compilationCache: Boolean = false,
    ) = MachineProject(project(id), worktrees, declares.toSet(), compilationCache = compilationCache)

    private class Calls {
        var daemons = emptyList<DaemonProcess>()
        var daemonFailure: Throwable? = null
        var daemonListings = 0
        var gradleLimit: Int? = null
        var daemonStartsBeforeEachListing = false
        private var nextStartedPid = 100L
        var emulatorReads = 0
        var simulatorReads = 0
        var derivedDataListings = 0
        val stoppedDaemons = mutableListOf<List<Long>?>()
        val startedEmulators = mutableListOf<String>()
        val stoppedSerials = mutableListOf<List<String>>()
        val deletedSimulators = mutableListOf<List<String>>()
        var derivedDataDeletions = 0
        val measured = mutableListOf<String>()
        var cacheMeasurements = 0
        var measureGate: CompletableDeferred<Unit>? = null
        val remeasures = mutableListOf<Boolean>()
        var trims = 0
        val derivedDataTrims = mutableListOf<Long>()
        val dispatchers = mutableListOf<ContinuationInterceptor?>()

        fun listDaemonsNow(): List<DaemonProcess> {
            if (daemonStartsBeforeEachListing) daemons = daemons + gradleDaemon(nextStartedPid++, DaemonState.Idle(Instant.EPOCH), memoryBytes = 1_000L)
            return daemons
        }

        suspend fun recordDispatcher() {
            dispatchers += currentCoroutineContext()[ContinuationInterceptor]
        }
    }

    private fun useCases(
        calls: Calls,
        emulators: Map<String, WorktreeEmulator> = emptyMap(),
        running: Map<String, EmulatorState> = emptyMap(),
        simulators: Map<String, WorktreeSimulator> = emptyMap(),
        orphans: List<SimulatorDevice> = emptyList(),
        derivedData: DerivedDataListing = DerivedDataListing(emptyList(), null),
        stopReport: DaemonStopReport = DaemonStopReport(),
        sizes: Map<String, Long> = emptyMap(),
        packages: Map<String, Long> = emptyMap(),
        packageCaches: PackageCacheSizes = PackageCacheSizes(),
        remembered: Map<String, Long> = emptyMap(),
        resolveQuota: suspend (ResourceSettings, Long) -> DerivedDataQuota = ::fixedQuotaOnly,
        startEmulator: suspend (ProjectConfig, String) -> Result<DeviceStart<WorktreeEmulator>> = { _, path ->
            calls.startedEmulators += path
            Result.success(DeviceStart(WorktreeEmulator("emulator-5554", EmulatorState.RUNNING), ResourceAcquisition(true, emptyList())))
        },
        pools: Map<ResourceKind, PoolUsage> = emptyMap(),
        trim: GradleTrim = GradleTrim(limit = null),
        nextTrim: () -> GradleTrim = { trim },
        settings: ResourceSettings = ResourceSettings(),
        derivedDataTrim: (Long) -> DerivedDataTrim = { DerivedDataTrim(it, 0L, 0L) },
        stopDaemons: suspend (List<Long>?) -> Result<DaemonStopReport> = { pids ->
            calls.stoppedDaemons += pids
            Result.success(stopReport)
        },
    ) = MachineUseCases(
        listDaemons = { calls.recordDispatcher(); calls.daemonListings++; calls.daemonFailure?.let { Result.failure(it) } ?: Result.success(calls.listDaemonsNow()) },
        stopDaemons = stopDaemons,
        listDerivedData = {
            calls.derivedDataListings++
            Result.success(derivedData)
        },
        measureDerivedData = { folders, remeasure ->
            calls.remeasures += remeasure
            flow {
                folders.forEach { folder -> remembered[folder.path]?.let { emit(MeasuredSize(folder.path, it, provisional = remeasure)) } }
                folders.filter { remeasure || it.path !in remembered }.forEach { folder ->
                    calls.measured += folder.path
                    calls.measureGate?.await()
                    emit(MeasuredSize(folder.path, sizes[folder.path] ?: 1_000L, packagesBytes = packages[folder.path]))
                }
            }
        },
        measureCompilationCache = { calls.cacheMeasurements++; Result.success(7_000L) },
        measurePackageCaches = { Result.success(packageCaches) },
        deleteOrphanedDerivedData = { calls.derivedDataDeletions++; Result.success(DerivedDataDeletion()) },
        readEmulatorStates = { _, _ -> calls.recordDispatcher(); calls.emulatorReads++; Result.success(emulators) },
        readRunningEmulators = { calls.recordDispatcher(); Result.success(running) },
        startEmulator = startEmulator,
        readPool = { kind, _, _, listed ->
            calls.recordDispatcher()
            val gradleLimit = calls.gradleLimit
            if (kind == ResourceKind.GRADLE_DAEMON && gradleLimit != null) {
                Result.success(gradlePool(gradleLimit, listed ?: calls.listDaemonsNow()))
            } else {
                pools[kind]?.let { Result.success(it) } ?: Result.failure(IllegalStateException())
            }
        },
        trimGradleDaemons = { calls.recordDispatcher(); calls.trims++; Result.success(nextTrim()) },
        resourceSettings = { settings },
        trimDerivedData = { quota, _, _ -> calls.derivedDataTrims += quota; Result.success(derivedDataTrim(quota)) },
        stopEmulator = { _, _ -> Result.success(null) },
        stopEmulatorSerials = { _, serials -> calls.stoppedSerials += serials; Result.success(emptyList()) },
        readSimulatorStates = { _, _ -> calls.recordDispatcher(); calls.simulatorReads++; Result.success(simulators) },
        startSimulator = { _, _ -> Result.failure(IllegalStateException()) },
        stopSimulator = { _, _ -> Result.success(null) },
        readSimulatorOrphans = { Result.success(orphans) },
        deleteSimulators = { udids -> calls.deletedSimulators += udids; Result.success(udids) },
        resolveDerivedDataQuota = resolveQuota,
    )

    private fun gradlePool(limit: Int, daemons: List<DaemonProcess>) = PoolUsage(
        ResourceKind.GRADLE_DAEMON,
        limit,
        limit,
        daemons.filter { it.kind == DaemonKind.GRADLE }.map {
            PoolHolder(ResourceKind.GRADLE_DAEMON, null, null, PoolHolderState.IDLE, true, null, reference = it.pid.toString())
        },
    )

    private suspend fun fixedQuotaOnly(settings: ResourceSettings, usage: Long): DerivedDataQuota =
        DerivedDataQuota(settings.quotaMode, settings.fixedDerivedDataQuotaBytes.takeIf { settings.quotaMode == DerivedDataQuotaMode.FIXED })

    private fun machine(
        useCases: MachineUseCases,
        projects: List<MachineProject>,
        existing: Set<String> = emptySet(),
        onDevicesChanged: suspend (String) -> Unit = {},
        desktopTurn: DesktopTurnWatch = DesktopTurnWatch.NONE,
        ioDispatcher: CoroutineDispatcher = dispatcher,
        cadence: RefreshCadence = RefreshCadence.BALANCED,
        clock: () -> Instant = { now },
    ) = MachineResources(
        scope = scope,
        useCases = useCases,
        projects = { projects },
        pathExists = { it in existing },
        onDevicesChanged = onDevicesChanged,
        desktopTurn = desktopTurn,
        ioDispatcher = ioDispatcher,
        cadence = { cadence },
        clock = clock,
    )

    @Test
    fun `entries are only visible for declared providers`() = machineTest {
        val machine = machine(
            useCases(Calls()),
            listOf(
                machineProject("main-repo", listOf(mainWorktree), MachineResource.GRADLE_DAEMONS),
                machineProject("api", listOf(apiWorktree)),
            ),
        )

        assertEquals(listOf(MachineResource.GRADLE_DAEMONS), machine.visibleResources)
    }

    @Test
    fun `nothing is visible when no project declares a provider`() = machineTest {
        val machine = machine(useCases(Calls()), listOf(machineProject("main-repo", listOf(mainWorktree))))

        assertTrue(machine.visibleResources.isEmpty())
    }

    @Test
    fun `loaders only run while their view is shown`() = machineTest {
        val calls = Calls().apply { daemons = listOf(gradleDaemon(10, idle)) }
        val machine = machine(
            useCases(calls),
            listOf(machineProject("main-repo", listOf(mainWorktree), MachineResource.GRADLE_DAEMONS, MachineResource.ANDROID_EMULATORS)),
        )
        advanceUntilIdle()
        assertEquals(0, calls.daemonListings)

        machine.show(MachineResource.GRADLE_DAEMONS)
        runCurrent()
        assertEquals(1, calls.daemonListings)
        advanceTimeBy(5.seconds)
        runCurrent()
        assertEquals(2, calls.daemonListings)

        machine.show(null)
        advanceTimeBy(30.seconds)
        runCurrent()
        assertEquals(2, calls.daemonListings)
        assertEquals(0, calls.emulatorReads)
    }

    @Test
    fun `daemons are attributed across projects, removed worktrees and outside folders`() = machineTest {
        val daemons = listOf(
            gradleDaemon(10, idle, lastBuildDirectory = "${apiWorktree.path}/app"),
            gradleDaemon(11, idle, lastBuildDirectory = "/home/dev/projects/gone"),
            gradleDaemon(12, idle, lastBuildDirectory = "/home/dev/other"),
        )
        val machine = machine(
            useCases(Calls().apply { this.daemons = daemons }),
            listOf(
                machineProject("main-repo", listOf(mainWorktree), MachineResource.GRADLE_DAEMONS),
                machineProject("api", listOf(apiWorktree), MachineResource.GRADLE_DAEMONS),
            ),
            existing = setOf("${apiWorktree.path}/app", "/home/dev/other"),
        )

        machine.show(MachineResource.GRADLE_DAEMONS)
        runCurrent()

        val owners = assertIs<MachineLoad.Loaded<List<MachineDaemon>>>(machine.gradleDaemons).value.map { it.owner }
        assertEquals(
            listOf(ProjectOwner.Owned("api", apiWorktree.path), ProjectOwner.RemovedWorktree, ProjectOwner.Outside),
            owners,
        )
    }

    @Test
    fun `a stop result is kept until dismissed and the stop rules are delegated`() = machineTest {
        val calls = Calls()
        val report = DaemonStopReport(
            stopped = listOf(gradleDaemon(10, idle)),
            kept = listOf(KeptDaemon(11, app.luxion.shogunai.domain.model.DaemonKind.GRADLE, DaemonKeptReason.BUSY)),
        )
        val machine = machine(
            useCases(calls, stopReport = report),
            listOf(machineProject("main-repo", listOf(mainWorktree), MachineResource.GRADLE_DAEMONS)),
        )
        machine.show(MachineResource.GRADLE_DAEMONS)
        runCurrent()

        machine.stopIdleGradleDaemons()
        runCurrent()
        machine.stopGradleDaemon(10)
        runCurrent()

        assertEquals(listOf<List<Long>?>(null, listOf(10L)), calls.stoppedDaemons)
        assertEquals(report, machine.daemonStopReport)
        assertFalse(machine.isStoppingDaemons)

        machine.dismissDaemonStopReport()
        assertNull(machine.daemonStopReport)
    }

    @Test
    fun `running emulators that no worktree owns are listed as unowned`() = machineTest {
        val owned = mapOf(apiWorktree.path to WorktreeEmulator("emulator-5554", EmulatorState.RUNNING))
        val calls = Calls()
        val machine = machine(
            useCases(
                calls,
                emulators = owned,
                running = mapOf("emulator-5554" to EmulatorState.RUNNING, "emulator-5560" to EmulatorState.RUNNING),
            ),
            listOf(machineProject("api", listOf(apiWorktree), MachineResource.ANDROID_EMULATORS)),
        )

        machine.show(MachineResource.ANDROID_EMULATORS)
        runCurrent()

        assertEquals(listOf(UnownedEmulator("emulator-5560", EmulatorState.RUNNING)), machine.unownedEmulators)
        machine.stopUnownedEmulator("emulator-5560")
        runCurrent()
        assertEquals(listOf(listOf("emulator-5560")), calls.stoppedSerials)
    }

    @Test
    fun `every worktree of a declaring project is listed even without a reserved emulator`() = machineTest {
        val machine = machine(
            useCases(Calls()),
            listOf(
                machineProject("api", listOf(apiWorktree, mainWorktree), MachineResource.ANDROID_EMULATORS),
                machineProject("other", listOf(Worktree("/home/dev/projects/other", "develop")), MachineResource.GRADLE_DAEMONS),
            ),
        )

        machine.show(MachineResource.ANDROID_EMULATORS)
        runCurrent()

        val entries = assertIs<MachineLoad.Loaded<List<MachineEmulator>>>(machine.worktreeEmulators).value
        assertEquals(listOf(apiWorktree.path, mainWorktree.path), entries.map { it.worktreePath })
        assertTrue(entries.all { it.emulator == null })
    }

    @Test
    fun `starting a worktree emulator delegates and tells the board to refresh that project`() = machineTest {
        val calls = Calls()
        val changed = mutableListOf<String>()
        val machine = machine(
            useCases(calls),
            listOf(machineProject("api", listOf(apiWorktree), MachineResource.ANDROID_EMULATORS)),
            onDevicesChanged = { changed += it },
        )

        machine.startEmulator("api", apiWorktree.path)
        assertTrue(machine.isBusy("api", apiWorktree.path))
        runCurrent()

        assertEquals(listOf(apiWorktree.path), calls.startedEmulators)
        assertEquals(listOf("api"), changed)
        assertFalse(machine.isBusy("api", apiWorktree.path))
    }

    @Test
    fun `a failed device action keeps its error until retried, and the retry clears it on success`() = machineTest {
        val calls = Calls()
        var failing = true
        val failure = IllegalStateException("adb offline")
        val machine = machine(
            useCases(
                calls,
                startEmulator = { _, path ->
                    calls.startedEmulators += path
                    if (failing) {
                        Result.failure(failure)
                    } else {
                        Result.success(DeviceStart(WorktreeEmulator("emulator-5554", EmulatorState.RUNNING), ResourceAcquisition(true, emptyList())))
                    }
                },
            ),
            listOf(machineProject("api", listOf(apiWorktree), MachineResource.ANDROID_EMULATORS)),
        )

        machine.startEmulator("api", apiWorktree.path)
        runCurrent()
        assertEquals(failure, machine.error)
        assertTrue(machine.errorRetry != null)

        failing = false
        machine.retryError()
        runCurrent()

        assertEquals(listOf(apiWorktree.path, apiWorktree.path), calls.startedEmulators)
        assertNull(machine.error)
        assertNull(machine.errorRetry)
    }

    @Test
    fun `an unrelated success keeps the error of another action`() = machineTest {
        val calls = Calls()
        val failure = IllegalStateException("adb offline")
        val machine = machine(
            useCases(calls, startEmulator = { _, _ -> Result.failure(failure) }),
            listOf(
                machineProject("api", listOf(apiWorktree), MachineResource.ANDROID_EMULATORS),
                machineProject("main-repo", listOf(mainWorktree), MachineResource.GRADLE_DAEMONS),
            ),
        )

        machine.startEmulator("api", apiWorktree.path)
        runCurrent()
        machine.stopIdleGradleDaemons()
        runCurrent()

        assertEquals(failure, machine.error)
    }

    @Test
    fun `a failed daemon stop is retried with the same pids and cleared when it succeeds`() = machineTest {
        val calls = Calls()
        var failing = true
        val machine = machine(
            useCases(
                calls,
                stopDaemons = { pids ->
                    calls.stoppedDaemons += pids
                    if (failing) Result.failure(IllegalStateException("kill failed")) else Result.success(DaemonStopReport())
                },
            ),
            listOf(machineProject("main-repo", listOf(mainWorktree), MachineResource.GRADLE_DAEMONS)),
        )

        machine.stopGradleDaemon(10)
        runCurrent()
        assertTrue(machine.error != null)

        failing = false
        machine.retryError()
        runCurrent()

        assertEquals(listOf<List<Long>?>(listOf(10L), listOf(10L)), calls.stoppedDaemons)
        assertNull(machine.error)
    }

    @Test
    fun `dismissing an error drops its retry`() = machineTest {
        val calls = Calls()
        val machine = machine(
            useCases(calls, startEmulator = { _, _ -> Result.failure(IllegalStateException("adb offline")) }),
            listOf(machineProject("api", listOf(apiWorktree), MachineResource.ANDROID_EMULATORS)),
        )
        machine.startEmulator("api", apiWorktree.path)
        runCurrent()

        machine.dismissError()

        assertNull(machine.error)
        assertNull(machine.errorRetry)
    }

    @Test
    fun `orphaned simulators are deleted through the existing use case`() = machineTest {
        val calls = Calls()
        val orphans = listOf(SimulatorDevice("U-1", "ShogunAi · main-repo · TASK-1", SimulatorState.SHUTDOWN))
        val machine = machine(
            useCases(calls, orphans = orphans),
            listOf(machineProject("main-repo", listOf(mainWorktree), MachineResource.IOS_SIMULATORS)),
        )
        machine.show(MachineResource.IOS_SIMULATORS)
        runCurrent()
        assertEquals(orphans, machine.simulatorOrphans)

        machine.deleteSimulatorOrphans()
        runCurrent()

        assertEquals(listOf(listOf("U-1")), calls.deletedSimulators)
        assertEquals(1, machine.simulatorsDeleted)
        machine.dismissSimulatorsDeleted()
        assertNull(machine.simulatorsDeleted)
    }

    @Test
    fun `derived data orphans are deleted and the listing is read again`() = machineTest {
        val calls = Calls()
        val machine = machine(
            useCases(calls),
            listOf(machineProject("main-repo", listOf(mainWorktree), MachineResource.XCODE_DERIVED_DATA, compilationCache = true)),
        )
        machine.show(MachineResource.XCODE_DERIVED_DATA)
        runCurrent()

        machine.deleteOrphanedDerivedData()
        runCurrent()

        assertEquals(1, calls.derivedDataDeletions)
        assertEquals(2, calls.derivedDataListings)
        assertTrue(machine.derivedDataDeletionReport != null)
        machine.dismissDerivedDataDeletionReport()
        assertNull(machine.derivedDataDeletionReport)
    }

    @Test
    fun `summaries are read only for declared providers and refreshed on every tick`() = machineTest {
        val calls = Calls().apply { daemons = listOf(gradleDaemon(10, idle, memoryBytes = 1_000L)) }
        val machine = machine(
            useCases(calls),
            listOf(machineProject("main-repo", listOf(mainWorktree), MachineResource.GRADLE_DAEMONS)),
        )

        val ticks = scope.launch { machine.runSummaryTicks() }
        runCurrent()
        assertEquals(mapOf(MachineResource.GRADLE_DAEMONS to MachineSummary(1, 1_000L)), machine.summaries)
        assertEquals(0, calls.simulatorReads)
        assertEquals(0, calls.derivedDataListings)

        calls.daemons = listOf(gradleDaemon(10, idle, memoryBytes = 1_000L), gradleDaemon(11, idle, memoryBytes = 2_000L))
        advanceTimeBy(30.seconds)
        runCurrent()
        assertEquals(MachineSummary(2, 3_000L), machine.summaries.getValue(MachineResource.GRADLE_DAEMONS))
        ticks.cancel()
    }

    @Test
    fun `a failed summary read is remembered with the last summary and cleared when it recovers`() = machineTest {
        val calls = Calls().apply { daemons = listOf(gradleDaemon(10, idle, memoryBytes = 1_000L)) }
        val machine = machine(
            useCases(calls),
            listOf(machineProject("main-repo", listOf(mainWorktree), MachineResource.GRADLE_DAEMONS)),
        )
        val failure = IllegalStateException("jps failed")

        machine.refreshSummaries()
        calls.daemonFailure = failure
        machine.refreshSummaries()

        assertEquals(mapOf(MachineResource.GRADLE_DAEMONS to failure), machine.summaryFailures)
        assertEquals(MachineSummary(1, 1_000L), machine.summaries.getValue(MachineResource.GRADLE_DAEMONS))

        calls.daemonFailure = null
        machine.refreshSummaries()

        assertTrue(machine.summaryFailures.isEmpty())
    }

    private fun usage(kind: ResourceKind, limit: Int?, vararg holders: Int) = PoolUsage(
        kind,
        limit,
        limit,
        holders.map { PoolHolder(kind, "/home/dev/projects/api-TASK-$it", null, PoolHolderState.RUNNING, false, null) },
    )

    @Test
    fun `while the window is hidden only the gradle daemons are trimmed and showing it refreshes the summaries`() = machineTest {
        val calls = Calls().apply { daemons = listOf(gradleDaemon(10, idle)) }
        val machine = machine(
            useCases(calls),
            listOf(machineProject("main-repo", listOf(mainWorktree), MachineResource.GRADLE_DAEMONS, MachineResource.ANDROID_EMULATORS)),
        )
        machine.setWindowActivity(WindowActivity.HIDDEN)

        val ticks = scope.launch { machine.runSummaryTicks() }
        runCurrent()
        advanceTimeBy(30.seconds)
        runCurrent()
        assertEquals(2, calls.trims)
        assertEquals(0, calls.daemonListings)
        assertTrue(machine.summaries.isEmpty())

        machine.setWindowActivity(WindowActivity.FOCUSED)
        runCurrent()
        ticks.cancel()

        assertEquals(3, calls.trims)
        assertEquals(1, calls.daemonListings)
        assertEquals(setOf(MachineResource.GRADLE_DAEMONS, MachineResource.ANDROID_EMULATORS), machine.summaries.keys)
    }

    @Test
    fun `a shown view stops refreshing while the window is hidden and refreshes when it is shown again`() = machineTest {
        val calls = Calls().apply { daemons = listOf(gradleDaemon(10, idle)) }
        val machine = machine(
            useCases(calls),
            listOf(machineProject("main-repo", listOf(mainWorktree), MachineResource.GRADLE_DAEMONS)),
        )
        machine.show(MachineResource.GRADLE_DAEMONS)
        runCurrent()
        assertEquals(1, calls.daemonListings)

        machine.setWindowActivity(WindowActivity.HIDDEN)
        advanceTimeBy(30.seconds)
        runCurrent()
        assertEquals(1, calls.daemonListings)

        machine.setWindowActivity(WindowActivity.FOCUSED)
        runCurrent()
        machine.show(null)

        assertEquals(2, calls.daemonListings)
    }

    private fun gradleMachine(calls: Calls, cadence: RefreshCadence) = machine(
        useCases(calls),
        listOf(machineProject("main-repo", listOf(mainWorktree), MachineResource.GRADLE_DAEMONS)),
        cadence = cadence,
    )

    @Test
    fun `the light cadence refreshes a shown view every fifteen seconds and the summaries every two minutes`() = machineTest {
        val calls = Calls().apply { daemons = listOf(gradleDaemon(10, idle)) }
        val machine = gradleMachine(calls, RefreshCadence.LIGHT)
        machine.show(MachineResource.GRADLE_DAEMONS)
        runCurrent()
        advanceTimeBy(60.seconds)
        runCurrent()
        machine.show(null)
        assertEquals(5, calls.daemonListings)

        calls.daemonListings = 0
        val ticks = scope.launch { machine.runSummaryTicks() }
        runCurrent()
        advanceTimeBy(119.seconds)
        runCurrent()
        assertEquals(1, calls.daemonListings)
        advanceTimeBy(1.seconds)
        runCurrent()
        ticks.cancel()

        assertEquals(2, calls.daemonListings)
    }

    @Test
    fun `an unfocused window refreshes the shown view no more often than unfocused checks`() = machineTest {
        val calls = Calls().apply { daemons = listOf(gradleDaemon(10, idle)) }
        val machine = gradleMachine(calls, RefreshCadence.BALANCED)
        machine.setWindowActivity(WindowActivity.UNFOCUSED)
        machine.show(MachineResource.GRADLE_DAEMONS)
        runCurrent()
        advanceTimeBy(60.seconds)
        runCurrent()
        machine.show(null)

        assertEquals(5, calls.daemonListings)
    }

    @Test
    fun `losing focus waits for the next refresh and regaining it refreshes the shown view at once`() = machineTest {
        val calls = Calls().apply { daemons = listOf(gradleDaemon(10, idle)) }
        val machine = gradleMachine(calls, RefreshCadence.BALANCED)
        machine.show(MachineResource.GRADLE_DAEMONS)
        runCurrent()

        machine.setWindowActivity(WindowActivity.UNFOCUSED)
        runCurrent()
        advanceTimeBy(10.seconds)
        runCurrent()
        assertEquals(1, calls.daemonListings)

        machine.setWindowActivity(WindowActivity.FOCUSED)
        runCurrent()
        machine.show(null)

        assertEquals(2, calls.daemonListings)
    }

    @Test
    fun `a view shows its pool usage against the effective limit`() = machineTest {
        val machine = machine(
            useCases(
                Calls(),
                pools = mapOf(ResourceKind.EMULATOR to usage(ResourceKind.EMULATOR, 2, 9)),
                settings = ResourceSettings(CapacityProfile.BALANCED),
            ),
            listOf(machineProject("api", listOf(apiWorktree), MachineResource.ANDROID_EMULATORS)),
        )

        machine.show(MachineResource.ANDROID_EMULATORS)
        runCurrent()

        val pool = machine.poolOf(MachineResource.ANDROID_EMULATORS)
        assertEquals(1, pool?.inUse)
        assertEquals(2, pool?.effectiveLimit)
        assertEquals(CapacityProfile.BALANCED, machine.profile)
        assertFalse(pool?.isOverLimit ?: true)
    }

    @Test
    fun `a Gradle pool above its limit is over the limit`() = machineTest {
        val calls = Calls().apply { daemons = (1L..3L).map { gradleDaemon(it, DaemonState.Busy) } }
        val machine = machine(
            useCases(calls, pools = mapOf(ResourceKind.GRADLE_DAEMON to usage(ResourceKind.GRADLE_DAEMON, 2, 1, 2, 3))),
            listOf(machineProject("api", listOf(apiWorktree), MachineResource.GRADLE_DAEMONS)),
        )

        machine.refreshSummaries()

        assertTrue(machine.poolOf(MachineResource.GRADLE_DAEMONS)?.isOverLimit == true)
        assertEquals(3, machine.summaries.getValue(MachineResource.GRADLE_DAEMONS).poolInUse)
        assertEquals(2, machine.summaries.getValue(MachineResource.GRADLE_DAEMONS).poolLimit)
    }

    @Test
    fun `the status bar, its tooltip and the Gradle view count the daemons of a single listing`() = machineTest {
        val calls = Calls().apply {
            gradleLimit = 3
            daemonStartsBeforeEachListing = true
            daemons = (1L..3L).map { gradleDaemon(it, idle, memoryBytes = 1_000L) } + kotlinDaemon(9, memoryBytes = 1_000L)
        }
        val machine = machine(
            useCases(calls),
            listOf(machineProject("api", listOf(apiWorktree), MachineResource.GRADLE_DAEMONS)),
        )

        machine.refreshSummaries()
        machine.show(MachineResource.GRADLE_DAEMONS)
        runCurrent()

        val strings = EnglishStrings
        val item = machineStatusItems(machine.visibleResources, machine.summaries, machine.summaryFailures, strings).single()
        val listed = assertIs<MachineLoad.Loaded<List<MachineDaemon>>>(machine.gradleDaemons).value
        assertEquals(5, listed.count { it.process.kind == DaemonKind.GRADLE })
        assertEquals(5, machine.poolOf(MachineResource.GRADLE_DAEMONS)?.inUse)
        assertEquals("5 of 3", item.figure)
        assertTrue(item.tooltip.startsWith("${strings.gradleDaemonsTitle}: 5 of 3 · "), item.tooltip)
    }

    @Test
    fun `a Gradle pool over its limit with no idle daemon is held by builds`() = machineTest {
        val busy = usage(ResourceKind.GRADLE_DAEMON, 2, 1, 2, 3).let { pool ->
            pool.copy(holders = pool.holders.map { it.copy(state = PoolHolderState.BUSY) })
        }
        val machine = machine(
            useCases(Calls(), pools = mapOf(ResourceKind.GRADLE_DAEMON to busy)),
            listOf(machineProject("api", listOf(apiWorktree), MachineResource.GRADLE_DAEMONS)),
        )

        machine.refreshSummaries()

        assertTrue(machine.summaries.getValue(MachineResource.GRADLE_DAEMONS).poolHeldByBuilds)
    }

    @Test
    fun `starting an emulator that released another one leaves a notice until dismissed`() = machineTest {
        val release = PoolRelease(ResourceKind.EMULATOR, "/home/dev/projects/api-TASK-1", PoolReleaseReason.MAKE_ROOM, "api · TASK-1")
        val machine = machine(
            useCases(
                Calls(),
                startEmulator = { _, _ ->
                    Result.success(
                        DeviceStart(WorktreeEmulator("emulator-5554", EmulatorState.RUNNING), ResourceAcquisition(true, listOf(release))),
                    )
                },
            ),
            listOf(machineProject("api", listOf(apiWorktree), MachineResource.ANDROID_EMULATORS)),
        )

        machine.startEmulator("api", apiWorktree.path)
        runCurrent()

        val notice = machine.noticesOf(MachineResource.ANDROID_EMULATORS).single()
        assertEquals(PoolNoticeContent.Released(ResourceKind.EMULATOR, "api · TASK-1", "api-TASK-9"), notice.content)

        machine.dismissNotices(MachineResource.ANDROID_EMULATORS)
        assertTrue(machine.noticesOf(MachineResource.ANDROID_EMULATORS).isEmpty())
    }

    @Test
    fun `the summary refresh trims Gradle daemons and reports what it stopped`() = machineTest {
        val calls = Calls()
        val trim = GradleTrim(limit = 2, stopped = listOf(gradleDaemon(7, idle)), remaining = 2)
        val machine = machine(
            useCases(calls, trim = trim),
            listOf(machineProject("api", listOf(apiWorktree), MachineResource.GRADLE_DAEMONS)),
        )

        machine.refreshSummaries()

        assertEquals(1, calls.trims)
        assertEquals(PoolNoticeContent.Trimmed(1, 2, 1, now), machine.noticesOf(MachineResource.GRADLE_DAEMONS).single().content)
    }

    @Test
    fun `repeated trims accumulate into one report until it is dismissed`() = machineTest {
        val calls = Calls()
        var stopped = listOf(gradleDaemon(7, idle))
        var time = now
        val machine = machine(
            useCases(calls, nextTrim = { GradleTrim(limit = 3, stopped = stopped) }),
            listOf(machineProject("api", listOf(apiWorktree), MachineResource.GRADLE_DAEMONS)),
            clock = { time },
        )

        machine.refreshSummaries()
        val first = machine.noticesOf(MachineResource.GRADLE_DAEMONS).single()
        stopped = listOf(gradleDaemon(8, idle), gradleDaemon(9, idle))
        time = now.plusSeconds(30)
        machine.refreshSummaries()
        stopped = emptyList()
        machine.refreshSummaries()
        stopped = listOf(gradleDaemon(10, idle))
        time = now.plusSeconds(90)
        machine.refreshSummaries()

        val accumulated = machine.noticesOf(MachineResource.GRADLE_DAEMONS).single()
        assertEquals(first.id, accumulated.id)
        assertEquals(PoolNoticeContent.Trimmed(4, 3, 3, now.plusSeconds(90)), accumulated.content)

        machine.dismissNotices(MachineResource.GRADLE_DAEMONS)
        machine.refreshSummaries()

        assertEquals(PoolNoticeContent.Trimmed(1, 3, 1, now.plusSeconds(90)), machine.noticesOf(MachineResource.GRADLE_DAEMONS).single().content)
    }

    @Test
    fun `the summary refresh and a shown view read every resource on the IO dispatcher`() = machineTest {
        val calls = Calls()
        val io = StandardTestDispatcher(dispatcher.scheduler, name = "io")
        val machine = machine(
            useCases(calls),
            listOf(
                machineProject(
                    "api",
                    listOf(apiWorktree),
                    MachineResource.GRADLE_DAEMONS,
                    MachineResource.ANDROID_EMULATORS,
                    MachineResource.IOS_SIMULATORS,
                ),
            ),
            ioDispatcher = io,
        )

        machine.refreshSummaries()
        machine.show(MachineResource.ANDROID_EMULATORS)
        runCurrent()

        assertEquals(1, calls.trims)
        assertEquals(1, calls.daemonListings)
        assertEquals(1, calls.emulatorReads)
        assertTrue(calls.dispatchers.isNotEmpty())
        assertTrue(calls.dispatchers.all { it === io }, "Called outside the IO dispatcher: ${calls.dispatchers}")
    }

    @Test
    fun `the summary refresh does not trim when no project declares Gradle`() = machineTest {
        val calls = Calls()
        val machine = machine(
            useCases(calls),
            listOf(machineProject("api", listOf(apiWorktree), MachineResource.ANDROID_EMULATORS)),
        )

        machine.refreshSummaries()

        assertEquals(0, calls.trims)
    }

    private val derivedFolders = listOf(
        AttributedDerivedData(DerivedDataFolder("/dd/A", "/home/dev/projects/main-repo/A.xcodeproj", null, null), DerivedDataOwner.OtherProject),
        AttributedDerivedData(DerivedDataFolder("/dd/B", "/gone/B.xcodeproj", null, null), DerivedDataOwner.Orphaned),
    )

    private fun derivedDataMachine(calls: Calls, compilationCache: Boolean = false) = machine(
        useCases(calls, derivedData = DerivedDataListing(derivedFolders, null), sizes = mapOf("/dd/A" to 300L, "/dd/B" to 200L)),
        listOf(machineProject("main-repo", listOf(mainWorktree), MachineResource.XCODE_DERIVED_DATA, compilationCache = compilationCache)),
    )

    private fun MachineResources.loadedFolders() = (derivedData as MachineLoad.Loaded).value

    @Test
    fun `derived data folders are listed before their sizes are known`() = machineTest {
        val calls = Calls().apply { measureGate = CompletableDeferred() }
        val machine = derivedDataMachine(calls)

        machine.show(MachineResource.XCODE_DERIVED_DATA)
        runCurrent()

        assertEquals(listOf("/dd/A", "/dd/B"), machine.loadedFolders().map { it.folder.path })
        assertTrue(machine.loadedFolders().all { it.folder.sizeBytes == null })
        assertEquals(listOf(null), machine.derivedDataOrphans.map { it.sizeBytes }.distinct())
    }

    @Test
    fun `derived data sizes arrive as they are measured`() = machineTest {
        val calls = Calls()
        val gates = listOf(CompletableDeferred<Unit>(), CompletableDeferred())
        var next = 0
        val machine = machine(
            useCases(calls, derivedData = DerivedDataListing(derivedFolders, null), sizes = mapOf("/dd/A" to 300L, "/dd/B" to 200L))
                .let { base ->
                    MachineUseCases(
                        listDaemons = base.listDaemons, stopDaemons = base.stopDaemons, listDerivedData = base.listDerivedData,
                        measureDerivedData = { folders, _ ->
                            flow { folders.forEach { gates[next++].await(); emit(MeasuredSize(it.path, if (it.path == "/dd/A") 300L else 200L)) } }
                        },
                        measureCompilationCache = base.measureCompilationCache,
                        deleteOrphanedDerivedData = base.deleteOrphanedDerivedData, readEmulatorStates = base.readEmulatorStates,
                        readRunningEmulators = base.readRunningEmulators, startEmulator = base.startEmulator,
                        stopEmulator = base.stopEmulator, stopEmulatorSerials = base.stopEmulatorSerials,
                        readSimulatorStates = base.readSimulatorStates, startSimulator = base.startSimulator,
                        stopSimulator = base.stopSimulator, readSimulatorOrphans = base.readSimulatorOrphans,
                        deleteSimulators = base.deleteSimulators,
                        readPool = base.readPool, trimGradleDaemons = base.trimGradleDaemons, resourceSettings = base.resourceSettings,
                    )
                },
            listOf(machineProject("main-repo", listOf(mainWorktree), MachineResource.XCODE_DERIVED_DATA)),
        )

        machine.show(MachineResource.XCODE_DERIVED_DATA)
        runCurrent()
        gates[0].complete(Unit)
        runCurrent()
        assertEquals(listOf(300L, null), machine.loadedFolders().map { it.folder.sizeBytes })
        gates[1].complete(Unit)
        runCurrent()

        assertEquals(listOf(300L, 200L), machine.loadedFolders().map { it.folder.sizeBytes })
        assertEquals(listOf(200L), machine.derivedDataOrphans.map { it.sizeBytes })
    }

    @Test
    fun `remembered sizes are shown as soon as the folders are listed`() = machineTest {
        val calls = Calls().apply { measureGate = CompletableDeferred() }
        val machine = machine(
            useCases(calls, derivedData = DerivedDataListing(derivedFolders, null), sizes = mapOf("/dd/B" to 200L), remembered = mapOf("/dd/A" to 300L)),
            listOf(machineProject("main-repo", listOf(mainWorktree), MachineResource.XCODE_DERIVED_DATA)),
        )

        machine.show(MachineResource.XCODE_DERIVED_DATA)
        runCurrent()

        assertEquals(listOf(300L, null), machine.loadedFolders().map { it.folder.sizeBytes })
        assertEquals(listOf("/dd/B"), calls.measured)
        assertEquals(listOf(false), calls.remeasures)
    }

    @Test
    fun `reloading measures every folder again and keeps the remembered sizes meanwhile`() = machineTest {
        val calls = Calls()
        val machine = machine(
            useCases(calls, derivedData = DerivedDataListing(derivedFolders, null), sizes = mapOf("/dd/A" to 310L, "/dd/B" to 200L), remembered = mapOf("/dd/A" to 300L)),
            listOf(machineProject("main-repo", listOf(mainWorktree), MachineResource.XCODE_DERIVED_DATA)),
        )
        machine.show(MachineResource.XCODE_DERIVED_DATA)
        runCurrent()
        calls.measureGate = CompletableDeferred()

        machine.reload()
        runCurrent()

        assertEquals(listOf(false, true), calls.remeasures)
        assertEquals(listOf(300L, null), machine.loadedFolders().map { it.folder.sizeBytes })
        calls.measureGate!!.complete(Unit)
        runCurrent()
        assertEquals(listOf(310L, 200L), machine.loadedFolders().map { it.folder.sizeBytes })
    }

    @Test
    fun `reloading derived data restarts the calculation`() = machineTest {
        val calls = Calls().apply { measureGate = CompletableDeferred() }
        val machine = derivedDataMachine(calls)
        machine.show(MachineResource.XCODE_DERIVED_DATA)
        runCurrent()
        assertEquals(listOf("/dd/A"), calls.measured)

        machine.reload()
        runCurrent()

        assertEquals(2, calls.derivedDataListings)
        assertEquals(listOf("/dd/A", "/dd/A"), calls.measured)
    }

    @Test
    fun `derived data folders get their packages and the shared package caches are measured`() = machineTest {
        val caches = PackageCacheSizes(swiftPmBytes = 2_000L, cocoaPodsBytes = 3_000L)
        val machine = machine(
            useCases(
                Calls(),
                derivedData = DerivedDataListing(derivedFolders, null),
                sizes = mapOf("/dd/A" to 300L, "/dd/B" to 200L),
                packages = mapOf("/dd/A" to 120L),
                packageCaches = caches,
            ),
            listOf(machineProject("main-repo", listOf(mainWorktree), MachineResource.XCODE_DERIVED_DATA)),
        )

        machine.show(MachineResource.XCODE_DERIVED_DATA)
        runCurrent()

        assertEquals(mapOf("/dd/A" to 120L, "/dd/B" to null), machine.loadedFolders().associate { it.folder.path to it.folder.packagesBytes })
        assertEquals(caches, machine.packageCacheSizes)
        assertEquals(500L, machine.derivedDataUsageBytes)
    }

    @Test
    fun `leaving the derived data view keeps the calculation going`() = machineTest {
        val calls = Calls().apply { measureGate = CompletableDeferred() }
        val machine = derivedDataMachine(calls, compilationCache = true)
        machine.show(MachineResource.XCODE_DERIVED_DATA)
        runCurrent()

        machine.show(MachineResource.GRADLE_DAEMONS)
        calls.measureGate!!.complete(Unit)
        runCurrent()

        assertEquals(listOf("/dd/A", "/dd/B"), calls.measured)
        assertEquals(1, calls.cacheMeasurements)
        assertEquals(7_000L, machine.compilationCacheBytes)
    }

    @Test
    fun `the sidebar summary calculates until every size is known`() = machineTest {
        val calls = Calls().apply { measureGate = CompletableDeferred() }
        val machine = derivedDataMachine(calls)

        machine.refreshSummaries()
        runCurrent()
        machine.refreshSummaries()
        assertEquals(MachineSummary(2, null, calculating = true), machine.summaries.getValue(MachineResource.XCODE_DERIVED_DATA))

        calls.measureGate!!.complete(Unit)
        runCurrent()
        machine.refreshSummaries()

        assertEquals(MachineSummary(2, 500L), machine.summaries.getValue(MachineResource.XCODE_DERIVED_DATA))
    }

    @Test
    fun `removing the xcode provider stops the calculation`() = machineTest {
        val calls = Calls().apply { measureGate = CompletableDeferred() }
        var declared = true
        val machine = MachineResources(
            scope = scope,
            useCases = useCases(calls, derivedData = DerivedDataListing(derivedFolders, null)),
            projects = {
                listOf(machineProject("main-repo", listOf(mainWorktree), *(if (declared) arrayOf(MachineResource.XCODE_DERIVED_DATA) else emptyArray())))
            },
            pathExists = { false },
            ioDispatcher = dispatcher,
        )
        machine.refreshSummaries()
        runCurrent()

        declared = false
        machine.refreshSummaries()
        calls.measureGate!!.complete(Unit)
        runCurrent()

        assertEquals(listOf("/dd/A"), calls.measured)
        assertTrue(machine.derivedData is MachineLoad.Loading)
    }

    private val gigabyte = 1_073_741_824L

    private fun quotaMachine(
        calls: Calls,
        quotaGigabytes: Int?,
        mode: DerivedDataQuotaMode? = null,
        resolveQuota: suspend (ResourceSettings, Long) -> DerivedDataQuota = ::fixedQuotaOnly,
        trim: (Long) -> DerivedDataTrim = { DerivedDataTrim(it, 0L, 0L) },
    ) = machine(
        useCases(
            calls,
            derivedData = DerivedDataListing(derivedFolders, null),
            sizes = mapOf("/dd/A" to 30 * gigabyte, "/dd/B" to 30 * gigabyte),
            settings = ResourceSettings(derivedDataQuotaGigabytes = quotaGigabytes, derivedDataQuotaMode = mode),
            derivedDataTrim = trim,
            resolveQuota = resolveQuota,
        ),
        listOf(machineProject("main-repo", listOf(mainWorktree), MachineResource.XCODE_DERIVED_DATA)),
    )

    @Test
    fun `the quota is applied once per measurement`() = machineTest {
        val calls = Calls()
        val machine = quotaMachine(calls, 50)

        machine.show(MachineResource.XCODE_DERIVED_DATA)
        runCurrent()
        assertEquals(listOf(50 * gigabyte), calls.derivedDataTrims)

        machine.reload()
        runCurrent()
        assertEquals(listOf(50 * gigabyte, 50 * gigabyte), calls.derivedDataTrims)
    }

    @Test
    fun `the quota is not applied while sizes are being calculated`() = machineTest {
        val calls = Calls().apply { measureGate = CompletableDeferred() }
        val machine = quotaMachine(calls, 50)

        machine.show(MachineResource.XCODE_DERIVED_DATA)
        runCurrent()

        assertTrue(calls.derivedDataTrims.isEmpty())
        assertNull(machine.derivedDataUsageBytes)
    }

    @Test
    fun `the automatic quota is resolved from the measured usage and applied`() = machineTest {
        val calls = Calls()
        val usages = mutableListOf<Long>()
        val machine = quotaMachine(calls, null, resolveQuota = { settings, usage ->
            usages += usage
            DerivedDataQuota(settings.quotaMode, 40 * gigabyte)
        })

        machine.show(MachineResource.XCODE_DERIVED_DATA)
        runCurrent()
        machine.refreshSummaries()

        assertEquals(listOf(60 * gigabyte), usages)
        assertEquals(listOf(40 * gigabyte), calls.derivedDataTrims)
        assertEquals(40 * gigabyte, machine.derivedDataQuotaBytes)
        assertTrue(machine.isDerivedDataQuotaAutomatic)
        assertEquals(
            MachineSummary(2, 60 * gigabyte, quotaBytes = 40 * gigabyte, quotaAutomatic = true),
            machine.summaries.getValue(MachineResource.XCODE_DERIVED_DATA),
        )
    }

    @Test
    fun `an automatic quota that can't be computed releases nothing and is shown as unknown`() = machineTest {
        val calls = Calls()
        val machine = quotaMachine(calls, null, resolveQuota = { settings, _ -> DerivedDataQuota(settings.quotaMode, null) })

        machine.show(MachineResource.XCODE_DERIVED_DATA)
        runCurrent()

        assertTrue(calls.derivedDataTrims.isEmpty())
        assertNull(machine.derivedDataQuotaBytes)
        assertTrue(machine.isDerivedDataQuotaUnknown)
    }

    @Test
    fun `nothing is applied without a quota`() = machineTest {
        val calls = Calls()
        val machine = quotaMachine(calls, 50, mode = DerivedDataQuotaMode.NONE)

        machine.show(MachineResource.XCODE_DERIVED_DATA)
        runCurrent()

        assertTrue(calls.derivedDataTrims.isEmpty())
        assertNull(machine.derivedDataQuotaBytes)
    }

    @Test
    fun `released folders leave the list and are reported until dismissed`() = machineTest {
        val calls = Calls()
        val machine = quotaMachine(calls, 40) { quota ->
            DerivedDataTrim(quota, 60 * gigabyte, 30 * gigabyte, released = listOf(derivedFolders[1].let { it.copy(folder = it.folder.copy(sizeBytes = 30 * gigabyte)) }))
        }

        machine.show(MachineResource.XCODE_DERIVED_DATA)
        runCurrent()

        assertEquals(listOf("/dd/A"), machine.loadedFolders().map { it.folder.path })
        assertEquals(30 * gigabyte, machine.derivedDataUsageBytes)
        assertEquals(1, machine.derivedDataTrimReport?.released?.size)
        machine.dismissDerivedDataTrimReport()
        assertNull(machine.derivedDataTrimReport)
    }

    @Test
    fun `an exceeded quota names the worktrees in use`() = machineTest {
        val calls = Calls()
        val inUse = AttributedDerivedData(derivedFolders[0].folder, DerivedDataOwner.Worktree("/home/dev/projects/TASK-1"))
        val machine = quotaMachine(calls, 10) { quota -> DerivedDataTrim(quota, 60 * gigabyte, 60 * gigabyte, inUse = listOf(inUse)) }

        machine.show(MachineResource.XCODE_DERIVED_DATA)
        runCurrent()

        assertTrue(machine.derivedDataQuotaExceeded)
        assertEquals(listOf("TASK-1"), machine.derivedDataInUse)
        assertNull(machine.derivedDataTrimReport)
    }

    private val apiAdmin = "/home/dev/projects/api/.git/worktrees/api-TASK-9"

    private fun heldByApi(waiting: Int = 0) = DesktopTurnState(
        DesktopTurn(apiAdmin, apiWorktree.path, 0, Long.MAX_VALUE),
        (1..waiting).map { WaitTicket("t$it", "/home/dev/projects/main-repo/.git/worktrees/TASK-$it", "/home/dev/projects/TASK-$it", ProcessIdentity(it.toLong(), 1), 0) },
    )

    private fun watchOf(state: () -> DesktopTurnState) = DesktopTurnWatch(
        read = { Result.success(state()) },
        forceRelease = { Result.success(DesktopTurnRelease.NobodyHolds) },
    )

    @Test
    fun `the desktop entry shows only while someone holds or waits for the turn`() = machineTest {
        var turn = DesktopTurnState(null, emptyList())
        val machine = machine(useCases(Calls()), listOf(machineProject("api", listOf(apiWorktree), MachineResource.GRADLE_DAEMONS)), desktopTurn = watchOf { turn })

        machine.refreshSummaries()
        assertFalse(MachineResource.DESKTOP_TURN in machine.visibleResources)

        turn = heldByApi(waiting = 2)
        machine.refreshSummaries()

        assertTrue(MachineResource.DESKTOP_TURN in machine.visibleResources)
        assertEquals("api · api-TASK-9 · 2 waiting", machine.summaries[MachineResource.DESKTOP_TURN]?.text(MachineResource.DESKTOP_TURN, EnglishStrings))

        turn = DesktopTurnState(null, heldByApi(waiting = 1).waiting)
        machine.desktopTurn.refresh()

        assertEquals("1 waiting", machine.summaries[MachineResource.DESKTOP_TURN]?.text(MachineResource.DESKTOP_TURN, EnglishStrings))
    }

    @Test
    fun `the desktop entry shows alone when no project declares a provider`() = machineTest {
        val machine = machine(useCases(Calls()), listOf(machineProject("api", listOf(apiWorktree))), desktopTurn = watchOf { heldByApi() })

        machine.refreshSummaries()

        assertEquals(listOf(MachineResource.DESKTOP_TURN), machine.visibleResources)
    }

    @Test
    fun `the desktop holder is attributed to its project or to a folder outside them`() = machineTest {
        val machine = machine(useCases(Calls()), listOf(machineProject("api", listOf(apiWorktree))), existing = setOf(apiWorktree.path, "/home/dev/elsewhere/TASK-7"))

        assertEquals(ProjectOwner.Owned("api", apiWorktree.path), machine.ownerOfPath(apiWorktree.path))
        assertEquals(ProjectOwner.Outside, machine.ownerOfPath("/home/dev/elsewhere/TASK-7"))
    }
}
