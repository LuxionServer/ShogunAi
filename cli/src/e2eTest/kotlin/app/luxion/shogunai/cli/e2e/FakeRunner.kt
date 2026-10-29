package app.luxion.shogunai.cli.e2e

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText

fun main(arguments: Array<String>) {
    val (tool, log) = arguments
    val call = arguments.drop(2)
    val response = recordAndRespond(Path.of(log), call) { history -> FakeRunners.respond(tool, history, call) }
    println(response)
}

private fun recordAndRespond(log: Path, call: List<String>, respond: (List<List<String>>) -> String): String =
    RandomAccessFile(log.toFile(), "rw").use { file ->
        file.channel.lock().use {
            val bytes = ByteArray(file.length().toInt()).also { file.readFully(it) }
            val history = String(bytes, StandardCharsets.UTF_8).lines().filter { it.isNotBlank() }.map(::parseCall)
            file.seek(file.length())
            file.write((encodeCall(call) + "\n").toByteArray(StandardCharsets.UTF_8))
            respond(history)
        }
    }

private fun encodeCall(call: List<String>): String = buildJsonArray { call.forEach { add(JsonPrimitive(it)) } }.toString()

private fun parseCall(line: String): List<String> = Json.parseToJsonElement(line).jsonArray.map { it.jsonPrimitive.content }

object FakeRunners {

    const val ORCA_VERSION = "1.4.243"

    fun install(sandbox: Sandbox) {
        if (Sandbox.isWindows) {
            writeWindowsLauncher(sandbox.pathFolder.resolve("orca.cmd"), "orca", sandbox.logOf("orca"))
        } else {
            writeUnixLauncher(sandbox.pathFolder.resolve("herdr"), "herdr", sandbox.logOf("herdr"))
            writeUnixLauncher(sandbox.pathFolder.resolve(if (Sandbox.isLinux) "orca-ide" else "orca"), "orca", sandbox.logOf("orca"))
        }
    }

    fun calls(sandbox: Sandbox, tool: String): List<List<String>> {
        val log = sandbox.logOf(tool)
        if (!Files.exists(log)) return emptyList()
        return Files.readAllLines(log).filter { it.isNotBlank() }.map(::parseCall)
    }

    fun respond(tool: String, history: List<List<String>>, call: List<String>): String = when (tool) {
        "herdr" -> FakeHerdr(history).respond(call)
        "orca" -> FakeOrca(history).respond(call)
        else -> error("unknown fake runner $tool")
    }

    private fun Sandbox.logOf(tool: String): Path = root.resolve("$tool-calls.jsonl")

    private fun writeUnixLauncher(file: Path, tool: String, log: Path) {
        file.writeText(
            "#!/bin/sh\nexec ${quoteForShell(javaExecutable())} -cp ${quoteForShell(classpath())} $MAIN_CLASS $tool ${quoteForShell(log.toString())} \"$@\"\n",
        )
        file.toFile().setExecutable(true)
    }

    private fun writeWindowsLauncher(file: Path, tool: String, log: Path) {
        file.writeText("@echo off\r\n\"${javaExecutable()}\" -cp \"${classpath()}\" $MAIN_CLASS $tool \"$log\" %*\r\n")
    }

    private fun javaExecutable(): String =
        Path.of(System.getProperty("java.home"), "bin", if (Sandbox.isWindows) "java.exe" else "java").toString()

    private fun classpath(): String = System.getProperty("shogun.e2e.classpath") ?: error("shogun.e2e.classpath is not set")

    private fun quoteForShell(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    private const val MAIN_CLASS = "app.luxion.shogunai.cli.e2e.FakeRunnerKt"
}

private fun List<String>.option(name: String): String? = indexOf(name).takeIf { it >= 0 }?.let { getOrNull(it + 1) }

private fun List<String>.startsWith(vararg words: String): Boolean = size >= words.size && words.indices.all { this[it] == words[it] }

private fun agentOf(command: String): String = command.trim().substringBefore(' ').substringAfterLast('/').substringAfterLast('\\')

private class FakeHerdr(history: List<List<String>>) {

    private data class Workspace(val id: String, val label: String, val cwd: String, val path: String?, val rootPane: String)

    private data class Pane(val id: String, val workspaceId: String, val cwd: String, var agent: String? = null, var closed: Boolean = false)

    private val workspaces = mutableListOf<Workspace>()
    private val panes = mutableListOf<Pane>()

    init {
        history.forEach { apply(it) }
    }

    fun respond(call: List<String>): String = buildJsonObject {
        put("id", "fake")
        put("result", apply(call))
    }.toString()

