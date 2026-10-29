package app.luxion.shogunai.ui.projectconfig

import app.luxion.shogunai.domain.FakeProjectRepository
import app.luxion.shogunai.domain.FakeRepositoryProvisioningSource
import app.luxion.shogunai.domain.model.DetectedStack
import app.luxion.shogunai.domain.model.DockerComposeProvider
import app.luxion.shogunai.domain.model.FlutterProvider
import app.luxion.shogunai.domain.model.GradleProvider
import app.luxion.shogunai.domain.model.MavenProvider
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.RepositoryConfigIssue
import app.luxion.shogunai.domain.model.RepositoryProviders
import app.luxion.shogunai.domain.model.RepositoryProvisioning
import app.luxion.shogunai.domain.model.Stack
import app.luxion.shogunai.domain.model.SuggestedProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StackSuggestionsTest {

    private val basePath = "/home/dev/projects/main-repo"
    private var provisioningSource = FakeRepositoryProvisioningSource()
    private val project = Project(
        id = "p",
        name = "Project",
        config = ProjectConfig(baseRepositoryPath = basePath, worktreesRoot = "/home/dev/projects", secretFiles = emptyList()),
    )

    private fun viewModel(
        stacks: List<DetectedStack> = listOf(DetectedStack(Stack.MAVEN, listOf("pom.xml"))),
        apple: Boolean = true,
    ) = ProjectConfigViewModel(
        FakeProjectRepository(),
        provisioningSource,
        project,
        supportsApplePlatforms = apple,
        detectStacks = { stacks },
    )

    private fun declaring(providers: RepositoryProviders) {
        provisioningSource = FakeRepositoryProvisioningSource(
            byRepository = mapOf(basePath to RepositoryProvisioning(providers = providers)),
        )
    }

    @Test
    fun `suggests the maven provider with pom xml as evidence`() {
        val suggestion = viewModel().stackSuggestions.single()

        assertEquals(SuggestedProvider.MAVEN, suggestion.provider)
        assertEquals(listOf("pom.xml"), suggestion.evidence)
    }

    @Test
    fun `accepting a suggestion turns the switch on without writing anything`() {
        val viewModel = viewModel()

        viewModel.acceptSuggestion(SuggestedProvider.MAVEN)

        assertEquals(MavenDraft(), viewModel.repositoryDraft.maven)
        assertTrue(viewModel.hasUnsavedChanges)
        assertTrue(provisioningSource.writtenEdits.isEmpty())
        assertTrue(viewModel.stackSuggestions.isEmpty())
    }

    @Test
    fun `nothing is suggested when every detected stack already has its provider`() {
        declaring(RepositoryProviders(maven = MavenProvider()))

        assertTrue(viewModel().stackSuggestions.isEmpty())
    }

    @Test
    fun `no apple suggestion is shown where the platform doesn't support it`() {
        val stacks = listOf(DetectedStack(Stack.XCODE, listOf("iosApp/iosApp.xcodeproj")))

        assertTrue(viewModel(stacks, apple = false).stackSuggestions.isEmpty())
        assertEquals(
            listOf(SuggestedProvider.IOS_SIMULATOR, SuggestedProvider.XCODE),
            viewModel(stacks, apple = true).stackSuggestions.map { it.provider },
        )
    }

    @Test
    fun `saving the maven heap writes it to the file`() {
        val viewModel = viewModel()
        viewModel.setMavenEnabled(true)
        viewModel.updateRepositoryDraft { it.copy(maven = MavenDraft("1g")) }

        assertIs<app.luxion.shogunai.ui.projectconfig.SaveOutcome.Saved>(viewModel.submit(previewConfirmed = true))

        assertEquals(MavenProvider("1g"), provisioningSource.writtenEdits.single().second.providers.maven)
    }

    @Test
    fun `an invalid maven heap blocks saving`() {
        val viewModel = viewModel()
        viewModel.setMavenEnabled(true)
        viewModel.updateRepositoryDraft { it.copy(maven = MavenDraft("1 GB")) }

        assertEquals(RepositoryConfigIssue.InvalidMemorySize, viewModel.repositoryDraft.issueFor(RepositoryConfigField.MavenMaxHeap))
        assertIs<SaveOutcome.InvalidRepositoryConfig>(viewModel.submit(previewConfirmed = true))
        assertTrue(provisioningSource.writtenEdits.isEmpty())
    }

    @Test
    fun `compose port variables are written as a list`() {
        val viewModel = viewModel()
        viewModel.setDockerComposeEnabled(true)
        viewModel.updateRepositoryDraft { it.copy(dockerCompose = ComposeDraft("DB_PORT, API_PORT")) }

        viewModel.submit(previewConfirmed = true)

        assertEquals(
            DockerComposeProvider(listOf("DB_PORT", "API_PORT")),
            provisioningSource.writtenEdits.single().second.providers.dockerCompose,
        )
    }

    @Test
    fun `an invalid compose port variable name blocks saving`() {
        val viewModel = viewModel()
        viewModel.setDockerComposeEnabled(true)
        viewModel.updateRepositoryDraft { it.copy(dockerCompose = ComposeDraft("1DB")) }

        assertEquals(RepositoryConfigIssue.InvalidVariableName, viewModel.repositoryDraft.issueFor(RepositoryConfigField.ComposePortVariables))
        assertIs<SaveOutcome.InvalidRepositoryConfig>(viewModel.submit(previewConfirmed = true))
    }

    @Test
    fun `duplicate and reserved compose port variables are flagged`() {
        val viewModel = viewModel()
        viewModel.setDockerComposeEnabled(true)

        viewModel.updateRepositoryDraft { it.copy(dockerCompose = ComposeDraft("DB_PORT, DB_PORT")) }
        assertEquals(RepositoryConfigIssue.DuplicateVariableName, viewModel.repositoryDraft.issueFor(RepositoryConfigField.ComposePortVariables))

        viewModel.updateRepositoryDraft { it.copy(dockerCompose = ComposeDraft("PATH")) }
        assertEquals(RepositoryConfigIssue.ReservedVariableName, viewModel.repositoryDraft.issueFor(RepositoryConfigField.ComposePortVariables))
    }

    @Test
    fun `turning maven off removes only its provider`() {
        declaring(RepositoryProviders(maven = MavenProvider("1g"), gradle = GradleProvider()))
        val viewModel = viewModel()

        viewModel.setMavenEnabled(false)
        viewModel.submit(previewConfirmed = true)

        val providers = provisioningSource.writtenEdits.single().second.providers
        assertNull(providers.maven)
        assertEquals(GradleProvider(), providers.gradle)
    }

    @Test
    fun `turning flutter on writes it and keeps the other providers`() {
        declaring(RepositoryProviders(gradle = GradleProvider()))
        val viewModel = viewModel()

        viewModel.setFlutterEnabled(true)
        viewModel.submit(previewConfirmed = true)

        val providers = provisioningSource.writtenEdits.single().second.providers
        assertEquals(FlutterProvider, providers.flutter)
        assertEquals(GradleProvider(), providers.gradle)
    }

    @Test
    fun `accepting the flutter suggestion turns the switch on without writing anything`() {
        val viewModel = viewModel(listOf(DetectedStack(Stack.FLUTTER, listOf("pubspec.yaml"))))

        assertEquals(SuggestedProvider.FLUTTER, viewModel.stackSuggestions.single().provider)
        viewModel.acceptSuggestion(SuggestedProvider.FLUTTER)

        assertTrue(viewModel.repositoryDraft.flutter)
        assertTrue(viewModel.hasUnsavedChanges)
        assertTrue(provisioningSource.writtenEdits.isEmpty())
    }

    @Test
    fun `turning flutter off removes only its provider`() {
        declaring(RepositoryProviders(flutter = FlutterProvider, gradle = GradleProvider()))
        val viewModel = viewModel()

        assertTrue(viewModel.repositoryDraft.flutter)
        viewModel.setFlutterEnabled(false)
        viewModel.submit(previewConfirmed = true)

        val providers = provisioningSource.writtenEdits.single().second.providers
        assertNull(providers.flutter)
        assertEquals(GradleProvider(), providers.gradle)
    }
}
