package app.luxion.shogunai.ui.machine

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import app.luxion.shogunai.MachineUseCases
import app.luxion.shogunai.domain.model.DerivedDataDeletion
import app.luxion.shogunai.domain.model.DerivedDataListing
import app.luxion.shogunai.domain.model.DerivedDataTrim
import app.luxion.shogunai.domain.model.DyldCacheFolder
import app.luxion.shogunai.domain.model.DyldCaches
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.ResourceSettings
import app.luxion.shogunai.domain.model.RuntimeDeletion
import app.luxion.shogunai.domain.model.RuntimeRefusal
import app.luxion.shogunai.domain.model.RuntimeUser
import app.luxion.shogunai.domain.model.SimulatorDevice
import app.luxion.shogunai.domain.model.SimulatorRuntimeImage
import app.luxion.shogunai.domain.model.SimulatorState
import app.luxion.shogunai.domain.model.SimulatorStorage
import app.luxion.shogunai.domain.model.StoredRuntime
import app.luxion.shogunai.domain.model.UnavailableDeletion
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeSimulator
import app.luxion.shogunai.domain.usecase.GradleTrim
import app.luxion.shogunai.ui.i18n.EnglishStrings
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.i18n.SpanishStrings
import app.luxion.shogunai.ui.i18n.Strings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class SimulatorStorageViewUiTest {

    private val gigabyte = 1_073_741_824L
    private val xcodeOnly = MachineProject(
        Project("x", "App", ProjectConfig("/home/dev/projects/app", "/home/dev/projects/worktrees", emptyList())),
        emptyList(),
        setOf(MachineResource.XCODE_DERIVED_DATA),
    )
    private val simulatorProject = xcodeOnly.copy(declares = setOf(MachineResource.IOS_SIMULATORS))

    private fun image(version: String, sizeBytes: Long? = 8 * gigabyte, lastUsed: Instant? = Instant.now()) = SimulatorRuntimeImage(
        "IMAGE-$version",
        "com.apple.CoreSimulator.SimRuntime.iOS-${version.replace('.', '-')}",
        version,
        "B$version",
        sizeBytes,
        lastUsed,
        deletable = true,
    )

    private val unused = StoredRuntime(image("25.0", lastUsed = null))
    private val inUse = StoredRuntime(
        image("27.0"),
        listOf(RuntimeUser.Worktree("ShogunAi · app · TASK-1"), RuntimeUser.Default),
        RuntimeRefusal.IN_USE,
    )
    private val unavailable = SimulatorDevice("UDID-A", "iPhone 15", SimulatorState.SHUTDOWN, isAvailable = false, dataBytes = gigabyte)
    private val stale = DyldCacheFolder("26A434", "/caches/26A434", 2 * gigabyte)
    private val current = DyldCacheFolder("26B100", "/caches/26B100", null)
    private val storage = SimulatorStorage(
        runtimes = listOf(inUse, unused),
        unavailableDevices = listOf(unavailable),
        dyldCaches = DyldCaches("26B100", listOf(stale, current)),
    )

    private class Calls {
        var lists = 0
        val deletedRuntimes = mutableListOf<String>()
        var unavailableDeletions = 0
    }

    private fun actions(
        calls: Calls,
        listed: () -> Result<SimulatorStorage> = { Result.success(storage) },
        deleteRuntime: suspend (String) -> Result<RuntimeDeletion> = { Result.success(RuntimeDeletion(unused, listOf(unavailable))) },
    ) = SimulatorStorageActions(
        supported = true,
        list = { calls.lists++; listed() },
        deleteRuntime = { identifier -> calls.deletedRuntimes += identifier; deleteRuntime(identifier) },
        deleteUnavailable = { calls.unavailableDeletions++; Result.success(UnavailableDeletion(listOf(unavailable))) },
    )

    private fun machineUseCases(
        states: Map<String, WorktreeSimulator> = emptyMap(),
        orphans: List<SimulatorDevice> = emptyList(),
        deleted: MutableList<List<String>> = mutableListOf(),
        started: MutableList<String> = mutableListOf(),
    ) = MachineUseCases(
        listDaemons = { Result.success(emptyList()) },
        stopDaemons = { Result.failure(IllegalStateException()) },
        listDerivedData = { Result.success(DerivedDataListing(emptyList(), null)) },
        measureDerivedData = { _, _ -> emptyFlow() },
        measureCompilationCache = { Result.success(null) },
        deleteOrphanedDerivedData = { Result.success(DerivedDataDeletion()) },
        readEmulatorStates = { _, _ -> Result.success(emptyMap()) },
        readRunningEmulators = { Result.success(emptyMap()) },
        startEmulator = { _, _ -> Result.failure(IllegalStateException()) },
        stopEmulator = { _, _ -> Result.success(null) },
        stopEmulatorSerials = { _, _ -> Result.success(emptyList()) },
        readSimulatorStates = { _, _ -> Result.success(states) },
        startSimulator = { _, path -> started += path; Result.failure(IllegalStateException()) },
        stopSimulator = { _, path -> started += "stop $path"; Result.success(null) },
        readSimulatorOrphans = { Result.success(orphans) },
        deleteSimulators = { deleted += it; Result.success(it) },
        readPool = { _, _, _, _ -> Result.failure(IllegalStateException()) },
        trimGradleDaemons = { Result.success(GradleTrim(limit = null)) },
        resourceSettings = { ResourceSettings() },
        deleteUnusedSwiftBuilds = { Result.failure(IllegalStateException()) },
        trimDerivedData = { quota, _, _ -> Result.success(DerivedDataTrim(quota, 0L, 0L)) },
    )

    private fun machine(
        job: Job,
        storage: MachineSimulatorStorage?,
        vararg projects: MachineProject,
        useCases: MachineUseCases = machineUseCases(),
    ): MachineResources {
        val scope = CoroutineScope(Dispatchers.Unconfined + job)
        return MachineResources(
            scope = scope,
            useCases = useCases,
            projects = { projects.toList() },
            pathExists = { true },
            ioDispatcher = Dispatchers.Unconfined,
            simulatorStorage = storage ?: MachineSimulatorStorage(scope, SimulatorStorageActions.NONE),
        )
    }

    private fun storageOf(job: Job, actions: SimulatorStorageActions) = MachineSimulatorStorage(CoroutineScope(Dispatchers.Unconfined + job), actions)

    @Test
    fun `projects declaring Xcode get the iOS simulators view for the simulator storage`() {
        val job = Job()
        val machine = machine(job, storageOf(job, actions(Calls())), xcodeOnly)

        assertTrue(MachineResource.IOS_SIMULATORS in machine.visibleResources)
        assertFalse(machine.declaresSimulators)
        job.cancel()
    }

    @Test
    fun `without simulator storage support Xcode projects don't get the view`() {
        val job = Job()

        assertFalse(MachineResource.IOS_SIMULATORS in machine(job, null, xcodeOnly).visibleResources)
        assertTrue(machine(job, null, simulatorProject).declaresSimulators)
        job.cancel()
    }

    @Test
    fun `loads, deletes a runtime and reads the storage again`() = runBlocking {
        val job = Job()
        val calls = Calls()
        val holder = storageOf(job, actions(calls))

        holder.load()
        assertEquals(MachineLoad.Loaded(storage), holder.storage)
        holder.deleteRuntime(unused)

        assertEquals(listOf("IMAGE-25.0"), calls.deletedRuntimes)
        assertIs<SimulatorStorageReport.Runtime>(holder.report)
        assertEquals(2, calls.lists)
        assertFalse(holder.isDeleting)
        holder.dismissReport()
        assertNull(holder.report)
        job.cancel()
    }

    @Test
    fun `a failed deletion is shown as an error and a failed read as unavailable`() = runBlocking {
        val job = Job()
        val holder = storageOf(
            job,
            actions(Calls(), listed = { Result.failure(IllegalStateException("simctl")) }, deleteRuntime = { Result.failure(IllegalStateException("denied")) }),
        )

        holder.deleteRuntime(unused)

        assertEquals("denied", holder.error?.message)
        assertEquals(MachineLoad.Unavailable, holder.storage)
        holder.dismissError()
        assertNull(holder.error)
        job.cancel()
    }

    @Test
    fun `a second deletion while one runs is ignored`() = runBlocking {
        val job = Job()
        val calls = Calls()
        val gate = CompletableDeferred<Unit>()
        val holder = storageOf(job, actions(calls, deleteRuntime = { gate.await(); Result.success(RuntimeDeletion(unused, emptyList())) }))

        holder.deleteRuntime(unused)
        assertTrue(holder.isDeleting)
        holder.deleteUnavailable()
        gate.complete(Unit)

        assertEquals(0, calls.unavailableDeletions)
        assertFalse(holder.isDeleting)
        job.cancel()
    }

    @Test
    fun `the none actions support nothing`() = runBlocking {
        val none = SimulatorStorageActions.NONE

        assertFalse(none.supported)
        assertFalse(none.list().getOrThrow().supported)
        assertTrue(none.deleteRuntime("ID").isFailure)
        assertTrue(none.deleteUnavailable().isFailure)
    }

    @Test
    fun `the view lists runtimes, unavailable devices and dyld caches for an Xcode project`() = runComposeUiTest {
        val job = Job()
        val machine = machine(job, storageOf(job, actions(Calls())), xcodeOnly)

        setContent {
            CompositionLocalProvider(LocalStrings provides EnglishStrings) {
                MaterialTheme { IosSimulatorsView(machine) { _, _ -> } }
            }
        }

        onNodeWithText("Simulator storage").assertIsDisplayed()
        onNodeWithText("2 runtimes · 16.0 GB").assertIsDisplayed()
        onNodeWithText("iOS 27.0 (B27.0)").assertIsDisplayed()
        onNodeWithText("Used by app · TASK-1, default for new simulators").assertIsDisplayed()
        onNodeWithText("Not used").assertIsDisplayed()
        onNodeWithText("Never used").assertIsDisplayed()
        onNodeWithTag("delete-runtime-IMAGE-27.0").assertIsNotEnabled()
        onNodeWithTag("delete-runtime-IMAGE-25.0").assertIsEnabled()
        onNodeWithText("1 unavailable simulator device · 1.0 GB").assertIsDisplayed()
        onNodeWithText("dyld caches · 2.0 GB").assertIsDisplayed()
        onNodeWithText("From another macOS build: remove it yourself as administrator").assertIsDisplayed()
        onNodeWithText("Current macOS build").assertIsDisplayed()
        onNodeWithText("Unknown size").assertIsDisplayed()
        onNodeWithTag("copy-dyld-26A434").assertIsDisplayed()
        assertEquals(0, onAllNodesWithText("No ShogunAi simulators yet").fetchSemanticsNodes().size)
        job.cancel()
    }

    @Test
    fun `deleting a runtime asks first and reports the space freed`() = runComposeUiTest {
        val job = Job()
        val calls = Calls()
        val machine = machine(job, storageOf(job, actions(calls)), xcodeOnly)
        setContent {
            CompositionLocalProvider(LocalStrings provides EnglishStrings) {
                MaterialTheme { IosSimulatorsView(machine) { _, _ -> } }
            }
        }

        onNodeWithTag("delete-runtime-IMAGE-25.0").performClick()
        onNodeWithText("iOS 25.0 (8.0 GB) will be deleted from this Mac. Xcode can download it again from its Components settings.").assertIsDisplayed()
        onAllNodesWithText("Delete").onLast().performClick()
        waitForIdle()

        assertEquals(listOf("IMAGE-25.0"), calls.deletedRuntimes)
        onNodeWithText("iOS 25.0 deleted, 8.0 GB freed. 1 simulator device is now unavailable").assertIsDisplayed()
        job.cancel()
    }

    @Test
    fun `deleting unavailable devices asks first and can be cancelled`() = runComposeUiTest {
        val job = Job()
        val calls = Calls()
        val machine = machine(job, storageOf(job, actions(calls)), xcodeOnly)
        setContent {
            CompositionLocalProvider(LocalStrings provides EnglishStrings) {
                MaterialTheme { IosSimulatorsView(machine) { _, _ -> } }
            }
        }

        onNodeWithTag("delete-unavailable-devices").performClick()
        onNodeWithText("1 simulator device whose runtime is gone will be deleted with their data (1.0 GB).").assertIsDisplayed()
        onNodeWithText("Cancel").performClick()
        assertEquals(0, calls.unavailableDeletions)
        onNodeWithTag("delete-unavailable-devices").performClick()
        onAllNodesWithText("Delete unavailable devices").onLast().performClick()
        waitForIdle()

        assertEquals(1, calls.unavailableDeletions)
        onNodeWithText("1 unavailable device deleted, 1.0 GB freed").assertIsDisplayed()
        job.cancel()
    }

    @Test
    fun `an empty or unreadable storage says so`() = runComposeUiTest {
        val job = Job()
        var result = Result.success(SimulatorStorage())
        val machine = machine(job, storageOf(job, actions(Calls(), listed = { result })), xcodeOnly)
        setContent {
            CompositionLocalProvider(LocalStrings provides EnglishStrings) {
                MaterialTheme { IosSimulatorsView(machine) { _, _ -> } }
            }
        }

        onNodeWithText("No simulator runtimes installed").assertIsDisplayed()
        onNodeWithTag("delete-unavailable-devices").assertIsNotEnabled()
        result = Result.failure(IllegalStateException("simctl"))
        runBlocking { machine.simulatorStorage.load() }
        onNodeWithText("The simulator storage couldn't be read").assertIsDisplayed()
        job.cancel()
    }

    @Test
    fun `projects declaring the simulator provider still see their worktree simulators and orphans`() = runComposeUiTest {
        val job = Job()
        val task1 = Worktree("/home/dev/projects/worktrees/TASK-1", "feature/TASK-1")
        val task2 = Worktree("/home/dev/projects/worktrees/TASK-2", "feature/TASK-2")
        val project = simulatorProject.copy(worktrees = listOf(task1, task2))
        val deleted = mutableListOf<List<String>>()
        val started = mutableListOf<String>()
        val useCases = machineUseCases(
            states = mapOf(task1.path to WorktreeSimulator("UDID-1", "ShogunAi · app · TASK-1", SimulatorState.BOOTED)),
            orphans = listOf(SimulatorDevice("UDID-9", "ShogunAi · app · TASK-9", SimulatorState.SHUTDOWN)),
            deleted = deleted,
            started = started,
        )
        val machine = machine(job, storageOf(job, actions(Calls())), project, useCases = useCases)
        machine.show(MachineResource.IOS_SIMULATORS)
        setContent {
            CompositionLocalProvider(LocalStrings provides EnglishStrings) {
                MaterialTheme { IosSimulatorsView(machine) { _, _ -> } }
            }
        }

        onNodeWithText("ShogunAi · app · TASK-1").assertIsDisplayed()
        onNodeWithText("1 orphaned simulator").assertIsDisplayed()
        onNodeWithText("Stop").performClick()
        onNodeWithText("Start").performClick()
        onNodeWithText("Delete orphans").performClick()
        onNodeWithText("Cancel").performClick()
        onNodeWithText("Delete orphans").performClick()
        onAllNodesWithText("Delete orphans").onLast().performClick()
        waitForIdle()

        assertEquals(listOf("stop ${task1.path}", task2.path), started)
        assertEquals(listOf(listOf("UDID-9")), deleted)
        onNodeWithText("1 simulator deleted").assertIsDisplayed()
        job.cancel()
    }

    @Test
    fun `runtime states and reports read well in both languages`() {
        val bundled = StoredRuntime(image("26.0"), refusal = RuntimeRefusal.BUNDLED)
        val booted = StoredRuntime(image("26.1"), refusal = RuntimeRefusal.BOOTED)
        val texts = listOf<Strings>(EnglishStrings, SpanishStrings).map { strings ->
            listOf(
                runtimeStatus(bundled, strings),
                runtimeStatus(booted, strings),
                runtimeStatus(inUse, strings),
                reportText(SimulatorStorageReport.Runtime(RuntimeDeletion(unused, emptyList())), strings),
                reportText(SimulatorStorageReport.Runtime(RuntimeDeletion(unused, listOf(unavailable, unavailable))), strings),
                reportText(SimulatorStorageReport.Unavailable(UnavailableDeletion(listOf(unavailable, unavailable))), strings),
                strings.simulatorRuntimesSummary(1, "1 GB"),
                strings.unavailableDevices(2, "1 GB"),
                strings.confirmDeleteUnavailableDevices(2, "1 GB"),
            )
        }

        assertEquals(
            listOf(
                "Comes with Xcode",
                "A simulator on it is booted",
                "Used by app · TASK-1, default for new simulators",
                "iOS 25.0 deleted, 8.0 GB freed",
                "iOS 25.0 deleted, 8.0 GB freed. 2 simulator devices are now unavailable",
                "2 unavailable devices deleted, 2.0 GB freed",
                "1 runtime · 1 GB",
                "2 unavailable simulator devices · 1 GB",
                "2 simulator devices whose runtime is gone will be deleted with their data (1 GB).",
            ),
            texts[0],
        )
        assertEquals(
            listOf(
                "Viene con Xcode",
                "Hay un simulador arrancado en él",
                "Lo usa app · TASK-1, por defecto para simuladores nuevos",
                "iOS 25.0 eliminado, 8.0 GB liberados",
                "iOS 25.0 eliminado, 8.0 GB liberados. 2 dispositivos de simulador han quedado no disponibles",
                "2 dispositivos no disponibles eliminados, 2.0 GB liberados",
                "1 runtime · 1 GB",
                "2 dispositivos de simulador no disponibles · 1 GB",
                "Se eliminarán 2 dispositivos de simulador cuyo runtime ya no existe, con sus datos (1 GB).",
            ),
            texts[1],
        )
    }
}
