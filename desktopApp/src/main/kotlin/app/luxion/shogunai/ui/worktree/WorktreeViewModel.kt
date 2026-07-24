package app.luxion.shogunai.ui.worktree

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.luxion.shogunai.WorktreeUseCases
import app.luxion.shogunai.domain.model.BranchOption
import app.luxion.shogunai.domain.model.BranchType
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.usecase.isValidGitRefSegment
import app.luxion.shogunai.domain.usecase.normalizeTaskId
import kotlinx.coroutines.launch

class WorktreeViewModel(private val useCases: WorktreeUseCases) : ViewModel() {
    var worktrees by mutableStateOf<List<Worktree>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var isCreating by mutableStateOf(false)
        private set
    var isRemoving by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var successMessage by mutableStateOf<String?>(null)
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
        viewModelScope.launch {
            isLoading = true
            useCases.list()
                .onSuccess { worktrees = it; errorMessage = null }
                .onFailure { errorMessage = it.message }
            isLoading = false
        }
        if (createMode == CreateMode.EXISTING_BRANCH) {
            loadLocalBranches()
        }
    }

    /** Whether [taskId] is usable as-is: still valid as a Git ref segment once normalized. */
    fun isTaskIdValid(taskId: String): Boolean = isValidGitRefSegment(normalizeTaskId(taskId))

    fun create(taskId: String, branchType: BranchType) {
        viewModelScope.launch {
            isCreating = true
            try {
                useCases.create(taskId, branchType)
                    .onSuccess { worktree ->
                        worktrees = worktrees + worktree
                        errorMessage = null
                        successMessage = "Worktree \"${worktree.path}\" creado"
                    }
                    .onFailure { errorMessage = it.message }
            } finally {
                isCreating = false
            }
        }
    }

    fun loadLocalBranches() {
        viewModelScope.launch {
            isLoadingBranches = true
            useCases.listLocalBranches()
                .onSuccess { localBranches = it; errorMessage = null }
                .onFailure { errorMessage = it.message }
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
                        errorMessage = null
                        successMessage = "Worktree \"${worktree.path}\" creado"
                    }
                    .onFailure { errorMessage = it.message }
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
                        errorMessage = null
                        pendingRemoval = null
                    }
                    .onFailure { error ->
                        if (!force && error.suggestsForceRetry()) {
                            pendingRemoval = PendingRemoval(worktree, deleteBranch = branchToDelete != null, requiresForce = true)
                        } else {
                            errorMessage = error.message
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

    fun openTerminal(worktree: Worktree) {
        viewModelScope.launch {
            useCases.openTerminal(worktree.path)
                .onSuccess { errorMessage = null }
                .onFailure { errorMessage = it.message }
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
        errorMessage = null
    }

    fun dismissSuccess() {
        successMessage = null
    }

    private data class PendingRemoval(
        val worktree: Worktree,
        val deleteBranch: Boolean,
        val requiresForce: Boolean = false,
    )
}

private fun Throwable.suggestsForceRetry(): Boolean =
    this is WorktreeError.GitCommandFailed && errorOutput.contains("--force", ignoreCase = true)

enum class CreateMode { NEW_BRANCH, EXISTING_BRANCH }
