package app.luxion.shogunai.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.luxion.shogunai.ui.theme.ControlShape
import app.luxion.shogunai.ui.theme.ShogunTheme

enum class ButtonSize(val height: Dp) {
    Compact(28.dp),
    Regular(32.dp),
}

data class ShogunButtonColors(
    val container: Color,
    val content: Color,
    val border: Color?,
    val disabledContainer: Color,
    val disabledContent: Color,
)

private val ButtonMinWidth = 56.dp
private val ButtonHorizontalPadding = 10.dp
private val ButtonContentGap = 6.dp
private val ButtonBorderWidth = 1.dp

private fun Modifier.singleLineLabel(): Modifier = this
    .layout { measurable, constraints ->
        val placeable = measurable.measure(constraints.copy(maxWidth = Constraints.Infinity))
        val width = placeable.width.coerceAtMost(constraints.maxWidth)
        layout(width, placeable.height) { placeable.place(0, 0) }
    }
    .widthIn(min = ButtonMinWidth)

@Composable
private fun ShogunButton(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    size: ButtonSize,
    colors: ShogunButtonColors,
    content: @Composable RowScope.() -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.focusRing(ControlShape).handPointer(enabled).singleLineLabel(),
        enabled = enabled,
        shape = ControlShape,
        color = if (enabled) colors.container else colors.disabledContainer,
        contentColor = if (enabled) colors.content else colors.disabledContent,
        border = colors.border?.let { BorderStroke(ButtonBorderWidth, it) },
    ) {
        ProvideTextStyle(MaterialTheme.typography.labelLarge) {
            Row(
                modifier = Modifier.height(size.height).padding(PaddingValues(horizontal = ButtonHorizontalPadding)),
                horizontalArrangement = Arrangement.spacedBy(ButtonContentGap, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }
    }
}

@Composable
private fun filledColors(container: Color, content: Color) = ShogunButtonColors(
    container = container,
    content = content,
    border = null,
    disabledContainer = ShogunTheme.colors.disabledContainer,
    disabledContent = ShogunTheme.colors.disabledContent,
)

@Composable
private fun textColors(content: Color) = ShogunButtonColors(
    container = Color.Transparent,
    content = content,
    border = null,
    disabledContainer = Color.Transparent,
    disabledContent = ShogunTheme.colors.disabledContent,
)

@Composable
fun primaryButtonColors(): ShogunButtonColors =
    filledColors(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)

@Composable
fun secondaryButtonColors(): ShogunButtonColors = ShogunButtonColors(
    container = MaterialTheme.colorScheme.surfaceContainerLowest,
    content = MaterialTheme.colorScheme.onSurface,
    border = MaterialTheme.colorScheme.outlineVariant,
    disabledContainer = Color.Transparent,
    disabledContent = ShogunTheme.colors.disabledContent,
)

@Composable
fun ghostButtonColors(): ShogunButtonColors = textColors(MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
fun dangerButtonColors(): ShogunButtonColors =
    filledColors(ShogunTheme.status.danger.color, ShogunTheme.status.danger.onColor)

@Composable
fun dangerTextButtonColors(): ShogunButtonColors = textColors(ShogunTheme.status.danger.color)

@Composable
fun neutralTextButtonColors(): ShogunButtonColors = textColors(MaterialTheme.colorScheme.onSurface)

@Composable
fun PrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: ButtonSize = ButtonSize.Compact,
    content: @Composable RowScope.() -> Unit,
) {
    ShogunButton(onClick, modifier, enabled, size, primaryButtonColors(), content)
}

@Composable
fun SecondaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: ButtonSize = ButtonSize.Compact,
    content: @Composable RowScope.() -> Unit,
) {
    ShogunButton(onClick, modifier, enabled, size, secondaryButtonColors(), content)
}

@Composable
fun GhostButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: ButtonSize = ButtonSize.Compact,
    content: @Composable RowScope.() -> Unit,
) {
    ShogunButton(onClick, modifier, enabled, size, ghostButtonColors(), content)
}

@Composable
fun DangerButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: ButtonSize = ButtonSize.Compact,
    content: @Composable RowScope.() -> Unit,
) {
    ShogunButton(onClick, modifier, enabled, size, dangerButtonColors(), content)
}

@Composable
fun DangerTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: ButtonSize = ButtonSize.Compact,
    content: @Composable RowScope.() -> Unit,
) {
    ShogunButton(onClick, modifier, enabled, size, dangerTextButtonColors(), content)
}

@Composable
fun NeutralTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: ButtonSize = ButtonSize.Compact,
    content: @Composable RowScope.() -> Unit,
) {
    ShogunButton(onClick, modifier, enabled, size, neutralTextButtonColors(), content)
}
