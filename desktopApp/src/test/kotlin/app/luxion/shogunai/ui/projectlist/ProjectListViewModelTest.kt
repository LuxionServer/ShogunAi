package app.luxion.shogunai.ui.projectlist

import app.luxion.shogunai.domain.FakeProjectRepository
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.ProjectConfig
import kotlin.test.Test
import kotlin.test.assertEquals

class ProjectListViewModelTest {

    private val config = ProjectConfig(
        baseRepositoryPath = "/home/dev/projects/main-repo",
        worktreesRoot = "/home/dev/projects",
        secretFiles = emptyList(),
    )
    private val projectA = Project(id = "a", name = "Project A", config = config)
    private val projectB = Project(id = "b", name = "Project B", config = config)

    @Test
    fun `refresh re-reads from the repository and replaces projects with the new result`() {
        val repository = FakeProjectRepository(initialProjects = listOf(projectA))
        val viewModel = ProjectListViewModel(repository)
        assertEquals(listOf(projectA), viewModel.projects)

        repository.save(projectB)
        repository.delete(projectA.id)
        viewModel.refresh()

        assertEquals(listOf(projectB), viewModel.projects)
    }
}
