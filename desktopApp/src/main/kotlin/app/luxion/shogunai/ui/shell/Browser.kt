package app.luxion.shogunai.ui.shell

import java.awt.Desktop
import java.net.URI

fun openInBrowser(url: String, desktop: AppDesktop? = systemDesktop()) {
    if (desktop?.supports(Desktop.Action.BROWSE) == true) desktop.browse(URI(url))
}
