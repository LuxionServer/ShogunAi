package app.luxion.shogunai.cli

import app.luxion.shogunai.domain.model.BuildSlot
import app.luxion.shogunai.domain.model.BuildTurnState
import app.luxion.shogunai.domain.model.BuildWaitProgress
import app.luxion.shogunai.domain.model.WaitTicket
import app.luxion.shogunai.domain.model.repositoryPath
import app.luxion.shogunai.domain.model.toLocalClockTime
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

internal fun buildWaitProgressLine(progress: BuildWaitProgress): String {
    val slots = if (progress.limit == 1) "1 build slot" else "${progress.limit} build slots"
    val holding = if (progress.running.isEmpty()) "free for earlier waiters" else "held by " + progress.running.joinToString(", ") { it.displayName }
    return "waiting for a build slot: ${ordinal(progress.position)} of ${progress.waiting} in line for $slots, $holding"
}

internal fun buildStateLines(state: BuildTurnState): List<String> = buildList {
    val limit = state.limit
    add(
        when (limit) {
            null -> "build slots: no limit, ${state.running.size} running"
            else -> "build slots: ${state.running.size} of $limit in use"
        },
    )
    state.running.forEach { add("  ${it.displayName} running `${it.command}` since ${clockTime(it.since)}") }
    state.waiting.forEachIndexed { index, ticket ->
        add("  ${index + 1}. ${ticket.displayName} waiting since ${clockTime(ticket.since)}")
    }
}

internal fun buildStateJson(state: BuildTurnState): JsonObject = buildJsonObject {
    put("limit", state.limit)
    put("running", buildJsonArray { state.running.forEach { add(runningJson(it)) } })
    put("waiting", buildJsonArray { state.waiting.forEach { add(waiterJson(it)) } })
}

internal fun buildStatusNote(state: BuildTurnState, worktreePath: String): String? {
    val running = state.running.count { sameBuildPath(it.worktreePath, worktreePath) }
    val position = state.waiting.indexOfFirst { sameBuildPath(it.worktreePath, worktreePath) }
    return when {
        position >= 0 -> "build(waiting ${position + 1}/${state.waiting.size})"
        running > 0 -> "build(running $running)"
        else -> null
    }
}

internal fun worktreeBuildJson(state: BuildTurnState, worktreePath: String): JsonObject? {
    val running = state.running.count { sameBuildPath(it.worktreePath, worktreePath) }
    val position = state.waiting.indexOfFirst { sameBuildPath(it.worktreePath, worktreePath) }
    if (running == 0 && position < 0) return null
    return buildJsonObject {
        put("running", running)
        put("waiting", (position + 1).takeIf { position >= 0 })
    }
}

internal fun buildTurnDenial(builds: List<String>): String =
    "Heavy builds share the machine's build slots. Run it through the build turn instead: " +
        builds.joinToString("; ") { "`shogun build run -- $it`" } +
        ", in the background or with a long enough shell timeout, since it can wait up to 30 minutes for a slot."

private fun runningJson(slot: BuildSlot): JsonObject = buildJsonObject {
    put("repository", slot.repositoryPath)
    put("worktree", slot.worktreePath)
    put("command", slot.command)
    put("since", Instant.ofEpochMilli(slot.since).toString())
}

private fun waiterJson(ticket: WaitTicket): JsonObject = buildJsonObject {
    put("repository", ticket.repositoryPath)
    put("worktree", ticket.worktreePath)
    put("since", Instant.ofEpochMilli(ticket.since).toString())
}

private fun sameBuildPath(first: String, second: String): Boolean =
    first.replace('\\', '/').trimEnd('/') == second.replace('\\', '/').trimEnd('/')

private fun clockTime(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis).toLocalClockTime()
