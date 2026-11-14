package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.io.NotificationPreferenceRepository
import app.luxion.shogunai.domain.model.NotificationPreferences

class JsonNotificationPreferenceRepository(
    private val settingsFile: JsonSettingsFile,
) : NotificationPreferenceRepository {

    override fun load(): NotificationPreferences {
        val settings = settingsFile.read()
        return NotificationPreferences(blocked = settings.notifyBlocked, done = settings.notifyDone)
    }

    override fun save(preferences: NotificationPreferences) {
        settingsFile.update { it.copy(notifyBlocked = preferences.blocked, notifyDone = preferences.done) }
    }
}
