package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.FileManager
import app.luxion.shogunai.domain.io.RepositoryProvisioningSource
import app.luxion.shogunai.domain.model.BranchType
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeError

class CreateWorktreeUseCase(
    private val config: ProjectConfig,
    private val executor: ShellCommandExecutor,
    private val fileManager: FileManager,
    private val provisioningSource: RepositoryProvisioningSource,
    private val environment: WorktreeEnvironmentPreparer = WorktreeEnvironmentPreparer.NONE,
) {
    suspend operator fun invoke(taskId: String, branchType: BranchType): Result<Worktree> =
        runCatching {
            val id = normalizeTaskId(taskId)
            if (!isValidGitRefSegment(id)) {
                throw WorktreeError.InvalidTaskId(taskId)
            }

            if (!fileManager.exists(config.baseRepositoryPath)) {
                throw WorktreeError.BaseRepositoryNotFound(config.baseRepositoryPath)
            }

            val worktreePath = config.worktreePathFor(id)
            if (fileManager.exists(worktreePath)) {
                throw WorktreeError.WorktreeAlreadyExists(worktreePath)
            }

            val repositoryFiles = checkProvisioningAndResolveFiles(config, executor, fileManager, provisioningSource)

            val branch = "${branchType.prefix}/$id"
            addWorktreeAndCopySecrets(
                config = config,
                executor = executor,
                fileManager = fileManager,
                worktreePath = worktreePath,
                branch = branch,
                addArgs = listOf("git", "worktree", "add", worktreePath, "-b", branch),
                branchToDeleteOnLfsFailure = branch,
                repositoryFiles = repositoryFiles,
                environment = environment,
            )
        }
}

fun normalizeTaskId(raw: String): String = raw.trim().replace(Regex("\\s+"), "-")

private val DISALLOWED_GIT_REF_CHARS = Regex("[ ~^:?*\\[\\\\]")

fun isValidGitRefSegment(id: String): Boolean =
    id.isNotEmpty() &&
        !DISALLOWED_GIT_REF_CHARS.containsMatchIn(id) &&
        !id.contains("..") &&
        !id.startsWith(".") && !id.endsWith(".") &&
        !id.startsWith("/") && !id.endsWith("/") &&
        !id.endsWith(".lock")
