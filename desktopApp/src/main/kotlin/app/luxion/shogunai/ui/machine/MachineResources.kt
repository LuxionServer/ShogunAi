package app.luxion.shogunai.ui.machine

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.luxion.shogunai.MachineUseCases
import app.luxion.shogunai.domain.model.CapacityProfile
import app.luxion.shogunai.domain.model.DaemonKind
import app.luxion.shogunai.domain.model.DaemonProcess
import app.luxion.shogunai.domain.model.DaemonStopReport
import app.luxion.shogunai.domain.model.DerivedDataDeletion
import app.luxion.shogunai.domain.model.DerivedDataFolder
import app.luxion.shogunai.domain.model.DerivedDataListing
import app.luxion.shogunai.domain.model.DerivedDataOwner
import app.luxion.shogunai.domain.model.DerivedDataQuota
import app.luxion.shogunai.domain.model.DerivedDataQuotaMode
import app.luxion.shogunai.domain.model.PackageCacheSizes
import app.luxion.shogunai.domain.model.DerivedDataTrim
import app.luxion.shogunai.domain.model.EmulatorState
import app.luxion.shogunai.domain.model.ComposeDownReport
import app.luxion.shogunai.domain.model.ComposeLeftoversReport
import app.luxion.shogunai.domain.model.ComposeResource
import app.luxion.shogunai.domain.model.ComposeOwner
import app.luxion.shogunai.domain.model.GradleProvider
import app.luxion.shogunai.domain.model.MavenDaemonProcess
import app.luxion.shogunai.domain.model.MavenDaemonStopReport
import app.luxion.shogunai.domain.model.WorktreeComposeProject
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.model.PoolUsage
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.ResourceKind
import app.luxion.shogunai.domain.model.SimulatorDevice
import app.luxion.shogunai.domain.model.SimulatorState
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeEmulator
import app.luxion.shogunai.domain.model.WorktreeSimulator
import app.luxion.shogunai.domain.usecase.ProjectOwner
import app.luxion.shogunai.domain.usecase.ResourceAcquisition
import app.luxion.shogunai.domain.usecase.ProjectWorktreeSet
import app.luxion.shogunai.domain.usecase.attributeToProjects
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

enum class MachineResource { GRADLE_DAEMONS, ANDROID_EMULATORS, IOS_SIMULATORS, XCODE_DERIVED_DATA, MAVEN_DAEMONS, COMPOSE_PROJECTS, DESKTOP_TURN }

data class MachineProject(
    val project: Project,
    val worktrees: List<Worktree>?,
    val declares: Set<MachineResource>,
    val gradleSettings: GradleProvider? = null,
    val gradleBuildCacheEnabled: Boolean = true,
    val compilationCache: Boolean = false,
)

data class MachineSummary(
    val count: Int,
    val bytes: Long? = null,
    val poolInUse: Int? = null,
    val poolLimit: Int? = null,
    val calculating: Boolean = false,
    val quotaBytes: Long? = null,
    val quotaAutomatic: Boolean = false,
    val holder: String? = null,
)

sealed interface PoolNoticeContent {
    data class Released(val kind: ResourceKind, val released: String, val forWorktree: String) : PoolNoticeContent
    data class Trimmed(val count: Int, val limit: Int?) : PoolNoticeContent
}

data class PoolNotice(val id: Long, val resource: MachineResource, val content: PoolNoticeContent)

sealed interface MachineLoad<out T> {
    data object Loading : MachineLoad<Nothing>
    data object Unavailable : MachineLoad<Nothing>
    data class Loaded<T>(val value: T) : MachineLoad<T>
}

data class MachineDaemon(val process: DaemonProcess, val owner: ProjectOwner?)

data class MachineMavenDaemon(val process: MavenDaemonProcess, val owner: ProjectOwner?)

data class MachineComposeProject(val projectId: String, val entry: WorktreeComposeProject)

