package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.io.GitHubClient
import app.luxion.shogunai.domain.io.OrcaClient
import app.luxion.shogunai.domain.io.OrcaWorkspaceStore
import app.luxion.shogunai.domain.model.OrcaWorktree
import app.luxion.shogunai.domain.model.OrcaWorktreeUpdate
import app.luxion.shogunai.domain.model.RunningAgent
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeStatus
import kotlinx.coroutines.CancellationException
import java.util.concurrent.ConcurrentHashMap

const val ORCA_COMMENT_PREFIX = "ShogunAi: "

data class OrcaSyncItem(
    val worktree: Worktree,
    val status: WorktreeStatus?,
    val agents: List<RunningAgent>,
    val activity: Activity,
)

data class OrcaSyncFailure(val path: String, val error: Throwable)

fun orcaBoardStatus(activity: Activity): String? = when (activity) {
    Activity.New -> "todo"
    Activity.WaitingForYou, Activity.AgentWorking, Activity.Unfinished -> "in-progress"
    Activity.Stopped, Activity.Inactive -> "in-review"
    Activity.Merged -> "completed"
    Activity.Missing, Activity.Idle, Activity.Unknown -> null
}

fun orcaComment(activity: Activity, status: WorktreeStatus?, agents: List<RunningAgent>): String? {
    val base = status?.baseName ?: "its base"
    val state = when (activity) {
        Activity.WaitingForYou -> "waiting for you"
        Activity.Unfinished -> "unfinished"
        Activity.AgentWorking -> "agent working"
        Activity.New -> "new, no commits yet"
        Activity.Stopped -> "pushed, waiting"
        Activity.Merged -> "merged into $base"
        Activity.Inactive -> "inactive"
        Activity.Missing, Activity.Idle, Activity.Unknown -> return null
    }
    val details = buildList {
        if (agents.any { it.waitsForUser }) add("agent waiting")
        if (status != null) {
            if (status.uncommittedChanges > 0) add(counted(status.uncommittedChanges, "uncommitted change"))
            val upstream = status.upstream
            if (upstream != null && upstream.ahead > 0) add(counted(upstream.ahead, "commit") + " not pushed")
            if (upstream == null && status.hasOwnCommits) add("branch not pushed")
            val divergence = status.base
            if (activity != Activity.Merged && divergence != null) {
                if (divergence.ahead > 0) add("${divergence.ahead} ahead of $base")
                if (divergence.behind > 0) add("${divergence.behind} behind $base")
            }
        }
    }
    return ORCA_COMMENT_PREFIX + (listOf(state) + details).joinToString(" · ")
}

fun issueNumberOf(branch: String?): Int? {
    if (branch == null) return null
    val match = ISSUE_BRANCH.matchEntire(branch.substringAfterLast('/')) ?: return null
    return match.groupValues[1].ifEmpty { match.groupValues[2] }.toIntOrNull()
}

private val ISSUE_BRANCH = Regex("""(?:(\d+)|(?:issue|gh)-(\d+))(?:[-_].*)?""", RegexOption.IGNORE_CASE)

private fun counted(count: Int, noun: String): String = if (count == 1) "1 $noun" else "$count ${noun}s"

class OrcaWorktreeSync(
    private val orca: OrcaClient,
    private val store: OrcaWorkspaceStore,
    private val github: GitHubClient,
    private val now: () -> Long = System::currentTimeMillis,
    private val canonical: (String) -> String = ::canonicalDirectory,
) {
    private val lookedUpAt = ConcurrentHashMap<String, Long>()

    suspend fun sync(items: List<OrcaSyncItem>, rows: List<OrcaWorktree>, prune: Boolean = false): List<OrcaSyncFailure> {
        val rowsByPath = rows.filter { it.folderId == null }.associateBy { canonical(it.path) }
        val remembered = runCatching { store.boardStatuses() }.getOrDefault(emptyMap())
        val written = remembered.toMutableMap()
        val failures = mutableListOf<OrcaSyncFailure>()
        items.filterNot { it.worktree.isMain }.forEach { item ->
            val key = canonical(item.worktree.path)
            val row = rowsByPath[key] ?: return@forEach
            val boardStatus = orcaBoardStatus(item.activity) ?: return@forEach
            try {
                val update = updateFor(item, row, boardStatus, remembered[key], key)
                if (!update.isEmpty) orca.updateWorktree(row.path, update)
                written[key] = boardStatus
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                failures += OrcaSyncFailure(item.worktree.path, error)
            }
        }
        val kept = if (prune) written.filterKeys { it in rowsByPath } else written
        if (kept != remembered) runCatching { store.rememberBoardStatuses(kept) }
        return failures
    }

    private suspend fun updateFor(item: OrcaSyncItem, row: OrcaWorktree, boardStatus: String, lastWritten: String?, key: String): OrcaWorktreeUpdate {
        val comment = orcaComment(item.activity, item.status, item.agents)!!
            .takeIf { row.comment.isNullOrBlank() || row.comment.startsWith(ORCA_COMMENT_PREFIX) }
            ?.takeIf { it != row.comment }
        val (pullRequest, issue) = links(item, row, key)
        return OrcaWorktreeUpdate(
            comment = comment,
            workspaceStatus = boardStatus.takeIf { it != (lastWritten ?: row.workspaceStatus) },
            pullRequest = pullRequest,
            issue = issue,
        )
    }

    private suspend fun links(item: OrcaSyncItem, row: OrcaWorktree, key: String): Pair<Int?, Int?> {
        val branch = item.worktree.branch ?: return null to null
        val looksForPullRequest = row.linkedPullRequest == null && item.status?.upstream != null
        val issueNumber = issueNumberOf(branch).takeIf { row.linkedIssue == null }
        if (!looksForPullRequest && issueNumber == null) return null to null
        val last = lookedUpAt[key]
        if (last != null && now() - last < LOOKUP_INTERVAL_MILLIS) return null to null
        lookedUpAt[key] = now()
        val pullRequest = if (looksForPullRequest) github.pullRequestFor(item.worktree.path, branch) else null
        val issue = issueNumber?.takeIf { github.issueExists(item.worktree.path, it) }
        return pullRequest to issue
    }

    private companion object {
        const val LOOKUP_INTERVAL_MILLIS = 10 * 60 * 1000L
    }
}
