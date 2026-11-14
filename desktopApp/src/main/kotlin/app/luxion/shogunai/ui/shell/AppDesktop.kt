package app.luxion.shogunai.ui.shell

import java.awt.Desktop
import java.awt.desktop.AboutHandler
import java.awt.desktop.PreferencesHandler
import java.awt.desktop.QuitHandler
import java.awt.desktop.SystemEventListener
import java.net.URI

interface AppDesktop {
    fun supports(action: Desktop.Action): Boolean
    fun browse(uri: URI)
    fun setAboutHandler(handler: AboutHandler)
    fun setPreferencesHandler(handler: PreferencesHandler)
    fun setQuitHandler(handler: QuitHandler)
    fun addAppEventListener(listener: SystemEventListener)
}

private class AwtAppDesktop(private val desktop: Desktop) : AppDesktop {
    override fun supports(action: Desktop.Action): Boolean = desktop.isSupported(action)
    override fun browse(uri: URI) = desktop.browse(uri)
    override fun setAboutHandler(handler: AboutHandler) = desktop.setAboutHandler(handler)
    override fun setPreferencesHandler(handler: PreferencesHandler) = desktop.setPreferencesHandler(handler)
    override fun setQuitHandler(handler: QuitHandler) = desktop.setQuitHandler(handler)
    override fun addAppEventListener(listener: SystemEventListener) = desktop.addAppEventListener(listener)
}

fun systemDesktop(supported: Boolean = Desktop.isDesktopSupported()): AppDesktop? =
    if (supported) AwtAppDesktop(Desktop.getDesktop()) else null
