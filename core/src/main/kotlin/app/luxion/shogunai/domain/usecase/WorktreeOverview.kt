package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.model.HerdrAgentState
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.RunningAgent
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeOrigin
import app.luxion.shogunai.domain.model.WorktreeStatus
import java.time.Duration
import java.time.Instant

val INACTIVITY_THRESHOLD: Duration = Duration.ofDays(14)

private const val CLAUDE_CODE_WORKTREES = "/.claude/worktrees/"

fun classifyOrigin(worktree: Worktree, config: ProjectConfig, herdrWorktreesRoot: String? = null): WorktreeOrigin? {
    if (worktree.isMain) return null
    val path = normalizePath(worktree.path)
    return when {
        CLAUDE_CODE_WORKTREES in "$path/" -> WorktreeOrigin.CLAUDE_CODE
        herdrWorktreesRoot != null && isInside(path, normalizePath(herdrWorktreesRoot)) -> WorktreeOrigin.HERDR
        isInside(path, normalizePath(config.worktreesRoot)) -> WorktreeOrigin.SHOGUNAI
        else -> WorktreeOrigin.OTHER
    }
}

fun isInactive(worktree: Worktree, status: WorktreeStatus?, agents: List<RunningAgent>?, now: Instant): Boolean {
    if (worktree.isMain || status == null) return false
    val lastCommitAt = status.lastCommitAt ?: return false
    return status.uncommittedChanges == 0 &&
        agents.isNullOrEmpty() &&
        Duration.between(lastCommitAt, now) > INACTIVITY_THRESHOLD
}

enum class Activity { AgentWorking, NeedsYou, InProgress, Merged, Inactive, Missing, Idle, Unknown }

val Activity.isActive: Boolean
    get() = this == Activity.AgentWorking || this == Activity.NeedsYou || this == Activity.InProgress

fun worktreeActivity(worktree: Worktree, status: WorktreeStatus?, agents: List<RunningAgent>?, now: Instant): Activity {
    if (!agents.isNullOrEmpty()) return if (agents.any { it.waitsForUser }) Activity.NeedsYou else Activity.AgentWorking
    if (worktree.isPrunable) return Activity.Missing
    if (status == null) return Activity.Unknown
    if (status.uncommittedChanges > 0) return Activity.NeedsYou
    return when {
        worktree.isMain -> Activity.Idle
        status.isNotStarted -> Activity.InProgress
        status.isMerged -> Activity.Merged
        isInactive(worktree, status, agents, now) -> Activity.Inactive
        hasUnpushedCommits(status) -> Activity.NeedsYou
        else -> Activity.InProgress
    }
}

val RunningAgent.waitsForUser: Boolean
    get() = herdrState == HerdrAgentState.BLOCKED || herdrState == HerdrAgentState.DONE

private fun hasUnpushedCommits(status: WorktreeStatus): Boolean =
    if (status.upstream != null) {
        status.upstream.ahead > 0
    } else {
        (status.mainBranch?.ahead ?: 0) > 0
    }

internal fun normalizePath(path: String): String = path.replace('\\', '/').trimEnd('/')

internal fun isInside(path: String, parent: String): Boolean =
    parent.isNotEmpty() && (path == parent || path.startsWith("$parent/"))
