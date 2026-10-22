package app.luxion.shogunai.ui.worktree

import app.luxion.shogunai.ui.components.StatusChip
import app.luxion.shogunai.ui.components.WithTooltip
import app.luxion.shogunai.ui.machine.MachineResource
import app.luxion.shogunai.ui.theme.Status

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.luxion.shogunai.domain.usecase.GroupKept
import app.luxion.shogunai.domain.usecase.GroupKeptReason
import app.luxion.shogunai.ui.components.NeutralTextButton
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalClipboard
import app.luxion.shogunai.ui.components.copyText
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.luxion.shogunai.domain.model.DEFAULT_INACTIVE_AFTER_DAYS
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.usecase.classifyOrigin
import app.luxion.shogunai.ui.components.DangerTextButton
import app.luxion.shogunai.ui.components.IconAction
import app.luxion.shogunai.ui.components.InlineProgress
import app.luxion.shogunai.ui.components.Spacing
import app.luxion.shogunai.ui.components.interactive
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.i18n.Strings
import app.luxion.shogunai.ui.components.ErrorNotice
import app.luxion.shogunai.ui.theme.ShogunTheme
import java.time.Instant

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WorktreeListPane(
    board: WorktreeBoard,
    onOpenProjectSettings: (Project) -> Unit,
    onOpenMachine: (MachineResource) -> Unit,
    modifier: Modifier = Modifier,
    onOpenGroup: (GroupTag) -> Unit = {},
) {
    val listState = rememberLazyListState()
    val items = listItems(board)
    LaunchedEffect(board.selected) {
        val selected = board.selected ?: return@LaunchedEffect
        val index = items.indexOfFirst {
            (it is ListItem.WorktreeItem && it.ref == selected) || (it is ListItem.StateRow && it.ref == selected) ||
                (it is ListItem.ChangeRow && it.ref == selected)
        }
        if (index >= 0) listState.bringIntoViewBelowHeader(index)
    }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionDown -> {
                            board.selectNext()
                            true
                        }
                        Key.DirectionUp -> {
                            board.selectPrevious()
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            },
    ) {
        val compact = maxWidth < CompactListWidth
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = Spacing.xl),
        ) {
            items.forEach { item ->
                when (item) {
                    is ListItem.Header -> stickyHeader(key = item.key) {
                        ProjectGroupHeader(
                            item.group,
                            board,
                            onOpenProjectSettings = { onOpenProjectSettings(item.group.project) },
                            onOpenGradle = { onOpenMachine(MachineResource.GRADLE_DAEMONS) },
                        )
                    }
                    is ListItem.StateHeader -> stickyHeader(key = item.key) {
                        StateGroupHeader(item.group.kind, item.group.items.size, board.canCleanUp(), { board.openCleanup() }, board.inactivity.days)
                    }
                    is ListItem.StateRow -> item(key = item.key) {
                        val entry = item.entry
                        WorktreeRowFor(
                            board, entry.project, entry.holder, entry.worktree,
                            projectName = entry.project.name.takeIf { board.shownProjects.size > 1 && !board.isMultirepoView },
                            compact = compact,
                            alias = board.aliasOf(entry.project.id),
                            onOpenGroup = onOpenGroup,
                        )
                        HorizontalDivider()
                    }
                    is ListItem.ChangeHeader -> stickyHeader(key = item.key) {
                        ChangeGroupHeader(item.group, board)
                    }
                    is ListItem.UngroupedHeader -> stickyHeader(key = item.key) {
                        UngroupedHeader(item.count)
                    }
                    is ListItem.ChangeRow -> item(key = item.key) {
                        val entry = item.aliased.entry
                        WorktreeRowFor(board, entry.project, entry.holder, entry.worktree, projectName = null, compact = compact, alias = item.aliased.alias)
                        HorizontalDivider()
                    }
                    is ListItem.GroupState -> item(key = item.key) {
                        GroupState(item.group.holder, item.group.project.id, projectName = item.group.project.name.takeIf { item.showsProjectName })
                    }
                    is ListItem.WorktreeItem -> item(key = item.key) {
                        WorktreeRowFor(board, item.group.project, item.group.holder, item.worktree, projectName = null, compact = compact, onOpenGroup = onOpenGroup)
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

private val CompactListWidth = 760.dp

private suspend fun LazyListState.bringIntoViewBelowHeader(index: Int) {
    repeat(2) {
        val info = layoutInfo
        val headerHeight = info.visibleItemsInfo.firstOrNull { it.key.isHeaderKey() }?.size ?: 0
        val bottom = info.viewportEndOffset - info.afterContentPadding
        val item = info.visibleItemsInfo.firstOrNull { it.index == index }
        if (item == null) {
            scrollToItem(index, -headerHeight)
        } else {
            val delta = scrollDeltaToReveal(item.offset, item.size, headerHeight, bottom)
            if (delta == 0) return
            scrollBy(delta.toFloat())
        }
    }
}

internal fun scrollDeltaToReveal(itemOffset: Int, itemSize: Int, headerHeight: Int, viewportBottom: Int): Int = when {
    itemOffset < headerHeight -> itemOffset - headerHeight
    itemOffset + itemSize > viewportBottom -> itemOffset + itemSize - viewportBottom
    else -> 0
}

private fun Any.isHeaderKey(): Boolean =
    this is String && (startsWith("header:") || startsWith("state-header:") || startsWith("change-header:") || this == UNGROUPED_HEADER_KEY)

private const val UNGROUPED_HEADER_KEY = "ungrouped-header"

internal sealed interface ListItem {
    val key: String

    data class Header(val group: WorktreeGroup) : ListItem {
        override val key = "header:${group.project.id}"
    }

    data class StateHeader(val group: StateGroup<WorktreeEntry>) : ListItem {
        override val key = "state-header:${group.kind}"
    }

    data class StateRow(val group: StateGroup<WorktreeEntry>, val entry: WorktreeEntry) : ListItem {
        val ref = entry.ref
        override val key = "state-row:${entry.project.id}:${entry.worktree.path}"
    }

    data class GroupState(val group: WorktreeGroup, val showsProjectName: Boolean = false) : ListItem {
        override val key = "state:${group.project.id}"
    }

    data class WorktreeItem(val group: WorktreeGroup, val worktree: Worktree) : ListItem {
        val ref = WorktreeRef(group.project.id, worktree.path)
        override val key = "worktree:${group.project.id}:${worktree.path}"
    }

    data class ChangeHeader(val group: ChangeGroup) : ListItem {
        override val key = "change-header:${group.branch}"
    }

    data class UngroupedHeader(val count: Int) : ListItem {
        override val key = UNGROUPED_HEADER_KEY
    }

    data class ChangeRow(val aliased: AliasedEntry) : ListItem {
        val ref = aliased.ref
        override val key = "change-row:${aliased.entry.project.id}:${aliased.entry.worktree.path}"
    }
}

internal fun listItems(board: WorktreeBoard): List<ListItem> = when {
    board.isGroupedByChange -> changeListItems(board)
    board.isGroupedByState -> stateListItems(board)
    else -> projectListItems(board)
}

private fun changeListItems(board: WorktreeBoard): List<ListItem> {
    val notices = board.shownProjects.mapNotNull { project ->
        val holder = board.holder(project.id)?.takeIf { it.hasNotice || it.loadState !is ProjectLoadState.Loaded } ?: return@mapNotNull null
        ListItem.GroupState(WorktreeGroup(project, holder, emptyList(), collapsed = false), showsProjectName = true)
    }
    val view = board.changeView
    val groups = view.groups.flatMap { group ->
        listOf<ListItem>(ListItem.ChangeHeader(group)) + if (group.collapsed) emptyList() else group.entries.map(ListItem::ChangeRow)
    }
    val ungrouped = if (view.ungrouped.isEmpty()) {
        emptyList()
    } else {
        listOf<ListItem>(ListItem.UngroupedHeader(view.ungrouped.size)) + view.ungrouped.map(ListItem::ChangeRow)
    }
    return notices + groups + ungrouped
}

private fun stateListItems(board: WorktreeBoard): List<ListItem> {
    val notices = board.shownProjects.mapNotNull { project ->
        val holder = board.holder(project.id)?.takeIf { it.hasNotice } ?: return@mapNotNull null
        ListItem.GroupState(WorktreeGroup(project, holder, emptyList(), collapsed = false), showsProjectName = true)
    }
    return notices + board.stateGroups.flatMap { group ->
        listOf<ListItem>(ListItem.StateHeader(group)) + group.items.map { ListItem.StateRow(group, it) }
    }
}

private fun projectListItems(board: WorktreeBoard): List<ListItem> = board.groups.flatMap { group ->
    buildList {
        if (!board.isFiltered) add(ListItem.Header(group))
        val holder = group.holder
        if (group.collapsed) {
            if (holder.hasNotice) add(ListItem.GroupState(group, showsProjectName = true))
            return@buildList
        }
        if (holder.hasGroupState) add(ListItem.GroupState(group))
        group.worktrees.forEach { add(ListItem.WorktreeItem(group, it)) }
    }
}

private val ProjectWorktrees.hasNotice: Boolean
    get() = loadState is ProjectLoadState.Failed || error != null || cleanupIssues != null || herdrCloseFailures.isNotEmpty() ||
        keptBranchNotice != null

private val ProjectWorktrees.hasGroupState: Boolean
    get() = loadState !is ProjectLoadState.Loaded || hasNotice

@Composable
private fun ProjectGroupHeader(
    group: WorktreeGroup,
    board: WorktreeBoard,
    onOpenProjectSettings: () -> Unit,
    onOpenGradle: () -> Unit,
) {
    val strings = LocalStrings.current
    val colors = MaterialTheme.colorScheme
    val holder = group.holder
    val projectId = group.project.id
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceContainer)
            .interactive { board.toggleCollapsed(projectId) }
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        IconAction(
            icon = if (group.collapsed) Icons.Default.ChevronRight else Icons.Default.ExpandMore,
            label = if (group.collapsed) strings.expand else strings.collapse,
            onClick = { board.toggleCollapsed(projectId) },
            tint = colors.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                board.multirepoNameOf(projectId)?.let { "$it / ${group.project.name}" } ?: group.project.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Text(
                strings.worktreeCount(if (board.isActiveView) group.worktrees.size else holder.worktrees.size),
                style = ShogunTheme.typography.dataSmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
            )
            if (holder.isLoading) InlineProgress()
            if (holder.isGradleRepository) {
                WithTooltip(label = strings.openGradleDaemons(group.project.name)) {
                    StatusChip(
                        Status.NEUTRAL,
                        strings.gradleChipLabel,
                        modifier = Modifier.interactive(shape = MaterialTheme.shapes.extraSmall, onClick = onOpenGradle),
                    )
                }
            }
        }
        DangerTextButton(
            onClick = { board.openCleanup(projectId) },
            enabled = board.canCleanUp(projectId),
        ) { Text(strings.cleanUp, style = MaterialTheme.typography.bodySmall) }
        IconAction(icon = Icons.Default.Settings, label = strings.projectConfigTitle, onClick = onOpenProjectSettings)
        IconAction(icon = Icons.Default.Add, label = strings.newWorktree, onClick = { board.openCreateDialog(projectId) })
    }
}

@Composable
private fun GroupState(holder: ProjectWorktrees, projectId: String, projectName: String? = null) {
    val strings = LocalStrings.current
    val noticeModifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)
    if (projectName != null) {
        Text(
            projectName,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(start = Spacing.md, end = Spacing.md, top = Spacing.sm),
        )
    }
    when (val state = holder.loadState) {
        ProjectLoadState.Loading -> Row(
            modifier = noticeModifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            InlineProgress()
            Text(strings.loadingWorktrees, style = MaterialTheme.typography.bodySmall)
        }
        is ProjectLoadState.Failed -> ErrorNotice(
            error = state.error,
            onDismiss = null,
            modifier = noticeModifier,
            projectId = projectId,
            onRetry = { holder.refresh() }.takeIf { !holder.isLoading },
        )
        ProjectLoadState.Loaded -> Unit
    }
    holder.error?.let { error ->
        ErrorNotice(
            error = error,
            onDismiss = { holder.dismissError() },
            modifier = noticeModifier,
            projectId = projectId,
            onRetry = holder.errorRetry?.let { { holder.retryError() } },
        )
    }
    holder.cleanupIssues?.let { report ->
        Box(Modifier.padding(horizontal = Spacing.md)) {
            CleanupIssues(report, onDismiss = { holder.dismissCleanupIssues() })
        }
    }
    holder.keptBranchNotice?.let { kept ->
        Box(Modifier.padding(horizontal = Spacing.md)) {
            KeptBranchNotice(kept, onDelete = { holder.requestForcedBranchDeletion() }, onDismiss = { holder.dismissKeptBranchNotice() })
        }
    }
    if (holder.herdrCloseFailures.isNotEmpty()) {
        Box(Modifier.padding(horizontal = Spacing.md)) {
            HerdrCloseFailures(holder.herdrCloseFailures, onDismiss = { holder.dismissHerdrCloseFailures() })
        }
    }
}

@Composable
internal fun StateGroupHeader(
    kind: StateGroupKind,
    count: Int,
    canCleanUp: Boolean,
    onCleanUp: () -> Unit,
    inactiveDays: Int = DEFAULT_INACTIVE_AFTER_DAYS,
) {
    val strings = LocalStrings.current
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceContainer)
            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            WithTooltip(label = kind.criterion(strings, inactiveDays), modifier = Modifier.weight(1f, fill = false)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Text(kind.label(strings), style = MaterialTheme.typography.titleSmall, maxLines = 1)
                    Text(
                        kind.hint(strings, inactiveDays),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
            }
            Text(strings.worktreeCount(count), style = ShogunTheme.typography.dataSmall, color = colors.onSurfaceVariant, maxLines = 1)
        }
        if (kind == StateGroupKind.MERGED) {
            DangerTextButton(
                onClick = onCleanUp,
                enabled = canCleanUp,
            ) { Text(strings.cleanUp, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

internal fun StateGroupKind.hint(strings: Strings, inactiveDays: Int = DEFAULT_INACTIVE_AFTER_DAYS): String = when (this) {
    StateGroupKind.AGENT_WORKING -> strings.groupAgentWorkingHint
    StateGroupKind.NEEDS_YOU -> strings.groupNeedsYouHint
    StateGroupKind.NEW -> strings.groupNewHint
    StateGroupKind.STOPPED -> strings.groupStoppedHint
    StateGroupKind.MERGED -> strings.groupMergedHint
    StateGroupKind.INACTIVE -> strings.groupInactiveHint(inactiveDays)
    StateGroupKind.FOLDER_MISSING -> strings.groupFolderMissingHint
    StateGroupKind.OTHER -> strings.groupOtherHint
}

internal fun StateGroupKind.criterion(strings: Strings, inactiveDays: Int = DEFAULT_INACTIVE_AFTER_DAYS): String = when (this) {
    StateGroupKind.AGENT_WORKING -> strings.groupAgentWorkingCriterion
    StateGroupKind.NEEDS_YOU -> strings.groupNeedsYouCriterion
    StateGroupKind.NEW -> strings.groupNewCriterion
    StateGroupKind.STOPPED -> strings.groupStoppedCriterion
    StateGroupKind.MERGED -> strings.groupMergedCriterion
    StateGroupKind.INACTIVE -> strings.groupInactiveCriterion(inactiveDays)
    StateGroupKind.FOLDER_MISSING -> strings.groupFolderMissingCriterion
    StateGroupKind.OTHER -> strings.groupOtherCriterion
}

internal fun StateGroupKind.label(strings: Strings): String = when (this) {
    StateGroupKind.AGENT_WORKING -> strings.groupAgentWorking
    StateGroupKind.NEEDS_YOU -> strings.groupNeedsYou
    StateGroupKind.NEW -> strings.groupNew
    StateGroupKind.STOPPED -> strings.groupStopped
    StateGroupKind.MERGED -> strings.groupMerged
    StateGroupKind.INACTIVE -> strings.groupInactive
    StateGroupKind.FOLDER_MISSING -> strings.groupFolderMissing
    StateGroupKind.OTHER -> strings.groupOther
}

@Composable
private fun WorktreeRowFor(
    board: WorktreeBoard,
    project: Project,
    holder: ProjectWorktrees,
    worktree: Worktree,
    projectName: String?,
    compact: Boolean,
    alias: String? = null,
    onOpenGroup: (GroupTag) -> Unit = {},
) {
    val strings = LocalStrings.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val ref = WorktreeRef(project.id, worktree.path)
    val groupTag = board.groupTagOf(ref)
    WorktreeRow(
        model = worktreeRowModel(
            worktree = worktree,
            status = holder.statuses[worktree.path],
            mainBranchName = holder.mainBranch?.name,
            agents = holder.agents.orEmpty()[worktree.path].orEmpty(),
            emulator = holder.emulators[worktree.path]?.takeIf { holder.isEmulatorRepository },
            simulator = holder.simulators[worktree.path]?.takeIf { holder.isSimulatorRepository },
            origin = classifyOrigin(worktree, project.config, holder.herdrWorktreesRoot),
            projectName = projectName,
            inactiveAfter = board.inactivity.threshold,
            now = Instant.now(),
            strings = strings,
            alias = alias,
            groupTag = groupTag?.label,
        ),
        selected = board.selected == ref,
        isCopied = holder.copiedWorktreePath == worktree.path,
        isMain = worktree.isMain,
        compact = compact,
        onSelect = { board.select(ref) },
        onCopyLaunchCommand = { holder.copyLaunchCommand(worktree) },
        onCopyPath = { scope.launch { clipboard.copyText(worktree.path) } },
        onOpenInHerdr = { holder.openInHerdr(worktree) },
        openActions = holder.openActions(worktree, strings),
        agentName = holder.agentFor(worktree).displayName.takeIf { holder.hasAgentChoice },
        launchChoices = holder.launchChoices(worktree, strings),
        onLaunch = { holder.launch(worktree, it.kind, it.agent) },
        onOpenGroupTag = { groupTag?.let(onOpenGroup) },
        onRemove = { holder.requestRemoval(worktree) },
    )
}

@Composable
private fun ChangeGroupHeader(group: ChangeGroup, board: WorktreeBoard) {
    val strings = LocalStrings.current
    ChangeGroupHeaderView(
        group = group,
        cleanUpBlocker = board.groupCleanupBlocker(group)?.describe(strings),
        canCleanUp = board.canCleanUpGroup(group),
        addableAliases = if (board.canAddRepositories(group)) board.missingAliases(group) else emptyList(),
        isAdding = board.addingToGroup.any { it.startsWith("${group.branch}:") },
        onToggle = { board.toggleChangeCollapsed(group.branch) },
        onOpenAll = { board.openAllInHerdr(group) },
        onAddRepository = { board.addRepositoryToGroup(group, it) },
        onCleanUp = { board.openGroupCleanup(group) },
    )
}

@Composable
internal fun ChangeGroupHeaderView(
    group: ChangeGroup,
    cleanUpBlocker: String?,
    canCleanUp: Boolean,
    addableAliases: List<String>,
    isAdding: Boolean,
    onToggle: () -> Unit,
    onOpenAll: () -> Unit,
    onAddRepository: (String) -> Unit,
    onCleanUp: () -> Unit,
) {
    val strings = LocalStrings.current
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceContainer)
            .interactive(onClick = onToggle)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        IconAction(
            icon = if (group.collapsed) Icons.Default.ChevronRight else Icons.Default.ExpandMore,
            label = if (group.collapsed) strings.expand else strings.collapse,
            onClick = onToggle,
            tint = colors.onSurfaceVariant,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(group.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOf(group.branch, group.aliases.joinToString(", ")).joinToString(" · "),
                style = ShogunTheme.typography.dataSmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        StatusChip(group.state.status ?: Status.INFO, group.state.label(strings))
        if (isAdding) InlineProgress()
        NeutralTextButton(onClick = onOpenAll) {
            Text(strings.openAllInHerdr, style = MaterialTheme.typography.bodySmall)
        }
        if (addableAliases.isNotEmpty()) AddRepositoryMenu(addableAliases, onAddRepository)
        val cleanUp: @Composable () -> Unit = {
            DangerTextButton(onClick = onCleanUp, enabled = canCleanUp) {
                Text(strings.cleanUpGroup, style = MaterialTheme.typography.bodySmall)
            }
        }
        cleanUpBlocker?.let { WithTooltip(label = it, content = cleanUp) } ?: cleanUp()
    }
}

@Composable
private fun AddRepositoryMenu(aliases: List<String>, onPick: (String) -> Unit) {
    val strings = LocalStrings.current
    var expanded by remember { mutableStateOf(false) }
    Box {
        NeutralTextButton(onClick = { expanded = true }) {
            Text(strings.addRepositoryToGroup, style = MaterialTheme.typography.bodySmall)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            aliases.forEach { alias ->
                DropdownMenuItem(
                    text = { Text(alias) },
                    onClick = {
                        expanded = false
                        onPick(alias)
                    },
                )
            }
        }
    }
}

internal fun GroupKept.describe(strings: Strings): String = when (reason) {
    GroupKeptReason.NOT_MERGED -> strings.groupKeptNotMerged(alias)
    GroupKeptReason.UNCOMMITTED_CHANGES -> strings.groupKeptUncommitted(alias)
    GroupKeptReason.STATUS_UNAVAILABLE -> strings.groupKeptStatusUnavailable(alias)
    GroupKeptReason.AGENT_RUNNING -> strings.groupKeptAgentRunning(alias, agents.map { it.name }.distinct())
}

@Composable
private fun UngroupedHeader(count: Int) {
    val strings = LocalStrings.current
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceContainer)
            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(strings.ungrouped, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Text(strings.worktreeCount(count), style = ShogunTheme.typography.dataSmall, color = colors.onSurfaceVariant)
    }
}
