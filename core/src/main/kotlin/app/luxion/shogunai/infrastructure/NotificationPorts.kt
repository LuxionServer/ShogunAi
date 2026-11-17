package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.HostEnvironment
import app.luxion.shogunai.domain.io.NotificationImages
import app.luxion.shogunai.domain.io.RunnerNotificationSource
import app.luxion.shogunai.domain.io.SystemNotifier
import app.luxion.shogunai.domain.model.HostOs
import java.io.File

class NotificationPorts(val notifier: SystemNotifier, val runners: RunnerNotificationSource, val images: NotificationImages)

fun notificationPorts(
    environment: HostEnvironment,
    files: ShogunFiles,
    executor: ShellCommandExecutor,
    resourcesDirectory: String?,
    javaHome: String,
    waitForClick: Boolean,
    windows: () -> SystemNotifier?,
    isFile: (String) -> Boolean = { File(it).isFile },
): NotificationPorts {
    val notifier = chooseSystemNotifier(
        environment.os,
        mac = {
            NotificationHelperLocator.locate(resourcesDirectory, javaHome, environment.home, isFile)
                ?.let { MacHelperNotifier(executor, it, files.appRequests.toString()) }
        },
        linux = { ExecutableLocator(environment).find("notify-send")?.let { NotifySendNotifier(executor, it, waitForClick) } },
        windows = windows,
    )
    val herdr = HerdrConfigFile(environment::variable, environment.home, if (environment.os == HostOs.WINDOWS) "Windows" else "")
    val orca = OrcaNotificationSettings(executor, environment::variable, environment.home, environment.os)
    val images = RenderedNotificationImages(files.notificationImages)
    return NotificationPorts(notifier, CachedRunnerNotifications(herdr::notifiesOnDesktop, orca::notifiedStates), images)
}
