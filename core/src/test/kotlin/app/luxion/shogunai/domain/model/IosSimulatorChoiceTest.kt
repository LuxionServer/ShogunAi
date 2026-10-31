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
    @Test
    fun `the newest runtime wins comparing versions part by part`() {
        fun runtimeOf(version: String) = SimulatorRuntime("id-$version", "iOS $version", version, "iOS", listOf(ipad))
        val runtimes = listOf("18.0", "18.0.1", "18.beta", "17.4").map(::runtimeOf)

        assertEquals("18.0.1", chooseSimulator(SimulatorSettings(), SimulatorSettings(), null, runtimes)?.runtime?.version)
        assertEquals("18.0", chooseSimulator(SimulatorSettings(), SimulatorSettings(), null, listOf("18.0", "18.beta").map(::runtimeOf))?.runtime?.version)
        assertEquals("alpha", chooseSimulator(SimulatorSettings(), SimulatorSettings(), null, listOf("alpha", "beta").map(::runtimeOf))?.runtime?.version)
        assertEquals("18", chooseSimulator(SimulatorSettings(), SimulatorSettings(), null, listOf("18", "18.0").map(::runtimeOf))?.runtime?.version)
    }

    @Test
    fun `numbered iPhones are preferred to unnumbered ones`() {
        val se = SimulatorDeviceType("se", "iPhone SE", "iPhone")
        val sixteen = SimulatorDeviceType("16", "iPhone 16", "iPhone")

        assertEquals(sixteen, chooseSimulator(SimulatorSettings(), SimulatorSettings(), null, listOf(runtime(se, sixteen)))?.deviceType)
        assertEquals(se, chooseSimulator(SimulatorSettings(), SimulatorSettings(), null, listOf(runtime(se)))?.deviceType)
    }
}
