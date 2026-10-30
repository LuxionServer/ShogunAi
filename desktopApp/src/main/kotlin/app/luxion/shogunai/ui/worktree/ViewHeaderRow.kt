package app.luxion.shogunai.ui.worktree

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.luxion.shogunai.ui.actions.ActionSpec
import app.luxion.shogunai.ui.components.DisabledReason
import app.luxion.shogunai.ui.components.Field
import app.luxion.shogunai.ui.components.FieldIconAction
import app.luxion.shogunai.ui.components.GhostButton
import app.luxion.shogunai.ui.components.IconAction
import app.luxion.shogunai.ui.components.InlineProgress
import app.luxion.shogunai.ui.components.MenuEntry
import app.luxion.shogunai.ui.components.MoreMenuButton
import app.luxion.shogunai.ui.components.PrimaryButton
import app.luxion.shogunai.ui.components.Spacing
import app.luxion.shogunai.ui.components.ViewTitle
import app.luxion.shogunai.ui.components.WithTooltip
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.theme.ShogunLayout

class HeaderFilterState(
    val text: String,
    val onChange: (String) -> Unit,
    val onClear: () -> Unit,
    val findRequests: Int,
)

@Composable
internal fun ViewHeaderRow(
    title: String,
    isBusy: Boolean,
    filter: HeaderFilterState,
    viewMenu: List<MenuEntry>,
    cleanUp: ActionSpec?,
    overflow: List<MenuEntry>,
    primary: ActionSpec?,
    onPerform: (ActionSpec) -> Unit,
    icons: @Composable RowScope.() -> Unit = {},
) {
    val strings = LocalStrings.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val compact = maxWidth < ShogunLayout.compactBreakpoint
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ViewTitle(title, modifier = Modifier.weight(1f, fill = false))
                if (isBusy) WithTooltip(strings.readingWorktreeStatus) { InlineProgress() }
            }
            HeaderFilter(filter, compact)
            ViewMenuButton(viewMenu)
            icons()
            cleanUp?.let { spec ->
                DisabledReason(spec.disabledReason) {
                    GhostButton(onClick = { onPerform(spec) }, enabled = spec.isAvailable) { Text(spec.label, maxLines = 1) }
                }
            }
            if (overflow.isNotEmpty()) MoreMenuButton(label = strings.moreActions, entries = overflow)
            primary?.let { spec -> PrimaryAction(spec, compact) { onPerform(spec) } }
        }
    }
}

@Composable
private fun PrimaryAction(spec: ActionSpec, compact: Boolean, onClick: () -> Unit) {
    DisabledReason(spec.disabledReason) {
        if (compact) {
            WithTooltip(spec.label) {
                PrimaryButton(onClick = onClick, enabled = spec.isAvailable, modifier = Modifier.semantics { contentDescription = spec.label }) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(ButtonIconSize))
                }
            }
        } else {
            PrimaryButton(onClick = onClick, enabled = spec.isAvailable) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(ButtonIconSize))
                Text(spec.label, modifier = Modifier.padding(start = Spacing.sm), maxLines = 1)
            }
        }
    }
}

@Composable
private fun HeaderFilter(filter: HeaderFilterState, compact: Boolean) {
    val strings = LocalStrings.current
    var open by remember { mutableStateOf(false) }
    val findRequestsOnEntry = remember { filter.findRequests }
    LaunchedEffect(filter.findRequests) {
        if (filter.findRequests != findRequestsOnEntry) open = true
    }
    if (!compact || open || filter.text.isNotEmpty()) {
        TextFilterField(filter, focusOnStart = compact && open, onFocusLost = { if (filter.text.isEmpty()) open = false })
    } else {
        IconAction(icon = Icons.Default.Search, label = strings.filterWorktrees, onClick = { open = true })
    }
}

@Composable
private fun TextFilterField(filter: HeaderFilterState, focusOnStart: Boolean, onFocusLost: () -> Unit) {
    val strings = LocalStrings.current
    val focusRequester = remember { FocusRequester() }
    val findRequestsOnEntry = remember { filter.findRequests }
    var hadFocus by remember { mutableStateOf(false) }
    LaunchedEffect(filter.findRequests) {
        if (focusOnStart || filter.findRequests != findRequestsOnEntry) focusRequester.requestFocus()
    }
    Field(
        value = filter.text,
        onValueChange = filter.onChange,
        placeholder = strings.filterWorktreesPlaceholder,
        leading = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(ButtonIconSize)) },
        trailing = if (filter.text.isNotEmpty()) {
            { FieldIconAction(icon = Icons.Default.Close, label = strings.clearFilter, onClick = filter.onClear) }
        } else {
            null
        },
        textStyle = MaterialTheme.typography.bodySmall,
        modifier = Modifier.width(FilterFieldWidth),
        inputModifier = Modifier.focusRequester(focusRequester).onFocusChanged { state ->
            if (hadFocus && !state.isFocused) onFocusLost()
            hadFocus = state.isFocused
        },
    )
}

private val ButtonIconSize = 18.dp
private val FilterFieldWidth = 240.dp
