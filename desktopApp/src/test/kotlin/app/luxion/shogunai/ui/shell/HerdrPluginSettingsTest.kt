package app.luxion.shogunai.ui.shell

import app.luxion.shogunai.domain.FakeHerdrClient
import app.luxion.shogunai.domain.model.HerdrPlugin
import app.luxion.shogunai.domain.model.HerdrPluginState
import app.luxion.shogunai.domain.usecase.HerdrPluginHealth
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HerdrPluginSettingsTest {

    private val installed = HerdrPlugin(HerdrPlugin.SHOGUN_PLUGIN_ID, enabled = true, sourceKind = "local", root = "/home/dev/projects/herdr")

    private fun settings(plugins: List<HerdrPlugin>) =
        HerdrPluginSettings(HerdrPluginHealth(FakeHerdrClient(plugins = plugins))).also { runBlocking { it.refresh() } }

    @Test
    fun `nothing is offered before the plugin is read`() {
        assertNull(HerdrPluginSettings(HerdrPluginHealth(FakeHerdrClient())).action)
    }

    @Test
    fun `a missing plugin offers to install it`() {
        assertEquals(HerdrPluginState.MISSING, settings(emptyList()).action)
    }

    @Test
    fun `an installed plugin offers nothing`() {
        val settings = settings(listOf(installed))

        assertEquals(HerdrPluginState.INSTALLED, settings.status?.state)
        assertNull(settings.action)
    }
}
