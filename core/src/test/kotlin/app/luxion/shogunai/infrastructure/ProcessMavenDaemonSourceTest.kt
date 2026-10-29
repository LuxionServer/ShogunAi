package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.model.DaemonState
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.attribute.FileTime
import java.time.Instant
import java.time.ZoneOffset
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ProcessMavenDaemonSourceTest {

    private val psOutput = """
          501 4096 /usr/bin/java -Dfoo -classpath /opt/mvnd/lib/mvnd-common.jar org.mvndaemon.mvnd.daemon.Server 6a4f
          502 2048 /usr/bin/java -classpath /opt/mvnd/lib/mvnd-common.jar org.mvndaemon.mvnd.daemon.Server 77b1
          610 9999 /usr/bin/java -classpath /opt/maven/boot/plexus-classworlds.jar org.codehaus.plexus.classworlds.launcher.Launcher package
          611 8888 /Applications/IDE.app/Contents/MacOS/idea
    """.trimIndent()

    @Test
    fun `lists only mvnd daemons with their memory and working directory`() = runTest {
        val executor = FakeShellCommandExecutor { command ->
            if (command.first() == "ps") {
                FakeShellCommandExecutor.success(command, psOutput)
            } else {
                FakeShellCommandExecutor.success(command, "p501\nn/home/dev/projects/worktrees/TASK-123/service\np502\nn/home/dev/projects/main-repo\n")
            }
        }

        val daemons = ProcessMavenDaemonSource(executor).list()

        assertEquals(listOf(501L, 502L), daemons.map { it.pid })
        assertEquals(4096L * 1024, daemons[0].residentMemoryBytes)
        assertEquals("/home/dev/projects/worktrees/TASK-123/service", daemons[0].workingDirectory)
        assertEquals(listOf("lsof", "-a", "-p", "501,502", "-d", "cwd", "-Fn"), executor.executedCommands.last())
    }

    @Test
    fun `a daemon launched through classworlds is found by its mvnd id`() {
        val output = """
              46437 168560 /opt/jdk/bin/java -classpath /opt/mvnd/mvn/boot/plexus-classworlds-2.11.0.jar -Dorg.slf4j.simpleLogger.logFile=/home/dev/.m2/mvnd/registry/1.0.6/daemon-69b58811.log -Dmvnd.id=69b58811 -Dmvnd.daemonStorage=/home/dev/.m2/mvnd/registry/1.0.6 org.codehaus.plexus.classworlds.launcher.Launcher
              46456 24768 /opt/mvnd/bin/mvnd -B validate
        """.trimIndent()

        assertEquals(
            listOf(MavenDaemonListing(46437, 168560L * 1024, "/home/dev/.m2/mvnd/registry/1.0.6/daemon-69b58811.log")),
            parseMavenDaemons(output),
        )
    }

    @Test
    fun `plain maven builds and IDEs are not daemons`() = runTest {
        val executor = FakeShellCommandExecutor { FakeShellCommandExecutor.success(it, psOutput.lines().drop(2).joinToString("\n")) }

        assertEquals(emptyList(), ProcessMavenDaemonSource(executor).list())
        assertEquals(1, executor.executedCommands.size)
    }

    @Test
    fun `a daemon whose directory can't be read is still listed`() = runTest {
        val executor = FakeShellCommandExecutor { command ->
            if (command.first() == "ps") FakeShellCommandExecutor.success(command, psOutput) else FakeShellCommandExecutor.failure(command)
        }

        val daemons = ProcessMavenDaemonSource(executor).list()

        assertEquals(2, daemons.size)
        assertNull(daemons[0].workingDirectory)
    }

    @Test
    fun `process inspection failing is reported`() = runTest {
        val executor = FakeShellCommandExecutor { FakeShellCommandExecutor.failure(it, exitCode = 2) }

        assertFailsWith<WorktreeError.ProcessListingFailed> { ProcessMavenDaemonSource(executor).list() }
    }

    @Test
    fun `the log path is read from mvnd 1 and mvnd 2 arguments`() {
        val output = """
              501 4096 /usr/bin/java -Dorg.slf4j.simpleLogger.logFile=/home/dev/.m2/mvnd/registry/1.0.6/daemon-6a4f.log -Dmvnd.id=6a4f org.mvndaemon.mvnd.daemon.Server
              502 2048 /usr/bin/java -Dmaven.logger.logFile=/home/dev/.m2/mvnd/registry/2.0.0/daemon-77b1.log org.mvndaemon.mvnd.daemon.Server
              503 1024 /usr/bin/java -Dorg.slf4j.simpleLogger.logFile=/home/dev/My Storage/daemon-9c2d.log -Dmvnd.id=9c2d org.mvndaemon.mvnd.daemon.Server
              504 1024 /usr/bin/java -classpath /opt/mvnd/lib/mvnd-common.jar org.mvndaemon.mvnd.daemon.Server
        """.trimIndent()

        assertEquals(
            listOf(
                "/home/dev/.m2/mvnd/registry/1.0.6/daemon-6a4f.log",
                "/home/dev/.m2/mvnd/registry/2.0.0/daemon-77b1.log",
                "/home/dev/My Storage/daemon-9c2d.log",
                null,
            ),
            parseMavenDaemons(output).map { it.logPath },
        )
    }

    @Test
    fun `a daemon back to idle after a build is idle since its marker`() {
        val log = """
            23:37:21.300 I Dispatch message: ProjectLogMessage{projectId='sample', message='[INFO] BUILD SUCCESS'}
            23:37:21.404 I Build finished, finishing message dispatch
            23:37:21.404 I Dispatch message: BuildFinished{exitCode=0}
            23:37:21.404 I No more message to dispatch
            23:37:21.404 I Daemon back to idle
            23:37:21.404 I Updating state to: Idle
        """.trimIndent()

        assertEquals(
            DaemonState.Idle(Instant.parse("2026-10-28T23:37:21.404Z")),
            parseMavenDaemonState(log, Instant.parse("2026-10-28T23:37:21.404Z"), ZoneOffset.UTC),
        )
    }

    @Test
    fun `a log restarted by a running build has no marker and is busy`() {
        val log = """
            23:37:30.248 I Dispatch message: BuildLogMessage{payload='[INFO] Processing build on daemon 69b58811'}
            23:37:30.249 I Dispatch message: BuildLogMessage{payload='[INFO] Scanning for projects...'}
            23:37:30.250 I Dispatch message: BuildStarted{projectId='sample', projectCount=1, maxThreads=1, artifactIdDisplayLength=6}
            23:37:31.000 I Dispatch message: ProjectLogMessage{projectId='sample', message='[INFO] Updating state to: Idle'}
            23:37:31.100 I Dispatch message: KeepAlive
        """.trimIndent()

        assertEquals(DaemonState.Busy, parseMavenDaemonState(log, Instant.parse("2026-10-28T23:37:31.100Z"), ZoneOffset.UTC))
    }

    @Test
    fun `a busy marker is busy`() {
        val log = """
            09:00:00.000 I Updating state to: Idle
            09:10:00.011 I Updating state to: Busy
            09:10:00.012 I Executing request
        """.trimIndent()

        assertEquals(DaemonState.Busy, parseMavenDaemonState(log, Instant.parse("2026-10-28T09:10:01Z"), ZoneOffset.UTC))
    }

    @Test
    fun `cancelled and broken daemons are busy and unknown states are unknown`() {
        val at = Instant.parse("2026-10-28T10:00:01Z")

        assertEquals(DaemonState.Busy, parseMavenDaemonState("10:00:00.000 I Updating state to: Canceled", at, ZoneOffset.UTC))
        assertEquals(DaemonState.Busy, parseMavenDaemonState("10:00:00.000 I Updating state to: Broken", at, ZoneOffset.UTC))
        assertEquals(DaemonState.Unknown, parseMavenDaemonState("10:00:00.000 I Updating state to: Sleeping", at, ZoneOffset.UTC))
        assertEquals(DaemonState.Unknown, parseMavenDaemonState("25:00:00.000 I Updating state to: Idle", at, ZoneOffset.UTC))
    }

    @Test
    fun `an idle marker before midnight is dated the day before a modification after midnight`() {
        val log = """
            23:58:30.500 I Updating state to: Idle
            23:58:40.500 D Expiration check running
            00:00:10.500 D Expiration check running
        """.trimIndent()

        assertEquals(
            DaemonState.Idle(Instant.parse("2026-10-27T23:58:30.500Z")),
            parseMavenDaemonState(log, Instant.parse("2026-10-28T00:00:10.500Z"), ZoneOffset.UTC),
        )
    }

    @Test
    fun `a modification time with second precision keeps the marker on the same day`() {
        assertEquals(
            DaemonState.Idle(Instant.parse("2026-10-28T12:57:03.841Z")),
            parseMavenDaemonState("12:57:03.841 I Updating state to: Idle", Instant.parse("2026-10-28T12:57:03Z"), ZoneOffset.UTC),
        )
    }

    @Test
    fun `listing reads each daemon's state from its log`() = runTest {
        val directory = Files.createTempDirectory("mvnd-registry")
        try {
            val log = directory.resolve("daemon-6a4f.log")
            log.writeText("12:57:03.841 I Updating state to: Idle\n")
            Files.setLastModifiedTime(log, FileTime.from(Instant.parse("2026-10-28T12:57:04Z")))
            val output = """
                  501 4096 /usr/bin/java -Dorg.slf4j.simpleLogger.logFile=$log org.mvndaemon.mvnd.daemon.Server
                  502 2048 /usr/bin/java -Dorg.slf4j.simpleLogger.logFile=${directory.resolve("daemon-missing.log")} org.mvndaemon.mvnd.daemon.Server
                  503 2048 /usr/bin/java org.mvndaemon.mvnd.daemon.Server
                  504 2048 /usr/bin/java -Dorg.slf4j.simpleLogger.logFile=${directory.resolve("folder.log").createDirectories()} org.mvndaemon.mvnd.daemon.Server
                  505 2048 /usr/bin/java -Dorg.slf4j.simpleLogger.logFile=${directory.resolve("bad")}${"\u0000"}name.log org.mvndaemon.mvnd.daemon.Server
            """.trimIndent()
            val executor = FakeShellCommandExecutor { command ->
                if (command.first() == "ps") FakeShellCommandExecutor.success(command, output) else FakeShellCommandExecutor.failure(command)
            }

            val daemons = ProcessMavenDaemonSource(executor, ZoneOffset.UTC).list()

            assertEquals(
                listOf(DaemonState.Idle(Instant.parse("2026-10-28T12:57:03.841Z")), DaemonState.Unknown, DaemonState.Unknown, DaemonState.Unknown, DaemonState.Unknown),
                daemons.map { it.state },
            )
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
