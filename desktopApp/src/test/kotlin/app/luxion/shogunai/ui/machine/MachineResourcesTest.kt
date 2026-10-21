package app.luxion.shogunai.ui.machine

import app.luxion.shogunai.domain.model.CapacityProfile
import app.luxion.shogunai.domain.model.PoolHolder
import app.luxion.shogunai.domain.model.PoolHolderState
import app.luxion.shogunai.domain.model.PoolRelease
import app.luxion.shogunai.domain.model.PoolReleaseReason
import app.luxion.shogunai.domain.model.PoolUsage
import app.luxion.shogunai.domain.model.ResourceKind
import app.luxion.shogunai.domain.model.ResourceSettings
import app.luxion.shogunai.domain.usecase.DeviceStart
import app.luxion.shogunai.domain.usecase.GradleTrim
import app.luxion.shogunai.domain.usecase.ResourceAcquisition
import app.luxion.shogunai.MachineUseCases
import app.luxion.shogunai.domain.FakeGradleDaemonSource.Companion.gradleDaemon
import app.luxion.shogunai.domain.model.DaemonKeptReason
import app.luxion.shogunai.domain.model.DaemonProcess
import app.luxion.shogunai.domain.model.DaemonState
import app.luxion.shogunai.domain.model.DaemonStopReport
import app.luxion.shogunai.domain.model.AttributedDerivedData
import app.luxion.shogunai.domain.model.DerivedDataDeletion
import app.luxion.shogunai.domain.model.DerivedDataFolder
import app.luxion.shogunai.domain.model.DerivedDataOwner
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
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
        var daemonListings = 0
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
        var trims = 0
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
        startEmulator: suspend (ProjectConfig, String) -> Result<DeviceStart<WorktreeEmulator>> = { _, path ->
            calls.startedEmulators += path
            Result.success(DeviceStart(WorktreeEmulator("emulator-5554", EmulatorState.RUNNING), ResourceAcquisition(true, emptyList())))
        },
        pools: Map<ResourceKind, PoolUsage> = emptyMap(),
        trim: GradleTrim = GradleTrim(limit = null),
        settings: ResourceSettings = ResourceSettings(),
        stopDaemons: suspend (List<Long>?) -> Result<DaemonStopReport> = { pids ->
            calls.stoppedDaemons += pids
            Result.success(stopReport)
        },
    ) = MachineUseCases(
        listDaemons = { calls.daemonListings++; Result.success(calls.daemons) },
        stopDaemons = stopDaemons,
        listDerivedData = {
            calls.derivedDataListings++
            Result.success(derivedData)
        },
        measureDerivedData = { folder ->
            calls.measured += folder.path
            calls.measureGate?.await()
            Result.success(sizes[folder.path] ?: 1_000L)
        },
        measureCompilationCache = { calls.cacheMeasurements++; Result.success(7_000L) },
        deleteOrphanedDerivedData = { calls.derivedDataDeletions++; Result.success(DerivedDataDeletion()) },
        readEmulatorStates = { _, _ -> calls.emulatorReads++; Result.success(emulators) },
        readRunningEmulators = { Result.success(running) },
        startEmulator = startEmulator,
        readPool = { kind, _, _ -> pools[kind]?.let { Result.success(it) } ?: Result.failure(IllegalStateException()) },
        trimGradleDaemons = { calls.trims++; Result.success(trim) },
        resourceSettings = { settings },
        stopEmulator = { _, _ -> Result.success(null) },
        stopEmulatorSerials = { _, serials -> calls.stoppedSerials += serials; Result.success(emptyList()) },
        readSimulatorStates = { _, _ -> calls.simulatorReads++; Result.success(simulators) },
        startSimulator = { _, _ -> Result.failure(IllegalStateException()) },
        stopSimulator = { _, _ -> Result.success(null) },
        readSimulatorOrphans = { Result.success(orphans) },
        deleteSimulators = { udids -> calls.deletedSimulators += udids; Result.success(udids) },
    )

    private fun machine(
        useCases: MachineUseCases,
        projects: List<MachineProject>,
        existing: Set<String> = emptySet(),
        onDevicesChanged: suspend (String) -> Unit = {},
    ) = MachineResources(
        scope = scope,
        useCases = useCases,
        projects = { projects },
        pathExists = { it in existing },
        onDevicesChanged = onDevicesChanged,
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

    private fun usage(kind: ResourceKind, limit: Int?, vararg holders: Int) = PoolUsage(
        kind,
        limit,
        limit,
        holders.map { PoolHolder(kind, "/home/dev/projects/api-TASK-$it", null, PoolHolderState.RUNNING, false, null) },
    )

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

        machine.dismissNotice(notice.id)
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
        assertEquals(PoolNoticeContent.Trimmed(1, 2), machine.noticesOf(MachineResource.GRADLE_DAEMONS).single().content)
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
    fun `derived data sizes arrive one by one`() = machineTest {
        val calls = Calls()
        val gates = listOf(CompletableDeferred<Unit>(), CompletableDeferred())
        var next = 0
        val machine = machine(
            useCases(calls, derivedData = DerivedDataListing(derivedFolders, null), sizes = mapOf("/dd/A" to 300L, "/dd/B" to 200L))
                .let { base ->
                    MachineUseCases(
                        listDaemons = base.listDaemons, stopDaemons = base.stopDaemons, listDerivedData = base.listDerivedData,
                        measureDerivedData = { folder -> gates[next++].await(); base.measureDerivedData(folder) },
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
}
