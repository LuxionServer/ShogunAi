package app.luxion.shogunai.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class CancellationTest {

    @Test
    fun `a block that succeeds returns its value`() = runTest {
        assertEquals(Result.success(42), runSuspendCatching { delay(1); 42 })
    }

    @Test
    fun `a block that fails returns the failure`() = runTest {
        val failure = runSuspendCatching { error("boom") }.exceptionOrNull()

        assertIs<IllegalStateException>(failure)
        assertEquals("boom", failure.message)
    }

    @Test
    fun `a cancelled block rethrows the cancellation`() = runTest {
        val cancellation = CancellationException("cancelled")

        val thrown = assertFailsWith<CancellationException> { runSuspendCatching { throw cancellation } }

        assertEquals(cancellation, thrown)
    }
}
