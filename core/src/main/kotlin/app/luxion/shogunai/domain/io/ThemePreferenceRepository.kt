package app.luxion.shogunai.domain.io

import app.luxion.shogunai.domain.model.ThemeMode

interface ThemePreferenceRepository {
    fun load(): ThemeMode
    fun save(mode: ThemeMode)
}
