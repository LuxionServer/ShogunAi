package app.luxion.shogunai.ui.projectconfig

import app.luxion.shogunai.domain.FakeProjectRepository
import app.luxion.shogunai.domain.FakeRepositoryProvisioningSource
import app.luxion.shogunai.domain.model.LocalFileCandidate
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.WorktreeError
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LocalFilePickerViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val basePath = "/home/dev/projects/main-repo"
    private val env = LocalFileCandidate(".env", isFolder = false)
    private val keys = LocalFileCandidate("keys", isFolder = true)
    private val localProperties = LocalFileCandidate("local.properties", isFolder = false)
    private val listedConfigs = mutableListOf<ProjectConfig>()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        secretFiles: List<String> = listOf("local.properties", "*.p12"),
        linked: List<String> = emptyList(),
        basePath: String = this.basePath,
        list: (suspend (ProjectConfig) -> List<LocalFileCandidate>)? = { listOf(localProperties, env, keys) },
    ) = ProjectConfigViewModel(
        FakeProjectRepository(),
        FakeRepositoryProvisioningSource(),
        Project(
            id = "p",
            name = "Project",
            config = ProjectConfig(
                baseRepositoryPath = basePath,
                worktreesRoot = "/home/dev/projects",
                secretFiles = secretFiles,
                linkedSecretFiles = linked,
            ),
        ),
        listLocalFileCandidates = list?.let { listing -> { config -> listedConfigs += config; listing(config) } },
    )

    @Test
    fun `opening the picker loads the base repository's candidates with the declared entries checked`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.openLocalFilePicker()
        val picker = viewModel.localFilePicker!!
        assertIs<LocalFileCandidatesLoad.Loading>(picker.load)
        advanceUntilIdle()

        assertEquals(LocalFileCandidatesLoad.Loaded(listOf(localProperties, env, keys)), picker.load)
        assertTrue(picker.isChecked(localProperties))
        assertEquals(basePath, listedConfigs.single().baseRepositoryPath)
    }

    @Test
    fun `applying adds the checked entries in copy mode and removes the unchecked ones`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.openLocalFilePicker()
        advanceUntilIdle()
        val picker = viewModel.localFilePicker!!

        picker.setChecked(localProperties, false)
        picker.setChecked(env, true)
        picker.setChecked(keys, true)
        viewModel.applyLocalFilePicker()

        assertEquals(listOf("*.p12", ".env", "keys"), viewModel.secretFiles)
        assertFalse(viewModel.isLinkedEntry(".env"))
        assertFalse(viewModel.isLinkedEntry("keys"))
        assertTrue(viewModel.hasUnsavedChanges)
        assertNull(viewModel.localFilePicker)
    }

    @Test
    fun `applying adds the patterns chosen in the picker`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.openLocalFilePicker()
        advanceUntilIdle()
        val picker = viewModel.localFilePicker!!

        picker.filter = "*.jks"
        picker.addOfferedPattern()
        viewModel.applyLocalFilePicker()

        assertEquals(listOf("local.properties", "*.p12", "*.jks"), viewModel.secretFiles)
        assertTrue(viewModel.isPatternEntry("*.jks"))
    }

    @Test
    fun `a linked entry that stays checked keeps its mode`() = runTest(dispatcher) {
        val viewModel = viewModel(linked = listOf("local.properties"))
        viewModel.openLocalFilePicker()
        advanceUntilIdle()

        viewModel.applyLocalFilePicker()

        assertTrue(viewModel.isLinkedEntry("local.properties"))
        assertFalse(viewModel.hasUnsavedChanges)
    }

    @Test
    fun `cancelling changes nothing`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.openLocalFilePicker()
        advanceUntilIdle()

        viewModel.localFilePicker!!.setChecked(env, true)
        viewModel.cancelLocalFilePicker()

        assertEquals(listOf("local.properties", "*.p12"), viewModel.secretFiles)
        assertNull(viewModel.localFilePicker)
    }

    @Test
    fun `a listing failure is kept for the picker to show and can be retried`() = runTest(dispatcher) {
        var fail = true
        val viewModel = viewModel(list = {
            if (fail) throw WorktreeError.GitCommandFailed(listOf("git", "ls-files"), 128, "fatal: not a git repository") else listOf(env)
        })
        viewModel.openLocalFilePicker()
        advanceUntilIdle()

        assertIs<LocalFileCandidatesLoad.Failed>(viewModel.localFilePicker!!.load)

        fail = false
        viewModel.retryLocalFileCandidates()
        advanceUntilIdle()

        assertEquals(LocalFileCandidatesLoad.Loaded(listOf(env)), viewModel.localFilePicker!!.load)
    }

    @Test
    fun `choosing needs a base repository and a candidate source`() = runTest(dispatcher) {
        assertFalse(viewModel(basePath = "").canChooseLocalFiles)
        assertFalse(viewModel(list = null).canChooseLocalFiles)
        assertTrue(viewModel().canChooseLocalFiles)
    }
}
