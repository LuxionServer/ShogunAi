package app.luxion.shogunai

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalClipboard
import app.luxion.shogunai.diagnostics.SecondaryWindowErrorHandler
import app.luxion.shogunai.diagnostics.UiFreezeWatchdog
import app.luxion.shogunai.diagnostics.asWindowExceptionHandlerFactory
import app.luxion.shogunai.diagnostics.copyDiagnosticReport
import app.luxion.shogunai.diagnostics.diagnosticsExceptionHandler
import app.luxion.shogunai.diagnostics.installUncaughtExceptionLogging
import app.luxion.shogunai.domain.io.DiagnosticsLog
import app.luxion.shogunai.ui.components.LocalDiagnosticsLog
import app.luxion.shogunai.ui.components.copyText
import app.luxion.shogunai.ui.shell.rememberAppMessages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.input.key.key
import app.luxion.shogunai.ui.components.rememberAppIconPainter
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.LocalWindowExceptionHandlerFactory
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
import app.luxion.shogunai.ui.shell.ShellDestination
import app.luxion.shogunai.ui.shell.rememberRestoredWindowState
import app.luxion.shogunai.ui.shell.saveWindowBounds
import app.luxion.shogunai.ui.tray.CloseAction
import app.luxion.shogunai.ui.tray.ShogunTray
import app.luxion.shogunai.ui.tray.TrayAction
import app.luxion.shogunai.ui.tray.TrayLaunchWatch
import app.luxion.shogunai.ui.tray.TrayPreferences
import app.luxion.shogunai.ui.tray.buildTrayMenu
import app.luxion.shogunai.ui.tray.rememberTrayIconPainter
import app.luxion.shogunai.ui.worktree.WorktreeBoard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.awt.AWTEvent
import java.awt.Desktop
import java.awt.Dimension
import java.awt.SystemTray
import java.awt.Taskbar
import java.awt.Toolkit
import java.awt.Window as AwtWindow
import java.awt.event.AWTEventListener
import java.awt.event.WindowEvent
import javax.swing.JFrame
import javax.swing.SwingUtilities
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.hours

private val MinimumWindowSize = Dimension(720, 480)
private val UpdateCheckInterval = 24.hours

fun main(args: Array<String>) {
    val cliArguments = args.filterNot { it.startsWith("-psn_") }
    if (cliArguments.isNotEmpty()) exitProcess(runShogun(cliArguments.toTypedArray()))
    System.setProperty("apple.laf.useScreenMenuBar", "true")
    System.setProperty("apple.awt.application.name", "ShogunAi")
    openWindow()
}

