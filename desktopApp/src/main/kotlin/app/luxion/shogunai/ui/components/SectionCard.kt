package app.luxion.shogunai.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Card with a title header wrapping [content].
 *
 * Pass [collapsible] = true to make the header clickable and hide [content] when [expanded]
 * is false; the caller owns [expanded]/[onExpandedChange] state.
 */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    collapsible: Boolean = false,
    expanded: Boolean = true,
    onExpandedChange: (Boolean) -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    OutlinedCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            if (collapsible) {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onExpandedChange(!expanded) },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                        contentDescription = if (expanded) "Contraer" else "Expandir",
                    )
                }
            } else {
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            if (!collapsible || expanded) {
                content()
            }
        }
    }
}
