package app.luxion.shogunai.domain.model

import java.time.Instant

enum class DaemonKind { GRADLE, KOTLIN }

sealed interface DaemonState {
    data object Busy : DaemonState
    data class Idle(val since: Instant) : DaemonState
    data object Unknown : DaemonState
    data object Untracked : DaemonState
}

data class DaemonProcess(
    val pid: Long,
    val kind: DaemonKind,
    val gradleVersion: String?,
    val residentMemoryBytes: Long,
    val lastBuildDirectory: String?,
    val state: DaemonState,
)

sealed interface DaemonOwner {
    data class Worktree(val path: String) : DaemonOwner
    data object RemovedWorktree : DaemonOwner
    data object OtherProject : DaemonOwner
}

data class GradleDaemon(
    val process: DaemonProcess,
    val owner: DaemonOwner?,
) {
    val pid: Long get() = process.pid
}

enum class DaemonKeptReason { BUSY, STATE_UNKNOWN, BUILD_RUNNING }

data class KeptDaemon(val pid: Long, val kind: DaemonKind, val reason: DaemonKeptReason)

data class DaemonStopFailure(val pid: Long, val detail: String)

data class DaemonStopReport(
    val stopped: List<DaemonProcess> = emptyList(),
    val kept: List<KeptDaemon> = emptyList(),
    val notDaemons: List<Long> = emptyList(),
    val failures: List<DaemonStopFailure> = emptyList(),
)
