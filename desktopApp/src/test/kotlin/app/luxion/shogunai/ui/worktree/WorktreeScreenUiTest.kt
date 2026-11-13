package app.luxion.shogunai.ui.worktree

import app.luxion.shogunai.domain.ScriptedShellCommandExecutor
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.luxion.shogunai.ToolUseCases
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.failure
import app.luxion.shogunai.domain.FakeToolDetector
import app.luxion.shogunai.domain.FakeToolPreferenceRepository
import app.luxion.shogunai.domain.executor.CommandResult
import app.luxion.shogunai.domain.model.DetectedTools
import app.luxion.shogunai.domain.model.TerminalId
import app.luxion.shogunai.domain.usecase.DeleteBranchUseCase
import app.luxion.shogunai.domain.usecase.RemoveWorktreeUseCase
import app.luxion.shogunai.ui.actions.ActionId
import app.luxion.shogunai.ui.i18n.EnglishStrings
import app.luxion.shogunai.ui.shell.AppMessagesHost
import app.luxion.shogunai.ui.shell.LocalAppMessages
import app.luxion.shogunai.ui.shell.ToolsPreferences
import app.luxion.shogunai.ui.shell.rememberAppMessages
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.model.AgentState
import app.luxion.shogunai.domain.model.GroupedView
import app.luxion.shogunai.domain.model.Multirepo
import app.luxion.shogunai.domain.model.MultirepoDeclaration
import app.luxion.shogunai.domain.model.RunnerId
import app.luxion.shogunai.domain.model.RunningAgent
import app.luxion.shogunai.ui.multirepo.MultirepoActions
import app.luxion.shogunai.ui.multirepo.multirepoMemberships
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class WorktreeScreenUiTest {

    private val strings = EnglishStrings
    private val taskPath = "/home/dev/projects/TASK-123"
    private val repository = FakeWorktreeRepository()
    private val taskRef = WorktreeRef(repository.id, taskPath)

    private fun ComposeUiTest.show(board: WorktreeBoard, width: Dp = 1400.dp, onSeeAll: () -> Unit = {}) {
        setContent {
            val messages = rememberAppMessages()
            CompositionLocalProvider(LocalAppMessages provides messages) {
                MaterialTheme {
                    Box(Modifier.size(width, 800.dp)) {
                        WorktreeScreen(board, onOpenProjectSettings = {}, onSeeAll = onSeeAll, onOpenMachine = {})
                    }
                    AppMessagesHost(messages)
                }
            }
        }
        waitUntil(timeoutMillis = 5_000) { board.isCatalogLoaded }
    }

    private fun ComposeUiTest.waitForText(text: String) {
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun gated(gate: CompletableDeferred<Unit>, delegate: ScriptedShellCommandExecutor) = object : ScriptedShellCommandExecutor() {
        override suspend fun respond(command: List<String>, workingDirectory: String?): CommandResult {
            gate.await()
            return delegate.respond(command, workingDirectory)
        }
    }

    @Test
    fun `the removal dialog removes the worktree and its branch, or is cancelled`() = runComposeUiTest {
        val gate = CompletableDeferred<Unit>()
        val board = uiBoard(listOf(repository)) { repo, useCases -> useCases.copy(remove = RemoveWorktreeUseCase(repo.config, gated(gate, repo.executor))) }
        show(board)
        val holder = board.holder(repository.id)!!

        runOnIdle { board.perform(taskRef, ActionId.RemoveWorktree) }
        waitForText(strings.removeWorktreeMessage(taskPath))
        onNodeWithText(strings.cancel).performClick()
        waitUntil { holder.worktreePendingRemoval == null }

        runOnIdle { board.perform(taskRef, ActionId.RemoveWorktree) }
        waitForText(strings.alsoDeleteLocalBranch("feature/TASK-123"))
        onNode(isToggleable()).performClick()
        runOnIdle { assertTrue(holder.pendingRemovalDeleteBranch) }
        onNodeWithText(strings.delete).performClick()
        waitUntil { holder.isRemoving }
        waitForIdle()
        gate.complete(Unit)
        waitUntil(timeoutMillis = 5_000) { holder.worktrees.none { it.path == taskPath } }
    }

    @Test
    fun `a locked worktree asks to force its removal and shows a failure to do so`() = runComposeUiTest {
        repository.respond = { command ->
            if (command.take(3) == listOf("git", "worktree", "remove")) failure(command, stderr = "fatal: cannot remove a locked working tree, lock reason: in use") else null
        }
        val board = uiBoard(listOf(repository))
        show(board)
        val holder = board.holder(repository.id)!!

        runOnIdle { board.perform(taskRef, ActionId.RemoveWorktree) }
        waitForText(strings.delete)
        onNodeWithText(strings.delete).performClick()
        waitUntil(timeoutMillis = 5_000) { holder.pendingRemovalRequiresForce }
        waitForText(strings.forceRemove)
        onNodeWithText(strings.forceRemove).performClick()
        waitUntil(timeoutMillis = 5_000) { holder.removeError != null }
        waitForIdle()
        runOnIdle { assertTrue(holder.worktreePendingRemoval != null) }
    }

    @Test
    fun `a removal git suggests forcing asks to force it`() = runComposeUiTest {
        repository.worktrees = listOf(taskPath to "")
        repository.respond = { command ->
            if (command.take(3) == listOf("git", "worktree", "remove")) failure(command, stderr = "contains modified files, use --force to delete it") else null
        }
        val board = uiBoard(listOf(repository))
        show(board)
        val holder = board.holder(repository.id)!!

        runOnIdle { board.perform(taskRef, ActionId.RemoveWorktree) }
        waitForText(strings.delete)
        onNodeWithText(strings.delete).performClick()
        waitForText(strings.forceRemoveMessage(taskPath))
        runOnIdle { assertTrue(holder.pendingRemovalRequiresForce) }
    }

    @Test
    fun `the kept branch can be deleted anyway or kept`() = runComposeUiTest {
        repository.respond = { command -> if (command.take(2) == listOf("git", "merge-base")) failure(command, exitCode = 1, stderr = "") else null }
        val gate = CompletableDeferred<Unit>()
        val board = uiBoard(listOf(repository)) { repo, useCases -> useCases.copy(deleteBranch = DeleteBranchUseCase(repo.config, gated(gate, repo.executor))) }
        show(board)
        val holder = board.holder(repository.id)!!
        runOnIdle { holder.remove(holder.worktrees.first { it.path == taskPath }, branchToDelete = "feature/TASK-123") }
        waitUntil(timeoutMillis = 5_000) { holder.keptBranchNotice != null }

        runOnIdle { holder.requestForcedBranchDeletion() }
        waitForText(strings.deleteBranchAnywayMessage("feature/TASK-123"))
        onNodeWithText(strings.cancel).performClick()
        waitUntil { !holder.isConfirmingForcedBranchDeletion }

        runOnIdle { holder.requestForcedBranchDeletion() }
        waitForText(strings.deleteBranchAnywayMessage("feature/TASK-123"))
        onNodeWithText(strings.delete).performClick()
        waitUntil { holder.isDeletingBranch }
        waitForIdle()
        gate.complete(Unit)
        waitUntil(timeoutMillis = 5_000) { !holder.isConfirmingForcedBranchDeletion }
    }

    @Test
    fun `the first terminal launch asks which terminal to use, or is cancelled`() = runComposeUiTest {
        val preferences = ToolsPreferences(FakeToolPreferenceRepository(), FakeToolDetector(DetectedTools(terminals = listOf(TerminalId.TERMINAL))))
        runBlocking { preferences.refresh() }
        val tools = ToolUseCases.NONE.copy(preferences = preferences, openInTerminal = { _, _, _ -> Result.success(Unit) })
        val board = uiBoard(listOf(repository)) { _, useCases -> useCases.copy(tools = tools) }
        show(board)
        val holder = board.holder(repository.id)!!

        runOnIdle { holder.openInTerminal(holder.worktrees.first { it.path == taskPath }) }
        waitForText(strings.chooseTerminalTitle)
        onNodeWithText(strings.cancel).performClick()
        waitUntil { holder.terminalChoiceFor == null }

        runOnIdle { holder.openInTerminal(holder.worktrees.first { it.path == taskPath }) }
        waitForText(TerminalId.TERMINAL.displayName)
        onNodeWithText(TerminalId.TERMINAL.displayName).performClick()
        waitUntil { holder.terminalChoiceFor == null }
        runOnIdle { assertEquals(TerminalId.TERMINAL, holder.tools.terminalInUse) }
    }

    private val product = Multirepo(
        "/home/dev/projects/product",
        MultirepoDeclaration(repos = mapOf("main-repo" to repository.base), worktreesRoot = "../product-worktrees"),
        approved = true,
    )

    private fun ComposeUiTest.pressNewItemShortcut() {
        onNode(hasSetTextAction()).performClick()
        onRoot().performKeyInput {
            keyDown(Key.MetaLeft)
            keyDown(Key.CtrlLeft)
            pressKey(Key.N)
            keyUp(Key.CtrlLeft)
            keyUp(Key.MetaLeft)
        }
    }

    @Test
    fun `the header cleans up and creates, also from the keyboard`() = runComposeUiTest {
        val board = uiBoard(listOf(repository))
        show(board)
        waitForText(strings.cleanUpCount(1))

        onNodeWithText(strings.cleanUpCount(1)).performClick()
        waitUntil { board.isCleanupDialogOpen }
        runOnIdle { board.dismissCleanup() }

        onAllNodesWithText(strings.newWorktree).onFirst().performClick()
        waitUntil { board.isCreateDialogOpen }
        runOnIdle { board.dismissCreateDialog() }

        pressNewItemShortcut()
        waitUntil { board.isCreateDialogOpen }
    }

    @Test
    fun `a multirepo header shows its agent and settings and opens its agent or a new change`() = runComposeUiTest {
        var settings = 0
        val board = uiBoard(
            listOf(repository),
            agents = mapOf(product.folder to listOf(RunningAgent("claude", AgentState.WORKING))),
            multirepoActions = MultirepoActions(
                createGroup = { _, _, _, _, _ -> Result.failure(IllegalStateException()) },
                installedRunners = setOf(RunnerId.HERDR, RunnerId.ORCA),
            ),
        )
        board.syncMultirepos(multirepoMemberships(listOf(product), board.projects))
        board.showMultirepo(product.folder)
        setContent {
            val messages = rememberAppMessages()
            CompositionLocalProvider(LocalAppMessages provides messages) {
                MaterialTheme {
                    Box(Modifier.size(1400.dp, 800.dp)) {
                        WorktreeScreen(board, onOpenProjectSettings = {}, onSeeAll = {}, onOpenMachine = {}, onOpenMultirepoSettings = { settings++ })
                    }
                }
            }
        }
        waitUntil(timeoutMillis = 5_000) { board.multirepoAgentsOf(product.folder).isNotEmpty() }

        onNodeWithContentDescription(strings.multirepoSettingsTitle).performClick()
        runOnIdle { assertEquals(1, settings) }

        onAllNodesWithContentDescription(strings.moreActions).onFirst().performClick()
        waitForText(strings.openMultirepoAgent)
        onAllNodesWithText(strings.openMultirepoAgent).onFirst().performClick()
        waitUntil(timeoutMillis = 5_000) { board.multirepoNotice != null }

        onNodeWithText(strings.newChange).performClick()
        waitUntil { board.newChange != null }
        runOnIdle { board.dismissNewChange() }
        pressNewItemShortcut()
        waitUntil { board.newChange != null }
    }

    @Test
    fun `a selection opens the inspector over a narrow list and the scrim closes it`() = runComposeUiTest {
        val board = uiBoard(listOf(repository))
        show(board, width = 700.dp)
        runOnIdle { board.select(taskRef) }
        waitUntil { board.selectedWorktree != null }

        onRoot().performTouchInput { click(Offset(20f, 400f)) }

        waitUntil { board.selected == null }
    }

    @Test
    fun `a selection docks the inspector beside a wide list`() = runComposeUiTest {
        val board = uiBoard(listOf(repository))
        show(board)

        runOnIdle { board.select(taskRef) }

        waitForText(strings.statusTitle.uppercase())
    }

    @Test
    fun `a filter without matches offers to clear it`() = runComposeUiTest {
        val board = uiBoard(listOf(repository))
        show(board)
        runOnIdle { board.updateTextFilter("nothing-like-this") }

        waitForText(strings.noWorktreeMatches)
        onAllNodesWithText(strings.clearFilter).onLast().performClick()

        waitUntil { board.textFilter.isEmpty() }
    }

    @Test
    fun `active with nothing listed says nothing needs the user and links to all worktrees`() = runComposeUiTest {
        var seeAll = 0
        val board = uiBoard(listOf(repository))
        board.show(null, GroupedView.ACTIVE)
        show(board) { seeAll++ }

        waitForText(strings.nothingNeedsYou)
        onNodeWithText(strings.seeAllWorktrees).performClick()

        runOnIdle { assertEquals(1, seeAll) }
    }

    @Test
    fun `active with worktrees in progress and nothing waiting shows a notice above them`() = runComposeUiTest {
        repository.respond = { command ->
            if (command == listOf("git", "rev-list", "--left-right", "--count", "origin/main...HEAD")) {
                FakeShellCommandExecutor.success(command, "0\t1\n")
            } else {
                null
            }
        }
        val board = uiBoard(listOf(repository))
        board.show(null, GroupedView.ACTIVE)
        show(board)

        waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag(NothingNeedsYouTag).fetchSemanticsNodes().isNotEmpty() }
        assertFalse(board.isListEmpty)
    }
}
