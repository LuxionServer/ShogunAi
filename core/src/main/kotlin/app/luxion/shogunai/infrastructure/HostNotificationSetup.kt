package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.HostEnvironment
import app.luxion.shogunai.domain.io.LanguagePreferenceRepository
import app.luxion.shogunai.domain.io.NotificationImages
import app.luxion.shogunai.domain.io.NotificationPreferenceRepository
import app.luxion.shogunai.domain.io.NotificationSetup
import app.luxion.shogunai.domain.io.PendingNotificationStore
import app.luxion.shogunai.domain.io.RunnerNotificationSource
import app.luxion.shogunai.domain.io.SystemNotifier
import kotlinx.coroutines.delay
import java.io.File

class HostNotificationSetup(
    private val environment: HostEnvironment,
    private val files: ShogunFiles,
    private val executor: ShellCommandExecutor,
    private val javaHome: String = System.getProperty("java.home"),
    private val classPath: String = System.getProperty("java.class.path"),
    private val mainClass: String = SHOGUN_MAIN_CLASS,
) : NotificationSetup {
    private val ports by lazy {
        notificationPorts(environment, files, executor, resourcesDirectory = null, javaHome = javaHome, waitForClick = false, windows = { null })
    }
    private val settings by lazy { JsonSettingsFile(files.settings) }

    override val notifier: SystemNotifier get() = ports.notifier
    override val runners: RunnerNotificationSource get() = ports.runners
    override val images: NotificationImages get() = ports.images
    override val pending: PendingNotificationStore by lazy { JsonPendingNotificationStore(files.pendingNotifications) }
    override val preferences: NotificationPreferenceRepository by lazy { JsonNotificationPreferenceRepository(settings) }
    override val language: LanguagePreferenceRepository by lazy { JsonLanguagePreferenceRepository(settings) }

    override fun startInBackground(arguments: List<String>) {
        ProcessBuilder(command(arguments))
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
            .outputStream.close()
    }

    fun command(arguments: List<String>): List<String> {
        val java = File(File(javaHome, "bin"), if (environment.os.isWindows) "java.exe" else "java").path
        return listOf(java, "-cp", classPath, mainClass) + arguments
    }

    override suspend fun pause(millis: Long) = delay(millis)

    private companion object {
        const val SHOGUN_MAIN_CLASS = "app.luxion.shogunai.cli.MainKt"
    }
}
