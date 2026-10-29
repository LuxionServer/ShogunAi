package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.io.ThemePreferenceRepository
import app.luxion.shogunai.domain.model.ThemeMode

class JsonThemePreferenceRepository(
    private val settingsFile: JsonSettingsFile,
) : ThemePreferenceRepository {

    override fun load(): ThemeMode = settingsFile.read().themeMode

    override fun save(mode: ThemeMode) {
        settingsFile.update { it.copy(themeMode = mode) }
    }
}
