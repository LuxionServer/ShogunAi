package app.luxion.shogunai.ui.worktree

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalClipboard
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.ui.actions.ActionId
import app.luxion.shogunai.ui.actions.PerformAction
import app.luxion.shogunai.ui.actions.contextMenuEntries
import app.luxion.shogunai.ui.actions.toMenu
import app.luxion.shogunai.ui.components.ContextMenuBox
import app.luxion.shogunai.ui.components.LocalErrorFixActions
import app.luxion.shogunai.ui.components.MenuEntry
import app.luxion.shogunai.ui.components.copyText
import app.luxion.shogunai.ui.i18n.LocalStrings
import kotlinx.coroutines.launch

@Composable
fun rememberWorktreeActions(holder: ProjectWorktrees, worktree: Worktree, onCopyPath: () -> Unit = {}): PerformAction {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val fixActions = LocalErrorFixActions.current
    return { spec, agent ->
        when (spec.id) {
            ActionId.CopyPath -> scope.launch {
                clipboard.copyText(worktree.path)
                onCopyPath()
            }
            ActionId.SetDefaultRunner -> fixActions?.openPreferences?.invoke()
            else -> holder.perform(worktree, spec.id, agent)
        }
    }
}

@Composable
fun worktreeContextMenu(holder: ProjectWorktrees, worktree: Worktree): () -> List<MenuEntry> {
    val strings = LocalStrings.current
    val perform = rememberWorktreeActions(holder, worktree)
    return { holder.actionCatalog(worktree, strings).contextMenuEntries(strings).toMenu(strings, perform) }
}

@Composable
fun WorktreeContextMenu(entries: () -> List<MenuEntry>, content: @Composable () -> Unit) {
    ContextMenuBox(entries = entries, content = content)
}
