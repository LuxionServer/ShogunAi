package app.luxion.shogunai.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.luxion.shogunai.ui.theme.ShogunTheme

@Composable
fun dangerButtonColors(): ButtonColors {
    val danger = ShogunTheme.status.danger
    return ButtonDefaults.buttonColors(containerColor = danger.color, contentColor = danger.onColor)
}

@Composable
fun dangerTextButtonColors(): ButtonColors =
    ButtonDefaults.textButtonColors(contentColor = ShogunTheme.status.danger.color)

@Composable
fun PrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier.focusRing(PillShape).handPointer(enabled),
        enabled = enabled,
        content = content,
    )
}

@Composable
fun SecondaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.focusRing(PillShape).handPointer(enabled),
        enabled = enabled,
        content = content,
    )
}

@Composable
fun DangerButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier.focusRing(PillShape).handPointer(enabled),
        enabled = enabled,
        colors = dangerButtonColors(),
        content = content,
    )
}

@Composable
fun DangerTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.focusRing(PillShape).handPointer(enabled),
        enabled = enabled,
        colors = dangerTextButtonColors(),
        content = content,
    )
}

@Composable
fun NeutralTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.focusRing(PillShape).handPointer(enabled),
        enabled = enabled,
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        content = content,
    )
}
