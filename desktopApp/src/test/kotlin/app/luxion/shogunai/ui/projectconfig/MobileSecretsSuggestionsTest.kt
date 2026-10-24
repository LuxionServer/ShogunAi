package app.luxion.shogunai.ui.projectconfig

import app.luxion.shogunai.domain.FakeProjectRepository
import app.luxion.shogunai.domain.FakeRepositoryProvisioningSource
import app.luxion.shogunai.domain.model.KeystoreReferenceWarning
import app.luxion.shogunai.domain.model.MobileSecret
import app.luxion.shogunai.domain.model.MobileSecretKind
import app.luxion.shogunai.domain.model.MobileSecretsReport
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.ui.i18n.EnglishStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MobileSecretsSuggestionsTest {

    private val dispatcher = StandardTestDispatcher()
    private val basePath = "/home/dev/projects/main-repo"
    private var provisioningSource = FakeRepositoryProvisioningSource()
    private val projectRepository = FakeProjectRepository()
    private val firebase = MobileSecret("app/google-services.json", MobileSecretKind.FIREBASE_CONFIG)
    private val warning = KeystoreReferenceWarning("keystore.properties", "storeFile", "../signing/release.jks")
    private val detectedConfigs = mutableListOf<ProjectConfig>()
    private val project = Project(
        id = "p",
        name = "Project",
        config = ProjectConfig(baseRepositoryPath = basePath, worktreesRoot = "/home/dev/projects", secretFiles = emptyList()),
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(report: MobileSecretsReport = MobileSecretsReport(listOf(firebase), listOf(warning))) =
        ProjectConfigViewModel(
            projectRepository,
            provisioningSource,
            project,
            detectMobileSecrets = { config -> detectedConfigs += config; report },
        )

    @Test
    fun `lists the detected secrets and warnings`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(listOf(firebase), viewModel.mobileSecretSuggestions)
        assertEquals(listOf(warning), viewModel.mobileSecrets.warnings)
        assertEquals(basePath, detectedConfigs.last().baseRepositoryPath)
    }

    @Test
    fun `linking a secret is a pending linked pattern written on save`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.linkMobileSecret(firebase)

        assertEquals(listOf("/app/google-services.json"), viewModel.pendingLinkPatterns)
        assertTrue(viewModel.mobileSecretSuggestions.isEmpty())
        assertTrue(viewModel.hasUnsavedChanges)
        assertTrue(provisioningSource.addedLinkPatterns.isEmpty())
        assertIs<SaveOutcome.NeedsPreview>(viewModel.submit())

        viewModel.submit(previewConfirmed = true)

        assertEquals(listOf(basePath to listOf("/app/google-services.json")), provisioningSource.addedLinkPatterns)
    }

    @Test
    fun `the pending link is shown with the repository's patterns`() {
        assertEquals(
            listOf("/app/google-services.json" to ".shogunai.json · linked · on save"),
            provisioningRows(null, emptyList(), EnglishStrings, listOf("/app/google-services.json")),
        )
    }

    @Test
    fun `no link action while the repository file can't be read`() = runTest(dispatcher) {
        provisioningSource = FakeRepositoryProvisioningSource(unreadable = setOf(basePath))
        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(listOf(firebase), viewModel.mobileSecretSuggestions)
        assertFalse(viewModel.canLinkMobileSecrets)
        viewModel.linkMobileSecret(firebase)
        assertTrue(viewModel.pendingLinkPatterns.isEmpty())
    }

    @Test
    fun `detection follows the local entries being edited`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.addSecretFile("app/google-services.json")
        advanceUntilIdle()

        assertEquals(listOf("app/google-services.json"), detectedConfigs.last().secretFiles)
    }

    @Test
    fun `nothing is shown when nothing is suggested`() = runTest(dispatcher) {
        val viewModel = viewModel(MobileSecretsReport())
        advanceUntilIdle()

        assertTrue(viewModel.mobileSecretSuggestions.isEmpty())
        assertTrue(viewModel.mobileSecrets.warnings.isEmpty())
        assertFalse(viewModel.hasUnsavedChanges)
    }
}
