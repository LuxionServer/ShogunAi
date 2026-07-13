package app.luxion.shogunai.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay
import org.jetbrains.skiko.SystemTheme
import org.jetbrains.skiko.currentSystemTheme

// isSystemInDarkTheme() de Compose Desktop no reacciona a cambios en caliente del tema
// del sistema (su CompositionLocal se calcula una sola vez y queda fijo). currentSystemTheme
// de Skiko sí consulta el estado real del SO en cada llamada, así que se sondea periódicamente.
@Composable
fun rememberSystemInDarkTheme(): State<Boolean> =
    produceState(initialValue = currentSystemTheme == SystemTheme.DARK) {
        while (true) {
            value = currentSystemTheme == SystemTheme.DARK
            delay(1000)
        }
    }
