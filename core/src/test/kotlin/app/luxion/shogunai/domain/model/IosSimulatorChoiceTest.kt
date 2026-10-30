package app.luxion.shogunai.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IosSimulatorChoiceTest {

    private val ipad = SimulatorDeviceType("com.apple.CoreSimulator.SimDeviceType.iPad-Air", "iPad Air", "iPad")
    private val watch = SimulatorDeviceType("com.apple.CoreSimulator.SimDeviceType.Apple-Watch", "Apple Watch", "Watch")

    private fun runtime(vararg types: SimulatorDeviceType) =
        SimulatorRuntime("com.apple.CoreSimulator.SimRuntime.iOS-27-0", "iOS 27.0", "27.0", "iOS", types.toList())

    @Test
    fun `without an iPhone the first iPad is chosen`() {
        assertEquals(ipad, chooseSimulator(SimulatorSettings(), SimulatorSettings(), null, listOf(runtime(watch, ipad)))?.deviceType)
    }

    @Test
    fun `without an iPhone or an iPad there is no choice`() {
        assertNull(chooseSimulator(SimulatorSettings(), SimulatorSettings(), null, listOf(runtime(watch))))
    }
}
