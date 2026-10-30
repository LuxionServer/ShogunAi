package app.luxion.shogunai.ui.worktree

import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import app.luxion.shogunai.domain.model.AgentLaunchConfig
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.i18n.SpanishStrings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(ExperimentalTestApi::class)
class WorktreeHeaderUiTest {

    private val config = ProjectConfig(
        baseRepositoryPath = "~/projects/main-repo",
        worktreesRoot = "~/projects",
        secretFiles = emptyList(),
        agentLaunchConfig = AgentLaunchConfig(),
    )
    private val projects = listOf(
        Project(id = "api", name = "api", config = config),
        Project(id = "main-repo", name = "main-repo", config = config),
    )

    @Test
    fun `the project filter reports the picked project and all projects`() = runComposeUiTest {
        val picks = mutableListOf<String?>()
        var selectedId by mutableStateOf<String?>(null)
        setContent {
            MaterialTheme {
                ViewProjectFilter(projects, selectedId) { id ->
                    picks += id
                    selectedId = id
                }
            }
        }

        onNodeWithText("All projects").performClick()
        onNodeWithText("api").performClick()
        onNodeWithText("api").performClick()
        onNodeWithText("All projects").performClick()

        assertEquals(listOf<String?>("api", null), picks)
    }

    @Test
    fun `a disabled merged cleanup says why`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                StateGroupHeader(
                    StateGroupKind.MERGED,
                    count = 1,
                    canCleanUp = false,
                    onCleanUp = {},
                    cleanUpBlockedReason = "A cleanup is already running",
                )
            }
        }

        onNodeWithText("Clean up").assertIsNotEnabled()
        onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "A cleanup is already running")).assertExists()
    }

    @Test
    fun `a state group header shows its hint next to its title`() = runComposeUiTest {
        setContent {
            MaterialTheme { StateGroupHeader(StateGroupKind.UNFINISHED, count = 2, canCleanUp = false, onCleanUp = {}) }
        }

        onNodeWithText("Unfinished").assertIsDisplayed()
        onNodeWithText("uncommitted or unpushed").assertIsDisplayed()
    }

    @Test
    fun `the merged group header keeps its cleanup button on one line in Spanish`() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalStrings provides SpanishStrings) {
                MaterialTheme {
                    Box(Modifier.width(220.dp)) {
                        StateGroupHeader(StateGroupKind.MERGED, count = 12, canCleanUp = true, onCleanUp = {})
                    }
                }
            }
        }

        val results = mutableListOf<TextLayoutResult>()
        onNodeWithText(SpanishStrings.cleanUp).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)

        assertEquals(1, results.single().lineCount)
    }

    @Test
    fun `the hint is translated`() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalStrings provides SpanishStrings) {
                MaterialTheme { StateGroupHeader(StateGroupKind.WAITING_FOR_YOU, count = 2, canCleanUp = false, onCleanUp = {}) }
            }
        }

        onNodeWithText("Esperándote").assertIsDisplayed()
        onNodeWithText("agente bloqueado o terminado").assertIsDisplayed()
    }

    @Test
    fun `the waiting header is drawn with its warning icon and the unfinished header without one`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                Column {
                    StateGroupHeader(StateGroupKind.WAITING_FOR_YOU, count = 1, canCleanUp = false, onCleanUp = {})
                    StateGroupHeader(StateGroupKind.UNFINISHED, count = 1, canCleanUp = false, onCleanUp = {})
                }
            }
        }

        onNodeWithText("Waiting for you").assertIsDisplayed()
        onNodeWithText("Unfinished").assertIsDisplayed()
        onAllNodesWithTag(WaitingHeaderIconTag, useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun `a state group header toggles its group and says whether it expands or collapses`() = runComposeUiTest {
        var collapsed by mutableStateOf(true)
        setContent {
            MaterialTheme {
                StateGroupHeader(StateGroupKind.STOPPED, count = 2, canCleanUp = false, onCleanUp = {}, collapsed = collapsed, onToggle = { collapsed = !collapsed })
            }
        }

        onNodeWithContentDescription("Expand").performClick()

        assertFalse(collapsed)
        onNodeWithContentDescription("Collapse").assertIsDisplayed()
        onNodeWithText("2 worktrees").assertIsDisplayed()
    }

    @Test
    fun `hovering a state group title shows its full criterion`() = runComposeUiTest {
        setContent {
            MaterialTheme { StateGroupHeader(StateGroupKind.INACTIVE, count = 1, canCleanUp = false, onCleanUp = {}) }
        }

        onNodeWithText("Inactive", useUnmergedTree = true).performMouseInput { moveTo(center) }
        mainClock.advanceTimeBy(2_000)

        onNodeWithText("No agent running and no activity in the last 3 days, even with uncommitted or unpushed work.").assertIsDisplayed()
    }

    @Test
    fun `the inactive criterion names the configured number of days`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                StateGroupHeader(StateGroupKind.INACTIVE, count = 1, canCleanUp = false, onCleanUp = {}, inactiveDays = 7)
            }
        }

        onNodeWithText("no activity for 7+ days").assertIsDisplayed()
        onNodeWithText("Inactive", useUnmergedTree = true).performMouseInput { moveTo(center) }
        mainClock.advanceTimeBy(2_000)

        onNodeWithText("No agent running and no activity in the last 7 days, even with uncommitted or unpushed work.").assertIsDisplayed()
    }

    @Test
    fun `the new and stopped groups explain themselves in both languages`() = runComposeUiTest {
        var strings by mutableStateOf<app.luxion.shogunai.ui.i18n.Strings>(app.luxion.shogunai.ui.i18n.EnglishStrings)
        setContent {
            CompositionLocalProvider(LocalStrings provides strings) {
                MaterialTheme {
                    Column {
                        StateGroupHeader(StateGroupKind.NEW, count = 1, canCleanUp = false, onCleanUp = {})
                        StateGroupHeader(StateGroupKind.STOPPED, count = 1, canCleanUp = false, onCleanUp = {})
                    }
                }
            }
        }

        onNodeWithText("New").assertIsDisplayed()
        onNodeWithText("no commits yet").assertIsDisplayed()
        onNodeWithText("Stopped").assertIsDisplayed()
        onNodeWithText("pushed, waiting").assertIsDisplayed()

        strings = SpanishStrings
        waitForIdle()

        onNodeWithText("Nuevos").assertIsDisplayed()
        onNodeWithText("aún sin commits").assertIsDisplayed()
        onNodeWithText("Parados").assertIsDisplayed()
        onNodeWithText("subido, en espera").assertIsDisplayed()
    }
}
