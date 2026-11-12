package app.luxion.shogunai.ui.worktree

import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.success
import app.luxion.shogunai.domain.model.BranchOption
import app.luxion.shogunai.domain.model.Multirepo
import app.luxion.shogunai.domain.model.MultirepoDeclaration
import app.luxion.shogunai.domain.model.RunningAgent
import app.luxion.shogunai.ui.multirepo.multirepoMemberships
import app.luxion.shogunai.domain.usecase.Activity
import app.luxion.shogunai.domain.usecase.GroupKept
import app.luxion.shogunai.domain.usecase.GroupKeptReason
import app.luxion.shogunai.domain.usecase.WorktreeGroup as BranchGroup
import app.luxion.shogunai.ui.i18n.EnglishStrings
import app.luxion.shogunai.ui.theme.Status
import app.luxion.shogunai.ui.worktree.inspector.composeStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ListPaneTextsTest {

    private val strings = EnglishStrings
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `every state group has its own label`() {
        assertEquals(
            listOf(
                strings.groupAgentWorking,
                strings.groupWaitingForYou,
                strings.groupUnfinished,
                strings.groupNew,
                strings.groupStopped,
                strings.groupMerged,
                strings.groupInactive,
                strings.groupFolderMissing,
                strings.groupOther,
            ).sorted(),
            StateGroupKind.entries.map { it.label(strings) }.sorted(),
        )
    }

    @Test
    fun `a cleanup block says why, down to the repository a group keeps`() {
        val group = BranchGroup("feature/TASK-1", emptyList())
        val agents = listOf(RunningAgent("claude"), RunningAgent("claude"))

        assertEquals(strings.cleanUpBlockedAgentRunning(listOf("TASK-1"), listOf("claude")), CleanupBlock.KeptForRunningAgent(listOf("TASK-1"), listOf("claude")).describe(strings))
        assertEquals(strings.groupKeptNotMerged("api"), CleanupBlock.Kept(GroupKept(group, "api", GroupKeptReason.NOT_MERGED)).describe(strings))
        assertEquals(strings.groupKeptUncommitted("api"), GroupKept(group, "api", GroupKeptReason.UNCOMMITTED_CHANGES).describe(strings))
        assertEquals(strings.groupKeptStatusUnavailable("api"), GroupKept(group, "api", GroupKeptReason.STATUS_UNAVAILABLE).describe(strings))
        assertEquals(strings.groupKeptAgentRunning("api", listOf("claude")), GroupKept(group, "api", GroupKeptReason.AGENT_RUNNING, agents).describe(strings))
    }

    @Test
    fun `compose states read as running, partly exited or stopped`() {
        assertEquals(Status.SUCCESS, composeStatus("running(2)"))
        assertEquals(Status.WARNING, composeStatus("running(1), exited(1)"))
        assertEquals(Status.NEUTRAL, composeStatus("exited(2)"))
    }

    @Test
    fun `a group's catalog offers the repositories it lacks and says why it can't be cleaned up`() = runTest(dispatcher) {
        val app = FakeWorktreeRepository("app", listOf("/home/dev/projects/app-TASK-1" to "feature/TASK-1"))
        val api = FakeWorktreeRepository("api", listOf("/home/dev/projects/api-TASK-1" to "feature/TASK-1")) { command ->
            if (command == listOf("git", "rev-list", "--left-right", "--count", "origin/main...HEAD")) {
                success(command, "0\t1\n")
            } else {
                null
            }
        }
        val docs = FakeWorktreeRepository("docs", emptyList())
        val board = uiBoard(listOf(app, api, docs))
        val product = Multirepo(
            "/home/dev/projects/product",
            MultirepoDeclaration(repos = mapOf("app" to app.base, "api" to api.base, "docs" to docs.base), worktreesRoot = "../product-worktrees"),
            approved = true,
        )
        board.syncMultirepos(multirepoMemberships(listOf(product), board.projects))
        board.showMultirepo(product.folder)
        advanceUntilIdle()
        val group = board.changeView.groups.single()

        val catalog = groupCatalogOf(board, group, strings)
        val bugfix = groupCatalogOf(board, ChangeGroup("bugfix/TASK-1", emptyList(), Activity.Unknown), strings)

        assertEquals(listOf("docs"), catalog.context.addableAliases)
        assertEquals(strings.groupKeptNotMerged("api"), catalog.context.cleanUpBlocked)
        assertEquals(emptyList(), bugfix.context.addableAliases)
        assertNull(bugfix.context.cleanUpBlocked)
    }

    @Test
    fun `a row without a change opens nothing from its tag`() {
        val opened = mutableListOf<GroupTag>()
        val tag = GroupTag("/home/dev/projects/product", "feature/TASK-1", listOf("api"))

        groupTagOpener(null) { opened += it }()
        groupTagOpener(tag) { opened += it }()

        assertEquals(listOf(tag), opened)
    }

    @Test
    fun `start points and branches are labelled and grouped by their prefix`() {
        val current = StartOption.Current
        val main = StartOption.Branch("main")
        val release = StartOption.Branch("release/1.0")
        val plain = StartOption.Branch("hotfix")

        assertEquals(strings.startFromCurrentBranch("feature/TASK-1"), startOptionLabel(current, "feature/TASK-1", "main", strings))
        assertEquals(strings.startFromMainBranch("main"), startOptionLabel(main, null, "main", strings))
        assertEquals("release/1.0", startOptionLabel(release, null, "main", strings))
        assertNull(startOptionGroup(current, "main", strings))
        assertNull(startOptionGroup(main, "main", strings))
        assertEquals("release", startOptionGroup(release, "main", strings))
        assertEquals(strings.otherBranchesGroup, startOptionGroup(plain, "main", strings))

        assertEquals(strings.branchAlreadyHasWorktree("main"), branchOptionLabel(BranchOption("main", isCheckedOut = true), strings))
        assertEquals(strings.remoteBranchLabel("origin/hotfix"), branchOptionLabel(BranchOption("origin/hotfix", isCheckedOut = false, isRemote = true), strings))
        assertEquals("release/1.0", branchOptionLabel(BranchOption("release/1.0", isCheckedOut = false), strings))
    }
}
