package app.luxion.shogunai

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import app.luxion.shogunai.ui.components.rememberAppIconPainter
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.lifecycle.viewmodel.compose.viewModel
import app.luxion.shogunai.cli.runShogun
import app.luxion.shogunai.ui.components.TextInputFocus
import app.luxion.shogunai.ui.shell.AboutWindow
import app.luxion.shogunai.ui.shell.AppMenuBar
import app.luxion.shogunai.ui.shell.AppShell
import app.luxion.shogunai.ui.shell.Appearance
import app.luxion.shogunai.ui.shell.AppearanceScope
import app.luxion.shogunai.ui.shell.PreferencesWindow
import app.luxion.shogunai.ui.shell.ShellViewModel
import app.luxion.shogunai.ui.shell.installMacAppMenuHandlers
import app.luxion.shogunai.ui.shell.rememberRestoredWindowState
import app.luxion.shogunai.ui.shell.saveWindowBounds
import java.awt.AWTEvent
import java.awt.Dimension
import java.awt.Taskbar
import java.awt.Toolkit
import java.awt.Window as AwtWindow
import java.awt.event.AWTEventListener
import java.awt.event.WindowEvent
import javax.swing.JFrame
import javax.swing.SwingUtilities
import kotlin.system.exitProcess

private val MinimumWindowSize = Dimension(720, 480)

fun main(args: Array<String>) {
    val cliArguments = args.filterNot { it.startsWith("-psn_") }
    if (cliArguments.isNotEmpty()) exitProcess(runShogun(cliArguments.toTypedArray()))
    System.setProperty("apple.laf.useScreenMenuBar", "true")
    System.setProperty("apple.awt.application.name", "ShogunAi")
    openWindow()
}

private fun openWindow() = application {
    val appContainer = remember { AppContainer() }
    val appearance = remember {
        Appearance(appContainer.themePreferenceRepository, appContainer.languagePreferenceRepository)
    }
    var preferencesOpen by remember { mutableStateOf(false) }
    var preferencesFrontRequests by remember { mutableIntStateOf(0) }
    var aboutOpen by remember { mutableStateOf(false) }
    val openPreferences: () -> Unit = {
        if (preferencesOpen) preferencesFrontRequests++ else preferencesOpen = true
    }
    val windowState = rememberRestoredWindowState(appContainer.shellPreferenceRepository)
    val quit = {
        saveWindowBounds(windowState, appContainer.shellPreferenceRepository)
        exitApplication()
    }
    val shellViewModelHolder = remember { mutableStateOf<ShellViewModel?>(null) }
    val requestQuit = { shellViewModelHolder.value?.requestLeave(quit) ?: quit() }
    val mainWindowHolder = remember { mutableStateOf<AwtWindow?>(null) }
    val focusMainWindow: () -> Unit = {
        mainWindowHolder.value?.takeIf { it.isShowing }?.let { window ->
            window.toFront()
            window.requestFocus()
            (window as? JFrame)?.let { frame ->
                val menuBar = frame.jMenuBar
                frame.jMenuBar = null
                frame.jMenuBar = menuBar
            }
        }
    }
    DisposableEffect(Unit) {
        val closedWindowListener = AWTEventListener { event ->
            val closed = event as? WindowEvent ?: return@AWTEventListener
            if (closed.id == WindowEvent.WINDOW_CLOSED && closed.window !== mainWindowHolder.value) {
                SwingUtilities.invokeLater { focusMainWindow() }
            }
        }
        Toolkit.getDefaultToolkit().addAWTEventListener(closedWindowListener, AWTEvent.WINDOW_EVENT_MASK)
        onDispose { Toolkit.getDefaultToolkit().removeAWTEventListener(closedWindowListener) }
    }
    DisposableEffect(Unit) {
        setDockIcon()
        installMacAppMenuHandlers(
            onAbout = { aboutOpen = true },
            onPreferences = openPreferences,
            onQuit = requestQuit,
        )
        onDispose { }
    }

    val escapeWorktreeView = remember { mutableStateOf<(() -> Unit)?>(null) }
    Window(
        onCloseRequest = requestQuit,
        state = windowState,
        title = "ShogunAi",
        icon = rememberAppIconPainter(),
        onPreviewKeyEvent = { event ->
            if (event.key == Key.Escape) escapeWorktreeView.value?.invoke()
            false
        },
    ) {
        LaunchedEffect(Unit) { window.minimumSize = MinimumWindowSize }
        LaunchedEffect(Unit) { appContainer.tools.refresh() }
        SideEffect { mainWindowHolder.value = window }
        val shellViewModel = viewModel {
            ShellViewModel(
                appContainer.projectRepository,
                appContainer.shellPreferenceRepository,
                appContainer::settingsProblem,
                appContainer::acknowledgeSettingsProblem,
            )
        }
        SideEffect { shellViewModelHolder.value = shellViewModel }
        val worktreeActions = shellViewModel.worktreeActions
        SideEffect { escapeWorktreeView.value = worktreeActions?.onEscape }
        AppMenuBar(
            strings = appearance.strings,
            onSave = shellViewModel::requestSaveSettings.takeIf { shellViewModel.canSaveSettings },
            onAddProject = shellViewModel::addProject,
            onNewWorktree = shellViewModel::requestNewWorktree.takeIf { shellViewModel.canCreateWorktree },
            onFind = worktreeActions?.onFind,
            onCopySelectedLaunchCommand = worktreeActions?.onCopySelectedLaunchCommand,
            onDeleteSelectedWorktree = worktreeActions?.onDeleteSelectedWorktree,
            onClearSelection = worktreeActions?.onClearSelection,
            hasWorktreeSelection = worktreeActions?.hasSelection?.invoke() == true,
            isEditingText = TextInputFocus.isActive,
            onShowActive = shellViewModel::selectActive.takeIf { shellViewModel.projects.isNotEmpty() },
            onShowAll = shellViewModel::selectAll.takeIf { shellViewModel.projects.isNotEmpty() },
            onReload = shellViewModel::reload,
            onCommandPalette = shellViewModel::openPalette,
            onToggleSidebar = shellViewModel::toggleSidebar,
            onPreferences = openPreferences,
            onAbout = { aboutOpen = true },
            onQuit = requestQuit,
        )
        AppearanceScope(appearance) {
            AppShell(appContainer, shellViewModel, openPreferences)
        }
    }

    if (preferencesOpen) {
        PreferencesWindow(appearance, appContainer.tools, preferencesFrontRequests, onClose = { preferencesOpen = false })
    }
    if (aboutOpen) {
        AboutWindow(appearance, onClose = { aboutOpen = false })
    }
}

private fun setDockIcon() {
    if (!Taskbar.isTaskbarSupported()) return
    val taskbar = Taskbar.getTaskbar()
    if (!taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) return
    val iconUrl = Thread.currentThread().contextClassLoader.getResource("icons/icon.png") ?: return
    taskbar.iconImage = Toolkit.getDefaultToolkit().createImage(iconUrl)
}
