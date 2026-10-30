package app.luxion.shogunai.ui

import app.luxion.shogunai.domain.model.RefreshCadence
import kotlin.time.Duration

enum class WindowActivity {
    FOCUSED,
    UNFOCUSED,
    HIDDEN,
    ;

    val isShown: Boolean get() = this != HIDDEN

    companion object {
        fun of(shown: Boolean, focused: Boolean): WindowActivity = when {
            !shown -> HIDDEN
            focused -> FOCUSED
            else -> UNFOCUSED
        }
    }
}

fun RefreshCadence.checkInterval(activity: WindowActivity): Duration =
    if (activity == WindowActivity.FOCUSED) focusedCheck else unfocusedCheck

fun RefreshCadence.machineViewInterval(activity: WindowActivity): Duration =
    if (activity == WindowActivity.FOCUSED) machineView else maxOf(machineView, unfocusedCheck)
