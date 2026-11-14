package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.io.SystemNotification
import app.luxion.shogunai.domain.io.SystemNotifier
import java.awt.Image
import java.awt.SystemTray
import java.awt.TrayIcon

interface TrayBalloon {
    fun display(title: String, body: String, onClick: () -> Unit)
}

class TrayNotifier(private val balloon: TrayBalloon) : SystemNotifier {
    override suspend fun show(notification: SystemNotification, onClick: () -> Unit) {
        runCatching { balloon.display(notification.title, notification.body, onClick) }
    }
}

class AwtTrayBalloon(private val image: () -> Image) : TrayBalloon {
    private var icon: TrayIcon? = null
    private var lastClick: () -> Unit = {}

    @Synchronized
    override fun display(title: String, body: String, onClick: () -> Unit) {
        lastClick = onClick
        val trayIcon = icon ?: TrayIcon(image(), "ShogunAi").also { created ->
            created.isImageAutoSize = true
            created.addActionListener { lastClick() }
            SystemTray.getSystemTray().add(created)
            icon = created
        }
        trayIcon.displayMessage(title, body, TrayIcon.MessageType.NONE)
    }
}
