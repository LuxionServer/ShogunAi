package app.luxion.shogunai.ui.worktree

import app.luxion.shogunai.domain.model.AgentState
import app.luxion.shogunai.domain.model.RunningAgent
import app.luxion.shogunai.ui.components.AgentIndicatorModel
import app.luxion.shogunai.ui.components.AgentPhase
import app.luxion.shogunai.ui.i18n.Strings
import java.time.Instant

fun AgentState?.phase(): AgentPhase = when (this) {
    AgentState.BLOCKED -> AgentPhase.NEEDS_YOU
    AgentState.DONE -> AgentPhase.DONE
    AgentState.WORKING -> AgentPhase.WORKING
    AgentState.IDLE -> AgentPhase.IDLE
    null -> AgentPhase.RUNNING
}

fun agentIndicator(agents: List<RunningAgent>, readAt: Instant?, now: Instant, strings: Strings): AgentIndicatorModel? {
    val sorted = agents.sortedBy { it.state.phase().ordinal }
    val urgent = sorted.firstOrNull() ?: return null
    val lines = sorted.map { strings.agentLabel(displayName(it.name), it.state.phase()) } +
        strings.agentStateSource(urgent.stateSource) +
        listOfNotNull(readAt?.let { strings.agentsUpdated(relativeTime(it, now, strings)) })
    return AgentIndicatorModel(
        phase = urgent.state.phase(),
        label = strings.agentLabel(displayName(urgent.name), urgent.state.phase()),
        moreCount = agents.size - 1,
        tooltip = lines.joinToString("\n"),
    )
}

private fun displayName(name: String): String = name.replaceFirstChar { it.uppercase() }
