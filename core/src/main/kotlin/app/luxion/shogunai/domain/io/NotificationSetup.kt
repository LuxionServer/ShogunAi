package app.luxion.shogunai.domain.io

interface NotificationSetup {
    val notifier: SystemNotifier
    val runners: RunnerNotificationSource
    val images: NotificationImages
    val pending: PendingNotificationStore
    val preferences: NotificationPreferenceRepository
    val language: LanguagePreferenceRepository
    fun startInBackground(arguments: List<String>)
    suspend fun pause(millis: Long)
}
