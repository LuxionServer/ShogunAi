package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.io.HerdrClient
import app.luxion.shogunai.domain.model.HerdrPlugin
import app.luxion.shogunai.domain.model.HerdrPluginState
import app.luxion.shogunai.domain.model.HerdrPluginStatus
import app.luxion.shogunai.domain.model.HerdrPluginStep
import app.luxion.shogunai.domain.model.WorktreeError
import kotlin.coroutines.cancellation.CancellationException

class HerdrPluginHealth(private val herdr: HerdrClient) {

    suspend fun status(): HerdrPluginStatus? {
        if (!herdr.isInstalled()) return null
        val plugins = try {
            herdr.listPlugins()
        } catch (error: WorktreeError) {
            return HerdrPluginStatus(HerdrPluginState.UNKNOWN, error = error.herdrDetail())
        }
        val plugin = plugins.firstOrNull { it.id == HerdrPlugin.SHOGUN_PLUGIN_ID }
            ?: return HerdrPluginStatus(HerdrPluginState.MISSING)
        val state = when {
            plugin.warnings.isNotEmpty() -> HerdrPluginState.BROKEN
            plugin.enabled -> HerdrPluginState.INSTALLED
            else -> HerdrPluginState.DISABLED
        }
        return HerdrPluginStatus(state, plugin)
    }

    fun reinstallSteps(status: HerdrPluginStatus): List<HerdrPluginStep> {
        val install = HerdrPluginStep.Install(HerdrPlugin.SHOGUN_PLUGIN_SOURCE)
        return when (status.state) {
            HerdrPluginState.MISSING -> listOf(install)
            HerdrPluginState.BROKEN -> listOfNotNull(status.plugin?.let(::removal)) + install
            else -> emptyList()
        }
    }

    suspend fun reinstall(steps: List<HerdrPluginStep>): Result<HerdrPluginStatus?> {
        try {
            steps.forEach { herdr.changePlugins(it) }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            return Result.failure(error)
        }
        return Result.success(status())
    }

    private fun removal(plugin: HerdrPlugin): HerdrPluginStep =
        if (plugin.isLinked) HerdrPluginStep.Unlink(plugin.id) else HerdrPluginStep.Uninstall(plugin.id)
}
