package app.luxion.shogunai.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import app.luxion.shogunai.ui.theme.ShogunTheme

@Composable
fun ViewTitle(text: String, modifier: Modifier = Modifier) {
    HeadingText(text, ShogunTheme.typography.viewTitle, modifier)
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    HeadingText(text, ShogunTheme.typography.sectionTitle, modifier, maxLines = Int.MAX_VALUE)
}

@Composable
fun GroupTitle(text: String, modifier: Modifier = Modifier) {
    HeadingText(text, ShogunTheme.typography.sectionTitle, modifier)
}

@Composable
private fun HeadingText(text: String, style: TextStyle, modifier: Modifier, maxLines: Int = 1) {
    Text(
        text,
        style = style,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.semantics { heading() },
    )
}
