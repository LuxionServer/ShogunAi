package app.luxion.shogunai.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay
import org.jetbrains.skiko.SystemTheme
import org.jetbrains.skiko.currentSystemTheme

@Composable
fun rememberSystemInDarkTheme(): State<Boolean> =
    produceState(initialValue = currentSystemTheme == SystemTheme.DARK) {
        while (true) {
            value = currentSystemTheme == SystemTheme.DARK
            delay(1000)
        }
    }
