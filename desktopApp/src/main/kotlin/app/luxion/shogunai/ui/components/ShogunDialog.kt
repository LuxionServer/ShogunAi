package app.luxion.shogunai.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import app.luxion.shogunai.ui.theme.DialogShape
import app.luxion.shogunai.ui.theme.ElevationBorderWidth
import app.luxion.shogunai.ui.theme.ShogunTheme

enum class DialogSize(val width: Dp) {
    Small(400.dp),
    Medium(520.dp),
    Large(680.dp),
}

private val DialogPadding = Spacing.lg

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShogunDialog(
    title: String,
    onDismissRequest: () -> Unit,
    confirm: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismiss: (@Composable () -> Unit)? = null,
    size: DialogSize = DialogSize.Small,
    scrollable: Boolean = true,
    dismissible: Boolean = true,
    notices: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    BasicAlertDialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = dismissible,
            dismissOnClickOutside = dismissible,
            usePlatformDefaultWidth = false,
        ),
        modifier = modifier.width(size.width),
    ) {
        val level = ShogunTheme.elevation.level2
        Surface(
            shape = DialogShape,
            color = level.container,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = level.border?.let { BorderStroke(ElevationBorderWidth, it) },
            shadowElevation = level.shadow,
        ) {
            Column(
                modifier = Modifier.padding(DialogPadding),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                SectionTitle(title)
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth()
                        .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    content = content,
                )
                notices?.let {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm), content = it)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    dismiss?.invoke()
                    confirm()
                }
            }
        }
    }
}
