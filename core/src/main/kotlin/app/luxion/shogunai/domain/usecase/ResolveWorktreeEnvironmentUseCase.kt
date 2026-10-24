package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.ComposeProjectRegistry
import app.luxion.shogunai.domain.io.ComposeRegistryEntry
import app.luxion.shogunai.domain.io.EnvironmentStateStore
import app.luxion.shogunai.domain.io.PortProbe
import app.luxion.shogunai.domain.io.RepositoryProvisioningSource
import app.luxion.shogunai.domain.io.RepositoryTrust
import app.luxion.shogunai.domain.model.EnvironmentDeclaration
import app.luxion.shogunai.domain.model.ShellQuoting
import app.luxion.shogunai.domain.model.WorktreeEnvironment
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.model.approvalSubject
import app.luxion.shogunai.domain.model.render

class ResolveWorktreeEnvironmentUseCase(
    private val executor: ShellCommandExecutor,
    private val provisioningSource: RepositoryProvisioningSource,
    private val stateStore: EnvironmentStateStore,
    private val portProbe: PortProbe,
    private val contributions: ProviderContributions = ProviderContributions.DEFAULT,
    private val cache: WorktreeEnvironmentCache? = null,
    private val trust: RepositoryTrust = RepositoryTrust.NONE,
    private val composeRegistry: ComposeProjectRegistry? = null,
) {
    suspend operator fun invoke(
        repositoryPath: String,
        worktreePath: String,
        reserve: Boolean = true,
    ): Result<WorktreeEnvironment> = runCatching {
        val provisioning = provisioningSource.read(repositoryPath)
        val approved = trust.isApproved(repositoryPath, provisioning.approvalSubject)
        val awaitingApproval = if (approved) emptyList() else provisioning.environment.variables.map { it.name }
        val declaration = if (approved) provisioning.environment else EnvironmentDeclaration()
        val gated = { environment: WorktreeEnvironment ->
            environment.copy(awaitingApproval = awaitingApproval, declarationApproved = approved)
        }
        val declaredNames = declaration.variables.map { it.name }.toSet()
        val contributed = contributions.forRepository(repositoryPath, provisioning.providers)
            .filterNot { it.names.isNotEmpty() && it.names.all { name -> name in declaredNames } }
        val requests = declaredPortRequests(declaration) + contributed.flatMap { it.ports }
        val resourceRequests = contributed.flatMap { it.resources }
        if (declaration.isEmpty && contributed.isEmpty()) return@runCatching gated(WorktreeEnvironment())
        val directories = gitDirectories(worktreePath)
        val portKeys = requests.map { it.key }.toSet()
        val resourceKeys = resourceRequests.map { it.key }.toSet()
        val requiredPortKeys = requests.filterNot { it.onDemand }.map { it.key }
        val requiredResourceKeys = resourceRequests.filterNot { it.onDemand }.map { it.key }
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
        if (!requiredPortKeys.all { it in ports } || !requiredResourceKeys.all { it in resources }) {
            return@runCatching gated(WorktreeEnvironment(ports = ports, resources = resources))
        }
        val declared = declaration.variables.map { it.name to it.render(folderName(worktreePath), ports) }
        val reservations = EnvironmentReservations(ports, resources)
        val context = ResourceContext(folderName(repositoryPath), folderName(worktreePath))
        val fromProviders = contributed.filter { it.isAcquiredIn(reservations) }
            .flatMap { it.variables(reservations) + it.contextual(context) }
            .filterNot { it.first in declaredNames }
        val environment = gated(WorktreeEnvironment(variables = fromProviders + declared, ports = ports, resources = resources))
        if (reserve && provisioning.providers.dockerCompose != null) recordComposeProject(repositoryPath, worktreePath, environment)
        environment
    }

    private fun recordComposeProject(repositoryPath: String, worktreePath: String, environment: WorktreeEnvironment) {
        val registry = composeRegistry ?: return
        val project = environment.variables.lastOrNull { it.first == COMPOSE_PROJECT_NAME }?.second ?: return
        runCatching { registry.record(repositoryPath, ComposeRegistryEntry(project, normalizePath(worktreePath))) }
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
            if (request.onDemand) {
                if (stored != null && !request.exists(stored)) resources.remove(request.key)
                return@forEach
            }
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
        requests.filterNot { it.key in reserved || it.onDemand }.forEach { request ->
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

    suspend fun directoriesOf(worktreePath: String): WorktreeGitDirectories =
        gitDirectories(worktreePath).let { WorktreeGitDirectories(it.admin, it.common) }

    private suspend fun gitDirectories(worktreePath: String): GitDirectories {
        cache?.directoriesOf(worktreePath)?.let { return GitDirectories(it.admin, it.common) }
        val directories = readGitDirectories(worktreePath)
        cache?.remember(worktreePath, CachedGitDirectories(directories.admin, directories.common))
        return directories
    }

    private suspend fun readGitDirectories(worktreePath: String): GitDirectories {
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

data class WorktreeGitDirectories(val admin: String, val common: String)

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
