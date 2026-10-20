package app.luxion.shogunai.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.MenuBar
import app.luxion.shogunai.ui.i18n.Strings
import app.luxion.shogunai.ui.isMacOs

@Composable
fun FrameWindowScope.AppMenuBar(
    strings: Strings,
    onAddProject: () -> Unit,
    onSave: (() -> Unit)?,
    onNewWorktree: (() -> Unit)?,
    onFind: (() -> Unit)?,
    onCopySelectedLaunchCommand: (() -> Unit)?,
    onOpenSelectedInHerdr: (() -> Unit)?,
    onDeleteSelectedWorktree: (() -> Unit)?,
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
        Menu(strings.menuWorktree) {
            val actsOnSelection = hasWorktreeSelection && !isEditingText
            onCopySelectedLaunchCommand?.let {
                Item(strings.copyLaunchCommand, shortcut = shortcut(Key.C, shift = true), enabled = actsOnSelection, onClick = it)
            }
            onOpenSelectedInHerdr?.let {
                Item(strings.openInHerdr, shortcut = shortcut(Key.E), enabled = actsOnSelection, onClick = it)
            }
            onDeleteSelectedWorktree?.let {
                Item(strings.deleteWorktree, shortcut = shortcut(Key.Backspace), enabled = actsOnSelection, onClick = it)
            }
            onClearSelection?.let { Item(strings.clearSelection, enabled = hasWorktreeSelection, onClick = it) }
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
            Item(strings.menuCheckForUpdates, onClick = onCheckForUpdates)
            if (!isMacOs) Item(strings.menuAbout, onClick = onAbout)
        }
    }
}

private fun shortcut(key: Key, shift: Boolean = false) =
    KeyShortcut(key, ctrl = !isMacOs, meta = isMacOs, shift = shift)
