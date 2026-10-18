package app.luxion.shogunai.ui.machine

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.luxion.shogunai.MachineUseCases
import app.luxion.shogunai.domain.model.DaemonKind
import app.luxion.shogunai.domain.model.DaemonProcess
import app.luxion.shogunai.domain.model.DaemonStopReport
import app.luxion.shogunai.domain.model.DerivedDataDeletion
import app.luxion.shogunai.domain.model.DerivedDataFolder
import app.luxion.shogunai.domain.model.DerivedDataListing
import app.luxion.shogunai.domain.model.DerivedDataOwner
import app.luxion.shogunai.domain.model.EmulatorState
import app.luxion.shogunai.domain.model.GradleProvider
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.SimulatorDevice
import app.luxion.shogunai.domain.model.SimulatorState
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeEmulator
import app.luxion.shogunai.domain.model.WorktreeSimulator
import app.luxion.shogunai.domain.usecase.ProjectOwner
import app.luxion.shogunai.domain.usecase.ProjectWorktreeSet
import app.luxion.shogunai.domain.usecase.attributeToProjects
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

enum class MachineResource { GRADLE_DAEMONS, ANDROID_EMULATORS, IOS_SIMULATORS, XCODE_DERIVED_DATA }

data class MachineProject(
    val project: Project,
    val worktrees: List<Worktree>?,
    val declares: Set<MachineResource>,
    val gradleSettings: GradleProvider? = null,
    val gradleBuildCacheEnabled: Boolean = true,
    val compilationCache: Boolean = false,
)

data class MachineSummary(val count: Int, val bytes: Long? = null)

sealed interface MachineLoad<out T> {
    data object Loading : MachineLoad<Nothing>
    data object Unavailable : MachineLoad<Nothing>
    data class Loaded<T>(val value: T) : MachineLoad<T>
}

data class MachineDaemon(val process: DaemonProcess, val owner: ProjectOwner?)

data class MachineDerivedData(val folder: DerivedDataFolder, val owner: MachineDerivedDataOwner)

sealed interface MachineDerivedDataOwner {
    data class Project(val owner: ProjectOwner) : MachineDerivedDataOwner
    data object Orphaned : MachineDerivedDataOwner
    data object UnavailableVolume : MachineDerivedDataOwner
}

data class MachineEmulator(
    val projectId: String,
    val worktreePath: String,
    val emulator: WorktreeEmulator?,
)

data class UnownedEmulator(val serial: String, val state: EmulatorState)

data class MachineSimulator(
    val projectId: String,
    val worktreePath: String,
    val simulator: WorktreeSimulator?,
)

