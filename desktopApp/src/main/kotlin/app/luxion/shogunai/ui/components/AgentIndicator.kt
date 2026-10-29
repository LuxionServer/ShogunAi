package app.luxion.shogunai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
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
)

private val AgentDotSize = 8.dp

const val AgentIndicatorTag = "agent-indicator"

@Composable
fun AgentIndicator(model: AgentIndicatorModel, modifier: Modifier = Modifier, compact: Boolean = false) {
    val description = if (model.moreCount > 0) "${model.label} +${model.moreCount}" else model.label
    WithTooltip(label = model.tooltip, modifier = modifier) {
        Row(
            modifier = Modifier.clearAndSetSemantics { contentDescription = description },
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(AgentDotSize).background(ShogunTheme.status.of(model.phase.status).color, CircleShape))
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
