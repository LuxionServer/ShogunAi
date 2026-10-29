package app.luxion.shogunai.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.theme.ShogunTheme

private val FieldHeight = 32.dp
private val FieldHorizontalPadding = 10.dp
private val FieldVerticalPadding = 6.dp
private val FieldBorderWidth = 1.dp
private val FieldFocusedBorderWidth = 2.dp
private val FieldIconSize = 16.dp
private val FieldIconPadding = 2.dp

@Composable
fun Field(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    help: String? = null,
    error: String? = null,
    isError: Boolean = error != null,
    placeholder: String? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    inputModifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        label?.let { FieldLabel(it, enabled) }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            FieldInput(
                value = value,
                onValueChange = onValueChange,
                description = label,
                error = error,
                isError = isError,
                placeholder = placeholder,
                enabled = enabled,
                readOnly = readOnly,
                singleLine = singleLine,
                maxLines = maxLines,
                textStyle = textStyle,
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                leading = leading,
                trailing = trailing,
                modifier = Modifier.weight(1f).then(inputModifier),
            )
            action?.invoke()
        }
        FieldSupportingText(help = help, error = error)
    }
}

@Composable
fun PathField(
    value: String,
    onValueChange: (String) -> Unit,
    onBrowse: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    help: String? = null,
    error: String? = null,
    placeholder: String? = null,
    enabled: Boolean = true,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    inputModifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    Field(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = label,
        help = help,
        error = error,
        placeholder = placeholder,
        enabled = enabled,
        textStyle = textStyle,
        inputModifier = inputModifier,
        action = { SecondaryButton(onClick = onBrowse, enabled = enabled, size = ButtonSize.Regular) { Text(strings.browse) } },
    )
}

@Composable
internal fun FieldLabel(text: String, enabled: Boolean = true) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else ShogunTheme.colors.disabledContent,
    )
}

@Composable
internal fun FieldSupportingText(help: String?, error: String?) {
    val text = error ?: help ?: return
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
internal fun FieldInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    error: String? = null,
    isError: Boolean = error != null,
    placeholder: String? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    maxLines: Int = 1,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val borderColor = when {
        isError -> colors.error
        focused -> ShogunTheme.colors.accent
        else -> colors.outline
    }
    val contentColor = if (enabled) colors.onSurface else ShogunTheme.colors.disabledContent
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        readOnly = readOnly,
        singleLine = singleLine,
        maxLines = maxLines,
        textStyle = textStyle.copy(color = contentColor),
        cursorBrush = SolidColor(ShogunTheme.colors.accent),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        interactionSource = interactionSource,
        modifier = modifier
            .trackTextInputFocus()
            .semantics {
                description?.let { contentDescription = it }
                error?.let { error(it) }
            },
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .heightIn(min = FieldHeight)
                    .border(if (focused) FieldFocusedBorderWidth else FieldBorderWidth, borderColor, MaterialTheme.shapes.extraSmall)
                    .padding(horizontal = FieldHorizontalPadding, vertical = FieldVerticalPadding),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                leading?.invoke()
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty() && placeholder != null) {
                        Text(placeholder, style = textStyle, color = colors.onSurfaceVariant, maxLines = 1)
                    }
                    innerTextField()
                }
                trailing?.invoke()
            }
        },
    )
}

@Composable
fun FieldIconAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    WithTooltip(label = label) {
        Icon(
            icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .interactive(shape = CircleShape, onClick = onClick)
                .padding(FieldIconPadding)
                .size(FieldIconSize),
        )
    }
}
