package app.luxion.shogunai.ui.machine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.luxion.shogunai.ui.components.ColumnSpec
import app.luxion.shogunai.ui.components.DataCell
import app.luxion.shogunai.ui.components.DataRow
import app.luxion.shogunai.ui.components.RowLines
import app.luxion.shogunai.ui.components.Spacing
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.theme.ShogunTheme

private val OverviewNameColumn = ColumnSpec(width = 200.dp)
private val OverviewFigureColumn = ColumnSpec(width = 140.dp)
private val OverviewIconSize = 16.dp

@Composable
fun MachineOverviewView(items: List<MachineStatusItem>, providersDeclared: Boolean, onOpen: (MachineResource) -> Unit) {
    val strings = LocalStrings.current
    MachineViewFrame(strings.machineTitle) {
        items.forEach { item ->
            HorizontalDivider()
            OverviewRow(item) { onOpen(item.resource) }
        }
        if (!providersDeclared) {
            HorizontalDivider()
            MachineSecondaryText(strings.machineOverviewNoProviders, Modifier.testTag("machine-overview-no-providers"))
        }
    }
}

@Composable
private fun OverviewRow(item: MachineStatusItem, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val attention = item.attention?.let { ShogunTheme.status.of(it).color }
    DataRow(
        cells = listOf(
            DataCell(OverviewNameColumn) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Icon(item.resource.icon, contentDescription = null, tint = attention ?: colors.onSurfaceVariant, modifier = Modifier.size(OverviewIconSize))
                    Text(item.title, style = MaterialTheme.typography.bodyMedium, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            },
            DataCell(OverviewFigureColumn) {
                MachineDataText(item.figure ?: NotMeasuredFigure, color = attention ?: colors.onSurface)
            },
            DataCell(MachineMainColumn) { MachineStateText(item.summary) },
        ),
        modifier = Modifier.testTag("machine-overview-${item.resource.name}"),
        lines = RowLines.ONE,
        onSelect = onClick,
    )
}

const val NotMeasuredFigure = "–"
