package app.luxion.shogunai.diagnostics

import app.luxion.shogunai.domain.io.DiagnosticsLog
import app.luxion.shogunai.domain.model.DiagnosticEntry
import app.luxion.shogunai.domain.model.DiagnosticKind
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

class UiFreezeWatchdog(
    private val log: DiagnosticsLog,
    private val post: (Runnable) -> Unit,
    private val uiThread: () -> Thread?,
    private val probeInterval: Duration = 500.milliseconds,
    private val threshold: Duration = 2.seconds,
) {
    @Volatile
    private var running = false

    fun start() {
        if (running) return
        running = true
        Thread(::watch, "ui-freeze-watchdog").apply { isDaemon = true }.start()
    }

    fun stop() {
        running = false
    }

    private fun watch() {
        while (running) {
            val startedAt = System.nanoTime()
            val answered = AtomicBoolean(false)
            post(Runnable { answered.set(true) })
            var stack: String? = null
            while (running && !answered.get()) {
                Thread.sleep(probeInterval.inWholeMilliseconds)
                val blocked = (System.nanoTime() - startedAt).nanoseconds
                if (!answered.get() && stack == null && blocked >= threshold) stack = captureStack()
            }
            if (stack != null && answered.get()) record((System.nanoTime() - startedAt).nanoseconds, stack)
            if (running) Thread.sleep(probeInterval.inWholeMilliseconds)
        }
    }

    private fun captureStack(): String =
        uiThread()?.stackTrace?.joinToString("\n") { "\tat $it" }.orEmpty()

    private fun record(duration: Duration, stack: String) {
        log.record(
            DiagnosticEntry(
                timestamp = Instant.now(),
                kind = DiagnosticKind.FREEZE,
                message = "UI thread unresponsive for ${duration.inWholeMilliseconds} ms",
                stackTrace = stack.ifEmpty { null },
            ),
        )
    }
}
