package app.luxion.shogunai.ui.shell

import java.awt.Desktop
import java.awt.desktop.AppReopenedEvent
import java.awt.desktop.AppReopenedListener

fun installMacAppMenuHandlers(
    onAbout: () -> Unit,
    onPreferences: () -> Unit,
    onQuit: () -> Unit,
    onReopen: () -> Unit,
    desktop: AppDesktop? = systemDesktop(),
) {
    if (desktop == null) return
    if (desktop.supports(Desktop.Action.APP_ABOUT)) desktop.setAboutHandler { onAbout() }
    if (desktop.supports(Desktop.Action.APP_PREFERENCES)) desktop.setPreferencesHandler { onPreferences() }
    if (desktop.supports(Desktop.Action.APP_QUIT_HANDLER)) {
        desktop.setQuitHandler { _, response ->
            response.cancelQuit()
            onQuit()
        }
    }
    if (desktop.supports(Desktop.Action.APP_EVENT_REOPENED)) {
        desktop.addAppEventListener(object : AppReopenedListener {
            override fun appReopened(event: AppReopenedEvent) = onReopen()
        })
    }
}
