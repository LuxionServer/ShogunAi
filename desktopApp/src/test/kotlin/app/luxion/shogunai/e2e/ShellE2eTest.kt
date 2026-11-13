package app.luxion.shogunai.e2e

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import app.luxion.shogunai.AppContainer
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.shellViewModelOf
import app.luxion.shogunai.ui.LocalPathPicker
import app.luxion.shogunai.ui.components.LocalShownErrorLog
import app.luxion.shogunai.ui.i18n.Strings
import app.luxion.shogunai.ui.machine.MachineResource
import app.luxion.shogunai.ui.rememberScreenViewModelStoreOwner
import app.luxion.shogunai.ui.shell.AppShell
import app.luxion.shogunai.ui.shell.Appearance
import app.luxion.shogunai.ui.shell.AppearanceScope
import app.luxion.shogunai.ui.shell.RecordingClipboard
import app.luxion.shogunai.ui.shell.ShellDestination
import app.luxion.shogunai.ui.shell.ShellViewModel
import app.luxion.shogunai.ui.worktree.InactivityPreferences
import app.luxion.shogunai.ui.worktree.WorktreeBoard
import app.luxion.shogunai.ui.worktree.WorktreeRef
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ShellE2eTest {

    private val clipboard = RecordingClipboard()
    private var shell: ShellViewModel? = null
    private var board: WorktreeBoard? = null

    private fun ComposeUiTest.show(appContainer: AppContainer, width: Dp? = null): Strings {
        val appearance = Appearance(appContainer.themePreferenceRepository, appContainer.languagePreferenceRepository)
        val inactivity = InactivityPreferences(appContainer.shellPreferenceRepository)
        setContent {
            CompositionLocalProvider(
                LocalViewModelStoreOwner provides rememberScreenViewModelStoreOwner(),
                LocalShownErrorLog provides appContainer.shownErrorLog,
                LocalPathPicker provides AnsweringPathPicker(),
                LocalClipboard provides clipboard,
            ) {
                val shellViewModel = viewModel { shellViewModelOf(appContainer) }.also { shell = it }
                AppearanceScope(appearance) {
                    Box(if (width == null) Modifier else Modifier.requiredWidth(width)) {
                        AppShell(appContainer, shellViewModel, onOpenPreferences = {}, inactivity = inactivity, onBoardReady = { board = it })
                    }
                }
            }
        }
        return appearance.strings
    }

    private fun project(repository: File) = Project(
        id = repository.name,
        name = repository.name,
        config = ProjectConfig(repository.absolutePath, repository.parentFile.resolve("${repository.name}-worktrees").absolutePath, emptyList()),
    )

    @Test
    fun `a project is removed after confirming`() = runComposeUiTest {
        val environment = E2eEnvironment.prepare()
        val appContainer = AppContainer(environment.hostEnvironment())
        appContainer.projectRepository.save(project(environment.repository("sample-app")))
        val strings = show(appContainer)

        awaitText("sample-app")
        onAllNodesWithText("sample-app").onFirst().performMouseInput { rightClick() }
        onNodeWithText(strings.removeProject).performClick()
        awaitText(strings.removeProjectTitle)
        onAllNodesWithText(strings.remove).onLast().performClick()

        awaitNoText(strings.removeProjectTitle)
        awaitCondition("the project to be removed") { appContainer.projectRepository.list().isEmpty() }
    }

    @Test
    fun `a project kept after asking is still listed`() = runComposeUiTest {
        val environment = E2eEnvironment.prepare()
        val appContainer = AppContainer(environment.hostEnvironment())
        appContainer.projectRepository.save(project(environment.repository("sample-app")))
        val strings = show(appContainer)

        awaitText("sample-app")
        onAllNodesWithText("sample-app").onFirst().performMouseInput { rightClick() }
        onNodeWithText(strings.removeProject).performClick()
        awaitText(strings.removeProjectTitle)
        onAllNodesWithText(strings.cancel).onLast().performClick()

        awaitNoText(strings.removeProjectTitle)
        assertEquals(listOf("sample-app"), appContainer.projectRepository.list().map { it.name })
    }

    @Test
    fun `an unreadable projects file is reported until it is dismissed`() = runComposeUiTest {
        val environment = E2eEnvironment.prepare()
        environment.shogunaiFolder.apply { mkdirs() }.resolve("projects.json").writeText("{ not json")
        val appContainer = AppContainer(environment.hostEnvironment())
        val strings = show(appContainer)

        awaitCondition("the unreadable file notice") { onAllNodesWithContentDescription(strings.closeError).fetchSemanticsNodes().isNotEmpty() }
        onAllNodesWithContentDescription(strings.closeError).onFirst().performClick()

        awaitCondition("the notice to go") { shell?.unreadableFiles?.isEmpty() == true }
    }

    @Test
    fun `the palette copies the selected worktree's path and the menu bar acts on the selection`() = runComposeUiTest {
        val environment = E2eEnvironment.prepare()
        val repository = environment.repository("sample-app")
        val appContainer = AppContainer(environment.hostEnvironment())
        appContainer.projectRepository.save(project(repository))
        val strings = show(appContainer)
        awaitText("sample-app")
        val main = WorktreeRef("sample-app", repository.absolutePath)
        awaitCondition("the worktrees to load") { board?.holder("sample-app")?.hasLoaded == true }
        val actions = assertNotNull(shell?.worktreeActions)
        assertFalse(actions.hasSelection())

        runOnIdle { board!!.revealInAllWorktrees(main) }
        assertTrue(actions.hasSelection())
        assertTrue(actions.menuBar(strings).isNotEmpty())
        runOnIdle { shell!!.openPalette() }
        awaitCondition("the palette") { onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size == 2 }
        onAllNodes(hasSetTextAction()).onLast().performTextInput(strings.copyPath)
        onAllNodes(hasSetTextAction()).onLast().performKeyInput { pressKey(Key.Enter) }

        awaitCondition("the path to be copied") { clipboard.entries.isNotEmpty() }
        assertFalse(shell!!.isPaletteOpen)
    }

    @Test
    fun `a narrow window shows the sidebar as a rail`() = runComposeUiTest {
        val environment = E2eEnvironment.prepare()
        val appContainer = AppContainer(environment.hostEnvironment())
        appContainer.projectRepository.save(project(environment.repository("sample-app")))
        show(appContainer, width = 800.dp)

        awaitCondition("the rail") { shell?.isRail == true }
    }

    @Test
    fun `a machine resource shown from the status bar is selected in it`() = runComposeUiTest {
        val environment = E2eEnvironment.prepare()
        val appContainer = AppContainer(environment.hostEnvironment())
        appContainer.projectRepository.save(project(environment.repository("sample-app")))
        show(appContainer)
        awaitText("sample-app")

        runOnIdle { shell!!.selectMachine(MachineResource.GRADLE_DAEMONS) }
        waitForIdle()

        assertTrue(appContainer.loadedBranches().contains(E2eEnvironment.MAIN_BRANCH))
        assertFalse(appContainer.declaresMobileProviders())
    }

    @Test
    fun `leaving a project's settings with unsaved changes asks before discarding them`() = runComposeUiTest {
        val environment = E2eEnvironment.prepare()
        val appContainer = AppContainer(environment.hostEnvironment())
        val sample = project(environment.repository("sample-app"))
        appContainer.projectRepository.save(sample)
        val strings = show(appContainer)
        awaitText("sample-app")

        runOnIdle { shell!!.editProject(sample) }
        awaitCondition("the settings form") { onAllNodesWithText("sample-app").fetchSemanticsNodes().isNotEmpty() && shell?.destination is ShellDestination.ProjectSettings }
        awaitCondition("the name field") { onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
        onAllNodes(hasSetTextAction()).onFirst().performTextInput("-renamed")
        runOnIdle { shell!!.selectAll() }
        awaitText(strings.discardChangesTitle)
        onAllNodesWithText(strings.discard).onLast().performClick()

        awaitCondition("the view of all worktrees") { shell?.destination == ShellDestination.All() }
    }
}
