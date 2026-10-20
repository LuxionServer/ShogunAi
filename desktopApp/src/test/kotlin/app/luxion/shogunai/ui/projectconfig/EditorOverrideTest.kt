package app.luxion.shogunai.ui.projectconfig

import app.luxion.shogunai.domain.FakeProjectRepository
import app.luxion.shogunai.domain.FakeRepositoryProvisioningSource
import app.luxion.shogunai.domain.FakeToolDetector
import app.luxion.shogunai.domain.FakeToolPreferenceRepository
import app.luxion.shogunai.domain.model.DetectedTools
import app.luxion.shogunai.domain.model.EditorId
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.ui.shell.ToolsPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EditorOverrideTest {

    private val dispatcher = StandardTestDispatcher()
    private val projectRepository = FakeProjectRepository()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun project(editorOverride: EditorId? = null) = Project(
        id = "p",
        name = "App",
        config = ProjectConfig(
            baseRepositoryPath = "/home/dev/projects/android-app",
            worktreesRoot = "/home/dev/projects/worktrees",
            secretFiles = emptyList(),
            editorOverride = editorOverride,
        ),
    )

    private suspend fun viewModel(project: Project, installed: List<EditorId>): ProjectConfigViewModel {
        val tools = ToolsPreferences(FakeToolPreferenceRepository(), FakeToolDetector(DetectedTools(editors = installed)))
        tools.refresh()
        return ProjectConfigViewModel(projectRepository, FakeRepositoryProvisioningSource(), project, tools = tools)
    }

    @Test
    fun `a project starts without an override and choosing one is an unsaved change`() = runTest(dispatcher) {
        val viewModel = viewModel(project(), listOf(EditorId.VS_CODE, EditorId.ANDROID_STUDIO))

        assertNull(viewModel.editorOverride)
        assertFalse(viewModel.hasUnsavedChanges)

        viewModel.editorOverride = EditorId.ANDROID_STUDIO

        assertTrue(viewModel.hasUnsavedChanges)
    }

    @Test
    fun `Xcode can be the project's IDE when it is installed`() = runTest(dispatcher) {
        val viewModel = viewModel(project(EditorId.XCODE), listOf(EditorId.VS_CODE, EditorId.XCODE))

        assertEquals(EditorId.XCODE, viewModel.editorOverride)
        assertFalse(viewModel.editorOverrideMissing)
    }

    @Test
    fun `an Xcode override is missing on a machine without it`() = runTest(dispatcher) {
        val viewModel = viewModel(project(EditorId.XCODE), listOf(EditorId.VS_CODE))

        assertTrue(viewModel.editorOverrideMissing)
    }

    @Test
    fun `the override is saved with the project and reloaded`() = runTest(dispatcher) {
        val viewModel = viewModel(project(), listOf(EditorId.VS_CODE, EditorId.ANDROID_STUDIO))
        viewModel.editorOverride = EditorId.ANDROID_STUDIO

        val saved = viewModel.save().getOrThrow()

        assertEquals(EditorId.ANDROID_STUDIO, saved.config.editorOverride)
        assertEquals(EditorId.ANDROID_STUDIO, projectRepository.list().single().config.editorOverride)
        assertEquals(EditorId.ANDROID_STUDIO, viewModel(project(EditorId.ANDROID_STUDIO), listOf(EditorId.ANDROID_STUDIO)).editorOverride)
    }

    @Test
    fun `going back to the default clears the override`() = runTest(dispatcher) {
        val viewModel = viewModel(project(EditorId.ANDROID_STUDIO), listOf(EditorId.ANDROID_STUDIO))
        viewModel.editorOverride = null

        assertNull(viewModel.save().getOrThrow().config.editorOverride)
    }

    @Test
    fun `an override that isn't installed is reported and kept until changed`() = runTest(dispatcher) {
        val viewModel = viewModel(project(EditorId.ANDROID_STUDIO), listOf(EditorId.VS_CODE))

        assertTrue(viewModel.editorOverrideMissing)
        assertEquals(EditorId.ANDROID_STUDIO, viewModel.save().getOrThrow().config.editorOverride)

        viewModel.editorOverride = EditorId.VS_CODE
        assertFalse(viewModel.editorOverrideMissing)
    }
}
