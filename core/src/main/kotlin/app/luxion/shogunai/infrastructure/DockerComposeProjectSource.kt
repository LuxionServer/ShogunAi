package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.executor.CommandExecutionException
import app.luxion.shogunai.domain.executor.CommandTimeouts
import app.luxion.shogunai.domain.executor.CommandResult
import app.luxion.shogunai.domain.executor.OutputLine
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.ComposeProjectSource
import app.luxion.shogunai.domain.model.ComposeAction
import app.luxion.shogunai.domain.model.ComposeProject
import app.luxion.shogunai.domain.model.ComposeResource
import app.luxion.shogunai.domain.model.ComposeResourceKind
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

class DockerComposeProjectSource(private val executor: ShellCommandExecutor) : ComposeProjectSource {

    override suspend fun list(): List<ComposeProject> {
        val result = try {
            executor.executeWithin(LIST_COMMAND, CommandTimeouts.LISTING)
        } catch (error: CommandExecutionException) {
            throw WorktreeError.DockerUnavailable(error.message)
        }
        if (!result.isSuccess) throw WorktreeError.DockerUnavailable(result.stderr.lineSequence().firstOrNull { it.isNotBlank() })
        return parseComposeProjects(result.stdout) ?: throw WorktreeError.DockerUnavailable("unexpected output of docker compose ls")
    }

    override suspend fun down(name: String, removeVolumes: Boolean): Result<Unit> = runCatching {
        val command = listOf("docker", "compose", "-p", name, "down") + if (removeVolumes) listOf("--volumes") else emptyList()
        val result = executor.executeWithin(command, CommandTimeouts.NETWORK)
        if (!result.isSuccess) {
            throw WorktreeError.DockerUnavailable(result.stderr.lineSequence().lastOrNull { it.isNotBlank() } ?: "exit code ${result.exitCode}")
        }
    }

    override suspend fun run(
        project: String,
        action: ComposeAction,
        workingDirectory: String,
        environment: Map<String, String>,
        onOutput: (OutputLine) -> Unit,
    ): Result<Unit> = runCatching {
        val result = try {
            executor.executeStreaming(composeCommand(action), workingDirectory, environment, onOutput)
        } catch (error: CommandExecutionException) {
            throw WorktreeError.DockerUnavailable(error.message)
        }
        if (!result.isSuccess) throw WorktreeError.ComposeCommandFailed(project, result.lastErrorLine())
    }

    override suspend fun listResources(): List<ComposeResource> =
        listVolumes() + listNetworks() + listImages()

    override suspend fun remove(resource: ComposeResource): Result<Unit> = runCatching {
        val result = executor.executeWithin(listOf("docker", resource.kind.id, "rm", resource.id), CommandTimeouts.NETWORK)
        if (!result.isSuccess) throw IllegalStateException(result.lastErrorLine())
    }

    private suspend fun listVolumes(): List<ComposeResource> =
        query(listOf("docker", "volume", "ls", "--filter", "label=$PROJECT_LABEL", "--format", "{{.Name}}|{{.Label \"$PROJECT_LABEL\"}}"))
            .let(::parseVolumes)

    private suspend fun listNetworks(): List<ComposeResource> =
        query(listOf("docker", "network", "ls", "--filter", "label=$PROJECT_LABEL", "--format", "{{.ID}}|{{.Name}}|{{.Label \"$PROJECT_LABEL\"}}"))
            .let(::parseNetworks)

    private suspend fun listImages(): List<ComposeResource> {
        val ids = query(listOf("docker", "image", "ls", "--filter", "label=$PROJECT_LABEL", "--quiet", "--no-trunc"))
            .lines().map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (ids.isEmpty()) return emptyList()
        val inspect = listOf("docker", "image", "inspect", "--format", "{{.Id}}|{{join .RepoTags \",\"}}|{{index .Config.Labels \"$PROJECT_LABEL\"}}")
        return parseImages(query(inspect + ids))
    }

    private suspend fun query(command: List<String>): String {
        val result = try {
            executor.executeWithin(command, CommandTimeouts.LISTING)
        } catch (error: CommandExecutionException) {
            throw WorktreeError.DockerUnavailable(error.message)
        }
        if (!result.isSuccess) throw WorktreeError.DockerUnavailable(result.stderr.lineSequence().firstOrNull { it.isNotBlank() })
        return result.stdout
    }

    private fun CommandResult.lastErrorLine(): String =
        stderr.lineSequence().lastOrNull { it.isNotBlank() }?.trim() ?: "exit code $exitCode"

    private companion object {
        const val PROJECT_LABEL = "com.docker.compose.project"
        val LIST_COMMAND = listOf("docker", "compose", "ls", "--all", "--format", "json")
    }
}

internal fun composeCommand(action: ComposeAction): List<String> = when (action) {
    is ComposeAction.Up -> listOf("docker", "compose", "up", "--detach") +
        (if (action.build) listOf("--build") else emptyList()) + action.extraArguments
    is ComposeAction.Build -> listOf("docker", "compose", "build") + action.extraArguments
}

internal fun parseVolumes(output: String): List<ComposeResource> = labelledLines(output, 2).map { (name, project) ->
    ComposeResource(ComposeResourceKind.VOLUME, id = name, name = name, project = project)
}

internal fun parseNetworks(output: String): List<ComposeResource> = labelledLines(output, 3).map { (id, name, project) ->
    ComposeResource(ComposeResourceKind.NETWORK, id = id, name = name, project = project)
}

internal fun parseImages(output: String): List<ComposeResource> = labelledLines(output, 3).map { (id, tags, project) ->
    val name = tags.split(',').map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: id.removePrefix("sha256:").take(12)
    ComposeResource(ComposeResourceKind.IMAGE, id = id, name = name, project = project)
}

private fun labelledLines(output: String, fields: Int): List<List<String>> =
    output.lines().map { it.trim() }.filter { it.isNotEmpty() }
        .map { it.split('|', limit = fields) }
        .filter { it.size == fields && it.last().isNotBlank() && it.first().isNotBlank() }

internal fun parseComposeProjects(output: String): List<ComposeProject>? {
    val text = output.trim().ifEmpty { "[]" }
    val array = try {
        Json.parseToJsonElement(text) as? JsonArray
    } catch (error: SerializationException) {
        null
    } ?: return null
    return array.mapNotNull { element ->
        val fields = element as? JsonObject ?: return@mapNotNull null
        val name = fields["Name"]?.jsonPrimitive?.content ?: return@mapNotNull null
        ComposeProject(
            name = name,
            status = fields["Status"]?.jsonPrimitive?.content.orEmpty(),
            configFiles = fields["ConfigFiles"]?.jsonPrimitive?.content.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() },
        )
    }
}
