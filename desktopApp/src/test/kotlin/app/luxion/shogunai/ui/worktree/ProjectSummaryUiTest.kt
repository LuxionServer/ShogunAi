package app.luxion.shogunai.ui.worktree

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import app.luxion.shogunai.ui.i18n.EnglishStrings
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class ProjectSummaryUiTest {

    @Test
    fun `the summary shows placeholders instead of zero while projects load`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                ProjectSummary(
                    worktreeCount = 0,
                    totalSize = 0,
                    cleanupCandidateCount = 0,
                    isLoading = true,
                )
            }
        }

        onNodeWithText(EnglishStrings.worktreesTitle).assertIsDisplayed()
        onNodeWithContentDescription(EnglishStrings.loadingWorktrees).assertExists()
        onNodeWithText(EnglishStrings.worktreesSummary(0, "0 B"), substring = true).assertDoesNotExist()
    }

    @Test
    fun `the summary only informs and offers no clean up`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                ProjectSummary(worktreeCount = 5, totalSize = 2048, cleanupCandidateCount = 2)
            }
        }

        onNodeWithText(EnglishStrings.cleanupCandidatesSummary(2)).assertIsDisplayed()
        onNodeWithText(EnglishStrings.cleanUp).assertDoesNotExist()
    }
}
