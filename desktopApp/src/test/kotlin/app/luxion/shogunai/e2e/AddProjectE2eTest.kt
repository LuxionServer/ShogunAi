package app.luxion.shogunai.e2e

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import app.luxion.shogunai.AppContainer
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class AddProjectE2eTest {

    @Test
    fun `adding a repository with a local file lists the project and stores it`() = runComposeUiTest {
        val environment = E2eEnvironment.prepare()
        val repository = environment.repository("sample-app", ignored = mapOf(".env.local" to "API_URL=http://localhost\n"))
        val appContainer = AppContainer()
        val strings = setShell(appContainer, AnsweringPathPicker(folder = repository))

        awaitText(strings.addProject)
        onAllNodesWithText(strings.addProject).onLast().performClick()
        awaitText(strings.chooseRepositoryFolder)
        onNodeWithText(strings.chooseRepositoryFolder).performClick()
        awaitText(strings.newLocalFileLabel)

        val newLocalFile = onNode(hasSetTextAction() and hasText(strings.newLocalFileLabel))
        newLocalFile.performScrollTo()
        newLocalFile.performTextInput(".env.local")
        onNode(hasSetTextAction() and hasText(".env.local")).performKeyInput { pressKey(Key.Enter) }
        awaitText(".env.local")
        onAllNodesWithText(strings.addProject).onLast().performClick()

        awaitCondition { appContainer.projectRepository.list().isNotEmpty() }
        val project = appContainer.projectRepository.list().single()
        assertEquals("sample-app", project.name)
        assertEquals(repository.canonicalPath, File(project.config.baseRepositoryPath).canonicalPath)
        assertEquals(listOf(".env.local"), project.config.secretFiles)
        awaitText("sample-app")
    }
}
