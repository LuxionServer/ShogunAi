package app.luxion.shogunai.ui.tray

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.luxion.shogunai.domain.io.ShellPreferenceRepository

class TrayPreferences(
    private val repository: ShellPreferenceRepository,
    val isSupported: Boolean,
) {
    var showIcon by mutableStateOf(repository.loadShowTrayIcon())
        private set
    var keepRunning by mutableStateOf(repository.loadKeepRunningInTray())
        private set

    val isIconShown: Boolean get() = isSupported && showIcon

    val closeAction: CloseAction get() = closeActionFor(isIconShown, keepRunning)

    fun changeShowIcon(show: Boolean) {
        showIcon = show
        repository.saveShowTrayIcon(show)
    }

    fun changeKeepRunning(keepRunning: Boolean) {
        this.keepRunning = keepRunning
        repository.saveKeepRunningInTray(keepRunning)
    }
}

enum class CloseAction { QUIT, HIDE }

fun closeActionFor(isIconShown: Boolean, keepRunning: Boolean): CloseAction =
    if (isIconShown && keepRunning) CloseAction.HIDE else CloseAction.QUIT

fun isTrayIconSupported(systemTraySupported: Boolean, runsHeadless: Boolean): Boolean = systemTraySupported && !runsHeadless

fun runsHeadless(property: (String) -> String? = System::getProperty): Boolean = property(HEADLESS_PROPERTY) == "true"

const val HEADLESS_PROPERTY = "shogunai.headless"
