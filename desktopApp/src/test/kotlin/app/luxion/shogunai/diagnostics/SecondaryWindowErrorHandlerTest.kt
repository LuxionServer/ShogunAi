package app.luxion.shogunai.diagnostics

import app.luxion.shogunai.domain.model.DiagnosticKind
import kotlinx.coroutines.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SecondaryWindowErrorHandlerTest {

    private val log = FakeDiagnosticsLog()
    private val calls = mutableListOf<String>()
    private val handler = SecondaryWindowErrorHandler(
        log,
        onClosed = { calls += "closed" },
        onReport = { calls += "reported" },
    )

    @Test
    fun `an error is recorded as an unhandled error`() {
        handler.handle(IllegalStateException("boom"))

        val entry = log.entries.single()
        assertEquals(DiagnosticKind.UNHANDLED_ERROR, entry.kind)
        assertEquals("java.lang.IllegalStateException: boom", entry.message)
    }

    @Test
    fun `an error closes the window and then reports it`() {
        handler.handle(IllegalStateException("boom"))

        assertEquals(listOf("closed", "reported"), calls)
    }

    @Test
    fun `an error is never rethrown`() {
        handler.handle(OutOfMemoryError("boom"))

        assertEquals(1, log.entries.size)
    }

    @Test
    fun `a cancellation is neither recorded nor reported`() {
        handler.handle(CancellationException("cancelled"))

        assertTrue(log.entries.isEmpty())
        assertTrue(calls.isEmpty())
    }
}
