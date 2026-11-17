package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.NoSystemNotifier
import app.luxion.shogunai.domain.io.SystemNotification
import app.luxion.shogunai.domain.io.SystemNotifier
import app.luxion.shogunai.domain.model.HostOs
import kotlinx.coroutines.CancellationException
import java.io.File
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

fun chooseSystemNotifier(
    os: HostOs,
    mac: () -> SystemNotifier?,
    linux: () -> SystemNotifier?,
    windows: () -> SystemNotifier?,
): SystemNotifier {
    val notifier = when (os) {
        HostOs.MACOS -> mac()
        HostOs.WINDOWS -> windows()
        HostOs.LINUX -> linux()
    }
    return notifier ?: NoSystemNotifier
}

data class NotificationHelper(val executable: String, val shogunApp: String?)

object NotificationHelperLocator {
    const val HELPER_BUNDLE = "ShogunAi Notifications.app"
    private const val EXECUTABLE = "Contents/MacOS/shogunai-notifier"
    private const val APP_MARKER = ".app/Contents/"
    private const val RESOURCES = "Contents/app/resources"

    fun locate(resourcesDirectory: String?, javaHome: String, userHome: String, isFile: (String) -> Boolean): NotificationHelper? {
        val fromResources = resourcesDirectory?.let { directory ->
            NotificationHelper(File(File(directory, HELPER_BUNDLE), EXECUTABLE).path, shogunAppOf(directory))
        }
        val installed = listOfNotNull(shogunAppOf(javaHome), "/Applications/ShogunAi.app", File(userHome, "Applications/ShogunAi.app").path)
            .map { app -> NotificationHelper(File(File(File(app, RESOURCES), HELPER_BUNDLE), EXECUTABLE).path, app) }
        return (listOfNotNull(fromResources) + installed).firstOrNull { isFile(it.executable) }
    }

    private fun shogunAppOf(path: String): String? {
        val normalized = path.replace('\\', '/')
        return if (APP_MARKER in normalized) normalized.substringBefore(APP_MARKER) + ".app" else null
    }
}

class MacHelperNotifier(
    private val executor: ShellCommandExecutor,
    private val helper: NotificationHelper,
    private val requestsDirectory: String,
) : SystemNotifier {

    override suspend fun show(notification: SystemNotification, onClick: () -> Unit) {
        val command = listOf(
            helper.executable, "post",
            "--title", notification.title,
            "--body", notification.body,
            "--request", notification.click.toJson(),
            "--requests-dir", requestsDirectory,
        ) + listOfNotNull(helper.shogunApp).flatMap { listOf("--app", it) } +
            listOfNotNull(notification.image).flatMap { listOf("--image", it) }
        runQuietly { executor.executeWithin(command, POST_TIMEOUT) }
    }

    private companion object {
        val POST_TIMEOUT = 30.seconds
    }
}

class NotifySendNotifier(
    private val executor: ShellCommandExecutor,
    private val notifySend: String,
    private val waitForClick: Boolean,
) : SystemNotifier {

    override suspend fun show(notification: SystemNotification, onClick: () -> Unit) {
        val action = if (waitForClick) listOf("--action=$CLICK_ACTION=${notification.openLabel}", "--wait") else emptyList()
        val icon = listOfNotNull(notification.image?.let { "--icon=$it" })
        val command = listOf(notifySend, "--app-name=ShogunAi") + icon + action + listOf("--", notification.title, notification.body)
        val result = runQuietly { executor.executeWithin(command, if (waitForClick) WAIT_TIMEOUT else POST_TIMEOUT) } ?: return
        if (waitForClick && result.stdout.lineSequence().any { it.trim() == CLICK_ACTION }) onClick()
    }

    private companion object {
        const val CLICK_ACTION = "default"
        val POST_TIMEOUT = 10.seconds
        val WAIT_TIMEOUT = 12.hours
    }
}

private suspend fun <T> runQuietly(block: suspend () -> T): T? = try {
    block()
} catch (error: CancellationException) {
    throw error
} catch (_: Exception) {
    null
}
