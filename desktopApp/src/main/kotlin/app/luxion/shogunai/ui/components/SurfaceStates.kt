package app.luxion.shogunai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.theme.ShogunTheme

class StateAction(val label: String, val onClick: () -> Unit)

private val StateIconSize = 32.dp
private val StateMaxWidth = 480.dp
private val SkeletonRowHeight = 48.dp
private val SkeletonPrimaryHeight = 12.dp
private val SkeletonSecondaryHeight = 10.dp
private const val SkeletonPrimaryFraction = 0.55f
private const val SkeletonSecondaryFraction = 0.35f

@Composable
fun EmptyState(
    icon: ImageVector,
    message: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    action: StateAction? = null,
    link: StateAction? = null,
) {
    StateFrame(modifier) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(StateIconSize))
        title?.let { ViewTitle(it) }
        StateMessage(message)
        if (action != null || link != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                action?.let { PrimaryButton(onClick = it.onClick) { Text(it.label) } }
                link?.let { NeutralTextButton(onClick = it.onClick) { Text(it.label) } }
            }
        }
    }
}

@Composable
fun ErrorState(
    message: String,
    action: StateAction,
    modifier: Modifier = Modifier,
    technicalDetails: String? = null,
) {
    StateFrame(modifier) {
        Icon(
            Icons.Default.ErrorOutline,
            contentDescription = null,
            tint = ShogunTheme.status.danger.color,
            modifier = Modifier.size(StateIconSize),
        )
        StateMessage(message)
        SecondaryButton(onClick = action.onClick) { Text(action.label) }
        technicalDetails?.takeIf { it.isNotBlank() }?.let { TechnicalDetailsDisclosure(it) }
    }
}

@Composable
fun SkeletonRows(modifier: Modifier = Modifier, count: Int = 3, description: String = LocalStrings.current.loading) {
    val bar = MaterialTheme.colorScheme.surfaceContainerHigh
    Column(modifier = modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description }) {
        repeat(count) {
            Column(
                modifier = Modifier.fillMaxWidth().height(SkeletonRowHeight).padding(horizontal = Spacing.smd),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs, Alignment.CenterVertically),
            ) {
                Box(Modifier.fillMaxWidth(SkeletonPrimaryFraction).height(SkeletonPrimaryHeight).background(bar, MaterialTheme.shapes.extraSmall))
                Box(Modifier.fillMaxWidth(SkeletonSecondaryFraction).height(SkeletonSecondaryHeight).background(bar, MaterialTheme.shapes.extraSmall))
            }
        }
    }
}

@Composable
private fun StateFrame(modifier: Modifier, content: @Composable () -> Unit) {
    Box(modifier = modifier.fillMaxSize().padding(Spacing.xl), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.widthIn(max = StateMaxWidth),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            content()
        }
    }
}

@Composable
private fun StateMessage(message: String) {
    Text(
        message,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}
