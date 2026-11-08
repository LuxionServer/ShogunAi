package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeAgentSessionStore
import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.FakeGradleDaemonSource
import app.luxion.shogunai.domain.FakeGradleDaemonSource.Companion.gradleDaemon
import app.luxion.shogunai.domain.FakeGradleDaemonSource.Companion.kotlinDaemon
import app.luxion.shogunai.domain.model.AgentSessionRecord
import app.luxion.shogunai.domain.model.AgentSessionRef
import app.luxion.shogunai.domain.model.AgentState
import app.luxion.shogunai.domain.model.DaemonOwner
import app.luxion.shogunai.domain.model.DaemonState
import app.luxion.shogunai.domain.model.GradleClient
import app.luxion.shogunai.domain.model.GradleDaemon
import app.luxion.shogunai.domain.model.HoldingBuild
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.coroutines.test.runTest
import java.time.Instant
import app.luxion.shogunai.domain.io.AgentSessionStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

class ListGradleDaemonsUseCaseTest {

    private val idle = DaemonState.Idle(Instant.parse("2026-10-16T10:57:00Z"))
    private val main = Worktree(path = "/home/dev/projects/main-repo", branch = "develop", isMain = true)
    private val nested = Worktree(path = "/home/dev/projects/main-repo/.claude/worktrees/task", branch = "worktree-task")
    private val sibling = Worktree(path = "/home/dev/projects/TASK-123", branch = "feature/TASK-123")
    private val worktrees = listOf(main, nested, sibling)

    private val existing = listOf(main.path, nested.path, sibling.path, "/home/dev/other-project")

    private fun useCase(source: FakeGradleDaemonSource) = ListGradleDaemonsUseCase(source, FakeFileManager(existing))

    @Test
    fun `attributes each gradle daemon to the worktree of its last build`() = runTest {
        val source = FakeGradleDaemonSource(
            listOf(
                gradleDaemon(1, idle, lastBuildDirectory = sibling.path),
                gradleDaemon(2, DaemonState.Busy, lastBuildDirectory = nested.path),
                gradleDaemon(3, idle, lastBuildDirectory = "/home/dev/projects/TASK-9"),
                gradleDaemon(4, idle, lastBuildDirectory = "/home/dev/other-project"),
                gradleDaemon(5, DaemonState.Unknown),
                kotlinDaemon(6),
            ),
        )

        val owners = useCase(source)(worktrees).getOrThrow().map { it.owner }

        assertEquals(
            listOf(
                DaemonOwner.Worktree(sibling.path),
                DaemonOwner.Worktree(nested.path),
                DaemonOwner.RemovedWorktree,
                DaemonOwner.OtherProject,
                null,
                null,
            ),
            owners,
        )
    }

    private val apps = "/home/dev/projects/worktrees/apps/task-123"
    private val api = "/home/dev/projects/worktrees/api/task-123"
    private val startedAt = Instant.parse("2026-10-16T19:02:00Z")

    private fun held(ancestors: List<Long>) = gradleDaemon(7, DaemonState.Busy, lastBuildDirectory = api).copy(
        holder = HoldingBuild(api, startedAt, GradleClient(81234, listOf("benchRun"), ancestors)),
    )

    private val sessions = FakeAgentSessionStore(
        mapOf(apps to listOf(AgentSessionRecord("963d9441", "claude", AgentState.WORKING, 0, pid = 700))),
    )

    @Test
    fun `a holding build started by an agent session names that session and its worktree`() = runTest {
        val useCase = ListGradleDaemonsUseCase(FakeGradleDaemonSource(listOf(held(listOf(81200, 700, 500)))), FakeFileManager(existing), sessions)

        val client = useCase(worktrees) { listOf(api, apps) }.getOrThrow().single().process.holder?.client

        assertEquals(AgentSessionRef("963d9441", "claude", apps), client?.session)
        assertEquals(listOf("benchRun"), client?.tasks)
    }

    @Test
    fun `sessions are looked up in the given worktrees by default`() = runTest {
        val useCase = ListGradleDaemonsUseCase(FakeGradleDaemonSource(listOf(held(listOf(81200, 700, 500)))), FakeFileManager(existing), sessions)

        val client = useCase(worktrees + Worktree(path = apps, branch = "feature/TASK-123")).getOrThrow().single().process.holder?.client

        assertEquals(AgentSessionRef("963d9441", "claude", apps), client?.session)
    }

