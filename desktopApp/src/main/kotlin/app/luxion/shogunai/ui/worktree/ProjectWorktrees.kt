package app.luxion.shogunai.ui.worktree

import app.luxion.shogunai.domain.model.AgentLaunchConfig
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.luxion.shogunai.WorktreeUseCases
import app.luxion.shogunai.domain.model.BranchOption
import app.luxion.shogunai.domain.model.BranchType
import app.luxion.shogunai.domain.model.DaemonStopReport
import app.luxion.shogunai.domain.model.DerivedDataFolder
import app.luxion.shogunai.domain.model.EmulatorState
import app.luxion.shogunai.domain.model.GradleDaemon
import app.luxion.shogunai.domain.model.GradleProvider
import app.luxion.shogunai.domain.model.HerdrOpening
import app.luxion.shogunai.domain.model.MainBranch
import app.luxion.shogunai.domain.model.ProvisionDrift
import app.luxion.shogunai.domain.model.WorktreeEnvironment
import app.luxion.shogunai.domain.model.RunningAgent
import app.luxion.shogunai.domain.model.TerminalId
import app.luxion.shogunai.ui.i18n.Strings
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.SimulatorDevice
import app.luxion.shogunai.domain.model.WorktreeEmulator
import app.luxion.shogunai.domain.model.WorktreeSimulator
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.model.WorktreeStatus
import app.luxion.shogunai.domain.model.withoutLocalTwins
import app.luxion.shogunai.ui.machine.MachineResource
import app.luxion.shogunai.ui.shell.ToolsPreferences
import app.luxion.shogunai.domain.usecase.BranchDeletion
import app.luxion.shogunai.domain.usecase.CleanupFailure
import app.luxion.shogunai.domain.usecase.CleanupReport
import app.luxion.shogunai.domain.usecase.CleanupSelection
import app.luxion.shogunai.domain.usecase.idleDaemonsOfRemovedWorktrees
import app.luxion.shogunai.domain.usecase.selectCleanup
import app.luxion.shogunai.domain.usecase.waitsForUser
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.time.Instant

