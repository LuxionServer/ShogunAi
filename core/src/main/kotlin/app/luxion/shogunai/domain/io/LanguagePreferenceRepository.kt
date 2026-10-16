package app.luxion.shogunai.domain.io

import app.luxion.shogunai.domain.model.AppLanguage

interface LanguagePreferenceRepository {
    fun load(): AppLanguage
    fun save(language: AppLanguage)
}
