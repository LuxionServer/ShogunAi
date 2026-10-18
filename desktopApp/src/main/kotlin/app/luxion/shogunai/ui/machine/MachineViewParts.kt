package app.luxion.shogunai.ui.machine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.luxion.shogunai.domain.usecase.ProjectOwner
import app.luxion.shogunai.ui.components.DangerButton
import app.luxion.shogunai.ui.components.InlineProgress
import app.luxion.shogunai.ui.components.NeutralTextButton
import app.luxion.shogunai.ui.components.Notice
import app.luxion.shogunai.ui.components.NoticeKind
import app.luxion.shogunai.ui.components.Spacing
import app.luxion.shogunai.ui.components.interactive
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.i18n.worktreeError
import app.luxion.shogunai.ui.theme.ShogunTheme

val MachineActionColumnWidth: Dp = 96.dp
val MachineOwnerColumnWidth: Dp = 220.dp

@Composable
fun MachineViewFrame(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        content()
    }
}

@Composable
fun MachineErrorNotice(error: Throwable?, onDismiss: () -> Unit) {
    if (error == null) return
    Notice(kind = NoticeKind.ERROR, message = LocalStrings.current.worktreeError(error), onDismiss = onDismiss)
}

@Composable
fun MachineSecondaryText(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

@Composable
fun MachineDataText(text: String, modifier: Modifier = Modifier, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(
        text,
        style = ShogunTheme.typography.dataSmall,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
fun MachineRowLine(content: @Composable (androidx.compose.foundation.layout.RowScope.() -> Unit)) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
fun OwnerCell(
    owner: ProjectOwner?,
    projectName: (String) -> String,
    onOpenWorktree: (projectId: String, path: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    if (owner == null) {
        MachineDataText("", modifier)
        return
    }
    val label = owner.label(strings, projectName)
    if (owner is ProjectOwner.Owned) {
        Text(
            label,
            style = ShogunTheme.typography.dataSmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier.interactive { onOpenWorktree(owner.projectId, owner.worktreePath) },
        )
    } else {
        MachineDataText(label, modifier)
    }
}

@Composable
fun MachineBusy(isBusy: Boolean) {
    if (isBusy) InlineProgress()
}

@Composable
fun ConfirmDeleteOrphansDialog(message: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val strings = LocalStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.confirmDeleteOrphansTitle) },
        text = { Text(message) },
        confirmButton = { DangerButton(onClick = onConfirm) { Text(strings.deleteOrphans) } },
        dismissButton = { NeutralTextButton(onClick = onDismiss) { Text(strings.cancel) } },
    )
}
