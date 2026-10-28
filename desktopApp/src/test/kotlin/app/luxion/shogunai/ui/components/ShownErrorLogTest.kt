package app.luxion.shogunai.ui.components

import app.luxion.shogunai.diagnostics.FakeDiagnosticsLog
import app.luxion.shogunai.domain.model.DiagnosticKind
import app.luxion.shogunai.domain.model.WorktreeError
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class ShownErrorLogTest {

    private val log = FakeDiagnosticsLog()
    private val clock = Instant.parse("2026-01-01T00:00:00Z")
    private fun failure() = WorktreeError.GitCommandFailed(
        listOf("git", "worktree", "remove", "/home/dev/projects/TASK-123"),
        255,
        "error: failed to delete '/home/dev/projects/TASK-123': Permission denied",
    )

    @Test
    fun `the same error shown again is recorded once`() {
        val shown = ShownErrorLog(log, { clock })
        val error = failure()

        shown.record(error, "Git failed", "details")
        shown.record(error, "Git failed", "details")

        assertEquals(1, log.entries.size)
        assertEquals(DiagnosticKind.SHOWN_ERROR, log.entries.single().kind)
        assertEquals(clock, log.entries.single().timestamp)
    }

    @Test
    fun `a later occurrence of the same failure is recorded again`() {
        val shown = ShownErrorLog(log, { clock })

        shown.record(failure(), "Git failed", "details")
        shown.record(failure(), "Git failed", "details")

        assertEquals(2, log.entries.size)
    }

    @Test
    fun `an error forgotten after many others is recorded again`() {
        val shown = ShownErrorLog(log, { clock }, remembered = 2)
        val first = failure()

        shown.record(first, "first", null)
        shown.record(failure(), "second", null)
        shown.record(failure(), "third", null)
        shown.record(first, "first", null)

        assertEquals(listOf("first", "second", "third", "first"), log.entries.map { it.message })
    }
}
