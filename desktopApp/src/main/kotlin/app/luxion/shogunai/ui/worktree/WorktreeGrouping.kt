package app.luxion.shogunai.ui.worktree

import app.luxion.shogunai.domain.usecase.Activity
import app.luxion.shogunai.domain.usecase.isActive

enum class StateGroupKind(val activities: Set<Activity>) {
    NEEDS_YOU(setOf(Activity.NeedsYou)),
    AGENT_WORKING(setOf(Activity.AgentWorking)),
    NEW(setOf(Activity.New)),
    STOPPED(setOf(Activity.Stopped)),
    MERGED(setOf(Activity.Merged)),
    INACTIVE(setOf(Activity.Inactive)),
    FOLDER_MISSING(setOf(Activity.Missing)),
    OTHER(setOf(Activity.Idle, Activity.Unknown)),
}

data class StateGroup<T>(val kind: StateGroupKind, val items: List<T>)

data class ActivityItem<T>(val item: T, val activity: Activity)

fun <T> groupByState(
    items: List<ActivityItem<T>>,
    includeInactive: Boolean,
    isPinned: (T) -> Boolean = { false },
): List<StateGroup<T>> {
    val shown = items.filter { includeInactive || it.activity.isActive || isPinned(it.item) }
    return StateGroupKind.entries.mapNotNull { kind ->
        val inGroup = shown.filter { it.activity in kind.activities }.map { it.item }
        inGroup.takeIf { it.isNotEmpty() }?.let { StateGroup(kind, it) }
    }
}
