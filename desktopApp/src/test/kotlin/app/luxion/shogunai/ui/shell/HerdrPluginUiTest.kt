package app.luxion.shogunai.ui.shell

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import app.luxion.shogunai.domain.FakeHerdrClient
import app.luxion.shogunai.domain.io.HerdrClient
import app.luxion.shogunai.domain.model.HerdrPlugin
import app.luxion.shogunai.domain.model.HerdrPluginStep
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.usecase.HerdrPluginHealth
import app.luxion.shogunai.ui.i18n.EnglishStrings
import app.luxion.shogunai.ui.i18n.LocalStrings
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class HerdrPluginUiTest {

    private val brokenLink = HerdrPlugin(
        HerdrPlugin.SHOGUN_PLUGIN_ID,
        enabled = true,
        sourceKind = "local",
        root = "/home/dev/projects/worktrees/TASK-7/integrations/herdr",
        warnings = listOf("manifest unavailable: No such file or directory (os error 2)"),
    )
    private val install = HerdrPluginStep.Install("LuxionServer/ShogunAi/integrations/herdr")

    private fun settings(herdr: HerdrClient) = HerdrPluginSettings(HerdrPluginHealth(herdr)).also { runBlocking { it.refresh() } }

    private fun ComposeUiTest.show(settings: HerdrPluginSettings) = setContent {
        CompositionLocalProvider(LocalStrings provides EnglishStrings) {
            MaterialTheme { Column { HerdrPluginPart(settings) } }
        }
    }

    @Test
    fun `a broken plugin is reinstalled from Settings after confirming`() = runComposeUiTest {
        val herdr = FakeHerdrClient(plugins = listOf(brokenLink))
        val settings = settings(herdr)
        show(settings)

        onNodeWithText("ShogunAi's Herdr plugin is broken").assertIsDisplayed()
        onNodeWithText("Herdr says: manifest unavailable: No such file or directory (os error 2)").assertIsDisplayed()
        onNodeWithText("Reinstall").performClick()
        onNodeWithText("herdr plugin unlink shogunai.worktrees").assertIsDisplayed()
        onNodeWithText("herdr plugin install LuxionServer/ShogunAi/integrations/herdr --yes").assertIsDisplayed()
        assertTrue(herdr.pluginSteps.isEmpty())
        onNodeWithText("Run").performClick()
        waitForIdle()

        assertEquals(listOf(HerdrPluginStep.Unlink("shogunai.worktrees"), install), herdr.pluginSteps)
        onNodeWithText("ShogunAi's Herdr plugin is installed").assertIsDisplayed()
        assertEquals(0, onAllNodesWithText("Reinstall").fetchSemanticsNodes().size)
    }

    @Test
    fun `cancelling runs nothing and keeps it broken`() = runComposeUiTest {
        val herdr = FakeHerdrClient(plugins = listOf(brokenLink))
        val settings = settings(herdr)
        show(settings)

        onNodeWithText("Reinstall").performClick()
        onNodeWithText("Cancel").performClick()

        assertTrue(herdr.pluginSteps.isEmpty())
        onNodeWithText("ShogunAi's Herdr plugin is broken").assertIsDisplayed()
    }

    @Test
    fun `a failing reinstall shows Herdr's error and the state read again`() = runComposeUiTest {
        val failure = WorktreeError.HerdrCommandFailed(listOf("herdr", "plugin", "install"), "network unreachable")
        val herdr = FakeHerdrClient(plugins = listOf(brokenLink), pluginStepFailures = mapOf(install to failure))
        val settings = settings(herdr)
        show(settings)

        onNodeWithText("Reinstall").performClick()
        onNodeWithText("Run").performClick()
        waitForIdle()

        assertEquals(failure, settings.error)
        onNodeWithText("ShogunAi's Herdr plugin isn't installed").assertIsDisplayed()
        onNodeWithText("Install").assertIsDisplayed()
    }

    @Test
    fun `an installed or disabled plugin offers no action`() = runComposeUiTest {
        val installed = settings(FakeHerdrClient(plugins = listOf(brokenLink.copy(warnings = emptyList()))))
        val disabled = settings(FakeHerdrClient(plugins = listOf(brokenLink.copy(warnings = emptyList(), enabled = false))))
        setContent {
            CompositionLocalProvider(LocalStrings provides EnglishStrings) {
                MaterialTheme {
                    Column {
                        HerdrPluginPart(installed)
                        HerdrPluginPart(disabled)
                    }
                }
            }
        }

        onNodeWithText("ShogunAi's Herdr plugin is installed").assertIsDisplayed()
        onNodeWithText("ShogunAi's Herdr plugin is disabled in Herdr").assertIsDisplayed()
        assertEquals(0, onAllNodesWithText("Reinstall").fetchSemanticsNodes().size)
        assertEquals(0, onAllNodesWithText("Install").fetchSemanticsNodes().size)
    }

    @Test
    fun `without Herdr the hooks section shows nothing about it`() = runComposeUiTest {
        val settings = settings(object : HerdrClient by FakeHerdrClient() {
            override fun isInstalled() = false
        })
        show(settings)

        assertEquals(0, onAllNodesWithText(EnglishStrings.herdrPluginLabel).fetchSemanticsNodes().size)
    }

    @Test
    fun `a Herdr that can't be checked says why`() = runComposeUiTest {
        val settings = settings(FakeHerdrClient(pluginsFailure = WorktreeError.HerdrCommandFailed(listOf("herdr"), "no server running")))
        show(settings)

        onNodeWithText("Couldn't check ShogunAi's Herdr plugin").assertIsDisplayed()
        onNodeWithText("Herdr says: no server running").assertIsDisplayed()
        settings.request()
        assertEquals(null, settings.pending)
    }

    @Test
    fun `nothing is requested or run before the state is read, and an error can be dismissed`() = runBlocking {
        val herdr = FakeHerdrClient(
            plugins = listOf(brokenLink),
            pluginStepFailures = mapOf(install to WorktreeError.HerdrCommandFailed(listOf("herdr"), "network unreachable")),
        )
        val settings = HerdrPluginSettings(HerdrPluginHealth(herdr))

        settings.request()
        settings.confirm()
        assertEquals(null, settings.pending)
        assertTrue(herdr.pluginSteps.isEmpty())

        settings.refresh()
        settings.request()
        settings.confirm()
        settings.dismissError()

        assertEquals(null, settings.error)
    }
}
