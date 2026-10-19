package app.luxion.shogunai.ui.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.luxion.shogunai.ui.components.rememberAppIconPainter
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import app.luxion.shogunai.domain.model.AppLanguage
import app.luxion.shogunai.domain.model.ThemeMode
import app.luxion.shogunai.ui.components.DropdownSelector
import app.luxion.shogunai.ui.components.SegmentedSelector
import app.luxion.shogunai.ui.components.Spacing
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.i18n.Strings
import app.luxion.shogunai.ui.i18n.strings

private val PreferencesWindowSize = DpSize(440.dp, 430.dp)

fun ThemeMode.label(strings: Strings): String = when (this) {
    ThemeMode.LIGHT -> strings.themeLight
    ThemeMode.DARK -> strings.themeDark
    ThemeMode.SYSTEM -> strings.themeSystem
}

@Composable
fun PreferencesWindow(appearance: Appearance, tools: ToolsPreferences, bringToFrontRequests: Int, onClose: () -> Unit) {
    Window(
        onCloseRequest = onClose,
        title = appearance.strings.preferencesTitle,
        icon = rememberAppIconPainter(),
        resizable = false,
        state = rememberWindowState(size = PreferencesWindowSize, position = WindowPosition(Alignment.Center)),
    ) {
        LaunchedEffect(bringToFrontRequests) { window.toFront() }
        LaunchedEffect(Unit) { tools.refresh() }
        AppearanceScope(appearance) {
            PreferencesContent(appearance, tools)
        }
    }
}

@Composable
private fun PreferencesContent(appearance: Appearance, tools: ToolsPreferences) {
    val strings = LocalStrings.current
    Column(
        modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(strings.appearanceLabel, style = MaterialTheme.typography.labelLarge)
        SegmentedSelector(
            options = ThemeMode.entries,
            selected = appearance.themeMode,
            onSelect = appearance::changeThemeMode,
            label = { it.label(strings) },
        )
        Text(
            strings.languageLabel,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = Spacing.md),
        )
        DropdownSelector(
            options = AppLanguage.entries,
            selected = appearance.language,
            onSelect = appearance::changeLanguage,
            label = { it.strings().languageName },
            placeholder = strings.languageLabel,
            modifier = Modifier.fillMaxWidth(),
        )
        ToolsSection(tools)
    }
}

@Composable
fun ToolsSection(tools: ToolsPreferences) {
    val strings = LocalStrings.current
    Text(
        strings.toolsLabel,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(top = Spacing.md),
    )
    ToolChoice(
        title = strings.terminalToolLabel,
        options = tools.availableTerminals,
        selected = tools.terminalShown,
        onSelect = tools::chooseTerminal,
        name = { it.displayName },
        noneFound = strings.noTerminalFound,
        isDetected = tools.detected != null,
    )
    ToolChoice(
        title = strings.editorToolLabel,
        options = tools.availableEditors,
        selected = tools.editorShown,
        onSelect = tools::chooseEditor,
        name = { it.displayName },
        noneFound = strings.noEditorFound,
        isDetected = tools.detected != null,
    )
}

@Composable
private fun <T> ToolChoice(
    title: String,
    options: List<T>,
    selected: T?,
    onSelect: (T) -> Unit,
    name: (T) -> String,
    noneFound: String,
    isDetected: Boolean,
) {
    Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (options.isEmpty()) {
        if (isDetected) Text(noneFound, style = MaterialTheme.typography.bodyMedium)
        return
    }
    DropdownSelector(
        options = options,
        selected = selected,
        onSelect = onSelect,
        label = name,
        placeholder = title,
        modifier = Modifier.fillMaxWidth(),
    )
}
