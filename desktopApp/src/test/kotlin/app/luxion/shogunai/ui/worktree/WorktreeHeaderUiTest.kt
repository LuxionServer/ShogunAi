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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Dp
import app.luxion.shogunai.domain.model.Grouping
import app.luxion.shogunai.domain.model.WorktreeSort
import app.luxion.shogunai.ui.actions.ActionId
import app.luxion.shogunai.ui.actions.ViewActionContext
import app.luxion.shogunai.ui.actions.ViewCatalog
import app.luxion.shogunai.ui.actions.toMenu
import app.luxion.shogunai.ui.actions.viewCatalog
import app.luxion.shogunai.ui.actions.viewOverflow
import app.luxion.shogunai.ui.i18n.EnglishStrings
import kotlin.test.assertTrue

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

    private fun catalog(cleanUpCount: Int = 0, offersCleanUp: Boolean = true, cleanUpBlocked: String? = null, isMultirepo: Boolean = false) = viewCatalog(
        ViewActionContext(
            isMultirepo = isMultirepo,
            isProjectView = false,
            createBlocked = null,
            cleanUpBlocked = cleanUpBlocked,
            offersCleanUp = offersCleanUp,
            cleanUpCount = cleanUpCount,
        ),
        EnglishStrings,
    )

    private val noOptions = ViewOptionsHandlers({}, {}, {}, {})

    @Composable
    private fun Header(
        catalog: ViewCatalog,
        width: Dp = 1000.dp,
        filterText: String = "",
        onFilter: (String) -> Unit = {},
        findRequests: Int = 0,
        handlers: ViewOptionsHandlers = noOptions,
        performed: MutableList<ActionId> = mutableListOf(),
    ) {
        MaterialTheme {
            Column(Modifier.width(width)) {
                ViewHeaderRow(
                    title = "All worktrees",
                    isBusy = false,
                    filter = HeaderFilterState(filterText, onFilter, {}, findRequests),
                    viewMenu = viewOptionsMenu(ViewOptions(projects, null, listOf(Grouping.BY_STATE, Grouping.BY_PROJECT), Grouping.BY_PROJECT, WorktreeSort.NAME), handlers, EnglishStrings),
                    cleanUp = catalog.cleanUp,
                    overflow = viewOverflow(catalog, EnglishStrings).toMenu(EnglishStrings) { spec, _ -> performed += spec.id },
                    primary = catalog.primary,
                    onPerform = { performed += it.id },
                )
                Text("elsewhere", modifier = Modifier.clickable {}.focusable())
            }
        }
    }

    @Test
    fun `a wide header shows every item in one row without a more menu`() = runComposeUiTest {
        val performed = mutableListOf<ActionId>()
        setContent { Header(catalog(cleanUpCount = 3), performed = performed) }

        onNodeWithText("All worktrees").assertIsDisplayed()
        onNodeWithText(EnglishStrings.filterWorktreesPlaceholder).assertIsDisplayed()
        onNodeWithContentDescription(EnglishStrings.moreActions).assertDoesNotExist()
        val top = onNodeWithText("All worktrees").getUnclippedBoundsInRoot().top
        listOf("View", "Clean up 3", "New worktree").forEach { label ->
            onNodeWithText(label).assertIsDisplayed()
            assertTrue(onNodeWithText(label).getUnclippedBoundsInRoot().top < top + 40.dp)
        }

        onNodeWithText("Clean up 3").performClick()
        onNodeWithText("New worktree").performClick()
        assertEquals(listOf<ActionId>(ActionId.CleanUp, ActionId.NewWorktree), performed)
    }

    @Test
    fun `the header has no clean up when nothing can be cleaned up`() = runComposeUiTest {
        setContent { Header(catalog(offersCleanUp = false, cleanUpBlocked = "Nothing to clean up")) }

        onNode(hasText("Clean up", substring = true)).assertDoesNotExist()
    }

    @Test
    fun `a running cleanup leaves clean up disabled with its reason`() = runComposeUiTest {
        setContent { Header(catalog(cleanUpCount = 2, cleanUpBlocked = "A cleanup is already running")) }

        onNodeWithText("Clean up 2").assertIsNotEnabled()
        onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "A cleanup is already running")).assertExists()
    }

    @Test
    fun `a multirepo header keeps its more menu`() = runComposeUiTest {
        setContent { Header(catalog(isMultirepo = true)) }

        onNodeWithContentDescription(EnglishStrings.moreActions).performClick()
        onNodeWithText(EnglishStrings.openMultirepoAgent).assertIsDisplayed()
    }

    @Test
    fun `the view menu reports the picked order and reloads`() = runComposeUiTest {
        val calls = mutableListOf<String>()
        val handlers = ViewOptionsHandlers({ calls += "project:$it" }, { calls += "grouping:$it" }, { calls += "sort:$it" }, { calls += "reload" })
        setContent { Header(catalog(), handlers = handlers) }

        onNodeWithText("View").performClick()
        onNodeWithText("Last activity").performClick()
        onNodeWithText("View").performClick()
        onNodeWithText("api").performClick()
        onNodeWithText("View").performClick()
        onNodeWithText("Reload").performClick()

        assertEquals(listOf("sort:ACTIVITY", "project:api", "reload"), calls)
    }

    @Test
    fun `a compact header shows the filter as an icon that opens the field`() = runComposeUiTest {
        var text by mutableStateOf("")
        setContent { Header(catalog(), width = 600.dp, filterText = text, onFilter = { text = it }) }

        onNodeWithText(EnglishStrings.filterWorktreesPlaceholder).assertDoesNotExist()
        onNodeWithContentDescription(EnglishStrings.filterWorktrees).performClick()

        onNode(hasSetTextAction()).assertIsFocused()
        onNodeWithText("elsewhere").performClick()
        onNodeWithContentDescription(EnglishStrings.filterWorktrees).assertIsDisplayed()
    }

    @Test
    fun `a compact filter with text stays open`() = runComposeUiTest {
        var text by mutableStateOf("")
        setContent { Header(catalog(), width = 600.dp, filterText = text, onFilter = { text = it }) }

        onNodeWithContentDescription(EnglishStrings.filterWorktrees).performClick()
        onNode(hasSetTextAction()).performTextInput("task-12")
        onNodeWithText("elsewhere").performClick()

        onNodeWithText("task-12").assertIsDisplayed()
    }

    @Test
    fun `find opens the compact filter`() = runComposeUiTest {
        var requests by mutableStateOf(0)
        setContent { Header(catalog(), width = 600.dp, findRequests = requests) }

        requests++
        waitForIdle()

        onNode(hasSetTextAction()).assertIsFocused()
    }

    @Test
    fun `a compact header shows new worktree as a plus button`() = runComposeUiTest {
        val performed = mutableListOf<ActionId>()
        setContent { Header(catalog(), width = 600.dp, performed = performed) }

        onNodeWithText("New worktree").assertDoesNotExist()
        onNodeWithContentDescription("New worktree").performClick()

        assertEquals(listOf<ActionId>(ActionId.NewWorktree), performed)
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