class ProjectWorktrees(
    private val useCases: WorktreeUseCases,
    private val scope: CoroutineScope,
    private val listPermits: Semaphore = Semaphore(1),
    private val sizeQueue: SizeQueue = SizeQueue(scope),
    private val clock: () -> Instant = Instant::now,
    private val onRemoved: (String) -> Unit = {},
    private val onUsed: (String) -> Unit = {},
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private var lastFingerprint: List<Pair<String, Long>>? = null

    var lastReadAt: Instant? = null
        private set

    var worktrees by mutableStateOf<List<Worktree>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var hasLoaded by mutableStateOf(false)
        private set
    var listFailure by mutableStateOf<Throwable?>(null)
        private set
    var isCreating by mutableStateOf(false)
        private set
    var isRemoving by mutableStateOf(false)
        private set
    var error by mutableStateOf<Throwable?>(null)
        private set
    var copiedWorktreePath by mutableStateOf<String?>(null)
        private set
    var success by mutableStateOf<WorktreeSuccess?>(null)
        private set

    var actionErrors by mutableStateOf<Map<String, Throwable>>(emptyMap())
        private set
    private var actionRetries by mutableStateOf<Map<String, () -> Unit>>(emptyMap())
    var errorRetry by mutableStateOf<(() -> Unit)?>(null)
        private set

    val orderedWorktrees: List<Worktree>
        get() = orderMainFirst(worktrees)

    val loadState: ProjectLoadState
        get() = listFailure?.let(ProjectLoadState::Failed)
            ?: if (hasLoaded) ProjectLoadState.Loaded else ProjectLoadState.Loading

    var statuses by mutableStateOf<Map<String, WorktreeStatusState>>(emptyMap())
        private set
    var mainBranch by mutableStateOf<MainBranch?>(null)
        private set
    private val statusReadPermits = Semaphore(MAX_PARALLEL_STATUS_READS)

    var agents by mutableStateOf<Map<String, List<RunningAgent>>?>(null)
        private set
    var sizes by mutableStateOf<Map<String, Long>>(emptyMap())
        private set
    var ports by mutableStateOf<Map<String, Map<String, Int>>>(emptyMap())
        private set
    var environments by mutableStateOf<Map<String, WorktreeEnvironment>>(emptyMap())
        private set
    var provisioning by mutableStateOf<Map<String, ProvisioningState>>(emptyMap())
        private set
    var provisioningInProgress by mutableStateOf<Set<String>>(emptySet())
        private set
    var herdrWorktreesRoot by mutableStateOf<String?>(null)
        private set
    var openingInHerdr by mutableStateOf<Set<String>>(emptySet())
        private set
    var openInHerdrPaths by mutableStateOf<Set<String>>(emptySet())
        private set
    var herdrCloseFailures by mutableStateOf<List<CleanupFailure>>(emptyList())
        private set
    var terminalChoiceFor by mutableStateOf<Worktree?>(null)
        private set
    val totalSize: Long
        get() = sizes.values.sum()

    private val cleanupSelection: CleanupSelection
        get() = selectCleanup(worktrees, loadedStatuses(), agents)
    val cleanupCandidates: List<Worktree>
        get() = cleanupSelection.candidates
    val cleanupKeptForRunningAgent: Map<Worktree, List<RunningAgent>>
        get() = cleanupSelection.keptForRunningAgent
    var isCleaningUp by mutableStateOf(false)
        private set
    var cleanupIssues by mutableStateOf<CleanupReport?>(null)
        private set

    var isSimulatorRepository by mutableStateOf(false)
        private set
    var simulators by mutableStateOf<Map<String, WorktreeSimulator>>(emptyMap())
        private set
    var startingSimulators by mutableStateOf<Set<String>>(emptySet())
        private set
    var stoppingSimulators by mutableStateOf<Set<String>>(emptySet())
        private set
    var orphanedSimulators by mutableStateOf<List<SimulatorDevice>>(emptyList())
        private set

    val declaredResources: Set<MachineResource>
        get() = buildSet {
            if (isGradleRepository) add(MachineResource.GRADLE_DAEMONS)
            if (isEmulatorRepository) add(MachineResource.ANDROID_EMULATORS)
            if (isSimulatorRepository) add(MachineResource.IOS_SIMULATORS)
            if (isXcodeRepository) add(MachineResource.XCODE_DERIVED_DATA)
        }

    val canCleanUp: Boolean
        get() = cleanupCandidates.isNotEmpty() ||
            (isSimulatorRepository && orphanedSimulators.isNotEmpty()) ||
            (isXcodeRepository && derivedDataOrphans.isNotEmpty())

    var isEmulatorRepository by mutableStateOf(false)
        private set
    var emulators by mutableStateOf<Map<String, WorktreeEmulator>>(emptyMap())
        private set
    var startingEmulators by mutableStateOf<Set<String>>(emptySet())
        private set
    var stoppingEmulators by mutableStateOf<Set<String>>(emptySet())
        private set

    var isGradleRepository by mutableStateOf(false)
        private set
    var isGradleBuildCacheEnabled by mutableStateOf(true)
        private set
    var gradleSettings by mutableStateOf<GradleProvider?>(null)
        private set

    var isXcodeRepository by mutableStateOf(false)
        private set
    var hasCompilationCache by mutableStateOf(false)
        private set
    var derivedDataOrphans by mutableStateOf<List<DerivedDataFolder>>(emptyList())
        private set

    var branches by mutableStateOf<List<BranchOption>>(emptyList())
        private set
    var defaultStartRef by mutableStateOf<String?>(null)
        private set
    var hasOrigin by mutableStateOf(false)
        private set
    var fetchError by mutableStateOf<Throwable?>(null)
        private set
    var isLoadingBranches by mutableStateOf(false)
        private set
    var branchesError by mutableStateOf<Throwable?>(null)
        private set
    var removeError by mutableStateOf<Throwable?>(null)
        private set
    var keptBranchNotice by mutableStateOf<KeptBranchNotice?>(null)
        private set
    var isConfirmingForcedBranchDeletion by mutableStateOf(false)
        private set
    var isDeletingBranch by mutableStateOf(false)
        private set

    var createError by mutableStateOf<Throwable?>(null)
        private set
    var createdWithErrorPath by mutableStateOf<String?>(null)
        private set
    var lastCreatedWorktree by mutableStateOf<Worktree?>(null)
        private set

    private var listGeneration = 0

    private var pendingRemoval by mutableStateOf<PendingRemoval?>(null)
    val worktreePendingRemoval: Worktree?
        get() = pendingRemoval?.worktree
    val pendingRemovalDeleteBranch: Boolean
        get() = pendingRemoval?.deleteBranch ?: false
    val pendingRemovalRequiresForce: Boolean
        get() = pendingRemoval?.requiresForce ?: false
    val pendingRemovalOpenInHerdr: Boolean
        get() = pendingRemoval?.worktree?.path in openInHerdrPaths

    fun refresh(remeasureSizes: Boolean = false) {
        if (isCreating) return
        val generation = ++listGeneration
        isLoading = true
        lastReadAt = clock()
        scope.launch {
            val result = listPermits.withPermit {
                lastFingerprint = useCases.readFingerprint().getOrNull() ?: lastFingerprint
                useCases.invalidateEnvironments()
                useCases.list()
            }
            applyListResult(generation, result, remeasureSizes)
        }
    }

    fun loadDetail(worktree: Worktree) {
        if (worktree.isPrunable) return
        scope.launch {
            useCases.readEnvironment(worktree).onSuccess { environments = environments + (worktree.path to it) }
        }
        if (worktree.isMain) return
        provisioning = provisioning + (worktree.path to ProvisioningState.Checking)
        scope.launch { checkProvisioning(worktree) }
    }

    private suspend fun checkProvisioning(worktree: Worktree) {
        val state = useCases.provisioning.check(worktree.path).fold(
            onSuccess = { ProvisioningState.Checked(it) },
            onFailure = { ProvisioningState.Unavailable },
        )
        provisioning = provisioning + (worktree.path to state)
    }

    fun provision(worktree: Worktree) {
        if (worktree.path in provisioningInProgress) return
        provisioningInProgress = provisioningInProgress + worktree.path
        scope.launch {
            try {
                useCases.provisioning.provision(worktree.path)
                    .onSuccess { clearActionError(worktree.path) }
                    .onFailure { recordActionError(worktree.path, it) { provision(worktree) } }
                checkProvisioning(worktree)
                useCases.invalidateEnvironments()
                useCases.readEnvironment(worktree).onSuccess { environments = environments + (worktree.path to it) }
                loadPorts(listGeneration, worktrees)
            } finally {
                provisioningInProgress = provisioningInProgress - worktree.path
            }
        }
    }

    fun refreshStatuses() {
        lastReadAt = clock()
        readStatuses(listGeneration, worktrees, mainBranch)
    }

    val isBusy: Boolean
        get() = isLoading || isCreating || isRemoving || isCleaningUp

    suspend fun checkFingerprint(): FingerprintCheck {
        val fingerprint = useCases.readFingerprint().getOrNull() ?: return FingerprintCheck.UNREADABLE
        val previous = lastFingerprint
        lastFingerprint = fingerprint
        if (previous != null && fingerprint != previous) {
            refresh()
            return FingerprintCheck.RELOADED
        }
        return FingerprintCheck.UNCHANGED
    }

    fun applyAgents(detected: Map<String, List<RunningAgent>>?) {
        val paths = worktrees.map { it.path }.toSet()
        agents = detected?.filterKeys { it in paths }
    }

    suspend fun refreshDevices() {
        if (isEmulatorRepository) loadEmulators(listGeneration, worktrees)
        if (isSimulatorRepository) loadSimulators(listGeneration, worktrees)
    }

    fun repairAfterMove(previousWorktreesRoot: String) {
        val generation = ++listGeneration
        isLoading = true
        lastReadAt = clock()
        scope.launch {
            val repair = useCases.repair(previousWorktreesRoot)
            applyListResult(generation, listPermits.withPermit { useCases.list() })
            repair
                .onSuccess { success = WorktreeSuccess.RepairedAfterMove }
                .onFailure { fail(it) }
        }
    }

    private fun applyListResult(generation: Int, result: Result<List<Worktree>>, remeasureSizes: Boolean = false) {
        if (generation != listGeneration) return
        result
            .onSuccess {
                worktrees = it
                useCases.agents.prune(it.map { worktree -> worktree.path }.toSet())
                listFailure = null
                loadStatuses(generation, it, remeasureSizes)
            }
            .onFailure { listFailure = it }
        hasLoaded = true
        isLoading = false
    }

    private fun loadStatuses(generation: Int, listed: List<Worktree>, remeasureSizes: Boolean) {
        statuses = listed.associate { it.path to (statuses[it.path] ?: WorktreeStatusState.Loading) }
        readStatuses(generation, listed, knownMainBranch = null)
        loadOverview(generation, listed, remeasureSizes)
    }

    private fun readStatuses(generation: Int, listed: List<Worktree>, knownMainBranch: MainBranch?) {
        scope.launch {
            val resolved = knownMainBranch ?: useCases.resolveMainBranch().getOrNull()
            if (generation != listGeneration) return@launch
            mainBranch = resolved
            listed.forEach { worktree -> launch { readStatus(generation, worktree, resolved) } }
        }
    }

    private fun loadOverview(generation: Int, listed: List<Worktree>, remeasureSizes: Boolean) {
        val listedPaths = listed.map { it.path }.toSet()
        sizes = sizes.filterKeys { it in listedPaths }
        scope.launch { herdrWorktreesRoot = useCases.herdr.worktreesRoot() }
        scope.launch {
            val detected = useCases.detectAgents(listed).getOrNull()
            if (generation == listGeneration) agents = detected
        }
        loadGradleSection()
        loadPorts(generation, listed)
        loadEmulatorSection(generation, listed)
        loadSimulatorSection(generation, listed)
        loadXcodeSection(generation, listed)
        sizeQueue.submit(
            owner = this,
            worktrees = if (remeasureSizes) listed else listed.filterNot { it.path in sizes },
            measure = { worktree -> useCases.measureSize(worktree, listed).getOrNull() },
            deliver = { path, size -> if (worktrees.any { it.path == path }) sizes = sizes + (path to size) },
            replacePending = remeasureSizes,
        )
    }

    fun dispose() {
        sizeQueue.cancel(this)
    }

    private fun loadSimulatorSection(generation: Int, listed: List<Worktree>) {
        scope.launch {
            isSimulatorRepository = useCases.simulator.declared()
            if (!isSimulatorRepository) {
                simulators = emptyMap()
                orphanedSimulators = emptyList()
                return@launch
            }
            loadSimulators(generation, listed)
            val orphans = useCases.simulator.orphans().getOrNull() ?: return@launch
            if (generation == listGeneration) orphanedSimulators = orphans
        }
    }

    private suspend fun loadSimulators(generation: Int, listed: List<Worktree>) {
        val loaded = useCases.simulator.readStates(listed).getOrNull() ?: return
        if (generation == listGeneration) simulators = loaded
    }

    private suspend fun deleteSimulatorsAfterCleanup(ofRemoved: List<String>, knownOrphans: List<SimulatorDevice>): Int {
        val orphans = useCases.simulator.orphans().getOrDefault(knownOrphans)
        val udids = (ofRemoved + orphans.map { it.udid }).distinct()
        if (udids.isEmpty()) return 0
        return useCases.simulator.delete(udids).getOrNull()?.size ?: 0
    }

    fun startSimulator(worktree: Worktree) {
        if (worktree.path in startingSimulators) return
        startingSimulators = startingSimulators + worktree.path
        scope.launch {
            try {
                useCases.simulator.start(worktree.path)
                    .onSuccess { clearActionError(worktree.path) }
                    .onFailure { recordActionError(worktree.path, it) { startSimulator(worktree) } }
                loadSimulators(listGeneration, worktrees)
            } finally {
                startingSimulators = startingSimulators - worktree.path
            }
        }
    }

    fun stopSimulator(worktree: Worktree) {
        if (worktree.path in stoppingSimulators) return
        stoppingSimulators = stoppingSimulators + worktree.path
        scope.launch {
            try {
                useCases.simulator.stop(worktree.path)
                    .onSuccess { clearActionError(worktree.path) }
                    .onFailure { recordActionError(worktree.path, it) { stopSimulator(worktree) } }
                loadSimulators(listGeneration, worktrees)
            } finally {
                stoppingSimulators = stoppingSimulators - worktree.path
            }
        }
    }

    private fun loadEmulatorSection(generation: Int, listed: List<Worktree>) {
        scope.launch {
            isEmulatorRepository = useCases.emulator.declared()
            if (!isEmulatorRepository) {
                emulators = emptyMap()
                return@launch
            }
            loadEmulators(generation, listed)
        }
    }

    private suspend fun loadEmulators(generation: Int, listed: List<Worktree>) {
        val loaded = useCases.emulator.readStates(listed).getOrNull() ?: return
        if (generation == listGeneration) emulators = loaded
    }

    fun startEmulator(worktree: Worktree) {
        if (worktree.path in startingEmulators) return
        startingEmulators = startingEmulators + worktree.path
        scope.launch {
            try {
                useCases.emulator.start(worktree.path)
                    .onSuccess { clearActionError(worktree.path) }
                    .onFailure { recordActionError(worktree.path, it) { startEmulator(worktree) } }
                loadEmulators(listGeneration, worktrees)
                loadPorts(listGeneration, worktrees)
            } finally {
                startingEmulators = startingEmulators - worktree.path
            }
        }
    }

    fun stopEmulator(worktree: Worktree) {
        if (worktree.path in stoppingEmulators) return
        stoppingEmulators = stoppingEmulators + worktree.path
        scope.launch {
            try {
                useCases.emulator.stop(worktree.path)
                    .onSuccess { clearActionError(worktree.path) }
                    .onFailure { recordActionError(worktree.path, it) { stopEmulator(worktree) } }
                loadEmulators(listGeneration, worktrees)
            } finally {
                stoppingEmulators = stoppingEmulators - worktree.path
            }
        }
    }

    private fun loadPorts(generation: Int, listed: List<Worktree>) {
        scope.launch {
            val loaded = listed.filterNot { it.isPrunable }.mapNotNull { worktree ->
                useCases.readPorts(worktree).getOrNull()?.takeIf { it.isNotEmpty() }?.let { worktree.path to it }
            }.toMap()
            if (generation == listGeneration) ports = loaded
        }
    }

    private fun loadGradleSection() {
        scope.launch {
            val provider = useCases.gradle.provider()
            isGradleRepository = provider != null
            gradleSettings = provider?.takeIf { it.hasSettings }
            if (provider == null) return@launch
            isGradleBuildCacheEnabled = useCases.gradle.isBuildCacheEnabled()
        }
    }

    private fun loadXcodeSection(generation: Int, listed: List<Worktree>) {
        scope.launch {
            val provider = useCases.xcode.provider()?.takeIf { useCases.xcode.supported() }
            isXcodeRepository = provider != null
            hasCompilationCache = provider?.compilationCache == true
            if (provider == null) {
                derivedDataOrphans = emptyList()
                return@launch
            }
            val orphans = useCases.xcode.list(listed, false).getOrNull()?.orphans ?: return@launch
            if (generation == listGeneration) derivedDataOrphans = orphans
        }
    }

    private fun loadStatusOf(worktree: Worktree) {
        val generation = listGeneration
        statuses = statuses + (worktree.path to WorktreeStatusState.Loading)
        scope.launch { readStatus(generation, worktree, mainBranch) }
    }

    private suspend fun readStatus(generation: Int, worktree: Worktree, mainBranch: MainBranch?) {
        val state = statusReadPermits.withPermit { useCases.readStatus(worktree, mainBranch) }
            .fold(onSuccess = { WorktreeStatusState.Loaded(it) }, onFailure = { WorktreeStatusState.Unavailable })
        if (generation == listGeneration) statuses = statuses + (worktree.path to state)
    }

    fun isBranchAvailable(branch: String?): Boolean =
        branches.withoutLocalTwins().any { it.name == branch && !it.isCheckedOut }

    fun dismissCreateError() {
        createError = null
        fetchError = null
        createdWithErrorPath = null
    }

    fun copyCreatedLaunchCommand() {
        val path = createdWithErrorPath ?: return
        scope.launch { useCases.copyLaunchCommand(path, useCases.agents.agentFor(path)).onFailure { recordActionError(path, it) } }
    }

    fun create(
        taskId: String,
        branchType: BranchType,
        startRef: String? = null,
        fetch: Boolean = false,
        onCreated: (Worktree) -> Unit = {},
    ) = runCreation(fetch, onCreated) {
        val startPoint = startRef?.let { useCases.resolveStartPoint(it) }
        val startFailure = startPoint?.exceptionOrNull()
        if (startFailure != null) Result.failure(startFailure) else useCases.create(taskId, branchType, startPoint?.getOrNull())
    }

    private fun runCreation(fetch: Boolean, onCreated: (Worktree) -> Unit, creation: suspend () -> Result<Worktree>) {
        if (isCreating) return
        isCreating = true
        createError = null
        fetchError = null
        scope.launch {
            try {
                if (fetch) {
                    val fetched = withContext(ioDispatcher) { useCases.fetchRemote() }
                    fetched.exceptionOrNull()?.let {
                        fetchError = it
                        return@launch
                    }
                    loadBranches()
                }
                withContext(ioDispatcher) { creation() }
                    .onSuccess { worktree ->
                        worktrees = worktrees.filterNot { it.path == worktree.path } + worktree
                        lastCreatedWorktree = worktree
                        success = WorktreeSuccess.Created(worktree.path, worktree.skippedPostCreateCommands.isNotEmpty())
                        loadStatusOf(worktree)
                        onUsed(worktree.path)
                        onCreated(worktree)
                    }
                    .onFailure { failure ->
                        createError = failure
                        if (failure is WorktreeError.PostCreateCommandFailed) {
                            createdWithErrorPath = failure.worktreePath
                            isCreating = false
                            refresh()
                        }
                    }
            } finally {
                isCreating = false
            }
        }
    }

    fun loadBranches() {
        scope.launch {
            isLoadingBranches = true
            useCases.listBranches()
                .onSuccess { branches = it; branchesError = null }
                .onFailure { branchesError = it }
            defaultStartRef = useCases.resolveMainBranch().getOrNull()?.ref
            hasOrigin = useCases.fetchRemote.hasOrigin()
            isLoadingBranches = false
        }
    }

    fun createFromBranch(branch: String, fetch: Boolean = false, onCreated: (Worktree) -> Unit = {}) =
        runCreation(fetch, onCreated) { useCases.createFromBranch(branch) }

    fun remove(worktree: Worktree, branchToDelete: String?, force: Boolean = false) {
        removeError = null
        scope.launch {
            isRemoving = true
            try {
                val resources = useCases.releaseResources.plan(worktree, worktrees)
                useCases.remove(worktree.path, branchToDelete, force)
                    .onSuccess { removal ->
                        launch { useCases.releaseResources.release(resources) }
                        removal.herdrFailure?.let { herdrCloseFailures = listOf(CleanupFailure(worktree.path, it)) }
                        worktrees = worktrees.filterNot { it.path == worktree.path }
                        statuses = statuses - worktree.path
                        sizes = sizes - worktree.path
                        dismissError()
                        pendingRemoval = null
                        keptBranchNotice = removal.branch?.let { keptBranchNoticeOf(worktree.branch, it) }
                        onRemoved(worktree.path)
                    }
                    .onFailure { failure ->
                        if (!force && failure.suggestsForceRetry()) {
                            pendingRemoval = PendingRemoval(worktree, deleteBranch = branchToDelete != null, requiresForce = true)
                        } else {
                            removeError = failure
                        }
                    }
            } finally {
                isRemoving = false
            }
        }
    }

    private fun keptBranchNoticeOf(branch: String?, deletion: BranchDeletion): KeptBranchNotice? = when {
        branch == null || deletion == BranchDeletion.Deleted -> null
        else -> KeptBranchNotice(branch, (deletion as? BranchDeletion.KeptFailed)?.reason)
    }

    fun dismissKeptBranchNotice() {
        if (isDeletingBranch) return
        keptBranchNotice = null
        isConfirmingForcedBranchDeletion = false
    }

    fun requestForcedBranchDeletion() {
        if (keptBranchNotice != null) isConfirmingForcedBranchDeletion = true
    }

    fun cancelForcedBranchDeletion() {
        if (!isDeletingBranch) isConfirmingForcedBranchDeletion = false
    }

    fun confirmForcedBranchDeletion() {
        val notice = keptBranchNotice ?: return
        if (isDeletingBranch) return
        scope.launch {
            isDeletingBranch = true
            try {
                useCases.deleteBranch(notice.branch)
                    .onSuccess { deletion ->
                        keptBranchNotice = keptBranchNoticeOf(notice.branch, deletion)
                        isConfirmingForcedBranchDeletion = false
                        if (deletion == BranchDeletion.Deleted) success = WorktreeSuccess.BranchDeleted(notice.branch)
                    }
                    .onFailure { failure ->
                        isConfirmingForcedBranchDeletion = false
                        fail(failure)
                    }
            } finally {
                isDeletingBranch = false
            }
        }
    }

    fun requestRemoval(worktree: Worktree) {
        removeError = null
        pendingRemoval = PendingRemoval(worktree, deleteBranch = false)
        lookUpOpenInHerdr(listOf(worktree.path))
    }

    fun lookUpCleanupCandidatesInHerdr() {
        lookUpOpenInHerdr(cleanupCandidates.map { it.path })
    }

    private fun lookUpOpenInHerdr(paths: List<String>) {
        openInHerdrPaths = emptySet()
        scope.launch {
            openInHerdrPaths = useCases.herdr.openWorkspaces(paths).keys
        }
    }

    fun setPendingRemovalDeleteBranch(deleteBranch: Boolean) {
        pendingRemoval = pendingRemoval?.copy(deleteBranch = deleteBranch)
    }

    fun copyLaunchCommand(worktree: Worktree, onCopied: () -> Unit = {}, picked: AgentLaunchConfig? = null) {
        onUsed(worktree.path)
        val agent = resolveAgent(worktree, picked)
        scope.launch {
            useCases.copyLaunchCommand(worktree.path, agent)
                .onSuccess {
                    clearActionError(worktree.path)
                    onCopied()
                    copiedWorktreePath = worktree.path
                    delay(COPIED_FEEDBACK_DURATION_MS)
                    copiedWorktreePath = null
                }
                .onFailure { recordActionError(worktree.path, it) }
        }
    }

    fun openInHerdr(worktree: Worktree, picked: AgentLaunchConfig? = null) {
        if (worktree.path in openingInHerdr) return
        onUsed(worktree.path)
        val agent = resolveAgent(worktree, picked)
        openingInHerdr = openingInHerdr + worktree.path
        val agentPane = agents.orEmpty()[worktree.path].orEmpty().agentPaneToFocus()
        scope.launch {
            try {
                val result = if (agentPane != null) {
                    useCases.herdr.focusAgent(agentPane).map { HerdrOpening.FOCUSED }
                } else {
                    useCases.herdr.open(worktree, agent)
                }
                result
                    .onSuccess { opening ->
                        clearActionError(worktree.path)
                        success = WorktreeSuccess.OpenedInHerdr(
                            path = worktree.path,
                            focused = opening == HerdrOpening.FOCUSED,
                        )
                    }
                    .onFailure { recordActionError(worktree.path, it) }
            } finally {
                openingInHerdr = openingInHerdr - worktree.path
            }
        }
    }

    val tools: ToolsPreferences get() = useCases.tools.preferences

    val agentOptions: List<AgentLaunchConfig> get() = useCases.agents.options
    val hasAgentChoice: Boolean get() = useCases.agents.hasChoice

    fun agentFor(worktree: Worktree): AgentLaunchConfig = useCases.agents.agentFor(worktree.path)

    fun setAgent(worktree: Worktree, agent: AgentLaunchConfig) {
        useCases.agents.choose(worktree.path, agent)
    }

    fun launchLabel(base: String, worktree: Worktree, strings: Strings): String =
        if (hasAgentChoice) strings.launchWithAgent(base, agentFor(worktree).displayName) else base

    fun launchChoices(worktree: Worktree, strings: Strings): List<LaunchChoice> {
        if (!hasAgentChoice) return emptyList()
        val current = agentFor(worktree)
        val kinds = if (worktree.isPrunable) {
            listOf(LaunchKind.COPY)
        } else {
            listOfNotNull(LaunchKind.COPY, LaunchKind.TERMINAL.takeIf { tools.terminalShown != null }, LaunchKind.HERDR)
        }
        return agentOptions.filter { it.displayName != current.displayName }.flatMap { agent ->
            kinds.map { kind ->
                val base = when (kind) {
                    LaunchKind.COPY -> strings.copyLaunchCommand
                    LaunchKind.TERMINAL -> strings.openInTool(tools.terminalInUse?.displayName ?: strings.terminalToolLabel.lowercase())
                    LaunchKind.HERDR -> strings.openInHerdr
                }
                LaunchChoice(kind, agent, strings.launchWithAgent(base, agent.displayName))
            }
        }
    }

    fun launch(worktree: Worktree, kind: LaunchKind, agent: AgentLaunchConfig) {
        when (kind) {
            LaunchKind.COPY -> copyLaunchCommand(worktree, picked = agent)
            LaunchKind.TERMINAL -> openInTerminal(worktree, agent)
            LaunchKind.HERDR -> openInHerdr(worktree, agent)
        }
    }

    private fun resolveAgent(worktree: Worktree, picked: AgentLaunchConfig?): AgentLaunchConfig {
        if (picked == null) return agentFor(worktree)
        setAgent(worktree, picked)
        return picked
    }

    fun openActions(worktree: Worktree, strings: Strings): List<OpenAction> {
        if (worktree.isPrunable) return emptyList()
        return listOfNotNull(
            tools.terminalShown?.let {
                val name = tools.terminalInUse?.displayName ?: strings.terminalToolLabel.lowercase()
                OpenAction(OpenKind.TERMINAL, launchLabel(strings.openInTool(name), worktree, strings)) { openInTerminal(worktree) }
            },
            tools.editorFor(useCases.tools.editorOverride)?.let {
                OpenAction(OpenKind.EDITOR, strings.openInTool(it.displayName)) { openInEditor(worktree) }
            },
            OpenAction(OpenKind.FOLDER, strings.openFolder) { openFolder(worktree) },
        )
    }

    fun open(worktree: Worktree, kind: OpenKind) {
        when (kind) {
            OpenKind.TERMINAL -> openInTerminal(worktree)
            OpenKind.EDITOR -> openInEditor(worktree)
            OpenKind.FOLDER -> openFolder(worktree)
        }
    }

    fun openInTerminal(worktree: Worktree, picked: AgentLaunchConfig? = null) {
        val terminal = tools.terminalInUse
        if (terminal == null) {
            if (tools.availableTerminals.isNotEmpty()) terminalChoiceFor = worktree
            return
        }
        onUsed(worktree.path)
        val agent = resolveAgent(worktree, picked)
        launchTool(worktree) { useCases.tools.openInTerminal(worktree.path, terminal, agent) }
    }

    fun chooseTerminal(terminal: TerminalId) {
        val worktree = terminalChoiceFor
        tools.chooseTerminal(terminal)
        terminalChoiceFor = null
        if (worktree != null) openInTerminal(worktree)
    }

    fun dismissTerminalChoice() {
        terminalChoiceFor = null
    }

    fun openInEditor(worktree: Worktree) {
        val editor = tools.editorFor(useCases.tools.editorOverride) ?: return
        onUsed(worktree.path)
        launchTool(worktree) { useCases.tools.openInEditor(worktree.path, editor) }
    }

    fun openFolder(worktree: Worktree) {
        launchTool(worktree) { useCases.tools.openFolder(worktree.path) }
    }

    private fun launchTool(worktree: Worktree, launch: suspend () -> Result<Unit>) {
        scope.launch {
            launch()
                .onSuccess { clearActionError(worktree.path) }
                .onFailure { recordActionError(worktree.path, it) }
        }
    }

    private fun List<RunningAgent>.agentPaneToFocus(): String? =
        (firstOrNull { it.waitsForUser && it.herdrPaneId != null } ?: firstOrNull { it.herdrPaneId != null })?.herdrPaneId


    fun copyLastCreatedLaunchCommand() {
        lastCreatedWorktree?.let(::copyLaunchCommand)
    }

    fun confirmPendingRemoval() {
        val pending = pendingRemoval ?: return
        val branchToDelete = pending.worktree.branch.takeIf { pending.deleteBranch }
        remove(pending.worktree, branchToDelete, force = pending.requiresForce)
    }

    fun dismissPendingRemoval() {
        if (isRemoving) return
        pendingRemoval = null
        removeError = null
    }

    suspend fun cleanUp(options: CleanupOptions): WorktreeSuccess.CleanedUp? {
        useCases.detectAgents(worktrees).getOrNull()?.let(::applyAgents)
        val candidates = cleanupCandidates
        val listed = worktrees
        val stopDaemons = isGradleRepository && options.stopGradleDaemons
        val deleteSimulators = isSimulatorRepository && options.deleteSimulators
        val deleteDerivedData = isXcodeRepository && options.deleteDerivedData
        val simulatorsToDelete = if (deleteSimulators) {
            simulators.filterKeys { path -> candidates.any { it.path == path } }.mapValues { it.value.udid }
        } else {
            emptyMap()
        }
        val knownOrphans = orphanedSimulators
        val emulatorsBefore = if (isEmulatorRepository && options.stopEmulators) {
            emulators.filter { (path, emulator) -> emulator.state != EmulatorState.STOPPED && candidates.any { it.path == path } }
        } else {
            emptyMap()
        }
        isCleaningUp = true
        var cleanedUp: WorktreeSuccess.CleanedUp? = null
        try {
            val daemonsBefore = if (stopDaemons) useCases.gradle.listDaemons(listed).getOrNull().orEmpty() else emptyList()
            useCases.cleanUp(candidates, mainBranch, options.deleteBranches)
                .onSuccess { report ->
                    dismissError()
                    val daemonReport = stopDaemonsOfRemovedWorktrees(daemonsBefore, listed, report.removed)
                    val removedSerials = report.removed.mapNotNull { emulatorsBefore[it]?.serial }
                    val stoppedEmulators = if (removedSerials.isEmpty()) {
                        0
                    } else {
                        useCases.emulator.stopSerials(removedSerials).getOrNull()?.size ?: 0
                    }
                    val deletedSimulators = if (deleteSimulators) {
                        deleteSimulatorsAfterCleanup(report.removed.mapNotNull { simulatorsToDelete[it] }, knownOrphans)
                    } else {
                        0
                    }
                    val deletedDerivedData = if (deleteDerivedData) {
                        useCases.xcode.deleteOrphans().getOrNull()
                    } else {
                        null
                    }
                    cleanedUp = WorktreeSuccess.CleanedUp(
                        removedCount = report.removed.size,
                        stoppedDaemons = daemonReport?.stopped?.size ?: 0,
                        keptDaemons = daemonReport?.kept?.size ?: 0,
                        stoppedEmulators = stoppedEmulators,
                        deletedSimulators = deletedSimulators,
                        deletedDerivedDataFolders = deletedDerivedData?.deleted?.size ?: 0,
                        derivedDataBytesFreed = deletedDerivedData?.bytesFreed ?: 0,
                    )
                    herdrCloseFailures = report.herdrFailures
                    cleanupIssues = report.takeIf { it.failures.isNotEmpty() || it.keptBranches.isNotEmpty() }
                }
                .onFailure { fail(it) }
        } finally {
            isCleaningUp = false
        }
        refresh()
        return cleanedUp
    }

    private suspend fun stopDaemonsOfRemovedWorktrees(
        daemons: List<GradleDaemon>,
        listed: List<Worktree>,
        removed: List<String>,
    ): DaemonStopReport? {
        val pids = idleDaemonsOfRemovedWorktrees(daemons, listed, removed)
        if (pids.isEmpty()) return null
        return useCases.gradle.stopDaemons(pids).getOrNull()
    }

    fun dismissCleanupIssues() {
        cleanupIssues = null
    }

    fun dismissHerdrCloseFailures() {
        herdrCloseFailures = emptyList()
    }

    private fun loadedStatuses(): Map<String, WorktreeStatus> =
        statuses.mapNotNull { (path, state) -> (state as? WorktreeStatusState.Loaded)?.let { path to it.status } }.toMap()

    fun dismissError() {
        error = null
        errorRetry = null
    }

    private fun fail(failure: Throwable, retry: (() -> Unit)? = null) {
        error = failure
        errorRetry = retry
    }

    fun retryError() {
        val retry = errorRetry ?: return
        dismissError()
        retry()
    }

    fun dismissSuccess() {
        success = null
    }

    fun dismissActionError(path: String) = clearActionError(path)

    fun actionRetry(path: String): (() -> Unit)? = actionRetries[path]?.let { retry ->
        {
            clearActionError(path)
            retry()
        }
    }

    private fun clearActionError(path: String) {
        actionErrors = actionErrors - path
        actionRetries = actionRetries - path
    }

    private fun recordActionError(path: String, failure: Throwable, retry: (() -> Unit)? = null) {
        actionErrors = actionErrors + (path to failure)
        actionRetries = if (retry != null) actionRetries + (path to retry) else actionRetries - path
    }

    data class KeptBranchNotice(val branch: String, val failureReason: String? = null)

    private data class PendingRemoval(
        val worktree: Worktree,
        val deleteBranch: Boolean,
        val requiresForce: Boolean = false,
    )
}

