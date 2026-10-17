package app.luxion.shogunai.ui.worktree

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.usecase.classifyOrigin
import app.luxion.shogunai.ui.components.Spacing
import app.luxion.shogunai.ui.i18n.LocalStrings
import java.time.Instant

@Composable
fun WorktreeListPane(viewModel: WorktreeViewModel, projectConfig: ProjectConfig, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    val strings = LocalStrings.current
    val mainBranchName = viewModel.mainBranch?.name
    val clipboard = LocalClipboardManager.current
    LaunchedEffect(viewModel.selectedWorktreePath) {
        val index = viewModel.orderedWorktrees.indexOfFirst { it.path == viewModel.selectedWorktreePath }
        if (index >= 0) listState.animateScrollToItem(index)
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionDown -> {
                            viewModel.selectNext()
                            true
                        }
                        Key.DirectionUp -> {
                            viewModel.selectPrevious()
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
            items(viewModel.orderedWorktrees, key = { it.path }) { worktree ->
                WorktreeRow(
                    model = worktreeRowModel(
                        worktree = worktree,
                        status = viewModel.statuses[worktree.path],
                        mainBranchName = mainBranchName,
                        agents = viewModel.agents.orEmpty()[worktree.path].orEmpty(),
                        emulator = emulatorFor(worktree, viewModel),
                        simulator = simulatorFor(worktree, viewModel),
                        origin = classifyOrigin(worktree, projectConfig, viewModel.herdrWorktreesRoot),
                        now = Instant.now(),
                        strings = strings,
                    ),
                    selected = viewModel.selectedWorktreePath == worktree.path,
                    isCopied = viewModel.copiedWorktreePath == worktree.path,
                    isMain = worktree.isMain,
                    onSelect = { viewModel.select(worktree.path) },
                    onCopyLaunchCommand = { viewModel.copyLaunchCommand(worktree) },
                    onCopyPath = { clipboard.setText(AnnotatedString(worktree.path)) },
                    onOpenInHerdr = { viewModel.openInHerdr(worktree) },
                    onRemove = { viewModel.requestRemoval(worktree) },
                )
                HorizontalDivider()
            }
            if (viewModel.isGradleRepository) {
                item(key = "gradle-daemons") {
                    GradleDaemonsSection(viewModel)
                }
            }
            if (viewModel.isXcodeRepository) {
                item(key = "xcode-derived-data") {
                    XcodeDerivedDataSection(viewModel)
                }
            }
        }
    }
}

private fun emulatorFor(worktree: Worktree, viewModel: WorktreeViewModel) =
    viewModel.emulators[worktree.path]?.takeIf { viewModel.isEmulatorRepository }

private fun simulatorFor(worktree: Worktree, viewModel: WorktreeViewModel) =
    viewModel.simulators[worktree.path]?.takeIf { viewModel.isSimulatorRepository }
