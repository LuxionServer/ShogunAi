package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.executor.CommandExecutionException
import app.luxion.shogunai.domain.executor.CommandTimeouts
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.ComposeProjectSource
import app.luxion.shogunai.domain.model.ComposeProject
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

    private companion object {
        val LIST_COMMAND = listOf("docker", "compose", "ls", "--all", "--format", "json")
    }
}

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
