package app.luxion.shogunai.ui.palette

import app.luxion.shogunai.ui.shell.ShellDestination
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PaletteMatcherTest {

    private fun item(id: String, title: String, vararg contextKeys: String, kind: PaletteKind = PaletteKind.WORKTREE) = PaletteItem(
        id = id,
        kind = kind,
        title = title,
        keys = listOf(title),
        contextKeys = contextKeys.toList(),
        primary = PaletteAction.Open(ShellDestination.Active),
    )

    private val api = item("project:api", "api", kind = PaletteKind.PROJECT)
    private val rapid = item("worktree:rapid", "fix/rapid-ui")
    private val task9 = item("worktree:task9", "feature/TASK-9", "api")

    @Test
    fun `prefix beats subsequence`() {
        val ranked = rankPaletteItems(listOf(rapid, api), "api", emptyList())

        assertEquals(listOf(api, rapid), ranked)
    }

    @Test
    fun `gaps are allowed and case is ignored`() {
        assertNotNull(matchScore("ft9", "feature/TASK-9"))
        assertNotNull(matchScore("FT9", "feature/task-9"))
    }

    @Test
    fun `a missing letter does not match`() {
        assertNull(matchScore("fz9", "feature/TASK-9"))
    }

    @Test
    fun `exact beats prefix beats word starts beats the rest`() {
        val exact = checkNotNull(matchScore("ui", "ui"))
        val prefix = checkNotNull(matchScore("ui", "uikit"))
        val wordStart = checkNotNull(matchScore("ui", "fix/user-input"))
        val other = checkNotNull(matchScore("ui", "build"))

        assertTrue(exact > prefix)
        assertTrue(prefix > wordStart)
        assertTrue(wordStart > other)
    }

    @Test
    fun `a case change counts as a word start`() {
        val camelCase = checkNotNull(matchScore("ab", "xAyBz"))
        val inWord = checkNotNull(matchScore("ab", "xaybz"))

        assertTrue(camelCase > inWord)
    }

    @Test
    fun `contiguous matches beat scattered ones`() {
        val contiguous = checkNotNull(matchScore("tas", "my-task"))
        val scattered = checkNotNull(matchScore("tas", "my-txaxs"))

        assertTrue(contiguous > scattered)
    }

    @Test
    fun `a match on the project ranks below a match on the item itself`() {
        val ranked = rankPaletteItems(listOf(task9, rapid, api), "api", emptyList())

        assertEquals(listOf(api, rapid, task9), ranked)
    }

    @Test
    fun `the most recent item wins a tie, then alphabetical order`() {
        val first = item("a", "alpha-1")
        val second = item("b", "alpha-2")
        val third = item("c", "alpha-3")

        assertEquals(listOf(first, second, third), rankPaletteItems(listOf(third, second, first), "alpha", emptyList()))
        assertEquals(listOf(third, first, second), rankPaletteItems(listOf(first, second, third), "alpha", listOf("c")))
    }

    @Test
    fun `an empty query lists recent items most recent first`() {
        val ranked = rankPaletteItems(listOf(api, rapid, task9), "", listOf("worktree:task9", "stale", "project:api"))

        assertEquals(listOf(task9, api), ranked)
    }

    @Test
    fun `an empty query without recent items lists the views`() {
        val view = item("view:active", "Active", kind = PaletteKind.VIEW)

        assertEquals(listOf(view), rankPaletteItems(listOf(api, view, rapid), "", emptyList()))
    }

    @Test
    fun `recording an item keeps ids unique, most recent first and at most twenty`() {
        val recent = (1..20).map { "item-$it" }

        val updated = recordRecent(recent, "item-5")

        assertEquals("item-5", updated.first())
        assertEquals(20, updated.size)
        assertEquals(1, updated.count { it == "item-5" })
        assertEquals(20, recordRecent(recent, "new").size)
        assertTrue("item-20" !in recordRecent(recent, "new"))
    }

    @Test
    fun `matched letters are highlighted in order`() {
        assertEquals(setOf(0, 1, 2), matchedIndices("api", "api"))
        assertEquals(setOf(0, 3, 13), matchedIndices("ft9", "feature/TASK-9"))
    }
}
