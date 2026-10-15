package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.io.LanguagePreferenceRepository
import app.luxion.shogunai.domain.model.AppLanguage

class JsonLanguagePreferenceRepository(
    private val settingsFile: JsonSettingsFile = JsonSettingsFile(),
) : LanguagePreferenceRepository {

    override fun load(): AppLanguage = settingsFile.read().language

    override fun save(language: AppLanguage) {
        settingsFile.update { it.copy(language = language) }
    }
}
