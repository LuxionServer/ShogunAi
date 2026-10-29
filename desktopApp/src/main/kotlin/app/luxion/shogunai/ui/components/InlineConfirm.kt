package app.luxion.shogunai.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.luxion.shogunai.ui.i18n.LocalStrings
import kotlinx.coroutines.delay

const val InlineConfirmMillis = 1500L
private val InlineConfirmIconSize = 18.dp

@Stable
class InlineConfirmState {
    var confirmed by mutableStateOf(false)
        internal set
    internal var requests by mutableIntStateOf(0)
        private set

    fun confirm() {
        requests++
    }
}

@Composable
fun rememberInlineConfirm(durationMillis: Long = InlineConfirmMillis): InlineConfirmState {
    val state = remember { InlineConfirmState() }
    LaunchedEffect(state.requests) {
        if (state.requests == 0) return@LaunchedEffect
        state.confirmed = true
        delay(durationMillis)
        state.confirmed = false
    }
    return state
}

@Composable
fun InlineConfirmButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector = Icons.Default.ContentCopy,
    state: InlineConfirmState = rememberInlineConfirm(),
) {
    val strings = LocalStrings.current
    SecondaryButton(
        onClick = {
            onClick()
            state.confirm()
        },
        modifier = modifier,
        enabled = enabled,
    ) {
        Icon(
            if (state.confirmed) Icons.Default.Check else icon,
            contentDescription = null,
            modifier = Modifier.size(InlineConfirmIconSize),
        )
        Text(if (state.confirmed) strings.copied else label, modifier = Modifier.padding(start = Spacing.sm))
    }
}

@Composable
fun InlineConfirmIconAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Default.ContentCopy,
    state: InlineConfirmState = rememberInlineConfirm(),
) {
    val strings = LocalStrings.current
    IconAction(
        icon = if (state.confirmed) Icons.Default.Check else icon,
        label = if (state.confirmed) strings.copied else label,
        onClick = {
            onClick()
            state.confirm()
        },
        modifier = modifier,
    )
}
