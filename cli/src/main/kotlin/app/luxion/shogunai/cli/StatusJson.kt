package app.luxion.shogunai.cli

import app.luxion.shogunai.domain.model.DesktopTurnState
import app.luxion.shogunai.domain.model.AgentStateSource
import app.luxion.shogunai.domain.model.AgentActivity
import app.luxion.shogunai.domain.model.Divergence
import app.luxion.shogunai.domain.model.ObservedPort
import app.luxion.shogunai.domain.model.RunningAgent
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeOrigin
import app.luxion.shogunai.domain.model.WorktreeStatus
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class WorktreeEntry(
    val worktree: Worktree,
    val status: WorktreeStatus?,
    val origin: WorktreeOrigin?,
    val statusProblem: String? = null,
    val agents: List<RunningAgent> = emptyList(),
    val desktop: DesktopTurnState = DesktopTurnState(holder = null, waiting = emptyList()),
    val base: String? = null,
    val listeningPorts: List<ObservedPort>? = null,
)

val WorktreeOrigin.label: String
    get() = when (this) {
        WorktreeOrigin.SHOGUNAI -> "shogunai"
        WorktreeOrigin.CLAUDE_CODE -> "claude-code"
        WorktreeOrigin.HERDR -> "herdr"
        WorktreeOrigin.ORCA -> "orca"
        WorktreeOrigin.OTHER -> "other"
    }

fun statusJson(repository: String, mainBranch: String?, entries: List<WorktreeEntry>): JsonObject = buildJsonObject {
    put("repository", repository)
    put("mainBranch", mainBranch)
    put("worktrees", buildJsonArray { entries.forEach { add(worktreeJson(it)) } })
}

private fun worktreeJson(entry: WorktreeEntry): JsonObject = buildJsonObject {
    val worktree = entry.worktree
    put("path", worktree.path)
    put("branch", worktree.branch)
    put("head", worktree.head)
    put("main", worktree.isMain)
    put("locked", worktree.isLocked)
    put("folderMissing", worktree.isPrunable)
    put("origin", entry.origin?.label)
    put("base", entry.base)
    put("agents", buildJsonArray { entry.agents.forEach { add(agentJson(it)) } })
    put("status", entry.status?.let(::statusObject) ?: JsonNull)
    entry.statusProblem?.let { put("statusProblem", it) }
    put("desktop", worktreeDesktopJson(entry.desktop, worktree.path) ?: JsonNull)
    put("listeningPorts", entry.listeningPorts?.let { ports -> buildJsonArray { ports.forEach { add(portJson(it)) } } } ?: JsonNull)
}

private fun portJson(port: ObservedPort): JsonObject = buildJsonObject {
    put("port", port.port)
    put("process", port.process)
    put("url", port.url)
}

internal fun agentJson(agent: RunningAgent): JsonObject = buildJsonObject {
    put("name", agent.name)
    put("state", agent.state?.name?.lowercase())
    put("stateSource", agent.stateSource?.let(::sourceLabel))
    put("herdrState", agent.state?.takeIf { agent.stateSource == AgentStateSource.HERDR }?.name?.lowercase())
    put("sessions", buildJsonArray {
        agent.sessions.forEach { session ->
            add(buildJsonObject {
                put("name", session.name)
                put("pid", session.pid)
            })
        }
    })
    put("activity", agent.activity?.let(::activityJson) ?: JsonNull)
}

private fun activityJson(activity: AgentActivity): JsonObject = buildJsonObject {
    put("waitingOn", activity.waitingOn?.let { tool ->
        buildJsonObject {
            put("tool", tool.name)
            put("input", tool.input)
        }
    } ?: JsonNull)
    put("prompt", activity.prompt)
    put("lastMessage", activity.lastMessage)
}

private fun sourceLabel(source: AgentStateSource): String = when (source) {
    AgentStateSource.HERDR -> "herdr"
    AgentStateSource.ORCA -> "orca"
    AgentStateSource.CLAUDE_CODE -> "claude-code"
}

private fun statusObject(status: WorktreeStatus): JsonObject = buildJsonObject {
    put("uncommittedChanges", status.uncommittedChanges)
    put("upstream", status.upstream?.let(::divergenceObject) ?: JsonNull)
    put("mainBranch", status.mainBranch?.let(::divergenceObject) ?: JsonNull)
    put("base", status.base?.let { divergence ->
        buildJsonObject {
            put("branch", status.baseName)
            put("ahead", divergence.ahead)
            put("behind", divergence.behind)
        }
    } ?: JsonNull)
    put("merged", status.isMerged)
    put("lastCommitAt", status.lastCommitAt?.toString())
    put("createdAt", status.createdAt?.toString())
}

private fun divergenceObject(divergence: Divergence): JsonObject = buildJsonObject {
    put("ahead", divergence.ahead)
    put("behind", divergence.behind)
}
