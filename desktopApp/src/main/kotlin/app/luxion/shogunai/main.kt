package app.luxion.shogunai

import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import app.luxion.shogunai.ui.AppRoot
import java.awt.Taskbar
import java.awt.Toolkit

fun main() = application {
    setDockIcon()
    val appContainer = AppContainer()
    Window(
        onCloseRequest = ::exitApplication,
        title = "ShogunAi",
        icon = painterResource("icons/icon.png"),
    ) {
        AppRoot(appContainer)
    }
}

// -Xdock:icon (via nativeDistributions.macOS.iconFile) isn't reliable across all
// launchers/JDKs; Taskbar.setIconImage works at runtime regardless of how the process was launched.
private fun setDockIcon() {
    if (!Taskbar.isTaskbarSupported()) return
    val taskbar = Taskbar.getTaskbar()
    if (!taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) return
    val iconUrl = Thread.currentThread().contextClassLoader.getResource("icons/icon.png") ?: return
    taskbar.iconImage = Toolkit.getDefaultToolkit().createImage(iconUrl)
}