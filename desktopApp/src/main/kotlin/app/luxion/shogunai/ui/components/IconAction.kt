package app.luxion.shogunai.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import app.luxion.shogunai.ui.theme.ControlShape
import app.luxion.shogunai.ui.theme.ShogunTheme
import androidx.compose.foundation.layout.size

private const val TooltipDelayMillis = 500
private val IconActionSize = 28.dp
private val IconActionIconSize = 18.dp
private val TooltipOffset = DpOffset(0.dp, 8.dp)
private val TooltipAboveOffset = DpOffset(0.dp, (-8).dp)

enum class TooltipSide { Below, Above }

@OptIn(ExperimentalFoundationApi::class)
private fun TooltipSide.placement(): TooltipPlacement = when (this) {
    TooltipSide.Below -> TooltipPlacement.ComponentRect(offset = TooltipOffset)
    TooltipSide.Above -> TooltipPlacement.ComponentRect(
        anchor = Alignment.TopStart,
        alignment = Alignment.TopEnd,
        offset = TooltipAboveOffset,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WithTooltip(
    label: String,
    modifier: Modifier = Modifier,
    side: TooltipSide = TooltipSide.Below,
    content: @Composable () -> Unit,
) {
    TooltipArea(
        tooltip = {
            Surface(
                shape = MaterialTheme.shapes.extraSmall,
                color = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                )
            }
        },
        delayMillis = TooltipDelayMillis,
        tooltipPlacement = side.placement(),
        modifier = modifier,
        content = content,
    )
}

@Composable
fun IconAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = LocalContentColor.current,
) {
    WithTooltip(label = label, modifier = modifier) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            colors = IconButtonDefaults.iconButtonColors(contentColor = tint, disabledContentColor = ShogunTheme.colors.disabledContent),
            shape = ControlShape,
            modifier = Modifier.focusRing(ControlShape).handPointer(enabled).size(IconActionSize),
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(IconActionIconSize))
        }
    }
}
