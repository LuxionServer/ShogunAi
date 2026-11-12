package app.luxion.shogunai.ui.worktree

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import app.luxion.shogunai.domain.FakeClipboardWriter
import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.FakeRepositoryProvisioningSource
import app.luxion.shogunai.domain.FakeRepositoryTrust
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.failure
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.success
import app.luxion.shogunai.domain.executor.CommandResult
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.model.RepositoryApprovalSubject
import app.luxion.shogunai.domain.model.RepositoryProvisioning
import app.luxion.shogunai.domain.usecase.CopyWorktreeLaunchCommandUseCase
import app.luxion.shogunai.domain.usecase.CreateWorktreeUseCase
import app.luxion.shogunai.ui.i18n.EnglishStrings
import app.luxion.shogunai.ui.shell.LocalAppMessages
import app.luxion.shogunai.ui.shell.rememberAppMessages
import kotlinx.coroutines.CompletableDeferred
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class CreateWorktreeDialogUiTest {

    private val strings = EnglishStrings
    private val forEachRef = listOf("git", "for-each-ref", "--format=%(refname)", "refs/heads", "refs/remotes/origin/")
    private val branches: (List<String>) -> CommandResult? = { command ->
        if (command == forEachRef) success(command, "refs/heads/main\nrefs/heads/release/1.0\nrefs/heads/hotfix\nrefs/remotes/origin/spike\n") else null
    }
    private val api = FakeWorktreeRepository("api", respond = branches)
    private val docs = FakeWorktreeRepository("docs", respond = branches)

    private fun ComposeUiTest.show(board: WorktreeBoard) {
        setContent {
            val messages = rememberAppMessages()
            CompositionLocalProvider(LocalAppMessages provides messages) {
                MaterialTheme {
                    Box(Modifier.size(1000.dp, 900.dp)) {
                        WorktreeScreen(board, onOpenProjectSettings = {}, onSeeAll = {}, onOpenMachine = {})
                    }
                }
            }
        }
        waitUntil(timeoutMillis = 5_000) { board.isCatalogLoaded }
    }

    private fun ComposeUiTest.waitForText(text: String) {
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun gated(gate: CompletableDeferred<Unit>, delegate: ShellCommandExecutor) = object : ShellCommandExecutor {
        override suspend fun execute(command: List<String>, workingDirectory: String?): CommandResult {
            if (command.take(3) == listOf("git", "worktree", "add")) gate.await()
            return delegate.execute(command, workingDirectory)
        }
    }

    @Test
    fun `the dialog chooses the project, the start point and an existing branch, and shows the creation running`() = runComposeUiTest {
        val gate = CompletableDeferred<Unit>()
        val board = uiBoard(listOf(api, docs)) { repository, useCases ->
            useCases.copy(create = CreateWorktreeUseCase(repository.config, gated(gate, repository.executor), FakeFileManager(existing = listOf(repository.base)), FakeRepositoryProvisioningSource()))
        }
        show(board)

        runOnIdle { board.openCreateDialog() }
        waitForText(strings.projectLabel)
        runOnIdle { board.selectCreateProject("docs") }
        waitUntil { board.createProjectId == "docs" }

        runOnIdle { board.updateCreateMode(CreateMode.EXISTING_BRANCH) }
        waitForText(strings.selectBranchPlaceholder)
        onNodeWithText(strings.selectBranchPlaceholder).performClick()
        waitForText("origin")
        onNodeWithText("origin").performClick()
        onNodeWithText("release").performClick()
        onNodeWithText(strings.otherBranchesGroup).performClick()
        waitForText("spike")
        onAllNodesWithText("hotfix").onLast().performClick()
        waitUntil { board.selectedBranch == "hotfix" }

        runOnIdle { board.updateCreateMode(CreateMode.NEW_BRANCH) }
        waitForText(strings.startFromLabel)
        onAllNodesWithText(strings.startFromCurrentBranch(board.currentBranchName)).onFirst().performClick()
        waitForText("release")
        onAllNodesWithText(strings.startFromCurrentBranch(board.currentBranchName)).onLast().performClick()

        onAllNodes(isToggleable()).onFirst().performClick()
        onAllNodes(hasSetTextAction()).onLast().performTextInput("TASK-77")
        runOnIdle {
            board.fetchFirst = false
            board.confirmCreate()
        }
        waitUntil { board.createHolder?.isCreating == true }
        waitForIdle()
        gate.complete(Unit)
        waitUntil(timeoutMillis = 5_000) { !board.isCreateDialogOpen }
    }

    @Test
    fun `a failing fetch offers to create without fetching`() = runComposeUiTest {
        api.respond = { command ->
            when {
                command.take(2) == listOf("git", "fetch") -> failure(command, stderr = "could not resolve host")
                else -> branches(command)
            }
        }
        val board = uiBoard(listOf(api))
        show(board)
        runOnIdle {
            board.openCreateDialog("api")
            board.updateTaskId("TASK-78")
        }
        waitUntil(timeoutMillis = 5_000) { board.canFetchFirst && board.canCreate }

        runOnIdle {
            board.fetchFirst = true
            board.confirmCreate()
        }
        waitForText(strings.createWithoutFetching)
        onNodeWithText(strings.createWithoutFetching).performClick()

        waitUntil(timeoutMillis = 5_000) { !board.isCreateDialogOpen }
    }

    @Test
    fun `post-creation commands ask for approval and a failing one leaves the launch command to copy`() = runComposeUiTest {
        api.respond = { command -> if (command.firstOrNull() == "npm") failure(command, exitCode = 1, stderr = "ENOTFOUND") else branches(command) }
        val approval = DialogRepositoryApproval()
        val subject = RepositoryApprovalSubject(listOf(listOf("npm", "install")))
        val board = uiBoard(listOf(api), approval = approval) { repository, useCases ->
            useCases.copy(
                create = CreateWorktreeUseCase(
                    repository.config,
                    repository.executor,
                    FakeFileManager(existing = listOf(repository.base)),
                    FakeRepositoryProvisioningSource(byRepository = mapOf(repository.base to RepositoryProvisioning(postCreateCommands = listOf(listOf("npm", "install"))))),
                    trust = FakeRepositoryTrust(),
                    approval = approval,
                ),
                copyLaunchCommand = CopyWorktreeLaunchCommandUseCase(repository.config, FakeClipboardWriter()),
            )
        }
        show(board)
        runOnIdle {
            board.openCreateDialog("api")
            board.updateTaskId("TASK-80")
            board.fetchFirst = false
            board.confirmCreate()
        }
        waitForText(strings.skipCommands)
        onNodeWithText(strings.skipCommands).performClick()
        waitUntil(timeoutMillis = 5_000) { !board.isCreateDialogOpen }
        runOnIdle { assertTrue(approval.pending == null) }

        runOnIdle {
            board.openCreateDialog("api")
            board.updateTaskId("TASK-79")
            board.fetchFirst = false
            board.confirmCreate()
        }
        waitForText(strings.runAndRemember)
        onNodeWithText(strings.runAndRemember).performClick()
        waitUntil(timeoutMillis = 5_000) { board.createHolder?.createdWithErrorPath != null }
        waitForText(strings.worktreeCreatedWithError)
        onNodeWithText(strings.copyLaunchCommand).performClick()
        onNodeWithText(strings.close).performClick()
        waitUntil { !board.isCreateDialogOpen }
    }

    @Test
    fun `a failed creation shows its error until dismissed`() = runComposeUiTest {
        api.respond = { command -> if (command.take(3) == listOf("git", "worktree", "add")) failure(command, stderr = "fatal: invalid reference") else branches(command) }
        val board = uiBoard(listOf(api))
        show(board)
        runOnIdle {
            board.openCreateDialog("api")
            board.updateTaskId("TASK-81")
            board.fetchFirst = false
            board.confirmCreate()
        }

        waitUntil(timeoutMillis = 5_000) { board.createHolder?.createError != null }
        waitForIdle()
        runOnIdle { board.createHolder!!.dismissCreateError() }
        runOnIdle { assertEquals(null, board.createHolder!!.createError) }
    }
}
