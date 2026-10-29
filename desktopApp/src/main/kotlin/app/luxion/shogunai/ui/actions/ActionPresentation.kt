package app.luxion.shogunai.ui.actions

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DesktopMac
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.ui.graphics.vector.ImageVector
import app.luxion.shogunai.ui.components.MenuEntry
import app.luxion.shogunai.ui.i18n.Strings
import app.luxion.shogunai.ui.isMacOs

typealias PerformAction = (spec: ActionSpec, agent: String?) -> Unit

fun ActionIcon.vector(): ImageVector = when (this) {
    ActionIcon.RUNNER -> Icons.AutoMirrored.Default.OpenInNew
    ActionIcon.TERMINAL -> Icons.Default.Terminal
    ActionIcon.EDITOR -> Icons.Default.Code
    ActionIcon.DESKTOP -> Icons.Default.DesktopMac
    ActionIcon.FOLDER -> Icons.Default.FolderOpen
    ActionIcon.COPY -> Icons.Default.ContentCopy
    ActionIcon.PATH -> Icons.Default.Link
    ActionIcon.DELETE -> Icons.Default.Delete
    ActionIcon.ADD -> Icons.Default.Add
    ActionIcon.SETTINGS -> Icons.Default.Settings
    ActionIcon.RELOAD -> Icons.Default.Refresh
    ActionIcon.CLEAN -> Icons.Default.CleaningServices
    ActionIcon.AGENT -> Icons.Default.SmartToy
    ActionIcon.MORE -> Icons.Default.MoreHoriz
}

fun shortcutLabel(shortcut: ActionShortcut, mac: Boolean = isMacOs): String {
    val key = if (!mac && shortcut.key == "⌫") "Backspace" else shortcut.key
    return if (mac) {
        (if (shortcut.shift) "⇧" else "") + "⌘" + key
    } else {
        "Ctrl+" + (if (shortcut.shift) "Shift+" else "") + key
    }
}

fun List<ActionEntry>.toMenu(strings: Strings, perform: PerformAction): List<MenuEntry> = map { it.toMenu(strings, perform) }

fun ActionEntry.toMenu(strings: Strings, perform: PerformAction): MenuEntry = when (this) {
    is ActionEntry.Run -> MenuEntry.Item(
        label = label,
        onClick = { perform(spec, agent) },
        shortcut = spec.shortcut?.takeIf { agent == null }?.let { shortcutLabel(it) },
        mark = strings.defaultRunnerMark.takeIf { marked },
        disabledReason = spec.disabledReason,
        danger = spec.isDestructive,
        checked = checked,
    )
    is ActionEntry.Nested -> MenuEntry.Submenu(
        label = label,
        entries = entries.toMenu(strings, perform),
        onClick = run?.let { run -> { perform(run.spec, run.agent) } },
        shortcut = run?.spec?.shortcut?.let { shortcutLabel(it) },
        mark = strings.defaultRunnerMark.takeIf { run?.marked == true },
    )
    is ActionEntry.Heading -> MenuEntry.Section(title)
    ActionEntry.Divider -> MenuEntry.Separator
}

fun ActionSpec.toMenuItem(perform: PerformAction): MenuEntry.Item = MenuEntry.Item(
    label = label,
    onClick = { perform(this, null) },
    shortcut = shortcut?.let { shortcutLabel(it) },
    disabledReason = disabledReason,
    danger = isDestructive,
)
