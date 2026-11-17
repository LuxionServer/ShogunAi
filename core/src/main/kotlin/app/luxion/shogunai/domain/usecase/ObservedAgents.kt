package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.model.AgentRunner
import app.luxion.shogunai.domain.model.AgentStateSource
import app.luxion.shogunai.domain.model.NotifiedWorktree
import app.luxion.shogunai.domain.model.ObservedAgent
import app.luxion.shogunai.domain.model.RunningAgent

fun observedAgents(worktrees: List<NotifiedWorktree>, detected: Map<String, List<RunningAgent>>): List<ObservedAgent> =
    worktrees.flatMap { worktree ->
        detected[worktree.worktreePath].orEmpty().map { agent ->
            ObservedAgent(worktree, agent.name, agentDisplayName(agent.name), agent.state, runnerOf(agent), agent.focusedInRunner)
        }
    }

fun agentDisplayName(name: String): String = name.take(1).uppercase() + name.drop(1)

private fun runnerOf(agent: RunningAgent): AgentRunner? = when {
    agent.herdrPaneId != null -> AgentRunner.HERDR
    agent.stateSource == AgentStateSource.ORCA -> AgentRunner.ORCA
    else -> null
}
