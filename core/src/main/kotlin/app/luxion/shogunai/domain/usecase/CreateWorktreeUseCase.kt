package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.FileManager
import app.luxion.shogunai.domain.io.RepositoryApproval
import app.luxion.shogunai.domain.io.ProvisioningRecordStore
import app.luxion.shogunai.domain.io.RepositoryTrust
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
    private val trust: RepositoryTrust = RepositoryTrust.NONE,
    private val approval: RepositoryApproval = RepositoryApproval.SKIP,
    private val records: ProvisioningRecordStore = ProvisioningRecordStore.NONE,
) {
    suspend operator fun invoke(taskId: String, branchType: BranchType, startPoint: String? = null): Result<Worktree> =
        runCatching {
            if (startPoint != null && isOptionLikeRef(startPoint)) throw WorktreeError.InvalidRef(startPoint)
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

            val provisioned = checkProvisioningAndResolveFiles(config, executor, fileManager, provisioningSource)

            val branch = "${branchType.prefix}/$id"
            addWorktreeAndCopySecrets(
                config = config,
                executor = executor,
                fileManager = fileManager,
                worktreePath = worktreePath,
                branch = branch,
                addArgs = listOf("git", "worktree", "add") + listOfNotNull("--no-track".takeIf { startPoint != null }) + listOf(worktreePath, "-b", branch) + listOfNotNull(startPoint),
                createdBranch = branch,
                provisioned = provisioned,
                environment = environment,
                trust = trust,
                approval = approval,
                records = records,
            )
        }
}

fun isOptionLikeRef(ref: String): Boolean = ref.trim().startsWith("-")

fun normalizeTaskId(raw: String): String = raw.trim().replace(Regex("\\s+"), "-")

private val DISALLOWED_GIT_REF_CHARS = Regex("[ ~^:?*\\[\\\\]")

fun isValidGitRefSegment(id: String): Boolean =
    id.isNotEmpty() &&
        !DISALLOWED_GIT_REF_CHARS.containsMatchIn(id) &&
        !id.contains("..") &&
        !id.startsWith(".") && !id.endsWith(".") &&
        !id.startsWith("/") && !id.endsWith("/") &&
        !id.endsWith(".lock")
