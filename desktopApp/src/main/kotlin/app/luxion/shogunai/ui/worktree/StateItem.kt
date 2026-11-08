package app.luxion.shogunai.ui.worktree

import app.luxion.shogunai.domain.model.Worktree

sealed interface StateItem {
    val entries: List<WorktreeEntry>
    val visibleRefs: List<WorktreeRef>
    val sortWorktree: Worktree

    fun holds(ref: WorktreeRef?): Boolean

    data class Single(val entry: WorktreeEntry) : StateItem {
        override val entries: List<WorktreeEntry> get() = listOf(entry)
        override val visibleRefs: List<WorktreeRef> get() = listOf(entry.ref)
        override val sortWorktree: Worktree get() = entry.worktree

        override fun holds(ref: WorktreeRef?): Boolean = entry.ref == ref
    }

    data class Change(val group: ChangeGroup, val expanded: Boolean) : StateItem {
        override val entries: List<WorktreeEntry> get() = group.entries.map { it.entry }
        override val visibleRefs: List<WorktreeRef> get() = if (expanded) group.entries.map { it.ref } else emptyList()
        override val sortWorktree: Worktree get() = group.members.first().entry.worktree

        override fun holds(ref: WorktreeRef?): Boolean = group.members.any { it.ref == ref }
    }
}
