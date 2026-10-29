package app.luxion.shogunai.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.MenuBar
import app.luxion.shogunai.ui.actions.ActionId
import app.luxion.shogunai.ui.actions.ActionShortcut
import app.luxion.shogunai.ui.actions.ActionSpec
import app.luxion.shogunai.ui.i18n.Strings
import app.luxion.shogunai.ui.isMacOs

@Composable
fun FrameWindowScope.AppMenuBar(
    strings: Strings,
    onAddProject: () -> Unit,
    onSave: (() -> Unit)?,
    onNewWorktree: (() -> Unit)?,
    onFind: (() -> Unit)?,
    worktreeMenu: List<ActionSpec>?,
    onWorktreeAction: (ActionId) -> Unit,
    onClearSelection: (() -> Unit)?,
    hasWorktreeSelection: Boolean,
    isEditingText: Boolean,
    onShowActive: (() -> Unit)?,
    onShowAll: (() -> Unit)?,
    onReload: () -> Unit,
    onCommandPalette: () -> Unit,
    onToggleSidebar: () -> Unit,
    onPreferences: () -> Unit,
    onCheckForUpdates: () -> Unit,
    onTour: () -> Unit,
    onCheckInstallation: () -> Unit,
    onCopyDiagnosticReport: () -> Unit,
    onAbout: () -> Unit,
    onQuit: () -> Unit,
) {
    MenuBar {
        Menu(strings.menuFile) {
            onNewWorktree?.let { Item(strings.newWorktree, shortcut = shortcut(Key.N), onClick = it) }
            onSave?.let { Item(strings.menuSave, shortcut = shortcut(Key.S), onClick = it) }
            Item(strings.addProject, shortcut = shortcut(Key.N, shift = true), onClick = onAddProject)
            if (!isMacOs) {
                Separator()
                Item(strings.menuPreferences, shortcut = shortcut(Key.Comma), onClick = onPreferences)
                Separator()
                Item(strings.menuQuit, shortcut = shortcut(Key.Q), onClick = onQuit)
            }
        }
        if (worktreeMenu != null) {
            Menu(strings.menuWorktree) {
                val actsOnSelection = hasWorktreeSelection && !isEditingText
                worktreeMenu.forEach { spec ->
                    if (spec.isDestructive) Separator()
                    Item(
                        spec.label,
                        shortcut = spec.shortcut?.let(::keyShortcut),
                        enabled = actsOnSelection && spec.isAvailable,
                        onClick = { onWorktreeAction(spec.id) },
                    )
                }
                onClearSelection?.let { Item(strings.clearSelection, enabled = hasWorktreeSelection, onClick = it) }
            }
        }
        Menu(strings.menuView) {
            onShowActive?.let { Item(strings.menuActive, shortcut = shortcut(Key.One), onClick = it) }
            onShowAll?.let { Item(strings.menuAllWorktrees, shortcut = shortcut(Key.Two), onClick = it) }
            onFind?.let { Item(strings.menuFind, shortcut = shortcut(Key.F), onClick = it) }
            Item(strings.menuCommandPalette, shortcut = shortcut(Key.K), onClick = onCommandPalette)
            Item(strings.reload, shortcut = shortcut(Key.R), onClick = onReload)
            Item(strings.menuToggleSidebar, shortcut = shortcut(Key.Backslash), onClick = onToggleSidebar)
        }
        Menu(strings.menuHelp) {
            Item(strings.tourMenuItem, onClick = onTour)
            Item(strings.menuCheckForUpdates, onClick = onCheckForUpdates)
            Item(strings.menuCheckInstallation, onClick = onCheckInstallation)
            Item(strings.menuCopyDiagnosticReport, onClick = onCopyDiagnosticReport)
            if (!isMacOs) Item(strings.menuAbout, onClick = onAbout)
        }
    }
}

fun keyShortcut(shortcut: ActionShortcut): KeyShortcut? =
    menuKeys[shortcut.key]?.let { shortcut(it, shortcut.shift) }

private val menuKeys = mapOf("O" to Key.O, "C" to Key.C, "⌫" to Key.Backspace, "N" to Key.N, "R" to Key.R)

private fun shortcut(key: Key, shift: Boolean = false) =
    KeyShortcut(key, ctrl = !isMacOs, meta = isMacOs, shift = shift)
