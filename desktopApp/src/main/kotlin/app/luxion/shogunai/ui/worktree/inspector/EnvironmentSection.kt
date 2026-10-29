package app.luxion.shogunai.ui.worktree.inspector

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalClipboard
import app.luxion.shogunai.domain.model.ObservedPort
import app.luxion.shogunai.domain.model.WorktreeEnvironment
import app.luxion.shogunai.ui.components.GhostButton
import app.luxion.shogunai.ui.components.InspectorSection
import app.luxion.shogunai.ui.components.PropertyList
import app.luxion.shogunai.ui.components.Spacing
import app.luxion.shogunai.ui.components.copyText
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.theme.ShogunTheme
import kotlinx.coroutines.launch

@Composable
fun EnvironmentSection(environment: WorktreeEnvironment?, ports: Map<String, Int>, observed: List<ObservedPort> = emptyList()) {
    val strings = LocalStrings.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val awaiting = environment?.awaitingApproval.orEmpty()
    InspectorSection(
        id = InspectorSectionId.ENVIRONMENT,
        title = strings.environmentTitle,
        state = environmentSectionState(environment, ports, observed),
        summary = environmentSummary(environment, ports, strings),
    ) {
        PropertyList(environmentProperties(environment, ports, observed.map { it.port }.toSet(), strings::portListening))
        if (environment != null && environment.variables.isNotEmpty()) {
            GhostButton(onClick = { scope.launch { clipboard.copyText(environmentAsDotenv(environment)) } }) {
                Text(strings.copyAsEnv)
            }
        }
        if (observed.isNotEmpty()) {
            Text(
                strings.orcaPortsTitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    observed.forEach { Text(observedPortLabel(it), style = ShogunTheme.typography.dataSmall) }
                }
            }
        }
        if (awaiting.isNotEmpty()) {
            Text(
                strings.variablesAwaitingApproval(awaiting),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
