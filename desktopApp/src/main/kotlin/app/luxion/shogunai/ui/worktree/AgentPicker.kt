package app.luxion.shogunai.ui.worktree

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.luxion.shogunai.domain.model.AgentLaunchConfig
import app.luxion.shogunai.ui.components.IconAction
import app.luxion.shogunai.ui.components.handPointer
import app.luxion.shogunai.ui.i18n.LocalStrings

enum class LaunchKind { COPY, TERMINAL, HERDR, ORCA }

data class LaunchChoice(val kind: LaunchKind, val agent: AgentLaunchConfig, val label: String)

@Composable
fun AgentMenuButton(agents: List<AgentLaunchConfig>, onPick: (AgentLaunchConfig) -> Unit) {
    val strings = LocalStrings.current
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconAction(icon = Icons.Default.ArrowDropDown, label = strings.chooseAgent, onClick = { expanded = true })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            agents.forEach { agent ->
                DropdownMenuItem(
                    text = { Text(agent.displayName) },
                    onClick = {
                        expanded = false
                        onPick(agent)
                    },
                    modifier = Modifier.handPointer(),
                )
            }
        }
    }
}
