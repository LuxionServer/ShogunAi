package app.luxion.shogunai.e2e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import app.luxion.shogunai.AppContainer
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.ProjectConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class CreateWorktreeE2eTest {

    @Test
    fun `creating a worktree from the dialog copies the project's local files`() = runComposeUiTest {
        val environment = E2eEnvironment.prepare()
        val localFiles = mapOf(
            ".env.local" to "API_URL=http://localhost:8080\n",
            "config/local.properties" to "sdk.dir=/opt/sdk\n",
            "keys/debug.keystore" to "debug-keystore\n",
        )
        val declaredLocalFiles = listOf(".env.local", "config/local.properties", "keys")
        val repository = environment.repository("sample-app", ignored = localFiles)
        val worktreesRoot = environment.workspace.resolve("sample-app-worktrees")
        val appContainer = AppContainer()
        appContainer.projectRepository.save(
            Project(
                id = "sample-app",
                name = "sample-app",
                config = ProjectConfig(
                    baseRepositoryPath = repository.path,
                    worktreesRoot = worktreesRoot.path,
                    secretFiles = declaredLocalFiles,
                    mainBranch = E2eEnvironment.MAIN_BRANCH,
                ),
            ),
        )
        val strings = setShell(appContainer)

        awaitText(strings.newWorktree)
        onAllNodesWithText(strings.newWorktree).onFirst().performClick()
        awaitText(strings.taskIdLabel)
        onAllNodes(hasSetTextAction()).onLast().performTextInput("TASK-123")
        onNode(hasClickAction() and hasText(strings.createWorktree)).performClick()

        val worktree = worktreesRoot.resolve("TASK-123")
        awaitCondition("the local files in the worktree") { localFiles.keys.all { worktree.resolve(it).exists() } }
        localFiles.forEach { (path, text) -> assertEquals(text, worktree.resolve(path).readText()) }
        assertTrue(environment.git(repository, "worktree", "list").contains(worktree.canonicalPath.replace('\\', '/')))
        assertTrue(environment.git(repository, "branch", "--list").contains("TASK-123"))
        awaitText("TASK-123", substring = true)
    }
}
