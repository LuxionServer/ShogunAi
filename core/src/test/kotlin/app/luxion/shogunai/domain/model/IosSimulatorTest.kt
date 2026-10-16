package app.luxion.shogunai.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IosSimulatorTest {

    private val iphone16 = SimulatorDeviceType("com.apple.CoreSimulator.SimDeviceType.iPhone-16", "iPhone 16", "iPhone")
    private val iphone16Pro = SimulatorDeviceType("com.apple.CoreSimulator.SimDeviceType.iPhone-16-Pro", "iPhone 16 Pro", "iPhone")
    private val iphone17 = SimulatorDeviceType("com.apple.CoreSimulator.SimDeviceType.iPhone-17", "iPhone 17", "iPhone")
    private val ipad = SimulatorDeviceType("com.apple.CoreSimulator.SimDeviceType.iPad-Pro-11", "iPad Pro 11-inch (M4)", "iPad")
    private val watch = SimulatorDeviceType("com.apple.CoreSimulator.SimDeviceType.Apple-Watch-10", "Apple Watch Series 10", "Apple Watch")

    private val ios185 = SimulatorRuntime("com.apple.CoreSimulator.SimRuntime.iOS-18-5", "iOS 18.5", "18.5", "iOS", listOf(iphone16, iphone16Pro, ipad))
    private val ios263 = SimulatorRuntime("com.apple.CoreSimulator.SimRuntime.iOS-26-3", "iOS 26.3", "26.3", "iOS", listOf(iphone16, iphone17, ipad))
    private val ios1810 = SimulatorRuntime("com.apple.CoreSimulator.SimRuntime.iOS-18-10", "iOS 18.10", "18.10", "iOS", listOf(iphone16))
    private val watchOs = SimulatorRuntime("com.apple.CoreSimulator.SimRuntime.watchOS-26-0", "watchOS 26.0", "26.0", "watchOS", listOf(watch))
    private val runtimes = listOf(ios185, ios263, watchOs)

    @Test
    fun `defaults to the newest iOS runtime and its newest iPhone`() {
        assertEquals(SimulatorChoice(ios263, iphone17), chooseSimulator(SimulatorSettings(), SimulatorSettings(), null, runtimes))
    }

    @Test
    fun `the default iPhone doesn't depend on the order Apple lists them in`() {
        val names = listOf("iPhone 18 Pro", "iPhone 18 Pro Max", "iPhone 17e", "iPhone Air", "iPhone 17", "iPhone SE (3rd generation)", "iPhone 11")
        val types = names.map { SimulatorDeviceType("com.apple.CoreSimulator.SimDeviceType.${it.replace(' ', '-')}", it, "iPhone") }
        val newestFirst = SimulatorRuntime("com.apple.CoreSimulator.SimRuntime.iOS-27-0", "iOS 27.0", "27.0", "iOS", types)
        val oldestFirst = newestFirst.copy(supportedDeviceTypes = types.reversed())

        assertEquals("iPhone 18 Pro", chooseSimulator(SimulatorSettings(), SimulatorSettings(), null, listOf(newestFirst))?.deviceType?.name)
        assertEquals("iPhone 18 Pro", chooseSimulator(SimulatorSettings(), SimulatorSettings(), null, listOf(oldestFirst))?.deviceType?.name)
    }

    @Test
    fun `compares runtime versions numerically`() {
        assertEquals(ios1810, chooseSimulator(SimulatorSettings(), SimulatorSettings(), null, listOf(ios185, ios1810))?.runtime)
    }

    @Test
    fun `this run wins, then the machine setting, then the repository's preference`() {
        val preferred = IosSimulatorProvider(device = "iPhone 16", runtime = "iOS 18.5")

        assertEquals(
            SimulatorChoice(ios185, iphone16),
            chooseSimulator(SimulatorSettings(), SimulatorSettings(), preferred, runtimes),
        )
        assertEquals(
            SimulatorChoice(ios185, iphone16Pro),
            chooseSimulator(SimulatorSettings(), SimulatorSettings(device = "iphone 16 pro"), preferred, runtimes),
        )
        assertEquals(
            SimulatorChoice(ios263, iphone17),
            chooseSimulator(SimulatorSettings("iPhone 17", "iOS 26.3"), SimulatorSettings(device = "iPhone 16 Pro"), preferred, runtimes),
        )
    }

    @Test
    fun `a preference missing here falls back to the default`() {
        val preferred = IosSimulatorProvider(device = "iPhone 12", runtime = "iOS 17.5")

        assertEquals(SimulatorChoice(ios263, iphone17), chooseSimulator(SimulatorSettings(), SimulatorSettings(), preferred, runtimes))
    }

    @Test
    fun `no iOS runtime`() {
        assertNull(chooseSimulator(SimulatorSettings(), SimulatorSettings(), null, listOf(watchOs)))
    }

    @Test
    fun `names the simulator after the repository and the worktree`() {
        assertEquals("ShogunAi · main-repo · TASK-123", worktreeSimulatorName("main-repo", "TASK-123"))
    }
}
