package app.luxion.shogunai.ui.machine

import androidx.compose.material3.MaterialTheme
import app.luxion.shogunai.MachineUseCases
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import app.luxion.shogunai.domain.model.DesktopTurn
import app.luxion.shogunai.domain.model.DesktopTurnRelease
import app.luxion.shogunai.domain.model.DesktopTurnState
import app.luxion.shogunai.domain.model.ProcessIdentity
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.ResourceSettings
import app.luxion.shogunai.domain.model.WaitTicket
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.toLocalClockTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class DesktopTurnViewUiTest {

    private val project = Project(
        id = "api",
        name = "api",
        config = ProjectConfig(baseRepositoryPath = "/home/dev/projects/api", worktreesRoot = "/home/dev/projects", secretFiles = emptyList()),
    )
    private val holderPath = "/home/dev/projects/api-TASK-1"
    private val waiterPath = "/home/dev/projects/api-TASK-2"
    private val acquiredAt = Instant.parse("2026-01-05T14:00:00Z")
    private val expiresAt = acquiredAt.plusSeconds(600)
    private val waitingSince = Instant.parse("2026-01-05T14:03:00Z")

    private var turn = DesktopTurnState(
        DesktopTurn("/home/dev/projects/api/.git/worktrees/api-TASK-1", holderPath, acquiredAt.toEpochMilli(), expiresAt.toEpochMilli()),
        listOf(WaitTicket("t", "/home/dev/projects/api/.git/worktrees/api-TASK-2", waiterPath, ProcessIdentity(5, 1), waitingSince.toEpochMilli())),
    )
    private var releases = 0
    private val watch = DesktopTurnWatch(
        read = { Result.success(turn) },
        forceRelease = {
            releases++
            turn = DesktopTurnState(null, turn.waiting)
            Result.success(DesktopTurnRelease.NobodyHolds)
        },
    )

    private fun machine() = MachineResources(
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        useCases = unusedMachineUseCases(),
        projects = { listOf(MachineProject(project, listOf(Worktree(holderPath, "feature/TASK-1"), Worktree(waiterPath, "feature/TASK-2")), emptySet())) },
        pathExists = { true },
        desktopTurn = watch,
    )

    @Test
    fun `the view lists the holder and the waiters and opens the holder's worktree`() = runComposeUiTest {
        runBlocking { watch.refresh() }
        val opened = mutableListOf<Pair<String, String>>()
        setContent { MaterialTheme { DesktopTurnView(machine()) { projectId, path -> opened += projectId to path } } }

        onNodeWithText("Since ${acquiredAt.toLocalClockTime()} until ${expiresAt.toLocalClockTime()}").assertIsDisplayed()
        onNodeWithText("Waiting since ${waitingSince.toLocalClockTime()}").assertIsDisplayed()
        onNodeWithText("api · api-TASK-1").performClick()

        assertEquals(listOf("api" to holderPath), opened)
    }

    @Test
    fun `release asks first naming the holder and then frees the desktop`() = runComposeUiTest {
        runBlocking { watch.refresh() }
        setContent { MaterialTheme { DesktopTurnView(machine()) { _, _ -> } } }

        onNodeWithText("Release").performClick()
        onNodeWithText("api · api-TASK-1 loses the desktop turn, and the first worktree waiting for it gets it.").assertIsDisplayed()
        onAllNodesWithText("Release")[1].performClick()
        waitUntil { onAllNodesWithText("Nobody holds the desktop.").fetchSemanticsNodes().isNotEmpty() }

        assertEquals(1, releases)
    }

    @Test
    fun `the view follows a release made from the command line`() = runComposeUiTest {
        runBlocking { watch.refresh() }
        setContent { MaterialTheme { DesktopTurnView(machine()) { _, _ -> } } }
        onNodeWithText("Release").assertIsDisplayed()

        turn = DesktopTurnState(null, emptyList())
        runBlocking { watch.refresh() }

        waitUntil { onAllNodesWithText("Nobody holds the desktop.").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun unusedMachineUseCases(): MachineUseCases {
        val unused = IllegalStateException("not used")
        return MachineUseCases(
            listDaemons = { Result.failure(unused) },
            stopDaemons = { Result.failure(unused) },
            listDerivedData = { Result.failure(unused) },
            measureDerivedData = { _, _ -> emptyFlow() },
            measureCompilationCache = { Result.failure(unused) },
            deleteOrphanedDerivedData = { Result.failure(unused) },
            readEmulatorStates = { _, _ -> Result.failure(unused) },
            readRunningEmulators = { Result.failure(unused) },
            startEmulator = { _, _ -> Result.failure(unused) },
            readPool = { _, _, _ -> Result.failure(unused) },
            trimGradleDaemons = { Result.failure(unused) },
            resourceSettings = { ResourceSettings() },
            stopEmulator = { _, _ -> Result.failure(unused) },
            stopEmulatorSerials = { _, _ -> Result.failure(unused) },
            readSimulatorStates = { _, _ -> Result.failure(unused) },
            startSimulator = { _, _ -> Result.failure(unused) },
            stopSimulator = { _, _ -> Result.failure(unused) },
            readSimulatorOrphans = { Result.failure(unused) },
            deleteSimulators = { Result.failure(unused) },
        )
    }
}
