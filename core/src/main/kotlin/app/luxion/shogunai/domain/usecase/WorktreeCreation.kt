package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.executor.CommandExecutionException
import app.luxion.shogunai.domain.io.FileManager
import app.luxion.shogunai.domain.io.RepositoryApproval
import app.luxion.shogunai.domain.io.ApprovalDecision
import app.luxion.shogunai.domain.io.ProvisioningRecordStore
import app.luxion.shogunai.domain.io.RepositoryTrust
import app.luxion.shogunai.domain.io.SymbolicLinksNotSupported
import app.luxion.shogunai.domain.io.RepositoryProvisioningSource
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.RepositoryApprovalSubject
import app.luxion.shogunai.domain.model.approvalSubject
import app.luxion.shogunai.domain.model.ProvisionMode
import app.luxion.shogunai.domain.model.ProvisionedFile
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.model.isLocalPattern
import app.luxion.shogunai.domain.model.joinPath
import java.io.IOException

internal data class ProvisionedFiles(
    val copied: List<String> = emptyList(),
    val linked: List<String> = emptyList(),
    val approvalSubject: RepositoryApprovalSubject = RepositoryApprovalSubject(),
    val linkedFolders: Map<String, List<String>> = emptyMap(),
    val copiedFolders: Map<String, List<String>> = emptyMap(),
    val skippedLinks: List<String> = emptyList(),
)

internal data class AppliedProvisioning(
    val files: List<ProvisionedFile>,
    val removed: List<String>,
)

internal suspend fun checkProvisioningAndResolveFiles(
    config: ProjectConfig,
    executor: ShellCommandExecutor,
    fileManager: FileManager,
    provisioningSource: RepositoryProvisioningSource,
): ProvisionedFiles {
    val missingSecrets = config.secretFiles
        .filterNot(::isLocalPattern)
        .filterNot { fileManager.exists(config.baseSecretPath(it)) }
    if (missingSecrets.isNotEmpty()) {
        throw WorktreeError.SecretFileNotFound(missingSecrets, config.baseRepositoryPath)
    }
    return resolveProvisionedFiles(config, executor, fileManager, provisioningSource)
}


private fun localFolderName(entry: String): String = entry.trimEnd('/', '\\')

internal suspend fun resolveProvisionedFiles(
    config: ProjectConfig,
    executor: ShellCommandExecutor,
    fileManager: FileManager,
    provisioningSource: RepositoryProvisioningSource,
): ProvisionedFiles {
    val provisioning = provisioningSource.read(config.baseRepositoryPath)
    val (patternEntries, pathEntries) = config.secretFiles.partition(::isLocalPattern)
    val (linkedPatterns, copiedPatterns) = patternEntries.partition(config::isLinked)
    val folders = pathEntries.map(::localFolderName)
        .filter { fileManager.isDirectory(config.baseSecretPath(it)) }
        .associateWith { fileManager.listTree(config.baseSecretPath(it)) }
    val (linkedFolders, copiedFolders) = folders.entries.partition { config.isLinked(it.key) }
    val localCopied = pathEntries.filterNot(config::isLinked).flatMap { entry ->
        val folder = localFolderName(entry)
        folders[folder]?.files?.map { "$folder/$it" } ?: listOf(entry)
    } + listUntrackedFiles(config, executor, copiedPatterns)
    val localLinked = pathEntries.filter { config.isLinked(it) && localFolderName(it) !in folders } +
        listUntrackedFiles(config, executor, linkedPatterns)
    val insideLinkedFolder = { file: String -> linkedFolders.any { file.startsWith("${it.key}/") } }
    val local = (localCopied + localLinked).toSet()
    val repositoryLinked = listUntrackedFiles(config, executor, provisioning.linkPatterns)
        .filterNot { it in local || insideLinkedFolder(it) }
    val linked = (localLinked + repositoryLinked).distinct()
    val copied = (localCopied + listUntrackedFiles(config, executor, provisioning.allPatterns))
        .distinct()
        .filterNot { it in linked || insideLinkedFolder(it) }
    return ProvisionedFiles(
        copied = copied,
        linked = linked,
        approvalSubject = provisioning.approvalSubject,
        linkedFolders = linkedFolders.associate { it.key to it.value.files },
        copiedFolders = copiedFolders.associate { it.key to it.value.files },
        skippedLinks = copiedFolders.flatMap { (folder, listing) -> listing.skippedLinks.map { "$folder/$it" } },
    )
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
    approval: RepositoryApproval,
    records: ProvisioningRecordStore,
    baseBranch: String? = null,
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

    baseBranch?.let { recordBaseBranchOrRollback(config, executor, worktreePath, branch, it, createdBranch) }
    copySecretsOrRollback(config, executor, fileManager, records, worktreePath, provisioned, createdBranch)
    val approved = settleApproval(config, trust, approval, provisioned.approvalSubject)
    val prepared = prepareEnvironmentOrRollback(config, executor, environment, worktreePath, createdBranch)
    val skipped = runPostCreateCommandsIfApproved(executor, approved, worktreePath, provisioned.approvalSubject)

    return Worktree(
        path = worktreePath,
        branch = branch,
        skippedPostCreateCommands = skipped.commands,
        skippedVariables = skipped.variables,
        skippedLinks = provisioned.skippedLinks,
        pods = prepared.pods,
    )
}

