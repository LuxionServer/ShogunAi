package app.luxion.shogunai.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.theme.Eyebrow
import app.luxion.shogunai.ui.theme.ShogunTheme

sealed interface SectionState {
    data object Content : SectionState

    data object Loading : SectionState

    data class Empty(val message: String) : SectionState

    data object NotApplicable : SectionState
}

private val SectionChevronSize = 16.dp

@Composable
fun InspectorSection(
    id: String,
    title: String,
    modifier: Modifier = Modifier,
    state: SectionState = SectionState.Content,
    summary: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (state == SectionState.NotApplicable) return
    val strings = LocalStrings.current
    var expanded by rememberSaveable(id) { mutableStateOf(true) }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .interactive { expanded = !expanded }
                .semantics { stateDescription = if (expanded) strings.expanded else strings.collapsed }
                .padding(vertical = Spacing.xxs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (expanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(SectionChevronSize),
            )
            Eyebrow(title, modifier = Modifier.weight(1f).semantics { heading() })
            summary?.let {
                Text(
                    it,
                    style = ShogunTheme.typography.dataSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (expanded) {
            when (state) {
                SectionState.Loading -> SkeletonRows(count = 1)
                is SectionState.Empty -> Text(
                    state.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> content()
            }
        }
    }
}
