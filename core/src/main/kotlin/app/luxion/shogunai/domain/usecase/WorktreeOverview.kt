package app.luxion.shogunai.domain.usecase

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

internal fun normalizePath(path: String): String = path.replace('\\', '/').trimEnd('/')

internal fun isInside(path: String, parent: String): Boolean =
    parent.isNotEmpty() && (path == parent || path.startsWith("$parent/"))
