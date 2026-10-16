package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.io.FileManager
import app.luxion.shogunai.domain.model.Worktree
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MeasureWorktreeSizeUseCase(
    private val fileManager: FileManager,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend operator fun invoke(worktree: Worktree, allWorktrees: List<Worktree>): Result<Long?> = runCatching {
        if (worktree.isPrunable || !fileManager.exists(worktree.path)) return@runCatching null
        val path = normalizePath(worktree.path)
        val nested = allWorktrees
            .map { it.path }
            .filter { other -> normalizePath(other) != path && isInside(normalizePath(other), path) }
            .toSet()
        withContext(ioDispatcher) { fileManager.directorySize(worktree.path, excluding = nested) }
    }
}
