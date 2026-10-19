package app.luxion.shogunai.domain.io

import app.luxion.shogunai.domain.model.AppVersion
import app.luxion.shogunai.domain.model.UpdatePreferences
import java.time.Instant

interface UpdatePreferenceRepository {
    fun load(): UpdatePreferences
    fun setCheckAutomatically(enabled: Boolean)
    fun recordCheck(at: Instant)
    fun skip(version: AppVersion)
}
