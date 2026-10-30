package app.luxion.shogunai.ui.tray

import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.usecase.Activity
import app.luxion.shogunai.ui.i18n.Strings
import app.luxion.shogunai.ui.actions.ActionId
import app.luxion.shogunai.ui.actions.TrayActionEntries
import app.luxion.shogunai.ui.worktree.WorktreeRef

sealed interface TrayAction {
    data object ShowWindow : TrayAction
    data object NewWorktree : TrayAction
    data class OpenProject(val projectId: String) : TrayAction
    data class ShowWorktree(val ref: WorktreeRef) : TrayAction
    data class Run(val ref: WorktreeRef, val id: ActionId) : TrayAction
    data object Preferences : TrayAction
    data object Quit : TrayAction
}

sealed interface TrayEntry {
    data class Item(val label: String, val action: TrayAction) : TrayEntry
    data class Submenu(val label: String, val entries: List<TrayEntry>) : TrayEntry
    data class Heading(val label: String) : TrayEntry
    data object Separator : TrayEntry
}

data class TrayWorktree(
    val ref: WorktreeRef,
    val name: String,
    val projectName: String,
    val activity: Activity,
    val isLoaded: Boolean,
    val actions: TrayActionEntries?,
)

fun buildTrayMenu(
    strings: Strings,
    recentProjects: List<Project>,
    recentWorktrees: List<TrayWorktree>,
): List<TrayEntry> = buildList {
    add(TrayEntry.Item(strings.trayShowApp, TrayAction.ShowWindow))
    add(TrayEntry.Item(strings.trayNewWorktree, TrayAction.NewWorktree))
    if (recentProjects.isNotEmpty()) {
        add(TrayEntry.Separator)
        add(TrayEntry.Heading(strings.trayRecentProjects))
        recentProjects.forEach { add(TrayEntry.Item(it.name, TrayAction.OpenProject(it.id))) }
    }
    if (recentWorktrees.isNotEmpty()) {
        add(TrayEntry.Separator)
        add(TrayEntry.Heading(strings.trayRecentWorktrees))
        recentWorktrees.forEach { add(worktreeSubmenu(it, strings)) }
    }
    add(TrayEntry.Separator)
    add(TrayEntry.Item(strings.menuPreferences, TrayAction.Preferences))
    add(TrayEntry.Item(strings.menuQuit, TrayAction.Quit))
}

fun trayWorktreeLabel(worktree: TrayWorktree): String =
    "${activityMark(worktree.activity)}${worktree.name} — ${worktree.projectName}"

private fun activityMark(activity: Activity): String = when (activity) {
    Activity.AgentWorking -> "● "
    Activity.WaitingForYou -> "! "
    else -> ""
}

private fun worktreeSubmenu(worktree: TrayWorktree, strings: Strings): TrayEntry {
    val entries = buildList {
        add(TrayEntry.Item(strings.trayShowInApp, TrayAction.ShowWorktree(worktree.ref)))
        val actions = worktree.actions?.takeIf { worktree.isLoaded } ?: return@buildList
        actions.primary?.let { add(TrayEntry.Item(it.label, TrayAction.Run(worktree.ref, it.id))) }
        if (actions.openWith.isNotEmpty()) {
            add(TrayEntry.Submenu(strings.openWithMenu, actions.openWith.map { TrayEntry.Item(it.label, TrayAction.Run(worktree.ref, it.id)) }))
        }
        actions.copyLaunchCommand?.let { add(TrayEntry.Item(it.label, TrayAction.Run(worktree.ref, it.id))) }
    }
    return TrayEntry.Submenu(trayWorktreeLabel(worktree), entries)
}
