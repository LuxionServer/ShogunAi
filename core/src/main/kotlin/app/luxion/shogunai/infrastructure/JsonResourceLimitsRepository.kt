package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.io.ResourceLimitsRepository
import app.luxion.shogunai.domain.model.ResourceSettings

class JsonResourceLimitsRepository(
    private val settingsFile: JsonSettingsFile = JsonSettingsFile(),
) : ResourceLimitsRepository {

    override fun load(): ResourceSettings = settingsFile.read().resources

    override fun save(settings: ResourceSettings) {
        settingsFile.update { it.copy(resources = settings) }
    }
}
