package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.GradleDaemonSource
import app.luxion.shogunai.domain.model.DaemonKeptReason
import app.luxion.shogunai.domain.model.DaemonKind
import app.luxion.shogunai.domain.model.DaemonProcess
import app.luxion.shogunai.domain.model.DaemonState
import app.luxion.shogunai.domain.model.DaemonStopFailure
import app.luxion.shogunai.domain.model.DaemonStopReport
import app.luxion.shogunai.domain.model.KeptDaemon

class StopGradleDaemonsUseCase(
    private val source: GradleDaemonSource,
    private val executor: ShellCommandExecutor,
) {
    suspend operator fun invoke(pids: List<Long>? = null): Result<DaemonStopReport> = runCatching {
        val current = source.list()
        val currentByPid = current.associateBy { it.pid }
        val targets = if (pids == null) current else pids.distinct().mapNotNull { currentByPid[it] }
        val notDaemons = pids.orEmpty().distinct().filter { it !in currentByPid }
        val buildRunning = current.any { it.kind == DaemonKind.GRADLE && it.state !is DaemonState.Idle }

        val stopped = mutableListOf<DaemonProcess>()
        val kept = mutableListOf<KeptDaemon>()
        val failures = mutableListOf<DaemonStopFailure>()

        targets.forEach { daemon ->
            val reason = keptReason(daemon, buildRunning)
            if (reason != null) {
                kept += KeptDaemon(daemon.pid, daemon.kind, reason)
                return@forEach
            }
            val result = executor.execute(listOf("kill", daemon.pid.toString()))
            if (result.isSuccess) {
                stopped += daemon
            } else {
                failures += DaemonStopFailure(daemon.pid, result.stderr.trim().ifEmpty { "exit code ${result.exitCode}" })
            }
        }

        DaemonStopReport(stopped = stopped, kept = kept, notDaemons = notDaemons, failures = failures)
    }

    private fun keptReason(daemon: DaemonProcess, buildRunning: Boolean): DaemonKeptReason? = when (daemon.kind) {
        DaemonKind.GRADLE -> when (daemon.state) {
            is DaemonState.Idle -> null
            DaemonState.Busy -> DaemonKeptReason.BUSY
            DaemonState.Unknown, DaemonState.Untracked -> DaemonKeptReason.STATE_UNKNOWN
        }
        DaemonKind.KOTLIN -> if (buildRunning) DaemonKeptReason.BUILD_RUNNING else null
    }
}
