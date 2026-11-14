package app.luxion.shogunai.ui.notifications

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import app.luxion.shogunai.ui.components.Spacing
import app.luxion.shogunai.ui.i18n.LocalStrings

internal const val NotifyBlockedTag = "notify-blocked"
internal const val NotifyDoneTag = "notify-done"

@Composable
fun NotificationsSection(notifications: AgentNotificationWatch) {
    val strings = LocalStrings.current
    val preferences = notifications.preferences
    Text(strings.notificationsLabel, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = Spacing.md))
    NotificationSwitch(strings.notifyWhenBlocked, preferences.blocked, NotifyBlockedTag) { notifications.update(notifications.preferences.copy(blocked = it)) }
    NotificationSwitch(strings.notifyWhenDone, preferences.done, NotifyDoneTag) { notifications.update(notifications.preferences.copy(done = it)) }
}

@Composable
private fun NotificationSwitch(label: String, checked: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange).testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}
