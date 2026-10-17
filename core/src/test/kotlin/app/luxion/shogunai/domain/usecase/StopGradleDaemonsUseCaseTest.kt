package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeGradleDaemonSource
import app.luxion.shogunai.domain.FakeGradleDaemonSource.Companion.gradleDaemon
import app.luxion.shogunai.domain.FakeGradleDaemonSource.Companion.kotlinDaemon
import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.model.DaemonKeptReason
import app.luxion.shogunai.domain.model.DaemonKind
import app.luxion.shogunai.domain.model.DaemonState
import app.luxion.shogunai.domain.model.KeptDaemon
import kotlinx.coroutines.test.runTest
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StopGradleDaemonsUseCaseTest {

    private val idle = DaemonState.Idle(Instant.parse("2026-10-16T10:57:00Z"))
    private val executor = FakeShellCommandExecutor()

    @Test
    fun `stops an idle gradle daemon`() = runTest {
        val source = FakeGradleDaemonSource(listOf(gradleDaemon(1, idle)))

        val report = StopGradleDaemonsUseCase(source, executor)(listOf(1)).getOrThrow()

        assertEquals(listOf(1L), report.stopped.map { it.pid })
        assertTrue(executor.executed("kill", "1"))
    }

    @Test
    fun `keeps busy and unknown gradle daemons`() = runTest {
        val source = FakeGradleDaemonSource(listOf(gradleDaemon(1, DaemonState.Busy), gradleDaemon(2, DaemonState.Unknown)))

        val report = StopGradleDaemonsUseCase(source, executor)(listOf(1, 2)).getOrThrow()

        assertEquals(
            listOf(
                KeptDaemon(1, DaemonKind.GRADLE, DaemonKeptReason.BUSY),
                KeptDaemon(2, DaemonKind.GRADLE, DaemonKeptReason.STATE_UNKNOWN),
            ),
            report.kept,
        )
        assertTrue(executor.executedCommands.isEmpty())
    }

    @Test
    fun `checks the state again before stopping`() = runTest {
        val source = FakeGradleDaemonSource(listOf(gradleDaemon(1, idle)))
        source.daemons = listOf(gradleDaemon(1, DaemonState.Busy))

        val report = StopGradleDaemonsUseCase(source, executor)(listOf(1)).getOrThrow()

        assertEquals(DaemonKeptReason.BUSY, report.kept.single().reason)
        assertEquals(1, source.listings)
    }

    @Test
    fun `keeps kotlin daemons while a gradle build runs`() = runTest {
        val source = FakeGradleDaemonSource(listOf(gradleDaemon(1, DaemonState.Busy), kotlinDaemon(2)))

        val report = StopGradleDaemonsUseCase(source, executor)(listOf(2)).getOrThrow()

        assertEquals(KeptDaemon(2, DaemonKind.KOTLIN, DaemonKeptReason.BUILD_RUNNING), report.kept.single())
    }

    @Test
    fun `stops kotlin daemons when every gradle daemon is idle`() = runTest {
        val source = FakeGradleDaemonSource(listOf(gradleDaemon(1, idle), kotlinDaemon(2)))

        val report = StopGradleDaemonsUseCase(source, executor)().getOrThrow()

        assertEquals(listOf(1L, 2L), report.stopped.map { it.pid })
    }

    @Test
    fun `stopping all keeps the busy daemon and the kotlin daemons`() = runTest {
        val source = FakeGradleDaemonSource(
            listOf(gradleDaemon(1, idle), gradleDaemon(2, DaemonState.Busy), gradleDaemon(3, idle), kotlinDaemon(4)),
        )

        val report = StopGradleDaemonsUseCase(source, executor)().getOrThrow()

        assertEquals(listOf(1L, 3L), report.stopped.map { it.pid })
        assertEquals(listOf(2L, 4L), report.kept.map { it.pid })
    }

    @Test
    fun `reports process ids that are not daemons`() = runTest {
        val source = FakeGradleDaemonSource(listOf(gradleDaemon(1, idle)))

        val report = StopGradleDaemonsUseCase(source, executor)(listOf(1, 99999)).getOrThrow()

        assertEquals(listOf(99999L), report.notDaemons)
        assertEquals(listOf(1L), report.stopped.map { it.pid })
    }

    @Test
    fun `reports a failed kill without stopping the rest`() = runTest {
        val failingExecutor = FakeShellCommandExecutor { command ->
            if (command == listOf("kill", "1")) FakeShellCommandExecutor.failure(command, stderr = "Operation not permitted")
            else FakeShellCommandExecutor.success(command)
        }
        val source = FakeGradleDaemonSource(listOf(gradleDaemon(1, idle), gradleDaemon(2, idle)))

        val report = StopGradleDaemonsUseCase(source, failingExecutor)().getOrThrow()

        assertEquals("Operation not permitted", report.failures.single().detail)
        assertEquals(listOf(2L), report.stopped.map { it.pid })
    }
}
