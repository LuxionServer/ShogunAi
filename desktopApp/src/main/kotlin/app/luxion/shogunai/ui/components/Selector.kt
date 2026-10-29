package app.luxion.shogunai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.luxion.shogunai.ui.theme.ControlShape
import app.luxion.shogunai.ui.theme.ShogunTheme

@Composable
fun <T> SegmentedSelector(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .height(SegmentHeight)
            .border(SegmentBorderWidth, colors.outlineVariant, ControlShape)
            .clip(ControlShape)
            .selectableGroup(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEachIndexed { index, option ->
            if (index > 0) VerticalDivider(color = colors.outlineVariant)
            Segment(label(option), isSelected = selected == option, enabled = enabled) { onSelect(option) }
        }
    }
}

@Composable
private fun Segment(text: String, isSelected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val content = when {
        !enabled -> ShogunTheme.colors.disabledContent
        isSelected -> ShogunTheme.colors.onAccentContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .background(if (isSelected) ShogunTheme.colors.accentContainer else Color.Transparent)
            .focusRing(ControlShape)
            .handPointer(enabled)
            .selectable(selected = isSelected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = SegmentHorizontalPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = content,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.width(IntrinsicSize.Max),
        )
    }
}

private val SegmentHeight = 28.dp
private val SegmentHorizontalPadding = 10.dp
private val SegmentBorderWidth = 1.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> DropdownSelector(
    options: List<T>,
    selected: T?,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: (T) -> Boolean = { true },
    groupBy: (T) -> String? = { null },
    expandedGroups: MutableMap<String, Boolean> = remember { mutableStateMapOf() },
    fieldLabel: String? = null,
    fieldEnabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        groupBy(selected ?: return@LaunchedEffect)?.let { group -> expandedGroups.putIfAbsent(group, true) }
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it && fieldEnabled },
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            fieldLabel?.let { FieldLabel(it, fieldEnabled) }
            FieldInput(
                value = selected?.let(label) ?: "",
                onValueChange = {},
                readOnly = true,
                enabled = fieldEnabled,
                description = fieldLabel,
                placeholder = placeholder,
                trailing = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, fieldEnabled).fillMaxWidth().handPointer(fieldEnabled),
            )
        }
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            groupOptions(options, groupBy).forEach { (group, groupOptions) ->
                if (group != null) {
                    val isExpanded = expandedGroups[group] ?: false
                    DropdownMenuItem(
                        text = { Text(group, style = MaterialTheme.typography.labelMedium) },
                        leadingIcon = {
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                                contentDescription = null,
                            )
                        },
                        onClick = { expandedGroups[group] = !isExpanded },
                        modifier = Modifier.handPointer(),
                    )
                }

                if (group == null || expandedGroups[group] == true) {
                    groupOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(label(option)) },
                            enabled = enabled(option),
                            modifier = Modifier.handPointer(enabled(option)),
                            onClick = {
                                if (enabled(option)) {
                                    onSelect(option)
                                    expanded = false
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

internal fun <T> groupOptions(options: List<T>, groupBy: (T) -> String?): List<Pair<String?, List<T>>> =
    options.groupBy(groupBy).toList()
