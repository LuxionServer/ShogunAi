package app.luxion.shogunai.ui.multirepo

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import app.luxion.shogunai.domain.model.MultirepoUnavailability
import app.luxion.shogunai.domain.model.UnavailableMultirepo
import app.luxion.shogunai.ui.components.DangerButton
import app.luxion.shogunai.ui.components.EmptyState
import app.luxion.shogunai.ui.components.NeutralTextButton
import app.luxion.shogunai.ui.components.ShogunDialog
import app.luxion.shogunai.ui.components.StateAction
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.i18n.Strings

fun Strings.unavailabilityOf(folder: String, reason: MultirepoUnavailability): String = when (reason) {
    MultirepoUnavailability.FolderUnreachable -> multirepoFolderUnreachable(folder)
    MultirepoUnavailability.DeclarationMissing -> multirepoDeclarationMissing(folder)
    is MultirepoUnavailability.InvalidDeclaration -> multirepoDeclarationInvalid(reason.detail)
}

@Composable
fun UnavailableMultirepoView(multirepo: UnavailableMultirepo, onTryAgain: () -> Unit, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    val strings = LocalStrings.current
    EmptyState(
        icon = Icons.Default.FolderOff,
        title = "${multirepo.name} · ${strings.multirepoUnavailable}",
        message = strings.unavailabilityOf(multirepo.folder, multirepo.reason),
        action = StateAction(strings.tryAgain, onTryAgain),
        link = StateAction(strings.remove, onRemove),
        modifier = modifier.testTag("unavailable-multirepo"),
    )
}

@Composable
fun RemoveMultirepoDialog(multirepo: UnavailableMultirepo, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val strings = LocalStrings.current
    ShogunDialog(
        title = strings.removeMultirepoTitle,
        onDismissRequest = onDismiss,
        confirm = { DangerButton(onClick = onConfirm) { Text(strings.remove) } },
        dismiss = { NeutralTextButton(onClick = onDismiss) { Text(strings.cancel) } },
    ) {
        Text(strings.removeMultirepoMessage(multirepo.name))
    }
}
