package app.luxion.shogunai.domain.io

class NotificationServices(
    val preferences: NotificationPreferenceRepository,
    val runners: RunnerNotificationSource,
    val notifier: SystemNotifier,
    val images: NotificationImages,
    val requests: AppRequestChannel,
)
