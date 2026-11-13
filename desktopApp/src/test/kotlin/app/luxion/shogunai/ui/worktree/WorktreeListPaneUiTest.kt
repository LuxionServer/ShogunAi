package app.luxion.shogunai.ui.worktree

import app.luxion.shogunai.domain.ScriptedShellCommandExecutor
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.luxion.shogunai.GradleUseCases
import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.FakeGradleDaemonSource
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.failure
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.success
import app.luxion.shogunai.domain.model.GradleProvider
import app.luxion.shogunai.domain.model.GroupedView
import app.luxion.shogunai.domain.model.Grouping
import app.luxion.shogunai.domain.model.Multirepo
import app.luxion.shogunai.domain.model.MultirepoDeclaration
import app.luxion.shogunai.domain.usecase.ListGradleDaemonsUseCase
import app.luxion.shogunai.domain.usecase.StopGradleDaemonsUseCase
import app.luxion.shogunai.ui.i18n.EnglishStrings
import app.luxion.shogunai.ui.machine.MachineResource
import app.luxion.shogunai.ui.multirepo.multirepoMemberships
import app.luxion.shogunai.ui.shell.LocalAppMessages
import app.luxion.shogunai.ui.shell.rememberAppMessages
import app.luxion.shogunai.domain.executor.CommandResult
import app.luxion.shogunai.domain.usecase.ListWorktreesUseCase
import app.luxion.shogunai.domain.model.RunningAgent
import app.luxion.shogunai.domain.model.AgentState
import kotlinx.coroutines.CompletableDeferred
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class WorktreeListPaneUiTest {

    private val strings = EnglishStrings
    private val divergence = listOf("git", "rev-list", "--left-right", "--count", "origin/main...HEAD")
    private val unmerged: (List<String>) -> CommandResult? = { command ->
        if (command == divergence) success(command, "0\t1\n") else null
    }
    private val app = FakeWorktreeRepository(
        "app",
        listOf("/home/dev/projects/app-TASK-1" to "feature/TASK-1", "/home/dev/projects/app-loose" to "feature/loose"),
        unmerged,
    )
    private val api = FakeWorktreeRepository("api", listOf("/home/dev/projects/api-TASK-1" to "feature/TASK-1"), unmerged)
    private val product = Multirepo(
        "/home/dev/projects/product",
        MultirepoDeclaration(repos = mapOf("app" to app.base, "api" to api.base), worktreesRoot = "../product-worktrees"),
        approved = true,
    )

    private fun ComposeUiTest.show(
        board: WorktreeBoard,
        width: Dp = 1000.dp,
        onOpenMachine: (MachineResource) -> Unit = {},
        onOpenGroup: (GroupTag) -> Unit = {},
        awaitLoad: Boolean = true,
    ) {
        setContent {
            val messages = rememberAppMessages()
            CompositionLocalProvider(LocalAppMessages provides messages) {
                MaterialTheme {
                    Box(Modifier.size(width, 700.dp)) {
                        WorktreeScreen(board, onOpenProjectSettings = {}, onSeeAll = {}, onOpenMachine = onOpenMachine, onOpenGroup = onOpenGroup)
                    }
                }
            }
        }
        if (awaitLoad) waitUntil(timeoutMillis = 5_000) { board.isCatalogLoaded }
    }

    private fun ComposeUiTest.waitForText(text: String) {
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun productBoard(): WorktreeBoard {
        val board = uiBoard(listOf(app, api))
        board.syncMultirepos(multirepoMemberships(listOf(product), board.projects))
        return board
    }

    @Test
    fun `the multirepo view lists changes and loose worktrees and moves the selection with the keyboard`() = runComposeUiTest {
        val board = productBoard()
        board.showMultirepo(product.folder)
        show(board)
        waitUntil(timeoutMillis = 5_000) { board.changeView.groups.isNotEmpty() }
        waitForText("TASK-1")

        val row = onAllNodesWithText("loose", substring = true).onFirst()
        row.performClick()
        row.performKeyInput { pressKey(Key.DirectionDown) }
        row.performKeyInput { pressKey(Key.DirectionUp) }
        row.performKeyInput {
            keyDown(Key.ShiftLeft)
            pressKey(Key.Enter)
            keyUp(Key.ShiftLeft)
        }
        row.performKeyInput { pressKey(Key.Enter) }
        row.performKeyInput { pressKey(Key.A) }
        runOnIdle { assertTrue(board.selected != null) }

        runOnIdle { board.toggleChangeCollapsed("feature/TASK-1") }
        waitForIdle()
        runOnIdle { assertTrue(board.changeView.groups.single().collapsed) }
    }

    @Test
    fun `an active change expands into its worktrees and shows its multirepo from its menu`() = runComposeUiTest {
        val opened = mutableListOf<GroupTag>()
        val board = productBoard()
        board.show(null, GroupedView.ACTIVE)
        show(board, onOpenGroup = { opened += it })
        waitUntil(timeoutMillis = 5_000) { board.stateGroups.flatMap { it.items }.any { it is StateItem.Change } }

        runOnIdle { board.toggleChangeExpanded(ChangeKey(product.folder, "feature/TASK-1")) }
        waitForText("app-TASK-1")

        onAllNodesWithContentDescription(strings.moreActions).onFirst().performClick()
        waitForText("Show in multirepo")
        onNodeWithText("Show in multirepo").performClick()
        runOnIdle { assertEquals(1, opened.size) }
    }

    @Test
    fun `a project header opens the gradle daemons and its cleanup, and a row's tag opens its change`() = runComposeUiTest {
        val machines = mutableListOf<MachineResource>()
        val opened = mutableListOf<GroupTag>()
        app.respond = { null }
        api.respond = { null }
        val board = uiBoard(listOf(app, api)) { repository, useCases ->
            useCases.copy(
                gradle = GradleUseCases(
                    provider = { GradleProvider().takeIf { repository.id == "app" } },
                    listDaemons = ListGradleDaemonsUseCase(FakeGradleDaemonSource(), FakeFileManager()),
                    stopDaemons = StopGradleDaemonsUseCase(FakeGradleDaemonSource(), repository.executor),
                    isBuildCacheEnabled = { true },
                ),
            )
        }
        board.syncMultirepos(multirepoMemberships(listOf(product), board.projects))
        show(board, onOpenMachine = { machines += it }, onOpenGroup = { opened += it })
        waitForText(strings.gradleChipLabel)

        onNodeWithText(strings.gradleChipLabel).performClick()
        runOnIdle { assertEquals(listOf(MachineResource.GRADLE_DAEMONS), machines) }

        onAllNodesWithText("TASK-1 · +api").onFirst().performClick()
        runOnIdle { assertEquals(1, opened.size) }

        onAllNodesWithContentDescription(strings.moreActions).onFirst().performClick()
        waitForText(strings.cleanUp)
        onAllNodesWithText(strings.cleanUp).onLast().performClick()
        waitUntil { board.isCleanupDialogOpen }
    }

    @Test
    fun `a project whose worktrees can't be listed shows its notice in every grouping`() = runComposeUiTest {
        api.respond = { command -> if (command == listOf("git", "worktree", "list", "--porcelain")) failure(command, stderr = "not a git repository") else unmerged(command) }
        val board = productBoard()
        board.updateGrouping(Grouping.BY_STATE)
        show(board)
        waitUntil(timeoutMillis = 5_000) { board.holder("api")!!.listFailure != null }
        runOnIdle { assertTrue(listItems(board).any { it is ListItem.GroupState }) }

        runOnIdle {
            board.updateGrouping(Grouping.BY_PROJECT)
            board.toggleCollapsed("api")
        }
        waitForIdle()
        runOnIdle { assertTrue(listItems(board).any { it is ListItem.GroupState }) }

        runOnIdle { board.showMultirepo(product.folder) }
        waitForIdle()
        runOnIdle { assertTrue(listItems(board).any { it is ListItem.GroupState }) }
        assertNull(board.selected)
    }

    @Test
    fun `selecting a worktree out of view scrolls it below the header in every view`() = runComposeUiTest {
        val many = FakeWorktreeRepository("many", (1..40).map { "/home/dev/projects/many-TASK-$it" to "feature/TASK-$it" })
        val board = uiBoard(listOf(many))
        show(board)
        val last = WorktreeRef("many", "/home/dev/projects/many-TASK-40")
        val middle = WorktreeRef("many", "/home/dev/projects/many-TASK-20")

        runOnIdle { board.select(last) }
        waitForText("TASK-40")
        runOnIdle { board.select(middle) }
        waitForIdle()
        runOnIdle { board.select(WorktreeRef("many", many.base)) }
        waitForIdle()

        runOnIdle { board.updateGrouping(Grouping.BY_STATE) }
        runOnIdle { board.select(last) }
        waitForIdle()
        runOnIdle { board.select(WorktreeRef("many", "/home/dev/projects/many-TASK-1")) }
        waitForIdle()
    }

    @Test
    fun `scrolling to the selection works in a project view and in a multirepo of loose worktrees`() = runComposeUiTest {
        val many = FakeWorktreeRepository("many", (1..40).map { "/home/dev/projects/many-TASK-$it" to "feature/TASK-$it" })
        val board = uiBoard(listOf(many))
        val loose = Multirepo("/home/dev/projects/loose", MultirepoDeclaration(repos = mapOf("many" to many.base), worktreesRoot = "../loose-worktrees"), approved = true)
        board.syncMultirepos(multirepoMemberships(listOf(loose), board.projects))
        board.show("many")
        show(board)

        runOnIdle { board.select(WorktreeRef("many", many.base)) }
        repeat(25) {
            runOnIdle { board.selectNext() }
            waitForIdle()
        }
        runOnIdle { board.select(WorktreeRef("many", "/home/dev/projects/many-TASK-40")) }
        waitForIdle()

        runOnIdle { board.showMultirepo(loose.folder) }
        waitForIdle()
        runOnIdle { board.select(WorktreeRef("many", "/home/dev/projects/many-TASK-40")) }
        waitForIdle()
        runOnIdle { board.select(WorktreeRef("many", "/home/dev/projects/many-TASK-1")) }
        waitForIdle()
    }

    @Test
    fun `a multirepo still listing shows its loading repository and no loose worktrees when there are none`() = runComposeUiTest {
        val gate = CompletableDeferred<Unit>()
        app.worktrees = listOf("/home/dev/projects/app-TASK-1" to "feature/TASK-1")
        val board = uiBoard(listOf(app, api)) { repository, useCases ->
            if (repository.id != "api") {
                useCases
            } else {
                useCases.copy(
                    list = ListWorktreesUseCase(
                        repository.config,
                        object : ScriptedShellCommandExecutor() {
                            override suspend fun respond(command: List<String>, workingDirectory: String?): CommandResult {
                                gate.await()
                                return repository.executor.respond(command, workingDirectory)
                            }
                        },
                    ),
                )
            }
        }
        board.syncMultirepos(multirepoMemberships(listOf(product), board.projects))
        board.showMultirepo(product.folder)
        show(board, awaitLoad = false)
        waitUntil(timeoutMillis = 5_000) { board.holder("app")!!.hasLoaded }

        runOnIdle { assertTrue(listItems(board).any { it is ListItem.GroupState }) }
        gate.complete(Unit)
        waitUntil(timeoutMillis = 5_000) { board.changeView.groups.isNotEmpty() }
        runOnIdle { assertTrue(board.changeView.ungrouped.isEmpty()) }
    }

    @Test
    fun `a row's agent focuses it and a change's menu runs its actions`() = runComposeUiTest {
        val board = uiBoard(listOf(app, api))
        board.syncMultirepos(multirepoMemberships(listOf(product), board.projects))
        board.show(null, GroupedView.ACTIVE)
        show(board)
        waitUntil(timeoutMillis = 5_000) { board.stateGroups.isNotEmpty() }
        runOnIdle {
            board.holder("app")!!.applyAgents(
                mapOf("/home/dev/projects/app-loose" to listOf(RunningAgent("claude", AgentState.BLOCKED, herdrPaneId = "w1:p1"))),
            )
        }
        waitForIdle()
        onAllNodesWithContentDescription("Claude · needs you", substring = true, useUnmergedTree = true).onFirst().performClick()
        runOnIdle { assertEquals(WorktreeRef("app", "/home/dev/projects/app-loose"), board.selected) }

        onAllNodesWithContentDescription(strings.moreActions).onFirst().performClick()
        waitForText(strings.openAllInHerdr)
        onAllNodesWithText(strings.openAllInHerdr).onLast().performClick()
        waitForIdle()
    }
}
