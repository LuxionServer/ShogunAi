package app.luxion.shogunai.domain

import app.luxion.shogunai.domain.io.NotificationPreferenceRepository
import app.luxion.shogunai.domain.io.RunnerNotificationSource
import app.luxion.shogunai.domain.io.SystemNotification
import app.luxion.shogunai.domain.io.SystemNotifier
import app.luxion.shogunai.domain.model.NotificationPreferences
import app.luxion.shogunai.domain.model.RunnerNotifications

class FakeNotificationPreferenceRepository(var stored: NotificationPreferences = NotificationPreferences()) : NotificationPreferenceRepository {
    override fun load(): NotificationPreferences = stored

    override fun save(preferences: NotificationPreferences) {
        stored = preferences
    }
}

class FakeRunnerNotificationSource(var runners: RunnerNotifications = RunnerNotifications()) : RunnerNotificationSource {
    var reads = 0

    override suspend fun read(): RunnerNotifications {
        reads++
        return runners
    }
}

class RecordingSystemNotifier : SystemNotifier {
    val shown = mutableListOf<SystemNotification>()
    private val clicks = mutableListOf<() -> Unit>()

    override suspend fun show(notification: SystemNotification, onClick: () -> Unit) {
        shown += notification
        clicks += onClick
    }

    fun click(index: Int = shown.lastIndex) = clicks[index]()
}