@OptIn(ExperimentalComposeUiApi::class)
private fun openWindow() = application {
    val appContainer = remember { AppContainer() }
    remember(appContainer) { installDiagnostics(appContainer.diagnosticsLog) }
    val diagnosticsHandler = remember(appContainer) { diagnosticsExceptionHandler(appContainer.diagnosticsLog) }
    val appearance = remember {
        Appearance(appContainer.themePreferenceRepository, appContainer.languagePreferenceRepository)
    }
    val messages = rememberAppMessages()
    var preferencesOpen by remember { mutableStateOf(false) }
    var preferencesFrontRequests by remember { mutableIntStateOf(0) }
    var aboutOpen by remember { mutableStateOf(false) }
    val openPreferences: () -> Unit = {
        if (preferencesOpen) preferencesFrontRequests++ else preferencesOpen = true
    }
    val reportSecondaryWindowError = { messages.show(appearance.strings.secondaryWindowError) }
    val preferencesErrorHandlers = SecondaryWindowErrorHandler(
        appContainer.diagnosticsLog,
        onClosed = { preferencesOpen = false },
        onReport = reportSecondaryWindowError,
    ).asWindowExceptionHandlerFactory()
    val aboutErrorHandlers = SecondaryWindowErrorHandler(
        appContainer.diagnosticsLog,
        onClosed = { aboutOpen = false },
        onReport = reportSecondaryWindowError,
    ).asWindowExceptionHandlerFactory()
    val updateScope = rememberCoroutineScope { diagnosticsHandler }
    val checkForUpdates: () -> Unit = { updateScope.launch { appContainer.updates.checkNow() } }
    val windowState = rememberRestoredWindowState(appContainer.shellPreferenceRepository)
    val quit = {
        saveWindowBounds(windowState, appContainer.shellPreferenceRepository)
        exitApplication()
    }
    val shellViewModelHolder = remember { mutableStateOf<ShellViewModel?>(null) }
    val boardHolder = remember { mutableStateOf<WorktreeBoard?>(null) }
    val trayPreferences = remember {
        TrayPreferences(appContainer.shellPreferenceRepository, isSupported = SystemTray.isSupported())
    }
    var windowVisible by remember { mutableStateOf(true) }
    var showWindowRequests by remember { mutableIntStateOf(0) }
    val showMainWindow: () -> Unit = {
        windowVisible = true
        showWindowRequests++
    }
    val requestQuit = {
        shellViewModelHolder.value?.let { shellViewModel ->
            shellViewModel.requestLeave(quit)
            if (shellViewModel.isLeaveConfirmationPending) showMainWindow()
        } ?: quit()
    }
    val closeMainWindow = {
        when (trayPreferences.closeAction) {
            CloseAction.QUIT -> requestQuit()
            CloseAction.HIDE -> shellViewModelHolder.value?.requestLeave { windowVisible = false } ?: run { windowVisible = false }
        }
    }
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
    LaunchedEffect(showWindowRequests) {
        if (showWindowRequests == 0) return@LaunchedEffect
        windowState.isMinimized = false
        withFrameNanos { }
        focusMainWindow()
        requestAppForeground()
    }
    LaunchedEffect(trayPreferences.isIconShown) {
        if (!trayPreferences.isIconShown) windowVisible = true
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
            onReopen = showMainWindow,
        )
        onDispose { }
    }

    val escapeWorktreeView = remember { mutableStateOf<(() -> Unit)?>(null) }
    val trayLaunches = remember { TrayLaunchWatch() }
    val shellViewModelForTray = shellViewModelHolder.value
    val boardForTray = boardHolder.value
    if (trayPreferences.isIconShown && shellViewModelForTray != null && boardForTray != null) {
        val strings = appearance.strings
        val entries = buildTrayMenu(
            strings = strings,
            recentProjects = shellViewModelForTray.recentProjects.mapNotNull { id ->
                shellViewModelForTray.projects.firstOrNull { it.id == id }
            },
            recentWorktrees = boardForTray.trayWorktrees(shellViewModelForTray.recentWorktrees, strings),
        )
        ShogunTray(
            entries = entries,
            icon = rememberTrayIconPainter(),
            onAction = { action ->
                runTrayAction(
                    action = action,
                    viewModel = shellViewModelForTray,
                    board = boardForTray,
                    launches = trayLaunches,
                    showMainWindow = showMainWindow,
                    openPreferences = openPreferences,
                    quit = requestQuit,
                )
            },
        )
        val launchErrors = boardForTray.actionErrors
        LaunchedEffect(launchErrors) {
            val failed = trayLaunches.failures(launchErrors)
            if (failed.isEmpty()) return@LaunchedEffect
            showMainWindow()
            shellViewModelForTray.navigate(ShellDestination.All()) { boardForTray.revealInAllWorktrees(failed.last()) }
        }
        LaunchedEffect(boardForTray.terminalChoice) {
            if (boardForTray.terminalChoice != null) showMainWindow()
        }
    }
    Window(
        visible = windowVisible,
        onCloseRequest = closeMainWindow,
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
        LaunchedEffect(Unit) {
            while (true) {
                appContainer.updates.checkInBackground()
                delay(UpdateCheckInterval)
            }
        }
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
        val clipboard = LocalClipboard.current
        val reportScope = rememberCoroutineScope { diagnosticsHandler }
        val copiedLabel = appearance.strings.copied
        val copyDiagnosticReport: () -> Unit = {
            reportScope.launch {
                copyDiagnosticReport(
                    build = { withContext(Dispatchers.IO) { appContainer.diagnosticReport.build() } },
                    copy = { clipboard.copyText(it) },
                    confirm = { messages.show(copiedLabel) },
                )
            }
        }
        val worktreeActions = shellViewModel.worktreeActions
        SideEffect { escapeWorktreeView.value = worktreeActions?.onEscape }
        AppMenuBar(
            strings = appearance.strings,
            onSave = shellViewModel::requestSaveSettings.takeIf { shellViewModel.canSaveSettings },
            onAddProject = shellViewModel::addProject,
            onNewWorktree = shellViewModel::requestNewWorktree.takeIf { shellViewModel.canCreateWorktree },
            onFind = worktreeActions?.onFind,
            onCopySelectedLaunchCommand = worktreeActions?.onCopySelectedLaunchCommand,
            onOpenSelectedInHerdr = worktreeActions?.onOpenSelectedInHerdr,
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
            onCheckForUpdates = checkForUpdates,
            onCopyDiagnosticReport = copyDiagnosticReport,
            onAbout = { aboutOpen = true },
            onQuit = requestQuit,
        )
        CompositionLocalProvider(LocalDiagnosticsLog provides appContainer.diagnosticsLog) {
            AppearanceScope(appearance) {
                AppShell(appContainer, shellViewModel, openPreferences, onBoardReady = { boardHolder.value = it }, messages = messages)
            }
        }
    }

    if (preferencesOpen) {
        CompositionLocalProvider(
            LocalDiagnosticsLog provides appContainer.diagnosticsLog,
            LocalWindowExceptionHandlerFactory provides preferencesErrorHandlers,
        ) {
            PreferencesWindow(appearance, appContainer.tools, appContainer.resources, appContainer.claudeHooks, appContainer.updates, trayPreferences, preferencesFrontRequests, onClose = { preferencesOpen = false })
        }
    }
    if (aboutOpen) {
        CompositionLocalProvider(LocalWindowExceptionHandlerFactory provides aboutErrorHandlers) {
            AboutWindow(appearance, onClose = { aboutOpen = false })
        }
    }
}

