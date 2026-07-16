package app.luxion.shogunai.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay
import org.jetbrains.skiko.SystemTheme
import org.jetbrains.skiko.currentSystemTheme

// Compose Desktop's isSystemInDarkTheme() doesn't react to hot changes in the system
// theme (its CompositionLocal is computed once and stays fixed). Skiko's currentSystemTheme
// does query the real OS state on every call, so it's polled periodically instead.
@Composable
fun rememberSystemInDarkTheme(): State<Boolean> =
    produceState(initialValue = currentSystemTheme == SystemTheme.DARK) {
        while (true) {
            value = currentSystemTheme == SystemTheme.DARK
            delay(1000)
        }
    }
