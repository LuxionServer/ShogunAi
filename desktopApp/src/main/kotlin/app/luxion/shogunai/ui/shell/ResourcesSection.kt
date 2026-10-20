package app.luxion.shogunai.ui.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.luxion.shogunai.domain.model.CapacityProfile
import app.luxion.shogunai.domain.model.ResourceKind
import app.luxion.shogunai.ui.components.Spacing
import app.luxion.shogunai.ui.components.trackTextInputFocus
import app.luxion.shogunai.ui.i18n.LocalStrings

@Composable
fun ResourcesSection(resources: ResourcePreferences) {
    val strings = LocalStrings.current
    Text(
        strings.resourcesLabel,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(top = Spacing.md),
    )
    CapacityProfile.entries.forEach { profile ->
        val selected = resources.settings.profile == profile
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxWidth().selectable(selected = selected, onClick = { resources.choose(profile) }, role = Role.RadioButton),
        ) {
            RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(Spacing.sm))
            Column(modifier = Modifier.padding(top = Spacing.sm)) {
                Text(strings.profileName(profile), style = MaterialTheme.typography.bodyMedium)
                if (profile != CapacityProfile.CUSTOM) {
                    Text(
                        strings.resourceLimitsSummary(resources.limitsOf(profile), resources.showsSimulators),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    if (resources.settings.profile == CapacityProfile.CUSTOM) {
        resources.kinds.forEach { kind -> CustomLimitRow(resources, kind) }
    }
}

@Composable
private fun CustomLimitRow(resources: ResourcePreferences, kind: ResourceKind) {
    val strings = LocalStrings.current
    val draft = resources.draft(kind)
    Column(modifier = Modifier.padding(start = Spacing.lg)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(strings.resourceKindLabel(kind), style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(140.dp))
            OutlinedTextField(
                value = draft.text,
                onValueChange = { resources.edit(kind, it) },
                enabled = !draft.unlimited,
                singleLine = true,
                isError = draft.invalid,
                modifier = Modifier.width(88.dp).trackTextInputFocus(),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.toggleable(
                    value = draft.unlimited,
                    onValueChange = { resources.setUnlimited(kind, it) },
                    role = Role.Checkbox,
                ),
            ) {
                Checkbox(checked = draft.unlimited, onCheckedChange = null)
                Text(strings.noLimit, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = Spacing.xs))
            }
        }
        if (draft.invalid) {
            Text(strings.invalidLimit, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}
