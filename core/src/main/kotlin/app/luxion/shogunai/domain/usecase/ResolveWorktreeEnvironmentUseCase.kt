package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.EnvironmentStateStore
import app.luxion.shogunai.domain.io.PortProbe
import app.luxion.shogunai.domain.io.RepositoryProvisioningSource
import app.luxion.shogunai.domain.model.ShellQuoting
import app.luxion.shogunai.domain.model.WorktreeEnvironment
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.model.render

class ResolveWorktreeEnvironmentUseCase(
    private val executor: ShellCommandExecutor,
    private val provisioningSource: RepositoryProvisioningSource,
    private val stateStore: EnvironmentStateStore,
    private val portProbe: PortProbe,
    private val contributions: ProviderContributions = ProviderContributions.DEFAULT,
) {
    suspend operator fun invoke(
        repositoryPath: String,
        worktreePath: String,
        reserve: Boolean = true,
    ): Result<WorktreeEnvironment> = runCatching {
        val provisioning = provisioningSource.read(repositoryPath)
        val declaration = provisioning.environment
        val contributed = contributions.forRepository(repositoryPath, provisioning.providers)
        val requests = declaredPortRequests(declaration) + contributed.flatMap { it.ports }
        val resourceRequests = contributed.flatMap { it.resources }
        if (declaration.isEmpty && contributed.isEmpty()) return@runCatching WorktreeEnvironment()
        val directories = gitDirectories(worktreePath)
        val portKeys = requests.map { it.key }.toSet()
        val resourceKeys = resourceRequests.map { it.key }.toSet()
        val ports = if (reserve) {
            stateStore.withLock { reservePorts(requests, directories) }
        } else {
            stateStore.readPorts(directories.admin).filterKeys { it in portKeys }
        }
        val resources = if (reserve) {
            reserveResources(resourceRequests, directories, ResourceContext(folderName(repositoryPath), folderName(worktreePath)))
        } else {
            stateStore.readResources(directories.admin).filterKeys { it in resourceKeys }
        }
        if (!portKeys.all { it in ports } || !resourceKeys.all { it in resources }) {
            return@runCatching WorktreeEnvironment(ports = ports, resources = resources)
        }
        val declared = declaration.variables.map { it.name to it.render(folderName(worktreePath), ports) }
        val declaredNames = declared.map { it.first }.toSet()
        val reservations = EnvironmentReservations(ports, resources)
        val fromProviders = contributed.flatMap { it.variables(reservations) }.filterNot { it.first in declaredNames }
        WorktreeEnvironment(variables = fromProviders + declared, ports = ports, resources = resources)
    }

    suspend fun recordedOnMachine(repositoryPath: String, key: String): Result<Set<String>> = runCatching {
        stateStore.recordedResourcesOnMachine(gitDirectories(repositoryPath).common, key)
    }

    private suspend fun reserveResources(
        requests: List<ResourceRequest>,
        directories: GitDirectories,
        context: ResourceContext,
    ): Map<String, String> {
        val resources = stateStore.readResources(directories.admin).filterKeys { key -> requests.any { it.key == key } }
            .toMutableMap()
        requests.forEach { request ->
            val stored = resources[request.key]
            if (stored != null && request.exists(stored)) return@forEach
            val created = request.create(context)
            val winner = stateStore.withLock {
                val current = stateStore.readResources(directories.admin)
                val recorded = current[request.key]
                if (recorded != null && recorded != stored) {
                    recorded
                } else {
                    stateStore.writeResources(directories.admin, current + (request.key to created))
                    created
                }
            }
            if (winner != created) request.discard(created)
            resources[request.key] = winner
        }
        return resources
    }

    private fun folderName(path: String): String = normalizePath(path).substringAfterLast('/')

    private fun reservePorts(requests: List<PortRequest>, directories: GitDirectories): Map<String, Int> {
        val stored = stateStore.readPorts(directories.admin)
        val keys = requests.map { it.key }.toSet()
        val kept = stored.filterKeys { it in keys }
        val taken = (stateStore.reservedPortsOnMachine(directories.common) - stored.values.toSet() + kept.values)
            .toMutableSet()
        val reserved = kept.toMutableMap()
        requests.filterNot { it.key in reserved }.forEach { request ->
            val port = request.candidates.firstOrNull { candidate ->
                val needed = listOf(candidate) + request.alsoFree(candidate)
                needed.none { it in taken } && needed.all(portProbe::isFree)
            } ?: throw request.exhausted()
            reserved[request.key] = port
            taken += port
            taken += request.alsoFree(port)
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
        const val FIRST_PORT = FIRST_VARIABLE_PORT
        const val LAST_PORT = LAST_VARIABLE_PORT
    }
}

enum class EnvironmentFormat { SH, DOTENV, JSON }

fun formatEnvironment(environment: WorktreeEnvironment, format: EnvironmentFormat): String = when (format) {
    EnvironmentFormat.SH -> environment.variables.joinToString("") { (name, value) -> "export $name=${ShellQuoting.posix(value)}\n" }
    EnvironmentFormat.DOTENV -> environment.variables.joinToString("") { (name, value) -> "$name=${dotenvValue(value)}\n" }
    EnvironmentFormat.JSON -> environment.variables.joinToString(",", prefix = "{", postfix = "}\n") { (name, value) ->
        "${jsonString(name)}:${jsonString(value)}"
    }
}

private val PLAIN_DOTENV_VALUE = Regex("[A-Za-z0-9_./:@%+,=-]*")

private fun dotenvValue(value: String): String =
    if (PLAIN_DOTENV_VALUE.matches(value)) {
        value
    } else {
        "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("$", "\\$").replace("`", "\\`") + "\""
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
