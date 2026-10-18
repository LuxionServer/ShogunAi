package app.luxion.shogunai.ui.palette

import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.ui.i18n.Strings
import app.luxion.shogunai.ui.isMacOs
import app.luxion.shogunai.ui.machine.MachineResource
import app.luxion.shogunai.ui.machine.title
import app.luxion.shogunai.ui.shell.ShellDestination
import app.luxion.shogunai.ui.worktree.PaletteWorktree
import app.luxion.shogunai.ui.worktree.WorktreeRef

fun buildPaletteIndex(
    projects: List<Project>,
    worktrees: List<PaletteWorktree>,
    machineResources: List<MachineResource>,
    canCleanUp: Boolean,
    strings: Strings,
    shortcutLabel: (key: String, shift: Boolean) -> String,
): List<PaletteItem> =
    viewItems(projects, machineResources, strings, shortcutLabel) +
        projects.flatMap { projectItems(it, strings) } +
        worktrees.map { worktreeItem(it, strings) } +
        actionItems(projects, canCleanUp, strings, shortcutLabel)

fun pruneRecent(recentIds: List<String>, items: List<PaletteItem>): List<String> {
    val known = items.mapTo(mutableSetOf()) { it.id }
    return recentIds.filter { it in known }.distinct().take(MaxRecentItems)
}

private fun viewItems(
    projects: List<Project>,
    machineResources: List<MachineResource>,
    strings: Strings,
    shortcutLabel: (String, Boolean) -> String,
): List<PaletteItem> {
    val preferences = viewItem(
        id = "view:preferences",
        title = strings.paletteViewPreferences,
        shortcut = shortcutLabel(",", false),
        action = PaletteAction.Run(PaletteCommand.OPEN_PREFERENCES),
        strings = strings,
    )
    if (projects.isEmpty()) return listOf(preferences)
    return listOf(
        viewItem("view:active", strings.paletteViewActive, shortcutLabel("1", false), PaletteAction.Open(ShellDestination.Active), strings),
        viewItem("view:all", strings.paletteViewAllWorktrees, shortcutLabel("2", false), PaletteAction.Open(ShellDestination.All()), strings),
    ) + machineResources.map { resource ->
        viewItem(
            id = "view:machine:${resource.name}",
            title = resource.title(strings),
            shortcut = null,
            action = PaletteAction.Open(ShellDestination.Machine(resource)),
            strings = strings,
            machineResource = resource,
        )
    } + preferences
}

private fun viewItem(
    id: String,
    title: String,
    shortcut: String?,
    action: PaletteAction,
    strings: Strings,
    machineResource: MachineResource? = null,
) = PaletteItem(
    id = id,
    kind = PaletteKind.VIEW,
    title = title,
    keys = listOf(title),
    context = strings.paletteKindView,
    shortcut = shortcut,
    machineResource = machineResource,
    primary = action,
)

private fun projectItems(project: Project, strings: Strings): List<PaletteItem> = listOf(
    PaletteItem(
        id = "project:${project.id}",
        kind = PaletteKind.PROJECT,
        title = project.name,
        keys = listOf(project.name, project.id),
        context = strings.paletteKindProject,
        primary = PaletteAction.Open(ShellDestination.All(projectFilter = project.id)),
    ),
    PaletteItem(
        id = "action:new-worktree:${project.id}",
        kind = PaletteKind.ACTION,
        title = strings.paletteNewWorktreeIn(project.name),
        keys = listOf(strings.paletteNewWorktreeIn(project.name)),
        context = strings.paletteKindAction,
        primary = PaletteAction.NewWorktreeIn(project.id),
    ),
    PaletteItem(
        id = "action:settings:${project.id}",
        kind = PaletteKind.ACTION,
        title = strings.paletteSettingsOf(project.name),
        keys = listOf(strings.paletteSettingsOf(project.name)),
        context = strings.paletteKindAction,
        primary = PaletteAction.Open(ShellDestination.ProjectSettings(project.id)),
    ),
)

private fun worktreeItem(entry: PaletteWorktree, strings: Strings): PaletteItem {
    val ref = WorktreeRef(entry.project.id, entry.worktree.path)
    val folder = entry.worktree.path.substringAfterLast('/')
    return PaletteItem(
        id = "worktree:${entry.project.id}:${entry.worktree.path}",
        kind = PaletteKind.WORKTREE,
        title = entry.worktree.branch ?: strings.detachedHead,
        keys = listOfNotNull(entry.worktree.branch, folder),
        contextKeys = listOf(entry.project.name),
        context = entry.project.name,
        indicator = entry.indicator,
        primary = PaletteAction.SelectWorktree(ref),
        secondary = PaletteAction.CopyLaunchCommand(ref),
    )
}

private fun actionItems(
    projects: List<Project>,
    canCleanUp: Boolean,
    strings: Strings,
    shortcutLabel: (String, Boolean) -> String,
): List<PaletteItem> = listOfNotNull(
    actionItem("action:add-project", strings.addProject, shortcutLabel("N", true), PaletteCommand.ADD_PROJECT, strings),
    actionItem("action:clean-up", strings.paletteCleanUp, null, PaletteCommand.CLEAN_UP, strings).takeIf { canCleanUp },
    actionItem("action:reload", strings.reload, shortcutLabel("R", false), PaletteCommand.RELOAD, strings).takeIf { projects.isNotEmpty() },
    actionItem("action:toggle-sidebar", strings.menuToggleSidebar, shortcutLabel("\\", false), PaletteCommand.TOGGLE_SIDEBAR, strings),
)

private fun actionItem(id: String, title: String, shortcut: String?, command: PaletteCommand, strings: Strings) = PaletteItem(
    id = id,
    kind = PaletteKind.ACTION,
    title = title,
    keys = listOf(title),
    context = strings.paletteKindAction,
    shortcut = shortcut,
    primary = PaletteAction.Run(command),
)

fun platformShortcutLabel(key: String, shift: Boolean): String = when {
    isMacOs -> (if (shift) "⇧" else "") + "⌘" + key
    else -> "Ctrl+" + (if (shift) "Shift+" else "") + key
}
