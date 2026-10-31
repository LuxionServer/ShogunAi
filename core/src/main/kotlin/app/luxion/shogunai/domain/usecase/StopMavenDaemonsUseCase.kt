package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.MavenDaemonSource
import app.luxion.shogunai.domain.model.DaemonKeptReason
import app.luxion.shogunai.domain.model.DaemonState
import app.luxion.shogunai.domain.model.DaemonStopFailure
import app.luxion.shogunai.domain.model.KeptMavenDaemon
import app.luxion.shogunai.domain.model.MavenDaemonProcess
import app.luxion.shogunai.domain.model.MavenDaemonStopReport

class StopMavenDaemonsUseCase(
    private val source: MavenDaemonSource,
    private val executor: ShellCommandExecutor,
) {
    suspend operator fun invoke(pids: List<Long>? = null): Result<MavenDaemonStopReport> = runCatching {
        val current = source.list()
        val currentByPid = current.associateBy { it.pid }
        val targets = if (pids == null) current else pids.distinct().mapNotNull { currentByPid[it] }
        val notDaemons = pids.orEmpty().distinct().filter { it !in currentByPid }

        val stopped = mutableListOf<MavenDaemonProcess>()
        val kept = mutableListOf<KeptMavenDaemon>()
        val failures = mutableListOf<DaemonStopFailure>()

        targets.forEach { daemon ->
            val reason = keptReason(daemon.state)
            if (reason != null) {
                kept += KeptMavenDaemon(daemon.pid, reason)
                return@forEach
            }
            val result = executor.execute(listOf("kill", daemon.pid.toString()))
            if (result.isSuccess) {
                stopped += daemon
            } else {
                failures += DaemonStopFailure(daemon.pid, result.stderr.trim().ifEmpty { "exit code ${result.exitCode}" })
            }
        }

        MavenDaemonStopReport(stopped = stopped, kept = kept, notDaemons = notDaemons, failures = failures)
    }

    private fun keptReason(state: DaemonState): DaemonKeptReason? = when (state) {
        is DaemonState.Idle -> null
        DaemonState.Busy -> DaemonKeptReason.BUSY
        DaemonState.Unknown, DaemonState.Untracked -> DaemonKeptReason.STATE_UNKNOWN
    }
}
