package app.luxion.shogunai.domain.io

import app.luxion.shogunai.domain.model.SimulatorDevice
import app.luxion.shogunai.domain.model.SimulatorRuntime

interface AppleSimulators {

    fun isSupported(): Boolean

    suspend fun runtimes(): List<SimulatorRuntime>

    suspend fun devices(): List<SimulatorDevice>

    suspend fun create(name: String, deviceTypeIdentifier: String, runtimeIdentifier: String): String

    suspend fun boot(udid: String)

    suspend fun shutdown(udid: String)

    suspend fun delete(udid: String)
}
