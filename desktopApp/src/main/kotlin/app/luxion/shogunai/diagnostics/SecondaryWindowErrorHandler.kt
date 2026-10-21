package app.luxion.shogunai.diagnostics

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.WindowExceptionHandler
import androidx.compose.ui.window.WindowExceptionHandlerFactory
import app.luxion.shogunai.domain.io.DiagnosticsLog
import kotlinx.coroutines.CancellationException

class SecondaryWindowErrorHandler(
    private val log: DiagnosticsLog,
    private val onClosed: () -> Unit,
    private val onReport: () -> Unit,
) {
    fun handle(error: Throwable) {
        if (error is CancellationException) return
        log.record(unhandledEntry(error))
        onClosed()
        onReport()
    }
}

@OptIn(ExperimentalComposeUiApi::class)
fun SecondaryWindowErrorHandler.asWindowExceptionHandlerFactory(): WindowExceptionHandlerFactory =
    WindowExceptionHandlerFactory {
        WindowExceptionHandler { error -> handle(error) }
    }
