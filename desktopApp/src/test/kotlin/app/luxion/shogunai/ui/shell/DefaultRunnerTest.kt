package app.luxion.shogunai.ui.shell

import app.luxion.shogunai.domain.FakeToolDetector
import app.luxion.shogunai.domain.FakeToolPreferenceRepository
import app.luxion.shogunai.domain.model.DetectedTools
import app.luxion.shogunai.domain.model.RunnerId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DefaultRunnerTest {

    private suspend fun tools(installed: Set<RunnerId>?, saved: RunnerId? = null): ToolsPreferences {
        val tools = ToolsPreferences(FakeToolPreferenceRepository(runner = saved), FakeToolDetector(DetectedTools(runners = installed.orEmpty())))
        if (installed != null) tools.refresh()
        return tools
    }

    @Test
    fun `settings without a default runner keep Herdr`() = runTest {
        assertEquals(RunnerId.HERDR, tools(RunnerId.entries.toSet()).defaultRunner)
    }

    @Test
    fun `before detection both runners are offered`() = runTest {
        val tools = tools(installed = null, saved = RunnerId.ORCA)

        assertEquals(RunnerId.entries, tools.installedRunners)
        assertEquals(RunnerId.ORCA, tools.defaultRunner)
    }

    @Test
    fun `an uninstalled choice falls back to an installed runner without forgetting it`() = runTest {
        val tools = tools(setOf(RunnerId.HERDR), saved = RunnerId.ORCA)

        assertEquals(RunnerId.HERDR, tools.defaultRunner)
        assertEquals(RunnerId.ORCA, tools.chosenRunner)
    }

    @Test
    fun `only Orca installed makes it the default`() = runTest {
        assertEquals(RunnerId.ORCA, tools(setOf(RunnerId.ORCA)).defaultRunner)
    }

    @Test
    fun `no runner installed leaves no default`() = runTest {
        assertNull(tools(emptySet()).defaultRunner)
    }

    @Test
    fun `choosing a runner saves it`() = runTest {
        val repository = FakeToolPreferenceRepository()
        val tools = ToolsPreferences(repository, FakeToolDetector(DetectedTools()))
        tools.refresh()

        tools.chooseRunner(RunnerId.ORCA)

        assertEquals(RunnerId.ORCA, repository.runner)
        assertEquals(RunnerId.ORCA, tools.defaultRunner)
    }
}
