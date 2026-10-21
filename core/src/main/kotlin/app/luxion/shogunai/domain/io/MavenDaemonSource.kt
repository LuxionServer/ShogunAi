package app.luxion.shogunai.domain.io

import app.luxion.shogunai.domain.model.MavenDaemonProcess

interface MavenDaemonSource {

    suspend fun list(): List<MavenDaemonProcess>
}
