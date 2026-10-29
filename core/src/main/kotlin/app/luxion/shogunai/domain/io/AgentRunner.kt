package app.luxion.shogunai.domain.io

import app.luxion.shogunai.domain.model.AgentLaunchConfig
import app.luxion.shogunai.domain.model.GroupRunnerOpening
import app.luxion.shogunai.domain.model.Multirepo
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.RunnerId
import app.luxion.shogunai.domain.model.RunnerOpening
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.usecase.WorktreeGroup

interface AgentRunner {

    val id: RunnerId

    fun isInstalled(): Boolean

    suspend fun openWorktree(config: ProjectConfig, worktree: Worktree, agent: AgentLaunchConfig = config.agentLaunchConfig): Result<RunnerOpening>

    suspend fun openGroup(group: WorktreeGroup): Result<List<GroupRunnerOpening>>

    suspend fun openMultirepoAgent(multirepo: Multirepo): Result<RunnerOpening>
}
