package app.luxion.shogunai.ui.projectconfig

import app.luxion.shogunai.domain.model.DetectedStack
import app.luxion.shogunai.SimulatorSetup
import app.luxion.shogunai.domain.FakeProjectRepository
import app.luxion.shogunai.domain.FakeRepositoryProvisioningSource
import app.luxion.shogunai.domain.FakeRepositoryTrust
import app.luxion.shogunai.domain.io.ProjectRepository
import app.luxion.shogunai.domain.io.RepositoryProvisioningSource
import app.luxion.shogunai.domain.io.RepositoryTrust
import app.luxion.shogunai.domain.model.AgentLaunchConfig
import app.luxion.shogunai.domain.model.EditorId
import app.luxion.shogunai.domain.model.EmulatorProvider
import app.luxion.shogunai.domain.model.EnvironmentDeclaration
import app.luxion.shogunai.domain.model.LocalFileCandidate
import app.luxion.shogunai.domain.model.MobileSecretsReport
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.RepositoryApprovalSubject
import app.luxion.shogunai.domain.model.RepositoryConfigEdit
import app.luxion.shogunai.domain.model.RepositoryProviders
import app.luxion.shogunai.domain.model.RepositoryProvisioning
import app.luxion.shogunai.domain.model.SimulatorRuntime
import app.luxion.shogunai.domain.model.SuggestedProvider
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.model.approvalSubject
import app.luxion.shogunai.domain.model.parseEnvironmentVariable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.io.IOException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProjectConfigEdgeCasesTest {

    private val dispatcher = StandardTestDispatcher()
    private val basePath = "/home/dev/projects/main-repo"
    private val otherPath = "/home/dev/projects/other-repo"

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun project(
        path: String = basePath,
        secretFiles: List<String> = emptyList(),
        editorOverride: EditorId? = null,
    ) = Project(
        id = "p",
        name = "Project",
        config = ProjectConfig(
            baseRepositoryPath = path,
            worktreesRoot = "/home/dev/projects",
            secretFiles = secretFiles,
            editorOverride = editorOverride,
        ),
    )

    private class ScriptedSource(
        val delegate: FakeRepositoryProvisioningSource = FakeRepositoryProvisioningSource(),
    ) : RepositoryProvisioningSource by delegate {
        var failReads = false
        var failFingerprint = false
        var failPreview = false
        var failReadsAfterWrite = false

        override fun read(repositoryPath: String): RepositoryProvisioning {
            if (failReads) throw IOException("unreadable disk")
            return delegate.read(repositoryPath)
        }

        override fun fingerprint(repositoryPath: String): String {
            if (failFingerprint) throw IOException("unreadable disk")
            return delegate.fingerprint(repositoryPath)
        }

        override fun preview(repositoryPath: String, edit: RepositoryConfigEdit): String {
            if (failPreview) throw IOException("preview failed")
            return delegate.preview(repositoryPath, edit)
        }

        override fun write(repositoryPath: String, edit: RepositoryConfigEdit, expectedFingerprint: String) {
            delegate.write(repositoryPath, edit, expectedFingerprint)
            if (failReadsAfterWrite) failReads = true
        }
    }

    private class FailingTrust : RepositoryTrust {
        override fun isApproved(repository: String, subject: RepositoryApprovalSubject): Boolean = throw IOException("no trust store")
        override fun approve(repository: String, subject: RepositoryApprovalSubject) = Unit
        override fun revoke(repository: String) = Unit
    }

    private class TestSimulatorSetup(
        private val runtimes: Result<List<SimulatorRuntime>> = Result.success(emptyList()),
        private val install: Result<Unit> = Result.success(Unit),
        private val uninstall: Result<Unit> = Result.success(Unit),
    ) : SimulatorSetup {
        override fun isAvailable(repositoryPath: String) = true
        override suspend fun runtimes() = runtimes
        override fun isWired(repositoryPath: String) = false
        override fun usesKotlinMultiplatform(repositoryPath: String) = true
        override fun installInitScript() = install
        override fun isInitScriptInstalled() = false
        override fun uninstallInitScript() = uninstall
        override val snippet = "tasks.withType<KotlinNativeSimulatorTest>()"
        override val initScriptPath = "/home/dev/.gradle/init.d/shogunai.init.gradle.kts"
    }

    private val environmentProvisioning = RepositoryProvisioning(
        environment = EnvironmentDeclaration(listOf(parseEnvironmentVariable("APP_PORT", "\${port}"))),
    )

    private fun viewModel(
        project: Project? = project(),
        source: RepositoryProvisioningSource = FakeRepositoryProvisioningSource(),
        projects: ProjectRepository = FakeProjectRepository(),
        trust: RepositoryTrust = FakeRepositoryTrust(),
        simulatorSetup: SimulatorSetup = SimulatorSetup.NONE,
        loadAvds: suspend (String) -> Result<List<String>> = { Result.success(emptyList()) },
        inspect: (suspend (String) -> Result<RepositoryInspection>)? = null,
        multirepoOf: (String) -> String? = { null },
        detectStacks: (String) -> List<DetectedStack> = { emptyList() },
        detectMobileSecrets: (suspend (ProjectConfig) -> MobileSecretsReport)? = null,
        list: (suspend (ProjectConfig) -> List<LocalFileCandidate>)? = null,
    ) = if (inspect == null) {
        ProjectConfigViewModel(
            projects, source, project,
            loadAvds = loadAvds, simulatorSetup = simulatorSetup, trust = trust,
            multirepoOfWorkspace = multirepoOf, detectStacks = detectStacks,
            detectMobileSecrets = detectMobileSecrets, listLocalFileCandidates = list,
        )
    } else {
        ProjectConfigViewModel(
            projects, source, project,
            loadAvds = loadAvds, simulatorSetup = simulatorSetup, inspectRepository = inspect, trust = trust,
            multirepoOfWorkspace = multirepoOf, detectStacks = detectStacks,
            detectMobileSecrets = detectMobileSecrets, listLocalFileCandidates = list,
        )
    }

    private fun inspectingAsRepository(folder: String) = Result.success(RepositoryInspection(folder, "main"))

    @Test
    fun `a new project starts empty with one default agent`() {
        val viewModel = viewModel(project = null)

        assertEquals("", viewModel.name)
        assertEquals("", viewModel.baseRepositoryPath)
        assertEquals("", viewModel.worktreesRoot)
        assertEquals(listOf(AgentDraft()), viewModel.agents)
        assertNull(viewModel.emulatorAvd)
        assertFalse(viewModel.emulatorNoWindow)
        assertNull(viewModel.simulatorDevice)
        assertNull(viewModel.simulatorRuntime)
        assertFalse(viewModel.folderChosen)
    }

    @Test
    fun `a folder that is not a repository is rejected when nothing can inspect it`() = runTest(dispatcher) {
        val viewModel = viewModel(project = null)

        assertTrue(viewModel.chooseFolder("/home/dev/notes").isFailure)
        assertEquals("/home/dev/notes", viewModel.rejectedFolder)

        viewModel.dismissRejectedFolder()
        assertNull(viewModel.rejectedFolder)
    }

    @Test
    fun `a repository that belongs to a multirepo is rejected naming the multirepo`() = runTest(dispatcher) {
        val viewModel = viewModel(project = null, inspect = ::inspectingAsRepository, multirepoOf = { "shop" })

        assertTrue(viewModel.chooseFolder(basePath).isFailure)
        assertEquals(basePath to "shop", viewModel.rejectedMultirepo)
        assertNull(viewModel.rejectedFolder)
        assertFalse(viewModel.folderChosen)

        viewModel.dismissRejectedMultirepo()
        assertNull(viewModel.rejectedMultirepo)
    }

    @Test
    fun `the project list failing to load leaves no duplicate to warn about`() {
        val failing = object : ProjectRepository {
            override fun list(): List<Project> = throw IOException("unreadable projects file")
            override fun save(project: Project) = Unit
            override fun delete(id: String) = Unit
        }

        assertNull(viewModel(projects = failing).duplicateProject)
    }

    @Test
    fun `an IDE override is missing only when it is set and not installed`() {
        assertNull(viewModel().missingEditorOverride)
        assertNotNull(viewModel(project = project(editorOverride = EditorId.entries.first())).missingEditorOverride)
    }

    @Test
    fun `a repository file that fails to read or fingerprint leaves an empty draft that can still be edited`() {
        val source = ScriptedSource().apply {
            failReads = true
            failFingerprint = true
        }

        val viewModel = viewModel(source = source)

        assertEquals(RepositoryConfigDraft(), viewModel.repositoryDraft)
        assertTrue(viewModel.repositoryConfigEditable)
        assertEquals(EmulatorSectionState.Hidden, viewModel.emulatorSection)
    }

    @Test
    fun `stack detection and trust failures read as no stacks and not approved`() {
        val source = FakeRepositoryProvisioningSource(byRepository = mapOf(basePath to environmentProvisioning))

        val viewModel = viewModel(source = source, trust = FailingTrust(), detectStacks = { throw IOException("unreadable") })

        assertTrue(viewModel.stackSuggestions.isEmpty())
        assertFalse(viewModel.repositoryApproved)
    }

    @Test
    fun `approving does nothing without anything to approve and revoking needs a repository`() {
        val trust = FakeRepositoryTrust()
        val viewModel = viewModel(trust = trust)

        viewModel.approveRepository()
        assertTrue(trust.approvals.isEmpty())

        val newProject = viewModel(project = null, trust = trust)
        newProject.revokeRepositoryApproval()
        assertFalse(newProject.repositoryApproved)
    }

    @Test
    fun `revoking an approved repository asks to approve it again`() {
        val source = FakeRepositoryProvisioningSource(byRepository = mapOf(basePath to environmentProvisioning))
        val trust = FakeRepositoryTrust()
        val viewModel = viewModel(source = source, trust = trust)

        viewModel.approveRepository()
        assertTrue(viewModel.repositoryApproved)
        viewModel.revokeRepositoryApproval()

        assertFalse(viewModel.repositoryApproved)
        assertTrue(trust.approvals.isEmpty())
    }

    @Test
    fun `accepting each suggestion turns its provider on`() {
        val viewModel = viewModel()

        SuggestedProvider.entries.forEach(viewModel::acceptSuggestion)

        val draft = viewModel.repositoryDraft
        assertEquals(GradleDraft(), draft.gradle)
        assertEquals(EmulatorDraft(), draft.emulator)
        assertEquals(IosSimulatorDraft(), draft.iosSimulator)
        assertEquals(XcodeDraft(), draft.xcode)
        assertEquals(MavenDraft(), draft.maven)
        assertEquals(ComposeDraft(), draft.dockerCompose)
        assertTrue(draft.flutter)
    }

    @Test
    fun `turning on a provider that is already on keeps its options and turning it off removes it`() {
        val viewModel = viewModel()
        viewModel.updateRepositoryDraft {
            it.copy(
                gradle = GradleDraft(daemonMaxHeap = "2g"),
                emulator = EmulatorDraft("Pixel_8"),
                iosSimulator = IosSimulatorDraft("iPhone 16"),
                xcode = XcodeDraft(compilationCache = true),
                maven = MavenDraft("1g"),
                dockerCompose = ComposeDraft("DB_PORT"),
            )
        }
        val configured = viewModel.repositoryDraft

        viewModel.setGradleEnabled(true)
        viewModel.setEmulatorEnabled(true)
        viewModel.setIosSimulatorEnabled(true)
        viewModel.setXcodeEnabled(true)
        viewModel.setMavenEnabled(true)
        viewModel.setDockerComposeEnabled(true)
        assertEquals(configured, viewModel.repositoryDraft)

        viewModel.setGradleEnabled(false)
        viewModel.setEmulatorEnabled(false)
        viewModel.setIosSimulatorEnabled(false)
        viewModel.setXcodeEnabled(false)
        viewModel.setMavenEnabled(false)
        viewModel.setDockerComposeEnabled(false)
        viewModel.setFlutterEnabled(false)
        assertEquals(RepositoryConfigDraft(), viewModel.repositoryDraft)
    }

    @Test
    fun `a project without a repository suggests no providers and ignores draft edits`() {
        val viewModel = viewModel(project = null, detectStacks = { error("not called") })

        viewModel.setGradleEnabled(true)

        assertTrue(viewModel.stackSuggestions.isEmpty())
        assertNull(viewModel.repositoryDraft.gradle)
    }

    @Test
    fun `environment rows are edited one field at a time and removed by id`() {
        val viewModel = viewModel()
        viewModel.addEnvironmentRow()
        viewModel.addEnvironmentRow()
        val (first, second) = viewModel.repositoryDraft.environment

        viewModel.updateEnvironmentRow(first.id, name = "APP_PORT")
        viewModel.updateEnvironmentRow(first.id, template = "\${port}")
        viewModel.removeEnvironmentRow(second.id)

        assertEquals(listOf(EnvironmentRow(first.id, "APP_PORT", "\${port}")), viewModel.repositoryDraft.environment)
    }

    @Test
    fun `settling a blank base path clears the inspection`() = runTest(dispatcher) {
        var inspections = 0
        val viewModel = viewModel(inspect = { inspections++; inspectingAsRepository(it) })

        viewModel.baseRepositoryPath = ""
        viewModel.settleBaseRepositoryPath()

        assertEquals(0, inspections)
        assertNull(viewModel.basePathProblem)
        assertNull(viewModel.pendingRepositoryChange)
    }

    @Test
    fun `a path that is not a repository is inspected once until it changes`() = runTest(dispatcher) {
        var inspections = 0
        val viewModel = viewModel(inspect = { inspections++; Result.failure(WorktreeError.RepositoryNotFound(it)) })

        viewModel.baseRepositoryPath = otherPath
        viewModel.settleBaseRepositoryPath()
        viewModel.settleBaseRepositoryPath()

        assertEquals(1, inspections)
        assertEquals(BasePathProblem.NOT_A_REPOSITORY, viewModel.basePathProblem)
    }

    @Test
    fun `a pending repository change is inspected once and inspected again after cancelling it`() = runTest(dispatcher) {
        var inspections = 0
        val viewModel = viewModel(inspect = { inspections++; inspectingAsRepository(it) })
        viewModel.addEnvironmentRow()

        viewModel.baseRepositoryPath = otherPath
        viewModel.settleBaseRepositoryPath()
        viewModel.settleBaseRepositoryPath()
        assertEquals(1, inspections)
        assertEquals(otherPath, viewModel.pendingRepositoryChange)

        viewModel.cancelRepositoryChange()
        assertEquals(basePath, viewModel.baseRepositoryPath)
        viewModel.baseRepositoryPath = otherPath
        viewModel.settleBaseRepositoryPath()

        assertEquals(2, inspections)
    }

    @Test
    fun `an inspection whose path changed meanwhile is discarded`() = runTest(dispatcher) {
        lateinit var viewModel: ProjectConfigViewModel
        viewModel = viewModel(
            inspect = { folder ->
                viewModel.baseRepositoryPath = "/home/dev/projects/typed-further"
                Result.failure(WorktreeError.RepositoryNotFound(folder))
            },
        )

        viewModel.baseRepositoryPath = otherPath
        viewModel.settleBaseRepositoryPath()

        assertNull(viewModel.basePathProblem)
    }

    @Test
    fun `confirming without a pending repository change keeps the loaded repository`() {
        val viewModel = viewModel()

        viewModel.confirmRepositoryChange()

        assertEquals(basePath, viewModel.baseRepositoryPath)
        assertNull(viewModel.pendingRepositoryChange)
    }

    @Test
    fun `saving with repository edits for a base path not loaded yet points to the base path`() {
        val viewModel = viewModel()
        viewModel.addEnvironmentRow()
        viewModel.updateEnvironmentRow(viewModel.repositoryDraft.environment.single().id, name = "APP_PORT", template = "\${port}")

        viewModel.baseRepositoryPath = otherPath

        assertEquals(SaveOutcome.Invalid(ProjectConfigField.BASE_REPOSITORY_PATH), viewModel.submit())
    }

    @Test
    fun `a preview that fails is reported as a failed save`() {
        val source = ScriptedSource().apply { failPreview = true }
        val viewModel = viewModel(source = source)
        viewModel.setFlutterEnabled(true)

        assertIs<SaveOutcome.Failed>(viewModel.submit())
    }

    @Test
    fun `an unreadable repository file before writing doesn't approve what is written`() {
        val source = ScriptedSource().apply { failReads = true }
        val trust = FakeRepositoryTrust()
        val viewModel = viewModel(source = source, trust = trust)
        viewModel.setFlutterEnabled(true)

        assertIs<SaveOutcome.Saved>(viewModel.submit(previewConfirmed = true))
        assertTrue(trust.approvals.isEmpty())
    }

    @Test
    fun `an unreadable repository file after writing approves nothing`() {
        val delegate = FakeRepositoryProvisioningSource(byRepository = mapOf(basePath to environmentProvisioning))
        val source = ScriptedSource(delegate).apply { failReadsAfterWrite = true }
        val trust = FakeRepositoryTrust()
        val viewModel = viewModel(source = source, trust = trust)
        viewModel.setFlutterEnabled(true)

        assertIs<SaveOutcome.Saved>(viewModel.submit(previewConfirmed = true))
        assertTrue(trust.approvals.isEmpty())
    }

    @Test
    fun `removing every variable of an approved environment leaves nothing to approve`() {
        val source = FakeRepositoryProvisioningSource(byRepository = mapOf(basePath to environmentProvisioning))
        val trust = FakeRepositoryTrust()
        val viewModel = viewModel(source = source, trust = trust)

        viewModel.removeEnvironmentRow(viewModel.repositoryDraft.environment.single().id)

        assertIs<SaveOutcome.Saved>(viewModel.submit(previewConfirmed = true))
        assertTrue(trust.approvals.isEmpty())
    }

    @Test
    fun `commands keep their approval after a save only when they were approved`() {
        val withCommands = environmentProvisioning.copy(postCreateCommands = listOf(listOf("npm", "install")))
        val approved = FakeRepositoryTrust(mapOf(basePath to withCommands.approvalSubject))
        val notApproved = FakeRepositoryTrust()

        listOf(approved, notApproved).forEach { trust ->
            val source = FakeRepositoryProvisioningSource(byRepository = mapOf(basePath to withCommands))
            val viewModel = viewModel(source = source, trust = trust)
            viewModel.setFlutterEnabled(true)
            assertIs<SaveOutcome.Saved>(viewModel.submit(previewConfirmed = true))
        }

        assertEquals(setOf(basePath), approved.approvals.keys)
        assertTrue(notApproved.approvals.isEmpty())
    }

    @Test
    fun `agents move only between positions that exist`() {
        val viewModel = viewModel()
        viewModel.addAgent()
        val agents = viewModel.agents

        viewModel.moveAgent(-1, 0)
        viewModel.moveAgent(5, 0)
        viewModel.moveAgent(0, -1)
        viewModel.moveAgent(0, 5)

        assertEquals(agents, viewModel.agents)
    }

    @Test
    fun `each saved field marks the form as changed on its own`() {
        val edits: List<(ProjectConfigViewModel) -> Unit> = listOf(
            { it.baseRepositoryPath = otherPath },
            { it.worktreesRoot = "/home/dev/worktrees" },
            { it.addAgent() },
            { it.emulatorNoWindow = true },
            {
                it.moveLocalFilesToRepository()
                it.addSecretFile("keys")
            },
        )

        edits.forEach { edit ->
            val viewModel = viewModel(project = project(secretFiles = listOf("keys")))
            edit(viewModel)
            assertTrue(viewModel.hasUnsavedChanges)
        }
    }

    @Test
    fun `local files move to the repository only when there is a repository`() {
        val viewModel = viewModel(project = project(secretFiles = listOf("local.properties")))

        viewModel.baseRepositoryPath = ""

        assertFalse(viewModel.canMoveLocalFilesToRepository)
    }

    @Test
    fun `blank and repeated local files are not added`() {
        val viewModel = viewModel(project = project(secretFiles = listOf("local.properties")))

        viewModel.addSecretFile("  ")
        viewModel.addSecretFile("local.properties")

        assertEquals(listOf("local.properties"), viewModel.secretFiles)
    }

    @Test
    fun `only declared entries can be linked and a linked entry can go back to being copied`() {
        val viewModel = viewModel(project = project(secretFiles = listOf("keys")))

        viewModel.setEntryLinked("unknown", linked = true)
        viewModel.setEntryLinked("keys", linked = true)
        assertEquals(setOf("keys"), viewModel.linkedSecretFiles)

        viewModel.setEntryLinked("keys", linked = false)
        assertTrue(viewModel.linkedSecretFiles.isEmpty())
    }

    @Test
    fun `the local file picker doesn't open without a lister or a repository and its actions need it open`() {
        val withoutLister = viewModel()
        withoutLister.openLocalFilePicker()
        assertNull(withoutLister.localFilePicker)

        val withoutRepository = viewModel(project = null, list = { emptyList() })
        withoutRepository.openLocalFilePicker()
        withoutRepository.retryLocalFileCandidates()
        withoutRepository.applyLocalFilePicker()
        assertNull(withoutRepository.localFilePicker)
        assertTrue(withoutRepository.secretFiles.isEmpty())
    }

    @Test
    fun `mobile secrets that fail to load read as none`() = runTest(dispatcher) {
        val viewModel = viewModel(detectMobileSecrets = { throw IOException("unreadable") })
        advanceUntilIdle()

        assertEquals(MobileSecretsReport(), viewModel.mobileSecrets)
    }

    @Test
    fun `an emulator section that fails to list its AVDs shows the failure`() = runTest(dispatcher) {
        val source = FakeRepositoryProvisioningSource(
            byRepository = mapOf(basePath to RepositoryProvisioning(providers = RepositoryProviders(emulator = EmulatorProvider()))),
        )
        val viewModel = viewModel(source = source, loadAvds = { Result.failure(IOException("adb failed")) })
        advanceUntilIdle()

        assertIs<EmulatorSectionState.Failed>(viewModel.emulatorSection)
    }

    @Test
    fun `a repository that declares local files and the emulator lists its AVDs`() = runTest(dispatcher) {
        val provisioning = RepositoryProvisioning(copyPatterns = listOf(".env"), providers = RepositoryProviders(emulator = EmulatorProvider()))
        val source = FakeRepositoryProvisioningSource(byRepository = mapOf(basePath to provisioning))
        val viewModel = viewModel(source = source, loadAvds = { Result.success(listOf("Pixel_8")) })
        advanceUntilIdle()

        assertEquals(EmulatorSectionState.Available(listOf("Pixel_8")), viewModel.emulatorSection)
    }

    @Test
    fun `a declared repository without the emulator hides the emulator section`() {
        val source = FakeRepositoryProvisioningSource(
            byRepository = mapOf(basePath to RepositoryProvisioning(copyPatterns = listOf(".env"))),
        )

        assertEquals(EmulatorSectionState.Hidden, viewModel(source = source).emulatorSection)
    }

    @Test
    fun `a simulator section whose runtimes fail to load shows the failure`() = runTest(dispatcher) {
        val viewModel = viewModel(simulatorSetup = TestSimulatorSetup(runtimes = Result.failure(IOException("simctl failed"))))
        advanceUntilIdle()

        assertIs<SimulatorSectionState.Failed>(viewModel.simulatorSection)
        assertEquals("tasks.withType<KotlinNativeSimulatorTest>()", viewModel.simulatorSnippet)
        assertEquals("/home/dev/.gradle/init.d/shogunai.init.gradle.kts", viewModel.simulatorInitScriptPath)
    }

    @Test
    fun `the init script result is returned and the section only changes once it has loaded`() = runTest(dispatcher) {
        val failure = IOException("read-only home")
        val failing = viewModel(simulatorSetup = TestSimulatorSetup(install = Result.failure(failure), uninstall = Result.failure(failure)))
        advanceUntilIdle()
        assertEquals(failure, failing.installSimulatorInitScript().exceptionOrNull())
        assertEquals(failure, failing.uninstallSimulatorInitScript().exceptionOrNull())

        val loading = viewModel(simulatorSetup = TestSimulatorSetup())
        assertTrue(loading.installSimulatorInitScript().isSuccess)
        assertTrue(loading.uninstallSimulatorInitScript().isSuccess)
        assertEquals(SimulatorSectionState.Loading, loading.simulatorSection)
    }

    @Test
    fun `loading another repository restarts the simulator section`() = runTest(dispatcher) {
        val viewModel = viewModel(simulatorSetup = TestSimulatorSetup(), inspect = ::inspectingAsRepository)

        viewModel.baseRepositoryPath = otherPath
        viewModel.settleBaseRepositoryPath()
        advanceUntilIdle()

        assertIs<SimulatorSectionState.Available>(viewModel.simulatorSection)
    }

    @Test
    fun `a blank AVD is saved as automatic`() {
        val viewModel = viewModel()
        viewModel.emulatorAvd = "  "

        assertNull(viewModel.save().getOrThrow().config.emulator.avd)
    }

    @Test
    fun `agents saved by a project keep their names and commands`() {
        val saved = project().let {
            it.copy(config = it.config.copy(additionalAgents = listOf(AgentLaunchConfig(name = "Review", agentCommand = "codex"))))
        }

        val viewModel = viewModel(project = saved)

        assertEquals(listOf(AgentDraft(), AgentDraft("Review", "codex")), viewModel.agents)
    }
}
