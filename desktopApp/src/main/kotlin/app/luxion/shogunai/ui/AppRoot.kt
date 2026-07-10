package app.luxion.shogunai.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import app.luxion.shogunai.AppContainer
import app.luxion.shogunai.ui.projectconfig.ProjectConfigScreen
import app.luxion.shogunai.ui.projectconfig.ProjectConfigViewModel
import app.luxion.shogunai.ui.projectlist.ProjectListScreen
import app.luxion.shogunai.ui.projectlist.ProjectListViewModel
import app.luxion.shogunai.ui.worktree.WorktreeScreen
import app.luxion.shogunai.ui.worktree.WorktreeViewModel

@Composable
fun AppRoot(appContainer: AppContainer) {
    var screen by remember { mutableStateOf<Screen>(Screen.ProjectList) }

    MaterialTheme {
        when (val current = screen) {
            is Screen.ProjectList -> {
                val viewModel = viewModel { ProjectListViewModel(appContainer.projectRepository) }
                LaunchedEffect(Unit) { viewModel.refresh() }
                ProjectListScreen(
                    viewModel = viewModel,
                    onSelectProject = { screen = Screen.WorktreeManagement(it) },
                    onNewProject = { screen = Screen.ProjectConfigForm() },
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
                WorktreeScreen(
                    projectName = current.project.name,
                    viewModel = viewModel,
                    onBack = { screen = Screen.ProjectList },
                )
            }
        }
    }
}
