package app.luxion.shogunai.domain.io

import app.luxion.shogunai.domain.model.DaemonProcess

interface GradleDaemonSource {

    suspend fun list(): List<DaemonProcess>
}
