package app.luxion.shogunai.ui.components

import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SegmentedSelector(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = selected == option,
                onClick = { onSelect(option) },
                enabled = enabled,
                modifier = Modifier.handPointer(enabled),
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                icon = {},
                label = {
                    Text(
                        label(option),
                        modifier = Modifier.width(IntrinsicSize.Max),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }
}

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
        OutlinedTextField(
            value = selected?.let(label) ?: "",
            onValueChange = {},
            readOnly = true,
            enabled = fieldEnabled,
            placeholder = { Text(placeholder) },
            label = fieldLabel?.let { { Text(it) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, fieldEnabled).fillMaxWidth().handPointer(fieldEnabled),
        )
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
