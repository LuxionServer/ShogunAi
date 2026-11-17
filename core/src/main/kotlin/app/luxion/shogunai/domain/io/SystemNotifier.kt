package app.luxion.shogunai.domain.io

import app.luxion.shogunai.domain.model.AppRequest

data class SystemNotification(
    val title: String,
    val body: String,
    val openLabel: String,
    val click: AppRequest,
    val image: String? = null,
)

interface SystemNotifier {
    suspend fun show(notification: SystemNotification, onClick: () -> Unit)
}

object NoSystemNotifier : SystemNotifier {
    override suspend fun show(notification: SystemNotification, onClick: () -> Unit) = Unit
}
