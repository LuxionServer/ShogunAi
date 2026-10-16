package app.luxion.shogunai.ui.worktree

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.luxion.shogunai.WorktreeUseCases
import app.luxion.shogunai.domain.model.BranchOption
import app.luxion.shogunai.domain.model.BranchType
import app.luxion.shogunai.domain.model.DaemonStopReport
import app.luxion.shogunai.domain.model.GradleDaemon
import app.luxion.shogunai.domain.model.MainBranch
import app.luxion.shogunai.domain.model.RunningAgent
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.model.WorktreeStatus
import app.luxion.shogunai.domain.usecase.CleanupReport
import app.luxion.shogunai.domain.usecase.idleDaemonsOfRemovedWorktrees
import app.luxion.shogunai.domain.usecase.isValidGitRefSegment
import app.luxion.shogunai.domain.usecase.selectCleanupCandidates
import app.luxion.shogunai.domain.usecase.normalizeTaskId
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class WorktreeViewModel(
    private val useCases: WorktreeUseCases,
    private val autoRefreshInterval: Duration = AUTO_REFRESH_INTERVAL,
) : ViewModel() {
    private var watchJob: Job? = null
    private var lastFingerprint: List<Pair<String, Long>>? = null

    var worktrees by mutableStateOf<List<Worktree>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
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
    val totalSize: Long
        get() = sizes.values.sum()

    val cleanupCandidates: List<Worktree>
        get() = selectCleanupCandidates(worktrees, loadedStatuses())
    var isCleanupDialogOpen by mutableStateOf(false)
        private set
    var cleanupDeleteBranches by mutableStateOf(false)
    var isCleaningUp by mutableStateOf(false)
        private set
    var cleanupIssues by mutableStateOf<CleanupReport?>(null)
        private set
    var cleanupStopGradleDaemons by mutableStateOf(true)

    var isGradleRepository by mutableStateOf(false)
        private set
    var gradleDaemons by mutableStateOf<GradleDaemonsState>(GradleDaemonsState.Loading)
        private set
    var isGradleBuildCacheEnabled by mutableStateOf(true)
        private set
    var isStoppingDaemons by mutableStateOf(false)
        private set
    var daemonStopReport by mutableStateOf<DaemonStopReport?>(null)
        private set

    var localBranches by mutableStateOf<List<BranchOption>>(emptyList())
        private set
    var isLoadingBranches by mutableStateOf(false)
        private set

    var taskId by mutableStateOf("")
        private set
    var branchType by mutableStateOf(BranchType.FEATURE)
    var createMode by mutableStateOf(CreateMode.NEW_BRANCH)
    var selectedBranch by mutableStateOf<String?>(null)

    private var listGeneration = 0

    private var pendingRemoval by mutableStateOf<PendingRemoval?>(null)
    val worktreePendingRemoval: Worktree?
        get() = pendingRemoval?.worktree
    val pendingRemovalDeleteBranch: Boolean
        get() = pendingRemoval?.deleteBranch ?: false
    val pendingRemovalRequiresForce: Boolean
        get() = pendingRemoval?.requiresForce ?: false

    init {
        refresh()
    }

    fun updateTaskId(raw: String) {
        taskId = normalizeTaskId(raw)
    }

    fun refresh() {
        val generation = ++listGeneration
        viewModelScope.launch {
            isLoading = true
            lastFingerprint = useCases.readFingerprint().getOrNull() ?: lastFingerprint
            applyListResult(generation, useCases.list())
        }
        if (createMode == CreateMode.EXISTING_BRANCH) {
            loadLocalBranches()
        }
    }

    fun startWatching() {
        watchJob?.cancel()
        watchJob = viewModelScope.launch {
            while (true) {
                delay(autoRefreshInterval)
                if (!isBusy) checkForChanges()
            }
        }
    }

    fun stopWatching() {
        watchJob?.cancel()
        watchJob = null
    }

    private val isBusy: Boolean
        get() = isLoading || isCreating || isRemoving || isCleaningUp

    private suspend fun checkForChanges() {
        val fingerprint = useCases.readFingerprint().getOrNull() ?: return
        val previous = lastFingerprint
        lastFingerprint = fingerprint
        if (previous != null && fingerprint != previous) {
            refresh()
        } else {
            val detected = useCases.detectAgents(worktrees).getOrNull()
            agents = detected
            if (isGradleRepository && !isStoppingDaemons) loadGradleDaemons(listGeneration, worktrees)
        }
    }

    fun repairAfterMove(previousWorktreesRoot: String) {
        val generation = ++listGeneration
        viewModelScope.launch {
            isLoading = true
            val repair = useCases.repair(previousWorktreesRoot)
            applyListResult(generation, useCases.list())
            repair
                .onSuccess { success = WorktreeSuccess.RepairedAfterMove }
                .onFailure { error = it }
        }
    }

    private fun applyListResult(generation: Int, result: Result<List<Worktree>>) {
        if (generation != listGeneration) return
        result
            .onSuccess { worktrees = it; error = null; loadStatuses(generation, it) }
            .onFailure { error = it }
        isLoading = false
    }

    private fun loadStatuses(generation: Int, listed: List<Worktree>) {
        statuses = listed.associate { it.path to WorktreeStatusState.Loading }
        viewModelScope.launch {
            val resolved = useCases.resolveMainBranch().getOrNull()
            if (generation != listGeneration) return@launch
            mainBranch = resolved
            listed.forEach { worktree -> launch { readStatus(generation, worktree, resolved) } }
        }
        loadOverview(generation, listed)
    }

    private fun loadOverview(generation: Int, listed: List<Worktree>) {
        sizes = emptyMap()
        viewModelScope.launch {
            val detected = useCases.detectAgents(listed).getOrNull()
            if (generation == listGeneration) agents = detected
        }
        loadGradleSection(generation, listed)
        loadPorts(generation, listed)
        viewModelScope.launch {
            for (worktree in listed) {
                val size = useCases.measureSize(worktree, listed).getOrNull()
                if (generation != listGeneration) return@launch
                if (size != null) sizes = sizes + (worktree.path to size)
            }
        }
    }

    private fun loadPorts(generation: Int, listed: List<Worktree>) {
        viewModelScope.launch {
            val loaded = listed.filterNot { it.isPrunable }.mapNotNull { worktree ->
                useCases.readPorts(worktree).getOrNull()?.takeIf { it.isNotEmpty() }?.let { worktree.path to it }
            }.toMap()
            if (generation == listGeneration) ports = loaded
        }
    }

    private fun loadGradleSection(generation: Int, listed: List<Worktree>) {
        isGradleRepository = useCases.gradle.declaresProvider()
        if (!isGradleRepository) return
        isGradleBuildCacheEnabled = useCases.gradle.isBuildCacheEnabled()
        viewModelScope.launch { loadGradleDaemons(generation, listed) }
    }

    private suspend fun loadGradleDaemons(generation: Int, listed: List<Worktree>) {
        val state = useCases.gradle.listDaemons(listed)
            .fold(onSuccess = { GradleDaemonsState.Loaded(it) }, onFailure = { GradleDaemonsState.Unavailable })
        if (generation == listGeneration) gradleDaemons = state
    }

    fun stopGradleDaemon(pid: Long) = stopGradleDaemons(listOf(pid))

    fun stopIdleGradleDaemons() = stopGradleDaemons(null)

    private fun stopGradleDaemons(pids: List<Long>?) {
        if (isStoppingDaemons) return
        viewModelScope.launch {
            isStoppingDaemons = true
            try {
                useCases.gradle.stopDaemons(pids)
                    .onSuccess { daemonStopReport = it }
                    .onFailure { error = it }
                loadGradleDaemons(listGeneration, worktrees)
            } finally {
                isStoppingDaemons = false
            }
        }
    }

    fun dismissDaemonStopReport() {
        daemonStopReport = null
    }

    private fun loadStatusOf(worktree: Worktree) {
        val generation = listGeneration
        statuses = statuses + (worktree.path to WorktreeStatusState.Loading)
        viewModelScope.launch { readStatus(generation, worktree, mainBranch) }
    }

    private suspend fun readStatus(generation: Int, worktree: Worktree, mainBranch: MainBranch?) {
        val state = statusReadPermits.withPermit { useCases.readStatus(worktree, mainBranch) }
            .fold(onSuccess = { WorktreeStatusState.Loaded(it) }, onFailure = { WorktreeStatusState.Unavailable })
        if (generation == listGeneration) statuses = statuses + (worktree.path to state)
    }

    fun isTaskIdValid(taskId: String): Boolean = isValidGitRefSegment(normalizeTaskId(taskId))

    val isSelectedBranchAvailable: Boolean
        get() = localBranches.any { it.name == selectedBranch && !it.isCheckedOut }

    fun create(taskId: String, branchType: BranchType) {
        viewModelScope.launch {
            isCreating = true
            try {
                useCases.create(taskId, branchType)
                    .onSuccess { worktree ->
                        worktrees = worktrees + worktree
                        error = null
                        success = WorktreeSuccess.Created(worktree.path)
                        loadStatusOf(worktree)
                    }
                    .onFailure { error = it }
            } finally {
                isCreating = false
            }
        }
    }

    fun loadLocalBranches() {
        viewModelScope.launch {
            isLoadingBranches = true
            useCases.listLocalBranches()
                .onSuccess { localBranches = it; error = null }
                .onFailure { error = it }
            isLoadingBranches = false
        }
    }

    fun createFromBranch(branch: String) {
        viewModelScope.launch {
            isCreating = true
            try {
                useCases.createFromBranch(branch)
                    .onSuccess { worktree ->
                        worktrees = worktrees + worktree
                        error = null
                        success = WorktreeSuccess.Created(worktree.path)
                        loadStatusOf(worktree)
                    }
                    .onFailure { error = it }
            } finally {
                isCreating = false
            }
        }
    }

    fun remove(worktree: Worktree, branchToDelete: String?, force: Boolean = false) {
        viewModelScope.launch {
            isRemoving = true
            try {
                useCases.remove(worktree.path, branchToDelete, force)
                    .onSuccess {
                        worktrees = worktrees.filterNot { it.path == worktree.path }
                        statuses = statuses - worktree.path
                        sizes = sizes - worktree.path
                        error = null
                        pendingRemoval = null
                    }
                    .onFailure { failure ->
                        if (!force && failure.suggestsForceRetry()) {
                            pendingRemoval = PendingRemoval(worktree, deleteBranch = branchToDelete != null, requiresForce = true)
                        } else {
                            error = failure
                        }
                    }
            } finally {
                isRemoving = false
            }
        }
    }

    fun requestRemoval(worktree: Worktree) {
        pendingRemoval = PendingRemoval(worktree, deleteBranch = false)
    }

    fun setPendingRemovalDeleteBranch(deleteBranch: Boolean) {
        pendingRemoval = pendingRemoval?.copy(deleteBranch = deleteBranch)
    }

    fun copyLaunchCommand(worktree: Worktree) {
        viewModelScope.launch {
            useCases.copyLaunchCommand(worktree.path)
                .onSuccess {
                    error = null
                    copiedWorktreePath = worktree.path
                    delay(COPIED_FEEDBACK_DURATION_MS)
                    if (copiedWorktreePath == worktree.path) copiedWorktreePath = null
                }
                .onFailure { error = it }
        }
    }

    fun confirmPendingRemoval() {
        val pending = pendingRemoval ?: return
        val branchToDelete = pending.worktree.branch.takeIf { pending.deleteBranch }
        remove(pending.worktree, branchToDelete, force = pending.requiresForce)
    }

    fun dismissPendingRemoval() {
        pendingRemoval = null
    }

    fun openCleanup() {
        if (cleanupCandidates.isEmpty()) return
        cleanupDeleteBranches = false
        cleanupStopGradleDaemons = true
        isCleanupDialogOpen = true
    }

    fun dismissCleanup() {
        if (!isCleaningUp) isCleanupDialogOpen = false
    }

    fun confirmCleanup() {
        val candidates = cleanupCandidates
        val listed = worktrees
        val stopDaemons = isGradleRepository && cleanupStopGradleDaemons
        viewModelScope.launch {
            isCleaningUp = true
            try {
                val daemonsBefore = if (stopDaemons) useCases.gradle.listDaemons(listed).getOrNull().orEmpty() else emptyList()
                useCases.cleanUp(candidates, mainBranch, cleanupDeleteBranches)
                    .onSuccess { report ->
                        error = null
                        val daemonReport = stopDaemonsOfRemovedWorktrees(daemonsBefore, listed, report.removed)
                        success = WorktreeSuccess.CleanedUp(
                            removedCount = report.removed.size,
                            stoppedDaemons = daemonReport?.stopped?.size ?: 0,
                            keptDaemons = daemonReport?.kept?.size ?: 0,
                        )
                        cleanupIssues = report.takeIf { it.failures.isNotEmpty() || it.keptBranches.isNotEmpty() }
                    }
                    .onFailure { error = it }
            } finally {
                isCleaningUp = false
                isCleanupDialogOpen = false
            }
            refresh()
        }
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

    private fun loadedStatuses(): Map<String, WorktreeStatus> =
        statuses.mapNotNull { (path, state) -> (state as? WorktreeStatusState.Loaded)?.let { path to it.status } }.toMap()

    fun dismissError() {
        error = null
    }

    fun dismissSuccess() {
        success = null
    }

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
private val AUTO_REFRESH_INTERVAL = 3.seconds

enum class CreateMode { NEW_BRANCH, EXISTING_BRANCH }

sealed interface WorktreeStatusState {
    data object Loading : WorktreeStatusState
    data class Loaded(val status: WorktreeStatus) : WorktreeStatusState
    data object Unavailable : WorktreeStatusState
}

sealed interface WorktreeSuccess {
    data class Created(val path: String) : WorktreeSuccess
    data object RepairedAfterMove : WorktreeSuccess
    data class CleanedUp(
        val removedCount: Int,
        val stoppedDaemons: Int = 0,
        val keptDaemons: Int = 0,
    ) : WorktreeSuccess
}

sealed interface GradleDaemonsState {
    data object Loading : GradleDaemonsState
    data class Loaded(val daemons: List<GradleDaemon>) : GradleDaemonsState
    data object Unavailable : GradleDaemonsState
}
