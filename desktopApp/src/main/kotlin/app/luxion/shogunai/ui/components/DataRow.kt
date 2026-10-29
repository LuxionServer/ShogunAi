package app.luxion.shogunai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.luxion.shogunai.ui.theme.Eyebrow
import app.luxion.shogunai.ui.theme.RowShape
import app.luxion.shogunai.ui.theme.ShogunTheme

sealed interface CompactBehavior {
    data object Keep : CompactBehavior

    data object Hide : CompactBehavior

    data class Shrink(val width: Dp) : CompactBehavior
}

data class ColumnSpec(
    val width: Dp? = null,
    val weight: Float = 1f,
    val compact: CompactBehavior = CompactBehavior.Keep,
) {
    fun isShown(compact: Boolean): Boolean = !compact || this.compact != CompactBehavior.Hide

    fun widthFor(compact: Boolean): Dp? {
        val shrink = this.compact as? CompactBehavior.Shrink
        return if (compact && shrink != null) shrink.width else width
    }
}

class DataCell(
    val spec: ColumnSpec,
    val modifier: Modifier = Modifier,
    val content: @Composable (compact: Boolean) -> Unit,
)

enum class RowLines(val minHeight: Dp) {
    ONE(32.dp),
    TWO(48.dp),
}

class ActionSlot(
    val width: Dp,
    val pinned: Boolean = false,
    val alwaysVisible: Boolean = false,
    val content: @Composable RowScope.() -> Unit,
)

@Composable
fun DataRow(
    cells: List<DataCell>,
    modifier: Modifier = Modifier,
    lines: RowLines = RowLines.TWO,
    compact: Boolean = false,
    selected: Boolean = false,
    onSelect: (() -> Unit)? = null,
    actions: ActionSlot? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    var focusInside by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = lines.minHeight)
            .hoverable(interactionSource)
            .onFocusChanged { focusInside = it.hasFocus }
            .then(if (onSelect != null) Modifier.interactive(onClick = onSelect) else Modifier)
            .background(if (selected) ShogunTheme.colors.accentContainer else Color.Transparent, RowShape)
            .padding(horizontal = Spacing.smd, vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        cells.filter { it.spec.isShown(compact) }.forEach { cell ->
            Box(modifier = cellModifier(cell.spec, compact).then(cell.modifier), contentAlignment = Alignment.CenterStart) {
                cell.content(compact)
            }
        }
        actions?.let { slot ->
            Row(
                modifier = Modifier.width(slot.width),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (slot.alwaysVisible || slot.pinned || hovered || focusInside) slot.content(this)
            }
        }
    }
}

@Composable
fun DataHeader(
    labels: List<Pair<ColumnSpec, String>>,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    actionSlotWidth: Dp? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.smd, vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        labels.filter { it.first.isShown(compact) }.forEach { (spec, label) ->
            Box(modifier = cellModifier(spec, compact)) { Eyebrow(label) }
        }
        actionSlotWidth?.let { Box(Modifier.width(it)) }
    }
}

private fun RowScope.cellModifier(spec: ColumnSpec, compact: Boolean): Modifier =
    spec.widthFor(compact)?.let { Modifier.width(it) } ?: Modifier.weight(spec.weight)
