package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.io.HostEnvironment
import app.luxion.shogunai.domain.io.HostPath
import app.luxion.shogunai.domain.io.NoSystemNotifier
import app.luxion.shogunai.domain.io.SystemNotification
import app.luxion.shogunai.domain.io.SystemNotifier
import app.luxion.shogunai.domain.model.HostOs
import app.luxion.shogunai.domain.model.HostPathSource
import app.luxion.shogunai.domain.model.RunnerNotifications
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class NotificationPortsTest {
    private val home = Files.createTempDirectory("notification-ports").toFile()
    private val bin = File(home, "bin").apply { mkdirs() }

    @AfterTest
    fun cleanUp() {
        home.deleteRecursively()
    }

    private fun environment(os: HostOs) = object : HostEnvironment {
        override val os = os
        override val home = this@NotificationPortsTest.home.path
        override val shell: String? = null
        override val path = HostPath(listOf(bin.path), HostPathSource.PROCESS)
        override val userBinFolders: List<String> = emptyList()
        override fun variable(name: String): String? = null
    }

    private object Tray : SystemNotifier {
        override suspend fun show(notification: SystemNotification, onClick: () -> Unit) = Unit
    }

    private fun ports(os: HostOs, isFile: (String) -> Boolean = { File(it).isFile }) =
        notificationPorts(environment(os), ShogunFiles(home.path), FakeShellCommandExecutor(), null, "/opt/jdk", waitForClick = true, windows = { Tray }, isFile = isFile)

    @Test
    fun `macOS posts through the helper when it is installed`() {
        assertIs<MacHelperNotifier>(ports(HostOs.MACOS) { it.startsWith("/Applications/") }.notifier)
        assertSame(NoSystemNotifier, ports(HostOs.MACOS) { false }.notifier)
    }

    @Test
    fun `Linux posts with notify-send when it is on the path`() {
        assertSame(NoSystemNotifier, ports(HostOs.LINUX).notifier)
        File(bin, "notify-send").writeText("")

        assertIs<NotifySendNotifier>(ports(HostOs.LINUX).notifier)
    }

    @Test
    fun `Windows uses the tray notifier it is given`() {
        assertSame(Tray, ports(HostOs.WINDOWS).notifier)
    }

    @Test
    fun `without Herdr or Orca configured no runner notifies by itself`() = runTest {
        assertEquals(RunnerNotifications(), ports(HostOs.LINUX).runners.read())
        assertEquals(RunnerNotifications(), ports(HostOs.WINDOWS).runners.read())
    }
}