private fun Throwable.suggestsForceRetry(): Boolean =
    this is WorktreeError.GitCommandFailed && errorOutput.contains("--force", ignoreCase = true)

private const val COPIED_FEEDBACK_DURATION_MS = 2000L
private const val MAX_PARALLEL_STATUS_READS = 4

enum class CreateMode { NEW_BRANCH, EXISTING_BRANCH }

sealed interface ProvisioningState {
    data object Checking : ProvisioningState
    data object Unavailable : ProvisioningState
    data class Checked(val drift: List<ProvisionDrift>) : ProvisioningState
}

enum class FingerprintCheck { RELOADED, UNCHANGED, UNREADABLE }

sealed interface ProjectLoadState {
    data object Loading : ProjectLoadState
    data object Loaded : ProjectLoadState
    data class Failed(val error: Throwable) : ProjectLoadState
}

data class CleanupOptions(
    val deleteBranches: Boolean = false,
    val stopGradleDaemons: Boolean = true,
    val stopEmulators: Boolean = true,
    val deleteSimulators: Boolean = true,
    val deleteDerivedData: Boolean = true,
)

sealed interface WorktreeStatusState {
    data object Loading : WorktreeStatusState
    data class Loaded(val status: WorktreeStatus) : WorktreeStatusState
    data object Unavailable : WorktreeStatusState
}

