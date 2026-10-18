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
    onNewWorktree: (() -> Unit)?,
    onFind: (() -> Unit)?,
    onCopySelectedLaunchCommand: (() -> Unit)?,
    onDeleteSelectedWorktree: (() -> Unit)?,
    onClearSelection: (() -> Unit)?,
    onShowActive: (() -> Unit)?,
    onShowAll: (() -> Unit)?,
    onReload: () -> Unit,
    onToggleSidebar: () -> Unit,
    onPreferences: () -> Unit,
    onAbout: () -> Unit,
    onQuit: () -> Unit,
) {
    MenuBar {
        Menu(strings.menuFile) {
            onNewWorktree?.let { Item(strings.newWorktree, shortcut = shortcut(Key.N), onClick = it) }
            Item(strings.addProject, shortcut = shortcut(Key.N, shift = true), onClick = onAddProject)
            if (!isMacOs) {
                Separator()
                Item(strings.menuPreferences, shortcut = shortcut(Key.Comma), onClick = onPreferences)
                Separator()
                Item(strings.menuQuit, shortcut = shortcut(Key.Q), onClick = onQuit)
            }
        }
        Menu(strings.menuWorktree) {
            onCopySelectedLaunchCommand?.let { Item(strings.copyLaunchCommand, shortcut = shortcut(Key.C, shift = true), onClick = it) }
            onDeleteSelectedWorktree?.let { Item(strings.deleteWorktree, shortcut = shortcut(Key.Backspace), onClick = it) }
            onClearSelection?.let { Item(strings.clearSelection, onClick = it) }
        }
        Menu(strings.menuView) {
            onShowActive?.let { Item(strings.menuActive, shortcut = shortcut(Key.One), onClick = it) }
            onShowAll?.let { Item(strings.menuAllWorktrees, shortcut = shortcut(Key.Two), onClick = it) }
            onFind?.let { Item(strings.menuFind, shortcut = shortcut(Key.F), onClick = it) }
            Item(strings.reload, shortcut = shortcut(Key.R), onClick = onReload)
            Item(strings.menuToggleSidebar, shortcut = shortcut(Key.Backslash), onClick = onToggleSidebar)
        }
        if (!isMacOs) {
            Menu(strings.menuHelp) {
                Item(strings.menuAbout, onClick = onAbout)
            }
        }
    }
}

private fun shortcut(key: Key, shift: Boolean = false) =
    KeyShortcut(key, ctrl = !isMacOs, meta = isMacOs, shift = shift)
