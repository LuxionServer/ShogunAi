package app.luxion.shogunai.ui.machine

import app.luxion.shogunai.domain.model.ResourceSettings
import app.luxion.shogunai.domain.usecase.GradleTrim
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import app.luxion.shogunai.MachineUseCases
import app.luxion.shogunai.domain.model.AttributedDerivedData
import app.luxion.shogunai.domain.model.DerivedDataDeletion
import app.luxion.shogunai.domain.model.DerivedDataFolder
import app.luxion.shogunai.domain.model.DerivedDataListing
import app.luxion.shogunai.domain.model.DerivedDataOwner
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.ui.i18n.EnglishStrings
import app.luxion.shogunai.ui.i18n.LocalStrings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class XcodeDerivedDataViewUiTest {

    private val folders = listOf(
        AttributedDerivedData(DerivedDataFolder("/dd/A", "/home/dev/projects/main-repo/App.xcodeproj", null, null), DerivedDataOwner.OtherProject),
        AttributedDerivedData(DerivedDataFolder("/dd/B", "/gone/Gone.xcodeproj", null, null), DerivedDataOwner.Orphaned),
    )

    private fun useCases(measuring: CompletableDeferred<Long>) = MachineUseCases(
        listDaemons = { Result.success(emptyList()) },
        stopDaemons = { Result.failure(IllegalStateException()) },
        listDerivedData = { Result.success(DerivedDataListing(folders, null)) },
        measureDerivedData = { Result.success(measuring.await()) },
        measureCompilationCache = { Result.success(null) },
        deleteOrphanedDerivedData = { Result.success(DerivedDataDeletion()) },
        readEmulatorStates = { _, _ -> Result.success(emptyMap()) },
        readRunningEmulators = { Result.success(emptyMap()) },
        startEmulator = { _, _ -> Result.failure(IllegalStateException()) },
        stopEmulator = { _, _ -> Result.success(null) },
        stopEmulatorSerials = { _, _ -> Result.success(emptyList()) },
        readSimulatorStates = { _, _ -> Result.success(emptyMap()) },
        startSimulator = { _, _ -> Result.failure(IllegalStateException()) },
        stopSimulator = { _, _ -> Result.success(null) },
        readSimulatorOrphans = { Result.success(emptyList()) },
        deleteSimulators = { Result.success(it) },
        readPool = { _, _, _ -> Result.failure(IllegalStateException()) },
        trimGradleDaemons = { Result.success(GradleTrim(limit = null)) },
        resourceSettings = { ResourceSettings() },
    )

    @Test
    fun `folders are listed with a calculating state while their sizes are unknown`() = runComposeUiTest {
        val job = Job()
        val project = Project("p", "Main repo", ProjectConfig("/home/dev/projects/main-repo", "/home/dev/projects", emptyList()))
        val machine = MachineResources(
            scope = CoroutineScope(Dispatchers.Unconfined + job),
            useCases = useCases(CompletableDeferred()),
            projects = { listOf(MachineProject(project, emptyList(), setOf(MachineResource.XCODE_DERIVED_DATA))) },
            pathExists = { true },
        )
        machine.show(MachineResource.XCODE_DERIVED_DATA)

        setContent {
            CompositionLocalProvider(LocalStrings provides EnglishStrings) {
                MaterialTheme { XcodeDerivedDataView(machine) { _, _ -> } }
            }
        }

        onNodeWithText("App.xcodeproj").assertIsDisplayed()
        onNodeWithText("Delete orphans").assertIsDisplayed()
        assertTrue(onAllNodesWithText("Calculating…", substring = true).fetchSemanticsNodes().size >= 3)
        job.cancel()
    }
}
