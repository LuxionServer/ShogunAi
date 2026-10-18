package app.luxion.shogunai.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import app.luxion.shogunai.ui.i18n.ErrorFix
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.i18n.TechnicalDetails
import app.luxion.shogunai.ui.i18n.describe
import app.luxion.shogunai.ui.theme.ShogunTheme

class ErrorFixActions(
    val openProjectSettings: (projectId: String) -> Unit,
    val openPreferences: () -> Unit,
)

val LocalErrorFixActions = staticCompositionLocalOf<ErrorFixActions?> { null }

private val DetailsMaxHeight = 200.dp

@Composable
fun ErrorNotice(
    error: Throwable,
    onDismiss: (() -> Unit)?,
    modifier: Modifier = Modifier,
    projectId: String? = null,
    onRetry: (() -> Unit)? = null,
) {
    val strings = LocalStrings.current
    val description = strings.describe(error)
    val fixAction = fixActionFor(description.fix, projectId)
    Notice(
        kind = NoticeKind.ERROR,
        message = description.summary,
        onDismiss = onDismiss,
        modifier = modifier,
        details = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(description.nextStep, style = MaterialTheme.typography.bodySmall)
                if (fixAction != null || onRetry != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                        fixAction?.let { (label, action) ->
                            SecondaryButton(onClick = action) { Text(label, style = MaterialTheme.typography.bodySmall) }
                        }
                        onRetry?.let {
                            SecondaryButton(onClick = it) { Text(strings.tryAgain, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
                description.details?.takeIf { it.asPlainText().isNotBlank() }?.let { TechnicalDetailsDisclosure(it) }
            }
        },
    )
}

@Composable
private fun fixActionFor(fix: ErrorFix?, projectId: String?): Pair<String, () -> Unit>? {
    val actions = LocalErrorFixActions.current ?: return null
    val strings = LocalStrings.current
    return when (fix) {
        ErrorFix.OpenProjectSettings -> projectId?.let { strings.openProjectSettings to { actions.openProjectSettings(it) } }
        ErrorFix.OpenPreferences -> strings.openPreferences to actions.openPreferences
        null -> null
    }
}

@Composable
private fun TechnicalDetailsDisclosure(details: TechnicalDetails) {
    val strings = LocalStrings.current
    val clipboard = LocalClipboardManager.current
    var expanded by remember(details) { mutableStateOf(false) }
    val text = details.asPlainText()
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NeutralTextButton(onClick = { expanded = !expanded }) {
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                    contentDescription = null,
                )
                Text(strings.technicalDetails, style = MaterialTheme.typography.bodySmall)
            }
            if (expanded) {
                IconAction(
                    icon = Icons.Default.ContentCopy,
                    label = strings.copyTechnicalDetails,
                    onClick = { clipboard.setText(AnnotatedString(text)) },
                )
            }
        }
        if (expanded) {
            SelectionContainer {
                Text(
                    text,
                    style = ShogunTheme.typography.dataSmall,
                    modifier = Modifier.fillMaxWidth().heightIn(max = DetailsMaxHeight).verticalScroll(rememberScrollState()),
                )
            }
        }
    }
}
