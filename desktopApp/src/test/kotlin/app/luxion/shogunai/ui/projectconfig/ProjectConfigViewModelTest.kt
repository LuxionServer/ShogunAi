package app.luxion.shogunai.ui.projectconfig

import app.luxion.shogunai.domain.FakeProjectRepository
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.ProjectConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ProjectConfigViewModelTest {

    private fun projectWith(
        secretFiles: List<String> = emptyList(),
        worktreesRoot: String = "/home/dev/projects",
    ) = Project(
        id = "p",
        name = "Project",
        config = ProjectConfig(
            baseRepositoryPath = "/home/dev/projects/main-repo",
            worktreesRoot = worktreesRoot,
            secretFiles = secretFiles,
        ),
    )

    private fun viewModelFor(project: Project?) = ProjectConfigViewModel(FakeProjectRepository(), project)

    @Test
    fun `relative secret files follow the base repository when it moves`() {
        val viewModel = viewModelFor(projectWith(listOf("local.properties", "app/secrets.properties")))

        viewModel.baseRepositoryPath = "/home/dev/moved/main-repo"
        val saved = viewModel.save()

        assertEquals("/home/dev/moved/main-repo", saved.config.baseRepositoryPath)
        assertEquals(listOf("local.properties", "app/secrets.properties"), saved.config.secretFiles)
        assertEquals("/home/dev/moved/main-repo/local.properties", saved.config.baseSecretPath("local.properties"))
    }

    @Test
    fun `absolute secret files inside the old base repository are rebased when it moves`() {
        val viewModel = viewModelFor(
            projectWith(listOf("/home/dev/projects/main-repo/local.properties", "/home/dev/projects/main-repo/app/key.json")),
        )

        viewModel.baseRepositoryPath = "/home/dev/moved/main-repo"
        val saved = viewModel.save()

        assertEquals(listOf("local.properties", "app/key.json"), saved.config.secretFiles)
    }

    @Test
    fun `absolute secret files are rebased while the base path is typed character by character`() {
        val viewModel = viewModelFor(projectWith(listOf("/home/dev/projects/main-repo/local.properties")))

        viewModel.baseRepositoryPath = ""
        "/home/dev/moved/main-repo".forEachIndexed { index, _ ->
            viewModel.baseRepositoryPath = "/home/dev/moved/main-repo".substring(0, index + 1)
        }

        assertEquals(listOf("local.properties"), viewModel.secretFiles)
    }

    @Test
    fun `absolute secret files outside the base repository are kept as they are`() {
        val viewModel = viewModelFor(projectWith(listOf("/etc/shared/secrets.properties")))

        viewModel.baseRepositoryPath = "/home/dev/moved/main-repo"

        assertEquals(listOf("/etc/shared/secrets.properties"), viewModel.secretFiles)
    }

    @Test
    fun `loading a project with absolute secret files inside the base does not mark it as modified`() {
        val viewModel = viewModelFor(projectWith(listOf("/home/dev/projects/main-repo/local.properties")))

        assertEquals(listOf("local.properties"), viewModel.secretFiles)
        assertFalse(viewModel.hasUnsavedChanges)
    }

    @Test
    fun `adding an absolute path inside the base repository stores it as relative`() {
        val viewModel = viewModelFor(projectWith(emptyList()))

        viewModel.addSecretFile("/home/dev/projects/main-repo/app/local.properties")

        assertEquals(listOf("app/local.properties"), viewModel.secretFiles)
    }

    @Test
    fun `worktrees root next to the base repository follows it when it moves`() {
        val viewModel = viewModelFor(projectWith(worktreesRoot = "/home/dev/projects"))

        viewModel.baseRepositoryPath = "/home/dev/moved/main-repo"

        assertEquals("/home/dev/moved", viewModel.worktreesRoot)
    }

    @Test
    fun `worktrees root inside the base repository follows it when it moves`() {
        val viewModel = viewModelFor(projectWith(worktreesRoot = "/home/dev/projects/main-repo/.worktrees"))

        viewModel.baseRepositoryPath = "/home/dev/moved/main-repo"

        assertEquals("/home/dev/moved/main-repo/.worktrees", viewModel.worktreesRoot)
    }

    @Test
    fun `worktrees root follows the base path while it is typed character by character`() {
        val viewModel = viewModelFor(projectWith(worktreesRoot = "/home/dev/projects/worktrees"))
        val newBase = "/home/dev/moved/main-repo"

        viewModel.baseRepositoryPath = ""
        newBase.indices.forEach { viewModel.baseRepositoryPath = newBase.substring(0, it + 1) }

        assertEquals("/home/dev/moved/worktrees", viewModel.worktreesRoot)
    }

    @Test
    fun `worktrees root unrelated to the base repository stays when it moves`() {
        val viewModel = viewModelFor(projectWith(worktreesRoot = "/mnt/fast-disk/worktrees"))

        viewModel.baseRepositoryPath = "/home/dev/moved/main-repo"

        assertEquals("/mnt/fast-disk/worktrees", viewModel.worktreesRoot)
    }

    @Test
    fun `worktrees root edited by hand stays when the base repository moves`() {
        val viewModel = viewModelFor(projectWith(worktreesRoot = "/home/dev/projects"))

        viewModel.worktreesRoot = "/home/dev/custom"
        viewModel.baseRepositoryPath = "/home/dev/moved/main-repo"

        assertEquals("/home/dev/custom", viewModel.worktreesRoot)
    }
}
