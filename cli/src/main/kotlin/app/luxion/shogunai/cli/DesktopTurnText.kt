package app.luxion.shogunai.cli

import app.luxion.shogunai.domain.model.DesktopTurn
import app.luxion.shogunai.domain.model.DesktopTurnAcquisition
import app.luxion.shogunai.domain.model.DesktopTurnRelease
import app.luxion.shogunai.domain.model.DesktopTurnState
import app.luxion.shogunai.domain.model.DesktopWaitProgress
import app.luxion.shogunai.domain.model.WaitTicket
import app.luxion.shogunai.domain.model.repositoryPath
import app.luxion.shogunai.domain.model.toLocalClockTime
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

internal fun desktopAcquisitionLine(acquisition: DesktopTurnAcquisition): String {
    val verb = if (acquisition.renewed) "renewed" else "acquired"
    return "$verb the desktop until ${clockTime(acquisition.turn.expiresAt)}"
}

internal fun desktopReleaseLine(release: DesktopTurnRelease): String = when (release) {
    is DesktopTurnRelease.Released -> "released the desktop"
    DesktopTurnRelease.NobodyHolds -> "nobody holds the desktop"
}

internal fun desktopStateLines(state: DesktopTurnState): List<String> = buildList {
    val holder = state.holder
    add(
        if (holder == null) {
            "desktop free"
        } else {
            "desktop held by ${holder.displayName} since ${clockTime(holder.acquiredAt)} until ${clockTime(holder.expiresAt)}"
        },
    )
    state.waiting.forEachIndexed { index, ticket ->
        add("  ${index + 1}. ${ticket.displayName} waiting since ${clockTime(ticket.since)}")
    }
}

internal fun desktopWaitProgressLine(progress: DesktopWaitProgress): String {
    val holder = progress.holder?.let { "held by ${it.displayName} until ${clockTime(it.expiresAt)}" } ?: "free for an earlier waiter"
    return "waiting for the desktop: ${ordinal(progress.position)} of ${progress.waiting} in line, $holder"
}

internal fun desktopTurnStatusNote(turn: DesktopTurnState, worktreePath: String): String? {
    val holder = turn.holder
    if (holder != null && samePath(holder.worktreePath, worktreePath)) return "desktop(until ${clockTime(holder.expiresAt)})"
    val position = turn.waiting.indexOfFirst { samePath(it.worktreePath, worktreePath) }
    return if (position < 0) null else "desktop(waiting ${position + 1}/${turn.waiting.size})"
}

internal fun desktopStateJson(state: DesktopTurnState): JsonObject = buildJsonObject { putDesktopState(state) }

internal fun desktopAcquisitionJson(acquisition: DesktopTurnAcquisition): JsonObject = buildJsonObject {
    putDesktopState(DesktopTurnState(acquisition.turn, acquisition.waiting))
    put("renewed", acquisition.renewed)
    put("waited", acquisition.waitedSeconds)
}

internal fun desktopReleaseJson(release: DesktopTurnRelease, state: DesktopTurnState): JsonObject = buildJsonObject {
    putDesktopState(state)
    put("released", release is DesktopTurnRelease.Released)
}

internal fun worktreeDesktopJson(turn: DesktopTurnState, worktreePath: String): JsonObject? {
    val holder = turn.holder
    if (holder != null && samePath(holder.worktreePath, worktreePath)) {
        return buildJsonObject {
            put("role", "holder")
            put("since", isoTime(holder.acquiredAt))
            put("expiresAt", isoTime(holder.expiresAt))
        }
    }
    val position = turn.waiting.indexOfFirst { samePath(it.worktreePath, worktreePath) }
    if (position < 0) return null
    return buildJsonObject {
        put("role", "waiting")
        put("position", position + 1)
        put("since", isoTime(turn.waiting[position].since))
    }
}

private fun JsonObjectBuilder.putDesktopState(state: DesktopTurnState) {
    put("holder", state.holder?.let(::holderJson) ?: JsonNull)
    put("waiting", buildJsonArray { state.waiting.forEach { add(waiterJson(it)) } })
}

private fun holderJson(turn: DesktopTurn): JsonObject = buildJsonObject {
    put("repository", turn.repositoryPath)
    put("worktree", turn.worktreePath)
    put("since", isoTime(turn.acquiredAt))
    put("expiresAt", isoTime(turn.expiresAt))
}

private fun waiterJson(ticket: WaitTicket): JsonObject = buildJsonObject {
    put("repository", ticket.repositoryPath)
    put("worktree", ticket.worktreePath)
    put("since", isoTime(ticket.since))
}

private fun samePath(first: String, second: String): Boolean = first.comparablePath() == second.comparablePath()

private fun String.comparablePath(): String = replace('\\', '/').trimEnd('/')

private fun clockTime(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis).toLocalClockTime()

private fun isoTime(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis).toString()
