package app.luxion.shogunai.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class ProgressSize(val diameter: Dp, val strokeWidth: Dp) {
    Small(14.dp, 1.5.dp),
    Medium(20.dp, 2.dp),
}

@Composable
fun InlineProgress(modifier: Modifier = Modifier, size: ProgressSize = ProgressSize.Medium) {
    CircularProgressIndicator(modifier = modifier.size(size.diameter), strokeWidth = size.strokeWidth)
}
