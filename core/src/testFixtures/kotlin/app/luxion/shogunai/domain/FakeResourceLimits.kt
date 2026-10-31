package app.luxion.shogunai.domain

import app.luxion.shogunai.domain.io.MachineCapacity
import app.luxion.shogunai.domain.io.ResourceLimitsRepository
import app.luxion.shogunai.domain.model.ResourceSettings

class FakeMachineCapacity(
    private val memoryBytes: Long? = 16L * 1024 * 1024 * 1024,
    private val cores: Int = 8,
) : MachineCapacity {
    override fun totalMemoryBytes(): Long? = memoryBytes

    override fun cores(): Int = cores
}

class FakeResourceLimitsRepository(var settings: ResourceSettings = ResourceSettings()) : ResourceLimitsRepository {
    var loadFailure: Exception? = null

    override fun load(): ResourceSettings {
        loadFailure?.let { throw it }
        return settings
    }

    override fun save(settings: ResourceSettings) {
        this.settings = settings
    }
}
