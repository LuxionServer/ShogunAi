package app.luxion.shogunai.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.luxion.shogunai.ui.theme.ControlShape

const val MaxActionBarIcons = 3
private val SplitGap = 1.dp
private val SplitIconSize = 18.dp

data class BarIcon(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
    val disabledReason: String? = null,
    val confirmsInPlace: Boolean = false,
)

@Composable
fun ActionBar(
    primary: @Composable () -> Unit,
    icons: List<BarIcon>,
    moreLabel: String,
    more: List<MenuEntry>,
    modifier: Modifier = Modifier,
) {
    require(icons.size <= MaxActionBarIcons) { "An action bar holds at most $MaxActionBarIcons icons" }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        primary()
        icons.forEach { key(it.label) { BarIconAction(it) } }
        Spacer(Modifier.weight(1f))
        if (more.isNotEmpty()) MoreMenuButton(label = moreLabel, entries = more)
    }
}

@Composable
private fun BarIconAction(icon: BarIcon) {
    if (icon.confirmsInPlace) {
        InlineConfirmIconAction(label = icon.label, onClick = icon.onClick)
        return
    }
    DisabledReason(icon.disabledReason) {
        IconAction(icon = icon.icon, label = icon.label, onClick = icon.onClick, enabled = icon.disabledReason == null)
    }
}

@Composable
fun MoreMenuButton(label: String, entries: List<MenuEntry>, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        IconAction(icon = Icons.Default.MoreHoriz, label = label, onClick = { expanded = true })
        ShogunMenu(expanded = expanded, onDismiss = { expanded = false }, entries = entries)
    }
}

@Composable
fun SplitButton(
    label: String,
    onClick: () -> Unit,
    menuLabel: String,
    menu: List<MenuEntry>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    disabledReason: String? = null,
    tooltip: String? = null,
    busy: Boolean = false,
    openMenuRequests: Int = 0,
) {
    var expanded by remember { mutableStateOf(false) }
    var focusFirst by remember { mutableStateOf(false) }
    val initialRequests = remember { openMenuRequests }
    LaunchedEffect(openMenuRequests) {
        if (openMenuRequests != initialRequests) {
            focusFirst = true
            expanded = true
        }
    }
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(SplitGap), verticalAlignment = Alignment.CenterVertically) {
        DisabledReason(disabledReason) {
            WithTooltipOrNot(tooltip.takeIf { disabledReason == null }) {
                PrimaryButton(onClick = onClick, enabled = enabled && disabledReason == null) {
                    Text(label)
                    if (busy) InlineProgress(size = ProgressSize.Small)
                }
            }
        }
        Box {
            WithTooltip(label = menuLabel) {
                SplitMenuPart(label = menuLabel) {
                    focusFirst = false
                    expanded = true
                }
            }
            ShogunMenu(expanded = expanded, onDismiss = { expanded = false }, entries = menu, focusFirst = focusFirst)
        }
    }
}

@Composable
private fun SplitMenuPart(label: String, onClick: () -> Unit) {
    val colors = primaryButtonColors()
    Surface(
        onClick = onClick,
        shape = ControlShape,
        color = colors.container,
        contentColor = colors.content,
        modifier = Modifier
            .focusRing(ControlShape)
            .handPointer()
            .size(ButtonSize.Compact.height)
            .semantics { contentDescription = label },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(SplitIconSize))
        }
    }
}

@Composable
private fun WithTooltipOrNot(label: String?, content: @Composable () -> Unit) {
    if (label == null) content() else WithTooltip(label = label, content = content)
}
