package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.model.MainBranch
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.WorktreeError

sealed interface BranchDeletion {
    data object Deleted : BranchDeletion
    data object KeptNotMerged : BranchDeletion
    data class KeptFailed(val reason: String) : BranchDeletion
}

class DeleteMergedBranchUseCase(
    private val config: ProjectConfig,
    private val executor: ShellCommandExecutor,
) {
    suspend operator fun invoke(branch: String): Result<Boolean> =
        outcome(branch).map { it == BranchDeletion.Deleted }

    suspend fun outcome(branch: String): Result<BranchDeletion> = runCatching {
        if (isOptionLikeRef(branch)) throw WorktreeError.InvalidRef(branch)
        val mainBranch = ResolveMainBranchUseCase(config, executor)().getOrNull()
        deleteBranchIfContained(executor, config, branch, mainBranch)
    }
}

internal suspend fun deleteBranchIfContained(
    executor: ShellCommandExecutor,
    config: ProjectConfig,
    branch: String,
    mainBranch: MainBranch?,
): BranchDeletion {
    val targets = listOfNotNull(recordedBaseRef(executor, config, branch), mainBranch).distinctBy { it.ref }
    if (targets.none { isContained(executor, config, branch, it) }) return BranchDeletion.KeptNotMerged
    return forceDeleteBranch(executor, config, branch)
}

private suspend fun isContained(executor: ShellCommandExecutor, config: ProjectConfig, branch: String, target: MainBranch): Boolean =
    target.name != branch && executor.execute(
        command = listOf("git", "merge-base", "--is-ancestor", branch, target.ref),
        workingDirectory = config.baseRepositoryPath,
    ).isSuccess

internal suspend fun forceDeleteBranch(
    executor: ShellCommandExecutor,
    config: ProjectConfig,
    branch: String,
): BranchDeletion {
    val deletion = executor.execute(
        command = listOf("git", "branch", "-D", "--", branch),
        workingDirectory = config.baseRepositoryPath,
    )
    return if (deletion.isSuccess) BranchDeletion.Deleted else BranchDeletion.KeptFailed(deletion.stderr.trim())
}
