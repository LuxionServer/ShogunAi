package app.luxion.shogunai.diagnostics

import app.luxion.shogunai.domain.io.DiagnosticsLog
import app.luxion.shogunai.domain.model.DiagnosticEntry
import app.luxion.shogunai.domain.model.DiagnosticKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import java.time.Instant

fun unhandledEntry(error: Throwable): DiagnosticEntry =
    DiagnosticEntry(Instant.now(), DiagnosticKind.UNHANDLED_ERROR, error.toString(), error.stackTraceToString())

fun diagnosticsExceptionHandler(log: DiagnosticsLog): CoroutineExceptionHandler =
    CoroutineExceptionHandler { _, error ->
        if (error !is CancellationException) log.record(unhandledEntry(error))
    }

fun installUncaughtExceptionLogging(log: DiagnosticsLog) {
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, error ->
        log.record(unhandledEntry(error))
        previous?.uncaughtException(thread, error)
    }
}
