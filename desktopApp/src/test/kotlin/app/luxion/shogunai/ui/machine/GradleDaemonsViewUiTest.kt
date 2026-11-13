package app.luxion.shogunai.ui.machine

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import app.luxion.shogunai.MachineUseCases
import app.luxion.shogunai.domain.model.AgentSessionRef
import app.luxion.shogunai.domain.model.DaemonKind
import app.luxion.shogunai.domain.model.DaemonProcess
import app.luxion.shogunai.domain.model.DaemonState
import app.luxion.shogunai.domain.model.DerivedDataDeletion
import app.luxion.shogunai.domain.model.DerivedDataListing
import app.luxion.shogunai.domain.model.DerivedDataQuota
import app.luxion.shogunai.domain.model.DerivedDataQuotaMode
import app.luxion.shogunai.domain.model.DerivedDataTrim
import app.luxion.shogunai.domain.model.GradleClient
import app.luxion.shogunai.domain.model.HoldingBuild
import app.luxion.shogunai.domain.model.PackageCacheSizes
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.ResourceSettings
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.usecase.GradleTrim
import app.luxion.shogunai.ui.i18n.EnglishStrings
import app.luxion.shogunai.ui.i18n.LocalStrings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.emptyFlow
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class GradleDaemonsViewUiTest {

    private val listedPaths = mutableListOf<List<String>>()

    private fun useCases(daemons: List<DaemonProcess>) = MachineUseCases(
        listDaemons = { paths -> listedPaths += paths; Result.success(daemons) },
        stopDaemons = { Result.failure(IllegalStateException()) },
        listDerivedData = { Result.success(DerivedDataListing(emptyList(), null)) },
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
    )

    private fun daemon(pid: Long, state: DaemonState, holder: HoldingBuild? = null) = DaemonProcess(pid, DaemonKind.GRADLE, "8.10", 0, null, state, holder)

    private fun project(name: String, worktrees: List<Worktree> = emptyList()) =
        MachineProject(Project(name, name, ProjectConfig("/home/dev/projects/$name", "/home/dev/projects", emptyList())), worktrees, setOf(MachineResource.GRADLE_DAEMONS), gradleBuildCacheEnabled = false)

    private fun ComposeUiTest.showView(daemons: List<DaemonProcess>, projects: List<MachineProject> = listOf(project("main-repo"))): Job {
        val job = Job()
        val machine = MachineResources(
            scope = CoroutineScope(Dispatchers.Unconfined + job),
            useCases = useCases(daemons),
            projects = { projects },
            pathExists = { true },
            ioDispatcher = Dispatchers.Unconfined,
        )
        machine.show(MachineResource.GRADLE_DAEMONS)
        setContent {
            CompositionLocalProvider(LocalStrings provides EnglishStrings) {
                MaterialTheme { GradleDaemonsView(machine) { _, _ -> } }
            }
        }
        return job
    }

    @Test
    fun `all busy daemons explain why stop idle is disabled`() = runComposeUiTest {
        val job = showView((1L..9L).map { daemon(it, DaemonState.Busy) })

        onNodeWithText("All 9 are busy, none is idle").assertIsDisplayed()
        onNodeWithText("Stop idle daemons").assertIsNotEnabled()
        assertEquals(9, onAllNodesWithText("busy").fetchSemanticsNodes().size)
        job.cancel()
    }

    @Test
    fun `unknown states drop the state column and unknown owners are dimmed`() = runComposeUiTest {
        val job = showView((1L..4L).map { daemon(it, DaemonState.Unknown) })

        assertTrue(onAllNodesWithText("state unknown").fetchSemanticsNodes().isEmpty())
        assertEquals(4, onAllNodesWithTag("owner-unknown", useUnmergedTree = true).fetchSemanticsNodes().size)
        onNodeWithText("The state of all 4 is unknown, none can be stopped safely").assertIsDisplayed()
        job.cancel()
    }

    @Test
    fun `one known state keeps the column for every row`() = runComposeUiTest {
        val job = showView(listOf(daemon(1, DaemonState.Idle(Instant.now()))) + (2L..4L).map { daemon(it, DaemonState.Unknown) })

        assertEquals(3, onAllNodesWithText("state unknown").fetchSemanticsNodes().size)
        assertTrue(onAllNodesWithTag("stop-idle-reason", useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        job.cancel()
    }

    @Test
    fun `projects with the build cache off share one notice with details`() = runComposeUiTest {
        val job = showView(emptyList(), listOf(project("api"), project("main-repo"), project("web")))

        onNodeWithText(EnglishStrings.gradleBuildCacheOffIn(3)).assertIsDisplayed()
        assertTrue(onAllNodesWithText("web").fetchSemanticsNodes().isEmpty())
        onNodeWithText("Details (3)").performClick()
        waitForIdle()
        onNodeWithText("web").assertIsDisplayed()
        job.cancel()
    }

    @Test
    fun `a busy daemon shows the build that holds it under its row and an idle one doesn't`() = runComposeUiTest {
        val session = AgentSessionRef("1a2b3c4d-0000-0000-0000-000000000000", "claude", "/home/dev/projects/worktrees/TASK-123")
        val holder = HoldingBuild("/home/dev/projects/worktrees/TASK-123", Instant.now().minusSeconds(180), GradleClient(4242, listOf(":app:assembleDebug"), session = session))
        val job = showView(listOf(daemon(1, DaemonState.Busy, holder), daemon(2, DaemonState.Idle(Instant.now()))))

        onNodeWithTag("daemon-build-1", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertTextEquals("Building :app:assembleDebug · started 3 minutes ago · agent session claude 1a2b3c4d")
        assertTrue(onAllNodesWithTag("daemon-build-2", useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        job.cancel()
    }

    @Test
    fun `the listing receives every saved project's worktrees to find agent sessions`() = runComposeUiTest {
        val projects = listOf(
            project("api", listOf(Worktree("/home/dev/projects/api", "main", isMain = true), Worktree("/home/dev/projects/worktrees/api/TASK-123", "feature/TASK-123"))),
            project("web", listOf(Worktree("/home/dev/projects/web", "main", isMain = true))),
        )
        val job = showView(emptyList(), projects)

        assertEquals(
            listOf("/home/dev/projects/api", "/home/dev/projects/worktrees/api/TASK-123", "/home/dev/projects/web"),
            listedPaths.first(),
        )
        job.cancel()
    }
}
