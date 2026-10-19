package app.luxion.shogunai.ui.palette

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasSetTextAction
import app.luxion.shogunai.ui.shell.ShellDestination
import app.luxion.shogunai.ui.worktree.WorktreeRef
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class CommandPaletteUiTest {

    private val ref = WorktreeRef("api", "~/projects/api-TASK-9")
    private val project = PaletteItem(
        id = "project:api",
        kind = PaletteKind.PROJECT,
        title = "api",
        keys = listOf("api"),
        primary = PaletteAction.Open(ShellDestination.All(projectFilter = "api")),
    )
    private val worktree = PaletteItem(
        id = "worktree:api:task9",
        kind = PaletteKind.WORKTREE,
        title = "feature/TASK-9",
        keys = listOf("feature/TASK-9"),
        context = "api",
        primary = PaletteAction.SelectWorktree(ref),
        secondary = PaletteAction.CopyLaunchCommand(ref),
    )

    @Test
    fun `typing narrows the results and enter runs the highlighted one`() = runComposeUiTest {
        val ran = mutableListOf<PaletteAction>()
        setContent {
            MaterialTheme { CommandPalette(listOf(project, worktree), emptyList(), { _, action -> ran += action }, {}) }
        }

        onNodeWithText("Search views, projects, worktrees and actions").assertIsDisplayed()
        onNodeWithText("Search views, projects, worktrees and actions").performTextInput("ft9")
        onNodeWithText("No results").assertDoesNotExist()
        onNode(hasSetTextAction()).performKeyInput { pressKey(Key.Enter) }

        assertEquals(listOf<PaletteAction>(PaletteAction.SelectWorktree(ref)), ran)
    }

    @Test
    fun `an unmatched query says there are no results and enter runs nothing`() = runComposeUiTest {
        val ran = mutableListOf<PaletteAction>()
        setContent {
            MaterialTheme { CommandPalette(listOf(project, worktree), emptyList(), { _, action -> ran += action }, {}) }
        }

        onNodeWithText("Search views, projects, worktrees and actions").performTextInput("zzz")
        onNodeWithText("No results").assertIsDisplayed()
        onNode(hasSetTextAction()).performKeyInput { pressKey(Key.Enter) }

        assertEquals(emptyList(), ran)
    }

    @Test
    fun `editing the query after scrolling shows the first results`() = runComposeUiTest {
        val many = (1..30).map {
            PaletteItem(
                id = "view:$it",
                kind = PaletteKind.VIEW,
                title = "item-%02d".format(it),
                keys = listOf("item-%02d".format(it)),
                primary = PaletteAction.Open(ShellDestination.Active),
            )
        }
        setContent { MaterialTheme { CommandPalette(many, many.map { it.id }, { _, _ -> }, {}) } }

        onNode(hasSetTextAction()).performKeyInput { repeat(20) { pressKey(Key.DirectionDown) } }
        onNode(hasSetTextAction()).performTextInput("item")
        waitForIdle()

        onNodeWithText("item-01").assertIsDisplayed()
    }

    @Test
    fun `the search field is focused as soon as the palette is composed`() = runComposeUiTest {
        setContent {
            MaterialTheme { CommandPalette(listOf(project, worktree), emptyList(), { _, _ -> }, {}) }
        }

        onNode(hasSetTextAction()).assertIsFocused()
    }

    @Test
    fun `a highlighted worktree shows that the secondary shortcut copies its launch command`() = runComposeUiTest {
        setContent {
            MaterialTheme { CommandPalette(listOf(worktree, project), listOf(worktree.id), { _, _ -> }, {}) }
        }

        onNodeWithText("Copy launch command", substring = true).assertIsDisplayed()
    }

    @Test
    fun `the hint is hidden while a project is highlighted`() = runComposeUiTest {
        setContent {
            MaterialTheme { CommandPalette(listOf(project, worktree), listOf(project.id), { _, _ -> }, {}) }
        }

        onNodeWithText("Copy launch command", substring = true).assertDoesNotExist()
    }
}
