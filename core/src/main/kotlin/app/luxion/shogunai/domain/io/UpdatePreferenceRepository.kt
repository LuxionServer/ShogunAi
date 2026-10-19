package app.luxion.shogunai.domain.io

import app.luxion.shogunai.domain.model.AppVersion
import app.luxion.shogunai.domain.model.UpdatePreferences

interface UpdatePreferenceRepository {
    fun load(): UpdatePreferences
    fun setCheckAutomatically(enabled: Boolean)
    fun skip(version: AppVersion)
}
