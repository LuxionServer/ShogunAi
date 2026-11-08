package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeMavenDaemonSource
import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.model.DaemonKeptReason
import app.luxion.shogunai.domain.model.DaemonState
import app.luxion.shogunai.domain.model.KeptMavenDaemon
import app.luxion.shogunai.domain.model.MavenDaemonProcess
import kotlinx.coroutines.test.runTest
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StopMavenDaemonsUseCaseTest {

    private val idle = DaemonState.Idle(Instant.parse("2026-10-28T12:57:00Z"))
    private val executor = FakeShellCommandExecutor()

    private fun daemon(pid: Long, state: DaemonState) = MavenDaemonProcess(pid, 100, "/home/dev/projects/main-repo", state)

    @Test
    fun `stops an idle daemon`() = runTest {
        val source = FakeMavenDaemonSource(listOf(daemon(1, idle)))

        val report = StopMavenDaemonsUseCase(source, executor)(listOf(1)).getOrThrow()

        assertEquals(listOf(1L), report.stopped.map { it.pid })
        assertTrue(executor.executed("kill", "1"))
    }

    @Test
    fun `keeps busy and unknown daemons`() = runTest {
        val source = FakeMavenDaemonSource(listOf(daemon(1, DaemonState.Busy), daemon(2, DaemonState.Unknown)))

        val report = StopMavenDaemonsUseCase(source, executor)(listOf(1, 2)).getOrThrow()

        assertEquals(
            listOf(KeptMavenDaemon(1, DaemonKeptReason.BUSY), KeptMavenDaemon(2, DaemonKeptReason.STATE_UNKNOWN)),
            report.kept,
        )
        assertTrue(executor.executedCommands.isEmpty())
    }

    @Test
    fun `checks the state again before stopping`() = runTest {
        val source = FakeMavenDaemonSource(listOf(daemon(1, idle)))
        source.daemons = listOf(daemon(1, DaemonState.Busy))

        val report = StopMavenDaemonsUseCase(source, executor)(listOf(1)).getOrThrow()

        assertEquals(DaemonKeptReason.BUSY, report.kept.single().reason)
        assertEquals(1, source.listings)
    }

    @Test
    fun `stopping all stops every idle daemon and keeps the busy one`() = runTest {
        val source = FakeMavenDaemonSource(listOf(daemon(1, idle), daemon(2, DaemonState.Busy), daemon(3, idle)))

        val report = StopMavenDaemonsUseCase(source, executor)().getOrThrow()

        assertEquals(listOf(1L, 3L), report.stopped.map { it.pid })
        assertEquals(listOf(KeptMavenDaemon(2, DaemonKeptReason.BUSY)), report.kept)
    }

    @Test
    fun `reports process ids that are not daemons`() = runTest {
        val source = FakeMavenDaemonSource(listOf(daemon(1, idle)))

        val report = StopMavenDaemonsUseCase(source, executor)(listOf(1, 99999)).getOrThrow()

        assertEquals(listOf(99999L), report.notDaemons)
        assertEquals(listOf(1L), report.stopped.map { it.pid })
    }

    @Test
    fun `reports a failed kill without stopping the rest`() = runTest {
        val failingExecutor = FakeShellCommandExecutor { command ->
            if (command == listOf("kill", "1")) FakeShellCommandExecutor.failure(command, stderr = "Operation not permitted")
            else FakeShellCommandExecutor.success(command)
        }
        val source = FakeMavenDaemonSource(listOf(daemon(1, idle), daemon(2, idle)))

        val report = StopMavenDaemonsUseCase(source, failingExecutor)().getOrThrow()

        assertEquals("Operation not permitted", report.failures.single().detail)
        assertEquals(listOf(2L), report.stopped.map { it.pid })
    }

    @Test
    fun `an untracked daemon is kept and a silent failed kill reports its exit code`() = runTest {
        val silent = FakeShellCommandExecutor { FakeShellCommandExecutor.failure(it, exitCode = 3, stderr = " ") }
        val source = FakeMavenDaemonSource(listOf(daemon(1, DaemonState.Untracked), daemon(2, idle)))

        val report = StopMavenDaemonsUseCase(source, silent)().getOrThrow()

        assertEquals(DaemonKeptReason.STATE_UNKNOWN, report.kept.single().reason)
        assertEquals("exit code 3", report.failures.single().detail)
    }
}