    private fun apply(call: List<String>): JsonObject = when {
        call.startsWith("worktree", "open") -> openWorktree(call)
        call.startsWith("workspace", "create") -> opening(newWorkspace(call.option("--label").orEmpty(), call.option("--cwd").orEmpty(), null), false)
        call.startsWith("pane", "split") -> {
            val parent = panes.first { it.id == call[2] }
            val pane = Pane("pane-${panes.size + 1}", parent.workspaceId, call.option("--cwd") ?: parent.cwd).also(panes::add)
            buildJsonObject { put("pane", buildJsonObject { put("pane_id", pane.id) }) }
        }
        call.startsWith("pane", "close") -> empty().also { panes.first { it.id == call[2] }.closed = true }
        call.startsWith("pane", "run") -> empty().also { panes.first { it.id == call[2] }.agent = agentOf(call[3]) }
        call.startsWith("pane", "list") -> buildJsonObject {
            val workspace = call.option("--workspace")
            put("panes", JsonArray(livePanes().filter { workspace == null || it.workspaceId == workspace }.map(::paneJson)))
        }
        call.startsWith("agent", "list") -> buildJsonObject {
            put("agents", JsonArray(livePanes().filter { it.agent != null }.map(::paneJson)))
        }
        call.startsWith("workspace", "list") -> buildJsonObject {
            put("workspaces", JsonArray(workspaces.map { workspace ->
                buildJsonObject {
                    put("workspace_id", workspace.id)
                    put("label", workspace.label)
                    workspace.path?.let { path -> put("worktree", buildJsonObject { put("checkout_path", path) }) }
                }
            }))
        }
        call.startsWith("worktree", "list") -> buildJsonObject {
            val cwd = call.option("--cwd")
            put("worktrees", JsonArray(workspaces.filter { it.path != null && it.cwd == cwd }.map { workspace ->
                buildJsonObject {
                    put("path", workspace.path)
                    put("open_workspace_id", workspace.id)
                }
            }))
        }
        else -> empty()
    }

    private fun openWorktree(call: List<String>): JsonObject {
        val path = call.option("--path").orEmpty()
        workspaces.firstOrNull { it.path == path }?.let { return opening(it, true) }
        return opening(newWorkspace(call.option("--label").orEmpty(), call.option("--cwd").orEmpty(), path), false)
    }

    private fun newWorkspace(label: String, cwd: String, path: String?): Workspace {
        val id = "workspace-${workspaces.size + 1}"
        val root = Pane("$id-root", id, path ?: cwd).also(panes::add)
        return Workspace(id, label, cwd, path, root.id).also(workspaces::add)
    }

    private fun opening(workspace: Workspace, alreadyOpen: Boolean): JsonObject = buildJsonObject {
        put("workspace", buildJsonObject { put("workspace_id", workspace.id) })
        put("root_pane", buildJsonObject { put("pane_id", workspace.rootPane) })
        put("already_open", alreadyOpen)
    }

    private fun livePanes(): List<Pane> = panes.filterNot { it.closed }

    private fun paneJson(pane: Pane): JsonObject = buildJsonObject {
        put("pane_id", pane.id)
        put("workspace_id", pane.workspaceId)
        put("cwd", pane.cwd)
        pane.agent?.let { put("agent", it) }
        put("agent_status", if (pane.agent == null) "unknown" else "idle")
    }

    private fun empty(): JsonObject = JsonObject(emptyMap())
}

private class FakeOrca(history: List<List<String>>) {

    private data class Repo(val id: String, val path: String)

    private data class Terminal(val handle: String, val selector: String, val title: String, val agent: String)

    private val repos = mutableListOf<Repo>()
    private val terminals = mutableListOf<Terminal>()

    init {
        history.forEach { apply(it) }
    }

    fun respond(call: List<String>): String = buildJsonObject {
        put("ok", true)
        put("result", apply(call))
    }.toString()

    private fun apply(call: List<String>): JsonObject = when {
        call.startsWith("status") -> buildJsonObject {
            put("runtime", buildJsonObject {
                put("reachable", true)
                put("appVersion", FakeRunners.ORCA_VERSION)
            })
        }
        call.startsWith("repo", "list") -> buildJsonObject { put("repos", JsonArray(repos.map(::repoJson))) }
        call.startsWith("repo", "add") -> {
            val repo = Repo("repo-${repos.size + 1}", call.option("--path").orEmpty()).also(repos::add)
            buildJsonObject { put("repo", repoJson(repo)) }
        }
        call.startsWith("worktree", "list") || call.startsWith("worktree", "ps") -> buildJsonObject { put("worktrees", JsonArray(emptyList())) }
        call.startsWith("ports") -> buildJsonObject { put("ports", JsonArray(emptyList())) }
        call.startsWith("terminal", "list") -> buildJsonObject {
            val selector = call.option("--worktree")
            put("terminals", JsonArray(terminals.filter { it.selector == selector }.map(::terminalJson)))
        }
        call.startsWith("terminal", "create") -> {
            val terminal = Terminal(
                "terminal-${terminals.size + 1}",
                call.option("--worktree").orEmpty(),
                call.option("--title").orEmpty(),
                agentOf(call.option("--command").orEmpty()),
            ).also(terminals::add)
            buildJsonObject { put("terminal", terminalJson(terminal)) }
        }
        call.startsWith("terminal", "show") -> buildJsonObject {
            val terminal = terminals.firstOrNull { it.handle == call.option("--terminal") }
            put("terminal", buildJsonObject { terminal?.let { put("agentIdentity", it.agent) } })
        }
        else -> JsonObject(emptyMap())
    }

    private fun repoJson(repo: Repo): JsonElement = buildJsonObject {
        put("id", repo.id)
        put("path", repo.path)
    }

    private fun terminalJson(terminal: Terminal): JsonElement = buildJsonObject {
        put("handle", terminal.handle)
        put("worktreePath", terminal.selector.removePrefix("path:"))
        put("title", terminal.title)
    }
}
