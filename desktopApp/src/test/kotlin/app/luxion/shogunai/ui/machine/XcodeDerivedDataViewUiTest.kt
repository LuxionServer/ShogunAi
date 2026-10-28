package app.luxion.shogunai.ui.machine

import app.luxion.shogunai.domain.model.PackageCacheSizes
import app.luxion.shogunai.domain.model.DerivedDataQuota
import app.luxion.shogunai.domain.model.DerivedDataQuotaMode
import app.luxion.shogunai.domain.model.MeasuredSize
import app.luxion.shogunai.domain.model.ResourceSettings
import app.luxion.shogunai.domain.usecase.GradleTrim
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import app.luxion.shogunai.MachineUseCases
import app.luxion.shogunai.domain.model.AttributedDerivedData
import app.luxion.shogunai.domain.model.DerivedDataDeletion
import app.luxion.shogunai.domain.model.DerivedDataFolder
import app.luxion.shogunai.domain.model.DerivedDataListing
import app.luxion.shogunai.domain.model.DerivedDataOwner
import app.luxion.shogunai.domain.model.DerivedDataTrim
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.ui.i18n.EnglishStrings
import app.luxion.shogunai.ui.i18n.LocalStrings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.flow

@OptIn(ExperimentalTestApi::class)
class XcodeDerivedDataViewUiTest {

    private val folders = listOf(
        AttributedDerivedData(DerivedDataFolder("/dd/A", "/home/dev/projects/main-repo/App.xcodeproj", null, null), DerivedDataOwner.OtherProject),
        AttributedDerivedData(DerivedDataFolder("/dd/B", "/gone/Gone.xcodeproj", null, null), DerivedDataOwner.Orphaned),
    )

