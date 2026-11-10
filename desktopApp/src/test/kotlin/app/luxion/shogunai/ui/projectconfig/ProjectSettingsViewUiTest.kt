package app.luxion.shogunai.ui.projectconfig

import androidx.compose.ui.test.hasContentDescription
import app.luxion.shogunai.SimulatorSetup
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.luxion.shogunai.domain.FakeProjectRepository
import app.luxion.shogunai.domain.FakeRepositoryProvisioningSource
import app.luxion.shogunai.domain.FakeToolDetector
import app.luxion.shogunai.domain.FakeToolPreferenceRepository
import app.luxion.shogunai.domain.io.ProjectRepository
import app.luxion.shogunai.domain.io.RepositoryProvisioningSource
import app.luxion.shogunai.domain.model.DetectedTools
import app.luxion.shogunai.domain.model.EditorId
import app.luxion.shogunai.domain.model.EmulatorProvider
import app.luxion.shogunai.domain.model.LocalFileCandidate
import app.luxion.shogunai.domain.model.MobileSecret
import app.luxion.shogunai.domain.model.MobileSecretKind
import app.luxion.shogunai.domain.model.MobileSecretsReport
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.RepositoryProviders
import app.luxion.shogunai.domain.model.RepositoryProvisioning
import app.luxion.shogunai.domain.model.SimulatorRuntime
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.ui.i18n.EnglishStrings
import app.luxion.shogunai.ui.shell.LeaveGuard
import app.luxion.shogunai.ui.shell.ToolsPreferences
import kotlinx.coroutines.runBlocking
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ProjectSettingsViewUiTest {

    private val basePath = "/home/dev/projects/main-repo"
    private val otherPath = "/home/dev/projects/other-repo"
    private val strings = EnglishStrings
    private val project = Project("p", "Project", ProjectConfig(basePath, "/home/dev/projects", emptyList()))

    private class Callbacks {
        val saved = mutableListOf<Pair<Project, Boolean>>()
        var cancels = 0
        var discards = 0
        var canSave: Boolean? = null
        var attached: LeaveGuard? = null
        var detached: LeaveGuard? = null
    }

    private fun viewModel(
        project: Project? = this.project,
        source: RepositoryProvisioningSource = FakeRepositoryProvisioningSource(),
        projects: ProjectRepository = FakeProjectRepository(),
        inspect: suspend (String) -> Result<RepositoryInspection> = { Result.success(RepositoryInspection(it, "main")) },
        multirepoOf: (String) -> String? = { null },
        tools: ToolsPreferences = ToolsPreferences.NONE,
        loadAvds: suspend (String) -> Result<List<String>> = { Result.success(emptyList()) },
        simulatorSetup: UiTestSimulatorSetup? = null,
        detectMobileSecrets: (suspend (ProjectConfig) -> MobileSecretsReport)? = null,
        list: (suspend (ProjectConfig) -> List<LocalFileCandidate>)? = null,
    ) = ProjectConfigViewModel(
        projects,
        source,
        project,
        loadAvds = loadAvds,
        simulatorSetup = simulatorSetup ?: SimulatorSetup.NONE,
        inspectRepository = inspect,
        tools = tools,
        multirepoOfWorkspace = multirepoOf,
        detectMobileSecrets = detectMobileSecrets,
        listLocalFileCandidates = list,
    )

    private fun ComposeUiTest.show(
        viewModel: ProjectConfigViewModel,
        callbacks: Callbacks = Callbacks(),
        initialFolder: String? = null,
        pathPicker: AnsweringPathPicker = AnsweringPathPicker(null),
        width: Dp = 800.dp,
        density: Float? = null,
    ): Callbacks {
        setSettingsContent(width = width, pathPicker = pathPicker, density = density) {
            ProjectSettingsView(
                viewModel = viewModel,
                saveRequests = 0,
                initialFolder = initialFolder,
                onAttachLeaveGuard = { callbacks.attached = it },
                onDetachLeaveGuard = { callbacks.detached = it },
                onCanSaveChanged = { callbacks.canSave = it },
                onSaved = { project, written -> callbacks.saved += project to written },
                onCancel = { callbacks.cancels++ },
                onDiscard = { callbacks.discards++ },
            )
        }
        return callbacks
    }

    private fun ComposeUiTest.input(text: String): SemanticsNodeInteraction = onNode(hasSetTextAction() and hasText(text))

    private fun ComposeUiTest.save() = onAllNodesWithText(strings.save).onLast().performClick()

    @Test
    fun `the folder step rejects a folder that isn't a repository until the notice is dismissed`() = runDesktopComposeUiTest(1000, 900) {
        val viewModel = viewModel(project = null, inspect = { Result.failure(WorktreeError.RepositoryNotFound(it)) })
        val picker = AnsweringPathPicker("/home/dev/notes")
        val callbacks = show(viewModel, pathPicker = picker)

        onNodeWithText(strings.chooseRepositoryFolder).performClick()
        onNodeWithText(strings.notAGitRepository("/home/dev/notes")).assertIsDisplayed()
        onNodeWithContentDescription(strings.closeError).performClick()
        onNodeWithText(strings.notAGitRepository("/home/dev/notes")).assertDoesNotExist()
        picker.answer = null
        onNodeWithText(strings.chooseRepositoryFolder).performClick()
        onNodeWithText(strings.cancel).performClick()

        assertEquals(listOf("", ""), picker.requests)
        assertEquals(1, callbacks.cancels)
        assertSame(viewModel, callbacks.attached)
    }

    @Test
    fun `a dropped folder that belongs to a multirepo is rejected naming it`() = runDesktopComposeUiTest(1000, 900) {
        val viewModel = viewModel(project = null, multirepoOf = { "shop" })
        show(viewModel, initialFolder = basePath)

        onNodeWithText(strings.folderIsMultirepoWorkspace(basePath, "shop")).assertIsDisplayed()
        onNodeWithContentDescription(strings.closeError).performClick()

        assertNull(viewModel.rejectedMultirepo)
    }

    @Test
    fun `a chosen folder opens the form and adding the project saves it`() = runDesktopComposeUiTest(1000, 1400) {
        val viewModel = viewModel(project = null)
        val callbacks = show(viewModel, initialFolder = basePath)

        waitUntil { viewModel.folderChosen }
        onNodeWithText(strings.detectedMainBranch("main")).assertIsDisplayed()
        onAllNodesWithText(strings.addProject).onLast().performClick()

        waitUntil { callbacks.saved.isNotEmpty() }
        assertEquals(basePath, callbacks.saved.single().first.config.baseRepositoryPath)
        assertEquals(true, callbacks.canSave)
    }

    @Test
    fun `an edited project shows the save bar, saves and discards`() = runDesktopComposeUiTest(1000, 1400) {
        val viewModel = viewModel()
        val callbacks = show(viewModel)

        onNodeWithText(strings.unsavedChanges).assertDoesNotExist()
        input("Project").performTextReplacement("Renamed")
        onNodeWithText(strings.unsavedChanges).assertIsDisplayed()
        save()
        waitUntil { callbacks.saved.isNotEmpty() }
        input("Renamed").performTextReplacement("Again")
        onNodeWithText(strings.discard).performClick()

        assertEquals("Renamed", callbacks.saved.single().first.name)
        assertFalse(callbacks.saved.single().second)
        assertEquals(1, callbacks.discards)
    }

    @Test
    fun `saving an invalid form focuses the first invalid field`() = runDesktopComposeUiTest(1000, 1400) {
        val viewModel = viewModel()
        show(viewModel)

        input("Project").performTextReplacement("")
        save()
        onAllNodes(hasSetTextAction())[0].assertIsFocused()
        onNodeWithText(strings.nameEmptyError).assertIsDisplayed()

        viewModel.name = "Project"
        input(basePath).performTextReplacement("")
        save()
        onNodeWithText(strings.baseRepositoryPathEmptyError).assertIsDisplayed()

        viewModel.baseRepositoryPath = basePath
        input("/home/dev/projects").performTextReplacement("")
        save()
        onNodeWithText(strings.worktreesRootEmptyError).assertIsDisplayed()

        viewModel.worktreesRoot = "/home/dev/projects"
        input("claude").performTextReplacement("")
        save()
        onNodeWithText(strings.agentCommandEmptyError).assertExists()
    }

    @Test
    fun `an invalid repository option keeps the form open`() = runDesktopComposeUiTest(1000, 1400) {
        val viewModel = viewModel()
        viewModel.updateRepositoryDraft { it.copy(gradle = GradleDraft(daemonMaxHeap = "lots")) }
        val callbacks = show(viewModel)

        save()
        waitForIdle()

        assertTrue(callbacks.saved.isEmpty())
        onNodeWithText(strings.repositoryConfigSection).assertExists()
    }

    @Test
    fun `the first repository write is previewed and saved after confirming`() = runDesktopComposeUiTest(1000, 1400) {
        val viewModel = viewModel()
        viewModel.setFlutterEnabled(true)
        val callbacks = show(viewModel)

        save()
        onNodeWithText(strings.repositoryPreviewTitle).assertIsDisplayed()
        onNodeWithText(strings.cancel).performClick()
        onNodeWithText(strings.repositoryPreviewTitle).assertDoesNotExist()
        save()
        onNodeWithText(strings.repositoryPreviewConfirm).performClick()

        waitUntil { callbacks.saved.isNotEmpty() }
        assertTrue(callbacks.saved.single().second)
    }

    @Test
    fun `a preview confirmed after the file changed shows the error and an invalid form saves nothing`() = runDesktopComposeUiTest(1000, 1400) {
        val source = FakeRepositoryProvisioningSource()
        val viewModel = viewModel(source = source)
        viewModel.setFlutterEnabled(true)
        val callbacks = show(viewModel)

        save()
        source.currentFingerprint = "fingerprint-2"
        onNodeWithText(strings.repositoryPreviewConfirm).performClick()
        onNodeWithContentDescription(strings.closeError).performScrollTo().performClick()
        onNodeWithContentDescription(strings.closeError).assertDoesNotExist()

        viewModel.reloadRepositoryConfig()
        viewModel.setFlutterEnabled(true)
        save()
        onNodeWithText(strings.repositoryPreviewTitle).assertIsDisplayed()
        viewModel.name = ""
        onNodeWithText(strings.repositoryPreviewConfirm).performClick()
        waitForIdle()

        assertTrue(callbacks.saved.isEmpty())
        onNodeWithText(strings.repositoryPreviewTitle).assertDoesNotExist()
    }

    @Test
    fun `a project that can't be stored shows the error until it is dismissed`() = runDesktopComposeUiTest(1000, 1400) {
        val failing = object : ProjectRepository {
            override fun list(): List<Project> = emptyList()
            override fun save(project: Project) = throw IOException("disk full")
            override fun delete(id: String) = Unit
        }
        val viewModel = viewModel(projects = failing)
        show(viewModel)

        input("Project").performTextReplacement("Renamed")
        save()
        onNodeWithContentDescription(strings.closeError).performScrollTo().performClick()

        onNodeWithContentDescription(strings.closeError).assertDoesNotExist()
    }

    @Test
    fun `moving the base path with repository edits asks before discarding them`() = runDesktopComposeUiTest(1000, 1400) {
        val viewModel = viewModel()
        viewModel.setFlutterEnabled(true)
        show(viewModel)

        input(basePath).performTextReplacement(otherPath)
        mainClock.advanceTimeBy(1_000)
        waitUntil { viewModel.pendingRepositoryChange != null }
        onNodeWithText(strings.keepEditing).performClick()
        assertEquals(basePath, viewModel.baseRepositoryPath)

        input(basePath).performTextReplacement(otherPath)
        mainClock.advanceTimeBy(1_000)
        waitUntil { viewModel.pendingRepositoryChange != null }
        onNode(hasText(strings.discard) and hasAnyAncestor(isDialog())).performClick()

        assertNull(viewModel.pendingRepositoryChange)
        assertFalse(viewModel.repositoryDraft.flutter)
    }

    @Test
    fun `the base path field explains why a path can't be used`() = runDesktopComposeUiTest(1000, 1400) {
        val projects = FakeProjectRepository(listOf(Project("q", "Other", ProjectConfig(otherPath, "/home/dev/projects", emptyList()))))
        val viewModel = viewModel(
            projects = projects,
            inspect = { folder ->
                when {
                    folder.endsWith("notes") -> Result.failure(WorktreeError.RepositoryNotFound(folder))
                    else -> Result.success(RepositoryInspection("/home/dev/projects", null))
                }
            },
        )
        show(viewModel)

        input(basePath).performTextReplacement(otherPath)
        onNodeWithText(strings.baseRepositoryAlreadyAdded("Other")).assertIsDisplayed()
        input(otherPath).performTextReplacement("/home/dev/notes")
        mainClock.advanceTimeBy(1_000)
        waitUntil { viewModel.basePathProblem != null }
        onNodeWithText(strings.notAGitRepository("/home/dev/notes")).assertIsDisplayed()
        input("/home/dev/notes").performTextReplacement("/home/dev/projects/main-repo/app")
        mainClock.advanceTimeBy(1_000)
        waitUntil { viewModel.basePathProblem == BasePathProblem.NOT_TOP_LEVEL }
        onNodeWithText(strings.baseRepositoryNotTopLevelError).assertIsDisplayed()
    }

    @Test
    fun `leaving the base path field settles it`() = runDesktopComposeUiTest(1000, 1400) {
        var inspections = 0
        val viewModel = viewModel(inspect = { inspections++; Result.failure(WorktreeError.RepositoryNotFound(it)) })
        show(viewModel)

        input(basePath).performClick()
        input(basePath).performTextReplacement(otherPath)
        input("Project").performClick()
        waitUntil { inspections > 0 }

        assertEquals(BasePathProblem.NOT_A_REPOSITORY, viewModel.basePathProblem)
    }

    @Test
    fun `browsing picks the base path and the worktrees root`() = runDesktopComposeUiTest(1000, 1400) {
        val picker = AnsweringPathPicker(otherPath)
        val viewModel = viewModel()
        show(viewModel, pathPicker = picker)

        val browse = onAllNodesWithText(strings.browse)
        browse[0].performClick()
        waitUntil { viewModel.baseRepositoryPath == otherPath }
        picker.answer = "/home/dev/worktrees"
        browse[1].performClick()
        waitUntil { viewModel.worktreesRoot == "/home/dev/worktrees" }
        picker.answer = null
        browse[0].performClick()
        browse[1].performClick()
        waitForIdle()

        assertEquals(otherPath, viewModel.baseRepositoryPath)
        assertEquals("/home/dev/worktrees", viewModel.worktreesRoot)
        assertEquals(4, picker.requests.size)
    }

    @Test
    fun `the section index follows the scroll and jumps to a section`() = runDesktopComposeUiTest(1400, 900) {
        val source = FakeRepositoryProvisioningSource(
            byRepository = mapOf(basePath to RepositoryProvisioning(providers = RepositoryProviders(emulator = EmulatorProvider()))),
        )
        val viewModel = viewModel(
            source = source,
            simulatorSetup = UiTestSimulatorSetup(runtimes = { Result.success(emptyList<SimulatorRuntime>()) }, kotlinMultiplatform = false),
        )
        show(viewModel, width = 1300.dp)

        listOf(
            strings.generalSection, strings.worktreeFilesSection, strings.repositoryConfigSection,
            strings.agentSection, strings.emulatorSection, strings.simulatorSection,
        ).forEach { onAllNodesWithText(it).onFirst().assertIsDisplayed() }
        onAllNodesWithText(strings.simulatorSection).onFirst().performClick()
        waitForIdle()
        onAllNodesWithText(strings.agentSection).onFirst().performClick()
        waitForIdle()
        onAllNodesWithText(strings.generalSection).onFirst().performClick()
        waitForIdle()

        onAllNodesWithText(strings.generalSection).onFirst().assertIsDisplayed()
    }

    @Test
    fun `local files are added, rejected, removed and moved into the repository`() = runDesktopComposeUiTest(1000, 1600) {
        val viewModel = viewModel()
        show(viewModel)

        input(strings.newLocalFileLabel).performTextReplacement("local.properties")
        onNodeWithText(strings.add).performClick()
        input(strings.newLocalFileLabel).performTextReplacement("keys")
        input("keys").performKeyInput { pressKey(Key.Enter) }
        input(strings.newLocalFileLabel).performTextReplacement("/etc/*.pem")
        onNodeWithText(strings.add).performClick()
        onNodeWithText(strings.localPatternMustBeRelative).assertIsDisplayed()
        onAllNodes(hasContentDescription(strings.removeLocalFile)).onLast().performClick()
        assertEquals(listOf("local.properties"), viewModel.secretFiles)

        onNodeWithText(strings.moveToRepositoryConfig).performClick()

        assertEquals(listOf("/local.properties"), viewModel.pendingRepositoryPatterns)
        onNodeWithText("/local.properties").assertIsDisplayed()
    }

    @Test
    fun `the local file picker opens, retries, cancels and applies`() = runDesktopComposeUiTest(1000, 1600) {
        var fail = true
        val viewModel = viewModel(
            list = {
                if (fail) throw IOException("git failed")
                listOf(LocalFileCandidate(".env", isFolder = false))
            },
        )
        show(viewModel)

        onNodeWithText(strings.chooseLocalFiles).performClick()
        onNodeWithText(strings.localFilePickerTitle).assertIsDisplayed()
        waitUntil { viewModel.localFilePicker?.load is LocalFileCandidatesLoad.Failed }
        fail = false
        onNodeWithText(strings.tryAgain).performClick()
        waitUntil { viewModel.localFilePicker?.load is LocalFileCandidatesLoad.Loaded }
        onNodeWithText(strings.cancel).performClick()
        assertNull(viewModel.localFilePicker)

        onNodeWithText(strings.chooseLocalFiles).performClick()
        waitUntil { viewModel.localFilePicker?.load is LocalFileCandidatesLoad.Loaded }
        onNode(hasTestTag("local-file-.env")).performClick()
        onNodeWithText(strings.applyLocalFiles).performClick()

        assertEquals(listOf(".env"), viewModel.secretFiles)
    }

    @Test
    fun `an unreadable repository file lists no provisioning and offers no links`() = runDesktopComposeUiTest(1000, 1600) {
        val secret = MobileSecret("app/google-services.json", MobileSecretKind.FIREBASE_CONFIG)
        val viewModel = viewModel(
            source = FakeRepositoryProvisioningSource(unreadable = setOf(basePath)),
            detectMobileSecrets = { MobileSecretsReport(listOf(secret)) },
        )
        waitUntil { viewModel.mobileSecretSuggestions.isNotEmpty() }
        show(viewModel)

        onAllNodesWithText("malformed", substring = true).onFirst().assertIsDisplayed()
        onNodeWithText("app/google-services.json", substring = true).assertIsDisplayed()
        onNodeWithText(strings.linkFromRepositoryConfig).assertDoesNotExist()
    }

    @Test
    fun `an unreadable repository file without a detail lists only its path`() = runDesktopComposeUiTest(1000, 1600) {
        val source = object : RepositoryProvisioningSource by FakeRepositoryProvisioningSource() {
            override fun read(repositoryPath: String): RepositoryProvisioning =
                throw WorktreeError.InvalidRepositoryConfig("$repositoryPath/.shogunai.json", null)
        }
        show(viewModel(source = source))

        assertEquals(2, onAllNodesWithText(strings.repositoryConfigUnreadable("$basePath/.shogunai.json")).fetchSemanticsNodes().size)
    }

    @Test
    fun `declared patterns and commands are listed`() = runDesktopComposeUiTest(1000, 1600) {
        val provisioning = RepositoryProvisioning(copyPatterns = listOf("local.properties"), postCreateCommands = listOf(listOf("npm", "install")))
        show(viewModel(source = FakeRepositoryProvisioningSource(byRepository = mapOf(basePath to provisioning))))

        onNodeWithText("local.properties").assertIsDisplayed()
        onNodeWithText(strings.postCreationCommandsNote(1)).assertIsDisplayed()
        onNodeWithText(strings.repositoryProvisioningEmpty).assertDoesNotExist()
    }

    @Test
    fun `agents are added, renamed, reordered, made default and removed`() = runDesktopComposeUiTest(1000, 2000) {
        val viewModel = viewModel()
        val callbacks = show(viewModel)

        onNodeWithText(strings.addAgent).performScrollTo().performClick()
        viewModel.updateAgent(1) { it.copy(command = "codex") }
        onNodeWithText("Claude · ${strings.defaultAgentMark}").assertIsDisplayed()
        onAllNodes(isToggleable()).onLast().performScrollTo().performClick()
        onNodeWithText(strings.moveAgentDown).performScrollTo().performClick()
        assertEquals("codex", viewModel.agents.first().command)
        onNodeWithText(strings.moveAgentUp).performScrollTo().performClick()
        assertEquals("claude", viewModel.agents.first().command)
        onNodeWithText(strings.makeDefaultAgent).performScrollTo().performClick()
        assertEquals("codex", viewModel.agents.first().command)
        assertTrue(viewModel.agents.first().useHeadroom)

        input("Claude").performTextReplacement("Codex")
        assertEquals(2, onAllNodesWithText(strings.duplicateAgentNameError("Codex")).fetchSemanticsNodes().size)
        save()
        waitForIdle()
        assertTrue(callbacks.saved.isEmpty())
        onAllNodesWithText(strings.removeAgent).onLast().performScrollTo().performClick()

        assertEquals(1, viewModel.agents.size)
    }

    @Test
    fun `the project IDE lists the detected ones and warns about a missing one`() = runDesktopComposeUiTest(1000, 2000) {
        val tools = ToolsPreferences(FakeToolPreferenceRepository(), FakeToolDetector(DetectedTools(editors = listOf(EditorId.VS_CODE))))
        runBlocking { tools.refresh() }
        val viewModel = viewModel(
            project = project.copy(config = project.config.copy(editorOverride = EditorId.ANDROID_STUDIO)),
            tools = tools,
        )
        show(viewModel)

        onNodeWithText(strings.editorOverrideMissing(EditorId.ANDROID_STUDIO.displayName)).assertIsDisplayed()
        onNodeWithText(strings.editorNotFoundOption(EditorId.ANDROID_STUDIO.displayName)).performClick()
        onNodeWithText(strings.defaultEditorOption(EditorId.VS_CODE.displayName)).assertIsDisplayed()
        onNodeWithText(EditorId.VS_CODE.displayName).performClick()

        assertEquals(EditorId.VS_CODE, viewModel.editorOverride)
        onNodeWithText(strings.editorOverrideMissing(EditorId.ANDROID_STUDIO.displayName)).assertDoesNotExist()
    }

    @Test
    fun `a form that fits without scrolling marks the section at the top`() = runDesktopComposeUiTest(1400, 4000) {
        show(viewModel(), width = 1300.dp)

        onAllNodesWithText(strings.generalSection).onFirst().assertIsDisplayed()
        onAllNodesWithText(strings.agentSection).onFirst().performClick()
        waitForIdle()
    }

    @Test
    fun `on a dense screen the first section is marked before it reaches the top`() = runDesktopComposeUiTest(4000, 3000) {
        show(viewModel(), width = 1300.dp, density = 3f)

        onAllNodesWithText(strings.generalSection).onFirst().assertIsDisplayed()
    }

    @Test
    fun `the project IDE defaults to the system one even when no IDE is detected`() = runDesktopComposeUiTest(1000, 2000) {
        val tools = ToolsPreferences(FakeToolPreferenceRepository(), FakeToolDetector(DetectedTools()))
        runBlocking { tools.refresh() }
        show(viewModel(tools = tools))

        onAllNodesWithText(strings.projectEditorLabel).onLast().performScrollTo().performClick()
        onNodeWithText(strings.defaultEditorOption(null)).assertIsDisplayed()
    }

}
