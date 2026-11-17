package app.luxion.shogunai

import app.luxion.shogunai.domain.FakeAppleSimulators
import app.luxion.shogunai.domain.model.SimulatorDevice
import app.luxion.shogunai.domain.model.SimulatorRuntimeImage
import app.luxion.shogunai.domain.model.SimulatorState
import app.luxion.shogunai.engine.ShogunEngine
import app.luxion.shogunai.engine.fakeEnginePorts
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SimulatorStorageWiringTest {

    @Test
    fun `the desktop actions list and delete through the engine`() = runBlocking {
        val simulators = FakeAppleSimulators(installedRuntimes = emptyList())
        simulators.images += SimulatorRuntimeImage("IMAGE", "com.apple.CoreSimulator.SimRuntime.iOS-25-0", "25.0", "B", 1L, null, true)
        simulators.addDevice(SimulatorDevice("U", "iPhone", SimulatorState.SHUTDOWN, isAvailable = false))
        val actions = simulatorStorageActions(ShogunEngine(fakeEnginePorts(appleSimulators = simulators)))

        assertTrue(actions.supported)
        assertEquals(listOf("IMAGE"), actions.list().getOrThrow().runtimes.map { it.image.identifier })
        assertEquals(1L, actions.deleteRuntime("IMAGE").getOrThrow().bytesFreed)
        assertEquals(listOf("U"), actions.deleteUnavailable().getOrThrow().devices.map { it.udid })
    }
}
