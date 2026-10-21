package app.luxion.shogunai.domain.model

data class MavenDaemonProcess(
    val pid: Long,
    val residentMemoryBytes: Long,
    val workingDirectory: String?,
)

data class MavenDaemon(
    val process: MavenDaemonProcess,
    val owner: DaemonOwner?,
) {
    val pid: Long get() = process.pid
}
