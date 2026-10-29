package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeClaudeSessionRegistry
import app.luxion.shogunai.domain.FakeRepositoryTrust
import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.FakeHerdrClient
import app.luxion.shogunai.domain.FakeRepositoryProvisioningSource
import app.luxion.shogunai.domain.FakeEnvironmentStateStore
import app.luxion.shogunai.domain.FakePortProbe
import app.luxion.shogunai.domain.model.AgentLaunchConfig
import app.luxion.shogunai.domain.model.ClaudeSession
import app.luxion.shogunai.domain.model.HerdrOpening
import app.luxion.shogunai.domain.model.HerdrOutcome
import app.luxion.shogunai.domain.model.HerdrPane
import app.luxion.shogunai.domain.model.HerdrWorkspace
import app.luxion.shogunai.domain.model.Multirepo
import app.luxion.shogunai.domain.model.RunnerId
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
        resolveBases = { RepositoryBases(ResolveMainBranchUseCase(it.config, world.git)().getOrNull(), ResolveRecordedBasesUseCase(it.config, world.git)()) },
        readStatus = { member, mainBranch, base -> ReadWorktreeStatusUseCase(world.git, FakeFileManager())(member.worktree, mainBranch, base) },
        detectAgents = { Result.success(agents) },
    )

    @Test
    fun `a group branched from an epic reads each repository against its own base`() = runTest {
        val appEpicTask = "${world.worktreesRoot}/app/TASK-3"
        val apiEpicTask = "${world.worktreesRoot}/api/TASK-3"
        with(world.git) {
            world.app.worktree(appEpicTask, "feature/TASK-3")
            world.api.worktree(apiEpicTask, "feature/TASK-3")
        }
        world.app.remoteBranches += "epic/redesign"
        world.app.recordedBases["feature/TASK-3"] = "epic/redesign"
        world.app.mergedOnlyInto[appEpicTask] = "origin/epic/redesign"
        world.api.mergedOnlyInto[apiEpicTask] = "origin/develop"

        val group = readGroups()(world.members()).groups.single { it.group.branch == "feature/TASK-3" }
        val app = group.worktrees.single { it.member.alias == "app" }
        val api = group.worktrees.single { it.member.alias == "api" }

        assertEquals("epic/redesign", app.base?.name)
        assertEquals("epic/redesign", app.status?.getOrThrow()?.baseName)
        assertTrue(app.isCleanAndMerged)
        assertEquals("develop", api.base?.name)
        assertTrue(api.isCleanAndMerged)
        assertTrue(selectGroupCleanup(listOf(group)).candidates.isNotEmpty())
    }

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
    fun `the multirepo context uses the declared name`() {
        val named = world.multirepo.copy(declaration = world.declaration.copy(name = "Product Suite"))

        val lines = GroupContext(FakeFileManager()).multirepoLines(named)

        assertTrue(lines[0].startsWith("ShogunAi multirepo Product Suite at ${world.folder},"))
        assertTrue(GroupContext(FakeFileManager()).multirepoLines(world.multirepo)[0].startsWith("ShogunAi multirepo product at"))
    }

    @Test
    fun `the multirepo context names the runner in use`() {
        assertTrue("`shogun open <id> --herdr`" in GroupContext(FakeFileManager()).multirepoLines(world.multirepo).last())
        val withOrca = GroupContext(FakeFileManager()).multirepoLines(world.multirepo, RunnerId.ORCA).last()
        assertTrue("`shogun open <id> --orca`" in withOrca, withOrca)
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
        val resolveEnvironment = ResolveWorktreeEnvironmentUseCase(world.git, FakeRepositoryProvisioningSource(), FakeEnvironmentStateStore(), FakePortProbe(), trust = FakeRepositoryTrust(approvesEverything = true))
        val group = DetectGroupsUseCase(world.git)(world.members()).groups.single()

        val openings = OpenGroupInHerdrUseCase(herdr, resolveEnvironment)(group)

        assertIs<WorktreeError.HerdrNotInstalled>(openings[0].result.exceptionOrNull())
        assertEquals(HerdrOutcome.OPENED, openings[1].result.getOrThrow().outcome)
        assertEquals(listOf(Triple(world.apiBase, apiTask, "TASK-1")), herdr.opened)
    }

    @Test
    fun `opening a group focuses live agents and starts the agent where the workspace has none`() = runTest {
        val herdr = FakeHerdrClient(
            alreadyOpen = setOf(appTask, apiTask),
            panes = listOf(HerdrPane("w1:p1", "w1", appTask, agent = "claude", agentStatus = "idle"), HerdrPane("w2:p1", "w2", apiTask)),
        )
        val resolveEnvironment = ResolveWorktreeEnvironmentUseCase(world.git, FakeRepositoryProvisioningSource(), FakeEnvironmentStateStore(), FakePortProbe(), trust = FakeRepositoryTrust(approvesEverything = true))
        val group = DetectGroupsUseCase(world.git)(world.members()).groups.single()

        val openings = OpenGroupInHerdrUseCase(herdr, resolveEnvironment)(group)

        assertEquals(HerdrOpening(HerdrOutcome.FOCUSED, "w1:p1", "claude"), openings[0].result.getOrThrow())
        assertEquals(HerdrOutcome.STARTED_AGENT, openings[1].result.getOrThrow().outcome)
        assertEquals("w2:p2", openings[1].result.getOrThrow().paneId)
        assertEquals(listOf("w2:p2"), herdr.ran.map { it.first })
    }

    @Test
    fun `a member whose agent doesn't start fails while the other still opens`() = runTest {
        val herdr = FakeHerdrClient(alreadyOpen = setOf(appTask), panes = listOf(HerdrPane("w1:p1", "w1", appTask, agent = "claude", agentStatus = "working")), startsAgents = false)
        val resolveEnvironment = ResolveWorktreeEnvironmentUseCase(world.git, FakeRepositoryProvisioningSource(), FakeEnvironmentStateStore(), FakePortProbe(), trust = FakeRepositoryTrust(approvesEverything = true))
        val group = DetectGroupsUseCase(world.git)(world.members()).groups.single()

        val openings = OpenGroupInHerdrUseCase(herdr, resolveEnvironment)(group)

        assertEquals(HerdrOutcome.FOCUSED, openings[0].result.getOrThrow().outcome)
        assertIs<WorktreeError.HerdrAgentNotStarted>(openings[1].result.exceptionOrNull())
    }

    @Test
    fun `the multirepo agent never runs the command a declaration holds`() = runTest {
        val declared = world.declaration.copy(agent = AgentLaunchConfig(name = "Claude", agentCommand = "touch /tmp/marker; claude", useHeadroom = false))
        world.source.declarations[world.folder] = declared
        val herdr = FakeHerdrClient()

        OpenMultirepoAgentInHerdrUseCase(herdr)(world.resolver(world.folder).getOrThrow()!!.multirepo).getOrThrow()

        assertEquals(listOf("plain1:p1" to "claude -n product"), herdr.ran)
    }

    @Test
    fun `the multirepo agent opens in a plain workspace or focuses the open one`() = runTest {
        val multirepo = Multirepo(world.folder, world.declaration, approved = true, localAgent = AgentLaunchConfig(agentCommand = "claude", useHeadroom = false))
        val fresh = FakeHerdrClient()

        assertEquals(HerdrOpening(HerdrOutcome.OPENED, "plain1:p1", multirepo.agent.displayName), OpenMultirepoAgentInHerdrUseCase(fresh)(multirepo).getOrThrow())
        assertEquals(listOf(world.folder to "product"), fresh.createdWorkspaces)
        assertEquals(listOf("plain1:p1" to "claude -n product"), fresh.ran)

        val open = FakeHerdrClient(
            workspaces = listOf(HerdrWorkspace("w5", "product", null), HerdrWorkspace("w6", "app", world.folder)),
            panes = listOf(
                HerdrPane("w6:p1", "w6", world.folder),
                HerdrPane("w5:p1", "w5", world.folder),
                HerdrPane("w5:p2", "w5", world.folder, agent = "claude", agentStatus = "idle"),
            ),
        )
        assertEquals(HerdrOpening(HerdrOutcome.FOCUSED, "w5:p2", "claude"), OpenMultirepoAgentInHerdrUseCase(open)(multirepo).getOrThrow())
        assertEquals(listOf("w5:p2"), open.focusedAgents)
        assertTrue(open.createdWorkspaces.isEmpty() && open.ran.isEmpty())
    }

    @Test
    fun `the multirepo agent starts in a new pane of its open workspace without an agent`() = runTest {
        val multirepo = Multirepo(world.folder, world.declaration, approved = true, localAgent = AgentLaunchConfig(agentCommand = "claude", useHeadroom = false))
        val herdr = FakeHerdrClient(
            workspaces = listOf(HerdrWorkspace("w5", "product", null)),
            panes = listOf(HerdrPane("w5:p1", "w5", world.folder)),
        )

        val opening = OpenMultirepoAgentInHerdrUseCase(herdr)(multirepo).getOrThrow()

        assertEquals(HerdrOpening(HerdrOutcome.STARTED_AGENT, "w5:p2", multirepo.agent.displayName), opening)
        assertEquals(listOf(FakeHerdrClient.Split("w5:p1", world.folder, emptyList())), herdr.splits)
        assertEquals(listOf("w5:p2" to "claude -n product"), herdr.ran)
        assertTrue(herdr.createdWorkspaces.isEmpty() && herdr.closed.isEmpty())
    }

    @Test
    fun `the multirepo agent that doesn't start fails`() = runTest {
        val multirepo = Multirepo(world.folder, world.declaration, approved = true, localAgent = AgentLaunchConfig(agentCommand = "claude", useHeadroom = false))

        val error = OpenMultirepoAgentInHerdrUseCase(FakeHerdrClient(startsAgents = false))(multirepo).exceptionOrNull()

        assertIs<WorktreeError.HerdrAgentNotStarted>(error)
        assertEquals("plain1:p1", error.paneId)
    }

    @Test
    fun `each member of a group gets its own session name`() = runTest {
        val herdr = FakeHerdrClient()
        val registry = FakeClaudeSessionRegistry(started = herdr::startedClaudeSessions)
        val launcher = HerdrAgentLauncher(herdr, registry = registry, isClaudeProcessAlive = { true })
        val resolveEnvironment = ResolveWorktreeEnvironmentUseCase(world.git, FakeRepositoryProvisioningSource(), FakeEnvironmentStateStore(), FakePortProbe(), trust = FakeRepositoryTrust(approvesEverything = true))
        val group = DetectGroupsUseCase(world.git)(world.members()).groups.single()

        val openings = OpenGroupInHerdrUseCase(herdr, resolveEnvironment, launcher)(group)

        assertEquals(setOf("api-task-1", "app-task-1"), openings.map { it.result.getOrThrow().sessionName }.toSet())
        assertEquals(listOf("api-task-1", "app-task-1"), herdr.ran.map { it.second.substringAfter(" -n ") }.sorted())
    }

    @Test
    fun `the multirepo agent reports its session name when opened, started or focused`() = runTest {
        val multirepo = Multirepo(world.folder, world.declaration, approved = true, localAgent = AgentLaunchConfig(agentCommand = "claude", useHeadroom = false))
        val fresh = FakeHerdrClient()
        val freshLauncher = HerdrAgentLauncher(fresh, registry = FakeClaudeSessionRegistry(started = fresh::startedClaudeSessions), isClaudeProcessAlive = { true })

        assertEquals("product", OpenMultirepoAgentInHerdrUseCase(fresh, freshLauncher)(multirepo).getOrThrow().sessionName)

        val started = FakeHerdrClient(workspaces = listOf(HerdrWorkspace("w5", "product", null)), panes = listOf(HerdrPane("w5:p1", "w5", world.folder)))
        val startedLauncher = HerdrAgentLauncher(started, registry = FakeClaudeSessionRegistry(started = started::startedClaudeSessions), isClaudeProcessAlive = { true })

        assertEquals("product", OpenMultirepoAgentInHerdrUseCase(started, startedLauncher)(multirepo).getOrThrow().sessionName)

        val open = FakeHerdrClient(
            workspaces = listOf(HerdrWorkspace("w5", "product", null)),
            panes = listOf(HerdrPane("w5:p2", "w5", world.folder, agent = "claude", agentStatus = "idle")),
        )
        val registry = FakeClaudeSessionRegistry(listOf(ClaudeSession(42, world.folder, "product")))

        val focused = OpenMultirepoAgentInHerdrUseCase(open, HerdrAgentLauncher(open, registry = registry, isClaudeProcessAlive = { true }))(multirepo).getOrThrow()

        assertEquals(HerdrOpening(HerdrOutcome.FOCUSED, "w5:p2", "claude", "product"), focused)
    }
}
