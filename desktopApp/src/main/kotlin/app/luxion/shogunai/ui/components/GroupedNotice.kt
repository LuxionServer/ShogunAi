package app.luxion.shogunai.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.luxion.shogunai.ui.i18n.LocalStrings

@Composable
fun GroupedNotice(
    kind: NoticeKind,
    summary: String,
    details: List<String>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    var expanded by remember { mutableStateOf(false) }
    Notice(
        kind = kind,
        message = summary,
        onDismiss = onDismiss,
        modifier = modifier,
        details = if (details.size > 1) {
            {
                Column {
                    NeutralTextButton(onClick = { expanded = !expanded }) {
                        Icon(
                            imageVector = if (expanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                            contentDescription = null,
                        )
                        Text(strings.noticeDetails(details.size))
                    }
                    if (expanded) {
                        details.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        } else {
            null
        },
    )
}
