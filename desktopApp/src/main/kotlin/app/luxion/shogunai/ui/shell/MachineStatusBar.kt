package app.luxion.shogunai.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import app.luxion.shogunai.ui.components.Spacing
import app.luxion.shogunai.ui.components.TooltipSide
import app.luxion.shogunai.ui.components.WithTooltip
import app.luxion.shogunai.ui.components.interactive
import app.luxion.shogunai.ui.machine.MachineResource
import app.luxion.shogunai.ui.machine.MachineStatusItem
import app.luxion.shogunai.ui.machine.icon
import app.luxion.shogunai.ui.theme.ShogunTheme

private val StatusBarHeight = 28.dp
private val StatusItemHeight = 22.dp
private val StatusIconSize = 14.dp
private const val NotMeasuredFigure = "–"

@Composable
fun MachineStatusBar(
    items: List<MachineStatusItem>,
    selected: MachineResource?,
    compact: Boolean,
    onSelect: (MachineResource) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(StatusBarHeight)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = Spacing.sm)
            .testTag("machine-status-bar"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        items.forEach { item ->
            MachineStatusEntry(item, selected = item.resource == selected, compact = compact) { onSelect(item.resource) }
        }
    }
}

@Composable
private fun MachineStatusEntry(item: MachineStatusItem, selected: Boolean, compact: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val attention = item.attention?.let { ShogunTheme.status.of(it).color }
    val neutral = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant
    WithTooltip(label = item.tooltip, side = TooltipSide.Above) {
        Row(
            modifier = Modifier
                .height(StatusItemHeight)
                .interactive(onClick = onClick)
                .background(if (selected) colors.secondaryContainer else colors.surfaceContainerLow)
                .clearAndSetSemantics {
                    role = Role.Button
                    contentDescription = item.tooltip
                    this.selected = selected
                    onClick { onClick(); true }
                    testTag = "machine-status-${item.resource.name}"
                }
                .padding(horizontal = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Icon(item.resource.icon, contentDescription = null, tint = attention ?: neutral, modifier = Modifier.size(StatusIconSize))
            if (!compact) {
                Text(item.name, style = MaterialTheme.typography.labelMedium, color = neutral, maxLines = 1, softWrap = false)
            }
            Text(
                item.figure ?: NotMeasuredFigure,
                style = ShogunTheme.typography.dataSmall,
                color = attention ?: if (selected) colors.onSecondaryContainer else colors.onSurface,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}
