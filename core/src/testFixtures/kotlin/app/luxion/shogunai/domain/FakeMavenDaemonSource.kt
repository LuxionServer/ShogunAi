package app.luxion.shogunai.domain

import app.luxion.shogunai.domain.io.MavenDaemonSource
import app.luxion.shogunai.domain.model.MavenDaemonProcess
import app.luxion.shogunai.domain.model.WorktreeError

class FakeMavenDaemonSource(
    var daemons: List<MavenDaemonProcess> = emptyList(),
    var failure: WorktreeError? = null,
) : MavenDaemonSource {

    var listings = 0

    override suspend fun list(): List<MavenDaemonProcess> {
        listings++
        failure?.let { throw it }
        return daemons
    }
}
