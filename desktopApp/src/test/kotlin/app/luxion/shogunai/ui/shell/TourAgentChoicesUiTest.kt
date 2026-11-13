package app.luxion.shogunai.ui.shell

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import app.luxion.shogunai.ui.actions.ActionId
import app.luxion.shogunai.ui.actions.OpenTarget
import app.luxion.shogunai.ui.i18n.EnglishStrings
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.worktree.FakeWorktreeRepository
import app.luxion.shogunai.ui.worktree.WorktreeBoard
import app.luxion.shogunai.ui.worktree.uiBoard
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class TourAgentChoicesUiTest {

    private val repository = FakeWorktreeRepository(worktrees = emptyList())
    private var launched = 0
    private var copied = 0

    private fun ComposeUiTest.show(board: WorktreeBoard) = setContent {
        CompositionLocalProvider(LocalStrings provides EnglishStrings) {
            MaterialTheme { TourAgentChoices(board, repository.project, onLaunched = { launched++ }, onCopied = { copied++ }) }
        }
    }

    @Test
    fun `the main worktree's agents are offered first to last and each launch closes the choices`() = runComposeUiTest {
        val board = uiBoard(listOf(repository))
        show(board)

        waitUntil { onAllNodesWithText(EnglishStrings.copyLaunchCommand).fetchSemanticsNodes().isNotEmpty() }
        val launches = board.holder(repository.id)!!.let { holder ->
            val main = holder.worktrees.single { it.isMain }
            val catalog = holder.actionCatalog(main, EnglishStrings)
            listOf(OpenTarget.TERMINAL, OpenTarget.HERDR, OpenTarget.ORCA, OpenTarget.CLAUDE_DESKTOP)
                .mapNotNull { target -> catalog.find(ActionId.Open(target))?.takeIf { it.isAvailable }?.label }
        }
        assertTrue(launches.size > 1)
        launches.forEach { onNodeWithText(it).performClick() }
        onNodeWithText(EnglishStrings.copyLaunchCommand).performClick()
        waitForIdle()

        assertEquals(launches.size + 1, launched)
        assertEquals(1, copied)
    }

    @Test
    fun `while the project's worktrees load it says so`() = runComposeUiTest {
        val board = uiBoard(emptyList())
        show(board)

        onNodeWithText(EnglishStrings.tourLoadingWorktrees).assertIsDisplayed()
    }
}
