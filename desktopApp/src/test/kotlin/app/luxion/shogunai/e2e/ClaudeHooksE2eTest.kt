package app.luxion.shogunai.e2e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import app.luxion.shogunai.AppContainer
import app.luxion.shogunai.domain.usecase.CommandLineToolState
import app.luxion.shogunai.domain.usecase.CommandLineToolStatus
import app.luxion.shogunai.domain.usecase.HooksInstallState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ClaudeHooksE2eTest {

    @Test
    fun `the hooks section installs the hooks into Claude Code's settings and removes them`() = runComposeUiTest {
        val environment = E2eEnvironment.prepare()
        environment.installFakeShogun()
        environment.claudeSettings.apply { parentFile.mkdirs() }.writeText("""{ "model": "opus" }""")
        val appContainer = AppContainer(environment.hostEnvironment())
        val strings = setHooksSection(appContainer)

        awaitText(strings.claudeHooksStatus(HooksInstallState.MISSING))
        onNode(hasClickAction() and hasText(strings.claudeHooksInstall)).performClick()
        awaitText(strings.claudeHooksConfirm)
        onNode(hasClickAction() and hasText(strings.claudeHooksConfirm)).performClick()

        awaitText(strings.claudeHooksStatus(HooksInstallState.INSTALLED))
        val installed = shogunCommands(environment)
        assertTrue(installed.isNotEmpty())
        assertTrue(installed.all { it.startsWith("shogun ") }, "$installed")
        assertEquals("opus", (settings(environment)["model"] as JsonPrimitive).content)

        onNode(hasClickAction() and hasText(strings.claudeHooksRemove)).performClick()
        awaitText(strings.claudeHooksConfirm)
        onNode(hasClickAction() and hasText(strings.claudeHooksConfirm)).performClick()

        awaitText(strings.claudeHooksStatus(HooksInstallState.MISSING))
        assertEquals(emptyList(), shogunCommands(environment))
        assertEquals("opus", (settings(environment)["model"] as JsonPrimitive).content)
    }

    @Test
    fun `without the command the hooks section says so and can't install the hooks`() = runComposeUiTest {
        val environment = E2eEnvironment.prepare()
        val appContainer = AppContainer(environment.hostEnvironment())
        val strings = setHooksSection(appContainer)

        awaitText(strings.commandLineToolStatus(CommandLineToolStatus(CommandLineToolState.MISSING)))
        awaitText(strings.claudeHooksNeedsTool)
        onNode(hasClickAction() and hasText(strings.claudeHooksInstall)).assertIsNotEnabled()
    }

    private fun settings(environment: E2eEnvironment): JsonObject =
        Json.parseToJsonElement(environment.claudeSettings.readText()).jsonObject

    private fun shogunCommands(environment: E2eEnvironment): List<String> =
        commandsIn(settings(environment)["hooks"]).filter { it.startsWith("shogun") }

    private fun commandsIn(element: JsonElement?): List<String> = when (element) {
        is JsonObject -> element.entries.flatMap { (key, value) ->
            if (key == "command" && value is JsonPrimitive) listOf(value.content) else commandsIn(value)
        }
        is JsonArray -> element.flatMap(::commandsIn)
        else -> emptyList()
    }
}
