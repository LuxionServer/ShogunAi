package app.luxion.shogunai.ui.multirepo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.luxion.shogunai.domain.model.Multirepo
import app.luxion.shogunai.ui.components.InlineProgress
import app.luxion.shogunai.ui.components.Notice
import app.luxion.shogunai.ui.components.NoticeKind
import app.luxion.shogunai.ui.components.PrimaryButton
import app.luxion.shogunai.ui.components.Spacing
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.theme.ShogunTheme

private val ReviewMaxWidth = 720.dp
private val UNPRINTABLE = Regex("[\\p{Cc}\\p{Cf}\\u2028\\u2029]")

data class MultirepoReviewLine(val label: String, val path: String, val outsideParent: Boolean)

fun multirepoReviewLines(multirepo: Multirepo, workspaceLabel: String, worktreesRootLabel: String): List<MultirepoReviewLine> =
    multirepo.repositories.map { repository ->
        val label = if (repository == multirepo.workspaceRepository) "${repository.alias} ($workspaceLabel)" else repository.alias
        MultirepoReviewLine(label, printablePath(repository.path), multirepo.isOutsideParent(repository.path))
    } + MultirepoReviewLine(worktreesRootLabel, printablePath(multirepo.worktreesRoot), multirepo.isOutsideParent(multirepo.worktreesRoot))

fun multirepoParentFolder(multirepo: Multirepo): String =
    printablePath(multirepo.parentFolder)

private fun printablePath(path: String): String =
    path.replace(UNPRINTABLE) { "\\u%04x".format(it.value.single().code) }

@Composable
fun MultirepoReviewDetails(multirepo: Multirepo, modifier: Modifier = Modifier) {
    val strings = LocalStrings.current
    val parent = multirepoParentFolder(multirepo)
    Column(modifier = modifier.testTag("multirepo-review"), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        multirepo.declaredName?.let { name ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(strings.multirepoNameTitle, style = MaterialTheme.typography.bodyMedium)
                Text(printablePath(name), style = MaterialTheme.typography.bodyMedium)
            }
        }
        multirepoReviewLines(multirepo, strings.workspaceRepositoryTitle, strings.multirepoWorktreesRootTitle).forEach { line ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(line.label, style = MaterialTheme.typography.bodyMedium)
                Column(modifier = Modifier.weight(1f)) {
                    Text(line.path, style = ShogunTheme.typography.data)
                    if (line.outsideParent) {
                        Text(strings.outsideMultirepoParent(parent), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        val sharedTemplates = multirepo.sharedTemplates
        if (sharedTemplates.isNotEmpty()) {
            Text(strings.multirepoSharedVariablesTitle, style = MaterialTheme.typography.bodyMedium)
            sharedTemplates.forEach { (alias, name, template) ->
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(alias, style = MaterialTheme.typography.bodyMedium)
                    Text("${printablePath(name)}=${printablePath(template)}", style = ShogunTheme.typography.data)
                }
            }
        }
        if (multirepo.ignoresDeclaredAgent) Text(strings.declaredAgentIgnored, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun MultirepoReviewPane(multirepo: Multirepo, isTrusting: Boolean, onTrust: () -> Unit, modifier: Modifier = Modifier) {
    val strings = LocalStrings.current
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(Spacing.md).widthIn(max = ReviewMaxWidth).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Notice(NoticeKind.WARNING, strings.multirepoNeedsReviewExplanation, onDismiss = null)
        Text(multirepo.declarationPath, style = ShogunTheme.typography.data)
        MultirepoReviewDetails(multirepo)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            PrimaryButton(onClick = onTrust, enabled = !isTrusting) { Text(strings.trustMultirepo) }
            if (isTrusting) InlineProgress()
        }
    }
}
