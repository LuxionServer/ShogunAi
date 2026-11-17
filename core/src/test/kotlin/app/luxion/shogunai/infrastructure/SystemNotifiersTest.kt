package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.executor.CommandExecutionException
import app.luxion.shogunai.domain.io.NoSystemNotifier
import app.luxion.shogunai.domain.io.SystemNotification
import app.luxion.shogunai.domain.io.SystemNotifier
import app.luxion.shogunai.domain.model.AppRequest
import app.luxion.shogunai.domain.model.HostOs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

class SystemNotifiersTest {
    private val click = AppRequest.openWorktree(createdAt = 0, projectId = "api", worktreePath = "/home/dev/projects/TASK-1")
    private val notification = SystemNotification("Claude needs you", "feature/login · api", "Open", click)
    private val helperExecutable = "/Applications/ShogunAi.app/Contents/app/resources/ShogunAi Notifications.app/Contents/MacOS/shogunai-notifier"

    private class Named(val name: String) : SystemNotifier {
        override suspend fun show(notification: SystemNotification, onClick: () -> Unit) = Unit
    }

    @Test
    fun `each system gets its own notifier, or none`() {
        val mac = Named("mac")
        val linux = Named("linux")
        val windows = Named("windows")
        fun choose(os: HostOs) = chooseSystemNotifier(os, { mac }, { linux }, { windows })

        assertSame(mac, choose(HostOs.MACOS))
        assertSame(windows, choose(HostOs.WINDOWS))
        assertSame(linux, choose(HostOs.LINUX))
        assertSame(NoSystemNotifier, chooseSystemNotifier(HostOs.LINUX, { mac }, { null }, { windows }))
    }

    @Test
    fun `the helper is found in the resources folder first, then in the installed app`() {
        val dev = "/home/dev/projects/main-repo/desktopApp/build/resources"
        val devHelper = "$dev/ShogunAi Notifications.app/Contents/MacOS/shogunai-notifier"
        val bundled = "/Applications/ShogunAi.app/Contents/app/resources"

        assertEquals(
            NotificationHelper(devHelper, null),
            NotificationHelperLocator.locate(dev, "/opt/jdk", "/home/dev") { it == devHelper || it == helperExecutable },
        )
        assertEquals(
            NotificationHelper("$bundled/ShogunAi Notifications.app/Contents/MacOS/shogunai-notifier", "/Applications/ShogunAi.app"),
            NotificationHelperLocator.locate(bundled, "/opt/jdk", "/home/dev") { true },
        )
    }

    @Test
    fun `the command line finds the helper in the app it runs from, then in the Applications folders`() {
        val custom = "/opt/apps/ShogunAi.app"
        val customHelper = "$custom/Contents/app/resources/ShogunAi Notifications.app/Contents/MacOS/shogunai-notifier"
        val userHelper = "/home/dev/Applications/ShogunAi.app/Contents/app/resources/ShogunAi Notifications.app/Contents/MacOS/shogunai-notifier"

        assertEquals(
            NotificationHelper(customHelper, custom),
            NotificationHelperLocator.locate(null, "$custom/Contents/runtime/Contents/Home", "/home/dev") { true },
        )
        assertEquals(NotificationHelper(helperExecutable, "/Applications/ShogunAi.app"), NotificationHelperLocator.locate(null, "/opt/jdk", "/home/dev") { it == helperExecutable })
        assertEquals(NotificationHelper(userHelper, "/home/dev/Applications/ShogunAi.app"), NotificationHelperLocator.locate(null, "/opt/jdk", "/home/dev") { it == userHelper })
        assertNull(NotificationHelperLocator.locate(null, "C:\\jdk", "/home/dev") { false })
    }

    @Test
    fun `the helper posts with the click request and opens the app it belongs to`() = runTest {
        val executor = FakeShellCommandExecutor()

        MacHelperNotifier(executor, NotificationHelper(helperExecutable, "/Applications/ShogunAi.app"), "/home/dev/.shogunai/app-requests").show(notification) {}

        assertEquals(
            listOf(
                helperExecutable, "post",
                "--title", "Claude needs you",
                "--body", "feature/login · api",
                "--request", click.toJson(),
                "--requests-dir", "/home/dev/.shogunai/app-requests",
                "--app", "/Applications/ShogunAi.app",
            ),
            executor.executedCommands.single(),
        )
    }

    @Test
    fun `the image goes to the helper as an attachment and to notify-send as its icon`() = runTest {
        val withImage = notification.copy(image = "/home/dev/.shogunai/notifications/images/needs-you-c.png")
        val helper = FakeShellCommandExecutor()
        val notifySend = FakeShellCommandExecutor()

        MacHelperNotifier(helper, NotificationHelper(helperExecutable, null), "/requests").show(withImage) {}
        NotifySendNotifier(notifySend, "notify-send", waitForClick = false).show(withImage) {}

        assertEquals(listOf("--image", "/home/dev/.shogunai/notifications/images/needs-you-c.png"), helper.executedCommands.single().takeLast(2))
        assertEquals("--icon=/home/dev/.shogunai/notifications/images/needs-you-c.png", notifySend.executedCommands.single()[2])
    }

    @Test
    fun `a helper outside an app bundle opens nothing and a failing helper is quiet`() = runTest {
        val executor = FakeShellCommandExecutor { _: List<String> -> throw CommandExecutionException(listOf("helper"), IOException("gone")) }

        MacHelperNotifier(executor, NotificationHelper("/dev/helper", null), "/requests").show(notification) {}

        assertEquals("/requests", executor.executedCommands.single().last())
    }

    @Test
    fun `notify-send waits for the click and reports it`() = runTest {
        val executor = FakeShellCommandExecutor { FakeShellCommandExecutor.success(it, "default\n") }
        var clicked = 0

        NotifySendNotifier(executor, "/usr/bin/notify-send", waitForClick = true).show(notification) { clicked++ }

        assertEquals(
            listOf("/usr/bin/notify-send", "--app-name=ShogunAi", "--action=default=Open", "--wait", "--", "Claude needs you", "feature/login · api"),
            executor.executedCommands.single(),
        )
        assertEquals(1, clicked)
    }

    @Test
    fun `notify-send dismissed or failing reports no click`() = runTest {
        var clicked = 0
        NotifySendNotifier(FakeShellCommandExecutor { FakeShellCommandExecutor.success(it, "") }, "notify-send", waitForClick = true).show(notification) { clicked++ }
        NotifySendNotifier(FakeShellCommandExecutor { _: List<String> -> throw CommandExecutionException(listOf("notify-send"), IOException("x")) }, "notify-send", true)
            .show(notification) { clicked++ }

        assertEquals(0, clicked)
    }

    @Test
    fun `notify-send without waiting posts without an action`() = runTest {
        val executor = FakeShellCommandExecutor { FakeShellCommandExecutor.success(it, "default") }
        var clicked = 0

        NotifySendNotifier(executor, "notify-send", waitForClick = false).show(notification) { clicked++ }

        assertEquals(listOf("notify-send", "--app-name=ShogunAi", "--", "Claude needs you", "feature/login · api"), executor.executedCommands.single())
        assertEquals(0, clicked)
    }

    @Test
    fun `cancellation reaches the caller`() = runTest {
        val executor = FakeShellCommandExecutor { _: List<String> -> throw CancellationException("stop") }

        assertFailsWith<CancellationException> { NotifySendNotifier(executor, "notify-send", false).show(notification) {} }
    }

    @Test
    fun `no notifier shows nothing`() = runTest {
        NoSystemNotifier.show(notification) { error("never") }
    }
}
