package app.luxion.shogunai.ui.tray

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.MenuScope
import androidx.compose.ui.window.Tray

@Composable
fun ApplicationScope.ShogunTray(
    entries: List<TrayEntry>,
    icon: Painter,
    onAction: (TrayAction) -> Unit,
) {
    Tray(
        icon = icon,
        tooltip = "ShogunAi",
        onAction = { onAction(TrayAction.ShowWindow) },
        menu = { render(entries, onAction) },
    )
}

@Composable
private fun MenuScope.render(entries: List<TrayEntry>, onAction: (TrayAction) -> Unit) {
    entries.forEach { entry ->
        when (entry) {
            is TrayEntry.Item -> Item(entry.label, onClick = { onAction(entry.action) })
            is TrayEntry.Submenu -> Menu(entry.label) { render(entry.entries, onAction) }
            is TrayEntry.Heading -> Item(entry.label, enabled = false, onClick = {})
            TrayEntry.Separator -> Separator()
        }
    }
}
