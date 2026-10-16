package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.EnvironmentStateStore
import app.luxion.shogunai.domain.io.PortProbe
import app.luxion.shogunai.domain.io.RepositoryProvisioningSource
import app.luxion.shogunai.domain.model.EnvironmentDeclaration
import app.luxion.shogunai.domain.model.WorktreeEnvironment
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.model.render

class ResolveWorktreeEnvironmentUseCase(
    private val executor: ShellCommandExecutor,
    private val provisioningSource: RepositoryProvisioningSource,
    private val stateStore: EnvironmentStateStore,
    private val portProbe: PortProbe,
) {
    suspend operator fun invoke(
        repositoryPath: String,
        worktreePath: String,
        reserve: Boolean = true,
    ): Result<WorktreeEnvironment> = runCatching {
        val declaration = provisioningSource.read(repositoryPath).environment
        if (declaration.isEmpty) return@runCatching WorktreeEnvironment()
        val directories = gitDirectories(worktreePath)
        val ports = if (reserve) {
            stateStore.withLock(directories.common) { reservePorts(declaration, directories) }
        } else {
            stateStore.readPorts(directories.admin).filterKeys { it in declaration.portKeys }
        }
        val worktreeName = normalizePath(worktreePath).substringAfterLast('/')
        WorktreeEnvironment(
            variables = if (declaration.portKeys.all { it in ports }) {
                declaration.variables.map { it.name to it.render(worktreeName, ports) }
            } else {
                emptyList()
            },
            ports = ports,
        )
    }

    private fun reservePorts(declaration: EnvironmentDeclaration, directories: GitDirectories): Map<String, Int> {
        val stored = stateStore.readPorts(directories.admin)
        val kept = stored.filterKeys { it in declaration.portKeys }
        val taken = (stateStore.reservedPortsOfRepository(directories.common) - stored.values.toSet() + kept.values)
            .toMutableSet()
        val reserved = kept.toMutableMap()
        declaration.portKeys.filterNot { it in reserved }.forEach { key ->
            val port = (FIRST_PORT..LAST_PORT).firstOrNull { it !in taken && portProbe.isFree(it) }
                ?: throw WorktreeError.NoFreePort(FIRST_PORT, LAST_PORT)
            reserved[key] = port
            taken += port
        }
        if (reserved != stored) stateStore.writePorts(directories.admin, reserved)
        return reserved
    }

    private suspend fun gitDirectories(worktreePath: String): GitDirectories {
        val result = executor.execute(
            command = listOf("git", "rev-parse", "--path-format=absolute", "--git-dir", "--git-common-dir"),
            workingDirectory = worktreePath,
        )
        val lines = result.stdout.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (!result.isSuccess || lines.size != 2) throw WorktreeError.RepositoryNotFound(worktreePath)
        return GitDirectories(admin = normalizePath(lines[0]), common = normalizePath(lines[1]))
    }

    private data class GitDirectories(val admin: String, val common: String)

    companion object {
        const val FIRST_PORT = 20000
        const val LAST_PORT = 29999
    }
}

enum class EnvironmentFormat { SH, DOTENV, JSON }

fun formatEnvironment(environment: WorktreeEnvironment, format: EnvironmentFormat): String = when (format) {
    EnvironmentFormat.SH -> environment.variables.joinToString("") { (name, value) -> "export $name=${shellQuote(value)}\n" }
    EnvironmentFormat.DOTENV -> environment.variables.joinToString("") { (name, value) -> "$name=${dotenvValue(value)}\n" }
    EnvironmentFormat.JSON -> environment.variables.joinToString(",", prefix = "{", postfix = "}\n") { (name, value) ->
        "${jsonString(name)}:${jsonString(value)}"
    }
}

private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

private val PLAIN_DOTENV_VALUE = Regex("[A-Za-z0-9_./:@%+,=-]*")

private fun dotenvValue(value: String): String =
    if (PLAIN_DOTENV_VALUE.matches(value)) {
        value
    } else {
        "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("$", "\\$") + "\""
    }

private fun jsonString(value: String): String = buildString {
    append('"')
    value.forEach { char ->
        when {
            char == '"' -> append("\\\"")
            char == '\\' -> append("\\\\")
            char == '\n' -> append("\\n")
            char == '\r' -> append("\\r")
            char == '\t' -> append("\\t")
            char < ' ' -> append("\\u%04x".format(char.code))
            else -> append(char)
        }
    }
    append('"')
}
