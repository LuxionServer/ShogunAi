package app.luxion.shogunai.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

class SelectorTest {

    private val byPrefix: (String) -> String? = { it.substringBefore('/', "otras") }

    @Test
    fun `merges non-contiguous options of the same group into a single group`() {
        val branches = listOf("bugfix/TASK-1", "develop", "feature/TASK-123", "main")

        assertEquals(
            listOf(
                "bugfix" to listOf("bugfix/TASK-1"),
                "otras" to listOf("develop", "main"),
                "feature" to listOf("feature/TASK-123"),
            ),
            groupOptions(branches, byPrefix),
        )
    }

    @Test
    fun `merges a group split by a sibling that sorts in between`() {
        // '-' sorts before '/', so "feature-old" lands between "feature" and "feature/..." branches.
        val branches = listOf("feature", "feature-old", "feature/TASK-123")

        assertEquals(
            listOf(
                "otras" to listOf("feature", "feature-old"),
                "feature" to listOf("feature/TASK-123"),
            ),
            groupOptions(branches, byPrefix),
        )
    }

    @Test
    fun `keeps ungrouped options together under a null group`() {
        assertEquals(
            listOf(null to listOf("a", "b")),
            groupOptions(listOf("a", "b")) { null },
        )
    }
}
