package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.executor.CommandExecutionException
import app.luxion.shogunai.domain.executor.CommandResult
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.OrcaClient
import app.luxion.shogunai.domain.model.AgentActivity
import app.luxion.shogunai.domain.model.AgentState
import app.luxion.shogunai.domain.model.AgentTool
import app.luxion.shogunai.domain.model.ORCA_MINIMUM_VERSION
import app.luxion.shogunai.domain.model.ORCA_START_SECONDS
import app.luxion.shogunai.domain.model.OrcaAgent
import app.luxion.shogunai.domain.model.OrcaPort
import app.luxion.shogunai.domain.model.OrcaRepo
import app.luxion.shogunai.domain.model.OrcaStatus
import app.luxion.shogunai.domain.model.OrcaTerminal
import app.luxion.shogunai.domain.model.OrcaWorkspace
import app.luxion.shogunai.domain.model.OrcaWorktree
import app.luxion.shogunai.domain.model.OrcaWorktreeUpdate
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import java.io.File
import kotlin.time.Duration.Companion.seconds

class CliOrcaClient(
    private val executor: ShellCommandExecutor,
    private val findExecutable: (String) -> String?,
    private val osName: String = System.getProperty("os.name", ""),
    private val isFile: (String) -> Boolean = { File(it).isFile },
) : OrcaClient {

    override fun isInstalled(): Boolean = location() != null

    override fun location(): String? = executableOrNull()

    override suspend fun status(): OrcaStatus {
        val result = run(listOf("status"))
        val runtime = result.field("runtime") as? JsonObject
        val running = (runtime?.get("reachable") as? JsonPrimitive)?.booleanOrNull ?: false
        val version = runtime?.string("appVersion")
        if (running && version != null && isOlder(version, ORCA_MINIMUM_VERSION)) {
            throw WorktreeError.OrcaCommandFailed(
                command(listOf("status")),
                "Orca $version is too old; ShogunAi needs Orca $ORCA_MINIMUM_VERSION or newer",
            )
        }
        return OrcaStatus(running, version)
    }

    override suspend fun start() {
        val executable = executableOrNull() ?: throw WorktreeError.OrcaNotInstalled
        val result = try {
            executor.executeWithin(listOf(executable, "open", "--json"), ORCA_START_SECONDS.seconds)
        } catch (error: CommandExecutionException.TimedOut) {
            throw WorktreeError.OrcaNotStarted
        } catch (error: CommandExecutionException) {
            throw WorktreeError.OrcaNotInstalled
        }
        if (!result.isSuccess) throw WorktreeError.OrcaNotStarted
    }

    override suspend fun bringToFront() = start()

    override suspend fun repos(): List<OrcaRepo> {
        val arguments = listOf("repo", "list")
        val repos = run(arguments).field("repos") as? JsonArray ?: throw unexpectedResponse(arguments)
        return repos.filterIsInstance<JsonObject>().mapNotNull(::repo)
    }

    override suspend fun addRepo(path: String): OrcaRepo {
        val arguments = listOf("repo", "add", "--path", path)
        return (run(arguments).field("repo") as? JsonObject)?.let(::repo) ?: throw unexpectedResponse(arguments)
    }

    override suspend fun showExternalWorktrees(repoId: String) {
        run(listOf("repo", "set", "--repo", "id:$repoId", "--external-worktree-visibility", "show"))
    }

    override suspend fun worktrees(repoId: String): List<OrcaWorktree> {
        val arguments = listOf("worktree", "list", "--repo", "id:$repoId", "--limit", ROW_LIMIT)
        val worktrees = run(arguments).field("worktrees") as? JsonArray ?: throw unexpectedResponse(arguments)
        return worktrees.filterIsInstance<JsonObject>().mapNotNull(::worktree)
    }

    override suspend fun agentsByWorktree(): List<OrcaWorktree> {
        val arguments = listOf("worktree", "ps", "--limit", ROW_LIMIT)
        val worktrees = run(arguments).field("worktrees") as? JsonArray ?: throw unexpectedResponse(arguments)
        return worktrees.filterIsInstance<JsonObject>().mapNotNull { row ->
            worktree(row)?.copy(agents = (row["agents"] as? JsonArray).orEmpty().filterIsInstance<JsonObject>().mapNotNull(::agent))
        }
    }

    override suspend fun updateWorktree(path: String, update: OrcaWorktreeUpdate) {
        if (update.isEmpty) return
        val arguments = buildList {
            addAll(listOf("worktree", "set", "--worktree", OrcaWorkspace.Worktree(path).selector))
            update.comment?.let { addAll(listOf("--comment", it)) }
            update.workspaceStatus?.let { addAll(listOf("--workspace-status", it)) }
            update.pullRequest?.let { addAll(listOf("--pr", it.toString())) }
            update.issue?.let { addAll(listOf("--issue", it.toString())) }
        }
        run(arguments)
    }

    override suspend fun ports(): List<OrcaPort> {
        val arguments = listOf("ports", "--all")
        val ports = run(arguments).field("ports") as? JsonArray ?: throw unexpectedResponse(arguments)
        return ports.filterIsInstance<JsonObject>().mapNotNull(::port)
    }

    override suspend fun terminals(workspace: OrcaWorkspace): List<OrcaTerminal> {
        val arguments = listOf("terminal", "list", "--worktree", workspace.selector)
        val terminals = run(arguments).field("terminals") as? JsonArray ?: throw unexpectedResponse(arguments)
        return terminals.filterIsInstance<JsonObject>().mapNotNull { terminal(it, workspace) }
    }

    override suspend fun terminalAgent(handle: String): String? {
        val arguments = listOf("terminal", "show", "--terminal", handle)
        val terminal = run(arguments).field("terminal") as? JsonObject ?: throw unexpectedResponse(arguments)
        return terminal.string("agentIdentity")
    }

    override suspend fun createTerminal(workspace: OrcaWorkspace, title: String, command: String, focus: Boolean): OrcaTerminal {
        val arguments = listOf("terminal", "create", "--worktree", workspace.selector, "--title", title, "--command", command) +
            listOfNotNull("--focus".takeIf { focus })
        val terminal = run(arguments).field("terminal") as? JsonObject
        return terminal?.let { terminal(it, workspace) } ?: throw unexpectedResponse(arguments)
    }

    override suspend fun switchTo(handle: String) {
        run(listOf("terminal", "switch", "--terminal", handle))
    }

    override suspend fun closeTerminals(workspace: OrcaWorkspace) {
        when (workspace) {
            is OrcaWorkspace.Worktree -> run(listOf("terminal", "close", "--worktree", workspace.selector, "--all"))
            is OrcaWorkspace.Folder -> terminals(workspace).forEach { run(listOf("terminal", "close", "--terminal", it.handle)) }
        }
    }

    override suspend fun attachEmulator(workspace: OrcaWorkspace, device: String) {
        run(listOf("emulator", "attach", device, "--worktree", workspace.selector, "--focus"))
    }

    private fun repo(repo: JsonObject): OrcaRepo? {
        val id = repo.string("id") ?: return null
        val path = repo.string("path") ?: return null
        return OrcaRepo(id, path, repo.string("externalWorktreeVisibility"))
    }

    private fun worktree(worktree: JsonObject): OrcaWorktree? {
        val path = worktree.string("path") ?: return null
        val provenance = worktree["creatorProvenance"]
        return OrcaWorktree(
            path,
            createdByOrca = provenance != null && provenance !is JsonNull,
            folderId = folderId(worktree),
            comment = worktree.string("comment"),
            workspaceStatus = worktree.string("workspaceStatus"),
            linkedPullRequest = linkedNumber(worktree["linkedPR"]),
            linkedIssue = linkedNumber(worktree["linkedIssue"]),
        )
    }

    private fun linkedNumber(link: JsonElement?): Int? = when (link) {
        is JsonPrimitive -> link.intOrNull
        is JsonObject -> (link["number"] as? JsonPrimitive)?.intOrNull
        else -> null
    }

    private fun folderId(worktree: JsonObject): String? {
        if (worktree.string("workspaceKind") != FOLDER_WORKSPACE) return null
        return worktree.string("worktreeId")?.takeIf { it.startsWith(FOLDER_PREFIX) }?.let { it.removePrefix(FOLDER_PREFIX).ifEmpty { null } }
    }

    private fun agent(agent: JsonObject): OrcaAgent? {
        val name = agent.string("agentType") ?: return null
        val state = agentState(agent.string("state"))
        return OrcaAgent(name, state, activity(agent, state))
    }

    private fun activity(agent: JsonObject, state: AgentState?): AgentActivity? {
        val waitingOn = agent.text("toolName")?.takeIf { state == AgentState.BLOCKED }?.let { AgentTool(it, agent.text("toolInput")) }
        val activity = AgentActivity(waitingOn, agent.text("prompt"), agent.text("lastAssistantMessage"))
        return activity.takeUnless { it == AgentActivity() }
    }

    private fun port(port: JsonObject): OrcaPort? {
        if (port.string("kind") != WORKSPACE_PORT) return null
        val number = (port["port"] as? JsonPrimitive)?.intOrNull ?: return null
        val path = (port["owner"] as? JsonObject)?.text("path") ?: return null
        return OrcaPort(number, path, port.text("processName"), port.text("advertisedUrl"))
    }

    private fun terminal(terminal: JsonObject, workspace: OrcaWorkspace): OrcaTerminal? {
        val handle = terminal.string("handle") ?: return null
        val fallback = (workspace as? OrcaWorkspace.Worktree)?.path.orEmpty()
        return OrcaTerminal(handle, terminal.string("worktreePath")?.takeIf { it.isNotEmpty() } ?: fallback, terminal.string("title"))
    }

    private fun agentState(state: String?): AgentState? = when (state) {
        "working" -> AgentState.WORKING
        "waiting" -> AgentState.BLOCKED
        "done" -> AgentState.DONE
        else -> null
    }

    fun executable(): String = executableOrNull() ?: binaryName()

    private fun executableOrNull(): String? =
        findExecutable(binaryName()) ?: MAC_APP_BINARY.takeIf { isMac() && isFile(it) }

    private fun binaryName(): String = when {
        isWindows() || isMac() -> "orca"
        else -> "orca-ide"
    }

    private fun isWindows() = osName.startsWith("Windows", ignoreCase = true)

    private fun isMac() = osName.startsWith("Mac", ignoreCase = true)

    private fun command(arguments: List<String>): List<String> = listOf(executable()) + arguments + "--json"

    private fun unexpectedResponse(arguments: List<String>) =
        WorktreeError.OrcaCommandFailed(command(arguments), "unexpected response from Orca")

    private suspend fun run(arguments: List<String>): JsonObject? {
        val executable = executableOrNull() ?: throw WorktreeError.OrcaNotInstalled
        val command = listOf(executable) + arguments + "--json"
        val result = try {
            executor.execute(command)
        } catch (error: CommandExecutionException) {
            throw WorktreeError.OrcaNotInstalled
        }
        return response(command, result)
    }

    private fun response(command: List<String>, result: CommandResult): JsonObject? {
        val response = parse(result.stdout)
        val ok = (response?.get("ok") as? JsonPrimitive)?.booleanOrNull
        if (!result.isSuccess || ok == false || response?.get("error").let { it != null && it !is JsonNull }) {
            val message = errorMessage(response) ?: errorMessage(parse(result.stderr))
            throw WorktreeError.OrcaCommandFailed(command, message ?: result.stderr.trim().ifBlank { "exit code ${result.exitCode}" })
        }
        return response?.get("result") as? JsonObject
    }

    private fun parse(output: String): JsonObject? =
        output.takeIf { it.isNotBlank() }?.let { runCatching { Json.parseToJsonElement(it) as? JsonObject }.getOrNull() }

    private fun errorMessage(response: JsonObject?): String? =
        (response?.get("error") as? JsonObject)?.string("message")

    private fun JsonObject?.field(name: String): JsonElement? = this?.get(name)

    private fun JsonObject.string(name: String): String? =
        (this[name] as? JsonPrimitive)?.stringOrNull()

    private fun JsonObject.text(name: String): String? = string(name)?.takeIf { it.isNotBlank() }

    companion object {
        private const val WORKSPACE_PORT = "workspace"
        private const val ROW_LIMIT = "1000"
        private const val FOLDER_WORKSPACE = "folder-workspace"
        private const val FOLDER_PREFIX = "folder:"
        private const val MAC_APP_BINARY = "/Applications/Orca.app/Contents/Resources/bin/orca"

        fun isOlder(version: String, minimum: String): Boolean {
            val parts = version.split('.', '-').map { it.toIntOrNull() ?: 0 }
            val required = minimum.split('.').map { it.toInt() }
            for (index in required.indices) {
                val part = parts.getOrNull(index) ?: 0
                if (part != required[index]) return part < required[index]
            }
            return false
        }
    }
}
