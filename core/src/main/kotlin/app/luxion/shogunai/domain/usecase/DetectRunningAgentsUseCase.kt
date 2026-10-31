package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.executor.CommandTimeouts
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.AgentSessionStore
import app.luxion.shogunai.domain.io.ClaudeSessionRegistry
import app.luxion.shogunai.domain.io.HerdrClient
import app.luxion.shogunai.domain.model.AgentSessionRecord
import app.luxion.shogunai.domain.model.HerdrAgent
import app.luxion.shogunai.domain.model.OrcaAgent
import app.luxion.shogunai.domain.model.OrcaWorktree
import app.luxion.shogunai.domain.model.AgentState
import app.luxion.shogunai.domain.model.AgentStateSource
import app.luxion.shogunai.domain.model.ClaudeSessionRef
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.RunningAgent
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import java.io.File

class DetectRunningAgentsUseCase(
    private val executor: ShellCommandExecutor,
    private val herdr: HerdrClient? = null,
    private val sessionStore: AgentSessionStore? = null,
    private val transcriptModifiedAt: (String) -> Long? = ::fileModifiedAt,
    private val now: () -> Long = System::currentTimeMillis,
    private val hasGitEntry: (String) -> Boolean = { directory -> File(directory, ".git").exists() },
    private val claudeSessions: ClaudeSessionRegistry? = null,
    private val orca: OrcaWorktreeReader? = null,
    private val canonical: (String) -> String = ::canonicalDirectory,
) {
    @Volatile
    private var herdrRetryAt = 0L

    suspend operator fun invoke(config: ProjectConfig, worktrees: List<Worktree>): Result<Map<String, List<RunningAgent>>> =
        invoke(listOf(AgentSearch(config.agents.map { it.agentCommand }, worktrees)))

    suspend operator fun invoke(searches: List<AgentSearch>): Result<Map<String, List<RunningAgent>>> = runCatching {
        val worktrees = searches.flatMap { it.worktrees }
        if (worktrees.isEmpty()) return@runCatching emptyMap()
        val exact = searches.filter { it.exact }.flatMap { it.worktrees }.map { normalizePath(it.path) }.toSet()
        val (processes, herdrAgents, orcaWorktrees) = coroutineScope {
            val herdrAgents = async { readHerdrAgents() }
            val orcaWorktrees = async { orca?.read().orEmpty() }
            val processes = runCatching { scanProcesses(searches, worktrees, exact) }
            Triple(processes, herdrAgents.await(), orcaWorktrees.await())
        }
        val scan = processes.getOrDefault(ProcessScan())
        merge(withSessionStates(scan, worktrees), herdrAgents, worktrees, exact)
            .let { merged -> mergeOrca(merged, orcaWorktrees, worktrees) }
            .let { merged -> withClaudeSessions(merged, scan.claudePids, worktrees, exact) }
            .also { merged -> if (merged.isEmpty()) processes.getOrThrow() }
    }

    private suspend fun scanProcesses(searches: List<AgentSearch>, worktrees: List<Worktree>, exact: Set<String>): ProcessScan {
        val agentNamesByPath = searches.flatMap { search ->
            val names = KNOWN_AGENTS + search.agentCommands.mapNotNull(::agentName)
            search.worktrees.map { it.path to names }
        }.toMap()
        val agentNames = agentNamesByPath.values.flatten().toSet()
        val candidates = run("ps", "-A", "-o", "pid=,comm=")
            .let(::parsePsExecutables)
            .map { (pid, executable) -> pid to executable.substringAfterLast('/') }
            .filter { (_, name) -> name in agentNames }
        if (candidates.isEmpty()) return ProcessScan()

        val worktreeByPid = workingDirectories(candidates.map { it.first })
            .mapNotNull { (pid, cwd) -> attributedWorktree(cwd, worktrees, exact)?.let { pid to it.path } }
            .toMap()
        val attributed = candidates.mapNotNull { (pid, name) -> worktreeByPid[pid]?.let { Triple(pid, name, it) } }
        val agents = attributed
            .filter { (_, name, path) -> name in agentNamesByPath.getValue(path) }
            .groupBy({ it.third }, { RunningAgent(it.second) })
            .mapValues { (_, agents) -> agents.distinct() }
        val claudePids = attributed
            .filter { (_, name, _) -> name == CLAUDE }
            .mapNotNull { (pid, _, _) -> pid.toLongOrNull() }
            .toSet()
        return ProcessScan(agents, claudePids)
    }

    private suspend fun workingDirectories(pids: List<String>): List<Pair<String, String>> {
        val command = listOf("lsof", "-a", "-d", "cwd", "-F", "pcn", "-p", pids.joinToString(","))
        val result = executor.executeWithin(command, CommandTimeouts.LISTING)
        if (!result.isSuccess && result.exitCode != LSOF_SOME_NOT_FOUND) {
            throw WorktreeError.ProcessListingFailed(result.command, result.exitCode)
        }
        val requested = pids.toSet()
        return parseLsofWorkingDirectories(result.stdout).filter { (pid, _) -> pid in requested }
    }

    private fun withSessionStates(scan: ProcessScan, worktrees: List<Worktree>): Map<String, List<RunningAgent>> {
        val store = sessionStore ?: return scan.agents
        val claudeWorktrees = scan.agents.filterValues { agents -> agents.any { it.name == CLAUDE } }.keys
        val states = worktrees.mapNotNull { worktree ->
            val live = liveSessions(store, worktree.path, scan.claudePids, worktree.path in claudeWorktrees)
            live.maxByOrNull { urgency(it) }?.let { worktree.path to it }
        }.toMap()
        return scan.agents.mapValues { (path, agents) ->
            val state = states[path] ?: return@mapValues agents
            agents.map { agent ->
                if (agent.name == CLAUDE) agent.copy(state = state, stateSource = AgentStateSource.CLAUDE_CODE) else agent
            }
        }
    }

    private fun withClaudeSessions(
        agents: Map<String, List<RunningAgent>>,
        claudePids: Set<Long>,
        worktrees: List<Worktree>,
        exact: Set<String>,
    ): Map<String, List<RunningAgent>> {
        val registry = claudeSessions ?: return agents
        val sessionsByPath = runCatching { registry.list() }.getOrDefault(emptyList())
            .filter { it.pid in claudePids }
            .mapNotNull { session -> attributedWorktree(session.cwd, worktrees, exact)?.let { it.path to session } }
            .groupBy({ it.first }, { it.second })
        if (sessionsByPath.isEmpty()) return agents
        return agents.mapValues { (path, running) ->
            val sessions = sessionsByPath[path] ?: return@mapValues running
            val refs = sessions
                .mapNotNull { session -> session.name?.let { ClaudeSessionRef(it, session.pid) } }
                .sortedWith(compareBy(ClaudeSessionRef::name, ClaudeSessionRef::pid))
            val state = sessions.mapNotNull { it.state }.maxByOrNull(::urgency)
            running.map { agent -> if (agent.name == CLAUDE) withRegistry(agent, refs, state) else agent }
        }
    }

    private fun withRegistry(agent: RunningAgent, refs: List<ClaudeSessionRef>, state: AgentState?): RunningAgent {
        val named = if (refs.isEmpty()) agent else agent.copy(sessions = refs)
        if (named.state != null || state == null) return named
        return named.copy(state = state, stateSource = AgentStateSource.CLAUDE_CODE)
    }

    private fun liveSessions(store: AgentSessionStore, path: String, claudePids: Set<Long>, hasClaudeProcess: Boolean): List<AgentState> =
        store.list(path).mapNotNull { record ->
            val live = record.pid?.let { it in claudePids } ?: (hasClaudeProcess && now() - record.updatedAt < UNKNOWN_PID_MAX_AGE_MILLIS)
            if (!live) {
                runCatching { store.remove(path, record.sessionId) }
                return@mapNotNull null
            }
            if (record.state in setOf(AgentState.BLOCKED, AgentState.DONE) && transcriptWrittenAfter(record)) AgentState.WORKING else record.state
        }

    private fun transcriptWrittenAfter(record: AgentSessionRecord): Boolean {
        val modifiedAt = record.transcriptPath?.let(transcriptModifiedAt) ?: return false
        return modifiedAt > record.updatedAt + TRANSCRIPT_GRACE_MILLIS
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
        exact: Set<String>,
    ): Map<String, List<RunningAgent>> {
        if (herdrAgents.isEmpty()) return processAgents
        val herdrByPath = herdrAgents
            .mapNotNull { agent -> attributedWorktree(agent.directory, worktrees, exact)?.let { it.path to agent } }
            .groupBy({ it.first }, { it.second })
        return (processAgents.keys + herdrByPath.keys).associateWith { path ->
            val herdrByName = herdrByPath[path].orEmpty().groupBy { it.name }.mapValues { (_, agents) -> mostUrgent(agents) }
            val processNames = processAgents[path].orEmpty().map { it.name }.toSet()
            processAgents[path].orEmpty().map { agent ->
                val herdrAgent = herdrByName[agent.name]
                if (herdrAgent != null) runningAgent(herdrAgent, agent) else agent
            } +
                herdrByName.filterKeys { it !in processNames }.values.map(::runningAgent)
        }
    }

    private fun mergeOrca(
        agents: Map<String, List<RunningAgent>>,
        orcaWorktrees: List<OrcaWorktree>,
        worktrees: List<Worktree>,
    ): Map<String, List<RunningAgent>> {
        if (orcaWorktrees.none { it.agents.isNotEmpty() }) return agents
        val pathsByCanonical = worktrees.associate { canonical(it.path) to it.path }
        val orcaByPath = orcaWorktrees
            .mapNotNull { row -> pathsByCanonical[canonical(row.path)]?.let { it to row.agents } }
            .filter { (_, rowAgents) -> rowAgents.isNotEmpty() }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, lists) -> lists.flatten() }
        return (agents.keys + orcaByPath.keys).associateWith { path ->
            val orcaByName = orcaByPath[path].orEmpty().groupBy { it.agent }.mapValues { (_, same) -> same.maxBy { urgency(it.state) } }
            val known = agents[path].orEmpty()
            val knownNames = known.map { it.name }.toSet()
            known.map { agent ->
                val orcaAgent = orcaByName[agent.name]
                if (orcaAgent != null) withOrcaState(agent, orcaAgent) else agent
            } +
                orcaByName.filterKeys { it !in knownNames }.values.map {
                    RunningAgent(it.agent, it.state, stateSource = it.state?.let { AgentStateSource.ORCA }, activity = it.activity)
                }
        }
    }

    private fun withOrcaState(agent: RunningAgent, orcaAgent: OrcaAgent): RunningAgent {
        val detailed = agent.copy(activity = orcaAgent.activity)
        val state = orcaAgent.state ?: return detailed
        if (agent.stateSource == AgentStateSource.HERDR && urgency(agent.state) >= urgency(state)) return detailed
        return detailed.copy(state = state, stateSource = AgentStateSource.ORCA)
    }

    private fun attributedWorktree(cwd: String, worktrees: List<Worktree>, exact: Set<String>): Worktree? {
        val worktree = owningWorktree(cwd, worktrees) ?: return null
        val root = normalizePath(worktree.path)
        if (root in exact) return worktree.takeIf { normalizePath(cwd) == root }
        var directory = normalizePath(cwd)
        while (directory.length > root.length) {
            if (hasGitEntry(directory)) return null
            directory = directory.substringBeforeLast('/')
        }
        return worktree
    }

    private fun mostUrgent(agents: List<HerdrAgent>): HerdrAgent = agents.maxBy { urgency(it.state) }

    private fun urgency(state: AgentState?): Int = if (state == null) 0 else when (state) {
        AgentState.BLOCKED -> 4
        AgentState.DONE -> 3
        AgentState.WORKING -> 2
        AgentState.IDLE -> 1
    }

    private fun runningAgent(agent: HerdrAgent) = RunningAgent(agent.name, agent.state, agent.paneId, agent.state?.let { AgentStateSource.HERDR })

    private fun runningAgent(agent: HerdrAgent, process: RunningAgent): RunningAgent =
        if (agent.state == null) process.copy(herdrPaneId = agent.paneId) else runningAgent(agent)

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
        const val CLAUDE = "claude"
        const val LSOF_SOME_NOT_FOUND = 1
        const val TRANSCRIPT_GRACE_MILLIS = 1_000L
        const val UNKNOWN_PID_MAX_AGE_MILLIS = 12 * 60 * 60 * 1_000L
        private val KNOWN_AGENTS = setOf("claude", "codex", "gemini", "cursor-agent", "opencode", "aider", "copilot")
        const val HERDR_TIMEOUT_MILLIS = 5_000L
        const val HERDR_BACK_OFF_MILLIS = 30_000L
    }
}

private data class ProcessScan(
    val agents: Map<String, List<RunningAgent>> = emptyMap(),
    val claudePids: Set<Long> = emptySet(),
)

data class AgentSearch(val agentCommands: List<String>, val worktrees: List<Worktree>, val exact: Boolean = false) {
    companion object {
        fun multirepoFolder(folder: String, agentCommand: String) =
            AgentSearch(listOf(agentCommand), listOf(Worktree(path = folder, branch = null)), exact = true)
    }
}

internal fun fileModifiedAt(path: String): Long? = File(path).lastModified().takeIf { it > 0 }

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
