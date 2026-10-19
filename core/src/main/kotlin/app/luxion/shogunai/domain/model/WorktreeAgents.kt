package app.luxion.shogunai.domain.model

object WorktreeAgents {
    fun agentFor(config: ProjectConfig, choices: Map<String, String>, worktreePath: String): AgentLaunchConfig =
        config.agentNamed(choices[worktreePath])

    fun choose(
        config: ProjectConfig,
        choices: Map<String, String>,
        worktreePath: String,
        agent: AgentLaunchConfig,
    ): Map<String, String> =
        if (agent.displayName == config.agentLaunchConfig.displayName) {
            choices - worktreePath
        } else {
            choices + (worktreePath to agent.displayName)
        }

    fun prune(
        config: ProjectConfig,
        choices: Map<String, String>,
        existingWorktreePaths: Set<String>,
    ): Map<String, String> {
        val names = config.agents.map { it.displayName }.toSet()
        val roots = listOf(config.baseRepositoryPath, config.worktreesRoot).map { it.trimEnd('/') + "/" }
        return choices.filter { (path, name) ->
            val belongsToProject = roots.any { path.startsWith(it) }
            !belongsToProject || (path in existingWorktreePaths && name in names)
        }
    }
}
