package app.luxion.shogunai.ui.worktree

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import app.luxion.shogunai.domain.model.Divergence
import app.luxion.shogunai.domain.model.AgentState
import app.luxion.shogunai.domain.model.HeadlessAppProcess
import app.luxion.shogunai.domain.model.HeadlessAppState
import app.luxion.shogunai.domain.model.RunningAgent
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeEmulator
import app.luxion.shogunai.domain.model.EmulatorState
import app.luxion.shogunai.domain.model.WorktreeSort
import app.luxion.shogunai.domain.model.WorktreeStatus
import app.luxion.shogunai.domain.model.toLocalClockTime
import app.luxion.shogunai.ui.i18n.EnglishStrings
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.i18n.SpanishStrings
import app.luxion.shogunai.ui.theme.ShogunTheme
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class WorktreeRowUiTest {

    private val now = Instant.parse("2026-10-16T12:00:00Z")

    private fun status(changes: Int = 0, mainBranch: Divergence = Divergence(2, 0)) = WorktreeStatus(
        uncommittedChanges = changes,
        upstream = Divergence(0, 0),
        mainBranch = mainBranch,
        lastCommitAt = now.minus(Duration.ofHours(3)),
    )

    private fun model(
        branch: String,
        status: WorktreeStatus = status(),
        agents: List<RunningAgent> = emptyList(),
        emulator: WorktreeEmulator? = null,
        desktopTurn: WorktreeDesktopTurn? = null,
        headlessApp: HeadlessAppProcess? = null,
    ) = worktreeRowModel(
        worktree = Worktree(path = "/home/dev/projects/${branch.substringAfterLast('/')}", branch = branch),
        status = WorktreeStatusState.Loaded(status),
        mainBranchName = "develop",
        agents = agents,
        emulator = emulator,
        simulator = null,
        origin = null,
        projectName = null,
        inactiveAfter = Duration.ofDays(3),
        now = now,
        strings = EnglishStrings,
        desktopTurn = desktopTurn,
        headlessApp = headlessApp,
    )

    @androidx.compose.runtime.Composable
    private fun Row(model: WorktreeRowModel, compact: Boolean = false) {
        WorktreeRow(
            model = model,
            selected = false,
            isCopied = false,
            isMain = false,
            compact = compact,
            onSelect = {},
            onCopyLaunchCommand = {},
            onCopyPath = {},
            onOpenInHerdr = {},
            onRemove = {},
        )
    }

    @Test
    fun `cells of rows with different contents start at the same position`() = runComposeUiTest {
        val busy = model(
            "feature/TASK-1",
            status = status(changes = 3),
            agents = listOf(RunningAgent("claude", AgentState.BLOCKED)),
            emulator = WorktreeEmulator("emulator-5556", EmulatorState.RUNNING),
        )
        val bare = model("feature/TASK-2")
        setContent {
            MaterialTheme {
                Column(Modifier.width(900.dp)) {
                    Row(busy)
                    Row(bare)
                }
            }
        }

        listOf(BranchTag, GitTag, TimeTag, ExecutionTag).forEach { tag ->
            val positions = onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().map { it.positionInRoot.x }
            assertEquals(2, positions.size, tag)
            assertEquals(positions[0], positions[1], tag)
        }
    }

    @Test
    fun `the badge exposes the name of the state`() = runComposeUiTest {
        setContent { MaterialTheme { Row(model("feature/TASK-1", status = status(changes = 2))) } }

        onNodeWithContentDescription("Needs you", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `a long branch name keeps the other cells in place`() = runComposeUiTest {
        val long = model("feature/" + "very-long-branch-name-".repeat(8))
        val short = model("feature/TASK-2")
        setContent {
            MaterialTheme {
                Column(Modifier.width(700.dp)) {
                    Row(long)
                    Row(short)
                }
            }
        }

        val times = onAllNodesWithTag(TimeTag, useUnmergedTree = true).fetchSemanticsNodes().map { it.positionInRoot.x }
        assertEquals(times[0], times[1])
    }

    @Test
    fun `a narrow row shows icons only and keeps the agent's state in its tooltip`() = runComposeUiTest {
        val blocked = model("feature/TASK-1", agents = listOf(RunningAgent("claude", AgentState.BLOCKED)))
        val working = model("feature/TASK-2", agents = listOf(RunningAgent("codex", AgentState.WORKING)))
        setContent {
            MaterialTheme {
                Column(Modifier.width(500.dp)) {
                    Row(blocked, compact = true)
                    Row(working, compact = true)
                }
            }
        }

        assertEquals(0, onAllNodesWithText("Claude · needs you", useUnmergedTree = true).fetchSemanticsNodes().size)
        assertEquals(2, onAllNodesWithTag(ExecutionTag, useUnmergedTree = true).fetchSemanticsNodes().size)
        val widths = onAllNodesWithTag(ExecutionTag, useUnmergedTree = true).fetchSemanticsNodes().map { it.size.width }
        assertTrue(widths.all { it == widths.first() })
        onNodeWithContentDescription("Claude · needs you", useUnmergedTree = true).performMouseInput { moveTo(center) }
        mainClock.advanceTimeBy(2_000)

        onNodeWithText("Claude · needs you\nOnly its process was found").assertIsDisplayed()
    }

    @Test
    fun `a wide row shows the desktop wait with the count of the rest`() = runComposeUiTest {
        val waiting = model(
            "feature/TASK-1",
            desktopTurn = WorktreeDesktopTurn.Waiting(2),
            headlessApp = HeadlessAppProcess(HeadlessAppState.RUNNING, 4242, "/home/dev/launch.log"),
        )
        setContent { MaterialTheme { Column(Modifier.width(900.dp)) { Row(waiting) } } }

        onNodeWithText("Waiting for the desktop, #2").assertIsDisplayed()
        onNodeWithText("+1").assertIsDisplayed()
    }

    @Test
    fun `a narrow row keeps the desktop turn and the app in the second line's tooltip`() = runComposeUiTest {
        val holding = model(
            "feature/TASK-1",
            desktopTurn = WorktreeDesktopTurn.Holding(now.toEpochMilli()),
            headlessApp = HeadlessAppProcess(HeadlessAppState.RUNNING, 4242, "/home/dev/launch.log"),
        )
        setContent { MaterialTheme { Column(Modifier.width(500.dp)) { Row(holding, compact = true) } } }

        assertEquals(0, onAllNodesWithText("Has the desktop", useUnmergedTree = true).fetchSemanticsNodes().size)
        onNodeWithContentDescription("Has the desktop", useUnmergedTree = true).performMouseInput { moveTo(center) }
        mainClock.advanceTimeBy(2_000)

        onNodeWithText("Has the desktop until ${now.toLocalClockTime()}\nApp in the background").assertIsDisplayed()
    }

    @Test
    fun `a wide row shows the agent's label`() = runComposeUiTest {
        val blocked = model("feature/TASK-1", agents = listOf(RunningAgent("claude", AgentState.BLOCKED)))
        setContent { MaterialTheme { Column(Modifier.width(900.dp)) { Row(blocked) } } }

        onNodeWithText("Claude · needs you", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `the sort selector reports the picked order`() = runComposeUiTest {
        val picks = mutableListOf<WorktreeSort>()
        var selected by mutableStateOf(WorktreeSort.NAME)
        setContent {
            MaterialTheme {
                SortSelector(selected) {
                    picks += it
                    selected = it
                }
            }
        }

        onNodeWithText("Last activity").performClick()

        assertEquals(listOf(WorktreeSort.ACTIVITY), picks)
    }

    @Test
    fun `the sort selector is translated`() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalStrings provides SpanishStrings) {
                MaterialTheme { SortSelector(WorktreeSort.NAME) {} }
            }
        }

        onNodeWithText("Nombre").assertIsDisplayed()
        onNodeWithText("Última actividad").assertIsDisplayed()
    }
}
