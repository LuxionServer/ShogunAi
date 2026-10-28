package app.luxion.shogunai.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.theme.ShogunTheme
import app.luxion.shogunai.ui.theme.Status

enum class NoticeKind(val status: Status, val icon: ImageVector) {
    ERROR(Status.DANGER, Icons.Default.Error),
    WARNING(Status.WARNING, Icons.Default.Warning),
    SUCCESS(Status.SUCCESS, Icons.Default.CheckCircle),
    INFO(Status.INFO, Icons.Default.Info),
}

private val NoticeIconSize = 18.dp
private val NoticeIconPadding = 2.dp

@Composable
fun Notice(
    kind: NoticeKind,
    message: String,
    onDismiss: (() -> Unit)?,
    modifier: Modifier = Modifier,
    details: (@Composable () -> Unit)? = null,
    onIconClick: (() -> Unit)? = null,
    iconDescription: String? = null,
) {
    val strings = LocalStrings.current
    val colors = ShogunTheme.status.of(kind.status)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = colors.container,
        contentColor = colors.onContainer,
    ) {
        Row(
            modifier = Modifier.padding(start = Spacing.md, end = Spacing.xs, top = Spacing.xs, bottom = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NoticeIcon(kind, onIconClick, iconDescription)
            Column(modifier = Modifier.weight(1f).padding(vertical = Spacing.xs)) {
                Text(message, style = MaterialTheme.typography.bodyMedium)
                details?.invoke()
            }
            onDismiss?.let {
                IconAction(
                    icon = Icons.Default.Close,
                    label = if (kind == NoticeKind.ERROR) strings.closeError else strings.closeMessage,
                    onClick = it,
                )
            }
        }
    }
}

@Composable
private fun NoticeIcon(kind: NoticeKind, onClick: (() -> Unit)?, description: String?) {
    if (onClick == null) {
        Icon(kind.icon, contentDescription = null, modifier = Modifier.size(NoticeIconSize))
        return
    }
    WithTooltip(label = description.orEmpty()) {
        Icon(
            kind.icon,
            contentDescription = description,
            modifier = Modifier
                .interactive(shape = CircleShape, onClick = onClick)
                .padding(NoticeIconPadding)
                .size(NoticeIconSize),
        )
    }
}
