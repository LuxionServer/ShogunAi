package app.luxion.shogunai.ui.machine

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import app.luxion.shogunai.MachineUseCases
import app.luxion.shogunai.domain.model.AttributedDerivedData
import app.luxion.shogunai.domain.model.ComposeResource
import app.luxion.shogunai.domain.model.ComposeResourceKind
import app.luxion.shogunai.domain.model.DaemonState
import app.luxion.shogunai.domain.model.DerivedDataDeletion
import app.luxion.shogunai.domain.model.DerivedDataFolder
import app.luxion.shogunai.domain.model.DerivedDataListing
import app.luxion.shogunai.domain.model.DerivedDataOwner
import app.luxion.shogunai.domain.model.DerivedDataQuota
import app.luxion.shogunai.domain.model.DerivedDataQuotaMode
import app.luxion.shogunai.domain.model.DerivedDataTrim
import app.luxion.shogunai.domain.model.MavenDaemonProcess
import app.luxion.shogunai.domain.model.PackageCacheSizes
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.ResourceSettings
import app.luxion.shogunai.domain.usecase.GradleTrim
import app.luxion.shogunai.ui.i18n.EnglishStrings
import app.luxion.shogunai.ui.i18n.LocalStrings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.emptyFlow
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class MachineViewsPolishUiTest {

    private val project = Project("main-repo", "main-repo", ProjectConfig("/home/dev/projects/main-repo", "/home/dev/projects", emptyList()))

    private fun useCases(
        mavenDaemons: List<MavenDaemonProcess> = emptyList(),
        derivedData: List<AttributedDerivedData> = emptyList(),
        leftovers: List<ComposeResource> = emptyList(),
    ) = MachineUseCases(
        listDaemons = { Result.success(emptyList()) },
        stopDaemons = { Result.failure(IllegalStateException()) },
        listDerivedData = { Result.success(DerivedDataListing(derivedData, null)) },
        measureDerivedData = { _, _ -> emptyFlow() },
        measureCompilationCache = { Result.success(null) },
        measurePackageCaches = { Result.success(PackageCacheSizes()) },
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
        readPool = { _, _, _, _ -> Result.failure(IllegalStateException()) },
        trimGradleDaemons = { Result.success(GradleTrim(limit = null)) },
        resourceSettings = { ResourceSettings() },
        trimDerivedData = { quota, _, _ -> Result.success(DerivedDataTrim(quota, 0L, 0L)) },
        resolveDerivedDataQuota = { _, _ -> DerivedDataQuota(DerivedDataQuotaMode.NONE, null) },
        listMavenDaemons = { Result.success(mavenDaemons) },
        listComposeLeftovers = { _, _ -> Result.success(leftovers) },
    )

    private fun ComposeUiTest.show(
        resource: MachineResource,
        useCases: MachineUseCases,
        view: @Composable (MachineResources) -> Unit,
    ): Job {
        val job = Job()
        val machine = MachineResources(
            scope = CoroutineScope(Dispatchers.Unconfined + job),
            useCases = useCases,
            projects = { listOf(MachineProject(project, emptyList(), setOf(resource))) },
            pathExists = { true },
            ioDispatcher = Dispatchers.Unconfined,
        )
        machine.show(resource)
        setContent {
            CompositionLocalProvider(LocalStrings provides EnglishStrings) {
                MaterialTheme { view(machine) }
            }
        }
        return job
    }

    @Test
    fun `maven daemons explain the disabled stop actions and dim the unknown owner`() = runComposeUiTest {
        val daemons = listOf(
            MavenDaemonProcess(1, 100, null, DaemonState.Busy),
            MavenDaemonProcess(2, 100, null, DaemonState.Busy),
            MavenDaemonProcess(3, 100, null, DaemonState.Unknown),
        )
        val job = show(MachineResource.MAVEN_DAEMONS, useCases(mavenDaemons = daemons)) { MavenDaemonsView(it) { _, _ -> } }

        onNodeWithText(EnglishStrings.stopIdleBusyAndUnknown(2, 1)).assertIsDisplayed()
        onNodeWithText("Stop idle daemons").assertIsNotEnabled()
        assertEquals(3, onAllNodesWithTag("owner-unknown", useUnmergedTree = true).fetchSemanticsNodes().size)
        val stops = onAllNodesWithText(EnglishStrings.stopDaemon).fetchSemanticsNodes()
        assertEquals(3, stops.size)
        repeat(3) { onAllNodesWithText(EnglishStrings.stopDaemon)[it].assertIsNotEnabled() }
        job.cancel()
    }

    @Test
    fun `derived data without orphans says why delete orphans is disabled`() = runComposeUiTest {
        val folders = listOf(
            AttributedDerivedData(DerivedDataFolder("/dd/A", "/home/dev/projects/main-repo/App.xcodeproj", 10, null), DerivedDataOwner.OtherProject),
        )
        val job = show(MachineResource.XCODE_DERIVED_DATA, useCases(derivedData = folders)) { XcodeDerivedDataView(it) { _, _ -> } }

        onNodeWithText("No folder is orphaned").assertIsDisplayed()
        onNodeWithText("Delete orphans").assertIsNotEnabled()
        job.cancel()
    }

    @Test
    fun `only volumes left disables the removal that keeps them and says why`() = runComposeUiTest {
        val volume = ComposeResource(ComposeResourceKind.VOLUME, "main-repo-task-9_db", "main-repo-task-9_db", "main-repo-task-9")
        val job = show(MachineResource.COMPOSE_PROJECTS, useCases(leftovers = listOf(volume))) { ComposeProjectsView(it) { _, _ -> } }

        onNodeWithText("Only volumes are left").assertIsDisplayed()
        onNodeWithText(EnglishStrings.removeComposeLeftovers).assertIsNotEnabled()
        onNodeWithText(EnglishStrings.removeComposeLeftoversWithVolumes).assertIsEnabled()
        job.cancel()
    }
}
