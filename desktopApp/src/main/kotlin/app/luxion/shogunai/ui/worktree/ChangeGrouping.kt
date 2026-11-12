package app.luxion.shogunai.ui.worktree

import app.luxion.shogunai.domain.usecase.Activity

data class AliasedEntry(val alias: String, val entry: WorktreeEntry) {
    val ref: WorktreeRef get() = entry.ref
}

data class ChangeGroup(
    val branch: String,
    val entries: List<AliasedEntry>,
    val activity: Activity,
    val collapsed: Boolean = false,
    val members: List<AliasedEntry> = entries,
    val multirepoFolder: String = "",
) {
    val title: String get() = changeTitle(branch)
    val state: StateBadgeKind get() = activity.badgeKind()
    val aliases: List<String> get() = members.map { it.alias }.distinct()
    val key: ChangeKey get() = ChangeKey(multirepoFolder, branch)
    val tag: GroupTag get() = GroupTag(multirepoFolder, branch, emptyList())
}

data class ChangeKey(val multirepoFolder: String, val branch: String)

data class ChangeView(val groups: List<ChangeGroup>, val ungrouped: List<AliasedEntry>) {
    val isEmpty: Boolean get() = groups.isEmpty() && ungrouped.isEmpty()
}

data class GroupTag(val multirepoFolder: String, val branch: String, val others: List<String>) {
    val title: String get() = changeTitle(branch)
    val label: String get() = if (others.isEmpty()) title else "$title · " + others.joinToString(" ") { "+$it" }
}

fun changeTitle(branch: String): String = branch.substringAfterLast('/')

fun combinedActivity(activities: List<Activity>): Activity {
    if (activities.isEmpty()) return Activity.Unknown
    if (activities.all { it == Activity.Merged }) return Activity.Merged
    return activities.filter { it != Activity.Merged }.min()
}

fun groupedBranches(entries: List<AliasedEntry>): Map<String, List<AliasedEntry>> =
    entries
        .mapNotNull { member -> member.entry.worktree.branch?.let { it to member } }
        .groupBy({ it.first }, { it.second })
        .filterValues { members -> members.map { it.alias }.distinct().size >= 2 }
