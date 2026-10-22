package app.luxion.shogunai.ui.worktree

data class AliasedEntry(val alias: String, val entry: WorktreeEntry) {
    val ref: WorktreeRef get() = entry.ref
}

data class ChangeGroup(
    val branch: String,
    val entries: List<AliasedEntry>,
    val state: StateBadgeKind,
    val collapsed: Boolean = false,
    val members: List<AliasedEntry> = entries,
) {
    val title: String get() = changeTitle(branch)
    val aliases: List<String> get() = members.map { it.alias }.distinct()
}

data class ChangeView(val groups: List<ChangeGroup>, val ungrouped: List<AliasedEntry>) {
    val isEmpty: Boolean get() = groups.isEmpty() && ungrouped.isEmpty()
}

data class GroupTag(val multirepoFolder: String, val branch: String, val others: List<String>) {
    val title: String get() = changeTitle(branch)
    val label: String get() = if (others.isEmpty()) title else "$title · " + others.joinToString(" ") { "+$it" }
}

fun changeTitle(branch: String): String = branch.substringAfterLast('/')

fun combinedState(kinds: List<StateBadgeKind>): StateBadgeKind {
    if (kinds.isEmpty()) return StateBadgeKind.OTHER
    if (kinds.all { it == StateBadgeKind.MERGED }) return StateBadgeKind.MERGED
    return kinds.filter { it != StateBadgeKind.MERGED }.minBy { it.ordinal }
}

fun groupedBranches(entries: List<AliasedEntry>): Map<String, List<AliasedEntry>> =
    entries
        .filter { it.entry.worktree.branch != null && !it.entry.worktree.isMain }
        .groupBy { it.entry.worktree.branch!! }
        .filterValues { members -> members.map { it.alias }.distinct().size >= 2 }
