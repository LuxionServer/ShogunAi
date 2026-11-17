package app.luxion.shogunai.domain.io

import app.luxion.shogunai.domain.model.RunnerNotifications

interface RunnerNotificationSource {
    suspend fun read(): RunnerNotifications
}
