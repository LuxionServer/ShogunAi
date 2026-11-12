package app.luxion.shogunai.domain

import app.luxion.shogunai.domain.io.PhysicalDeviceInventoryStore
import app.luxion.shogunai.domain.io.PhysicalDeviceLeaseStore
import app.luxion.shogunai.domain.io.PhysicalDevices
import app.luxion.shogunai.domain.model.DeviceLeases
import app.luxion.shogunai.domain.model.PhysicalDevice
import app.luxion.shogunai.domain.model.PhysicalDeviceListing
import app.luxion.shogunai.domain.model.RecordedDevice

class FakePhysicalDevices(var listing: PhysicalDeviceListing = PhysicalDeviceListing()) : PhysicalDevices {

    var lists = 0
        private set

    var connected: List<PhysicalDevice>
        get() = listing.devices
        set(value) {
            listing = listing.copy(devices = value)
        }

    override suspend fun list(): PhysicalDeviceListing {
        lists++
        return listing
    }
}

class FakePhysicalDeviceInventoryStore(var devices: List<RecordedDevice> = emptyList()) : PhysicalDeviceInventoryStore {

    var writes = 0
        private set

    override fun read(): List<RecordedDevice> = devices

    override fun write(devices: List<RecordedDevice>) {
        writes++
        this.devices = devices
    }
}

class FakePhysicalDeviceLeaseStore(var leases: DeviceLeases = DeviceLeases()) : PhysicalDeviceLeaseStore {

    override fun read(): DeviceLeases = leases

    override fun write(leases: DeviceLeases) {
        this.leases = leases
    }
}
