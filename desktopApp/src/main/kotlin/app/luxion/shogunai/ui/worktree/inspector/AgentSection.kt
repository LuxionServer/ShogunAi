package app.luxion.shogunai.ui.worktree.inspector

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextOverflow
import app.luxion.shogunai.domain.model.RunningAgent
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.ui.components.AgentIndicator
import app.luxion.shogunai.ui.components.InspectorSection
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.theme.ShogunTheme
import app.luxion.shogunai.ui.worktree.agentIndicator
import java.time.Instant

@Composable
fun AgentSection(worktree: Worktree, agents: Map<String, List<RunningAgent>>?, readAt: Instant?) {
    val strings = LocalStrings.current
    val running = agents?.get(worktree.path).orEmpty()
    InspectorSection(
        id = InspectorSectionId.AGENT,
        title = strings.agentSectionTitle,
        state = agentSectionState(agents, worktree, strings),
    ) {
        running.forEach { agent ->
            agentIndicator(listOf(agent), readAt, Instant.now(), strings)?.let { AgentIndicator(it) }
            agent.activity?.let { activity ->
                agentActivityLines(activity, strings).forEach { line ->
                    Text(
                        line,
                        style = ShogunTheme.typography.dataSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = DETAIL_TEXT_MAX_LINES,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
