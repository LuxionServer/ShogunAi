package app.luxion.shogunai.ui.worktree

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.luxion.shogunai.WorktreeUseCases
import app.luxion.shogunai.domain.model.BranchOption
import app.luxion.shogunai.domain.model.BranchType
import app.luxion.shogunai.domain.model.MainBranch
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.model.WorktreeStatus
import app.luxion.shogunai.domain.usecase.isValidGitRefSegment
import app.luxion.shogunai.domain.usecase.normalizeTaskId
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class WorktreeViewModel(private val useCases: WorktreeUseCases) : ViewModel() {
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
            applyListResult(generation, useCases.list())
        }
        if (createMode == CreateMode.EXISTING_BRANCH) {
            loadLocalBranches()
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

enum class CreateMode { NEW_BRANCH, EXISTING_BRANCH }

sealed interface WorktreeStatusState {
    data object Loading : WorktreeStatusState
    data class Loaded(val status: WorktreeStatus) : WorktreeStatusState
    data object Unavailable : WorktreeStatusState
}

sealed interface WorktreeSuccess {
    data class Created(val path: String) : WorktreeSuccess
    data object RepairedAfterMove : WorktreeSuccess
}
