package app.luxion.shogunai.ui.shell

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.ui.components.DangerButton
import app.luxion.shogunai.ui.components.NeutralTextButton
import app.luxion.shogunai.ui.components.ShogunDialog
import app.luxion.shogunai.ui.i18n.LocalStrings

@Composable
internal fun RemoveProjectDialog(project: Project, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val strings = LocalStrings.current
    ShogunDialog(
        title = strings.removeProjectTitle,
        onDismissRequest = onDismiss,
        confirm = { DangerButton(onClick = onConfirm) { Text(strings.remove) } },
        dismiss = { NeutralTextButton(onClick = onDismiss) { Text(strings.cancel) } },
    ) {
        Text(strings.removeProjectMessage(project.name))
    }
}
