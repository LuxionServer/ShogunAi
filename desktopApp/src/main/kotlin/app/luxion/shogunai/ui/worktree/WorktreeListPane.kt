package app.luxion.shogunai.ui.worktree

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.usecase.classifyOrigin
import app.luxion.shogunai.ui.components.Spacing
import java.time.Instant

@Composable
fun WorktreeListPane(
    projectConfig: ProjectConfig,
    viewModel: WorktreeViewModel,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboardManager.current
    val listState = rememberLazyListState()
    val selectedIndex = viewModel.worktrees.indexOfFirst { it.path == viewModel.selectedWorktreePath }
    LaunchedEffect(selectedIndex) {
        val visible = listState.layoutInfo.visibleItemsInfo
        if (selectedIndex >= 0 && visible.none { it.index == selectedIndex }) {
            listState.animateScrollToItem(selectedIndex)
        }
    }
    LazyColumn(
        state = listState,
        modifier = modifier
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionDown -> true.also { viewModel.selectNext() }
                    Key.DirectionUp -> true.also { viewModel.selectPrevious() }
                    else -> false
                }
            },
        contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = Spacing.xs),
    ) {
        items(viewModel.worktrees, key = { it.path }) { worktree ->
            val agents = viewModel.agents?.get(worktree.path).orEmpty()
            val model = worktreeRowModel(
                worktree = worktree,
                status = viewModel.statuses[worktree.path],
                agents = agents,
                emulator = viewModel.emulators[worktree.path].takeIf { viewModel.isEmulatorRepository },
                simulator = viewModel.simulators[worktree.path].takeIf { viewModel.isSimulatorRepository },
                origin = classifyOrigin(worktree, projectConfig, viewModel.herdrWorktreesRoot),
                now = Instant.now(),
            )
            WorktreeRow(
                model = model,
                selected = worktree.path == viewModel.selectedWorktreePath,
                isCopied = viewModel.copiedWorktreePath == worktree.path,
                onSelect = {
                    viewModel.select(worktree.path)
                    focusRequester.requestFocus()
                },
                onCopyLaunchCommand = { viewModel.copyLaunchCommand(worktree) },
                onCopyPath = { clipboard.setText(AnnotatedString(worktree.path)) },
                onRemove = { viewModel.requestRemoval(worktree) },
            )
        }
        if (viewModel.isGradleRepository) {
            item { GradleDaemonsSection(viewModel, modifier = Modifier.padding(vertical = Spacing.lg)) }
        }
        if (viewModel.isXcodeRepository) {
            item { XcodeDerivedDataSection(viewModel, modifier = Modifier.padding(vertical = Spacing.lg)) }
        }
    }
}
