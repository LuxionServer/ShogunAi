package app.luxion.shogunai.ui.worktree.inspector

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalClipboard
import app.luxion.shogunai.domain.model.WorktreeEnvironment
import app.luxion.shogunai.ui.components.GhostButton
import app.luxion.shogunai.ui.components.InspectorSection
import app.luxion.shogunai.ui.components.PropertyList
import app.luxion.shogunai.ui.components.copyText
import app.luxion.shogunai.ui.i18n.LocalStrings
import kotlinx.coroutines.launch

@Composable
fun EnvironmentSection(environment: WorktreeEnvironment?, ports: Map<String, Int>) {
    val strings = LocalStrings.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val awaiting = environment?.awaitingApproval.orEmpty()
    InspectorSection(
        id = InspectorSectionId.ENVIRONMENT,
        title = strings.environmentTitle,
        state = environmentSectionState(environment, ports),
        summary = environmentSummary(environment, ports, strings),
    ) {
        PropertyList(environmentProperties(environment, ports))
        if (environment != null && environment.variables.isNotEmpty()) {
            GhostButton(onClick = { scope.launch { clipboard.copyText(environmentAsDotenv(environment)) } }) {
                Text(strings.copyAsEnv)
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
