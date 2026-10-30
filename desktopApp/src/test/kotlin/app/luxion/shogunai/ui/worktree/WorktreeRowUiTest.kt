package app.luxion.shogunai.ui.worktree

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
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
import app.luxion.shogunai.domain.model.AgentStateSource
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
import kotlin.test.assertFalse
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
        projectName: String? = null,
        groupTag: String? = null,
        alias: String? = null,
    ) = worktreeRowModel(
        worktree = Worktree(path = "/home/dev/projects/${branch.substringAfterLast('/')}", branch = branch),
        status = WorktreeStatusState.Loaded(status),
        mainBranchName = "develop",
        agents = agents,
        emulator = emulator,
        simulator = null,
        origin = null,
        projectName = projectName,
        inactiveAfter = Duration.ofDays(3),
        now = now,
        strings = EnglishStrings,
        alias = alias,
        groupTag = groupTag,
        desktopTurn = desktopTurn,
        headlessApp = headlessApp,
    )

    @androidx.compose.runtime.Composable
    private fun Row(model: WorktreeRowModel, compact: Boolean = false, onFocusAgent: () -> Unit = {}) {
        WorktreeRow(
            model = model,
            selected = false,
            isCopied = false,
            compact = compact,
            onSelect = {},
            onCopyLaunchCommand = {},
            onFocusAgent = onFocusAgent,
        )
    }

    @Test
    fun `clicking the agent indicator focuses an agent in a known runner`() {
        listOf(false, true).forEach { compact ->
            runComposeUiTest {
                var focused = 0
                val agents = listOf(RunningAgent("claude", AgentState.BLOCKED, "w1:p1", AgentStateSource.HERDR))
                setContent {
                    MaterialTheme {
                        Column(Modifier.width(900.dp)) { Row(model("feature/TASK-1", agents = agents), compact, onFocusAgent = { focused++ }) }
                    }
                }

                onNodeWithContentDescription("Claude · needs you").performClick()

                assertEquals(1, focused, "compact = $compact")
            }
        }
    }

    @Test
    fun `the agent indicator of an agent without a known runner can't be clicked`() = runComposeUiTest {
        setContent { MaterialTheme { Row(model("feature/TASK-1", agents = listOf(RunningAgent("claude"))), onFocusAgent = {}) } }

        onNodeWithContentDescription("Claude · running", useUnmergedTree = true).assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
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
    fun `the second line separates the group tag from the project`() = runComposeUiTest {
        setContent { MaterialTheme { Row(model("feature/TASK-7", projectName = "main-repo", groupTag = "TASK-7 · +app")) } }

        onNodeWithText("TASK-7 · +app", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("· main-repo", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `a compact row shows the compact activity`() = runComposeUiTest {
        setContent { MaterialTheme { Column(Modifier.width(500.dp)) { Row(model("feature/TASK-1"), compact = true) } } }

        onNodeWithText("3 h", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("commit 3 hours ago", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `a wide row shows the full activity`() = runComposeUiTest {
        setContent { MaterialTheme { Row(model("feature/TASK-1")) } }

        onNodeWithText("commit 3 hours ago", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `a measured alias column shows the workspace alias whole`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                val width = aliasColumnWidth(listOf("workspace", "app", "api"))
                Column(Modifier.width(900.dp)) {
                    WorktreeRow(
                        model = model("feature/TASK-1", alias = "workspace"),
                        selected = false,
                        isCopied = false,
                        aliasWidth = width,
                        onSelect = {},
                        onCopyLaunchCommand = {},
                    )
                }
            }
        }

        val layouts = mutableListOf<TextLayoutResult>()
        onNodeWithTag(AliasTag, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue(layouts.single().fitsWidth())
    }

    @Test
    fun `a narrow alias column truncates a long alias`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                Column(Modifier.width(900.dp)) {
                    WorktreeRow(
                        model = model("feature/TASK-1", alias = "a-very-long-repository-alias"),
                        selected = false,
                        isCopied = false,
                        aliasWidth = aliasColumnWidth(listOf("a-very-long-repository-alias")),
                        onSelect = {},
                        onCopyLaunchCommand = {},
                    )
                }
            }
        }

        val layouts = mutableListOf<TextLayoutResult>()
        onNodeWithTag(AliasTag, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertFalse(layouts.single().fitsWidth())
    }

    private fun TextLayoutResult.fitsWidth(): Boolean = multiParagraph.maxIntrinsicWidth <= size.width

    @Test
    fun `the alias column fits the usual aliases and is clamped`() {
        assertEquals(48.dp, fitAliasColumnWidth(20.dp))
        assertEquals(80.dp, fitAliasColumnWidth(80.dp))
        assertEquals(128.dp, fitAliasColumnWidth(300.dp))
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
