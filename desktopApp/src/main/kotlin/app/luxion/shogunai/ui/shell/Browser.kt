package app.luxion.shogunai.ui.shell

import java.awt.Desktop
import java.net.URI

fun openInBrowser(url: String) {
    if (!Desktop.isDesktopSupported()) return
    val desktop = Desktop.getDesktop()
    if (desktop.isSupported(Desktop.Action.BROWSE)) desktop.browse(URI(url))
}
