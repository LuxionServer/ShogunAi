package app.luxion.shogunai.ui.worktree.inspector

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.style.TextOverflow
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeOrigin
import app.luxion.shogunai.ui.components.InlineConfirmIconAction
import app.luxion.shogunai.ui.components.Spacing
import app.luxion.shogunai.ui.components.StatusChip
import app.luxion.shogunai.ui.components.copyText
import app.luxion.shogunai.ui.components.rememberInlineConfirm
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.theme.ShogunTheme
import app.luxion.shogunai.ui.theme.Status
import app.luxion.shogunai.ui.worktree.originLabel
import kotlinx.coroutines.launch

@Composable
fun InspectorHeader(worktree: Worktree, origin: WorktreeOrigin?, projectName: String?) {
    val strings = LocalStrings.current
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        projectName?.let {
            Text(it, style = MaterialTheme.typography.labelMedium, color = secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Text(
                worktree.branch ?: strings.detachedHead,
                style = ShogunTheme.typography.data,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            origin?.let { StatusChip(Status.NEUTRAL, originLabel(it, strings)) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            SelectionContainer(Modifier.weight(1f)) {
                Text(worktree.path, style = ShogunTheme.typography.dataSmall, color = secondary)
            }
            InlineConfirmIconAction(
                label = strings.copyPath,
                onClick = { scope.launch { clipboard.copyText(worktree.path) } },
                state = key(worktree.path) { rememberInlineConfirm() },
            )
        }
    }
}
