package app.luxion.shogunai.ui.tray

import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.usecase.Activity
import app.luxion.shogunai.ui.i18n.Strings
import app.luxion.shogunai.ui.worktree.OpenKind
import app.luxion.shogunai.ui.worktree.WorktreeRef

sealed interface TrayAction {
    data object ShowWindow : TrayAction
    data object NewWorktree : TrayAction
    data class OpenProject(val projectId: String) : TrayAction
    data class ShowWorktree(val ref: WorktreeRef) : TrayAction
    data class CopyLaunchCommand(val ref: WorktreeRef) : TrayAction
    data class Open(val ref: WorktreeRef, val kind: OpenKind) : TrayAction
    data object Preferences : TrayAction
    data object Quit : TrayAction
}

sealed interface TrayEntry {
    data class Item(val label: String, val action: TrayAction) : TrayEntry
    data class Submenu(val label: String, val entries: List<TrayEntry>) : TrayEntry
    data class Heading(val label: String) : TrayEntry
    data object Separator : TrayEntry
}

data class TrayOpenAction(val kind: OpenKind, val label: String)

data class TrayWorktree(
    val ref: WorktreeRef,
    val name: String,
    val projectName: String,
    val activity: Activity,
    val isLoaded: Boolean,
    val openActions: List<TrayOpenAction>,
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
    Activity.NeedsYou -> "! "
    else -> ""
}

private fun worktreeSubmenu(worktree: TrayWorktree, strings: Strings): TrayEntry {
    val entries = buildList {
        add(TrayEntry.Item(strings.trayShowInApp, TrayAction.ShowWorktree(worktree.ref)))
        if (worktree.isLoaded) {
            add(TrayEntry.Item(strings.copyLaunchCommand, TrayAction.CopyLaunchCommand(worktree.ref)))
            worktree.openActions.forEach { add(TrayEntry.Item(it.label, TrayAction.Open(worktree.ref, it.kind))) }
        }
    }
    return TrayEntry.Submenu(trayWorktreeLabel(worktree), entries)
}