class MachineResources(
    private val scope: CoroutineScope,
    private val useCases: MachineUseCases,
    private val projects: () -> List<MachineProject>,
    private val pathExists: (String) -> Boolean,
    private val onDevicesChanged: suspend (projectId: String) -> Unit = {},
    private val processInterval: Duration = PROCESS_INTERVAL,
    private val summaryInterval: Duration = SUMMARY_INTERVAL,
) {
    private var shownJob: Job? = null
    var shown by mutableStateOf<MachineResource?>(null)
        private set

    var summaries by mutableStateOf<Map<MachineResource, MachineSummary>>(emptyMap())
        private set

    private var daemonProcesses by mutableStateOf<MachineLoad<List<DaemonProcess>>>(MachineLoad.Loading)
    private var derivedDataListing by mutableStateOf<MachineLoad<DerivedDataListing>>(MachineLoad.Loading)
    private var ownedEmulators by mutableStateOf<MachineLoad<Map<String, Map<String, WorktreeEmulator>>>>(MachineLoad.Loading)
    private var runningEmulators by mutableStateOf<Map<String, EmulatorState>>(emptyMap())
    private var ownedSimulators by mutableStateOf<MachineLoad<Map<String, Map<String, WorktreeSimulator>>>>(MachineLoad.Loading)
    var simulatorOrphans by mutableStateOf<List<SimulatorDevice>>(emptyList())
        private set

    var isStoppingDaemons by mutableStateOf(false)
        private set
    var daemonStopReport by mutableStateOf<DaemonStopReport?>(null)
        private set
    var isDeletingDerivedData by mutableStateOf(false)
        private set
    var derivedDataDeletionReport by mutableStateOf<DerivedDataDeletion?>(null)
        private set
    var isDeletingSimulators by mutableStateOf(false)
        private set
    var simulatorsDeleted by mutableStateOf<Int?>(null)
        private set
    var startingDevices by mutableStateOf<Set<String>>(emptySet())
        private set
    var error by mutableStateOf<Throwable?>(null)
        private set
    var errorRetry by mutableStateOf<(() -> Unit)?>(null)
        private set
    private var errorKey: String? = null

    val declared: Set<MachineResource>
        get() = projects().flatMapTo(mutableSetOf()) { it.declares }

    val visibleResources: List<MachineResource>
        get() = MachineResource.entries.filter { it in declared }

    private val worktreeSets: List<ProjectWorktreeSet>
        get() = projects().map { ProjectWorktreeSet(it.project.id, it.worktrees) }

    val gradleDaemons: MachineLoad<List<MachineDaemon>>
        get() = daemonProcesses.map { processes ->
            val sets = worktreeSets
            processes.map { process -> MachineDaemon(process, ownerOf(process, sets)) }
        }

    val gradleProjects: List<MachineProject>
        get() = projects().filter { MachineResource.GRADLE_DAEMONS in it.declares }

    val derivedData: MachineLoad<List<MachineDerivedData>>
        get() = derivedDataListing.map { listing ->
            val sets = worktreeSets
            listing.folders.map { MachineDerivedData(it.folder, derivedOwnerOf(it.owner, it.folder, sets)) }
        }

    val derivedDataOrphans: List<DerivedDataFolder>
        get() = (derivedDataListing as? MachineLoad.Loaded)?.value?.orphans.orEmpty()

    val compilationCacheBytes: Long?
        get() = (derivedDataListing as? MachineLoad.Loaded)?.value?.compilationCacheBytes

    val worktreeEmulators: MachineLoad<List<MachineEmulator>>
        get() = ownedEmulators.map { byProject ->
            declaringWorktrees(MachineResource.ANDROID_EMULATORS).map { (projectId, worktree) ->
                MachineEmulator(projectId, worktree.path, byProject[projectId]?.get(worktree.path))
            }
        }

    val unownedEmulators: List<UnownedEmulator>
        get() {
            val owned = (ownedEmulators as? MachineLoad.Loaded)?.value.orEmpty()
                .values.flatMap { it.values }.map { it.serial }.toSet()
            return runningEmulators.filterKeys { it !in owned }.map { (serial, state) -> UnownedEmulator(serial, state) }
        }

    val worktreeSimulators: MachineLoad<List<MachineSimulator>>
        get() = ownedSimulators.map { byProject ->
            declaringWorktrees(MachineResource.IOS_SIMULATORS).map { (projectId, worktree) ->
                MachineSimulator(projectId, worktree.path, byProject[projectId]?.get(worktree.path))
            }
        }

    private fun declaringWorktrees(resource: MachineResource): List<Pair<String, Worktree>> =
        projects().filter { resource in it.declares }.flatMap { machineProject ->
            machineProject.worktrees.orEmpty().filterNot { it.isPrunable }.map { machineProject.project.id to it }
        }

    fun projectName(projectId: String): String =
        projects().firstOrNull { it.project.id == projectId }?.project?.name ?: projectId

    fun show(resource: MachineResource?) {
        if (resource == shown) return
        shownJob?.cancel()
        shown = resource
        shownJob = resource?.let { scope.launch { runLoader(it) } }
    }

    fun reload() {
        val resource = shown ?: return
        scope.launch { load(resource) }
    }

    suspend fun runSummaryTicks() {
        while (true) {
            refreshSummaries()
            delay(summaryInterval)
        }
    }

    suspend fun refreshSummaries() {
        val resources = declared
        val current = summaries.filterKeys { it in resources }.toMutableMap()
        if (MachineResource.GRADLE_DAEMONS in resources) {
            useCases.listDaemons().getOrNull()?.let { daemons ->
                current[MachineResource.GRADLE_DAEMONS] = MachineSummary(daemons.size, daemons.sumOf { it.residentMemoryBytes })
            }
        }
        if (MachineResource.ANDROID_EMULATORS in resources) {
            readRunningEmulators()?.let { running ->
                current[MachineResource.ANDROID_EMULATORS] = MachineSummary(running.count { it.value != EmulatorState.STOPPED })
            }
        }
        if (MachineResource.IOS_SIMULATORS in resources) {
            readSimulators()?.let { byProject ->
                current[MachineResource.IOS_SIMULATORS] =
                    MachineSummary(byProject.values.sumOf { it.values.count { sim -> sim.state == SimulatorState.BOOTED } })
            }
        }
        if (MachineResource.XCODE_DERIVED_DATA in resources) {
            if (derivedDataListing !is MachineLoad.Loaded) loadDerivedData()
            (derivedDataListing as? MachineLoad.Loaded)?.value?.let { listing ->
                current[MachineResource.XCODE_DERIVED_DATA] = MachineSummary(listing.folders.size, listing.folders.sumOf { it.folder.sizeBytes })
            }
        }
        summaries = current
    }

    private suspend fun runLoader(resource: MachineResource) {
        if (resource == MachineResource.XCODE_DERIVED_DATA) {
            load(resource)
            return
        }
        while (true) {
            load(resource)
            delay(processInterval)
        }
    }

    private suspend fun load(resource: MachineResource) {
        when (resource) {
            MachineResource.GRADLE_DAEMONS -> loadDaemons()
            MachineResource.ANDROID_EMULATORS -> loadEmulators()
            MachineResource.IOS_SIMULATORS -> loadSimulators()
            MachineResource.XCODE_DERIVED_DATA -> loadDerivedData()
        }
    }

    private suspend fun loadDaemons() {
        daemonProcesses = useCases.listDaemons().fold({ MachineLoad.Loaded(it) }, { MachineLoad.Unavailable })
    }

    private suspend fun loadEmulators() {
        val declaring = projects().filter { MachineResource.ANDROID_EMULATORS in it.declares && it.worktrees != null }
        val states = declaring.associate { machineProject ->
            machineProject.project.id to
                useCases.readEmulatorStates(machineProject.project.config, machineProject.worktrees.orEmpty()).getOrNull()
        }
        if (states.values.any { it == null }) {
            ownedEmulators = MachineLoad.Unavailable
            return
        }
        ownedEmulators = MachineLoad.Loaded(
            states.mapValues { (_, byPath) -> byPath.orEmpty() }.filterValues { it.isNotEmpty() },
        )
        runningEmulators = readRunningEmulators() ?: runningEmulators
    }

    private suspend fun readRunningEmulators(): Map<String, EmulatorState>? {
        val declaring = projects().firstOrNull { MachineResource.ANDROID_EMULATORS in it.declares } ?: return null
        return useCases.readRunningEmulators(declaring.project.config).getOrNull()
    }

    private suspend fun loadSimulators() {
        val owned = readSimulators()
        ownedSimulators = if (owned == null) MachineLoad.Unavailable else MachineLoad.Loaded(owned)
        val declaring = projects().firstOrNull { MachineResource.IOS_SIMULATORS in it.declares } ?: return
        simulatorOrphans = useCases.readSimulatorOrphans(declaring.project.config).getOrNull() ?: simulatorOrphans
    }

    private suspend fun readSimulators(): Map<String, Map<String, WorktreeSimulator>>? {
        val declaring = projects().filter { MachineResource.IOS_SIMULATORS in it.declares && it.worktrees != null }
        val states = declaring.associate { machineProject ->
            machineProject.project.id to
                useCases.readSimulatorStates(machineProject.project.config, machineProject.worktrees.orEmpty()).getOrNull()
        }
        if (states.values.any { it == null }) return null
        return states.mapValues { (_, byPath) -> byPath.orEmpty() }.filterValues { it.isNotEmpty() }
    }

    private suspend fun loadDerivedData() {
        val includeCompilationCache = projects().any { it.compilationCache }
        derivedDataListing = useCases.listDerivedData(includeCompilationCache)
            .fold({ MachineLoad.Loaded(it) }, { MachineLoad.Unavailable })
    }

    fun stopGradleDaemon(pid: Long) = stopGradleDaemons(listOf(pid))

    fun stopIdleGradleDaemons() = stopGradleDaemons(null)

    private fun stopGradleDaemons(pids: List<Long>?) {
        if (isStoppingDaemons) return
        isStoppingDaemons = true
        scope.launch {
            try {
                useCases.stopDaemons(pids)
                    .onSuccess { daemonStopReport = it; clearError(DAEMONS_KEY) }
                    .onFailure { fail(DAEMONS_KEY, it) { stopGradleDaemons(pids) } }
                loadDaemons()
            } finally {
                isStoppingDaemons = false
            }
        }
    }

    fun dismissDaemonStopReport() {
        daemonStopReport = null
    }

    fun deleteOrphanedDerivedData() {
        if (isDeletingDerivedData) return
        isDeletingDerivedData = true
        scope.launch {
            try {
                useCases.deleteOrphanedDerivedData()
                    .onSuccess { derivedDataDeletionReport = it; clearError(DERIVED_DATA_KEY) }
                    .onFailure { fail(DERIVED_DATA_KEY, it, ::deleteOrphanedDerivedData) }
                loadDerivedData()
            } finally {
                isDeletingDerivedData = false
            }
        }
    }

    fun dismissDerivedDataDeletionReport() {
        derivedDataDeletionReport = null
    }

    fun deleteSimulatorOrphans() {
        if (isDeletingSimulators || simulatorOrphans.isEmpty()) return
        isDeletingSimulators = true
        val udids = simulatorOrphans.map { it.udid }
        scope.launch {
            try {
                useCases.deleteSimulators(udids)
                    .onSuccess { simulatorsDeleted = it.size; clearError(SIMULATOR_ORPHANS_KEY) }
                    .onFailure { fail(SIMULATOR_ORPHANS_KEY, it, ::deleteSimulatorOrphans) }
                loadSimulators()
            } finally {
                isDeletingSimulators = false
            }
        }
    }

    fun dismissSimulatorsDeleted() {
        simulatorsDeleted = null
    }

    fun startEmulator(projectId: String, worktreePath: String) =
        runDeviceAction(projectId, worktreePath) { config ->
            useCases.startEmulator(config, worktreePath).also { loadEmulators() }
        }

    fun stopEmulator(projectId: String, worktreePath: String) =
        runDeviceAction(projectId, worktreePath) { config ->
            useCases.stopEmulator(config, worktreePath).also { loadEmulators() }
        }

    fun startSimulator(projectId: String, worktreePath: String) =
        runDeviceAction(projectId, worktreePath) { config ->
            useCases.startSimulator(config, worktreePath).also { loadSimulators() }
        }

    fun stopSimulator(projectId: String, worktreePath: String) =
        runDeviceAction(projectId, worktreePath) { config ->
            useCases.stopSimulator(config, worktreePath).also { loadSimulators() }
        }

    fun stopUnownedEmulator(serial: String) {
        val declaring = projects().firstOrNull { MachineResource.ANDROID_EMULATORS in it.declares } ?: return
        if (serial in startingDevices) return
        startingDevices = startingDevices + serial
        scope.launch {
            try {
                val key = "unowned:$serial"
                useCases.stopEmulatorSerials(declaring.project.config, listOf(serial))
                    .onSuccess { clearError(key) }
                    .onFailure { fail(key, it) { stopUnownedEmulator(serial) } }
                loadEmulators()
            } finally {
                startingDevices = startingDevices - serial
            }
        }
    }

    fun isBusy(projectId: String, worktreePath: String): Boolean = deviceKey(projectId, worktreePath) in startingDevices

    fun dismissError() {
        error = null
        errorRetry = null
        errorKey = null
    }

    fun retryError() {
        val retry = errorRetry ?: return
        dismissError()
        retry()
    }

    private fun fail(key: String, failure: Throwable, retry: () -> Unit) {
        error = failure
        errorRetry = retry
        errorKey = key
    }

    private fun clearError(key: String) {
        if (errorKey == key) dismissError()
    }

    private fun runDeviceAction(projectId: String, worktreePath: String, action: suspend (config: ProjectConfig) -> Result<*>) {
        val key = deviceKey(projectId, worktreePath)
        val config = projects().firstOrNull { it.project.id == projectId }?.project?.config ?: return
        if (key in startingDevices) return
        startingDevices = startingDevices + key
        scope.launch {
            try {
                action(config)
                    .onSuccess { clearError(key) }
                    .onFailure { fail(key, it) { runDeviceAction(projectId, worktreePath, action) } }
                onDevicesChanged(projectId)
            } finally {
                startingDevices = startingDevices - key
            }
        }
    }

    private fun ownerOf(process: DaemonProcess, sets: List<ProjectWorktreeSet>): ProjectOwner? {
        if (process.kind != DaemonKind.GRADLE) return null
        val directory = process.lastBuildDirectory ?: return null
        return attributeToProjects(directory, sets, pathExists)
    }

    private fun derivedOwnerOf(
        owner: DerivedDataOwner,
        folder: DerivedDataFolder,
        sets: List<ProjectWorktreeSet>,
    ): MachineDerivedDataOwner = when (owner) {
        DerivedDataOwner.Orphaned -> MachineDerivedDataOwner.Orphaned
        DerivedDataOwner.UnavailableVolume -> MachineDerivedDataOwner.UnavailableVolume
        is DerivedDataOwner.Worktree, DerivedDataOwner.OtherProject ->
            MachineDerivedDataOwner.Project(attributeToProjects(folder.workspacePath, sets) { true })
    }

    companion object {
        val PROCESS_INTERVAL = 5.seconds
        val SUMMARY_INTERVAL = 30.seconds
        private const val DAEMONS_KEY = "daemons"
        private const val DERIVED_DATA_KEY = "derivedData"
        private const val SIMULATOR_ORPHANS_KEY = "simulatorOrphans"

        fun deviceKey(projectId: String, worktreePath: String): String = "$projectId:$worktreePath"
    }
}

private fun <T, R> MachineLoad<T>.map(transform: (T) -> R): MachineLoad<R> = when (this) {
    MachineLoad.Loading -> MachineLoad.Loading
    MachineLoad.Unavailable -> MachineLoad.Unavailable
    is MachineLoad.Loaded -> MachineLoad.Loaded(transform(value))
}
