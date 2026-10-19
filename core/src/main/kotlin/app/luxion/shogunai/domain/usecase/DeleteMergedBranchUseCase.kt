package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.model.MainBranch
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.WorktreeError

class DeleteMergedBranchUseCase(
    private val config: ProjectConfig,
    private val executor: ShellCommandExecutor,
) {
    suspend operator fun invoke(branch: String): Result<Boolean> = runCatching {
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
): Boolean {
    if (mainBranch == null) return false
    val contained = executor.execute(
        command = listOf("git", "merge-base", "--is-ancestor", branch, mainBranch.ref),
        workingDirectory = config.baseRepositoryPath,
    )
    if (!contained.isSuccess) return false
    return executor.execute(
        command = listOf("git", "branch", "-D", "--", branch),
        workingDirectory = config.baseRepositoryPath,
    ).isSuccess
}
