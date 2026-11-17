package app.luxion.shogunai.cli

import app.luxion.shogunai.domain.model.AppRequest
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.model.runSuspendCatching
import app.luxion.shogunai.domain.usecase.herdrDetail
import app.luxion.shogunai.domain.usecase.orcaDetail

internal fun projectOrWorktreeRequest(createdAt: Long, projectId: String, settings: Boolean, worktree: String?): AppRequest =
    if (worktree != null && !settings) AppRequest.openWorktree(createdAt, projectId, worktree) else AppRequest.openProject(createdAt, projectId, settings)

internal suspend fun linkedWorktreeOf(
    directory: String,
    repository: String,
    topLevel: suspend (String) -> String,
    sameDirectory: (String, String) -> Boolean,
): String? = runSuspendCatching { topLevel(directory) }.getOrNull()?.takeUnless { sameDirectory(it, repository) }

internal fun appOpeningMessage(repository: String?, multirepoName: String?, request: AppRequest): String {
    if (repository == null) return "Opening ShogunAi"
    val folder = repository.substringAfterLast('/')
    val worktree = request.worktreePath
    return when {
        multirepoName != null -> "Opening ShogunAi; $multirepoName is a multirepo, its workspace repository belongs to it"
        request.settings -> "Opening the settings of $folder in ShogunAi"
        worktree != null -> "Opening ${worktree.substringAfterLast('/')} of $folder in ShogunAi"
        else -> "Opening $folder in ShogunAi"
    }
}

internal fun herdrCloseFailure(path: String, error: WorktreeError) =
    "shogun: couldn't close the Herdr workspace of $path: ${error.herdrDetail()}"

internal fun orcaCloseFailure(path: String, error: WorktreeError) =
    "shogun: couldn't close the Orca terminals of $path: ${error.orcaDetail()}"
