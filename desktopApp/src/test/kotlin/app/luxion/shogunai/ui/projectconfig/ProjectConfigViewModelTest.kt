package app.luxion.shogunai.ui.projectconfig

import app.luxion.shogunai.domain.FakeProjectRepository
import app.luxion.shogunai.domain.FakeRepositoryProvisioningSource
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.RepositoryProvisioning
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.coroutines.runBlocking
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

    private fun newProjectViewModel(
        inspections: Map<String, RepositoryInspection> = emptyMap(),
    ) = ProjectConfigViewModel(
        projectRepository,
        provisioningSource,
        existingProject = null,
        inspectRepository = { folder ->
            inspections[folder]?.let { Result.success(it) } ?: Result.failure(WorktreeError.RepositoryNotFound(folder))
        },
    )

    @Test
    fun `an untouched new form shows no errors`() {
        val viewModel = newProjectViewModel()

        assertFalse(viewModel.isErrorVisible(ProjectConfigField.NAME))
        assertFalse(viewModel.isErrorVisible(ProjectConfigField.BASE_REPOSITORY_PATH))
    }

    @Test
    fun `emptying an edited name shows its error`() {
        val viewModel = newProjectViewModel()

        viewModel.name = "api"
        assertFalse(viewModel.isErrorVisible(ProjectConfigField.NAME))
        viewModel.name = ""

        assertTrue(viewModel.isErrorVisible(ProjectConfigField.NAME))
        assertFalse(viewModel.isErrorVisible(ProjectConfigField.BASE_REPOSITORY_PATH))
    }

    @Test
    fun `saving with errors does not save and reports the first invalid field`() {
        val viewModel = newProjectViewModel()

        val outcome = viewModel.submit()

        assertEquals(SaveOutcome.Invalid(ProjectConfigField.NAME), outcome)
        assertTrue(viewModel.isErrorVisible(ProjectConfigField.NAME))
        assertTrue(viewModel.isErrorVisible(ProjectConfigField.BASE_REPOSITORY_PATH))
        assertTrue(projectRepository.list().isEmpty())
    }

    @Test
    fun `saving a valid form saves the project`() {
        val viewModel = newProjectViewModel()
        viewModel.name = "api"
        viewModel.baseRepositoryPath = "/home/dev/projects/api"

        val outcome = viewModel.submit()

        assertIs<SaveOutcome.Saved>(outcome)
        assertEquals(1, projectRepository.list().size)
    }

    @Test
    fun `choosing a repository prefills the name, a sibling worktrees folder and the detected main branch`() = runBlocking {
        val viewModel = newProjectViewModel(
            mapOf("/home/dev/projects/main-repo/app" to RepositoryInspection("/home/dev/projects/main-repo", "main")),
        )

        val result = viewModel.chooseFolder("/home/dev/projects/main-repo/app")

        assertTrue(result.isSuccess)
        assertTrue(viewModel.folderChosen)
        assertEquals("main-repo", viewModel.name)
        assertEquals("/home/dev/projects/main-repo", viewModel.baseRepositoryPath)
        assertEquals("/home/dev/projects/main-repo-worktrees", viewModel.worktreesRoot)
        assertEquals("main", viewModel.detectedMainBranch)
        assertEquals("", viewModel.mainBranch)
        assertFalse(viewModel.hasUnsavedChanges)
        assertFalse(viewModel.isErrorVisible(ProjectConfigField.NAME))
    }

    @Test
    fun `a folder that is not a repository is rejected and the form stays closed`() = runBlocking {
        val viewModel = newProjectViewModel()

        val result = viewModel.chooseFolder("/home/dev/projects/plain")

        assertTrue(result.isFailure)
        assertFalse(viewModel.folderChosen)
        assertEquals("/home/dev/projects/plain", viewModel.rejectedFolder)
        assertEquals("", viewModel.baseRepositoryPath)
    }
}
