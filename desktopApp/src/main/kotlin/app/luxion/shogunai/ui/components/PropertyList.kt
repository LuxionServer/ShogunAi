package app.luxion.shogunai.ui.components

import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.theme.ShogunTheme
import kotlinx.coroutines.launch

data class Property(val key: String, val value: String, val copyable: Boolean = false, val isData: Boolean = true)

private val PropertyKeyWidth = 112.dp
private val PropertyRowMinHeight = 24.dp
private val PropertyCopySlot = 28.dp

@Composable
fun PropertyList(
    properties: List<Property>,
    modifier: Modifier = Modifier,
    onCopy: ((String) -> Unit)? = null,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val copy = onCopy ?: { value: String -> scope.launch { clipboard.copyText(value) }; Unit }
    Column(modifier = modifier.fillMaxWidth()) {
        properties.forEach { property -> PropertyValueRow(property, copy) }
    }
}

@Composable
private fun PropertyValueRow(property: Property, onCopy: (String) -> Unit) {
    val strings = LocalStrings.current
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val confirm = key(property.value) { rememberInlineConfirm() }
    PropertyRow(property.key, Modifier.hoverable(interactionSource)) {
        SelectionContainer(Modifier.weight(1f)) {
            Text(
                property.value,
                style = if (property.isData) ShogunTheme.typography.dataSmall else MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (property.copyable) {
            Box(Modifier.size(PropertyCopySlot), contentAlignment = Alignment.Center) {
                if (hovered || confirm.confirmed) {
                    InlineConfirmIconAction(
                        label = strings.copyValue(property.key),
                        onClick = { onCopy(property.value) },
                        state = confirm,
                    )
                }
            }
        }
    }
}

@Composable
fun PropertyRow(key: String, modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = PropertyRowMinHeight),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            key,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(PropertyKeyWidth),
        )
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}
