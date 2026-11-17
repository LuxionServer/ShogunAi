package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.HostEnvironment
import app.luxion.shogunai.domain.io.NotificationServices
import app.luxion.shogunai.domain.model.AppLanguage
import app.luxion.shogunai.ui.notifications.AgentNotificationWatch
import app.luxion.shogunai.ui.tray.trayIconImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

fun desktopNotificationServices(
    environment: HostEnvironment,
    files: ShogunFiles,
    executor: ShellCommandExecutor,
    settingsFile: JsonSettingsFile,
    property: (String) -> String? = System::getProperty,
): NotificationServices {
    val ports = notificationPorts(
        environment,
        files,
        executor,
        resourcesDirectory = property("compose.application.resources.dir"),
        javaHome = property("java.home").orEmpty(),
        waitForClick = true,
        windows = { TrayNotifier(AwtTrayBalloon { trayIconImage(darkMenuBar = false) }) },
    )
    return NotificationServices(JsonNotificationPreferenceRepository(settingsFile), ports.runners, ports.notifier, ports.images, FileAppRequestChannel(files.appRequests))
}

fun desktopAgentNotifications(
    environment: HostEnvironment,
    files: ShogunFiles,
    executor: ShellCommandExecutor,
    settingsFile: JsonSettingsFile,
    language: () -> AppLanguage,
): AgentNotificationWatch =
    AgentNotificationWatch(desktopNotificationServices(environment, files, executor, settingsFile), language, CoroutineScope(SupervisorJob() + Dispatchers.IO))
