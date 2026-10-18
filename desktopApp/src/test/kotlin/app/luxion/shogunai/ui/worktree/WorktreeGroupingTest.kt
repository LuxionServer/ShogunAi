package app.luxion.shogunai.ui.worktree

import app.luxion.shogunai.domain.usecase.Activity
import kotlin.test.Test
import kotlin.test.assertEquals

class WorktreeGroupingTest {

    private fun items(vararg entries: Pair<String, Activity>) = entries.map { ActivityItem(it.first, it.second) }

    private fun kinds(groups: List<StateGroup<String>>) = groups.map { it.kind }

    @Test
    fun `active worktrees are grouped in order and the rest is left out`() {
        val groups = groupByState(
            items(
                "progress" to Activity.InProgress,
                "merged" to Activity.Merged,
                "needs" to Activity.NeedsYou,
                "agent" to Activity.AgentWorking,
                "idle" to Activity.Idle,
                "unknown" to Activity.Unknown,
            ),
            includeInactive = false,
        )

        assertEquals(
            listOf(StateGroupKind.AGENT_WORKING, StateGroupKind.NEEDS_YOU, StateGroupKind.IN_PROGRESS),
            kinds(groups),
        )
        assertEquals(listOf("agent"), groups.first().items)
    }

    @Test
    fun `all worktrees add merged inactive and folder missing groups after the active ones`() {
        val groups = groupByState(
            items(
                "missing" to Activity.Missing,
                "inactive" to Activity.Inactive,
                "merged-1" to Activity.Merged,
                "merged-2" to Activity.Merged,
                "progress" to Activity.InProgress,
                "main" to Activity.Idle,
            ),
            includeInactive = true,
        )

        assertEquals(
            listOf(
                StateGroupKind.IN_PROGRESS,
                StateGroupKind.MERGED,
                StateGroupKind.INACTIVE,
                StateGroupKind.FOLDER_MISSING,
                StateGroupKind.OTHER,
            ),
            kinds(groups),
        )
        assertEquals(listOf("merged-1", "merged-2"), groups[1].items)
    }

    @Test
    fun `empty groups are hidden`() {
        assertEquals(emptyList(), groupByState(items("merged" to Activity.Merged), includeInactive = false))
    }

    @Test
    fun `a pinned worktree stays in the group for its real state`() {
        val groups = groupByState(
            items("merged" to Activity.Merged, "progress" to Activity.InProgress),
            includeInactive = false,
            isPinned = { it == "merged" },
        )

        assertEquals(listOf(StateGroupKind.IN_PROGRESS, StateGroupKind.MERGED), kinds(groups))
    }
}
