package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.executor.CommandExecutionException
import app.luxion.shogunai.domain.io.FileManager
import app.luxion.shogunai.domain.io.PostCreateApproval
import app.luxion.shogunai.domain.io.PostCreateDecision
import app.luxion.shogunai.domain.io.RepositoryTrust
import app.luxion.shogunai.domain.io.SymbolicLinksNotSupported
import app.luxion.shogunai.domain.io.RepositoryProvisioningSource
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.ProvisionMode
import app.luxion.shogunai.domain.model.ProvisionedFile
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.model.joinPath
import java.io.IOException

internal data class ProvisionedFiles(
    val copied: List<String> = emptyList(),
    val linked: List<String> = emptyList(),
    val postCreateCommands: List<List<String>> = emptyList(),
)

internal suspend fun checkProvisioningAndResolveFiles(
    config: ProjectConfig,
    executor: ShellCommandExecutor,
    fileManager: FileManager,
    provisioningSource: RepositoryProvisioningSource,
): ProvisionedFiles {
    val missingSecrets = config.secretFiles.filterNot { fileManager.exists(config.baseSecretPath(it)) }
    if (missingSecrets.isNotEmpty()) {
        throw WorktreeError.SecretFileNotFound(missingSecrets, config.baseRepositoryPath)
    }
    return resolveProvisionedFiles(config, executor, fileManager, provisioningSource)
}

internal suspend fun resolveProvisionedFiles(
    config: ProjectConfig,
    executor: ShellCommandExecutor,
    fileManager: FileManager,
    provisioningSource: RepositoryProvisioningSource,
): ProvisionedFiles {
    val provisioning = provisioningSource.read(config.baseRepositoryPath)
    val localFiles = config.secretFiles.flatMap { expandLocalEntry(config, fileManager, it) }
    val linked = listUntrackedFiles(config, executor, provisioning.linkPatterns).filterNot { it in localFiles }
    val copied = (localFiles + listUntrackedFiles(config, executor, provisioning.allPatterns))
        .distinct()
        .filterNot { it in linked }
    return ProvisionedFiles(copied, linked, provisioning.postCreateCommands)
}

private fun expandLocalEntry(config: ProjectConfig, fileManager: FileManager, entry: String): List<String> {
    val path = config.baseSecretPath(entry)
    if (!fileManager.isDirectory(path)) return listOf(entry)
    val folder = entry.trimEnd('/', '\\')
    return fileManager.listFiles(path).map { "$folder/$it" }
}

private suspend fun listUntrackedFiles(
    config: ProjectConfig,
    executor: ShellCommandExecutor,
    patterns: List<String>,
): List<String> {
    if (patterns.isEmpty()) return emptyList()
    val command = listOf("git", "ls-files", "--others", "--ignored", "-z") + patterns.flatMap { listOf("-x", it) }
    val result = executor.execute(command = command, workingDirectory = config.baseRepositoryPath)
    if (!result.isSuccess) throw WorktreeError.GitCommandFailed(result.command, result.exitCode, result.stderr)
    return result.stdout.split('\u0000').filter { it.isNotEmpty() }
}

internal suspend fun addWorktreeAndCopySecrets(
    config: ProjectConfig,
    executor: ShellCommandExecutor,
    fileManager: FileManager,
    worktreePath: String,
    branch: String,
    addArgs: List<String>,
    createdBranch: String?,
    provisioned: ProvisionedFiles,
    environment: WorktreeEnvironmentPreparer,
    trust: RepositoryTrust,
    approval: PostCreateApproval,
): Worktree {
    val add = executor.execute(command = addArgs, workingDirectory = config.baseRepositoryPath)
    if (!add.isSuccess) {
        val alreadyCheckedOutPath = BRANCH_ALREADY_CHECKED_OUT.find(add.stderr)?.groupValues?.get(1)
        if (alreadyCheckedOutPath != null || add.stderr.contains("is already checked out")) {
            throw WorktreeError.BranchAlreadyCheckedOut(branch, alreadyCheckedOutPath)
        }

        if (GIT_LFS_MISSING_MARKERS.any { add.stderr.contains(it) }) {
            rollBack(config, executor, worktreePath, createdBranch)
            throw WorktreeError.GitLfsNotFound
        }
        throw WorktreeError.GitCommandFailed(add.command, add.exitCode, add.stderr)
    }

    copySecretsOrRollback(config, executor, fileManager, worktreePath, provisioned, createdBranch)
    prepareEnvironmentOrRollback(config, executor, environment, worktreePath, createdBranch)
    val skipped = runApprovedPostCreateCommands(
        config, executor, trust, approval, worktreePath, provisioned.postCreateCommands,
    )

    return Worktree(path = worktreePath, branch = branch, skippedPostCreateCommands = skipped)
}

