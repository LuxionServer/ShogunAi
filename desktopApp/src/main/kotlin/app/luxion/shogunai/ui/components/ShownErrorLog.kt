package app.luxion.shogunai.ui.components

import app.luxion.shogunai.domain.io.DiagnosticsLog
import app.luxion.shogunai.domain.model.DiagnosticEntry
import app.luxion.shogunai.domain.model.DiagnosticKind
import java.time.Instant

class ShownErrorLog(
    private val log: DiagnosticsLog,
    private val now: () -> Instant = Instant::now,
    private val remembered: Int = REMEMBERED_ERRORS,
) {
    private val recorded = ArrayDeque<Throwable>()

    fun record(error: Throwable, summary: String, details: String?) {
        if (!remember(error)) return
        log.record(DiagnosticEntry(now(), DiagnosticKind.SHOWN_ERROR, summary, details?.takeIf { it.isNotBlank() } ?: error.stackTraceToString()))
    }

    private fun remember(error: Throwable): Boolean = synchronized(recorded) {
        if (recorded.any { it === error }) return false
        recorded.addLast(error)
        if (recorded.size > remembered) recorded.removeFirst()
        true
    }

    private companion object {
        const val REMEMBERED_ERRORS = 256
    }
}
