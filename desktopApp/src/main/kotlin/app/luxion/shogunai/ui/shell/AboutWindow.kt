package app.luxion.shogunai.ui.shell

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.luxion.shogunai.ui.components.rememberAppIconPainter
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import app.luxion.shogunai.BuildInfo
import app.luxion.shogunai.ui.components.NeutralTextButton
import app.luxion.shogunai.ui.components.Spacing
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.theme.ShogunTheme
import java.awt.Desktop
import java.net.URI

private const val RepositoryUrl = "https://github.com/LuxionServer/ShogunAi"
private val AboutWindowSize = DpSize(360.dp, 340.dp)
private val AboutIconSize = 72.dp

@Composable
fun AboutWindow(appearance: Appearance, onClose: () -> Unit) {
    DialogWindow(
        onCloseRequest = onClose,
        title = appearance.strings.aboutTitle,
        icon = rememberAppIconPainter(),
        resizable = false,
        state = rememberDialogState(size = AboutWindowSize),
    ) {
        AppearanceScope(appearance) {
            AboutContent()
        }
    }
}

@Composable
private fun AboutContent() {
    val strings = LocalStrings.current
    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.CenterVertically),
    ) {
        Image(rememberAppIconPainter(), contentDescription = null, modifier = Modifier.size(AboutIconSize))
        Text("ShogunAi", style = MaterialTheme.typography.titleLarge)
        Text(
            strings.aboutTagline,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Text(
            strings.aboutVersion(BuildInfo.VERSION),
            style = ShogunTheme.typography.dataSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        NeutralTextButton(onClick = ::openRepository) { Text(strings.aboutRepositoryLink) }
    }
}

private fun openRepository() {
    if (!Desktop.isDesktopSupported()) return
    val desktop = Desktop.getDesktop()
    if (desktop.isSupported(Desktop.Action.BROWSE)) desktop.browse(URI(RepositoryUrl))
}
