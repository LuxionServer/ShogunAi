package app.luxion.shogunai.ui.projectconfig

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import app.luxion.shogunai.domain.FakeProjectRepository
import app.luxion.shogunai.domain.FakeRepositoryProvisioningSource
import app.luxion.shogunai.domain.FakeRepositoryTrust
import app.luxion.shogunai.domain.io.RepositoryProvisioningSource
import app.luxion.shogunai.domain.model.DetectedStack
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.RepositoryProvisioning
import app.luxion.shogunai.domain.model.Stack
import app.luxion.shogunai.domain.model.SuggestedProvider
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.ui.i18n.EnglishStrings
import app.luxion.shogunai.ui.i18n.repositoryConfigIssue
import app.luxion.shogunai.domain.model.RepositoryConfigIssue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class RepositoryConfigSectionUiTest {

    private val basePath = "/home/dev/projects/main-repo"
    private val strings = EnglishStrings
    private val project = Project("p", "Project", ProjectConfig(basePath, "/home/dev/projects", emptyList()))

    private fun viewModel(
        source: RepositoryProvisioningSource = FakeRepositoryProvisioningSource(),
        project: Project? = this.project,
        supportsApplePlatforms: Boolean = true,
        trust: FakeRepositoryTrust = FakeRepositoryTrust(),
        detectStacks: (String) -> List<DetectedStack> = { emptyList() },
    ) = ProjectConfigViewModel(
        FakeProjectRepository(),
        source,
        project,
        supportsApplePlatforms = supportsApplePlatforms,
        trust = trust,
        detectStacks = detectStacks,
    )

    private fun ComposeUiTest.field(label: String): SemanticsNodeInteraction =
        onNode(hasSetTextAction() and hasContentDescription(label))

    @Test
    fun `an unreadable repository file shows its detail and can be reloaded`() = runDesktopComposeUiTest(width = 900, height = 2400) {
        val viewModel = viewModel(source = FakeRepositoryProvisioningSource(unreadable = setOf(basePath)))
        setSettingsContent { RepositoryConfigSection(viewModel) }

        onNodeWithText("malformed", substring = true).assertIsDisplayed()
        onNodeWithText(strings.repositoryConfigUnreadableExplanation).assertIsDisplayed()
        onNodeWithText(strings.reloadRepositoryConfig).performClick()

        assertIs<RepositoryProvisioningState.Unreadable>(viewModel.repositoryProvisioning)
    }

    @Test
    fun `an unreadable repository file without a detail shows only its path`() = runDesktopComposeUiTest(width = 900, height = 2400) {
        val source = object : RepositoryProvisioningSource by FakeRepositoryProvisioningSource() {
            override fun read(repositoryPath: String): RepositoryProvisioning =
                throw WorktreeError.InvalidRepositoryConfig("$repositoryPath/.shogunai.json", null)
        }
        val viewModel = viewModel(source = source)
        setSettingsContent { RepositoryConfigSection(viewModel) }

        onNodeWithText(strings.repositoryConfigUnreadable("$basePath/.shogunai.json")).assertIsDisplayed()
    }

    @Test
    fun `without a repository only the shared note is shown`() = runDesktopComposeUiTest(width = 900, height = 2400) {
        val viewModel = viewModel(project = null)
        setSettingsContent { RepositoryConfigSection(viewModel) }

        onNodeWithText(strings.repositoryConfigSharedNote).assertIsDisplayed()
        onNodeWithText(strings.repositoryConfigProvidersTitle).assertDoesNotExist()
    }

    @Test
    fun `a repository file changed on disk offers to reload it`() = runDesktopComposeUiTest(width = 900, height = 2400) {
        val source = FakeRepositoryProvisioningSource()
        val viewModel = viewModel(source = source)
        viewModel.setFlutterEnabled(true)
        source.currentFingerprint = "fingerprint-2"
        assertIs<SaveOutcome.Failed>(viewModel.submit(previewConfirmed = true))
        setSettingsContent { RepositoryConfigSection(viewModel) }

        onNodeWithText(strings.repositoryConfigChanged(RepositoryProvisioning.CONFIG_FILE_NAME)).assertIsDisplayed()
        onNodeWithText(strings.reloadRepositoryConfig).performClick()

        assertFalse(viewModel.repositoryChangedOnDisk)
        onNodeWithText(strings.repositoryConfigChanged(RepositoryProvisioning.CONFIG_FILE_NAME)).assertDoesNotExist()
    }

    @Test
    fun `every provider switch turns its provider on and its options are edited in place`() = runDesktopComposeUiTest(width = 900, height = 2400) {
        val viewModel = viewModel()
        setSettingsContent { RepositoryConfigSection(viewModel) }

        (7 downTo 0).forEach { onAllNodes(isToggleable())[it].performClick() }
        onAllNodes(isToggleable())[8].performClick()
        onAllNodes(isToggleable())[9].performClick()
        field(strings.gradleDaemonMaxHeapLabel).performTextReplacement("lots")
        field(strings.gradleIdleTimeoutLabel).performTextReplacement("soon")
        field(strings.gradleKotlinDaemonMaxHeapLabel).performTextReplacement("1g")
        field(strings.preferredAvdLabel).performTextReplacement("Pixel_8")
        field(strings.mavenMaxHeapLabel).performTextReplacement("2g")
        field(strings.composePortVariablesLabel).performTextReplacement("DB_PORT, DB_PORT")
        field(strings.preferredDeviceLabel).performTextReplacement("iPhone 16")
        field(strings.preferredRuntimeLabel).performTextReplacement("iOS 18.5")

        val draft = viewModel.repositoryDraft
        assertEquals(GradleDraft("lots", "soon", "1g"), draft.gradle)
        assertEquals(EmulatorDraft("Pixel_8"), draft.emulator)
        assertEquals(MavenDraft("2g"), draft.maven)
        assertEquals(ComposeDraft("DB_PORT, DB_PORT"), draft.dockerCompose)
        assertTrue(draft.flutter)
        assertTrue(draft.node)
        assertEquals(IosSimulatorDraft("iPhone 16", "iOS 18.5"), draft.iosSimulator)
        assertEquals(XcodeDraft(compilationCache = true, cocoaPods = true), draft.xcode)
        onNodeWithText(strings.repositoryConfigIssue(RepositoryConfigIssue.InvalidMemorySize)).assertIsDisplayed()
        onNodeWithText(strings.repositoryConfigIssue(RepositoryConfigIssue.InvalidMinutes)).assertIsDisplayed()
        onNodeWithText(strings.repositoryConfigIssue(RepositoryConfigIssue.DuplicateVariableName)).assertIsDisplayed()
    }

    @Test
    fun `Apple platform providers are hidden where they can't run`() = runDesktopComposeUiTest(width = 900, height = 2400) {
        val viewModel = viewModel(supportsApplePlatforms = false)
        setSettingsContent { RepositoryConfigSection(viewModel) }

        onNodeWithText(strings.providerFlutter).assertIsDisplayed()
        onNodeWithText(strings.providerIosSimulator).assertDoesNotExist()
        onNodeWithText(strings.providerXcode).assertDoesNotExist()
    }

    @Test
    fun `a detected stack suggests its provider and turns it on`() = runDesktopComposeUiTest(width = 900, height = 2400) {
        val viewModel = viewModel(detectStacks = { listOf(DetectedStack(Stack.MAVEN, listOf("pom.xml"))) })
        setSettingsContent { RepositoryConfigSection(viewModel) }

        onNodeWithText(strings.stackSuggestionsTitle).assertIsDisplayed()
        onNodeWithText(strings.stackSuggestion(strings.providerMaven, "pom.xml")).assertIsDisplayed()
        onNodeWithText(strings.turnOnProvider).performClick()

        assertEquals(MavenDraft(), viewModel.repositoryDraft.maven)
        onNodeWithText(strings.stackSuggestionsTitle).assertDoesNotExist()
    }

    @Test
    fun `every suggested provider has the label of its switch`() {
        assertEquals(
            listOf(
                strings.providerGradle, strings.providerEmulator, strings.providerIosSimulator, strings.providerXcode,
                strings.providerMaven, strings.providerDockerCompose, strings.providerFlutter, strings.providerNode,
            ),
            SuggestedProvider.entries.map { it.label(strings) },
        )
    }

    @Test
    fun `environment variables are added, edited, checked and removed`() = runDesktopComposeUiTest(width = 900, height = 2400) {
        val viewModel = viewModel()
        setSettingsContent { RepositoryConfigSection(viewModel) }

        onNodeWithText(strings.environmentEmpty).assertIsDisplayed()
        onNodeWithText(strings.addVariable).performClick()
        field(strings.environmentVariableNameLabel).performTextReplacement("1PORT")
        field(strings.environmentVariableTemplateLabel).performTextReplacement("\${unknown}")
        field(strings.envFileLabel).performTextReplacement("../outside.env")
        onNodeWithText(strings.templateHelpTitle).performClick()
        onNodeWithText(strings.templateHelp.first().template).assertIsDisplayed()

        onNodeWithText(strings.repositoryConfigIssue(RepositoryConfigIssue.InvalidVariableName)).assertIsDisplayed()
        onNodeWithText(strings.repositoryConfigIssue(RepositoryConfigIssue.EnvFileOutsideWorktree)).assertIsDisplayed()
        assertEquals("../outside.env", viewModel.repositoryDraft.envFile)

        field(strings.environmentVariableNameLabel).performTextReplacement("APP_URL")
        val row = viewModel.repositoryDraft.environment.single()
        val templateIssue = viewModel.repositoryDraft.issueFor(RepositoryConfigField.EnvironmentTemplate(row.id))
        onNodeWithText(strings.repositoryConfigIssue(templateIssue!!)).assertIsDisplayed()
        onNodeWithContentDescription(strings.removeVariable).performClick()

        assertTrue(viewModel.repositoryDraft.environment.isEmpty())
    }

    @Test
    fun `commands are approved and the approval revoked from the section`() = runDesktopComposeUiTest(width = 900, height = 2400) {
        val source = FakeRepositoryProvisioningSource(
            byRepository = mapOf(basePath to RepositoryProvisioning(postCreateCommands = listOf(listOf("npm", "install")))),
        )
        val viewModel = viewModel(source = source)
        setSettingsContent { RepositoryConfigSection(viewModel) }

        onNodeWithText(strings.postCreateNotApproved).assertIsDisplayed()
        onNodeWithText(strings.approveCommands).performClick()
        onNodeWithText(strings.postCreateApproved).assertIsDisplayed()
        assertTrue(viewModel.repositoryApproved)
        onNodeWithText(strings.revokeApproval).performClick()

        assertFalse(viewModel.repositoryApproved)
    }
}
