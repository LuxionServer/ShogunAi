package app.luxion.shogunai.diagnostics

import app.luxion.shogunai.domain.model.DiagnosticKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DiagnosticsHooksTest {

    @Test
    fun `an unhandled error in a scope reaches the log with its stack trace`() = runBlocking {
        val log = FakeDiagnosticsLog()
        val job = Job()
        val scope = CoroutineScope(job + diagnosticsExceptionHandler(log))

        scope.launch { throw IllegalStateException("boom") }.join()

        val entry = log.entries.single()
        assertEquals(DiagnosticKind.UNHANDLED_ERROR, entry.kind)
        assertEquals("java.lang.IllegalStateException: boom", entry.message)
        assertTrue(entry.stackTrace.orEmpty().contains("DiagnosticsHooksTest"))
    }

    @Test
    fun `a cancelled scope is not recorded as an error`() = runBlocking {
        val log = FakeDiagnosticsLog()
        val scope = CoroutineScope(Job() + diagnosticsExceptionHandler(log))

        scope.launch { kotlinx.coroutines.awaitCancellation() }.also { it.cancel() }.join()

        assertTrue(log.entries.isEmpty())
    }
}
