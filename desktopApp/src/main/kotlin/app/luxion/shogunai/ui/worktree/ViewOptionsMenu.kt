package app.luxion.shogunai.ui.worktree

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.luxion.shogunai.domain.model.Grouping
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.WorktreeSort
import app.luxion.shogunai.ui.actions.ActionShortcut
import app.luxion.shogunai.ui.actions.shortcutLabel
import app.luxion.shogunai.ui.components.GhostButton
import app.luxion.shogunai.ui.components.MenuEntry
import app.luxion.shogunai.ui.components.ShogunMenu
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.i18n.Strings
import app.luxion.shogunai.ui.isMacOs

data class ViewOptions(
    val projects: List<Project>?,
    val selectedProjectId: String?,
    val groupings: List<Grouping>,
    val grouping: Grouping,
    val sort: WorktreeSort,
)

class ViewOptionsHandlers(
    val onProject: (String?) -> Unit,
    val onGrouping: (Grouping) -> Unit,
    val onSort: (WorktreeSort) -> Unit,
    val onReload: () -> Unit,
)

fun viewOptionsMenu(options: ViewOptions, handlers: ViewOptionsHandlers, strings: Strings, mac: Boolean = isMacOs): List<MenuEntry> = buildList {
    options.projects?.let { projects ->
        add(MenuEntry.Section(strings.viewMenuProject))
        add(MenuEntry.Item(strings.allProjects, { handlers.onProject(null) }, checked = projects.none { it.id == options.selectedProjectId }))
        projects.forEach { project ->
            add(MenuEntry.Item(project.name, { handlers.onProject(project.id) }, checked = project.id == options.selectedProjectId))
        }
    }
    if (options.groupings.size > 1) {
        add(MenuEntry.Section(strings.viewMenuGrouping))
        options.groupings.forEach { grouping ->
            add(MenuEntry.Item(strings.groupingLabel(grouping), { handlers.onGrouping(grouping) }, checked = grouping == options.grouping))
        }
    }
    add(MenuEntry.Section(strings.sortLabel))
    WorktreeSort.entries.forEach { sort ->
        add(MenuEntry.Item(strings.sortLabel(sort), { handlers.onSort(sort) }, checked = sort == options.sort))
    }
    add(MenuEntry.Separator)
    add(MenuEntry.Item(strings.reload, handlers.onReload, shortcut = shortcutLabel(ActionShortcut("R"), mac)))
}

fun Strings.groupingLabel(grouping: Grouping): String = when (grouping) {
    Grouping.BY_STATE -> groupByState
    Grouping.BY_PROJECT -> groupByProject
    Grouping.BY_CHANGE -> groupByChange
}

fun Strings.sortLabel(sort: WorktreeSort): String = if (sort == WorktreeSort.NAME) sortByName else sortByLastActivity

@Composable
fun ViewMenuButton(entries: List<MenuEntry>, modifier: Modifier = Modifier) {
    val strings = LocalStrings.current
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        GhostButton(onClick = { expanded = true }) {
            Text(strings.viewMenu)
            Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.padding(start = 2.dp).size(18.dp))
        }
        ShogunMenu(expanded = expanded, onDismiss = { expanded = false }, entries = entries)
    }
}
