package app.luxion.shogunai.domain

import app.luxion.shogunai.domain.io.GradleDaemonSource
import app.luxion.shogunai.domain.model.DaemonKind
import app.luxion.shogunai.domain.model.DaemonProcess
import app.luxion.shogunai.domain.model.DaemonState
import app.luxion.shogunai.domain.model.WorktreeError

class FakeGradleDaemonSource(
    var daemons: List<DaemonProcess> = emptyList(),
    var failure: WorktreeError? = null,
    var laterDaemons: List<DaemonProcess>? = null,
) : GradleDaemonSource {

    var listings = 0
        private set

    override suspend fun list(): List<DaemonProcess> {
        listings++
        failure?.let { throw it }
        return laterDaemons?.takeIf { listings > 1 } ?: daemons
    }

    companion object {
        fun gradleDaemon(
            pid: Long,
            state: DaemonState,
            lastBuildDirectory: String? = null,
            version: String = "9.6.1",
            memoryBytes: Long = 1_000_000_000L,
        ) = DaemonProcess(pid, DaemonKind.GRADLE, version, memoryBytes, lastBuildDirectory, state)

        fun kotlinDaemon(pid: Long, memoryBytes: Long = 500_000_000L) =
            DaemonProcess(pid, DaemonKind.KOTLIN, null, memoryBytes, null, DaemonState.Untracked)
    }
}
