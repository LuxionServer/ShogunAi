package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeGitHubClient
import app.luxion.shogunai.domain.FakeOrcaClient
import app.luxion.shogunai.domain.io.OrcaWorkspaceStore
import app.luxion.shogunai.domain.model.AgentState
import app.luxion.shogunai.domain.model.Divergence
import app.luxion.shogunai.domain.model.OrcaWorktree
import app.luxion.shogunai.domain.model.OrcaWorktreeUpdate
import app.luxion.shogunai.domain.model.RunningAgent
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.model.WorktreeStatus
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OrcaWorktreeSyncTest {

    private val path = "/projects/main-repo-worktrees/TASK-1"
    private val worktree = Worktree(path, "feature/TASK-1")

    private class MemoryStore(var statuses: Map<String, String> = emptyMap()) : OrcaWorkspaceStore {
        override fun workspacesRoot(): String? = null
        override fun remember(workspacesRoot: String) = Unit
        override fun boardStatuses(): Map<String, String> = statuses
        override fun rememberBoardStatuses(statuses: Map<String, String>) {
            this.statuses = statuses
        }
    }

    private fun status(
        uncommitted: Int = 0,
        upstream: Divergence? = Divergence(0, 0),
        base: Divergence? = Divergence(2, 0),
    ) = WorktreeStatus(uncommitted, upstream, mainBranch = base, lastCommitAt = null, base = base, baseName = "develop")

    private fun row(
        comment: String? = "",
        workspaceStatus: String? = "in-progress",
        pullRequest: Int? = null,
        issue: Int? = null,
        rowPath: String = path,
    ) = OrcaWorktree(rowPath, createdByOrca = false, comment = comment, workspaceStatus = workspaceStatus, linkedPullRequest = pullRequest, linkedIssue = issue)

    private fun item(activity: Activity, status: WorktreeStatus? = status(), agents: List<RunningAgent> = emptyList(), target: Worktree = worktree) =
        OrcaSyncItem(target, status, agents, activity)

    private fun sync(
        orca: FakeOrcaClient = FakeOrcaClient(),
        store: MemoryStore = MemoryStore(),
        github: FakeGitHubClient = FakeGitHubClient(),
        clock: () -> Long = { 0L },
    ) = OrcaWorktreeSync(orca, store, github, clock, canonical = { it })

    @Test
    fun `every state maps to its board column`() {
        assertEquals("todo", orcaBoardStatus(Activity.New))
        assertEquals("in-progress", orcaBoardStatus(Activity.AgentWorking))
        assertEquals("in-progress", orcaBoardStatus(Activity.WaitingForYou))
        assertEquals("in-progress", orcaBoardStatus(Activity.Unfinished))
        assertEquals("in-review", orcaBoardStatus(Activity.Stopped))
        assertEquals("in-review", orcaBoardStatus(Activity.Inactive))
        assertEquals("completed", orcaBoardStatus(Activity.Merged))
        assertNull(orcaBoardStatus(Activity.Missing))
        assertNull(orcaBoardStatus(Activity.Idle))
        assertNull(orcaBoardStatus(Activity.Unknown))
    }

    @Test
    fun `the comment summarises what needs attention`() {
        val comment = orcaComment(Activity.Unfinished, status(uncommitted = 3, upstream = Divergence(2, 0)), emptyList())

        assertEquals("ShogunAi: unfinished · 3 uncommitted changes · 2 commits not pushed · 2 ahead of develop", comment)
    }

    @Test
    fun `the comment uses the singular and names a waiting agent and an unpushed branch`() {
        val blocked = RunningAgent("claude", AgentState.BLOCKED)
        val comment = orcaComment(Activity.WaitingForYou, status(uncommitted = 1, upstream = null, base = Divergence(1, 1)), listOf(blocked))

        assertEquals("ShogunAi: waiting for you · agent waiting · 1 uncommitted change · branch not pushed · 1 ahead of develop · 1 behind develop", comment)
    }

    @Test
    fun `a merged worktree names its base and nothing else`() {
        assertEquals("ShogunAi: merged into develop", orcaComment(Activity.Merged, status(base = Divergence(0, 4)), emptyList()))
    }

    @Test
    fun `the other states have their own wording`() {
        assertEquals("ShogunAi: new, no commits yet", orcaComment(Activity.New, status(base = Divergence(0, 0)), emptyList()))
        assertEquals("ShogunAi: pushed, waiting · 2 ahead of develop", orcaComment(Activity.Stopped, status(), emptyList()))
        assertEquals("ShogunAi: inactive · 2 ahead of develop", orcaComment(Activity.Inactive, status(), emptyList()))
        assertEquals("ShogunAi: agent working · 2 ahead of develop", orcaComment(Activity.AgentWorking, status(), listOf(RunningAgent("claude", AgentState.WORKING))))
        assertNull(orcaComment(Activity.Missing, null, emptyList()))
    }

    @Test
    fun `issue numbers come from the branch's last segment`() {
        assertEquals(123, issueNumberOf("feature/123-login"))
        assertEquals(123, issueNumberOf("fix/123"))
        assertEquals(123, issueNumberOf("feature/issue-123"))
        assertEquals(123, issueNumberOf("feature/GH-123_login"))
        assertNull(issueNumberOf("feature/TASK-123"))
        assertNull(issueNumberOf("feature/login-123"))
        assertNull(issueNumberOf(null))
    }

    @Test
    fun `a new worktree goes to todo with its summary`() = runTest {
        val orca = FakeOrcaClient()

        sync(orca).sync(listOf(item(Activity.New, status(base = Divergence(0, 0)))), listOf(row()))

        assertEquals(listOf(path to OrcaWorktreeUpdate(comment = "ShogunAi: new, no commits yet", workspaceStatus = "todo")), orca.updates)
    }

    @Test
    fun `a merged worktree goes to completed`() = runTest {
        val orca = FakeOrcaClient()

        sync(orca).sync(listOf(item(Activity.Merged, status(base = Divergence(0, 3)))), listOf(row(comment = "ShogunAi: pushed, waiting")))

        assertEquals(OrcaWorktreeUpdate(comment = "ShogunAi: merged into develop", workspaceStatus = "completed"), orca.updates.single().second)
    }

    @Test
    fun `a pushed worktree goes to in-review`() = runTest {
        val orca = FakeOrcaClient()

        sync(orca).sync(listOf(item(Activity.Stopped)), listOf(row()))

        assertEquals("in-review", orca.updates.single().second.workspaceStatus)
    }

    @Test
    fun `the main worktree, a missing folder and an unread state are left alone`() = runTest {
        val orca = FakeOrcaClient()
        val main = Worktree("/projects/main-repo", "develop", isMain = true)

        sync(orca).sync(
            listOf(item(Activity.Idle, target = main), item(Activity.Missing), item(Activity.Unknown, status = null)),
            listOf(row(rowPath = main.path), row()),
        )

        assertTrue(orca.updates.isEmpty())
    }

    @Test
    fun `a worktree Orca doesn't list is skipped`() = runTest {
        val orca = FakeOrcaClient()

        sync(orca).sync(listOf(item(Activity.New)), listOf(row(rowPath = "/projects/other")))

        assertTrue(orca.updates.isEmpty())
    }

    @Test
    fun `a card moved by hand stays until the state changes`() = runTest {
        val orca = FakeOrcaClient()
        val store = MemoryStore(mapOf(path to "in-review"))
        val stopped = item(Activity.Stopped)

        sync(orca, store).sync(listOf(stopped), listOf(row(comment = orcaComment(Activity.Stopped, stopped.status, emptyList()), workspaceStatus = "todo")))
        assertTrue(orca.updates.isEmpty())

        sync(orca, store).sync(listOf(item(Activity.Merged)), listOf(row(comment = "ShogunAi: pushed, waiting", workspaceStatus = "todo")))
        assertEquals("completed", orca.updates.single().second.workspaceStatus)
        assertEquals(mapOf(path to "completed"), store.statuses)
    }

    @Test
    fun `the first time, a status Orca already shows isn't rewritten but is remembered`() = runTest {
        val orca = FakeOrcaClient()
        val store = MemoryStore()

        sync(orca, store).sync(listOf(item(Activity.Stopped)), listOf(row(comment = "ShogunAi: pushed, waiting · 2 ahead of develop", workspaceStatus = "in-review")))

        assertTrue(orca.updates.isEmpty())
        assertEquals(mapOf(path to "in-review"), store.statuses)
    }

    @Test
    fun `a comment written in Orca is kept`() = runTest {
        val orca = FakeOrcaClient()

        sync(orca).sync(listOf(item(Activity.Stopped)), listOf(row(comment = "waiting for design review")))

        assertNull(orca.updates.single().second.comment)
    }

    @Test
    fun `an unchanged summary isn't sent again`() = runTest {
        val orca = FakeOrcaClient()

        sync(orca, MemoryStore(mapOf(path to "in-review"))).sync(
            listOf(item(Activity.Stopped)),
            listOf(row(comment = "ShogunAi: pushed, waiting · 2 ahead of develop", workspaceStatus = "in-review")),
        )

        assertTrue(orca.updates.isEmpty())
    }

    @Test
    fun `a pushed branch's pull request is linked`() = runTest {
        val orca = FakeOrcaClient()
        val github = FakeGitHubClient(pullRequests = mapOf("feature/TASK-1" to 42))

        sync(orca, github = github).sync(listOf(item(Activity.Stopped)), listOf(row()))

        assertEquals(42, orca.updates.single().second.pullRequest)
        assertEquals(listOf(path to "feature/TASK-1"), github.pullRequestLookups)
    }

    @Test
    fun `an existing pull request link is kept without asking GitHub`() = runTest {
        val orca = FakeOrcaClient()
        val github = FakeGitHubClient(pullRequests = mapOf("feature/TASK-1" to 42))

        sync(orca, github = github).sync(listOf(item(Activity.Stopped)), listOf(row(pullRequest = 7)))

        assertNull(orca.updates.single().second.pullRequest)
        assertTrue(github.pullRequestLookups.isEmpty())
    }

    @Test
    fun `an unpushed branch isn't looked up`() = runTest {
        val github = FakeGitHubClient(pullRequests = mapOf("feature/TASK-1" to 42))

        sync(github = github).sync(listOf(item(Activity.Unfinished, status(upstream = null))), listOf(row()))

        assertTrue(github.pullRequestLookups.isEmpty())
    }

    @Test
    fun `without GitHub the status and comment are still synced`() = runTest {
        val orca = FakeOrcaClient()

        sync(orca).sync(listOf(item(Activity.Stopped)), listOf(row()))

        val update = orca.updates.single().second
        assertNull(update.pullRequest)
        assertEquals("in-review", update.workspaceStatus)
        assertEquals("ShogunAi: pushed, waiting · 2 ahead of develop", update.comment)
    }

    @Test
    fun `a branch named after an existing issue links it`() = runTest {
        val orca = FakeOrcaClient()
        val github = FakeGitHubClient(issues = setOf(123))
        val issueWorktree = Worktree(path, "feature/123-login")

        sync(orca, github = github).sync(listOf(item(Activity.Unfinished, status(upstream = null), target = issueWorktree)), listOf(row()))

        assertEquals(123, orca.updates.single().second.issue)
    }

    @Test
    fun `an issue that doesn't exist, a linked issue or a task id link nothing`() = runTest {
        val orca = FakeOrcaClient()
        val github = FakeGitHubClient(issues = setOf(7))

        sync(orca, github = github).sync(listOf(item(Activity.Unfinished, status(upstream = null), target = Worktree(path, "feature/123-login"))), listOf(row()))
        sync(orca, github = github).sync(listOf(item(Activity.Unfinished, status(upstream = null), target = Worktree(path, "feature/7-login"))), listOf(row(issue = 3)))
        sync(orca, github = github).sync(listOf(item(Activity.Unfinished, status(upstream = null), target = Worktree(path, "feature/TASK-123"))), listOf(row()))

        assertTrue(orca.updates.all { it.second.issue == null })
        assertEquals(listOf(path to 123), github.issueLookups)
    }

    @Test
    fun `GitHub is asked at most once every ten minutes per worktree`() = runTest {
        var now = 0L
        val github = FakeGitHubClient()
        val sync = sync(github = github, clock = { now })

        repeat(3) { sync.sync(listOf(item(Activity.Stopped)), listOf(row())) }
        now = 9 * 60 * 1000L
        sync.sync(listOf(item(Activity.Stopped)), listOf(row()))
        now = 10 * 60 * 1000L
        sync.sync(listOf(item(Activity.Stopped)), listOf(row()))

        assertEquals(2, github.pullRequestLookups.size)
    }

    @Test
    fun `a failed update is reported and doesn't stop the others`() = runTest {
        val other = "/projects/main-repo-worktrees/TASK-2"
        val orca = FakeOrcaClient(updateFailures = mapOf(path to WorktreeError.OrcaCommandFailed(listOf("orca"), "selector_not_found")))
        val store = MemoryStore()

        val failures = sync(orca, store).sync(
            listOf(item(Activity.New), item(Activity.New, target = Worktree(other, "feature/TASK-2"))),
            listOf(row(), row(rowPath = other)),
        )

        assertEquals(listOf(path), failures.map { it.path })
        assertEquals(listOf(other), orca.updates.map { it.first })
        assertEquals(mapOf(other to "todo"), store.statuses)
    }

    @Test
    fun `a full sync forgets worktrees Orca no longer lists`() = runTest {
        val store = MemoryStore(mapOf("/projects/gone" to "todo"))

        sync(store = store).sync(listOf(item(Activity.Stopped)), listOf(row()), prune = true)

        assertEquals(mapOf(path to "in-review"), store.statuses)
    }

    @Test
    fun `a single-worktree sync keeps the others remembered`() = runTest {
        val store = MemoryStore(mapOf("/projects/elsewhere" to "todo"))

        sync(store = store).sync(listOf(item(Activity.Stopped)), listOf(row()))

        assertEquals(mapOf("/projects/elsewhere" to "todo", path to "in-review"), store.statuses)
    }
}
