package app.luxion.shogunai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import app.luxion.shogunai.ui.theme.ControlShape
import app.luxion.shogunai.ui.theme.ShogunTheme
import app.luxion.shogunai.ui.theme.elevation

sealed interface MenuEntry {
    data class Item(
        val label: String,
        val onClick: () -> Unit,
        val shortcut: String? = null,
        val mark: String? = null,
        val disabledReason: String? = null,
        val danger: Boolean = false,
        val checked: Boolean = false,
    ) : MenuEntry

    data class Submenu(
        val label: String,
        val entries: List<MenuEntry>,
        val onClick: (() -> Unit)? = null,
        val shortcut: String? = null,
        val mark: String? = null,
    ) : MenuEntry

    data class Section(val title: String) : MenuEntry

    data object Separator : MenuEntry
}

object OpenMenus {
    private val dismissals = mutableListOf<() -> Unit>()
    private var escapeClosedAt = 0L

    val isAnyOpen: Boolean get() = dismissals.isNotEmpty()

    internal fun opened(dismiss: () -> Unit) {
        dismissals += dismiss
    }

    internal fun closed(dismiss: () -> Unit) {
        dismissals -= dismiss
    }

    internal fun closedByEscape(now: Long = System.currentTimeMillis()) {
        escapeClosedAt = now
    }

    fun handleEscape(now: Long = System.currentTimeMillis()): Boolean {
        if (now - escapeClosedAt < EscapeEchoMillis) return true
        val top = dismissals.lastOrNull() ?: return false
        top()
        return true
    }
}

private const val EscapeEchoMillis = 500L

private val MenuMinWidth = 200.dp
private val MenuMaxWidth = 340.dp
private val MenuItemHeight = 28.dp
private val MenuIconSize = 16.dp

private fun MenuEntry.isActionable(): Boolean = when (this) {
    is MenuEntry.Item -> disabledReason == null
    is MenuEntry.Submenu -> true
    else -> false
}

@Composable
fun ShogunMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    entries: List<MenuEntry>,
    offset: IntOffset = IntOffset.Zero,
    focusFirst: Boolean = false,
) {
    if (!expanded) return
    Popup(
        popupPositionProvider = remember(offset) { BelowAnchor(offset) },
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        MenuPanel(entries = entries, onClose = onDismiss, onCloseAll = onDismiss, focusFirst = focusFirst)
    }
}

