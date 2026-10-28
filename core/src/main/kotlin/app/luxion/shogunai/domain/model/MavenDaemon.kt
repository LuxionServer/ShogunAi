package app.luxion.shogunai.domain.model

data class MavenDaemonProcess(
    val pid: Long,
    val residentMemoryBytes: Long,
    val workingDirectory: String?,
    val state: DaemonState = DaemonState.Unknown,
)

data class MavenDaemon(
    val process: MavenDaemonProcess,
    val owner: DaemonOwner?,
) {
    val pid: Long get() = process.pid
}

data class KeptMavenDaemon(val pid: Long, val reason: DaemonKeptReason)

data class MavenDaemonStopReport(
    val stopped: List<MavenDaemonProcess> = emptyList(),
    val kept: List<KeptMavenDaemon> = emptyList(),
    val notDaemons: List<Long> = emptyList(),
    val failures: List<DaemonStopFailure> = emptyList(),
)
