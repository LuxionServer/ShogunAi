package app.luxion.shogunai.ui.shell

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.luxion.shogunai.ui.components.Spacing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class MessageAction(val label: String, val onAction: () -> Unit)

class AppMessages(private val scope: CoroutineScope) {
    val hostState = SnackbarHostState()
    private var current: Job? = null

    fun show(message: String, action: MessageAction? = null) {
        current?.cancel()
        current = scope.launch {
            val result = withTimeoutOrNull(durationFor(action)) {
                hostState.showSnackbar(message, action?.label, duration = SnackbarDuration.Indefinite)
            }
            if (result == SnackbarResult.ActionPerformed) action?.onAction?.invoke()
        }
    }

    companion object {
        val SHORT_DURATION = 4.seconds
        val WITH_ACTION_DURATION = 8.seconds

        fun durationFor(action: MessageAction?): Duration = if (action == null) SHORT_DURATION else WITH_ACTION_DURATION
    }
}

val LocalAppMessages = staticCompositionLocalOf<AppMessages> { error("AppMessages is not provided") }

@Composable
fun rememberAppMessages(): AppMessages {
    val scope = rememberCoroutineScope()
    return remember(scope) { AppMessages(scope) }
}

private val MessageMaxWidth = 560.dp

@Composable
fun AppMessagesHost(messages: AppMessages, modifier: Modifier = Modifier) {
    SnackbarHost(messages.hostState, modifier = modifier.padding(Spacing.md).widthIn(max = MessageMaxWidth)) { data ->
        Snackbar(data)
    }
}
