package app.luxion.shogunai.domain.io

import app.luxion.shogunai.domain.model.AgentNotification

interface NotificationImages {
    fun imageFor(notification: AgentNotification): String?
}

object NoNotificationImages : NotificationImages {
    override fun imageFor(notification: AgentNotification): String? = null
}
