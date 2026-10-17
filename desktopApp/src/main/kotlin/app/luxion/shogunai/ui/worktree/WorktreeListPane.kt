package app.luxion.shogunai.ui.worktree

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.usecase.classifyOrigin
import app.luxion.shogunai.ui.components.DangerTextButton
import app.luxion.shogunai.ui.components.IconAction
import app.luxion.shogunai.ui.components.InlineProgress
import app.luxion.shogunai.ui.components.Notice
import app.luxion.shogunai.ui.components.NoticeKind
import app.luxion.shogunai.ui.components.SecondaryButton
import app.luxion.shogunai.ui.components.Spacing
import app.luxion.shogunai.ui.components.interactive
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.i18n.worktreeError
import app.luxion.shogunai.ui.theme.ShogunTheme
import java.time.Instant

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WorktreeListPane(
    board: WorktreeBoard,
    onOpenProjectSettings: (Project) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val items = listItems(board)
    LaunchedEffect(board.selected) {
        val selected = board.selected ?: return@LaunchedEffect
        val index = items.indexOfFirst { it is ListItem.WorktreeItem && it.ref == selected }
        if (index >= 0) listState.animateScrollToItem(index)
    }
    Box(
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
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = Spacing.xl),
        ) {
            items.forEach { item ->
                when (item) {
                    is ListItem.Header -> stickyHeader(key = item.key) {
                        ProjectGroupHeader(item.group, board, onOpenProjectSettings = { onOpenProjectSettings(item.group.project) })
                    }
                    is ListItem.GroupState -> item(key = item.key) { GroupState(item.group.holder) }
                    is ListItem.WorktreeItem -> item(key = item.key) {
                        GroupWorktreeRow(board, item.group, item.worktree)
                        HorizontalDivider()
                    }
                    is ListItem.Gradle -> item(key = item.key) { GradleDaemonsSection(item.group.holder) }
                    is ListItem.Xcode -> item(key = item.key) { XcodeDerivedDataSection(item.group.holder) }
                }
            }
        }
    }
}

private sealed interface ListItem {
    val key: String

    data class Header(val group: WorktreeGroup) : ListItem {
        override val key = "header:${group.project.id}"
    }

    data class GroupState(val group: WorktreeGroup) : ListItem {
        override val key = "state:${group.project.id}"
    }

    data class WorktreeItem(val group: WorktreeGroup, val worktree: Worktree) : ListItem {
        val ref = WorktreeRef(group.project.id, worktree.path)
        override val key = "worktree:${group.project.id}:${worktree.path}"
    }

    data class Gradle(val group: WorktreeGroup) : ListItem {
        override val key = "gradle:${group.project.id}"
    }

    data class Xcode(val group: WorktreeGroup) : ListItem {
        override val key = "xcode:${group.project.id}"
    }
}

private fun listItems(board: WorktreeBoard): List<ListItem> = board.groups.flatMap { group ->
    buildList {
        if (!board.isFiltered) add(ListItem.Header(group))
        if (group.collapsed) return@buildList
        val holder = group.holder
        if (holder.hasGroupState) add(ListItem.GroupState(group))
        group.worktrees.forEach { add(ListItem.WorktreeItem(group, it)) }
        if (!board.isTextFiltering && holder.isGradleRepository) add(ListItem.Gradle(group))
        if (!board.isTextFiltering && holder.isXcodeRepository) add(ListItem.Xcode(group))
    }
}

private val ProjectWorktrees.hasGroupState: Boolean
    get() = loadState !is ProjectLoadState.Loaded || error != null || cleanupIssues != null

@Composable
private fun ProjectGroupHeader(group: WorktreeGroup, board: WorktreeBoard, onOpenProjectSettings: () -> Unit) {
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
        Text(
            group.project.name,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Text(
            strings.worktreeCount(holder.worktrees.size),
            style = ShogunTheme.typography.dataSmall,
            color = colors.onSurfaceVariant,
        )
        if (holder.isLoading) InlineProgress()
        Box(Modifier.weight(1f))
        DangerTextButton(
            onClick = { board.openCleanup(projectId) },
            enabled = board.canCleanUp(projectId),
        ) { Text(strings.cleanUp, style = MaterialTheme.typography.bodySmall) }
        IconAction(icon = Icons.Default.Settings, label = strings.projectConfigTitle, onClick = onOpenProjectSettings)
        IconAction(icon = Icons.Default.Add, label = strings.newWorktree, onClick = { board.openCreateDialog(projectId) })
    }
}

@Composable
private fun GroupState(holder: ProjectWorktrees) {
    val strings = LocalStrings.current
    val noticeModifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)
    when (val state = holder.loadState) {
        ProjectLoadState.Loading -> Row(
            modifier = noticeModifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            InlineProgress()
            Text(strings.loadingWorktrees, style = MaterialTheme.typography.bodySmall)
        }
        is ProjectLoadState.Failed -> Row(
            modifier = noticeModifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                strings.worktreeError(state.error),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f),
            )
            SecondaryButton(onClick = { holder.refresh() }, enabled = !holder.isLoading) { Text(strings.reload) }
        }
        ProjectLoadState.Loaded -> Unit
    }
    holder.error?.let { error ->
        Notice(
            kind = NoticeKind.ERROR,
            message = strings.worktreeError(error),
            onDismiss = { holder.dismissError() },
            modifier = noticeModifier,
        )
    }
    holder.cleanupIssues?.let { report ->
        Box(Modifier.padding(horizontal = Spacing.md)) {
            CleanupIssues(report, onDismiss = { holder.dismissCleanupIssues() })
        }
    }
}

@Composable
private fun GroupWorktreeRow(board: WorktreeBoard, group: WorktreeGroup, worktree: Worktree) {
    val strings = LocalStrings.current
    val clipboard = LocalClipboardManager.current
    val holder = group.holder
    val ref = WorktreeRef(group.project.id, worktree.path)
    WorktreeRow(
        model = worktreeRowModel(
            worktree = worktree,
            status = holder.statuses[worktree.path],
            mainBranchName = holder.mainBranch?.name,
            agents = holder.agents.orEmpty()[worktree.path].orEmpty(),
            emulator = holder.emulators[worktree.path]?.takeIf { holder.isEmulatorRepository },
            simulator = holder.simulators[worktree.path]?.takeIf { holder.isSimulatorRepository },
            origin = classifyOrigin(worktree, group.project.config, holder.herdrWorktreesRoot),
            now = Instant.now(),
            strings = strings,
        ),
        selected = board.selected == ref,
        isCopied = holder.copiedWorktreePath == worktree.path,
        isMain = worktree.isMain,
        onSelect = { board.select(ref) },
        onCopyLaunchCommand = { holder.copyLaunchCommand(worktree) },
        onCopyPath = { clipboard.setText(AnnotatedString(worktree.path)) },
        onOpenInHerdr = { holder.openInHerdr(worktree) },
        onRemove = { holder.requestRemoval(worktree) },
    )
}
