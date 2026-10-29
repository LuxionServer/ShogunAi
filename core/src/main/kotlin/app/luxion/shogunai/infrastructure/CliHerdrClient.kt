package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.executor.CommandExecutionException
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.HerdrClient
import app.luxion.shogunai.domain.model.HerdrAgent
import app.luxion.shogunai.domain.model.HerdrPane
import app.luxion.shogunai.domain.model.HerdrWorkspace
import app.luxion.shogunai.domain.model.AgentState
import app.luxion.shogunai.domain.model.HerdrWorktreeOpening
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
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
    private val findOnPath: (String) -> String? = { name -> ExecutableLocator().find(name) },
) : HerdrClient {

    fun isInstalled(): Boolean = executable().let { isFile(it) || findOnPath(it) != null }

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

    override suspend fun openWorktreeWorkspaces(repository: String): Map<String, String> {
        val arguments = listOf("worktree", "list", "--cwd", repository)
        val worktrees = run(arguments).field("worktrees") as? JsonArray ?: throw unexpectedResponse(arguments)
        return worktrees.filterIsInstance<JsonObject>().mapNotNull { worktree ->
            val path = worktree.string("path")
            val workspaceId = worktree.string("open_workspace_id")
            if (path != null && workspaceId != null) path to workspaceId else null
        }.toMap()
    }

    override suspend fun closeWorkspace(workspaceId: String) {
        run(listOf("workspace", "close", workspaceId))
    }

    override suspend fun listAgents(): List<HerdrAgent> {
        val arguments = listOf("agent", "list")
        val agents = run(arguments).field("agents") as? JsonArray ?: throw unexpectedResponse(arguments)
        return agents.filterIsInstance<JsonObject>().mapNotNull { agent ->
            val name = agent.string("agent")
            val directory = agent.string("foreground_cwd") ?: agent.string("cwd")
            val paneId = agent.string("pane_id")
            if (name != null && directory != null && paneId != null) {
                HerdrAgent(name, agentState(agent.string("agent_status")), directory, paneId)
            } else {
                null
            }
        }
    }

    override suspend fun focusAgent(paneId: String) {
        run(listOf("agent", "focus", paneId))
    }

    override suspend fun createWorkspace(cwd: String, label: String): HerdrWorktreeOpening {
        val arguments = listOf("workspace", "create", "--cwd", cwd, "--label", label, "--focus")
        val result = run(arguments)
        val workspaceId = (result.field("workspace") as? JsonObject)?.string("workspace_id")
        val rootPaneId = (result.field("root_pane") as? JsonObject)?.string("pane_id")
        if (workspaceId == null || rootPaneId == null) throw unexpectedResponse(arguments)
        return HerdrWorktreeOpening(workspaceId = workspaceId, rootPaneId = rootPaneId, alreadyOpen = false)
    }

    override suspend fun listWorkspaces(): List<HerdrWorkspace> {
        val arguments = listOf("workspace", "list")
        val workspaces = run(arguments).field("workspaces") as? JsonArray ?: throw unexpectedResponse(arguments)
        return workspaces.filterIsInstance<JsonObject>().mapNotNull { workspace ->
            val id = workspace.string("workspace_id") ?: return@mapNotNull null
            HerdrWorkspace(id, workspace.string("label").orEmpty(), (workspace["worktree"] as? JsonObject)?.string("checkout_path"))
        }
    }

    override suspend fun listPanes(workspaceId: String?): List<HerdrPane> {
        val arguments = listOf("pane", "list") + listOfNotNull(workspaceId).flatMap { listOf("--workspace", it) }
        val panes = run(arguments).field("panes") as? JsonArray ?: throw unexpectedResponse(arguments)
        return panes.filterIsInstance<JsonObject>().mapNotNull { pane ->
            val paneId = pane.string("pane_id") ?: return@mapNotNull null
            val paneWorkspaceId = pane.string("workspace_id") ?: return@mapNotNull null
            HerdrPane(
                paneId = paneId,
                workspaceId = paneWorkspaceId,
                directory = pane.string("foreground_cwd") ?: pane.string("cwd"),
                agent = pane.string("agent"),
                agentStatus = pane.string("agent_status") ?: HerdrPane.UNKNOWN_AGENT_STATUS,
            )
        }
    }

    override suspend fun focusWorkspace(workspaceId: String) {
        run(listOf("workspace", "focus", workspaceId))
    }

    private fun agentState(status: String?): AgentState? = when (status) {
        "working" -> AgentState.WORKING
        "blocked" -> AgentState.BLOCKED
        "done" -> AgentState.DONE
        "idle" -> AgentState.IDLE
        else -> null
    }

    fun executable(): String {
        val name = binaryName()
        val candidates = USER_BIN_FOLDERS.map { "${userHome.trimEnd('/', '\\')}/$it/$name" } +
            SYSTEM_BIN_FOLDERS.map { "$it/$name" }
        return candidates.firstOrNull(isFile) ?: name
    }

    private fun binaryName(): String = if (osName.startsWith("Windows", ignoreCase = true)) "herdr.exe" else "herdr"

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
        private val SYSTEM_BIN_FOLDERS = listOf("/opt/homebrew/bin", "/usr/local/bin")
    }
}
