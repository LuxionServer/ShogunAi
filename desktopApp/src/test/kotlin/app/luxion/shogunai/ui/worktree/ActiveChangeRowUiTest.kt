package app.luxion.shogunai.ui.worktree

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import app.luxion.shogunai.domain.model.AgentState
import app.luxion.shogunai.domain.model.RunningAgent
import app.luxion.shogunai.domain.usecase.Activity
import app.luxion.shogunai.ui.actions.ActionId
import app.luxion.shogunai.ui.actions.GroupActionContext
import app.luxion.shogunai.ui.actions.OpenTarget
import app.luxion.shogunai.ui.actions.groupCatalog
import app.luxion.shogunai.ui.components.MenuEntry
import app.luxion.shogunai.ui.i18n.EnglishStrings
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ActiveChangeRowUiTest {

    private val group = ChangeGroup(
        branch = "feature/TASK-1",
        entries = emptyList(),
        activity = Activity.Unfinished,
        members = emptyList(),
        multirepoFolder = "/home/dev/projects/product",
    )

    private val catalog = groupCatalog(GroupActionContext(orcaInstalled = false, isOpeningInOrca = false, addableAliases = listOf("docs")), EnglishStrings)

    @Test
    fun `the menu of a change row offers the group actions and shows it in its multirepo`() {
        val actions = mutableListOf<ActionId>()
        var shown = 0

        val menu = changeRowMenu(catalog, EnglishStrings, onAction = { actions += it }, onShowInMultirepo = { shown++ })

        val items = menu.filterIsInstance<MenuEntry.Item>()
        assertEquals(listOf("Open all in Herdr", "Clean up group", "Show in multirepo"), items.map { it.label })
        assertEquals(listOf("Add repository"), menu.filterIsInstance<MenuEntry.Submenu>().map { it.label })
        items.forEach { it.onClick() }
        assertEquals(listOf(ActionId.OpenAll(OpenTarget.HERDR), ActionId.CleanUpGroup), actions)
        assertEquals(1, shown)
        assertEquals(GroupTag("/home/dev/projects/product", "feature/TASK-1", emptyList()), group.tag)
    }

    @Test
    fun `a change row shows its state, agents, title and branch and toggles when activated`() = runComposeUiTest {
        var expanded by mutableStateOf(false)
        var shown = 0
        val agent = agentIndicator(listOf(RunningAgent("claude", AgentState.BLOCKED)), null, Instant.parse("2026-10-16T12:00:00Z"), EnglishStrings)
        setContent {
            MaterialTheme {
                ActiveChangeRow(
                    group = group,
                    expanded = expanded,
                    agent = agent,
                    menu = { changeRowMenu(catalog, EnglishStrings, onAction = {}, onShowInMultirepo = { shown++ }) },
                    isBusy = true,
                    compact = false,
                    showExecution = true,
                    onToggle = { expanded = !expanded },
                )
            }
        }

        onNodeWithText("TASK-1").assertIsDisplayed()
        onNodeWithTag(ActiveChangeDetailTag, useUnmergedTree = true).assertIsDisplayed()
        onNode(hasText("feature/TASK-1", substring = true), useUnmergedTree = true).assertIsDisplayed()
        onNodeWithContentDescription("Unfinished", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithContentDescription("Claude · needs you", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithContentDescription(EnglishStrings.expand, useUnmergedTree = true).assertIsDisplayed()

        onNodeWithTag(ActiveChangeTag).performClick()
        waitForIdle()
        assertEquals(true, expanded)
        onNodeWithContentDescription(EnglishStrings.collapse, useUnmergedTree = true).assertIsDisplayed()

        onNodeWithContentDescription(EnglishStrings.moreActions).performClick()
        onNodeWithText("Show in multirepo").performClick()
        waitForIdle()
        assertEquals(1, shown)
    }

    @Test
    fun `a change row without the execution column still shows its title`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                ActiveChangeRow(
                    group = group,
                    expanded = true,
                    agent = null,
                    menu = { emptyList() },
                    isBusy = false,
                    compact = true,
                    showExecution = false,
                    onToggle = {},
                )
            }
        }

        onNodeWithText("TASK-1").assertIsDisplayed()
        assertEquals("feature/TASK-1 · ", changeDetail(group))
    }
}