private suspend fun prepareEnvironmentOrRollback(
    config: ProjectConfig,
    executor: ShellCommandExecutor,
    environment: WorktreeEnvironmentPreparer,
    worktreePath: String,
    createdBranch: String?,
) {
    try {
        environment.prepare(config.baseRepositoryPath, worktreePath)
    } catch (error: Exception) {
        rollBack(config, executor, worktreePath, createdBranch)
        throw error
    }
}

private suspend fun copySecretsOrRollback(
    config: ProjectConfig,
    executor: ShellCommandExecutor,
    fileManager: FileManager,
    worktreePath: String,
    provisioned: ProvisionedFiles,
    createdBranch: String?,
) {
    try {
        applyProvisionedFiles(config, fileManager, worktreePath, provisioned)
    } catch (copyError: WorktreeError.SecretCopyFailed) {
        rollBack(config, executor, worktreePath, createdBranch)
        throw copyError
    }
}

internal fun applyProvisionedFiles(
    config: ProjectConfig,
    fileManager: FileManager,
    worktreePath: String,
    provisioned: ProvisionedFiles,
): List<ProvisionedFile> {
    try {
        val copied = provisioned.copied.map { fileName ->
            copyFromBase(config, fileManager, worktreePath, fileName)
            ProvisionedFile(fileName, ProvisionMode.COPIED)
        }
        val linked = provisioned.linked.map { fileName ->
            requireInsideWorktree(fileManager, worktreePath, fileName)
            try {
                fileManager.createSymbolicLink(
                    target = config.baseSecretPath(fileName),
                    link = joinPath(worktreePath, fileName),
                )
                ProvisionedFile(fileName, ProvisionMode.LINKED)
            } catch (unsupported: SymbolicLinksNotSupported) {
                copyFromBase(config, fileManager, worktreePath, fileName)
                ProvisionedFile(fileName, ProvisionMode.COPIED_INSTEAD_OF_LINKING)
            }
        }
        return copied + linked
    } catch (copyError: Exception) {
        throw WorktreeError.SecretCopyFailed(copyError)
    }
}

private fun requireInsideWorktree(fileManager: FileManager, worktreePath: String, fileName: String) {
    if (!fileManager.isInside(worktreePath, joinPath(worktreePath, fileName))) {
        throw IOException("$fileName resolves outside the worktree")
    }
}

private fun copyFromBase(config: ProjectConfig, fileManager: FileManager, worktreePath: String, fileName: String) {
    requireInsideWorktree(fileManager, worktreePath, fileName)
    fileManager.copy(source = config.baseSecretPath(fileName), destination = joinPath(worktreePath, fileName))
}

internal suspend fun runApprovedPostCreateCommands(
    config: ProjectConfig,
    executor: ShellCommandExecutor,
    trust: RepositoryTrust,
    approval: PostCreateApproval,
    worktreePath: String,
    commands: List<List<String>>,
): List<List<String>> {
    if (commands.isEmpty()) return emptyList()
    val repository = config.baseRepositoryPath
    if (!trust.isApproved(repository, commands)) {
        if (approval.decide(repository, commands) != PostCreateDecision.RUN) return commands
        trust.approve(repository, commands)
    }
    runPostCreateCommands(executor, worktreePath, commands)
    return emptyList()
}

private suspend fun runPostCreateCommands(
    executor: ShellCommandExecutor,
    worktreePath: String,
    commands: List<List<String>>,
) {
    commands.forEach { command ->
        val result = try {
            executor.execute(command = command, workingDirectory = worktreePath)
        } catch (error: CommandExecutionException) {
            throw WorktreeError.PostCreateCommandFailed(worktreePath, command, null, error.cause?.message ?: error.message.orEmpty())
        }
        if (!result.isSuccess) {
            throw WorktreeError.PostCreateCommandFailed(worktreePath, command, result.exitCode, result.stderr)
        }
    }
}

private suspend fun rollBack(
    config: ProjectConfig,
    executor: ShellCommandExecutor,
    worktreePath: String,
    createdBranch: String?,
) {
    executor.execute(
        command = listOf("git", "worktree", "remove", "--force", worktreePath),
        workingDirectory = config.baseRepositoryPath,
    )
    createdBranch?.let {
        executor.execute(
            command = listOf("git", "branch", "-D", "--", it),
            workingDirectory = config.baseRepositoryPath,
        )
    }
}

internal val GIT_LFS_MISSING_MARKERS = listOf(
    "git-lfs' was not found on your path",
    "git-lfs filter-process: git-lfs: command not found",
)

private val BRANCH_ALREADY_CHECKED_OUT = Regex("is already checked out at '([^']*)'")
