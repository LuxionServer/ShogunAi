package app.luxion.shogunai.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.luxion.shogunai.ui.theme.ShogunTheme
import app.luxion.shogunai.ui.theme.Status

enum class AgentPhase(val status: Status) {
    NEEDS_YOU(Status.WARNING),
    DONE(Status.SUCCESS),
    WORKING(Status.INFO),
    RUNNING(Status.INFO),
    IDLE(Status.NEUTRAL),
}

data class AgentIndicatorModel(
    val phase: AgentPhase,
    val label: String,
    val moreCount: Int,
    val tooltip: String,
    val focusable: Boolean = false,
)

enum class AgentIndicatorSize(val icon: Dp) {
    SMALL(12.dp),
    MEDIUM(14.dp),
    LARGE(16.dp),
}

@Composable
fun AgentIndicator(
    model: AgentIndicatorModel,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    size: AgentIndicatorSize = AgentIndicatorSize.MEDIUM,
    onClick: (() -> Unit)? = null,
) {
    val description = if (model.moreCount > 0) "${model.label} +${model.moreCount}" else model.label
    val clickable = onClick?.let { Modifier.semantics { role = Role.Button }.interactive(onClick = it) } ?: Modifier
    WithTooltip(label = model.tooltip, modifier = modifier) {
        Row(
            modifier = clickable.clearAndSetSemantics { contentDescription = description },
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.SmartToy,
                contentDescription = null,
                tint = ShogunTheme.status.of(model.phase.status).color,
                modifier = Modifier.size(size.icon),
            )
            if (!compact) {
                Text(
                    model.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (model.moreCount > 0) {
                    Text("+${model.moreCount}", style = ShogunTheme.typography.dataSmall, maxLines = 1)
                }
            }
        }
    }
}
