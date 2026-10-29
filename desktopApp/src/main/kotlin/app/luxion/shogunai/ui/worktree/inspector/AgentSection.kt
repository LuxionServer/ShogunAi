package app.luxion.shogunai.ui.worktree.inspector

import androidx.compose.runtime.Composable
import app.luxion.shogunai.domain.model.RunningAgent
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.ui.components.AgentIndicator
import app.luxion.shogunai.ui.components.InspectorSection
import app.luxion.shogunai.ui.i18n.LocalStrings
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
        }
    }
}
