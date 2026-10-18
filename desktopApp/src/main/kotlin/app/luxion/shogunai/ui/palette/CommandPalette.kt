package app.luxion.shogunai.ui.palette

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import app.luxion.shogunai.ui.components.Spacing
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.i18n.Strings
import app.luxion.shogunai.ui.isMacOs
import app.luxion.shogunai.ui.machine.icon
import app.luxion.shogunai.ui.theme.ShogunTheme

private val PaletteWidth = 560.dp
private val ResultRowHeight = 40.dp
private const val VisibleResults = 8
private val KindIconSize = 16.dp
private val IndicatorDotSize = 8.dp
private val ContextMaxWidth = 200.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommandPalette(
    items: List<PaletteItem>,
    recentIds: List<String>,
    onRun: (PaletteItem, PaletteAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalStrings.current
    var query by remember { mutableStateOf("") }
    var highlighted by remember { mutableIntStateOf(0) }
    val results = remember(items, recentIds, query) { rankPaletteItems(items, query, recentIds) }
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    var focusRequested by remember { mutableStateOf(false) }
    LaunchedEffect(query) { listState.scrollToItem(0) }
    LaunchedEffect(highlighted) { if (results.isNotEmpty()) listState.scrollToItem(highlighted.coerceIn(0, results.lastIndex)) }

    fun run(secondary: Boolean) {
        val item = results.getOrNull(highlighted) ?: return
        val action = if (secondary) item.secondary else item.primary
        action?.let { onRun(item, it) }
    }

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.width(PaletteWidth),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(KindIconSize))
                    SearchField(
                        query = query,
                        onQueryChange = {
                            query = it
                            highlighted = 0
                        },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester)
                            .onGloballyPositioned { if (!focusRequested) focusRequested = runCatching { focusRequester.requestFocus() }.isSuccess }
                            .onPreviewKeyEvent { event ->
                                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                when (event.key) {
                                    Key.DirectionDown -> {
                                        highlighted = (highlighted + 1).coerceAtMost((results.size - 1).coerceAtLeast(0))
                                        true
                                    }
                                    Key.DirectionUp -> {
                                        highlighted = (highlighted - 1).coerceAtLeast(0)
                                        true
                                    }
                                    Key.Enter, Key.NumPadEnter -> {
                                        run(secondary = if (isMacOs) event.isMetaPressed else event.isCtrlPressed)
                                        true
                                    }
                                    else -> false
                                }
                            },
                    )
                }
                HorizontalDivider()
                if (results.isEmpty()) {
                    Text(
                        strings.paletteNoResults,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(Spacing.md),
                    )
                } else {
                    LazyColumn(state = listState, modifier = Modifier.heightIn(max = ResultRowHeight * VisibleResults)) {
                        itemsIndexed(results, key = { _, item -> item.id }) { index, item ->
                            ResultRow(
                                item = item,
                                query = query,
                                highlighted = index == highlighted,
                                onClick = { onRun(item, item.primary) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier) {
    val strings = LocalStrings.current
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = modifier,
        decorationBox = { inner ->
            Box(modifier = Modifier.heightIn(min = ResultRowHeight), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) {
                    Text(strings.paletteHint, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                inner()
            }
        },
    )
}

@Composable
private fun ResultRow(item: PaletteItem, query: String, highlighted: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val strings = LocalStrings.current
    val currentOnClick by rememberUpdatedState(onClick)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ResultRowHeight)
            .background(if (highlighted) colors.secondaryContainer else Color.Transparent)
            .pointerInput(Unit) { detectTapGestures { currentOnClick() } }
            .padding(horizontal = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Icon(item.icon, contentDescription = item.kindLabel(strings), tint = colors.onSurfaceVariant, modifier = Modifier.size(KindIconSize))
            Text(
                highlight(item.title, query),
                style = MaterialTheme.typography.bodyMedium,
                color = if (highlighted) colors.onSecondaryContainer else colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            item.indicator?.let {
                Box(modifier = Modifier.size(IndicatorDotSize).background(ShogunTheme.status.of(it.status).color, CircleShape))
            }
            item.context?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = ContextMaxWidth),
                )
            }
        }
        item.shortcut?.let {
            Text(it, style = ShogunTheme.typography.dataSmall, color = colors.onSurfaceVariant)
        }
    }
}

private fun highlight(text: String, query: String): AnnotatedString {
    val indices = matchedIndices(query, text)
    return buildAnnotatedString {
        text.forEachIndexed { index, char ->
            if (index in indices) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(char) } else append(char)
        }
    }
}

private val PaletteItem.icon: ImageVector
    get() = when (kind) {
        PaletteKind.VIEW -> machineResource?.icon ?: Icons.Default.ViewList
        PaletteKind.PROJECT -> Icons.Default.Folder
        PaletteKind.WORKTREE -> Icons.Default.AccountTree
        PaletteKind.ACTION -> Icons.Default.Bolt
    }

private fun PaletteItem.kindLabel(strings: Strings): String = when (kind) {
    PaletteKind.VIEW -> strings.paletteKindView
    PaletteKind.PROJECT -> strings.paletteKindProject
    PaletteKind.WORKTREE -> strings.paletteKindWorktree
    PaletteKind.ACTION -> strings.paletteKindAction
}
