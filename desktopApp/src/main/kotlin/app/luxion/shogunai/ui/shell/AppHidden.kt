package app.luxion.shogunai.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import java.awt.Desktop
import java.awt.desktop.AppHiddenEvent
import java.awt.desktop.AppHiddenListener

@Composable
fun rememberAppHidden(): State<Boolean> {
    val hidden = remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        val desktop = if (Desktop.isDesktopSupported()) Desktop.getDesktop().takeIf { it.isSupported(Desktop.Action.APP_EVENT_HIDDEN) } else null
        val listener = object : AppHiddenListener {
            override fun appHidden(event: AppHiddenEvent) {
                hidden.value = true
            }

            override fun appUnhidden(event: AppHiddenEvent) {
                hidden.value = false
            }
        }
        desktop?.addAppEventListener(listener)
        onDispose { desktop?.removeAppEventListener(listener) }
    }
    return hidden
}
