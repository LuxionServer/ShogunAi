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
    mac: Boolean = isMacOs,
) {
    MenuBar {
        Menu(strings.menuFile) {
            onNewWorktree?.let { Item(strings.newWorktree, shortcut = menuShortcut(Key.N, false, mac), onClick = it) }
            onSave?.let { Item(strings.menuSave, shortcut = menuShortcut(Key.S, false, mac), onClick = it) }
            Item(strings.addProject, shortcut = menuShortcut(Key.N, true, mac), onClick = onAddProject)
            if (!mac) {
                Separator()
                Item(strings.menuPreferences, shortcut = menuShortcut(Key.Comma, false, mac), onClick = onPreferences)
                Separator()
                Item(strings.menuQuit, shortcut = menuShortcut(Key.Q, false, mac), onClick = onQuit)
            }
        }
        if (worktreeMenu != null) {
            Menu(strings.menuWorktree) {
                val actsOnSelection = hasWorktreeSelection && !isEditingText
                worktreeMenu.forEach { spec ->
                    if (spec.isDestructive) Separator()
                    Item(
                        spec.label,
                        shortcut = spec.shortcut?.let { keyShortcut(it, mac) },
                        enabled = actsOnSelection && spec.isAvailable,
                        onClick = { onWorktreeAction(spec.id) },
                    )
                }
                onClearSelection?.let { Item(strings.clearSelection, enabled = hasWorktreeSelection, onClick = it) }
            }
        }
        Menu(strings.menuView) {
            onShowActive?.let { Item(strings.menuActive, shortcut = menuShortcut(Key.One, false, mac), onClick = it) }
            onShowAll?.let { Item(strings.menuAllWorktrees, shortcut = menuShortcut(Key.Two, false, mac), onClick = it) }
            onFind?.let { Item(strings.menuFind, shortcut = menuShortcut(Key.F, false, mac), onClick = it) }
            Item(strings.menuCommandPalette, shortcut = menuShortcut(Key.K, false, mac), onClick = onCommandPalette)
            Item(strings.reload, shortcut = menuShortcut(Key.R, false, mac), onClick = onReload)
            Item(strings.menuToggleSidebar, shortcut = menuShortcut(Key.Backslash, false, mac), onClick = onToggleSidebar)
        }
        Menu(strings.menuHelp) {
            Item(strings.tourMenuItem, onClick = onTour)
            Item(strings.menuCheckForUpdates, onClick = onCheckForUpdates)
            Item(strings.menuCheckInstallation, onClick = onCheckInstallation)
            Item(strings.menuCopyDiagnosticReport, onClick = onCopyDiagnosticReport)
            if (!mac) Item(strings.menuAbout, onClick = onAbout)
        }
    }
}

fun keyShortcut(shortcut: ActionShortcut, mac: Boolean = isMacOs): KeyShortcut? =
    menuKeys[shortcut.key]?.let { menuShortcut(it, shortcut.shift, mac) }

private val menuKeys = mapOf("O" to Key.O, "C" to Key.C, "⌫" to Key.Backspace, "N" to Key.N, "R" to Key.R)

private fun menuShortcut(key: Key, shift: Boolean, mac: Boolean) =
    KeyShortcut(key, ctrl = !mac, meta = mac, shift = shift)
