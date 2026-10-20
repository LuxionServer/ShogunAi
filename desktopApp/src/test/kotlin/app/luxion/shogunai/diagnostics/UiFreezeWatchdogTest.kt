package app.luxion.shogunai.diagnostics

import app.luxion.shogunai.domain.model.DiagnosticKind
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class UiFreezeWatchdogTest {

    private val queue = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "fake-ui").apply { isDaemon = true } }
    private val log = FakeDiagnosticsLog()
    private var uiThread: Thread? = null
    private val watchdog = UiFreezeWatchdog(
        log = log,
        post = { queue.execute(it) },
        uiThread = { uiThread },
        probeInterval = 20.milliseconds,
        threshold = 200.milliseconds,
    )

    init {
        queue.submit { uiThread = Thread.currentThread() }.get()
    }

    @AfterTest
    fun stop() {
        watchdog.stop()
        queue.shutdownNow()
    }

    private fun block(millis: Long) {
        queue.execute { Thread.sleep(millis) }
    }

    @Test
    fun `records one entry with the duration and the ui stack for a long freeze`() {
        watchdog.start()
        block(800)

        waitUntil { log.entries.isNotEmpty() }
        Thread.sleep(300)

        val entry = log.entries.single()
        assertEquals(DiagnosticKind.FREEZE, entry.kind)
        assertTrue(entry.message.startsWith("UI thread unresponsive for"))
        assertTrue(entry.stackTrace.orEmpty().contains("sleep"))
    }

    @Test
    fun `records nothing for a short pause`() {
        watchdog.start()
        block(80)

        Thread.sleep(600)

        assertTrue(log.entries.isEmpty())
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!condition() && System.nanoTime() < deadline) Thread.sleep(10)
    }
}