    private fun useCases(
        measuring: CompletableDeferred<Long>,
        settings: ResourceSettings = ResourceSettings(),
        trim: DerivedDataTrim? = null,
        automaticQuota: Long? = null,
        packages: Long? = null,
        packageCaches: PackageCacheSizes = PackageCacheSizes(),
    ) = MachineUseCases(
        listDaemons = { Result.success(emptyList()) },
        stopDaemons = { Result.failure(IllegalStateException()) },
        listDerivedData = { Result.success(DerivedDataListing(folders, null)) },
        measureDerivedData = { list, _ -> flow { list.forEach { emit(MeasuredSize(it.path, measuring.await(), packagesBytes = packages)) } } },
        measureCompilationCache = { Result.success(null) },
        measurePackageCaches = { Result.success(packageCaches) },
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
        resourceSettings = { settings },
        trimDerivedData = { quota, _, _ -> Result.success(trim ?: DerivedDataTrim(quota, 0L, 0L)) },
        resolveDerivedDataQuota = { current, _ ->
            when (current.quotaMode) {
                DerivedDataQuotaMode.AUTOMATIC -> DerivedDataQuota(DerivedDataQuotaMode.AUTOMATIC, automaticQuota)
                DerivedDataQuotaMode.FIXED -> DerivedDataQuota(DerivedDataQuotaMode.FIXED, current.fixedDerivedDataQuotaBytes)
                DerivedDataQuotaMode.NONE -> DerivedDataQuota(DerivedDataQuotaMode.NONE, null)
            }
        },
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
            ioDispatcher = Dispatchers.Unconfined,
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

    private val gigabyte = 1_073_741_824L

    @Test
    fun `a folder shows its packages and the shared package caches are listed`() = runComposeUiTest {
        val job = Job()
        val project = Project("p", "Main repo", ProjectConfig("/home/dev/projects/main-repo", "/home/dev/projects", emptyList()))
        val machine = MachineResources(
            scope = CoroutineScope(Dispatchers.Unconfined + job),
            useCases = useCases(
                CompletableDeferred(gigabyte),
                packages = gigabyte / 2,
                packageCaches = PackageCacheSizes(swiftPmBytes = 2 * gigabyte, cocoaPodsBytes = null),
            ),
            projects = { listOf(MachineProject(project, emptyList(), setOf(MachineResource.XCODE_DERIVED_DATA))) },
            pathExists = { true },
            ioDispatcher = Dispatchers.Unconfined,
        )
        machine.show(MachineResource.XCODE_DERIVED_DATA)

        setContent {
            CompositionLocalProvider(LocalStrings provides EnglishStrings) {
                MaterialTheme { XcodeDerivedDataView(machine) { _, _ -> } }
            }
        }

        onNodeWithText("App.xcodeproj · packages 512.0 MB").assertIsDisplayed()
        onNodeWithText("SwiftPM cache · 2.0 GB").assertIsDisplayed()
        assertTrue(onAllNodesWithText("CocoaPods cache", substring = true).fetchSemanticsNodes().isEmpty())
        job.cancel()
    }

    private fun androidx.compose.ui.test.ComposeUiTest.showView(machine: MachineResources) = setContent {
        CompositionLocalProvider(LocalStrings provides EnglishStrings) {
            MaterialTheme { XcodeDerivedDataView(machine) { _, _ -> } }
        }
    }

    private fun quotaMachine(
        job: Job,
        trim: DerivedDataTrim?,
        quotaGigabytes: Int?,
        mode: DerivedDataQuotaMode? = null,
        automaticQuota: Long? = null,
    ): MachineResources {
        val project = Project("p", "Main repo", ProjectConfig("/home/dev/projects/main-repo", "/home/dev/projects", emptyList()))
        val machine = MachineResources(
            scope = CoroutineScope(Dispatchers.Unconfined + job),
            useCases = useCases(CompletableDeferred<Long>().also { it.complete(20 * gigabyte) }, ResourceSettings(derivedDataQuotaGigabytes = quotaGigabytes, derivedDataQuotaMode = mode), trim, automaticQuota),
            projects = { listOf(MachineProject(project, emptyList(), setOf(MachineResource.XCODE_DERIVED_DATA))) },
            pathExists = { true },
            ioDispatcher = Dispatchers.Unconfined,
        )
        machine.show(MachineResource.XCODE_DERIVED_DATA)
        return machine
    }

    @Test
    fun `usage is shown against the quota`() = runComposeUiTest {
        val job = Job()
        showView(quotaMachine(job, null, quotaGigabytes = 50))

        onNodeWithText("40.0 GB of 50.0 GB quota", substring = true).assertIsDisplayed()
        job.cancel()
    }

    @Test
    fun `an exceeded quota is marked and names the worktrees in use`() = runComposeUiTest {
        val job = Job()
        val inUse = AttributedDerivedData(folders[0].folder, DerivedDataOwner.Worktree("/home/dev/projects/TASK-1"))
        val trim = DerivedDataTrim(30 * gigabyte, 40 * gigabyte, 40 * gigabyte, inUse = listOf(inUse))
        showView(quotaMachine(job, trim, quotaGigabytes = 30))

        onNodeWithText("Over the quota; kept because they are in use: TASK-1").assertIsDisplayed()
        job.cancel()
    }

    @Test
    fun `released folders are reported with the space freed`() = runComposeUiTest {
        val job = Job()
        val released = AttributedDerivedData(folders[1].folder.copy(sizeBytes = 20 * gigabyte), DerivedDataOwner.Worktree("/home/dev/projects/TASK-2"))
        val trim = DerivedDataTrim(30 * gigabyte, 40 * gigabyte, 20 * gigabyte, released = listOf(released))
        showView(quotaMachine(job, trim, quotaGigabytes = 30))

        onNodeWithText("Released 1 folder to honour the quota, freeing 20.0 GB").assertIsDisplayed()
        onNodeWithText("Gone.xcodeproj · TASK-2").assertIsDisplayed()
        job.cancel()
    }

    @Test
    fun `without a quota the view shows no quota line`() = runComposeUiTest {
        val job = Job()
        showView(quotaMachine(job, null, quotaGigabytes = 50, mode = DerivedDataQuotaMode.NONE))

        assertTrue(onAllNodesWithText("quota", substring = true).fetchSemanticsNodes().isEmpty())
        job.cancel()
    }

    @Test
    fun `an automatic quota is shown as automatic`() = runComposeUiTest {
        val job = Job()
        showView(quotaMachine(job, null, quotaGigabytes = null, automaticQuota = 45 * gigabyte))

        onNodeWithText("40.0 GB of 45.0 GB quota (automatic)").assertIsDisplayed()
        job.cancel()
    }

    @Test
    fun `an automatic quota that can't be computed is shown as unknown`() = runComposeUiTest {
        val job = Job()
        showView(quotaMachine(job, null, quotaGigabytes = null))

        onNodeWithText("Automatic quota unknown: the free disk space couldn't be read").assertIsDisplayed()
        job.cancel()
    }

    @Test
    fun `sizes still being calculated release nothing`() = runComposeUiTest {
        val job = Job()
        val project = Project("p", "Main repo", ProjectConfig("/home/dev/projects/main-repo", "/home/dev/projects", emptyList()))
        val machine = MachineResources(
            scope = CoroutineScope(Dispatchers.Unconfined + job),
            useCases = useCases(CompletableDeferred(), ResourceSettings(derivedDataQuotaGigabytes = 30)),
            projects = { listOf(MachineProject(project, emptyList(), setOf(MachineResource.XCODE_DERIVED_DATA))) },
            pathExists = { true },
            ioDispatcher = Dispatchers.Unconfined,
        )
        machine.show(MachineResource.XCODE_DERIVED_DATA)
        showView(machine)

        assertTrue(onAllNodesWithText("Calculating…", substring = true).fetchSemanticsNodes().size >= 3)
        assertTrue(onAllNodesWithText("Released", substring = true).fetchSemanticsNodes().isEmpty())
        job.cancel()
    }

    @Test
    fun `the sidebar summary shows usage and quota`() {
        val summary = MachineSummary(count = 3, bytes = 32 * gigabyte, quotaBytes = 50 * gigabyte)

        assertEquals("32.0 GB of 50.0 GB quota", summary.text(MachineResource.XCODE_DERIVED_DATA, EnglishStrings))
        assertEquals("32.0 GB", summary.copy(quotaBytes = null).text(MachineResource.XCODE_DERIVED_DATA, EnglishStrings))
    }
}
