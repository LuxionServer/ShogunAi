package app.luxion.shogunai.domain.io

import app.luxion.shogunai.domain.model.ResourceSettings

interface ResourceLimitsRepository {

    fun load(): ResourceSettings

    fun save(settings: ResourceSettings)
}
