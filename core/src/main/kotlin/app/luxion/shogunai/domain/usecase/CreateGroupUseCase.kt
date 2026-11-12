package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.executor.CommandTimeouts
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.FileManager
import app.luxion.shogunai.domain.io.RepositoryApproval
import app.luxion.shogunai.domain.io.ProvisioningRecordStore
import app.luxion.shogunai.domain.io.RepositoryProvisioningSource
import app.luxion.shogunai.domain.io.RepositoryTrust
import app.luxion.shogunai.domain.model.BranchType
import app.luxion.shogunai.domain.model.REMOTE_PREFIX
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.runSuspendCatching

enum class GroupRepositoryOutcome { CREATED, EXISTING, FAILED, SKIPPED }

data class GroupRepositoryResult(
    val alias: String,
    val outcome: GroupRepositoryOutcome,
    val path: String? = null,
    val skippedPostCreateCommands: List<List<String>> = emptyList(),
    val skippedVariables: List<String> = emptyList(),
    val error: Throwable? = null,
    val blockedBy: List<String> = emptyList(),
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
    private val approval: RepositoryApproval = RepositoryApproval.SKIP,
    private val records: ProvisioningRecordStore = ProvisioningRecordStore.NONE,
) {
    suspend operator fun invoke(
        members: List<MultirepoMember>,
        taskId: String,
        branchType: BranchType,
        fetch: Boolean = false,
        onRepository: (GroupRepositoryResult) -> Unit = {},
    ): Result<GroupCreation> = runSuspendCatching {
        val id = normalizeTaskId(taskId)
        if (!isValidGitRefSegment(id)) throw WorktreeError.InvalidTaskId(taskId)
        val branch = "${branchType.prefix}/$id"
        val plans = members.map { member -> plan(member, branch) }
        val unavailable = plans.mapNotNull { it.unavailable?.let { error -> it.member.alias to error } }.toMap()
        val results = plans.map { plan ->
            val settled = plan.settled
            when {
                settled != null -> settled
                unavailable.isNotEmpty() -> stopped(plan.member.alias, unavailable)
                else -> create(plan.member, id, branchType, branch, fetch)
            }.also(onRepository)
        }
        GroupCreation(branch, results)
    }

    private class MemberPlan(
        val member: MultirepoMember,
        val settled: GroupRepositoryResult? = null,
        val unavailable: WorktreeError.LocalFilesUnavailable? = null,
    )

    private suspend fun plan(member: MultirepoMember, branch: String): MemberPlan {
        val config = member.config
        val existing = ListWorktreesUseCase(config, executor)()
            .getOrElse { return MemberPlan(member, GroupRepositoryResult(member.alias, GroupRepositoryOutcome.FAILED, error = it)) }
            .firstOrNull { !it.isMain && it.branch == branch }
        if (existing != null) return MemberPlan(member, GroupRepositoryResult(member.alias, GroupRepositoryOutcome.EXISTING, existing.path))
        val files = try {
            checkLocalFiles(config, executor, fileManager, provisioningSource).unavailable
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Exception) {
            emptyList()
        }
        return MemberPlan(member, unavailable = files.takeIf { it.isNotEmpty() }?.let { WorktreeError.LocalFilesUnavailable(config.baseRepositoryPath, it) })
    }

    private fun stopped(alias: String, unavailable: Map<String, WorktreeError.LocalFilesUnavailable>): GroupRepositoryResult =
        unavailable[alias]?.let { GroupRepositoryResult(alias, GroupRepositoryOutcome.FAILED, error = it) }
            ?: GroupRepositoryResult(alias, GroupRepositoryOutcome.SKIPPED, blockedBy = unavailable.keys.toList())

    private suspend fun create(
        member: MultirepoMember,
        id: String,
        branchType: BranchType,
        branch: String,
        fetch: Boolean,
    ): GroupRepositoryResult {
        val config = member.config
        return try {
            if (config.worktreesRoot.isBlank()) throw WorktreeError.InvalidRepositoryConfig(config.baseRepositoryPath, "the project has no worktrees folder")
            if (fetch) FetchRemoteUseCase(config, executor)().getOrThrow()
            val created = if (branchExists(config.baseRepositoryPath, branch)) {
                CreateWorktreeFromBranchUseCase(config, executor, fileManager, provisioningSource, environment, trust, approval, records)(branch)
            } else {
                val base = ResolveMainBranchUseCase(config, executor)().getOrThrow()
                    ?: throw WorktreeError.BaseBranchNotFound(config.baseRepositoryPath)
                CreateWorktreeUseCase(config, executor, fileManager, provisioningSource, environment, trust, approval, records)(id, branchType, base.ref, base.name)
            }
            val worktree = created.getOrThrow()
            GroupRepositoryResult(member.alias, GroupRepositoryOutcome.CREATED, worktree.path, worktree.skippedPostCreateCommands, worktree.skippedVariables)
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
            executor.executeWithin(listOf("git", "show-ref", "--verify", "--quiet", ref), CommandTimeouts.GIT_READ, repository).isSuccess
        }
}