private suspend fun recordBaseBranchOrRollback(
    config: ProjectConfig,
    executor: ShellCommandExecutor,
    worktreePath: String,
    branch: String,
    baseBranch: String,
    createdBranch: String?,
) {
    try {
        writeBaseBranch(config, executor, branch, baseBranch)
    } catch (error: WorktreeError.GitCommandFailed) {
        rollBack(config, executor, worktreePath, createdBranch)
        throw error
    }
}

private suspend fun prepareEnvironmentOrRollback(
    config: ProjectConfig,
    executor: ShellCommandExecutor,
    environment: WorktreeEnvironmentPreparer,
    worktreePath: String,
    createdBranch: String?,
): PreparedWorktree =
    try {
        environment.prepare(config.baseRepositoryPath, worktreePath)
    } catch (error: Exception) {
        rollBack(config, executor, worktreePath, createdBranch)
        throw error
    }

private suspend fun copySecretsOrRollback(
    config: ProjectConfig,
    executor: ShellCommandExecutor,
    fileManager: FileManager,
    records: ProvisioningRecordStore,
    worktreePath: String,
    provisioned: ProvisionedFiles,
    createdBranch: String?,
) {
    try {
        applyProvisionedFiles(config, fileManager, records, worktreePath, provisioned)
    } catch (copyError: WorktreeError) {
        if (copyError !is WorktreeError.SecretCopyFailed && copyError !is WorktreeError.LinkedFolderOccupied) throw copyError
        rollBack(config, executor, worktreePath, createdBranch)
        throw copyError
    }
}

internal fun applyProvisionedFiles(
    config: ProjectConfig,
    fileManager: FileManager,
    records: ProvisioningRecordStore,
    worktreePath: String,
    provisioned: ProvisionedFiles,
): AppliedProvisioning {
    try {
        val previous = records.read(worktreePath)
        unlinkCopiedFolders(config, fileManager, worktreePath, provisioned)
        val removed = removeDeletedFolderFiles(fileManager, worktreePath, provisioned, previous)
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
        val fallbackFolders = mutableMapOf<String, List<String>>()
        val linkedFolders = provisioned.linkedFolders.flatMap { (folder, files) ->
            if (linkFolder(config, fileManager, worktreePath, folder, previous[folder].orEmpty())) {
                listOf(ProvisionedFile(folder, ProvisionMode.LINKED))
            } else {
                fallbackFolders[folder] = files
                files.map { file ->
                    copyFromBase(config, fileManager, worktreePath, "$folder/$file")
                    ProvisionedFile("$folder/$file", ProvisionMode.COPIED_INSTEAD_OF_LINKING)
                }
            }
        }
        records.write(worktreePath, provisioned.copiedFolders + fallbackFolders)
        return AppliedProvisioning(copied + linked + linkedFolders, removed)
    } catch (occupied: WorktreeError.LinkedFolderOccupied) {
        throw occupied
    } catch (copyError: Exception) {
        throw WorktreeError.SecretCopyFailed(copyError)
    }
}

