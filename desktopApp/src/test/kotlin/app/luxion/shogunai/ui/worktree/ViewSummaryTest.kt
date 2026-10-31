package app.luxion.shogunai.ui.worktree

import androidx.compose.ui.unit.dp
import app.luxion.shogunai.ui.i18n.EnglishStrings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ViewSummaryTest {

    @Test
    fun `the summary names the worktrees, their size and what can be cleaned up`() {
        assertEquals(
            "5 worktrees · 2.0 KB · 2 to clean up",
            viewSummaryLine(worktreeCount = 5, totalSize = 2048, cleanupCandidateCount = 2, isLoading = false, strings = EnglishStrings),
        )
    }

    @Test
    fun `without cleanup candidates the summary names only the worktrees and their size`() {
        assertEquals(
            "1 worktree · 2.0 KB",
            viewSummaryLine(worktreeCount = 1, totalSize = 2048, cleanupCandidateCount = 0, isLoading = false, strings = EnglishStrings),
        )
    }

    @Test
    fun `no summary while the projects load`() {
        assertNull(viewSummaryLine(worktreeCount = 0, totalSize = 0, cleanupCandidateCount = 0, isLoading = true, strings = EnglishStrings))
    }

    @Test
    fun `the inspector docks from 980 dp of content and opens over the list below`() {
        assertTrue(isInspectorDocked(980.dp))
        assertTrue(isInspectorDocked(1000.dp))
        assertFalse(isInspectorDocked(979.dp))
        assertFalse(isInspectorDocked(900.dp))
    }
}
