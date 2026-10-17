package app.luxion.shogunai.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.luxion.shogunai.domain.model.ThemeMode
import app.luxion.shogunai.ui.components.NeutralTextButton
import app.luxion.shogunai.ui.components.handPointer
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.i18n.Strings

private fun ThemeMode.label(strings: Strings): String = when (this) {
    ThemeMode.LIGHT -> strings.themeLight
    ThemeMode.DARK -> strings.themeDark
    ThemeMode.SYSTEM -> strings.themeSystem
}

@Composable
fun ThemeModeToggle(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        NeutralTextButton(onClick = { expanded = true }) {
            Text(themeMode.label(strings))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ThemeMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(mode.label(strings)) },
                    modifier = Modifier.handPointer(),
                    onClick = {
                        onThemeModeChange(mode)
                        expanded = false
                    },
                )
            }
        }
    }
}