data class MachineComposeLeftover(val projectId: String, val resource: ComposeResource)

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
    val desktopTurn: DesktopTurnWatch = DesktopTurnWatch.NONE,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private var shownJob: Job? = null
    private val windowActive = MutableStateFlow(true)
    var shown by mutableStateOf<MachineResource?>(null)
        private set

    private var providerSummaries by mutableStateOf<Map<MachineResource, MachineSummary>>(emptyMap())

    var summaryFailures by mutableStateOf<Map<MachineResource, Throwable>>(emptyMap())
        private set

    val summaries: Map<MachineResource, MachineSummary>
        get() {
            val turn = desktopTurn.state
            if (!desktopTurn.isActive) return providerSummaries
            return providerSummaries + (MachineResource.DESKTOP_TURN to MachineSummary(turn.waiting.size, holder = turn.holder?.displayName))
        }

    private var daemonProcesses by mutableStateOf<MachineLoad<List<DaemonProcess>>>(MachineLoad.Loading)
    private var mavenProcesses by mutableStateOf<MachineLoad<List<MavenDaemonProcess>>>(MachineLoad.Loading)
    private var composeProjects by mutableStateOf<MachineLoad<List<MachineComposeProject>>>(MachineLoad.Loading)
    var composeUnavailableReason by mutableStateOf<String?>(null)
        private set
    private var composeFailure: Throwable? = null
    private var derivedDataFailure: Throwable? = null
    var isTakingDownCompose by mutableStateOf(false)
        private set
    var composeDownReport by mutableStateOf<ComposeDownReport?>(null)
        private set
    var composeLeftovers by mutableStateOf<List<MachineComposeLeftover>>(emptyList())
        private set
    var isRemovingComposeLeftovers by mutableStateOf(false)
        private set
    var composeLeftoversReport by mutableStateOf<ComposeLeftoversReport?>(null)
        private set
    private var derivedDataListing by mutableStateOf<MachineLoad<DerivedDataListing>>(MachineLoad.Loading)
    private var derivedDataSizes by mutableStateOf<Map<String, Long>>(emptyMap())
    private var derivedDataPackageSizes by mutableStateOf<Map<String, Long>>(emptyMap())
    var packageCacheSizes by mutableStateOf<PackageCacheSizes?>(null)
        private set
    private var compilationCacheSize by mutableStateOf<Long?>(null)
    private var compilationCacheMeasured by mutableStateOf(false)
    private var resolvedDerivedDataQuota by mutableStateOf<DerivedDataQuota?>(null)
    private var sizeJob: Job? = null
    private var ownedEmulators by mutableStateOf<MachineLoad<Map<String, Map<String, WorktreeEmulator>>>>(MachineLoad.Loading)
    private var runningEmulators by mutableStateOf<Map<String, EmulatorState>>(emptyMap())
    private var ownedSimulators by mutableStateOf<MachineLoad<Map<String, Map<String, WorktreeSimulator>>>>(MachineLoad.Loading)
    var simulatorOrphans by mutableStateOf<List<SimulatorDevice>>(emptyList())
        private set

    var isStoppingDaemons by mutableStateOf(false)
        private set
    var daemonStopReport by mutableStateOf<DaemonStopReport?>(null)
        private set
    var isStoppingMavenDaemons by mutableStateOf(false)
        private set
    var mavenDaemonStopReport by mutableStateOf<MavenDaemonStopReport?>(null)
        private set
    var isDeletingDerivedData by mutableStateOf(false)
        private set
    var derivedDataDeletionReport by mutableStateOf<DerivedDataDeletion?>(null)
        private set
    var derivedDataTrimReport by mutableStateOf<DerivedDataTrim?>(null)
        private set
    private var derivedDataTrim by mutableStateOf<DerivedDataTrim?>(null)
    private var isTrimmingDerivedData = false
    var isDeletingSimulators by mutableStateOf(false)
        private set
    var simulatorsDeleted by mutableStateOf<Int?>(null)
        private set
    var startingDevices by mutableStateOf<Set<String>>(emptySet())
        private set
    var pools by mutableStateOf<Map<ResourceKind, PoolUsage>>(emptyMap())
        private set
    var notices by mutableStateOf<List<PoolNotice>>(emptyList())
        private set
    private var nextNoticeId = 0L
    var error by mutableStateOf<Throwable?>(null)
        private set
    var errorRetry by mutableStateOf<(() -> Unit)?>(null)
        private set
    private var errorKey: String? = null

    val profile: CapacityProfile get() = useCases.resourceSettings().profile

    fun poolOf(resource: MachineResource): PoolUsage? = poolKindOf(resource)?.let { pools[it] }

    fun noticesOf(resource: MachineResource): List<PoolNotice> = notices.filter { it.resource == resource }

    fun dismissNotices(resource: MachineResource) {
        notices = notices.filterNot { it.resource == resource }
    }

    private fun addNotice(resource: MachineResource, content: PoolNoticeContent) {
        notices = notices + PoolNotice(nextNoticeId++, resource, content)
    }

    private fun recordReleases(resource: MachineResource, kind: ResourceKind, worktreePath: String, acquisition: ResourceAcquisition) {
        acquisition.releases.forEach { release ->
            addNotice(resource, PoolNoticeContent.Released(kind, release.holderName, worktreePath.substringAfterLast('/')))
        }
    }

    private suspend fun <T> io(call: suspend () -> T): T = withContext(ioDispatcher) { call() }

    private suspend fun loadPool(kind: ResourceKind) {
        val resource = machineResourceOf(kind)
        val project = projects().firstOrNull { resource in it.declares } ?: return
        io { useCases.readPool(kind, project.project.config, project.worktrees) }.getOrNull()?.let { usage ->
            pools = pools + (kind to usage)
        }
    }

    val declared: Set<MachineResource>
        get() = projects().flatMapTo(mutableSetOf()) { it.declares }

    val visibleResources: List<MachineResource>
        get() {
            val resources = declared + setOfNotNull(MachineResource.DESKTOP_TURN.takeIf { desktopTurn.isActive || shown == MachineResource.DESKTOP_TURN })
            return MachineResource.entries.filter { it in resources }
        }

    fun ownerOfPath(worktreePath: String): ProjectOwner = attributeToProjects(worktreePath, worktreeSets, pathExists)

    private val worktreeSets: List<ProjectWorktreeSet>
        get() = projects().map { ProjectWorktreeSet(it.project.id, it.worktrees) }

    val gradleDaemons: MachineLoad<List<MachineDaemon>>
        get() = daemonProcesses.map { processes ->
            val sets = worktreeSets
            processes.map { process -> MachineDaemon(process, ownerOf(process, sets)) }
        }

    val mavenDaemons: MachineLoad<List<MachineMavenDaemon>>
        get() = mavenProcesses.map { processes ->
            val sets = worktreeSets
            processes.map { process ->
                MachineMavenDaemon(process, process.workingDirectory?.let { attributeToProjects(it, sets, pathExists) })
            }
        }

    val composeProjectList: MachineLoad<List<MachineComposeProject>> get() = composeProjects

    val removedComposeProjectNames: List<String>
        get() = (composeProjects as? MachineLoad.Loaded)?.value.orEmpty()
            .filter { it.entry.owner == ComposeOwner.RemovedWorktree }
            .map { it.entry.project.name }
            .distinct()

    val gradleProjects: List<MachineProject>
        get() = projects().filter { MachineResource.GRADLE_DAEMONS in it.declares }

    val derivedData: MachineLoad<List<MachineDerivedData>>
        get() = derivedDataListing.map { listing ->
            val sets = worktreeSets
            val sizes = derivedDataSizes
            val packages = derivedDataPackageSizes
            listing.folders.map { entry ->
                val folder = entry.folder.copy(sizeBytes = sizes[entry.folder.path], packagesBytes = packages[entry.folder.path])
                MachineDerivedData(folder, derivedOwnerOf(entry.owner, folder, sets))
            }
        }

    val derivedDataOrphans: List<DerivedDataFolder>
        get() {
            val sizes = derivedDataSizes
            return (derivedDataListing as? MachineLoad.Loaded)?.value?.orphans.orEmpty()
                .map { it.copy(sizeBytes = sizes[it.path]) }
        }

    val derivedDataQuotaBytes: Long?
        get() {
            val settings = useCases.resourceSettings()
            return when (settings.quotaMode) {
                DerivedDataQuotaMode.NONE -> null
                DerivedDataQuotaMode.FIXED -> settings.fixedDerivedDataQuotaBytes
                DerivedDataQuotaMode.AUTOMATIC -> resolvedDerivedDataQuota?.takeIf { it.mode == DerivedDataQuotaMode.AUTOMATIC }?.bytes
            }
        }

    val isDerivedDataQuotaAutomatic: Boolean
        get() = useCases.resourceSettings().quotaMode == DerivedDataQuotaMode.AUTOMATIC

    val isDerivedDataQuotaUnknown: Boolean
        get() = isDerivedDataQuotaAutomatic && resolvedDerivedDataQuota?.isUnknown == true

    val derivedDataUsageBytes: Long?
        get() {
            val listing = (derivedDataListing as? MachineLoad.Loaded)?.value ?: return null
            val sizes = derivedDataSizes
            if (listing.folders.any { it.folder.path !in sizes }) return null
            return sizes.values.sum()
        }

    val derivedDataQuotaExceeded: Boolean
        get() {
            val quota = derivedDataQuotaBytes ?: return false
            return (derivedDataUsageBytes ?: return false) > quota
        }

    val derivedDataInUse: List<String>
        get() = derivedDataTrim?.inUse.orEmpty().map { (it.owner as DerivedDataOwner.Worktree).path.substringAfterLast('/') }

    val compilationCacheBytes: Long?
        get() = compilationCacheSize

    val isCompilationCacheCalculating: Boolean
        get() = projects().any { it.compilationCache } && derivedDataListing is MachineLoad.Loaded && !compilationCacheMeasured

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

    fun project(projectId: String): Project? = projects().firstOrNull { it.project.id == projectId }?.project

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
        scope.launch { load(resource, remeasure = true) }
    }

    fun setWindowActive(active: Boolean) {
        windowActive.value = active
    }

    suspend fun runSummaryTicks() {
        while (true) {
            if (windowActive.value) refreshSummaries() else trimGradleDaemonsWhileHidden()
            withTimeoutOrNull(summaryInterval) { windowActive.drop(1).first { it } }
        }
    }

    private suspend fun trimGradleDaemonsWhileHidden() {
        if (MachineResource.GRADLE_DAEMONS in declared) trimGradleDaemons()
    }

    suspend fun refreshSummaries() {
        val resources = declared
        val current = providerSummaries.filterKeys { it in resources }.toMutableMap()
        val failures = mutableMapOf<MachineResource, Throwable>()
        fun <T> record(resource: MachineResource, result: Result<T>?, summary: (T) -> MachineSummary) {
            result?.fold({ current[resource] = summary(it) }, { failures[resource] = it })
        }
        if (MachineResource.GRADLE_DAEMONS in resources) {
            trimGradleDaemons()
            val daemons = io { useCases.listDaemons() }
            if (daemons.isSuccess) loadPool(ResourceKind.GRADLE_DAEMON)
            record(MachineResource.GRADLE_DAEMONS, daemons) { listed ->
                val pool = pools[ResourceKind.GRADLE_DAEMON]
                MachineSummary(listed.size, listed.sumOf { it.residentMemoryBytes }, pool?.inUse, pool?.effectiveLimit)
            }
        }
        if (MachineResource.MAVEN_DAEMONS in resources) {
            record(MachineResource.MAVEN_DAEMONS, io { useCases.listMavenDaemons() }) { daemons ->
                MachineSummary(daemons.size, daemons.sumOf { it.residentMemoryBytes })
            }
        }
        if (MachineResource.COMPOSE_PROJECTS in resources) {
            val projects = readComposeProjects()
            record(MachineResource.COMPOSE_PROJECTS, projects?.let { Result.success(it) } ?: composeFailure?.let { Result.failure(it) }) {
                MachineSummary(it.size)
            }
        }
        if (MachineResource.ANDROID_EMULATORS in resources) {
            val running = runningEmulatorsResult()
            if (running?.isSuccess == true) loadPool(ResourceKind.EMULATOR)
            record(MachineResource.ANDROID_EMULATORS, running) { states ->
                val pool = pools[ResourceKind.EMULATOR]
                MachineSummary(states.count { it.value != EmulatorState.STOPPED }, poolInUse = pool?.inUse, poolLimit = pool?.effectiveLimit)
            }
        }
        if (MachineResource.IOS_SIMULATORS in resources) {
            val simulators = simulatorsResult()
            if (simulators.isSuccess) loadPool(ResourceKind.IOS_SIMULATOR)
            record(MachineResource.IOS_SIMULATORS, simulators) { byProject ->
                val pool = pools[ResourceKind.IOS_SIMULATOR]
                MachineSummary(
                    byProject.values.sumOf { it.values.count { sim -> sim.state == SimulatorState.BOOTED } },
                    poolInUse = pool?.inUse,
                    poolLimit = pool?.effectiveLimit,
                )
            }
        }
        if (MachineResource.XCODE_DERIVED_DATA in resources) {
            if (derivedDataListing !is MachineLoad.Loaded) loadDerivedData()
            (derivedDataListing as? MachineLoad.Loaded)?.value?.let { listing ->
                val sizes = derivedDataSizes
                val calculating = listing.folders.any { it.folder.path !in sizes }
                current[MachineResource.XCODE_DERIVED_DATA] = MachineSummary(
                    count = listing.folders.size,
                    bytes = if (calculating) null else sizes.values.sum(),
                    calculating = calculating,
                    quotaBytes = derivedDataQuotaBytes,
                    quotaAutomatic = isDerivedDataQuotaAutomatic && derivedDataQuotaBytes != null,
                )
            }
            derivedDataFailure?.let { failures[MachineResource.XCODE_DERIVED_DATA] = it }
        } else {
            stopDerivedDataMeasuring()
        }
        desktopTurn.refresh()
        providerSummaries = current
        summaryFailures = failures
    }

    private suspend fun trimGradleDaemons() {
        val trim = io { useCases.trimGradleDaemons(null) }.getOrNull() ?: return
        if (trim.stopped.isNotEmpty()) addNotice(MachineResource.GRADLE_DAEMONS, PoolNoticeContent.Trimmed(trim.stopped.size, trim.limit))
    }

    private suspend fun runLoader(resource: MachineResource) {
        if (resource == MachineResource.XCODE_DERIVED_DATA) {
            load(resource)
            return
        }
        while (true) {
            windowActive.first { it }
            load(resource)
            delay(processInterval)
        }
    }

    private suspend fun load(resource: MachineResource, remeasure: Boolean = false) {
        when (resource) {
            MachineResource.GRADLE_DAEMONS -> loadDaemons()
            MachineResource.ANDROID_EMULATORS -> loadEmulators()
            MachineResource.IOS_SIMULATORS -> loadSimulators()
            MachineResource.XCODE_DERIVED_DATA -> loadDerivedData(remeasure)
            MachineResource.MAVEN_DAEMONS -> loadMavenDaemons()
            MachineResource.COMPOSE_PROJECTS -> loadComposeProjects()
            MachineResource.DESKTOP_TURN -> desktopTurn.refresh()
        }
    }

    private suspend fun loadMavenDaemons() {
        mavenProcesses = io { useCases.listMavenDaemons() }.fold({ MachineLoad.Loaded(it) }, { MachineLoad.Unavailable })
    }

    private suspend fun loadComposeProjects() {
        val projects = readComposeProjects()
        composeProjects = if (projects == null) MachineLoad.Unavailable else MachineLoad.Loaded(projects)
        composeLeftovers = if (projects == null) emptyList() else readComposeLeftovers()
    }

    private suspend fun readComposeLeftovers(): List<MachineComposeLeftover> =
        composeDeclaring().flatMap { machineProject ->
            io { useCases.listComposeLeftovers(machineProject.project.config, machineProject.worktrees.orEmpty()) }.getOrNull().orEmpty()
                .map { MachineComposeLeftover(machineProject.project.id, it) }
        }

    private fun composeDeclaring(): List<MachineProject> =
        projects().filter { MachineResource.COMPOSE_PROJECTS in it.declares && it.worktrees != null }

    fun removeComposeLeftovers(removeVolumes: Boolean) {
        val leftovers = composeLeftovers
        if (isRemovingComposeLeftovers || leftovers.isEmpty()) return
        isRemovingComposeLeftovers = true
        scope.launch {
            try {
                val configs = composeDeclaring().associate { it.project.id to it.project.config }
                composeLeftoversReport = leftovers.groupBy { it.projectId }
                    .mapNotNull { (projectId, entries) ->
                        configs[projectId]?.let { io { useCases.removeComposeLeftovers(it, entries.map { entry -> entry.resource }, removeVolumes) } }
                    }
                    .fold(ComposeLeftoversReport()) { total, next ->
                        ComposeLeftoversReport(total.removed + next.removed, total.kept + next.kept, total.failures + next.failures)
                    }
                loadComposeProjects()
            } finally {
                isRemovingComposeLeftovers = false
            }
        }
    }

    fun dismissComposeLeftoversReport() {
        composeLeftoversReport = null
    }

    private suspend fun readComposeProjects(): List<MachineComposeProject>? {
        val listed = composeDeclaring().map { machineProject ->
            val config = machineProject.project.config
            val root = config.worktreesRoot.takeIf { it.isNotBlank() }
            machineProject.project.id to io { useCases.listComposeProjects(config, machineProject.worktrees.orEmpty(), root) }
        }
        listed.firstNotNullOfOrNull { (_, result) -> result.exceptionOrNull() }?.let { failure ->
            composeFailure = failure
            composeUnavailableReason = (failure as? WorktreeError.DockerUnavailable)?.reason ?: failure.message
            return null
        }
        composeFailure = null
        composeUnavailableReason = null
        return listed.flatMap { (projectId, result) -> result.getOrThrow().map { MachineComposeProject(projectId, it) } }
    }

    fun retryComposeProjects() {
        scope.launch { loadComposeProjects() }
    }

    fun takeDownRemovedComposeProjects() {
        val names = removedComposeProjectNames
        if (isTakingDownCompose || names.isEmpty()) return
        isTakingDownCompose = true
        scope.launch {
            try {
                composeDownReport = io { useCases.takeDownComposeProjects(names) }
                loadComposeProjects()
            } finally {
                isTakingDownCompose = false
            }
        }
    }

    fun dismissComposeDownReport() {
        composeDownReport = null
    }

    private suspend fun loadDaemons() {
        daemonProcesses = io { useCases.listDaemons() }.fold({ MachineLoad.Loaded(it) }, { MachineLoad.Unavailable })
        loadPool(ResourceKind.GRADLE_DAEMON)
    }

    private suspend fun loadEmulators() {
        val declaring = projects().filter { MachineResource.ANDROID_EMULATORS in it.declares && it.worktrees != null }
        val states = declaring.associate { machineProject ->
            machineProject.project.id to
                io { useCases.readEmulatorStates(machineProject.project.config, machineProject.worktrees.orEmpty()) }.getOrNull()
        }
        if (states.values.any { it == null }) {
            ownedEmulators = MachineLoad.Unavailable
            return
        }
        ownedEmulators = MachineLoad.Loaded(
            states.mapValues { (_, byPath) -> byPath.orEmpty() }.filterValues { it.isNotEmpty() },
        )
        runningEmulators = readRunningEmulators() ?: runningEmulators
        loadPool(ResourceKind.EMULATOR)
    }

    private suspend fun readRunningEmulators(): Map<String, EmulatorState>? = runningEmulatorsResult()?.getOrNull()

    private suspend fun runningEmulatorsResult(): Result<Map<String, EmulatorState>>? {
        val declaring = projects().firstOrNull { MachineResource.ANDROID_EMULATORS in it.declares } ?: return null
        return io { useCases.readRunningEmulators(declaring.project.config) }
    }

    private suspend fun loadSimulators() {
        val owned = readSimulators()
        ownedSimulators = if (owned == null) MachineLoad.Unavailable else MachineLoad.Loaded(owned)
        val declaring = projects().firstOrNull { MachineResource.IOS_SIMULATORS in it.declares } ?: return
        simulatorOrphans = io { useCases.readSimulatorOrphans(declaring.project.config) }.getOrNull() ?: simulatorOrphans
        loadPool(ResourceKind.IOS_SIMULATOR)
    }

    private suspend fun readSimulators(): Map<String, Map<String, WorktreeSimulator>>? = simulatorsResult().getOrNull()

    private suspend fun simulatorsResult(): Result<Map<String, Map<String, WorktreeSimulator>>> {
        val declaring = projects().filter { MachineResource.IOS_SIMULATORS in it.declares && it.worktrees != null }
        val states = declaring.associate { machineProject ->
            machineProject.project.id to io { useCases.readSimulatorStates(machineProject.project.config, machineProject.worktrees.orEmpty()) }
        }
        states.values.firstNotNullOfOrNull { it.exceptionOrNull() }?.let { return Result.failure(it) }
        return Result.success(states.mapValues { (_, result) -> result.getOrThrow() }.filterValues { it.isNotEmpty() })
    }

    private suspend fun loadDerivedData(remeasure: Boolean = false) {
        sizeJob?.cancel()
        val listing = io { useCases.listDerivedData() }
        derivedDataSizes = emptyMap()
        derivedDataPackageSizes = emptyMap()
        compilationCacheSize = null
        compilationCacheMeasured = false
        packageCacheSizes = null
        derivedDataListing = listing.fold({ MachineLoad.Loaded(it) }, { MachineLoad.Unavailable })
        derivedDataFailure = listing.exceptionOrNull()
        listing.getOrNull()?.let { sizeJob = scope.launch { measureDerivedData(it.folders.map { entry -> entry.folder }, remeasure) } }
    }

    private suspend fun measureDerivedData(folders: List<DerivedDataFolder>, remeasure: Boolean) {
        coroutineScope {
            val compilationCache = async { if (projects().any { it.compilationCache }) io { useCases.measureCompilationCache() }.getOrNull() else null }
            val packageCaches = async { io { useCases.measurePackageCaches() }.getOrNull() }
            useCases.measureDerivedData(folders, remeasure).flowOn(ioDispatcher).collect { measured ->
                derivedDataSizes = derivedDataSizes + (measured.path to (measured.sizeBytes ?: 0L))
                measured.packagesBytes?.let { derivedDataPackageSizes = derivedDataPackageSizes + (measured.path to it) }
            }
            compilationCacheSize = compilationCache.await()
            packageCacheSizes = packageCaches.await()
        }
        currentCoroutineContext().ensureActive()
        compilationCacheMeasured = true
        applyDerivedDataQuota()
    }

    private suspend fun applyDerivedDataQuota() {
        val settings = useCases.resourceSettings()
        if (settings.quotaMode == DerivedDataQuotaMode.NONE) {
            resolvedDerivedDataQuota = null
            derivedDataTrim = null
            return
        }
        val listing = (derivedDataListing as? MachineLoad.Loaded)?.value ?: return
        if (isTrimmingDerivedData) return
        isTrimmingDerivedData = true
        try {
            val sizes = derivedDataSizes
            val measured = listing.copy(folders = listing.folders.map { it.copy(folder = it.folder.copy(sizeBytes = sizes[it.folder.path])) })
            val resolved = io { useCases.resolveDerivedDataQuota(settings, measured.folders.sumOf { it.folder.sizeBytes ?: 0L }) }
            resolvedDerivedDataQuota = resolved
            val quota = resolved.bytes
            if (quota == null) {
                derivedDataTrim = null
                return
            }
            val declaring = projects().filter { MachineResource.XCODE_DERIVED_DATA in it.declares }
            io { useCases.trimDerivedData(quota, declaring, measured) }
                .onSuccess { trim ->
                    derivedDataTrim = trim
                    if (trim.released.isNotEmpty() || trim.failures.isNotEmpty()) derivedDataTrimReport = trim
                    val gone = trim.released.map { it.folder.path }.toSet()
                    derivedDataListing = MachineLoad.Loaded(listing.copy(folders = listing.folders.filterNot { it.folder.path in gone }))
                    derivedDataSizes = derivedDataSizes - gone
                    derivedDataPackageSizes = derivedDataPackageSizes - gone
                    clearError(DERIVED_DATA_KEY)
                }
                .onFailure { fail(DERIVED_DATA_KEY, it) { reloadDerivedData() } }
        } finally {
            isTrimmingDerivedData = false
        }
    }

    private fun reloadDerivedData() {
        scope.launch { loadDerivedData() }
    }

    fun dismissDerivedDataTrimReport() {
        derivedDataTrimReport = null
    }

    private fun stopDerivedDataMeasuring() {
        sizeJob?.cancel()
        sizeJob = null
        if (derivedDataListing is MachineLoad.Loading) return
        derivedDataListing = MachineLoad.Loading
        derivedDataSizes = emptyMap()
        derivedDataPackageSizes = emptyMap()
        compilationCacheSize = null
        compilationCacheMeasured = false
        packageCacheSizes = null
    }

    fun stopGradleDaemon(pid: Long) = stopGradleDaemons(listOf(pid))

    fun stopIdleGradleDaemons() = stopGradleDaemons(null)

    private fun stopGradleDaemons(pids: List<Long>?) {
        if (isStoppingDaemons) return
        isStoppingDaemons = true
        scope.launch {
            try {
                io { useCases.stopDaemons(pids) }
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

    fun stopMavenDaemon(pid: Long) = stopMavenDaemons(listOf(pid))

    fun stopIdleMavenDaemons() = stopMavenDaemons(null)

    private fun stopMavenDaemons(pids: List<Long>?) {
        if (isStoppingMavenDaemons) return
        isStoppingMavenDaemons = true
        scope.launch {
            try {
                io { useCases.stopMavenDaemons(pids) }
                    .onSuccess { mavenDaemonStopReport = it; clearError(MAVEN_DAEMONS_KEY) }
                    .onFailure { fail(MAVEN_DAEMONS_KEY, it) { stopMavenDaemons(pids) } }
                loadMavenDaemons()
            } finally {
                isStoppingMavenDaemons = false
            }
        }
    }

    fun dismissMavenDaemonStopReport() {
        mavenDaemonStopReport = null
    }

    fun deleteOrphanedDerivedData() {
        if (isDeletingDerivedData) return
        isDeletingDerivedData = true
        scope.launch {
            try {
                io { useCases.deleteOrphanedDerivedData() }
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
                io { useCases.deleteSimulators(udids) }
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
            io { useCases.startEmulator(config, worktreePath) }
                .onSuccess { recordReleases(MachineResource.ANDROID_EMULATORS, ResourceKind.EMULATOR, worktreePath, it.acquisition) }
                .also { loadEmulators() }
        }

    fun stopEmulator(projectId: String, worktreePath: String) =
        runDeviceAction(projectId, worktreePath) { config ->
            io { useCases.stopEmulator(config, worktreePath) }.also { loadEmulators() }
        }

    fun startSimulator(projectId: String, worktreePath: String) =
        runDeviceAction(projectId, worktreePath) { config ->
            io { useCases.startSimulator(config, worktreePath) }
                .onSuccess { recordReleases(MachineResource.IOS_SIMULATORS, ResourceKind.IOS_SIMULATOR, worktreePath, it.acquisition) }
                .also { loadSimulators() }
        }

    fun stopSimulator(projectId: String, worktreePath: String) =
        runDeviceAction(projectId, worktreePath) { config ->
            io { useCases.stopSimulator(config, worktreePath) }.also { loadSimulators() }
        }

    fun stopUnownedEmulator(serial: String) {
        val declaring = projects().firstOrNull { MachineResource.ANDROID_EMULATORS in it.declares } ?: return
        if (serial in startingDevices) return
        startingDevices = startingDevices + serial
        scope.launch {
            try {
                val key = "unowned:$serial"
                io { useCases.stopEmulatorSerials(declaring.project.config, listOf(serial)) }
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
        private const val MAVEN_DAEMONS_KEY = "mavenDaemons"
        private const val DERIVED_DATA_KEY = "derivedData"
        private const val SIMULATOR_ORPHANS_KEY = "simulatorOrphans"

        fun deviceKey(projectId: String, worktreePath: String): String = "$projectId:$worktreePath"
    }
}

fun poolKindOf(resource: MachineResource): ResourceKind? = when (resource) {
    MachineResource.GRADLE_DAEMONS -> ResourceKind.GRADLE_DAEMON
    MachineResource.ANDROID_EMULATORS -> ResourceKind.EMULATOR
    MachineResource.IOS_SIMULATORS -> ResourceKind.IOS_SIMULATOR
    MachineResource.XCODE_DERIVED_DATA -> null
    MachineResource.MAVEN_DAEMONS -> null
    MachineResource.COMPOSE_PROJECTS -> null
    MachineResource.DESKTOP_TURN -> null
}

private fun machineResourceOf(kind: ResourceKind): MachineResource = when (kind) {
    ResourceKind.GRADLE_DAEMON -> MachineResource.GRADLE_DAEMONS
    ResourceKind.EMULATOR -> MachineResource.ANDROID_EMULATORS
    ResourceKind.IOS_SIMULATOR -> MachineResource.IOS_SIMULATORS
}

private fun <T, R> MachineLoad<T>.map(transform: (T) -> R): MachineLoad<R> = when (this) {
    MachineLoad.Loading -> MachineLoad.Loading
    MachineLoad.Unavailable -> MachineLoad.Unavailable
    is MachineLoad.Loaded -> MachineLoad.Loaded(transform(value))
}
