package app.luxion.shogunai.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription

@Composable
fun DisabledReason(reason: String?, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    if (reason == null) {
        content()
        return
    }
    WithTooltip(label = reason, modifier = modifier.semantics { stateDescription = reason }, content = content)
}
