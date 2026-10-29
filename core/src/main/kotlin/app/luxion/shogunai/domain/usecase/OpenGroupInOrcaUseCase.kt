package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.io.OrcaClient
import app.luxion.shogunai.domain.model.AGENT_START_SECONDS
import app.luxion.shogunai.domain.model.HostOs
import app.luxion.shogunai.domain.model.Multirepo
import app.luxion.shogunai.domain.model.OrcaOpening
import app.luxion.shogunai.domain.model.OrcaWorkspace
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.model.groupMemberSessionName
import app.luxion.shogunai.domain.model.multirepoSessionName
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

data class GroupOrcaOpening(val alias: String, val path: String, val result: Result<OrcaOpening>)

class OpenGroupInOrcaUseCase(
    private val orca: OrcaClient,
    private val resolveEnvironment: ResolveWorktreeEnvironmentUseCase,
    private val naming: ClaudeSessionNaming = ClaudeSessionNaming(),
    private val usesPowerShell: Boolean = HostOs.current() == HostOs.WINDOWS,
    private val samePath: (String, String) -> Boolean = ::sameDirectory,
    private val timeout: Duration = AGENT_START_SECONDS.seconds,
    private val pollInterval: Duration = 500.milliseconds,
    private val runningEmulator: suspend (ProjectConfig, String) -> String? = { _, _ -> null },
) {
    suspend operator fun invoke(group: WorktreeGroup): Result<List<GroupOrcaOpening>> {
        if (!orca.isInstalled()) return Result.failure(WorktreeError.OrcaNotInstalled)
        return Result.success(
            group.worktrees.map { member ->
                val open = OpenWorktreeInOrcaUseCase(member.config, orca, resolveEnvironment, naming, usesPowerShell, samePath, timeout, pollInterval) { path ->
                    runningEmulator(member.config, path)
                }
                val opening = open(member.worktree, member.config.agentLaunchConfig, groupMemberSessionName(member.alias, group.title))
                GroupOrcaOpening(member.alias, member.worktree.path, opening)
            },
        )
    }
}

class OpenMultirepoAgentInOrcaUseCase(
    private val orca: OrcaClient,
    naming: ClaudeSessionNaming = ClaudeSessionNaming(),
    private val samePath: (String, String) -> Boolean = ::sameDirectory,
    timeout: Duration = AGENT_START_SECONDS.seconds,
    pollInterval: Duration = 500.milliseconds,
) {
    private val launcher = OrcaAgentLauncher(orca, naming, samePath, timeout, pollInterval)

    suspend operator fun invoke(multirepo: Multirepo): Result<OrcaOpening> = runCatching {
        launcher.ensureRunning()
        val folder = multirepo.folder
        val workspace = folderWorkspace(multirepo)
        val agent = multirepo.agent
        val sessionName = multirepoSessionName(multirepo)
        launcher.focusLiveAgent(workspace, folder, sessionName)
            ?: launcher.start(workspace, folder, agent.resolvedCommand(), agent.displayName, sessionName)
    }

    private suspend fun folderWorkspace(multirepo: Multirepo): OrcaWorkspace {
        val rows = orca.agentsByWorktree().filter { samePath(it.path, multirepo.folder) }
        val chosen = rows.firstOrNull { it.agents.isNotEmpty() } ?: rows.firstOrNull()
        if (chosen != null) return chosen.workspace
        if (!multirepo.folderIsRepository) throw WorktreeError.OrcaMultirepoFolderNotOpen(multirepo.folder)
        launcher.registeredRepo(multirepo.folder)
        return OrcaWorkspace.Worktree(multirepo.folder)
    }
}
