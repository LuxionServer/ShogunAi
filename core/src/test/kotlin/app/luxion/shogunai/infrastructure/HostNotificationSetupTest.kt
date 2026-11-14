package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.io.NoSystemNotifier
import app.luxion.shogunai.domain.model.AppLanguage
import app.luxion.shogunai.domain.model.HostOs
import app.luxion.shogunai.domain.model.NotificationPreferences
import app.luxion.shogunai.domain.model.PendingSession
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class HostNotificationSetupTest {
    private val home = Files.createTempDirectory("host-notification-setup").toFile()

    @AfterTest
    fun cleanUp() {
        home.deleteRecursively()
    }

    private fun setup(os: HostOs = HostOs.current(), javaHome: String = "/opt/jdk", mainClass: String = "app.luxion.shogunai.cli.MainKt") =
        HostNotificationSetup(SystemHostEnvironment(os, emptyMap(), home.path, loginShell = null), ShogunFiles(home.path), FakeShellCommandExecutor(), javaHome, "/opt/shogun/lib/*", mainClass)

    @Test
    fun `the background command runs the same JVM and class path`() {
        assertEquals(
            listOf("/opt/jdk/bin/java", "-cp", "/opt/shogun/lib/*", "app.luxion.shogunai.cli.MainKt", "hook", "notify-pending"),
            setup(HostOs.LINUX).command(listOf("hook", "notify-pending")).map { it.replace('\\', '/') },
        )
        assertEquals("/opt/jdk/bin/java.exe", setup(HostOs.WINDOWS).command(emptyList()).first().replace('\\', '/'))
    }

    @Test
    fun `a background command is started without waiting for it`() {
        setup(javaHome = System.getProperty("java.home"), mainClass = "-version").startInBackground(emptyList())
    }

    @Test
    fun `the command line never notifies on Windows`() {
        assertSame(NoSystemNotifier, setup(HostOs.WINDOWS).notifier)
    }

    @Test
    fun `the pending store and preferences live in the ShogunAi folder`() = runTest {
        val setup = setup()
        setup.preferences.save(NotificationPreferences(blocked = false, done = true))
        setup.pending.update { listOf<PendingSession>() to Unit }
        setup.pause(1)
        setup.runners.read()
        assertIs<RenderedNotificationImages>(setup.images)

        assertEquals(NotificationPreferences(blocked = false, done = true), setup.preferences.load())
        assertEquals(AppLanguage.ENGLISH, setup.language.load())
        assertEquals(true, File(home, ".shogunai/settings.json").isFile)
    }
}