    @Test
    fun `a holding build from a terminal has no session`() = runTest {
        val useCase = ListGradleDaemonsUseCase(FakeGradleDaemonSource(listOf(held(listOf(81200, 500)))), FakeFileManager(existing), sessions)

        val holder = useCase(worktrees) { listOf(api, apps) }.getOrThrow().single().process.holder

        assertEquals(HoldingBuild(api, startedAt, GradleClient(81234, listOf("benchRun"), listOf(81200, 500))), holder)
    }

    @Test
    fun `sessions are not read without a traceable client`() = runTest {
        var asked = false
        val daemon = gradleDaemon(7, DaemonState.Busy).copy(holder = HoldingBuild(api, startedAt, null))
        val useCase = ListGradleDaemonsUseCase(FakeGradleDaemonSource(listOf(daemon)), FakeFileManager(existing), sessions)

        val holder = useCase(worktrees) { asked = true; listOf(apps) }.getOrThrow().single().process.holder

        assertEquals(HoldingBuild(api, startedAt, null), holder)
        assertFalse(asked)
    }

    @Test
    fun `leaves daemons unattributed outside a repository`() = runTest {
        val source = FakeGradleDaemonSource(listOf(gradleDaemon(1, idle, lastBuildDirectory = sibling.path)))

        assertNull(useCase(source)(null).getOrThrow().single().owner)
    }

    @Test
    fun `fails when processes can't be listed`() = runTest {
        val source = FakeGradleDaemonSource(failure = WorktreeError.ProcessListingFailed(listOf("ps"), 1))

        assertIs<WorktreeError.ProcessListingFailed>(useCase(source)(worktrees).exceptionOrNull())
    }

    @Test
    fun `selects the idle gradle daemons of removed worktrees`() {
        val daemons = listOf(
            GradleDaemon(gradleDaemon(1, idle, lastBuildDirectory = sibling.path), null),
            GradleDaemon(gradleDaemon(2, DaemonState.Busy, lastBuildDirectory = sibling.path), null),
            GradleDaemon(gradleDaemon(3, idle, lastBuildDirectory = nested.path), null),
            GradleDaemon(gradleDaemon(4, idle, lastBuildDirectory = main.path), null),
            GradleDaemon(kotlinDaemon(5), null),
        )

        val pids = idleDaemonsOfRemovedWorktrees(daemons, worktrees, listOf(sibling.path, nested.path))

        assertEquals(listOf(1L, 3L), pids)
    }

    @Test
    fun `total memory adds the resident memory of every daemon`() {
        val daemons = listOf(GradleDaemon(kotlinDaemon(1, memoryBytes = 300L), null), GradleDaemon(kotlinDaemon(2, memoryBytes = 200L), null))

        assertEquals(500L, totalResidentMemory(daemons))
    }
    @Test
    fun `sessions are skipped without a store, worktrees, a readable store or a process id`() = runTest {
        val source = FakeGradleDaemonSource(listOf(held(listOf(81200, 700, 500))))
        val unreadable = object : AgentSessionStore by FakeAgentSessionStore() {
            override fun list(worktreePath: String): List<AgentSessionRecord> = throw IllegalStateException("unreadable")
        }
        val withoutPid = FakeAgentSessionStore(mapOf(apps to listOf(AgentSessionRecord("963d9441", "claude", AgentState.WORKING, 0))))

        val clients = listOf(
            ListGradleDaemonsUseCase(source, FakeFileManager(existing))(worktrees) { listOf(apps) },
            ListGradleDaemonsUseCase(source, FakeFileManager(existing), sessions)(null),
            ListGradleDaemonsUseCase(source, FakeFileManager(existing), unreadable)(worktrees) { listOf(apps) },
            ListGradleDaemonsUseCase(source, FakeFileManager(existing), withoutPid)(worktrees) { listOf(apps) },
        ).map { it.getOrThrow().single().process.holder?.client?.session }

        assertEquals(listOf<AgentSessionRef?>(null, null, null, null), clients)
    }

    @Test
    fun `idle daemons without a build folder or outside every worktree aren't selected`() {
        val daemons = listOf(
            GradleDaemon(gradleDaemon(1, idle), null),
            GradleDaemon(gradleDaemon(2, idle, lastBuildDirectory = "/home/dev/other-project"), null),
        )

        assertEquals(emptyList(), idleDaemonsOfRemovedWorktrees(daemons, worktrees, listOf(sibling.path)))
    }
}
