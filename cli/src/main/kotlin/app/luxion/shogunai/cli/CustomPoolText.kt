package app.luxion.shogunai.cli

import app.luxion.shogunai.domain.model.CustomPool
import app.luxion.shogunai.domain.model.CustomPoolAcquisition
import app.luxion.shogunai.domain.model.CustomPoolHolder
import app.luxion.shogunai.domain.model.CustomPoolRelease
import app.luxion.shogunai.domain.model.CustomPoolState
import app.luxion.shogunai.domain.model.CustomPoolWaitProgress
import app.luxion.shogunai.domain.model.ShellQuoting
import app.luxion.shogunai.domain.model.toLocalClockTime
import java.time.Instant

internal fun poolAcquisitionLines(acquisition: CustomPoolAcquisition): List<String> {
    val verb = if (acquisition.renewed) "renewed" else "acquired"
    val taken = acquisition.takenFrom?.let { ", taken from $it" }.orEmpty()
    return listOf("$verb slot ${acquisition.slot} of pool ${acquisition.pool}, lease until ${acquisition.expiresAt.toLocalClockTime()}$taken") +
        acquisition.variables.map { (name, value) -> "export $name=${ShellQuoting.posix(value)}" }
}

internal fun poolReleaseLine(release: CustomPoolRelease): String =
    if (release.slot == null) "held no slot of pool ${release.pool}" else "released slot ${release.slot} of pool ${release.pool}"

internal fun poolWaitProgressLine(progress: CustomPoolWaitProgress): String {
    val holders = progress.holders.joinToString("; ") { it.text }.ifEmpty { "no holders" }
    val next = progress.nextLeaseExpiry?.let { "; the next lease expires at ${it.toLocalClockTime()}" }.orEmpty()
    return "waiting for pool ${progress.pool}: ${ordinal(progress.position)} of ${progress.waiting} in line for ${progress.max} " +
        "${if (progress.max == 1) "slot" else "slots"}; $holders$next"
}

internal fun customPoolStatusLines(states: List<CustomPoolState>): List<String> = states.flatMap { state ->
    listOf("pool ${state.pool.name} ${state.holders.size}/${state.pool.max}") +
        state.holders.map { "  slot ${it.lease.slot} ${it.displayName} ${holderNote(it)}" } +
        state.waiting.map { "  waiting: ${it.displayName} since ${Instant.ofEpochMilli(it.since).toLocalClockTime()}" }
}

private fun holderNote(holder: CustomPoolHolder): String = when {
    holder.running -> "running a command"
    holder.note.agentWorking -> "agent working"
    else -> "lease until ${holder.expiresAt.toLocalClockTime()}"
}

internal fun customPoolStatusNotes(states: List<CustomPoolState>, worktreePath: String): List<String> = states.mapNotNull { state ->
    val held = state.holders.firstOrNull { samePoolPath(it.lease.worktreePath, worktreePath) }
    val position = state.waiting.indexOfFirst { samePoolPath(it.worktreePath, worktreePath) }
    val lost = state.losses.lastOrNull { samePoolPath(it.worktreePath, worktreePath) }
    when {
        held != null -> "pool=${state.pool.name}#${held.lease.slot}"
        position >= 0 -> "pool=${state.pool.name}(waiting ${position + 1}/${state.waiting.size})"
        lost != null -> "pool=${state.pool.name}(taken for ${lost.forWorktree})"
        else -> null
    }
}

internal fun poolDeclarationLines(repository: String, pools: List<CustomPool>): String = buildString {
    if (pools.isEmpty()) return@buildString
    append("Pools declared by $repository:\n")
    pools.forEach { pool ->
        append("  ${pool.name} (at most ${pool.max} at once)\n")
        append("    acquire: ${ShellQuoting.posixCommand(pool.acquireSource)}\n")
        pool.releaseSource?.let { append("    release: ${ShellQuoting.posixCommand(it)}\n") }
        pool.variables.forEach { append("    ${it.name} = ${it.template.source}\n") }
    }
}

internal fun samePoolPath(first: String, second: String): Boolean =
    first.replace('\\', '/').trimEnd('/') == second.replace('\\', '/').trimEnd('/')
