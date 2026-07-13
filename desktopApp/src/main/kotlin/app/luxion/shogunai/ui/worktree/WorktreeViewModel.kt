package app.luxion.shogunai.ui.worktree

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.luxion.shogunai.WorktreeUseCases
import app.luxion.shogunai.domain.model.BranchType
import app.luxion.shogunai.domain.model.Worktree
import kotlinx.coroutines.launch

class WorktreeViewModel(private val useCases: WorktreeUseCases) : ViewModel() {
    var worktrees by mutableStateOf<List<Worktree>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            isLoading = true
            useCases.list()
                .onSuccess { worktrees = it; errorMessage = null }
                .onFailure { errorMessage = it.message }
            isLoading = false
        }
    }

    fun create(taskId: String, branchType: BranchType) {
        viewModelScope.launch {
            useCases.create(taskId, branchType)
                .onSuccess { worktree -> worktrees = worktrees + worktree; errorMessage = null }
                .onFailure { errorMessage = it.message }
        }
    }

    fun remove(worktree: Worktree, branchToDelete: String?, force: Boolean = false) {
        viewModelScope.launch {
            useCases.remove(worktree.path, branchToDelete, force)
                .onSuccess { worktrees = worktrees.filterNot { it.path == worktree.path }; errorMessage = null }
                .onFailure { errorMessage = it.message }
        }
    }

    fun openTerminal(worktree: Worktree) {
        viewModelScope.launch {
            useCases.openTerminal(worktree.path)
                .onSuccess { errorMessage = null }
                .onFailure { errorMessage = it.message }
        }
    }
}
