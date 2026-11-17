package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.model.AppLanguage
import app.luxion.shogunai.domain.model.AppRequest
import app.luxion.shogunai.domain.model.HostOs
import app.luxion.shogunai.domain.model.NotificationPreferences
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopNotificationServicesTest {
    private val home = Files.createTempDirectory("desktop-notifications").toFile()
    private val files = ShogunFiles(home.path)

    @AfterTest
    fun cleanUp() {
        home.deleteRecursively()
    }

    @Test
    fun `the watch reads the saved switches and a click leaves an open-worktree request`() {
        val environment = SystemHostEnvironment(HostOs.LINUX, emptyMap(), home.path, loginShell = null)
        val settings = JsonSettingsFile(files.settings)
        JsonNotificationPreferenceRepository(settings).save(NotificationPreferences(blocked = false, done = true))

        val watch = desktopAgentNotifications(environment, files, FakeShellCommandExecutor(), settings) { AppLanguage.ENGLISH }
        watch.onClick(AppRequest.openWorktree(1_000, "api", "/home/dev/projects/TASK-1"))

        assertEquals(NotificationPreferences(blocked = false, done = true), watch.preferences)
        assertEquals(listOf(AppRequest.openWorktree(1_000, "api", "/home/dev/projects/TASK-1")), FileAppRequestChannel(files.appRequests).receive(1_000))
    }

    @Test
    fun `the resources folder and Java home come from the system properties`() {
        val asked = mutableListOf<String>()
        val environment = SystemHostEnvironment(HostOs.LINUX, emptyMap(), home.path, loginShell = null)

        desktopNotificationServices(environment, files, FakeShellCommandExecutor(), JsonSettingsFile(files.settings)) { asked += it; null }

        assertEquals(listOf("compose.application.resources.dir", "java.home"), asked)
    }
}
