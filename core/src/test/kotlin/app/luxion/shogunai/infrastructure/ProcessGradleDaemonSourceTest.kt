package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.model.DaemonKind
import app.luxion.shogunai.domain.model.DaemonState
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ProcessGradleDaemonSourceTest {

    private val gradleUserHome: Path = Files.createTempDirectory("gradle-home")

    @AfterTest
    fun cleanUp() {
        gradleUserHome.toFile().deleteRecursively()
    }

    @Test
    fun `parses gradle and kotlin daemons and skips other processes`() {
        val processes = parseDaemonProcesses(PS_OUTPUT)

        assertEquals(listOf(36474L, 36475L), processes.map { it.pid })
        val gradle = processes[0]
        assertEquals(DaemonKind.GRADLE, gradle.kind)
        assertEquals("9.6.1", gradle.gradleVersion)
        assertEquals(1_162_208L * 1024, gradle.residentMemoryBytes)
        val kotlin = processes[1]
        assertEquals(DaemonKind.KOTLIN, kotlin.kind)
        assertNull(kotlin.gradleVersion)
        assertEquals(DaemonState.Untracked, kotlin.state)
    }

    @Test
    fun `reads an idle state with its time and the last build directory`() {
        val record = parseDaemonLog(IDLE_LOG)

        assertEquals(DaemonState.Idle(Instant.parse("2026-10-16T10:57:23.799Z")), record.state)
        assertEquals("/home/dev/projects/worktrees/TASK-123", record.lastBuildDirectory)
    }

    @Test
    fun `reads a busy state from the latest marker`() {
        val record = parseDaemonLog(IDLE_LOG + BUSY_LINES)

        assertEquals(DaemonState.Busy, record.state)
        assertEquals("/home/dev/projects/main-repo", record.lastBuildDirectory)
    }

    @Test
    fun `reports unknown without any state marker`() {
        val record = parseDaemonLog(
            "2026-10-16T12:50:00.000+0200 [DEBUG] [org.gradle.launcher.daemon.server.Daemon] Starting periodic daemon health check\n",
        )

        assertEquals(DaemonState.Unknown, record.state)
        assertNull(record.lastBuildDirectory)
    }

    @Test
    fun `lists daemons with the state read from their log`() = runTest {
        val logDirectory = gradleUserHome.resolve("daemon/9.6.1").createDirectories()
        logDirectory.resolve("daemon-36474.out.log").writeText(IDLE_LOG)
        val source = ProcessGradleDaemonSource(
            FakeShellCommandExecutor { FakeShellCommandExecutor.success(it, PS_OUTPUT) },
            gradleUserHome.toString(),
        )

        val daemons = source.list()

        assertEquals(DaemonState.Idle(Instant.parse("2026-10-16T10:57:23.799Z")), daemons[0].state)
        assertEquals("/home/dev/projects/worktrees/TASK-123", daemons[0].lastBuildDirectory)
        assertEquals(DaemonState.Untracked, daemons[1].state)
    }

    @Test
    fun `reports unknown when the log is missing`() = runTest {
        val source = ProcessGradleDaemonSource(
            FakeShellCommandExecutor { FakeShellCommandExecutor.success(it, PS_OUTPUT) },
            gradleUserHome.toString(),
        )

        assertEquals(DaemonState.Unknown, source.list()[0].state)
    }

    @Test
    fun `fails with ProcessListingFailed when ps fails`() = runTest {
        val source = ProcessGradleDaemonSource(FakeShellCommandExecutor { FakeShellCommandExecutor.failure(it) }, "/home/dev/.gradle")

        assertFailsWith<WorktreeError.ProcessListingFailed> { source.list() }
    }

    private companion object {
        val PS_OUTPUT = """
              1   12000 /sbin/launchd
            36474 1162208 /opt/jdk-21/bin/java --add-opens=java.base/java.lang=ALL-UNNAMED -Xmx3072M -Dfile.encoding=UTF-8 -cp /home/dev/.gradle/wrapper/dists/gradle-9.6.1-bin/abc/gradle-9.6.1/lib/gradle-daemon-main-9.6.1.jar org.gradle.launcher.daemon.bootstrap.GradleDaemon 9.6.1
            36475 1935840 /opt/jdk-21/bin/java -cp /home/dev/.gradle/caches/kotlin-daemon-client-2.4.0.jar -Xmx3072M -Dkotlin.daemon.custom.run.files.path.for.tests=/home/dev/Library/Application Support/kotlin/daemon org.jetbrains.kotlin.daemon.KotlinCompileDaemon --daemon-logsPath /tmp
            40000  800000 /opt/idea/jbr/bin/java -Xmx4g com.intellij.idea.Main
        """.trimIndent()

        val IDLE_LOG = """
            2026-10-16T12:56:31.229+0200 [INFO] [org.gradle.launcher.daemon.server.DefaultIncomingConnectionHandler] Received command: Build{id=77b2614e-47e4-4e9e-b37b-404f3735ae91, currentDir=/home/dev/projects/worktrees/TASK-123}.
            2026-10-16T12:56:31.229+0200 [INFO] [org.gradle.launcher.daemon.server.DaemonRegistryUpdater] Marking the daemon as busy, address: [0fd86451 port:58388, addresses:[localhost/127.0.0.1]]
            2026-10-16T12:57:23.799+0200 [INFO] [org.gradle.launcher.daemon.server.DaemonRegistryUpdater] Marking the daemon as idle, address: [0fd86451 port:58388, addresses:[localhost/127.0.0.1]]
            2026-10-16T12:57:24.000+0200 [DEBUG] [org.gradle.launcher.daemon.server.Daemon] Starting periodic daemon health check
        """.trimIndent() + "\n"

        val BUSY_LINES = """
            2026-10-16T13:10:00.000+0200 [INFO] [org.gradle.launcher.daemon.server.DefaultIncomingConnectionHandler] Received command: Build{id=88c3725f-58f5-4f0f-c48f-515f4846bf02, currentDir=/home/dev/projects/main-repo}.
            2026-10-16T13:10:00.001+0200 [INFO] [org.gradle.launcher.daemon.server.DaemonRegistryUpdater] Marking the daemon as busy, address: [0fd86451 port:58388, addresses:[localhost/127.0.0.1]]
        """.trimIndent() + "\n"
    }
}
