package app.luxion.shogunai.ui.projectconfig

import app.luxion.shogunai.domain.FakeProjectRepository
import app.luxion.shogunai.domain.FakeRepositoryProvisioningSource
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.RepositoryProvisioning
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProjectConfigViewModelTest {

    private val basePath = "/home/dev/projects/main-repo"
    private var provisioningSource = FakeRepositoryProvisioningSource()
    private val projectRepository = FakeProjectRepository()

    private fun projectWith(
        secretFiles: List<String> = emptyList(),
        worktreesRoot: String = "/home/dev/projects",
        mainBranch: String? = null,
    ) = Project(
        id = "p",
        name = "Project",
        config = ProjectConfig(
            baseRepositoryPath = "/home/dev/projects/main-repo",
            worktreesRoot = worktreesRoot,
            secretFiles = secretFiles,
            mainBranch = mainBranch,
        ),
    )

    private fun viewModelFor(project: Project?) = ProjectConfigViewModel(projectRepository, provisioningSource, project)

    @Test
    fun `relative secret files follow the base repository when it moves`() {
        val viewModel = viewModelFor(projectWith(listOf("local.properties", "app/secrets.properties")))

        viewModel.baseRepositoryPath = "/home/dev/moved/main-repo"
        val saved = viewModel.save().getOrThrow()

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
        val saved = viewModel.save().getOrThrow()

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

    @Test
    fun `adding a secret file that is already listed leaves the list unchanged`() {
        val viewModel = viewModelFor(projectWith(listOf("local.properties")))

        viewModel.addSecretFile("local.properties")

        assertEquals(listOf("local.properties"), viewModel.secretFiles)
    }

    @Test
    fun `an untouched form has no unsaved changes`() {
        assertFalse(viewModelFor(projectWith()).hasUnsavedChanges)
        assertFalse(viewModelFor(null).hasUnsavedChanges)
    }

    @Test
    fun `editing a field marks the form as having unsaved changes`() {
        val viewModel = viewModelFor(projectWith())

        viewModel.name = "Renamed"

        assertTrue(viewModel.hasUnsavedChanges)
    }

    @Test
    fun `a new project has no main branch by default`() {
        val viewModel = viewModelFor(null).apply {
            name = "Project"
            baseRepositoryPath = "/home/dev/projects/main-repo"
        }

        assertNull(viewModel.save().getOrThrow().config.mainBranch)
    }

    @Test
    fun `the main branch is saved trimmed`() {
        val viewModel = viewModelFor(projectWith())

        viewModel.mainBranch = "  develop "

        assertEquals("develop", viewModel.save().getOrThrow().config.mainBranch)
    }

    @Test
    fun `a blank main branch is saved as not set`() {
        val viewModel = viewModelFor(projectWith(mainBranch = "develop"))

        viewModel.mainBranch = "   "

        assertNull(viewModel.save().getOrThrow().config.mainBranch)
    }

    @Test
    fun `editing the main branch marks the form as having unsaved changes`() {
        val viewModel = viewModelFor(projectWith(mainBranch = "develop"))
        assertFalse(viewModel.hasUnsavedChanges)

        viewModel.mainBranch = "main"

        assertTrue(viewModel.hasUnsavedChanges)
    }

    @Test
    fun `shows the provisioning the repository declares`() {
        val declared = RepositoryProvisioning(copyPatterns = listOf("local.properties"), worktreeIncludePatterns = listOf(".env"))
        provisioningSource = FakeRepositoryProvisioningSource(byRepository = mapOf(basePath to declared))

        val viewModel = viewModelFor(projectWith())

        assertEquals(RepositoryProvisioningState.Declared(declared), viewModel.repositoryProvisioning)
    }

    @Test
    fun `shows nothing declared for a repository without provisioning files`() {
        assertEquals(RepositoryProvisioningState.None, viewModelFor(projectWith()).repositoryProvisioning)
    }

    @Test
    fun `reloads the provisioning when the base repository path changes`() {
        val other = RepositoryProvisioning(copyPatterns = listOf(".env*"))
        provisioningSource = FakeRepositoryProvisioningSource(byRepository = mapOf("/home/dev/projects/other-repo" to other))
        val viewModel = viewModelFor(projectWith())

        viewModel.baseRepositoryPath = "/home/dev/projects/other-repo"

        assertEquals(RepositoryProvisioningState.Declared(other), viewModel.repositoryProvisioning)
    }

    @Test
    fun `reports an unreadable repository file and offers no move`() {
        provisioningSource = FakeRepositoryProvisioningSource(unreadable = setOf(basePath))

        val viewModel = viewModelFor(projectWith(listOf("local.properties")))

        assertIs<RepositoryProvisioningState.Unreadable>(viewModel.repositoryProvisioning)
        assertFalse(viewModel.canMoveLocalFilesToRepository)
        viewModel.moveLocalFilesToRepository()
        assertEquals(listOf("local.properties"), viewModel.secretFiles)
    }

    @Test
    fun `moving local files anchors the ones inside the repository and keeps the rest local`() {
        val viewModel = viewModelFor(
            projectWith(listOf("local.properties", "app/google-services.json", "/etc/keys/release.jks", "../shared/key.json")),
        )

        viewModel.moveLocalFilesToRepository()

        assertEquals(listOf("/local.properties", "/app/google-services.json"), viewModel.pendingRepositoryPatterns)
        assertEquals(listOf("/etc/keys/release.jks", "../shared/key.json"), viewModel.secretFiles)
        assertTrue(viewModel.hasUnsavedChanges)
        assertTrue(provisioningSource.addedPatterns.isEmpty(), "nothing is written before saving")
    }

    @Test
    fun `saving writes the moved patterns to the repository before the project`() {
        val viewModel = viewModelFor(projectWith(listOf("local.properties", "/etc/keys/release.jks")))
        viewModel.moveLocalFilesToRepository()

        val saved = viewModel.save().getOrThrow()

        assertEquals(listOf(basePath to listOf("/local.properties")), provisioningSource.addedPatterns)
        assertEquals(listOf("/etc/keys/release.jks"), saved.config.secretFiles)
        assertEquals(saved, projectRepository.list().single())
        assertEquals(emptyList(), viewModel.pendingRepositoryPatterns)
    }

    @Test
    fun `a failed write leaves the project unchanged`() {
        provisioningSource = FakeRepositoryProvisioningSource(failWritesFor = setOf(basePath))
        val viewModel = viewModelFor(projectWith(listOf("local.properties")))
        viewModel.moveLocalFilesToRepository()

        val result = viewModel.save()

        assertTrue(result.isFailure)
        assertTrue(projectRepository.list().isEmpty())
        assertEquals(listOf("/local.properties"), viewModel.pendingRepositoryPatterns)
    }

    @Test
    fun `nothing to move without local files inside the repository`() {
        assertFalse(viewModelFor(projectWith()).canMoveLocalFilesToRepository)
        assertFalse(viewModelFor(projectWith(listOf("/etc/keys/release.jks"))).canMoveLocalFilesToRepository)
        assertTrue(viewModelFor(projectWith(listOf("local.properties"))).canMoveLocalFilesToRepository)
    }

    @Test
    fun `saving without moving never writes to the repository`() {
        val viewModel = viewModelFor(projectWith(listOf("local.properties")))
        viewModel.name = "Renamed"

        viewModel.save().getOrThrow()

        assertTrue(provisioningSource.addedPatterns.isEmpty())
    }
}
