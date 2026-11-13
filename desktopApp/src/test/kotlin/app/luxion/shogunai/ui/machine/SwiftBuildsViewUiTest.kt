package app.luxion.shogunai.ui.machine

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import app.luxion.shogunai.MachineUseCases
import app.luxion.shogunai.domain.model.AttributedDerivedData
import app.luxion.shogunai.domain.model.DerivedDataDeletion
import app.luxion.shogunai.domain.model.DerivedDataFolder
import app.luxion.shogunai.domain.model.DerivedDataKind
import app.luxion.shogunai.domain.model.DerivedDataListing
import app.luxion.shogunai.domain.model.DerivedDataOwner
import app.luxion.shogunai.domain.model.DerivedDataTrim
import app.luxion.shogunai.domain.model.MeasuredSize
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.ResourceSettings
import app.luxion.shogunai.domain.model.SwiftBuildDeletion
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.usecase.GradleTrim
import app.luxion.shogunai.ui.i18n.EnglishStrings
import app.luxion.shogunai.ui.i18n.LocalStrings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.flow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class SwiftBuildsViewUiTest {

    private val gigabyte = 1_073_741_824L
    private val task1 = Worktree("/home/dev/projects/worktrees/TASK-1", "feature/TASK-1")
    private val task2 = Worktree("/home/dev/projects/worktrees/TASK-2", "feature/TASK-2")
    private val package1 = MachineProject(
        Project("p", "Package", ProjectConfig("/home/dev/projects/package", "/home/dev/projects/worktrees", emptyList())),
        listOf(task1, task2),
        setOf(MachineResource.XCODE_DERIVED_DATA),
    )
    private val gradleOnly = MachineProject(
        Project("g", "Gradle", ProjectConfig("/home/dev/projects/gradle", "/home/dev/projects/gradle-worktrees", emptyList())),
        emptyList(),
        setOf(MachineResource.GRADLE_DAEMONS),
    )

    private fun build(worktree: Worktree) = AttributedDerivedData(
        DerivedDataFolder("${worktree.path}/.build", worktree.path, null, null, kind = DerivedDataKind.SWIFT_BUILD),
        DerivedDataOwner.Worktree(worktree.path),
    )

    private class Calls {
        val listedFor = mutableListOf<List<MachineProject>>()
        val deletedFor = mutableListOf<List<MachineProject>>()
        val trimmedFor = mutableListOf<List<MachineProject>>()
    }

    private fun useCases(
        calls: Calls,
        folders: List<AttributedDerivedData>,
        deletion: suspend () -> Result<SwiftBuildDeletion> = { Result.success(SwiftBuildDeletion()) },
        settings: ResourceSettings = ResourceSettings(),
    ) = MachineUseCases(
        listDaemons = { Result.success(emptyList()) },
        stopDaemons = { Result.failure(IllegalStateException()) },
        listDerivedData = { declaring -> calls.listedFor += declaring; Result.success(DerivedDataListing(folders, null)) },
        measureDerivedData = { list, _ -> flow { list.forEach { emit(MeasuredSize(it.path, 2 * gigabyte, packagesBytes = 0L)) } } },
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
        readPool = { _, _, _, _ -> Result.failure(IllegalStateException()) },
        trimGradleDaemons = { Result.success(GradleTrim(limit = null)) },
        resourceSettings = { settings },
        deleteUnusedSwiftBuilds = { declaring -> calls.deletedFor += declaring; deletion() },
        trimDerivedData = { quota, declaring, _ -> calls.trimmedFor += declaring; Result.success(DerivedDataTrim(quota, 0L, 0L)) },
    )

    private fun machine(useCases: MachineUseCases, job: Job) = MachineResources(
        scope = CoroutineScope(Dispatchers.Unconfined + job),
        useCases = useCases,
        projects = { listOf(package1, gradleOnly) },
        pathExists = { true },
        ioDispatcher = Dispatchers.Unconfined,
    )

    @Test
    fun `derived data is listed and trimmed with the projects declaring the Xcode provider`() {
        val calls = Calls()
        val job = Job()
        val machine = machine(useCases(calls, listOf(build(task1)), settings = ResourceSettings(derivedDataQuotaGigabytes = 1)), job)

        machine.show(MachineResource.XCODE_DERIVED_DATA)

        assertEquals(listOf(listOf(package1)), calls.listedFor)
        assertEquals(listOf(listOf(package1)), calls.trimmedFor)
        job.cancel()
    }

    @Test
    fun `build folders are shown as rows of their worktree with their total`() = runComposeUiTest {
        val job = Job()
        val machine = machine(useCases(Calls(), listOf(build(task1), build(task2))), job)
        machine.show(MachineResource.XCODE_DERIVED_DATA)

        setContent {
            CompositionLocalProvider(LocalStrings provides EnglishStrings) {
                MaterialTheme { XcodeDerivedDataView(machine) { _, _ -> } }
            }
        }

        onNodeWithText("TASK-1 · .build").assertIsDisplayed()
        onNodeWithText("TASK-2 · .build").assertIsDisplayed()
        onNodeWithText("2 .build folders · 4.0 GB").assertIsDisplayed()
        onNodeWithText("Delete unused .build").assertIsDisplayed()
        job.cancel()
    }

    @Test
    fun `without build folders there is no build total or action`() = runComposeUiTest {
        val job = Job()
        val machine = machine(useCases(Calls(), emptyList()), job)
        machine.show(MachineResource.XCODE_DERIVED_DATA)

        setContent {
            CompositionLocalProvider(LocalStrings provides EnglishStrings) {
                MaterialTheme { XcodeDerivedDataView(machine) { _, _ -> } }
            }
        }

        assertTrue(onAllNodesWithText(".build", substring = true).fetchSemanticsNodes().isEmpty())
        job.cancel()
    }

    @Test
    fun `confirming deletes the unused build folders of the declaring projects and reports them`() = runComposeUiTest {
        val calls = Calls()
        val job = Job()
        val deleted = SwiftBuildDeletion(
            deleted = listOf(build(task1).folder.copy(sizeBytes = 2 * gigabyte)),
            inUse = listOf(build(task2).folder),
        )
        val machine = machine(useCases(calls, listOf(build(task1), build(task2)), deletion = { Result.success(deleted) }), job)
        machine.show(MachineResource.XCODE_DERIVED_DATA)

        setContent {
            CompositionLocalProvider(LocalStrings provides EnglishStrings) {
                MaterialTheme { XcodeDerivedDataView(machine) { _, _ -> } }
            }
        }
        onNodeWithText("Delete unused .build").performClick()
        onNodeWithText("Delete unused .build folders?").assertIsDisplayed()
        onAllNodesWithText("Delete unused .build")[1].performClick()

        assertEquals(listOf(listOf(package1)), calls.deletedFor)
        onNodeWithText("1 .build folder deleted · 2.0 GB freed · 1 kept because in use").assertIsDisplayed()
        assertEquals(2, calls.listedFor.size)
        job.cancel()
    }

    @Test
    fun `a failed deletion is shown with a retry`() {
        val job = Job()
        val failure = IllegalStateException("disk busy")
        val machine = machine(useCases(Calls(), listOf(build(task1)), deletion = { Result.failure(failure) }), job)

        machine.swiftBuilds.deleteUnused()

        assertEquals(failure, machine.error)
        assertEquals(null, machine.swiftBuilds.report)
        assertTrue(!machine.swiftBuilds.isDeleting)
        job.cancel()
    }

    @Test
    fun `a deletion already running is not started twice and its report can be dismissed`() {
        val gate = CompletableDeferred<Result<SwiftBuildDeletion>>()
        val calls = Calls()
        val job = Job()
        val machine = machine(useCases(calls, listOf(build(task1)), deletion = { gate.await() }), job)

        machine.swiftBuilds.deleteUnused()
        machine.swiftBuilds.deleteUnused()
        gate.complete(Result.success(SwiftBuildDeletion()))
        machine.swiftBuilds.dismissReport()

        assertEquals(1, calls.deletedFor.size)
        assertEquals(null, machine.swiftBuilds.report)
        job.cancel()
    }
}
