package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.FileManager
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.model.joinPath

internal suspend fun addWorktreeAndCopySecrets(
    config: ProjectConfig,
    executor: ShellCommandExecutor,
    fileManager: FileManager,
    worktreePath: String,
    branch: String,
    addArgs: List<String>,
    branchToDeleteOnLfsFailure: String?,
): Worktree {
    val add = executor.execute(command = addArgs, workingDirectory = config.baseRepositoryPath)
    if (!add.isSuccess) {
        val alreadyCheckedOutPath = BRANCH_ALREADY_CHECKED_OUT.find(add.stderr)?.groupValues?.get(1)
        if (alreadyCheckedOutPath != null || add.stderr.contains("is already checked out")) {
            throw WorktreeError.BranchAlreadyCheckedOut(branch, alreadyCheckedOutPath)
        }

        if (GIT_LFS_MISSING_MARKERS.any { add.stderr.contains(it) }) {
            removeIncompleteWorktreeIgnoringResult(config, executor, worktreePath)
            branchToDeleteOnLfsFailure?.let {
                executor.execute(
                    command = listOf("git", "branch", "-D", it),
                    workingDirectory = config.baseRepositoryPath,
                )
            }
            throw WorktreeError.GitLfsNotFound
        }
        throw WorktreeError.GitCommandFailed(add.command, add.exitCode, add.stderr)
    }

    copySecretsOrRollback(config, executor, fileManager, worktreePath)

    return Worktree(path = worktreePath, branch = branch)
}

private suspend fun copySecretsOrRollback(
    config: ProjectConfig,
    executor: ShellCommandExecutor,
    fileManager: FileManager,
    worktreePath: String,
) {
    try {
        config.secretFiles.forEach { fileName ->
            fileManager.copy(
                source = config.baseSecretPath(fileName),
                destination = joinPath(worktreePath, fileName),
            )
        }
    } catch (copyError: Exception) {
        removeIncompleteWorktreeIgnoringResult(config, executor, worktreePath)
        throw WorktreeError.SecretCopyFailed(copyError)
    }
}

private suspend fun removeIncompleteWorktreeIgnoringResult(
    config: ProjectConfig,
    executor: ShellCommandExecutor,
    worktreePath: String,
) {
    executor.execute(
        command = listOf("git", "worktree", "remove", "--force", worktreePath),
        workingDirectory = config.baseRepositoryPath,
    )
}

internal val GIT_LFS_MISSING_MARKERS = listOf(
    "git-lfs' was not found on your path",
    "git-lfs filter-process: git-lfs: command not found",
)

private val BRANCH_ALREADY_CHECKED_OUT = Regex("is already checked out at '([^']*)'")
