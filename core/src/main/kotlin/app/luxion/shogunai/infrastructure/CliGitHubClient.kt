package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.executor.CommandExecutionException
import app.luxion.shogunai.domain.executor.CommandTimeouts
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.GitHubClient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

class CliGitHubClient(
    private val executor: ShellCommandExecutor,
    private val findExecutable: (String) -> String?,
) : GitHubClient {

    override suspend fun pullRequestFor(directory: String, branch: String): Int? {
        val output = run(directory, listOf("pr", "list", "--head", branch, "--state", "all", "--limit", "1", "--json", "number"))
        return ((output as? JsonArray)?.firstOrNull() as? JsonObject)?.number()
    }

    override suspend fun issueExists(directory: String, number: Int): Boolean {
        val output = run(directory, listOf("issue", "view", number.toString(), "--json", "number"))
        return (output as? JsonObject)?.number() == number
    }

    private suspend fun run(directory: String, arguments: List<String>): JsonElement? {
        val executable = findExecutable(GH) ?: return null
        val result = try {
            executor.executeWithin(listOf(executable) + arguments, CommandTimeouts.LISTING, directory)
        } catch (error: CommandExecutionException) {
            return null
        }
        if (!result.isSuccess) return null
        return runCatching { Json.parseToJsonElement(result.stdout) }.getOrNull()
    }

    private fun JsonObject.number(): Int? = (this["number"] as? JsonPrimitive)?.intOrNull

    private companion object {
        const val GH = "gh"
    }
}
