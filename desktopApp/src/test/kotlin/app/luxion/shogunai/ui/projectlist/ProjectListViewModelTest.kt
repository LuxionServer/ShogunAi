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

    @Test
    fun `search filters projects by name ignoring case`() {
        val viewModel = ProjectListViewModel(FakeProjectRepository(initialProjects = listOf(projectA, projectB)))

        viewModel.searchQuery = "project b"

        assertEquals(listOf(projectB), viewModel.visibleProjects)
    }

    @Test
    fun `clearing the search shows every project again`() {
        val viewModel = ProjectListViewModel(FakeProjectRepository(initialProjects = listOf(projectA, projectB)))
        viewModel.searchQuery = "project b"

        viewModel.searchQuery = ""

        assertEquals(listOf(projectA, projectB), viewModel.visibleProjects)
    }

    @Test
    fun `sorting reorders the visible projects without changing the loaded order`() {
        val viewModel = ProjectListViewModel(FakeProjectRepository(initialProjects = listOf(projectA, projectB)))

        viewModel.sortOrder = ProjectSortOrder.NAME_DESC

        assertEquals(listOf(projectB, projectA), viewModel.visibleProjects)
        assertEquals(listOf(projectA, projectB), viewModel.projects)
    }

    @Test
    fun `deleting a project removes it from the repository and the list`() {
        val repository = FakeProjectRepository(initialProjects = listOf(projectA, projectB))
        val viewModel = ProjectListViewModel(repository)

        viewModel.deleteProject(projectA)

        assertEquals(listOf(projectB), viewModel.projects)
        assertEquals(listOf(projectB), repository.list())
    }
}
