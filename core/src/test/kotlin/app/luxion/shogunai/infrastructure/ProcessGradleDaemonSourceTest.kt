package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.executor.CommandResult
import app.luxion.shogunai.domain.model.GradleClient
import app.luxion.shogunai.domain.model.HoldingBuild
import app.luxion.shogunai.domain.model.DaemonKind
import app.luxion.shogunai.domain.model.DaemonState
import app.luxion.shogunai.domain.model.HostOs
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.coroutines.test.runTest
import java.io.IOException
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

    @Test
    fun `the Gradle user home is GRADLE_USER_HOME when set and the home's gradle folder otherwise`() {
        fun environment(variables: Map<String, String>) = SystemHostEnvironment(HostOs.MACOS, variables, "/home/dev", loginShell = null)

        assertEquals("/projects/gradle-home", defaultGradleUserHome(environment(mapOf("GRADLE_USER_HOME" to "/projects/gradle-home"))))
        assertEquals(Path.of("/home/dev", ".gradle").toString(), defaultGradleUserHome(environment(mapOf("GRADLE_USER_HOME" to " "))))
        assertEquals(Path.of("/home/dev", ".gradle").toString(), defaultGradleUserHome(environment(emptyMap())))
    }

    @Test
    fun `reads the latest build's directory, start and client port`() {
        val build = parseDaemonLog(IDLE_LOG + HELD_LINES).latestBuild

        assertEquals(LoggedBuild("/home/dev/projects/worktrees/api/task-123", Instant.parse("2026-10-16T19:02:00Z"), 61048), build)
    }

    @Test
    fun `falls back to the received command without a client port`() {
        val build = parseDaemonLog(IDLE_LOG + BUSY_LINES).latestBuild

        assertEquals(LoggedBuild("/home/dev/projects/main-repo", Instant.parse("2026-10-16T11:10:00Z"), null), build)
    }

    private fun writeLog(text: String) {
        gradleUserHome.resolve("daemon/9.6.1").createDirectories().resolve("daemon-36474.out.log").writeText(text)
    }

    private fun heldSource(lsof: (List<String>) -> CommandResult, processTable: String = PROCESS_TABLE) =
        ProcessGradleDaemonSource(
            FakeShellCommandExecutor { command ->
                when {
                    command.first() == "lsof" -> lsof(command)
                    command == listOf("ps", "-A", "-ww", "-o", "pid=,ppid=,command=") -> FakeShellCommandExecutor.success(command, processTable)
                    else -> FakeShellCommandExecutor.success(command, PS_OUTPUT)
                }
            },
            gradleUserHome.toString(),
        )

    private val connection: (List<String>) -> CommandResult = { command ->
        FakeShellCommandExecutor.success(command, "p36474\ncjava\nf12\np81234\ncjava\nf9\n")
    }

    @Test
    fun `a busy daemon's holding build has its client, tasks and ancestors`() = runTest {
        writeLog(IDLE_LOG + HELD_LINES)
        val lsofCalls = mutableListOf<List<String>>()

        val holder = heldSource({ lsofCalls += it; connection(it) }).list()[0].holder

        assertEquals("/home/dev/projects/worktrees/api/task-123", holder?.directory)
        assertEquals(Instant.parse("2026-10-16T19:02:00Z"), holder?.startedAt)
        assertEquals(GradleClient(81234, listOf("benchRun"), listOf(81200, 700, 500)), holder?.client)
        assertEquals(listOf(listOf("lsof", "-nP", "-iTCP:61048", "-sTCP:ESTABLISHED", "-Fpc")), lsofCalls)
    }

    @Test
    fun `a wrapper run as a jar is a Gradle client with the arguments after the jar`() = runTest {
        writeLog(IDLE_LOG + HELD_LINES)
        val table = "  81234   700 java -Xmx64m -Dorg.gradle.appname=gradlew -jar /home/dev/projects/worktrees/api/task-123/gradle/wrapper/gradle-wrapper.jar :core:test --rerun-tasks\n"

        val client = heldSource(connection, processTable = table).list()[0].holder?.client

        assertEquals(GradleClient(81234, listOf(":core:test", "--rerun-tasks"), listOf(700)), client)
    }

    @Test
    fun `the holding build keeps its directory and start when the connection can't be inspected`() = runTest {
        writeLog(IDLE_LOG + HELD_LINES)

        val failing = heldSource({ FakeShellCommandExecutor.failure(it) }).list()[0].holder
        val throwing = heldSource({ throw IOException("lsof: not found") }).list()[0].holder

        listOf(failing, throwing).forEach { holder ->
            assertEquals(HoldingBuild("/home/dev/projects/worktrees/api/task-123", Instant.parse("2026-10-16T19:02:00Z"), null), holder)
        }
    }

    @Test
    fun `a connected process that isn't a Gradle client isn't reported`() = runTest {
        writeLog(IDLE_LOG + HELD_LINES)

        val holder = heldSource(connection, processTable = "  81234   700 /usr/bin/nc localhost 61047\n").list()[0].holder

        assertNull(holder?.client)
    }

    @Test
    fun `an idle daemon has no holding build`() = runTest {
        writeLog(IDLE_LOG)

        assertNull(heldSource(connection).list()[0].holder)
    }

    @Test
    fun `malformed process lines are skipped`() {
        assertEquals(emptyList(), parseDaemonProcesses("x\nabc 1 java\n1 abc java\n"))
        assertEquals(mapOf(5L to ProcessEntry(1, "")), parseProcessTable("x\n5\n5 x\n5 1\n"))
        assertEquals(emptyList(), ancestorsOf(99, emptyMap()))
    }

    @Test
    fun `an idle marker without a readable time and build lines without a folder read as unknown and no build`() {
        val record = parseDaemonLog(
            "junk [INFO] Marking the daemon as idle, address: [x]\n" +
                "2026-10-16T21:02:00.000+0200 [DEBUG] Starting executing command: Build{id=1} with connection: socket\n",
        )
        val received = parseDaemonLog("2026-10-16T21:02:00.000+0200 [INFO] Received command: Build{id=1}.\n")

        assertEquals(DaemonState.Unknown, record.state)
        assertNull(record.latestBuild)
        assertNull(received.latestBuild)
    }

    @Test
    fun `a busy daemon without a build or a client port holds no client`() = runTest {
        writeLog(BUSY_LINES.lines().drop(1).joinToString("\n"))
        assertNull(heldSource(connection).list()[0].holder)

        writeLog(BUSY_LINES)
        assertNull(heldSource(connection).list()[0].holder?.client)
    }

    @Test
    fun `daemons without a version are left as listed and an unreadable or partial process table gives no client`() = runTest {
        val unversioned = "36474 1162208 /opt/jdk-21/bin/java org.gradle.launcher.daemon.bootstrap.GradleDaemon\n"
        val source = ProcessGradleDaemonSource(FakeShellCommandExecutor { FakeShellCommandExecutor.success(it, unversioned) }, gradleUserHome.toString())
        assertEquals(DaemonState.Unknown, source.list().single().state)

        writeLog(IDLE_LOG + HELD_LINES)
        val failingTable = ProcessGradleDaemonSource(
            FakeShellCommandExecutor { command ->
                when {
                    command.first() == "lsof" -> connection(command)
                    command.contains("pid=,ppid=,command=") -> FakeShellCommandExecutor.failure(command)
                    else -> FakeShellCommandExecutor.success(command, PS_OUTPUT)
                }
            },
            gradleUserHome.toString(),
        )
        assertNull(failingTable.list()[0].holder?.client)
        assertNull(heldSource(connection, processTable = "  500     1 /bin/zsh -l\n").list()[0].holder?.client)
    }

    @Test
    fun `two busy daemons read the process table once`() = runTest {
        val logs = gradleUserHome.resolve("daemon/9.6.1").createDirectories()
        logs.resolve("daemon-36474.out.log").writeText(IDLE_LOG + HELD_LINES)
        logs.resolve("daemon-36476.out.log").writeText(IDLE_LOG + HELD_LINES)
        val second = "36476 1162208 /opt/jdk-21/bin/java -cp gradle-daemon-main-9.6.1.jar org.gradle.launcher.daemon.bootstrap.GradleDaemon 9.6.1\n"
        val tableReads = mutableListOf<List<String>>()
        val source = ProcessGradleDaemonSource(
            FakeShellCommandExecutor { command ->
                when {
                    command.first() == "lsof" -> connection(command)
                    command.contains("pid=,ppid=,command=") -> FakeShellCommandExecutor.success(command, PROCESS_TABLE).also { tableReads += command }
                    else -> FakeShellCommandExecutor.success(command, PS_OUTPUT + "\n" + second)
                }
            },
            gradleUserHome.toString(),
        )

        assertEquals(2, source.list().count { it.holder != null })
        assertEquals(1, tableReads.size)
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

        val HELD_LINES = """
            2026-10-16T21:02:00.000+0200 [INFO] [org.gradle.launcher.daemon.server.DefaultIncomingConnectionHandler] Received command: Build{id=99d4836a-69a6-4a1a-d59a-626a5957cf13, currentDir=/home/dev/projects/worktrees/api/task-123}.
            2026-10-16T21:02:00.000+0200 [DEBUG] [org.gradle.launcher.daemon.server.DefaultIncomingConnectionHandler] Starting executing command: Build{id=99d4836a-69a6-4a1a-d59a-626a5957cf13, currentDir=/home/dev/projects/worktrees/api/task-123} with connection: socket connection from /127.0.0.1:61047 to /127.0.0.1:61048.
            2026-10-16T21:02:00.001+0200 [INFO] [org.gradle.launcher.daemon.server.DaemonRegistryUpdater] Marking the daemon as busy, address: [0fd86451 port:61047, addresses:[localhost/127.0.0.1]]
        """.trimIndent() + "\n"

        val PROCESS_TABLE = """
              1     0 /sbin/launchd
            500     1 /bin/zsh -l
            700   500 claude
          81200   700 /bin/sh ./gradlew benchRun
          81234 81200 /opt/jdk-21/bin/java -Xmx64m -classpath /home/dev/projects/worktrees/api/task-123/gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain benchRun
          36474     1 /opt/jdk-21/bin/java org.gradle.launcher.daemon.bootstrap.GradleDaemon 9.6.1
        """.trimIndent() + "\n"

        val BUSY_LINES = """
            2026-10-16T13:10:00.000+0200 [INFO] [org.gradle.launcher.daemon.server.DefaultIncomingConnectionHandler] Received command: Build{id=88c3725f-58f5-4f0f-c48f-515f4846bf02, currentDir=/home/dev/projects/main-repo}.
            2026-10-16T13:10:00.001+0200 [INFO] [org.gradle.launcher.daemon.server.DaemonRegistryUpdater] Marking the daemon as busy, address: [0fd86451 port:58388, addresses:[localhost/127.0.0.1]]
        """.trimIndent() + "\n"
    }
}
