package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.RunningAgent
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeError

class DetectRunningAgentsUseCase(
    private val config: ProjectConfig,
    private val executor: ShellCommandExecutor,
) {
    suspend operator fun invoke(worktrees: List<Worktree>): Result<Map<String, List<RunningAgent>>> = runCatching {
        val processes = run("lsof", "-d", "cwd", "-F", "pcn").let(::parseLsofWorkingDirectories)
        val worktreeByPid = processes.mapNotNull { (pid, cwd) ->
            owningWorktree(cwd, worktrees)?.let { pid to it.path }
        }.toMap()
        if (worktreeByPid.isEmpty()) return@runCatching emptyMap()

        val executables = run("ps", "-o", "pid=,comm=", "-p", worktreeByPid.keys.joinToString(","))
            .let(::parsePsExecutables)
        val agentNames = KNOWN_AGENTS + listOfNotNull(projectAgentName())

        executables.mapNotNull { (pid, executable) ->
            val name = executable.substringAfterLast('/')
            val path = worktreeByPid[pid]
            if (path != null && name in agentNames) path to RunningAgent(name) else null
        }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, agents) -> agents.distinct() }
    }

    private fun projectAgentName(): String? =
        config.agentLaunchConfig.agentCommand.trim().substringBefore(' ').substringAfterLast('/').ifEmpty { null }

    private suspend fun run(vararg command: String): String {
        val result = executor.execute(command.toList())
        if (!result.isSuccess) {
            throw WorktreeError.ProcessListingFailed(result.command, result.exitCode)
        }
        return result.stdout
    }

    private companion object {
        val KNOWN_AGENTS = setOf("claude", "codex", "gemini", "cursor-agent", "opencode", "aider", "copilot")
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
