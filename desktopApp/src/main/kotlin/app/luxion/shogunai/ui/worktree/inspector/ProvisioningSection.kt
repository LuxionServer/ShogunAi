package app.luxion.shogunai.ui.worktree.inspector

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.ui.components.InlineProgress
import app.luxion.shogunai.ui.components.InspectorSection
import app.luxion.shogunai.ui.components.ProgressSize
import app.luxion.shogunai.ui.components.Property
import app.luxion.shogunai.ui.components.PropertyList
import app.luxion.shogunai.ui.components.SecondaryButton
import app.luxion.shogunai.ui.components.Spacing
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.worktree.ProvisioningState

@Composable
fun ProvisioningSection(worktree: Worktree, state: ProvisioningState?, isProvisioning: Boolean, onProvision: () -> Unit) {
    val strings = LocalStrings.current
    InspectorSection(
        id = InspectorSectionId.PROVISIONING,
        title = strings.provisioningTitle,
        state = provisioningSectionState(worktree, state, strings),
    ) {
        val drift = (state as? ProvisioningState.Checked)?.drift.orEmpty()
        PropertyList(drift.map { Property(it.path, strings.provisionDriftState(it.state), isData = false) })
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            SecondaryButton(onClick = onProvision, enabled = !isProvisioning) { Text(strings.provisionAction) }
            if (isProvisioning) InlineProgress(size = ProgressSize.Small)
        }
    }
}