private fun unlinkCopiedFolders(
    config: ProjectConfig,
    fileManager: FileManager,
    worktreePath: String,
    provisioned: ProvisionedFiles,
) {
    provisioned.copiedFolders.keys.forEach { folder ->
        val destination = joinPath(worktreePath, folder)
        if (fileManager.isSymbolicLinkTo(destination, config.baseSecretPath(folder))) fileManager.deleteFile(destination)
    }
}

private fun removeDeletedFolderFiles(
    fileManager: FileManager,
    worktreePath: String,
    provisioned: ProvisionedFiles,
    previous: Map<String, List<String>>,
): List<String> = provisioned.copiedFolders.flatMap { (folder, files) ->
    deletedFromBase(folder, files, previous).filter { fileName ->
        requireInsideWorktree(fileManager, worktreePath, fileName)
        val destination = joinPath(worktreePath, fileName)
        val isRegularFile = fileManager.exists(destination) &&
            !fileManager.isDirectory(destination) &&
            !fileManager.isSymbolicLink(destination)
        if (isRegularFile) fileManager.deleteFile(destination)
        isRegularFile
    }
}

internal fun deletedFromBase(folder: String, files: List<String>, previous: Map<String, List<String>>): List<String> =
    previous[folder].orEmpty().filterNot { it in files }.map { "$folder/$it" }

private fun linkFolder(
    config: ProjectConfig,
    fileManager: FileManager,
    worktreePath: String,
    folder: String,
    recorded: List<String>,
): Boolean {
    requireInsideWorktree(fileManager, worktreePath, folder)
    val destination = joinPath(worktreePath, folder)
    if (fileManager.isDirectory(destination) && !fileManager.isSymbolicLink(destination)) {
        val existing = fileManager.listTree(destination)
        if (existing.skippedLinks.isNotEmpty() || !recorded.containsAll(existing.files)) {
            throw WorktreeError.LinkedFolderOccupied(folder, worktreePath)
        }
        fileManager.deleteRecursively(destination)
    }
    return try {
        fileManager.createSymbolicLink(target = config.baseSecretPath(folder), link = destination)
        true
    } catch (unsupported: SymbolicLinksNotSupported) {
        false
    }
}

private fun requireInsideWorktree(fileManager: FileManager, worktreePath: String, fileName: String) {
    if (!fileManager.isInside(worktreePath, joinPath(worktreePath, fileName).substringBeforeLast('/'))) {
        throw IOException("$fileName resolves outside the worktree")
    }
}

private fun copyFromBase(config: ProjectConfig, fileManager: FileManager, worktreePath: String, fileName: String) {
    requireInsideWorktree(fileManager, worktreePath, fileName)
    fileManager.copy(source = config.baseSecretPath(fileName), destination = joinPath(worktreePath, fileName))
}

internal data class SkippedConfiguration(
    val commands: List<List<String>> = emptyList(),
    val variables: List<String> = emptyList(),
)

internal suspend fun settleApproval(
    config: ProjectConfig,
    trust: RepositoryTrust,
    approval: RepositoryApproval,
    subject: RepositoryApprovalSubject,
): Boolean {
    val repository = config.baseRepositoryPath
    if (trust.isApproved(repository, subject)) return true
    if (approval.decide(repository, subject) != ApprovalDecision.APPROVE) return false
    trust.approve(repository, subject)
    return true
}

internal suspend fun runPostCreateCommandsIfApproved(
    executor: ShellCommandExecutor,
    approved: Boolean,
    worktreePath: String,
    subject: RepositoryApprovalSubject,
): SkippedConfiguration {
    if (!approved) return SkippedConfiguration(subject.commands, subject.variableNames)
    runPostCreateCommands(executor, worktreePath, subject.commands)
    return SkippedConfiguration()
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
