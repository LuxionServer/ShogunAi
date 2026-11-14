package app.luxion.shogunai.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import app.luxion.shogunai.domain.io.ShellPreferenceRepository
import app.luxion.shogunai.domain.model.WindowBounds
import java.awt.GraphicsEnvironment
import java.awt.HeadlessException
import java.awt.Rectangle

private val DefaultWindowSize = DpSize(1200.dp, 800.dp)

@Composable
fun rememberRestoredWindowState(preferences: ShellPreferenceRepository): WindowState = remember {
    val saved = preferences.loadWindowBounds()
    WindowState(
        placement = if (saved?.maximized == true) WindowPlacement.Maximized else WindowPlacement.Floating,
        position = saved?.takeIf(::isOnAnyDisplay)?.let { WindowPosition(it.x.dp, it.y.dp) }
            ?: WindowPosition(Alignment.Center),
        size = saved?.let { DpSize(it.width.dp, it.height.dp) } ?: DefaultWindowSize,
    )
}

fun saveWindowBounds(state: WindowState, preferences: ShellPreferenceRepository) {
    val maximized = state.placement == WindowPlacement.Maximized
    val previous = preferences.loadWindowBounds()
    val position = state.position
    val bounds = when {
        maximized && previous != null -> previous.copy(maximized = true)
        position is WindowPosition.Absolute -> WindowBounds(
            x = position.x.value.toInt(),
            y = position.y.value.toInt(),
            width = state.size.width.value.toInt(),
            height = state.size.height.value.toInt(),
            maximized = maximized,
        )
        else -> return
    }
    preferences.saveWindowBounds(bounds)
}

internal fun isOnAnyDisplay(bounds: WindowBounds, screens: List<Rectangle> = screenBounds()): Boolean {
    val window = Rectangle(bounds.x, bounds.y, bounds.width, bounds.height)
    return screens.any { it.intersects(window) }
}

internal fun screenBounds(environment: GraphicsEnvironment = GraphicsEnvironment.getLocalGraphicsEnvironment()): List<Rectangle> = try {
    environment.screenDevices.map { it.defaultConfiguration.bounds }
} catch (_: HeadlessException) {
    emptyList()
}