private fun installDiagnostics(log: DiagnosticsLog) {
    installUncaughtExceptionLogging(log)
    var eventThread: Thread? = null
    SwingUtilities.invokeLater { eventThread = Thread.currentThread() }
    UiFreezeWatchdog(log, post = SwingUtilities::invokeLater, uiThread = { eventThread }).start()
}

private fun runTrayAction(
    action: TrayAction,
    viewModel: ShellViewModel,
    board: WorktreeBoard,
    launches: TrayLaunchWatch,
    showMainWindow: () -> Unit,
    openPreferences: () -> Unit,
    quit: () -> Unit,
) {
    when (action) {
        TrayAction.ShowWindow -> showMainWindow()
        TrayAction.NewWorktree -> {
            showMainWindow()
            if (viewModel.canCreateWorktree) board.openCreateDialog() else viewModel.navigate(ShellDestination.All()) { board.openCreateDialog() }
        }
        is TrayAction.OpenProject -> {
            showMainWindow()
            viewModel.projects.firstOrNull { it.id == action.projectId }?.let(viewModel::selectProject)
        }
        is TrayAction.ShowWorktree -> {
            showMainWindow()
            viewModel.navigate(ShellDestination.All()) { board.revealInAllWorktrees(action.ref) }
        }
        is TrayAction.CopyLaunchCommand -> {
            launches.started(action.ref, board.actionErrors[action.ref])
            board.copyLaunchCommand(action.ref)
        }
        is TrayAction.Open -> {
            launches.started(action.ref, board.actionErrors[action.ref])
            board.open(action.ref, action.kind)
        }
        TrayAction.Preferences -> openPreferences()
        TrayAction.Quit -> quit()
    }
}

private fun requestAppForeground() {
    if (!Desktop.isDesktopSupported()) return
    val desktop = Desktop.getDesktop()
    if (desktop.isSupported(Desktop.Action.APP_REQUEST_FOREGROUND)) desktop.requestForeground(true)
}

private fun setDockIcon() {
    if (!Taskbar.isTaskbarSupported()) return
    val taskbar = Taskbar.getTaskbar()
    if (!taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) return
    val iconUrl = Thread.currentThread().contextClassLoader.getResource("icons/icon.png") ?: return
    taskbar.iconImage = Toolkit.getDefaultToolkit().createImage(iconUrl)
}
