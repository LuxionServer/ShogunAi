package app.luxion.shogunai.ui.worktree

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.ui.actions.ActionId
import app.luxion.shogunai.ui.actions.ActionSpec
import app.luxion.shogunai.ui.actions.OpenWithShortcut
import app.luxion.shogunai.ui.actions.WorktreeCatalog
import app.luxion.shogunai.ui.actions.iconActions
import app.luxion.shogunai.ui.actions.openWithEntries
import app.luxion.shogunai.ui.actions.overflowEntries
import app.luxion.shogunai.ui.actions.shortcutLabel
import app.luxion.shogunai.ui.actions.toMenu
import app.luxion.shogunai.ui.actions.vector
import app.luxion.shogunai.ui.components.ActionBar
import app.luxion.shogunai.ui.components.BarIcon
import app.luxion.shogunai.ui.components.SplitButton
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.i18n.Strings

@Composable
fun WorktreeActionBar(
    holder: ProjectWorktrees,
    worktree: Worktree,
    modifier: Modifier = Modifier,
    openWithRequests: Int = 0,
) {
    val strings = LocalStrings.current
    val catalog = holder.actionCatalog(worktree, strings)
    val perform = rememberWorktreeActions(holder, worktree)
    val icons = catalog.iconActions
    ActionBar(
        modifier = modifier,
        primary = {
            catalog.primary?.let { spec ->
                SplitButton(
                    label = spec.label,
                    onClick = { perform(spec, null) },
                    menuLabel = "${strings.openWith} ${shortcutLabel(OpenWithShortcut)}",
                    menu = catalog.openWithEntries(strings).toMenu(strings, perform),
                    disabledReason = spec.disabledReason.takeIf { !catalog.isOpening(spec) },
                    enabled = !catalog.isOpening(spec),
                    tooltip = withShortcut(spec, strings, catalog.context.agent.takeIf { catalog.context.hasAgentChoice }),
                    busy = catalog.isOpening(spec),
                    openMenuRequests = openWithRequests,
                )
            }
        },
        icons = icons.map { spec ->
            BarIcon(
                icon = spec.icon.vector(),
                label = withShortcut(spec, strings, null),
                onClick = { perform(spec, null) },
                disabledReason = spec.disabledReason,
                confirmsInPlace = spec.id == ActionId.CopyLaunchCommand,
            )
        },
        moreLabel = strings.moreActions,
        more = catalog.overflowEntries().toMenu(strings, perform),
    )
}

private fun WorktreeCatalog.isOpening(spec: ActionSpec): Boolean =
    (spec.id as? ActionId.Open)?.target in context.opening

private fun withShortcut(spec: ActionSpec, strings: Strings, agent: String?): String {
    val label = agent?.let { strings.launchWithAgent(spec.label, it) } ?: spec.label
    return spec.shortcut?.let { "$label ${shortcutLabel(it)}" } ?: label
}
