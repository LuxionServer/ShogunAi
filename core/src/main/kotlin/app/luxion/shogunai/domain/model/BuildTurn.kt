package app.luxion.shogunai.domain.model

import java.time.Duration

data class BuildSlot(
    val id: String,
    val adminDirectory: String,
    val worktreePath: String,
    val command: String,
    val since: Long,
    val process: ProcessIdentity,
) {
    val displayName: String
        get() = poolDisplayName(adminDirectory, worktreePath)

    val repositoryPath: String
        get() = repositoryPathOf(adminDirectory)
}

data class BuildTurnState(
    val limit: Int?,
    val running: List<BuildSlot>,
    val waiting: List<WaitTicket>,
) {
    companion object {
        val EMPTY = BuildTurnState(limit = null, running = emptyList(), waiting = emptyList())
    }
}

data class BuildSlotAcquisition(val slot: BuildSlot, val waitedSeconds: Long)

data class BuildWait(
    val timeout: Duration = Duration.ofMinutes(DEFAULT_DEVICE_WAIT_MINUTES),
    val onProgress: (BuildWaitProgress) -> Unit = {},
)

data class BuildWaitProgress(
    val position: Int,
    val waiting: Int,
    val limit: Int,
    val running: List<BuildSlot>,
)

sealed class BuildTurnError(override val message: String) : Exception(message) {

    class SlotsTaken(val limit: Int, val running: List<BuildSlot>, val waiting: Int) :
        BuildTurnError(buildSlotsSummary(limit, running) + buildQueueNote(waiting))

    class WaitTimedOut(val waited: Duration, val limit: Int, val running: List<BuildSlot>) :
        BuildTurnError("Waited ${waited.toWaitText()} for a build slot; " + buildSlotsSummary(limit, running))
}

private fun buildSlotsSummary(limit: Int, running: List<BuildSlot>): String {
    val slots = if (limit == 1) "The only build slot is" else "All $limit build slots are"
    val holders = running.joinToString(", ") { it.displayName }
    return if (running.isEmpty()) "Other builds are waiting for a slot first" else "$slots taken by $holders"
}

private fun buildQueueNote(waiting: Int): String = when (waiting) {
    0 -> ""
    1 -> "; 1 build is waiting"
    else -> "; $waiting builds are waiting"
}