sealed interface WorktreeSuccess {
    data class Created(val path: String, val commandsSkipped: Boolean = false) : WorktreeSuccess
    data object RepairedAfterMove : WorktreeSuccess
    data class OpenedInHerdr(val path: String, val focused: Boolean) : WorktreeSuccess
    data class BranchDeleted(val branch: String) : WorktreeSuccess
    data class CleanedUp(
        val removedCount: Int,
        val stoppedDaemons: Int = 0,
        val keptDaemons: Int = 0,
        val stoppedEmulators: Int = 0,
        val deletedSimulators: Int = 0,
        val deletedDerivedDataFolders: Int = 0,
        val derivedDataBytesFreed: Long = 0,
    ) : WorktreeSuccess {
        operator fun plus(other: CleanedUp) = CleanedUp(
            removedCount = removedCount + other.removedCount,
            stoppedDaemons = stoppedDaemons + other.stoppedDaemons,
            keptDaemons = keptDaemons + other.keptDaemons,
            stoppedEmulators = stoppedEmulators + other.stoppedEmulators,
            deletedSimulators = deletedSimulators + other.deletedSimulators,
            deletedDerivedDataFolders = deletedDerivedDataFolders + other.deletedDerivedDataFolders,
            derivedDataBytesFreed = derivedDataBytesFreed + other.derivedDataBytesFreed,
        )
    }
}

