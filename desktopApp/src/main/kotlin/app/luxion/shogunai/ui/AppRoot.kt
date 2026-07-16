package app.luxion.shogunai.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.luxion.shogunai.AppContainer
import app.luxion.shogunai.ui.projectconfig.ProjectConfigScreen
import app.luxion.shogunai.ui.projectconfig.ProjectConfigViewModel
import app.luxion.shogunai.ui.projectlist.ProjectListScreen
import app.luxion.shogunai.ui.projectlist.ProjectListViewModel
import app.luxion.shogunai.ui.theme.ShogunAiTheme
import app.luxion.shogunai.ui.theme.ThemeModeToggle
import app.luxion.shogunai.ui.worktree.WorktreeScreen
import app.luxion.shogunai.ui.worktree.WorktreeViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter

@Composable
fun AppRoot(appContainer: AppContainer) {
    var screen by remember { mutableStateOf<Screen>(Screen.ProjectList) }
    var themeMode by remember { mutableStateOf(appContainer.themePreferenceRepository.load()) }

    ShogunAiTheme(themeMode) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                ThemeModeToggle(
                    themeMode = themeMode,
                    onThemeModeChange = { mode ->
                        themeMode = mode
                        appContainer.themePreferenceRepository.save(mode)
                    },
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                when (val current = screen) {
                    is Screen.ProjectList -> {
                        val viewModel = viewModel { ProjectListViewModel(appContainer.projectRepository) }
                        val windowInfo = LocalWindowInfo.current
                        LaunchedEffect(Unit) { viewModel.refresh() }
                        LaunchedEffect(windowInfo) {
                            snapshotFlow { windowInfo.isWindowFocused }
                                .drop(1)
                                .filter { it }
                                .collectLatest { viewModel.refresh() }
                        }
                        ProjectListScreen(
                            viewModel = viewModel,
                            onSelectProject = { screen = Screen.WorktreeManagement(it) },
                            onNewProject = { screen = Screen.ProjectConfigForm() },
                            onEditProject = { screen = Screen.ProjectConfigForm(existingProject = it) },
                        )
                    }

                    is Screen.ProjectConfigForm -> {
                        val viewModel = viewModel(key = current.existingProject?.id ?: "new-project") {
                            ProjectConfigViewModel(appContainer.projectRepository, current.existingProject)
                        }
                        ProjectConfigScreen(
                            viewModel = viewModel,
                            onSaved = { project -> screen = Screen.WorktreeManagement(project) },
                            onCancel = { screen = Screen.ProjectList },
                        )
                    }

                    is Screen.WorktreeManagement -> {
                        val viewModel = viewModel(key = current.project.id) {
                            WorktreeViewModel(appContainer.worktreeUseCases(current.project.config))
                        }
                        val windowInfo = LocalWindowInfo.current
                        LaunchedEffect(current.project.id) { viewModel.refresh() }
                        LaunchedEffect(current.project.id, windowInfo) {
                            snapshotFlow { windowInfo.isWindowFocused }
                                .drop(1)
                                .filter { it }
                                .collectLatest { viewModel.refresh() }
                        }
                        WorktreeScreen(
                            projectName = current.project.name,
                            viewModel = viewModel,
                            onBack = { screen = Screen.ProjectList },
                        )
                    }
                }
            }
        }
    }
}
