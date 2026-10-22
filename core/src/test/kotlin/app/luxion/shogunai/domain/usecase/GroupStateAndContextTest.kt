package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.FakeHerdrClient
import app.luxion.shogunai.domain.FakeRepositoryProvisioningSource
import app.luxion.shogunai.domain.FakeEnvironmentStateStore
import app.luxion.shogunai.domain.FakePortProbe
import app.luxion.shogunai.domain.model.AgentLaunchConfig
import app.luxion.shogunai.domain.model.HerdrOpening
import app.luxion.shogunai.domain.model.HerdrPane
import app.luxion.shogunai.domain.model.HerdrWorkspace
import app.luxion.shogunai.domain.model.Multirepo
import app.luxion.shogunai.domain.model.RunningAgent
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GroupStateAndContextTest {

    private val world = MultirepoTestWorld()
    private val appTask = "${world.worktreesRoot}/app/TASK-1"
    private val apiTask = "${world.worktreesRoot}/api/TASK-1"

    init {
        with(world.git) {
            world.app.worktree(appTask, "feature/TASK-1", merged = true)
            world.api.worktree(apiTask, "feature/TASK-1", merged = true)
            world.api.worktree("${world.worktreesRoot}/api/loose", "feature/LOOSE")
        }
    }

    private fun readGroups(agents: Map<String, List<RunningAgent>> = emptyMap()) = ReadGroupsUseCase(
        DetectGroupsUseCase(world.git),
        resolveBase = { ResolveMainBranchUseCase(it.config, world.git)().getOrNull() },
        readStatus = { member, base -> ReadWorktreeStatusUseCase(world.git, FakeFileManager())(member.worktree, base) },
        detectAgents = { Result.success(agents) },
    )

    @Test
    fun `status reads each worktree against its own base and lists the ungrouped ones`() = runTest {
        val state = readGroups()(world.members())

        val group = state.groups.single()
        assertEquals(listOf("develop", "develop"), group.worktrees.map { it.base?.name })
        assertTrue(group.worktrees.all { it.status?.getOrNull()?.isMerged == true })
        assertEquals(listOf("${world.worktreesRoot}/api/loose"), state.ungrouped.map { it.member.worktree.path })
    }

    @Test
    fun `a group is a cleanup candidate only when every worktree is merged and has no agent`() = runTest {
        assertEquals(1, selectGroupCleanup(readGroups()(world.members()).groups).candidates.size)

        world.api.merged.remove(apiTask)
        val notMerged = selectGroupCleanup(readGroups()(world.members()).groups)
        assertEquals(GroupKeptReason.NOT_MERGED, notMerged.kept.single().reason)
        assertEquals("api", notMerged.kept.single().alias)

        world.api.merged += apiTask
        val withAgent = selectGroupCleanup(readGroups(mapOf(apiTask to listOf(RunningAgent("claude"))))(world.members()).groups)
        assertEquals(GroupKeptReason.AGENT_RUNNING, withAgent.kept.single().reason)

        world.api.dirty[apiTask] = 2
        assertEquals(GroupKeptReason.UNCOMMITTED_CHANGES, selectGroupCleanup(readGroups()(world.members()).groups).kept.single().reason)
    }

    @Test
    fun `the group context names the other repositories, the missing ones and where OpenSpec lives`() = runTest {
        val fileManager = FakeFileManager(existing = listOf("$appTask/openspec"))
        val group = DetectGroupsUseCase(world.git)(world.members()).groups.single()

        val lines = GroupContext(fileManager).groupLines(world.multirepo, group, apiTask)

        assertEquals("ShogunAi multirepo product: this worktree is api in the group feature/TASK-1.", lines[0])
        assertEquals("Other repositories of the group: app at $appTask.", lines[1])
        assertTrue(lines[2].contains("docs") && lines[2].contains("shogun new TASK-1 --repos docs"))
        assertEquals("OpenSpec changes are planned in $appTask/AGENTS.md; read it before planning.", lines[3])
    }

    @Test
    fun `OpenSpec in a repository outside the group points to its main checkout`() = runTest {
        val fileManager = FakeFileManager(existing = listOf("${world.docsBase}/openspec"))
        val group = DetectGroupsUseCase(world.git)(world.members()).groups.single()

        val lines = GroupContext(fileManager).groupLines(world.multirepo, group, apiTask)

        assertTrue(lines.last().contains("${world.docsBase}/AGENTS.md"))
    }

    @Test
    fun `the multirepo context lists the repositories, the shared instructions and the commands`() {
        val lines = GroupContext(FakeFileManager(existing = listOf("${world.folder}/AGENTS.md"))).multirepoLines(world.multirepo)

        assertTrue(lines[0].contains("app (${world.appBase})"))
        assertEquals("Read ${world.folder}/AGENTS.md for what all the repositories share.", lines[1])
        assertTrue(lines[2].contains("shogun status") && lines[2].contains("in app, api") && lines[2].contains("shogun open <id> --herdr"))
        assertEquals(2, GroupContext(FakeFileManager()).multirepoLines(world.multirepo).size)
    }

    @Test
    fun `a group is found by branch or by id when only one prefix matches`() = runTest {
        with(world.git) {
            world.app.worktree("/w/app-fix-1", "fix/TASK-1")
            world.docs.worktree("/w/docs-fix-1", "fix/TASK-1")
        }
        val worktrees = DetectGroupsUseCase(world.git)(world.members())

        assertEquals("feature/TASK-1", (findGroup(worktrees, "feature/TASK-1") as GroupTarget.Found).group.branch)
        assertEquals(listOf("feature/TASK-1", "fix/TASK-1"), (findGroup(worktrees, "TASK-1") as GroupTarget.Ambiguous).branches)
        assertEquals(GroupTarget.Missing, findGroup(worktrees, "TASK-9"))
    }

    @Test
    fun `opening a group opens each worktree under its repository and keeps going after a failure`() = runTest {
        val herdr = FakeHerdrClient(openFailures = mapOf(appTask to WorktreeError.HerdrNotInstalled))
        val resolveEnvironment = ResolveWorktreeEnvironmentUseCase(world.git, FakeRepositoryProvisioningSource(), FakeEnvironmentStateStore(), FakePortProbe())
        val group = DetectGroupsUseCase(world.git)(world.members()).groups.single()

        val openings = OpenGroupInHerdrUseCase(herdr, resolveEnvironment)(group)

        assertIs<WorktreeError.HerdrNotInstalled>(openings[0].result.exceptionOrNull())
        assertEquals(HerdrOpening.OPENED, openings[1].result.getOrThrow())
        assertEquals(listOf(Triple(world.apiBase, apiTask, "TASK-1")), herdr.opened)
    }

    @Test
    fun `the multirepo agent opens in a plain workspace or focuses the open one`() = runTest {
        val multirepo = Multirepo(world.folder, world.declaration.copy(agent = AgentLaunchConfig(agentCommand = "claude", useHeadroom = false)))
        val fresh = FakeHerdrClient()

        assertEquals(HerdrOpening.OPENED, OpenMultirepoAgentInHerdrUseCase(fresh)(multirepo).getOrThrow())
        assertEquals(listOf(world.folder to "product"), fresh.createdWorkspaces)
        assertEquals(listOf("plain1:p1" to "claude"), fresh.ran)

        val open = FakeHerdrClient(
            workspaces = listOf(HerdrWorkspace("w5", "product", null), HerdrWorkspace("w6", "app", world.folder)),
            panes = listOf(HerdrPane("w6:p1", "w6", world.folder), HerdrPane("w5:p1", "w5", world.folder)),
        )
        assertEquals(HerdrOpening.FOCUSED, OpenMultirepoAgentInHerdrUseCase(open)(multirepo).getOrThrow())
        assertEquals(listOf("w5"), open.focusedWorkspaces)
        assertTrue(open.createdWorkspaces.isEmpty() && open.ran.isEmpty())
    }
}
