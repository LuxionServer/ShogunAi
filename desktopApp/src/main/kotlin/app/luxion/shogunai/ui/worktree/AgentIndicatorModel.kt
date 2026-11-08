package app.luxion.shogunai.ui.worktree

import app.luxion.shogunai.domain.model.AgentState
import app.luxion.shogunai.domain.model.AgentStateSource
import app.luxion.shogunai.domain.model.RunnerId
import app.luxion.shogunai.domain.model.RunningAgent
import app.luxion.shogunai.ui.components.AgentIndicatorModel
import app.luxion.shogunai.ui.components.AgentPhase
import app.luxion.shogunai.ui.i18n.Strings
import java.time.Instant

data class AgentFocusTarget(val agent: String, val runner: RunnerId, val herdrPaneId: String? = null)

fun AgentState?.phase(): AgentPhase = when (this) {
    AgentState.BLOCKED -> AgentPhase.NEEDS_YOU
    AgentState.DONE -> AgentPhase.DONE
    AgentState.WORKING -> AgentPhase.WORKING
    AgentState.IDLE -> AgentPhase.IDLE
    null -> AgentPhase.RUNNING
}

fun RunningAgent.runner(): RunnerId? = when {
    herdrPaneId != null -> RunnerId.HERDR
    stateSource == AgentStateSource.ORCA -> RunnerId.ORCA
    else -> null
}

fun agentFocusTarget(agents: List<RunningAgent>): AgentFocusTarget? =
    byUrgency(agents).firstNotNullOfOrNull { agent ->
        agent.runner()?.let { AgentFocusTarget(agent.name, it, agent.herdrPaneId) }
    }

fun agentIndicator(
    agents: List<RunningAgent>,
    readAt: Instant?,
    now: Instant,
    strings: Strings,
    clickToFocus: Boolean = false,
): AgentIndicatorModel? {
    val sorted = byUrgency(agents)
    val urgent = sorted.firstOrNull() ?: return null
    val target = sorted.firstOrNull { it.runner() != null }?.takeIf { clickToFocus }
    val lines = sorted.map { agentLine(it, it === target, strings) } +
        strings.agentStateSource(urgent.stateSource) +
        listOfNotNull(readAt?.let { strings.agentsUpdated(relativeTime(it, now, strings)) })
    return AgentIndicatorModel(
        phase = urgent.state.phase(),
        label = strings.agentLabel(displayName(urgent.name), urgent.state.phase()),
        moreCount = agents.size - 1,
        tooltip = lines.joinToString("\n"),
        focusable = target != null,
    )
}

private fun byUrgency(agents: List<RunningAgent>): List<RunningAgent> = agents.sortedBy { it.state.phase().ordinal }

private fun agentLine(agent: RunningAgent, focusTarget: Boolean, strings: Strings): String {
    val label = strings.agentLabel(displayName(agent.name), agent.state.phase())
    val located = agent.runner()?.let { "$label · ${strings.agentInRunner(it)}" } ?: label
    return if (focusTarget) "$located — ${strings.clickToFocus}" else located
}

private fun displayName(name: String): String = name.replaceFirstChar { it.uppercase() }
