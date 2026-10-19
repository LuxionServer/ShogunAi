package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.executor.CommandTimeouts
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.HerdrClient
import app.luxion.shogunai.domain.model.HerdrAgent
import app.luxion.shogunai.domain.model.HerdrAgentState
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.RunningAgent
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout

class DetectRunningAgentsUseCase(
    private val executor: ShellCommandExecutor,
    private val herdr: HerdrClient? = null,
    private val now: () -> Long = System::currentTimeMillis,
) {
    @Volatile
    private var herdrRetryAt = 0L

    suspend operator fun invoke(config: ProjectConfig, worktrees: List<Worktree>): Result<Map<String, List<RunningAgent>>> =
        invoke(listOf(AgentSearch(config.agentLaunchConfig.agentCommand, worktrees)))

    suspend operator fun invoke(searches: List<AgentSearch>): Result<Map<String, List<RunningAgent>>> = runCatching {
        val worktrees = searches.flatMap { it.worktrees }
        if (worktrees.isEmpty()) return@runCatching emptyMap()
        val (processes, herdrAgents) = coroutineScope {
            val herdrAgents = async { readHerdrAgents() }
            val processes = runCatching { scanProcesses(searches, worktrees) }
            processes to herdrAgents.await()
        }
        merge(processes.getOrDefault(emptyMap()), herdrAgents, worktrees)
            .also { merged -> if (merged.isEmpty()) processes.getOrThrow() }
    }

    private suspend fun scanProcesses(searches: List<AgentSearch>, worktrees: List<Worktree>): Map<String, List<RunningAgent>> {
        val processes = run("lsof", "-d", "cwd", "-F", "pcn").let(::parseLsofWorkingDirectories)
        val worktreeByPid = processes.mapNotNull { (pid, cwd) ->
            owningWorktree(cwd, worktrees)?.let { pid to it.path }
        }.toMap()
        if (worktreeByPid.isEmpty()) return emptyMap()

        val executables = run("ps", "-o", "pid=,comm=", "-p", worktreeByPid.keys.joinToString(","))
            .let(::parsePsExecutables)
        val agentNamesByPath = searches.flatMap { search ->
            val names = KNOWN_AGENTS + listOfNotNull(agentName(search.agentCommand))
            search.worktrees.map { it.path to names }
        }.toMap()

        return executables.mapNotNull { (pid, executable) ->
            val name = executable.substringAfterLast('/')
            val path = worktreeByPid[pid]
            if (path != null && name in agentNamesByPath[path].orEmpty()) path to RunningAgent(name) else null
        }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, agents) -> agents.distinct() }
    }

    private suspend fun readHerdrAgents(): List<HerdrAgent> {
        val client = herdr ?: return emptyList()
        if (now() < herdrRetryAt) return emptyList()
        return try {
            withTimeout(HERDR_TIMEOUT_MILLIS) { client.listAgents() }
        } catch (error: CancellationException) {
            if (error is TimeoutCancellationException) failHerdrRead() else throw error
        } catch (error: Exception) {
            failHerdrRead()
        }
    }

    private fun failHerdrRead(): List<HerdrAgent> {
        herdrRetryAt = now() + HERDR_BACK_OFF_MILLIS
        return emptyList()
    }

    private fun merge(
        processAgents: Map<String, List<RunningAgent>>,
        herdrAgents: List<HerdrAgent>,
        worktrees: List<Worktree>,
    ): Map<String, List<RunningAgent>> {
        if (herdrAgents.isEmpty()) return processAgents
        val herdrByPath = herdrAgents
            .mapNotNull { agent -> owningWorktree(agent.directory, worktrees)?.let { it.path to agent } }
            .groupBy({ it.first }, { it.second })
        return (processAgents.keys + herdrByPath.keys).associateWith { path ->
            val herdrByName = herdrByPath[path].orEmpty().groupBy { it.name }.mapValues { (_, agents) -> mostUrgent(agents) }
            val processNames = processAgents[path].orEmpty().map { it.name }.toSet()
            processAgents[path].orEmpty().map { agent -> herdrByName[agent.name]?.let(::runningAgent) ?: agent } +
                herdrByName.filterKeys { it !in processNames }.values.map(::runningAgent)
        }
    }

    private fun mostUrgent(agents: List<HerdrAgent>): HerdrAgent = agents.maxBy { urgency(it.state) }

    private fun urgency(state: HerdrAgentState?): Int = when (state) {
        HerdrAgentState.BLOCKED -> 4
        HerdrAgentState.DONE -> 3
        HerdrAgentState.WORKING -> 2
        HerdrAgentState.IDLE -> 1
        null -> 0
    }

    private fun runningAgent(agent: HerdrAgent) = RunningAgent(agent.name, agent.state, agent.paneId)

    private fun agentName(agentCommand: String): String? =
        agentCommand.trim().substringBefore(' ').substringAfterLast('/').ifEmpty { null }

    private suspend fun run(vararg command: String): String {
        val result = executor.executeWithin(command.toList(), CommandTimeouts.LISTING)
        if (!result.isSuccess) {
            throw WorktreeError.ProcessListingFailed(result.command, result.exitCode)
        }
        return result.stdout
    }

    private companion object {
        val KNOWN_AGENTS = setOf("claude", "codex", "gemini", "cursor-agent", "opencode", "aider", "copilot")
        const val HERDR_TIMEOUT_MILLIS = 5_000L
        const val HERDR_BACK_OFF_MILLIS = 30_000L
    }
}

data class AgentSearch(val agentCommand: String, val worktrees: List<Worktree>)

fun owningWorktree(cwd: String, worktrees: List<Worktree>): Worktree? {
    val path = normalizePath(cwd)
    return worktrees
        .filter { isInside(path, normalizePath(it.path)) }
        .maxByOrNull { normalizePath(it.path).length }
}

internal fun parseLsofWorkingDirectories(output: String): List<Pair<String, String>> {
    val processes = mutableListOf<Pair<String, String>>()
    var pid: String? = null
    output.lineSequence().forEach { line ->
        when {
            line.startsWith("p") -> pid = line.drop(1)
            line.startsWith("n") -> pid?.let { processes += it to line.drop(1) }
        }
    }
    return processes
}

internal fun parsePsExecutables(output: String): List<Pair<String, String>> =
    output.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .mapNotNull { line ->
            val pid = line.substringBefore(' ')
            val executable = line.substringAfter(' ', "").trim()
            if (executable.isEmpty()) null else pid to executable
        }
        .toList()