@Composable
private fun MenuPanel(
    entries: List<MenuEntry>,
    onClose: () -> Unit,
    onCloseAll: () -> Unit,
    focusFirst: Boolean,
) {
    val actionable = entries.indices.filter { entries[it].isActionable() }
    var focused by remember(entries) { mutableStateOf(if (focusFirst) actionable.firstOrNull() else null) }
    var openSubmenu by remember(entries) { mutableStateOf<Int?>(null) }
    var submenuFocusFirst by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val currentOnClose by rememberUpdatedState(onClose)
    DisposableEffect(Unit) {
        val dismiss = { currentOnClose() }
        OpenMenus.opened(dismiss)
        onDispose { OpenMenus.closed(dismiss) }
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    LaunchedEffect(openSubmenu) { if (openSubmenu == null) runCatching { focusRequester.requestFocus() } }

    fun move(step: Int) {
        if (actionable.isEmpty()) return
        val position = actionable.indexOf(focused)
        focused = when {
            position < 0 -> if (step > 0) actionable.first() else actionable.last()
            else -> actionable[(position + step).mod(actionable.size)]
        }
    }

    fun activate(index: Int, fromKeyboard: Boolean) {
        when (val entry = entries[index]) {
            is MenuEntry.Item -> if (entry.disabledReason == null) {
                onCloseAll()
                entry.onClick()
            }
            is MenuEntry.Submenu -> {
                val click = entry.onClick
                if (click != null) {
                    onCloseAll()
                    click()
                } else {
                    submenuFocusFirst = fromKeyboard
                    openSubmenu = index
                }
            }
            else -> Unit
        }
    }

    Column(
        modifier = Modifier
            .width(IntrinsicSize.Max)
            .widthIn(min = MenuMinWidth, max = MenuMaxWidth)
            .elevation(ShogunTheme.elevation.level2, ControlShape)
            .padding(vertical = Spacing.xs)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (openSubmenu != null) {
                    val closesSubmenu = event.key == Key.Escape || event.key == Key.DirectionLeft
                    if (closesSubmenu && event.type == KeyEventType.KeyDown) {
                        if (event.key == Key.Escape) OpenMenus.closedByEscape()
                        openSubmenu = null
                    }
                    return@onPreviewKeyEvent closesSubmenu
                }
                if (event.key == Key.Escape) {
                    if (event.type == KeyEventType.KeyDown) {
                        OpenMenus.closedByEscape()
                        onClose()
                    }
                    return@onPreviewKeyEvent true
                }
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionDown -> move(1)
                    Key.DirectionUp -> move(-1)
                    Key.DirectionRight -> focused?.takeIf { entries[it] is MenuEntry.Submenu }?.let {
                        submenuFocusFirst = true
                        openSubmenu = it
                    }
                    Key.Enter, Key.NumPadEnter, Key.Spacebar -> focused?.let { activate(it, fromKeyboard = true) }
                    Key.DirectionLeft -> onClose()
                    else -> return@onPreviewKeyEvent false
                }
                true
            },
    ) {
        entries.forEachIndexed { index, entry ->
            when (entry) {
                MenuEntry.Separator -> HorizontalDivider(
                    modifier = Modifier.padding(vertical = Spacing.xs),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                is MenuEntry.Section -> Text(
                    entry.title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                        .semantics { heading() },
                )
                is MenuEntry.Item -> DisabledReason(entry.disabledReason) {
                    MenuRow(
                        label = entry.label,
                        mark = entry.mark,
                        shortcut = entry.shortcut,
                        checked = entry.checked,
                        danger = entry.danger,
                        disabledReason = entry.disabledReason,
                        focused = focused == index,
                        hasSubmenu = false,
                        onHover = {
                            if (entry.disabledReason == null) focused = index
                            openSubmenu = null
                        },
                        onClick = { activate(index, fromKeyboard = false) },
                    )
                }
                is MenuEntry.Submenu -> Box {
                    MenuRow(
                        label = entry.label,
                        mark = entry.mark,
                        shortcut = entry.shortcut,
                        checked = false,
                        danger = false,
                        disabledReason = null,
                        focused = focused == index,
                        hasSubmenu = true,
                        onHover = {
                            focused = index
                            submenuFocusFirst = false
                            openSubmenu = index
                        },
                        onClick = { activate(index, fromKeyboard = false) },
                    )
                    if (openSubmenu == index) {
                        Popup(
                            popupPositionProvider = BesideAnchor,
                            onDismissRequest = { openSubmenu = null },
                            properties = PopupProperties(focusable = submenuFocusFirst),
                        ) {
                            MenuPanel(
                                entries = entry.entries,
                                onClose = { openSubmenu = null },
                                onCloseAll = onCloseAll,
                                focusFirst = submenuFocusFirst,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuRow(
    label: String,
    mark: String?,
    shortcut: String?,
    checked: Boolean,
    danger: Boolean,
    disabledReason: String?,
    focused: Boolean,
    hasSubmenu: Boolean,
    onHover: () -> Unit,
    onClick: () -> Unit,
) {
    val enabled = disabledReason == null
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    LaunchedEffect(hovered) { if (hovered) onHover() }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val content = when {
        !enabled -> ShogunTheme.colors.disabledContent
        danger -> ShogunTheme.status.danger.color
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(MenuItemHeight)
            .padding(horizontal = Spacing.xs)
            .background(if (focused) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent, ControlShape)
            .hoverable(interactionSource)
            .handPointer(enabled)
            .clickable(interactionSource = interactionSource, indication = null, enabled = enabled, onClick = onClick)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                if (checked) selected = true
                if (disabledReason != null) {
                    disabled()
                    stateDescription = disabledReason
                }
            }
            .padding(horizontal = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(MenuIconSize), contentAlignment = Alignment.Center) {
            if (checked) Icon(Icons.Default.Check, contentDescription = null, tint = content, modifier = Modifier.size(MenuIconSize))
        }
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        mark?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = muted, maxLines = 1) }
        shortcut?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = muted, maxLines = 1) }
        if (hasSubmenu) {
            Icon(Icons.AutoMirrored.Default.KeyboardArrowRight, contentDescription = null, tint = muted, modifier = Modifier.size(MenuIconSize))
        }
    }
}

private class BelowAnchor(private val offset: IntOffset) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val wanted = if (offset == IntOffset.Zero) {
            IntOffset(anchorBounds.left, anchorBounds.bottom)
        } else {
            IntOffset(anchorBounds.left + offset.x, anchorBounds.top + offset.y)
        }
        return fitInWindow(wanted, windowSize, popupContentSize, flipY = anchorBounds.top - popupContentSize.height)
    }
}

private object BesideAnchor : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val right = anchorBounds.right
        val x = if (right + popupContentSize.width <= windowSize.width) right else anchorBounds.left - popupContentSize.width
        return fitInWindow(IntOffset(x, anchorBounds.top), windowSize, popupContentSize, flipY = anchorBounds.bottom - popupContentSize.height)
    }
}

private fun fitInWindow(wanted: IntOffset, window: IntSize, content: IntSize, flipY: Int): IntOffset {
    val x = wanted.x.coerceIn(0, (window.width - content.width).coerceAtLeast(0))
    val y = if (wanted.y + content.height <= window.height) wanted.y else flipY.coerceAtLeast(0)
    return IntOffset(x, y)
}
