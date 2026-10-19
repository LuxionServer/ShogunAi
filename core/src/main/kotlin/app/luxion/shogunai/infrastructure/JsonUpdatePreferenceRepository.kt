package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.io.UpdatePreferenceRepository
import app.luxion.shogunai.domain.model.AppVersion
import app.luxion.shogunai.domain.model.UpdatePreferences

class JsonUpdatePreferenceRepository(
    private val settingsFile: JsonSettingsFile = JsonSettingsFile(),
) : UpdatePreferenceRepository {

    override fun load(): UpdatePreferences {
        val settings = settingsFile.read()
        return UpdatePreferences(
            checkAutomatically = settings.checkForUpdates,
            skippedVersion = settings.skippedVersion?.let(AppVersion::parse),
        )
    }

    override fun setCheckAutomatically(enabled: Boolean) {
        settingsFile.update { it.copy(checkForUpdates = enabled) }
    }

    override fun skip(version: AppVersion) {
        settingsFile.update { it.copy(skippedVersion = version.toString()) }
    }
}
