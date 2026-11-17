package app.luxion.shogunai.domain.io

import app.luxion.shogunai.domain.model.SimulatorDevice
import app.luxion.shogunai.domain.model.SimulatorRuntime
import app.luxion.shogunai.domain.model.SimulatorRuntimeImage

interface AppleSimulators {

    fun isSupported(): Boolean

    suspend fun runtimes(): List<SimulatorRuntime>

    suspend fun devices(): List<SimulatorDevice>

    suspend fun create(name: String, deviceTypeIdentifier: String, runtimeIdentifier: String): String

    suspend fun boot(udid: String)

    suspend fun show(udid: String)

    suspend fun shutdown(udid: String)

    suspend fun delete(udid: String)

    suspend fun runtimeImages(): List<SimulatorRuntimeImage>

    suspend fun deleteRuntime(identifier: String)

    suspend fun deleteUnavailable()
}
