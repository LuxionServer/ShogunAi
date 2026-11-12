package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeHerdrClient
import app.luxion.shogunai.domain.io.HerdrClient
import app.luxion.shogunai.domain.model.HerdrPlugin
import app.luxion.shogunai.domain.model.HerdrPluginState
import app.luxion.shogunai.domain.model.HerdrPluginStatus
import app.luxion.shogunai.domain.model.HerdrPluginStep
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class HerdrPluginHealthTest {

    private val brokenLink = HerdrPlugin(
        HerdrPlugin.SHOGUN_PLUGIN_ID,
        enabled = true,
        sourceKind = "local",
        root = "/home/dev/projects/worktrees/TASK-7/integrations/herdr",
        warnings = listOf("manifest unavailable: No such file or directory (os error 2)"),
    )
    private val gitHubInstall = HerdrPlugin(HerdrPlugin.SHOGUN_PLUGIN_ID, enabled = true, sourceKind = "github", root = "/home/dev/plugins/checkout")
    private val install = HerdrPluginStep.Install("LuxionServer/ShogunAi/integrations/herdr")

    @Test
    fun `a plugin linked to a deleted folder is broken`() = runTest {
        val status = HerdrPluginHealth(FakeHerdrClient(plugins = listOf(brokenLink))).status()

        assertEquals(HerdrPluginStatus(HerdrPluginState.BROKEN, brokenLink), status)
    }

    @Test
    fun `warnings make a disabled plugin broken too`() = runTest {
        val disabledWithWarnings = brokenLink.copy(enabled = false)

        assertEquals(HerdrPluginState.BROKEN, HerdrPluginHealth(FakeHerdrClient(plugins = listOf(disabledWithWarnings))).status()?.state)
    }

    @Test
    fun `an enabled plugin without warnings is installed, whether from GitHub or linked`() = runTest {
        val linked = brokenLink.copy(warnings = emptyList())

        assertEquals(HerdrPluginStatus(HerdrPluginState.INSTALLED, gitHubInstall), HerdrPluginHealth(FakeHerdrClient(plugins = listOf(gitHubInstall))).status())
        assertEquals(HerdrPluginState.INSTALLED, HerdrPluginHealth(FakeHerdrClient(plugins = listOf(linked))).status()?.state)
    }

    @Test
    fun `a plugin disabled by the user is disabled`() = runTest {
        val disabled = gitHubInstall.copy(enabled = false)

        assertEquals(HerdrPluginState.DISABLED, HerdrPluginHealth(FakeHerdrClient(plugins = listOf(disabled))).status()?.state)
    }

    @Test
    fun `without ShogunAi's plugin it is missing, whatever other plugins there are`() = runTest {
        val other = HerdrPlugin("example.other", enabled = true, sourceKind = "local", root = "/home/dev/plugins/other")

        assertEquals(HerdrPluginStatus(HerdrPluginState.MISSING), HerdrPluginHealth(FakeHerdrClient(plugins = listOf(other))).status())
    }

    @Test
    fun `without Herdr there is no status`() = runTest {
        val notInstalled = object : HerdrClient by FakeHerdrClient() {
            override fun isInstalled() = false
        }

        assertNull(HerdrPluginHealth(notInstalled).status())
    }

    @Test
    fun `when Herdr fails the state is unknown with its message`() = runTest {
        val failing = FakeHerdrClient(pluginsFailure = WorktreeError.HerdrCommandFailed(listOf("herdr", "plugin", "list", "--json"), "no server running"))

        assertEquals(HerdrPluginStatus(HerdrPluginState.UNKNOWN, error = "no server running"), HerdrPluginHealth(failing).status())
    }

    @Test
    fun `a broken link is unlinked and installed from the repository`() {
        val steps = HerdrPluginHealth(FakeHerdrClient()).reinstallSteps(HerdrPluginStatus(HerdrPluginState.BROKEN, brokenLink))

        assertEquals(listOf(HerdrPluginStep.Unlink("shogunai.worktrees"), install), steps)
    }

    @Test
    fun `a broken GitHub install is uninstalled before installing it again`() {
        val steps = HerdrPluginHealth(FakeHerdrClient()).reinstallSteps(HerdrPluginStatus(HerdrPluginState.BROKEN, gitHubInstall.copy(sourceKind = null)))

        assertEquals(listOf(HerdrPluginStep.Uninstall("shogunai.worktrees"), install), steps)
    }

    @Test
    fun `a broken status without its plugin is only installed`() {
        val steps = HerdrPluginHealth(FakeHerdrClient()).reinstallSteps(HerdrPluginStatus(HerdrPluginState.BROKEN))

        assertEquals(listOf<HerdrPluginStep>(install), steps)
    }

    @Test
    fun `a missing plugin is only installed, and nothing is done in any other state`() {
        val health = HerdrPluginHealth(FakeHerdrClient())

        assertEquals(listOf(install), health.reinstallSteps(HerdrPluginStatus(HerdrPluginState.MISSING)))
        listOf(HerdrPluginState.INSTALLED, HerdrPluginState.DISABLED, HerdrPluginState.UNKNOWN).forEach {
            assertTrue(health.reinstallSteps(HerdrPluginStatus(it, gitHubInstall)).isEmpty())
        }
    }

    @Test
    fun `reinstalling runs the steps in order and reads the status again`() = runTest {
        val herdr = FakeHerdrClient(plugins = listOf(brokenLink))
        val health = HerdrPluginHealth(herdr)

        val result = health.reinstall(health.reinstallSteps(checkNotNull(health.status())))

        assertEquals(listOf(HerdrPluginStep.Unlink("shogunai.worktrees"), install), herdr.pluginSteps)
        assertEquals(HerdrPluginState.INSTALLED, result.getOrThrow()?.state)
    }

    @Test
    fun `a failing step stops the reinstall and is returned`() = runTest {
        val failure = WorktreeError.HerdrCommandFailed(listOf("herdr", "plugin", "install"), "network unreachable")
        val herdr = FakeHerdrClient(plugins = listOf(brokenLink), pluginStepFailures = mapOf(install to failure))
        val health = HerdrPluginHealth(herdr)

        val result = health.reinstall(listOf(HerdrPluginStep.Unlink("shogunai.worktrees"), install, HerdrPluginStep.Unlink("other")))

        assertSame(failure, result.exceptionOrNull())
        assertEquals(listOf<HerdrPluginStep>(HerdrPluginStep.Unlink("shogunai.worktrees")), herdr.pluginSteps)
    }

    @Test
    fun `cancelling a reinstall isn't reported as a failure`() = runTest {
        val herdr = FakeHerdrClient(pluginStepFailures = mapOf(install to CancellationException("cancelled")))

        assertFailsWith<CancellationException> { HerdrPluginHealth(herdr).reinstall(listOf(install)) }
    }
}
