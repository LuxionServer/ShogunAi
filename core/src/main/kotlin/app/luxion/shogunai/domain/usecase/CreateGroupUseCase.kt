package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.FileManager
import app.luxion.shogunai.domain.io.PostCreateApproval
import app.luxion.shogunai.domain.io.ProvisioningRecordStore
import app.luxion.shogunai.domain.io.RepositoryProvisioningSource
import app.luxion.shogunai.domain.io.RepositoryTrust
import app.luxion.shogunai.domain.model.BranchType
import app.luxion.shogunai.domain.model.REMOTE_PREFIX
import app.luxion.shogunai.domain.model.WorktreeError

enum class GroupRepositoryOutcome { CREATED, EXISTING, FAILED }

data class GroupRepositoryResult(
    val alias: String,
    val outcome: GroupRepositoryOutcome,
    val path: String? = null,
    val skippedPostCreateCommands: List<List<String>> = emptyList(),
    val error: Throwable? = null,
)

data class GroupCreation(val branch: String, val repositories: List<GroupRepositoryResult>) {
    val failed: Boolean
        get() = repositories.any { it.outcome == GroupRepositoryOutcome.FAILED }
}

class CreateGroupUseCase(
    private val executor: ShellCommandExecutor,
    private val fileManager: FileManager,
    private val provisioningSource: RepositoryProvisioningSource,
    private val environment: WorktreeEnvironmentPreparer = WorktreeEnvironmentPreparer.NONE,
    private val trust: RepositoryTrust = RepositoryTrust.NONE,
    private val approval: PostCreateApproval = PostCreateApproval.SKIP,
    private val records: ProvisioningRecordStore = ProvisioningRecordStore.NONE,
) {
    suspend operator fun invoke(
        members: List<MultirepoMember>,
        taskId: String,
        branchType: BranchType,
        fetch: Boolean = false,
        onRepository: (GroupRepositoryResult) -> Unit = {},
    ): Result<GroupCreation> = runCatching {
        val id = normalizeTaskId(taskId)
        if (!isValidGitRefSegment(id)) throw WorktreeError.InvalidTaskId(taskId)
        val branch = "${branchType.prefix}/$id"
        val results = members.map { member ->
            create(member, id, branchType, branch, fetch).also(onRepository)
        }
        GroupCreation(branch, results)
    }

    private suspend fun create(
        member: MultirepoMember,
        id: String,
        branchType: BranchType,
        branch: String,
        fetch: Boolean,
    ): GroupRepositoryResult {
        val config = member.config
        return try {
            val existing = ListWorktreesUseCase(config, executor)().getOrThrow()
                .firstOrNull { !it.isMain && it.branch == branch }
            if (existing != null) return GroupRepositoryResult(member.alias, GroupRepositoryOutcome.EXISTING, existing.path)
            if (config.worktreesRoot.isBlank()) throw WorktreeError.InvalidRepositoryConfig(config.baseRepositoryPath, "the project has no worktrees folder")
            if (fetch) FetchRemoteUseCase(config, executor)().getOrThrow()
            val created = if (branchExists(config.baseRepositoryPath, branch)) {
                CreateWorktreeFromBranchUseCase(config, executor, fileManager, provisioningSource, environment, trust, approval, records)(branch)
            } else {
                val base = ResolveMainBranchUseCase(config, executor)().getOrThrow()
                    ?: throw WorktreeError.BaseBranchNotFound(config.baseRepositoryPath)
                CreateWorktreeUseCase(config, executor, fileManager, provisioningSource, environment, trust, approval, records)(id, branchType, base.ref)
            }
            val worktree = created.getOrThrow()
            GroupRepositoryResult(member.alias, GroupRepositoryOutcome.CREATED, worktree.path, worktree.skippedPostCreateCommands)
        } catch (error: WorktreeError.PostCreateCommandFailed) {
            GroupRepositoryResult(member.alias, GroupRepositoryOutcome.FAILED, error.worktreePath, error = error)
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Exception) {
            GroupRepositoryResult(member.alias, GroupRepositoryOutcome.FAILED, error = error)
        }
    }

    private suspend fun branchExists(repository: String, branch: String): Boolean =
        listOf("refs/heads/$branch", "refs/remotes/$REMOTE_PREFIX$branch").any { ref ->
            executor.execute(listOf("git", "show-ref", "--verify", "--quiet", ref), repository).isSuccess
        }
}
