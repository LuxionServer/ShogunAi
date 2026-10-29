package app.luxion.shogunai.ui.palette

import app.luxion.shogunai.ui.actions.ActionId
import app.luxion.shogunai.ui.machine.MachineResource
import app.luxion.shogunai.ui.shell.ShellDestination
import app.luxion.shogunai.ui.worktree.StateBadgeKind
import app.luxion.shogunai.ui.worktree.WorktreeRef

enum class PaletteKind { VIEW, SETTING, PROJECT, WORKTREE, ACTION }

enum class PaletteCommand { ADD_PROJECT, CLEAN_UP, RELOAD, TOGGLE_SIDEBAR, OPEN_PREFERENCES }

sealed interface PaletteAction {
    data class Open(val destination: ShellDestination) : PaletteAction
    data class SelectWorktree(val ref: WorktreeRef) : PaletteAction
    data class CopyLaunchCommand(val ref: WorktreeRef) : PaletteAction
    data class NewWorktreeIn(val projectId: String) : PaletteAction
    data class RunSelected(val id: ActionId, val agent: String?) : PaletteAction
    data class Run(val command: PaletteCommand) : PaletteAction
}

data class PaletteItem(
    val id: String,
    val kind: PaletteKind,
    val title: String,
    val keys: List<String>,
    val contextKeys: List<String> = emptyList(),
    val context: String? = null,
    val shortcut: String? = null,
    val indicator: StateBadgeKind? = null,
    val machineResource: MachineResource? = null,
    val primary: PaletteAction,
    val secondary: PaletteAction? = null,
)
