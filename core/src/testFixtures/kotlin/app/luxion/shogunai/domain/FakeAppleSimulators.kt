package app.luxion.shogunai.domain

import app.luxion.shogunai.domain.io.AppleSimulators
import app.luxion.shogunai.domain.model.SimulatorDevice
import app.luxion.shogunai.domain.model.SimulatorDeviceType
import app.luxion.shogunai.domain.model.SimulatorRuntime
import app.luxion.shogunai.domain.model.SimulatorState

class FakeAppleSimulators(
    var supported: Boolean = true,
    var installedRuntimes: List<SimulatorRuntime> = listOf(IOS_18_5),
    private val bootAfterPolls: Int = 1,
) : AppleSimulators {

    data class Created(val name: String, val deviceType: String, val runtime: String)

    val devicesByUdid = linkedMapOf<String, SimulatorDevice>()
    val created = mutableListOf<Created>()
    val booted = mutableListOf<String>()
    val shown = mutableListOf<String>()
    var showFailure: Throwable? = null
    var shutdownFailure: Throwable? = null
    var devicesFailure: Throwable? = null
    var deleteFailure: Throwable? = null
    val shutDown = mutableListOf<String>()
    val deleted = mutableListOf<String>()
    var calls = 0
        private set
    private var pollsSinceBoot = 0

    var lockProbe: () -> Boolean = { false }
    val callsUnderLock = mutableListOf<String>()
    var afterListing: (suspend () -> Unit)? = null
    var onCreate: (suspend (String) -> Unit)? = null
    var createFailure: Throwable? = null

    private fun observe(call: String) {
        calls++
        if (lockProbe()) callsUnderLock += call
    }

    override fun isSupported(): Boolean = supported

    override suspend fun runtimes(): List<SimulatorRuntime> {
        observe("runtimes")
        return installedRuntimes
    }

    override suspend fun devices(): List<SimulatorDevice> {
        observe("devices")
        devicesFailure?.let { throw it }
        val booting = devicesByUdid.values.filter { it.state == SimulatorState.BOOTING }
        if (booting.isNotEmpty() && ++pollsSinceBoot > bootAfterPolls) {
            booting.forEach { devicesByUdid[it.udid] = it.copy(state = SimulatorState.BOOTED) }
        }
        val listed = devicesByUdid.values.toList()
        afterListing?.also { afterListing = null }?.invoke()
        return listed
    }

    override suspend fun create(name: String, deviceTypeIdentifier: String, runtimeIdentifier: String): String {
        observe("create")
        createFailure?.let { throw it }
        created += Created(name, deviceTypeIdentifier, runtimeIdentifier)
        val udid = "UDID-${created.size}"
        devicesByUdid[udid] = SimulatorDevice(udid, name, SimulatorState.SHUTDOWN)
        onCreate?.also { onCreate = null }?.invoke(udid)
        return udid
    }

    override suspend fun boot(udid: String) {
        observe("boot")
        booted += udid
        devicesByUdid[udid]?.let { devicesByUdid[udid] = it.copy(state = SimulatorState.BOOTING) }
        pollsSinceBoot = 0
    }

    override suspend fun show(udid: String) {
        observe("show")
        showFailure?.let { throw it }
        shown += udid
    }

    override suspend fun shutdown(udid: String) {
        observe("shutdown")
        shutdownFailure?.let { throw it }
        shutDown += udid
        devicesByUdid[udid]?.let { devicesByUdid[udid] = it.copy(state = SimulatorState.SHUTDOWN) }
    }

    override suspend fun delete(udid: String) {
        observe("delete")
        deleteFailure?.let { throw it }
        deleted += udid
        devicesByUdid.remove(udid)
    }

    fun addExisting(udid: String, name: String, state: SimulatorState = SimulatorState.SHUTDOWN) {
        devicesByUdid[udid] = SimulatorDevice(udid, name, state)
    }

    companion object {
        val IPHONE_16 = SimulatorDeviceType("com.apple.CoreSimulator.SimDeviceType.iPhone-16", "iPhone 16", "iPhone")
        val IOS_18_5 = SimulatorRuntime(
            "com.apple.CoreSimulator.SimRuntime.iOS-18-5",
            "iOS 18.5",
            "18.5",
            "iOS",
            listOf(IPHONE_16),
        )
    }
}
