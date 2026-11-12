package app.luxion.shogunai.domain.io

import app.luxion.shogunai.domain.model.DeviceLeases
import app.luxion.shogunai.domain.model.PhysicalDeviceListing
import app.luxion.shogunai.domain.model.RecordedDevice

fun interface PhysicalDevices {

    suspend fun list(): PhysicalDeviceListing
}

interface PhysicalDeviceInventoryStore {

    fun read(): List<RecordedDevice>

    fun write(devices: List<RecordedDevice>)
}

interface PhysicalDeviceLeaseStore {

    fun read(): DeviceLeases

    fun write(leases: DeviceLeases)
}
