package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.executor.CommandTimeouts
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.AgentSessionStore
import app.luxion.shogunai.domain.io.ClaudeSessionRegistry
import app.luxion.shogunai.domain.io.HerdrClient
import app.luxion.shogunai.domain.model.AgentSessionRecord
import app.luxion.shogunai.domain.model.HerdrAgent
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
    private val transcriptModifiedAt: (String) -> Long? = { path -> File(path).lastModified().takeIf { it > 0 } },
    private val now: () -> Long = System::currentTimeMillis,
    private val hasGitEntry: (String) -> Boolean = { directory -> File(directory, ".git").exists() },
    private val claudeSessions: ClaudeSessionRegistry? = null,
) {
    @Volatile
    private var herdrRetryAt = 0L

    suspend operator fun invoke(config: ProjectConfig, worktrees: List<Worktree>): Result<Map<String, List<RunningAgent>>> =
        invoke(listOf(AgentSearch(config.agents.map { it.agentCommand }, worktrees)))

    suspend operator fun invoke(searches: List<AgentSearch>): Result<Map<String, List<RunningAgent>>> = runCatching {
        val worktrees = searches.flatMap { it.worktrees }
        if (worktrees.isEmpty()) return@runCatching emptyMap()
        val exact = searches.filter { it.exact }.flatMap { it.worktrees }.map { normalizePath(it.path) }.toSet()
        val (processes, herdrAgents) = coroutineScope {
            val herdrAgents = async { readHerdrAgents() }
            val processes = runCatching { scanProcesses(searches, worktrees, exact) }
            processes to herdrAgents.await()
        }
        val scan = processes.getOrDefault(ProcessScan())
        merge(withSessionStates(scan, worktrees), herdrAgents, worktrees, exact)
            .let { merged -> withClaudeSessions(merged, scan.claudePids, worktrees, exact) }
            .also { merged -> if (merged.isEmpty()) processes.getOrThrow() }
    }

    private suspend fun scanProcesses(searches: List<AgentSearch>, worktrees: List<Worktree>, exact: Set<String>): ProcessScan {
        val processes = run("lsof", "-d", "cwd", "-F", "pcn").let(::parseLsofWorkingDirectories)
        val worktreeByPid = processes.mapNotNull { (pid, cwd) ->
            attributedWorktree(cwd, worktrees, exact)?.let { pid to it.path }
        }.toMap()
        if (worktreeByPid.isEmpty()) return ProcessScan()

        val executables = run("ps", "-o", "pid=,comm=", "-p", worktreeByPid.keys.joinToString(","))
            .let(::parsePsExecutables)
        val agentNamesByPath = searches.flatMap { search ->
            val names = KNOWN_AGENTS + search.agentCommands.mapNotNull(::agentName)
            search.worktrees.map { it.path to names }
        }.toMap()

        val agents = executables.mapNotNull { (pid, executable) ->
            val name = executable.substringAfterLast('/')
            val path = worktreeByPid[pid]
            if (path != null && name in agentNamesByPath[path].orEmpty()) path to RunningAgent(name) else null
        }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, agents) -> agents.distinct() }
        val claudePids = executables
            .filter { (_, executable) -> executable.substringAfterLast('/') == CLAUDE }
            .mapNotNull { (pid, _) -> pid.toLongOrNull() }
            .toSet()
        return ProcessScan(agents, claudePids)
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
                if (agent.name == CLAUDE && agent.state == null) agent.copy(state = state, stateSource = AgentStateSource.CLAUDE_CODE) else agent
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
        val refsByPath = runCatching { registry.list() }.getOrDefault(emptyList())
            .filter { it.pid in claudePids }
            .mapNotNull { session ->
                val name = session.name ?: return@mapNotNull null
                attributedWorktree(session.cwd, worktrees, exact)?.let { it.path to ClaudeSessionRef(name, session.pid) }
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, refs) -> refs.sortedWith(compareBy(ClaudeSessionRef::name, ClaudeSessionRef::pid)) }
        if (refsByPath.isEmpty()) return agents
        return agents.mapValues { (path, running) ->
            val refs = refsByPath[path] ?: return@mapValues running
            running.map { agent -> if (agent.name == CLAUDE) agent.copy(sessions = refs) else agent }
        }
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
            processAgents[path].orEmpty().map { agent -> herdrByName[agent.name]?.let { runningAgent(it, agent) } ?: agent } +
                herdrByName.filterKeys { it !in processNames }.values.map(::runningAgent)
        }
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

    private fun urgency(state: AgentState?): Int = when (state) {
        AgentState.BLOCKED -> 4
        AgentState.DONE -> 3
        AgentState.WORKING -> 2
        AgentState.IDLE -> 1
        null -> 0
    }

    private fun runningAgent(agent: HerdrAgent) = RunningAgent(agent.name, agent.state, agent.paneId, agent.state?.let { AgentStateSource.HERDR })

    private fun runningAgent(agent: HerdrAgent, process: RunningAgent?): RunningAgent =
        if (agent.state == null && process != null) process.copy(herdrPaneId = agent.paneId) else runningAgent(agent)

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
        const val TRANSCRIPT_GRACE_MILLIS = 1_000L
        const val UNKNOWN_PID_MAX_AGE_MILLIS = 12 * 60 * 60 * 1_000L
        val KNOWN_AGENTS = setOf("claude", "codex", "gemini", "cursor-agent", "opencode", "aider", "copilot")
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
