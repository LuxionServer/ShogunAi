package app.luxion.shogunai.domain.io

import app.luxion.shogunai.domain.model.NotificationPreferences

interface NotificationPreferenceRepository {
    fun load(): NotificationPreferences
    fun save(preferences: NotificationPreferences)
}
