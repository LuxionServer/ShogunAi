package app.luxion.shogunai.e2e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import app.luxion.shogunai.AppContainer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(ExperimentalTestApi::class)
class UnavailableMultirepoE2eTest {

    @Test
    fun `opening the app with a multirepo on an unmounted volume keeps it registered until it's removed`() = runComposeUiTest {
        val environment = E2eEnvironment.prepare()
        val declaration = environment.workspace.resolve("External/main-multirepo/.shogunai-multirepo.json").absolutePath
        val registry = environment.shogunaiFolder.resolve("multirepos.json").apply { parentFile.mkdirs() }
        val registered = """{"multirepos":["$declaration"],"local":{"$declaration":{"approved":"digest","agent":{"agentCommand":"claude"}}}}"""
        registry.writeText(registered)
        val appContainer = AppContainer(environment.hostEnvironment())
        val strings = setShell(appContainer)

        awaitText("main-multirepo")
        waitForIdle()
        assertEquals(registered, registry.readText())

        onNodeWithText("main-multirepo").performClick()
        awaitText(strings.tryAgain)
        onNodeWithText(strings.tryAgain).performClick()
        waitForIdle()
        assertEquals(registered, registry.readText())
        onNodeWithText(strings.remove).performClick()
        awaitText(strings.removeMultirepoTitle)
        onAllNodesWithText(strings.remove).onLast().performClick()

        awaitNoText("main-multirepo")
        awaitNoText(strings.removeMultirepoTitle)
        assertFalse(registry.readText().contains("main-multirepo"))
    }
}
