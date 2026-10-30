package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.model.AgentState
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.RunningAgent
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeOrigin
import app.luxion.shogunai.domain.model.WorktreeStatus
import java.time.Duration
import java.time.Instant

val DEFAULT_INACTIVITY_THRESHOLD: Duration = Duration.ofDays(3)

private const val CLAUDE_CODE_WORKTREES = "/.claude/worktrees/"

fun classifyOrigin(
    worktree: Worktree,
    config: ProjectConfig,
    herdrWorktreesRoot: String? = null,
    orca: OrcaOrigins? = null,
): WorktreeOrigin? {
    if (worktree.isMain) return null
    val path = normalizePath(worktree.path)
    return when {
        CLAUDE_CODE_WORKTREES in "$path/" -> WorktreeOrigin.CLAUDE_CODE
        herdrWorktreesRoot != null && isInside(path, normalizePath(herdrWorktreesRoot)) -> WorktreeOrigin.HERDR
        orca != null && orca.createdByOrca(worktree.path) -> WorktreeOrigin.ORCA
        isInside(path, normalizePath(config.worktreesRoot)) -> WorktreeOrigin.SHOGUNAI
        else -> WorktreeOrigin.OTHER
    }
}

enum class ActivityKind { COMMIT, CREATED }

data class ActivityDate(val at: Instant, val kind: ActivityKind)

fun activityDate(worktree: Worktree, status: WorktreeStatus?): ActivityDate? {
    if (status == null) return null
    val commit = status.lastCommitAt?.let { ActivityDate(it, ActivityKind.COMMIT) }
    if (worktree.isMain || status.hasOwnCommits) return commit
    return status.createdAt?.let { ActivityDate(it, ActivityKind.CREATED) } ?: commit
}

fun isInactive(
    worktree: Worktree,
    status: WorktreeStatus?,
    agents: List<RunningAgent>?,
    now: Instant,
    inactiveAfter: Duration,
): Boolean {
    if (worktree.isMain || status == null || !status.hasOwnCommits) return false
    val lastActivity = activityDate(worktree, status) ?: return false
    return status.uncommittedChanges == 0 &&
        agents.isNullOrEmpty() &&
        Duration.between(lastActivity.at, now) > inactiveAfter
}

enum class Activity { NeedsYou, AgentWorking, New, Stopped, Merged, Inactive, Missing, Idle, Unknown }

val Activity.isActive: Boolean
    get() = this == Activity.NeedsYou || this == Activity.AgentWorking || this == Activity.New || this == Activity.Stopped

fun worktreeActivity(
    worktree: Worktree,
    status: WorktreeStatus?,
    agents: List<RunningAgent>?,
    now: Instant,
    inactiveAfter: Duration = DEFAULT_INACTIVITY_THRESHOLD,
): Activity {
    if (worktree.isPrunable) return Activity.Missing
    val deciding = agents.orEmpty().filter { it.state != AgentState.IDLE }
    if (deciding.any { it.waitsForUser }) return Activity.NeedsYou
    if (deciding.isNotEmpty()) return Activity.AgentWorking
    if (status == null) return Activity.Unknown
    return when {
        status.uncommittedChanges > 0 || hasUnpushedCommits(status) -> Activity.NeedsYou
        worktree.isMain -> Activity.Idle
        !status.hasOwnCommits && !status.isMerged -> Activity.New
        status.isMerged -> Activity.Merged
        isInactive(worktree, status, agents, now, inactiveAfter) -> Activity.Inactive
        else -> Activity.Stopped
    }
}

val RunningAgent.waitsForUser: Boolean
    get() = state == AgentState.BLOCKED || state == AgentState.DONE

private fun hasUnpushedCommits(status: WorktreeStatus): Boolean =
    if (status.upstream != null) {
        status.upstream.ahead > 0
    } else {
        status.base != null && status.base.ahead > 0
    }

internal fun normalizePath(path: String): String = path.replace('\\', '/').trimEnd('/')

internal fun isInside(path: String, parent: String): Boolean =
    parent.isNotEmpty() && (path == parent || path.startsWith("$parent/"))
