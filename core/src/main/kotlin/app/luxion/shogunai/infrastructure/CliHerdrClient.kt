package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.executor.CommandExecutionException
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.HerdrClient
import app.luxion.shogunai.domain.model.HerdrWorktreeOpening
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import java.io.File

class CliHerdrClient(
    private val executor: ShellCommandExecutor,
    private val userHome: String = System.getProperty("user.home"),
    private val osName: String = System.getProperty("os.name").orEmpty(),
    private val isFile: (String) -> Boolean = { File(it).isFile },
) : HerdrClient {

    override suspend fun openWorktree(repository: String, path: String, label: String): HerdrWorktreeOpening {
        val arguments = listOf("worktree", "open", "--cwd", repository, "--path", path, "--label", label, "--focus")
        val result = run(arguments)
        val workspaceId = (result.field("workspace") as? JsonObject)?.string("workspace_id")
        val rootPaneId = (result.field("root_pane") as? JsonObject)?.string("pane_id")
        if (workspaceId == null || rootPaneId == null) throw unexpectedResponse(arguments)
        val alreadyOpen = (result.field("already_open") as? JsonPrimitive)?.booleanOrNull ?: false
        return HerdrWorktreeOpening(workspaceId = workspaceId, rootPaneId = rootPaneId, alreadyOpen = alreadyOpen)
    }

    override suspend fun splitPane(paneId: String, cwd: String, environment: List<Pair<String, String>>): String {
        val arguments = listOf("pane", "split", paneId, "--direction", "right", "--cwd", cwd) +
            environment.flatMap { (name, value) -> listOf("--env", "$name=$value") } +
            "--focus"
        return (run(arguments).field("pane") as? JsonObject)?.string("pane_id") ?: throw unexpectedResponse(arguments)
    }

    override suspend fun closePane(paneId: String) {
        run(listOf("pane", "close", paneId))
    }

    override suspend fun runInPane(paneId: String, command: String) {
        run(listOf("pane", "run", paneId, command))
    }

    fun executable(): String {
        val name = if (osName.startsWith("Windows", ignoreCase = true)) "herdr.exe" else "herdr"
        return USER_BIN_FOLDERS.map { File(File(userHome, it), name).path }.firstOrNull(isFile) ?: name
    }

    private fun command(arguments: List<String>): List<String> = listOf(executable()) + arguments

    private fun unexpectedResponse(arguments: List<String>) =
        WorktreeError.HerdrCommandFailed(command(arguments), "unexpected response from Herdr")

    private suspend fun run(arguments: List<String>): JsonObject? {
        val command = command(arguments)
        val result = try {
            executor.execute(command)
        } catch (error: CommandExecutionException) {
            throw WorktreeError.HerdrNotInstalled
        }
        val response = parse(result.stdout)
        if (!result.isSuccess || response?.get("error") != null) {
            val message = errorMessage(response) ?: errorMessage(parse(result.stderr))
            throw WorktreeError.HerdrCommandFailed(command, message ?: result.stderr.trim().ifBlank { "exit code ${result.exitCode}" })
        }
        return response?.get("result") as? JsonObject
    }

    private fun parse(stdout: String): JsonObject? =
        stdout.takeIf { it.isNotBlank() }?.let { runCatching { Json.parseToJsonElement(it) as? JsonObject }.getOrNull() }

    private fun errorMessage(response: JsonObject?): String? =
        (response?.get("error") as? JsonObject)?.string("message")

    private fun JsonObject?.field(name: String): JsonElement? = this?.get(name)

    private fun JsonObject.string(name: String): String? =
        (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content

    companion object {
        private val USER_BIN_FOLDERS = listOf(".local/bin", ".cargo/bin")
    }
}
