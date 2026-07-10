package app.luxion.shogunai

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import app.luxion.shogunai.ui.AppRoot

fun main() = application {
    val appContainer = AppContainer()
    Window(
        onCloseRequest = ::exitApplication,
        title = "ShogunAi",
    ) {
        AppRoot(appContainer)
    }
}