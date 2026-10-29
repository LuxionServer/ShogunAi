package app.luxion.shogunai.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.toUpperCase

@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier) {
    Text(
        text.toUpperCase(Locale.current),
        style = ShogunTheme.typography.eyebrow,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier = modifier,
    )
}
