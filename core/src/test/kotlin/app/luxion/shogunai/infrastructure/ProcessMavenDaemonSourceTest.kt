package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.coroutines.test.runTest
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
}
