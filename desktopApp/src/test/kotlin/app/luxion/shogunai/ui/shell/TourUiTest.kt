package app.luxion.shogunai.ui.shell

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.io.UserPathEntries
import app.luxion.shogunai.domain.model.HostOs
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.model.newProject
import app.luxion.shogunai.domain.usecase.ClaudeCodeHooks
import app.luxion.shogunai.domain.usecase.CommandLineTool
import app.luxion.shogunai.domain.model.CommandLineToolState
import app.luxion.shogunai.ui.i18n.EnglishStrings
import app.luxion.shogunai.ui.i18n.LocalStrings
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val tourProject = newProject("tour", "/home/dev/.shogunai/tour/sample-repo").copy(name = "ShogunAi tour")

private class FakeTour(
    var existing: Project? = null,
    var leftover: Boolean = false,
    var toolAvailable: Boolean = true,
    var offered: Boolean = false,
    var createResult: Result<Project> = Result.success(tourProject),
    var endResult: Result<Unit> = Result.success(Unit),
) {
    var created = 0
    var ended = 0
    var rememberedOffers = 0

    val actions = TourActions(
        create = {
            created++
            createResult
        },
        end = {
            ended++
            endResult.onSuccess {
                existing = null
                leftover = false
            }
        },
        existingProject = { existing },
        leftoverFolder = { leftover },
        toolAvailable = { toolAvailable },
        offered = { offered },
        rememberOffered = {
            offered = true
            rememberedOffers++
        },
    )
}

class TourStateTest {

    @Test
    fun `the notice isn't shown without projects or while the tour is open`() {
        val state = TourState(FakeTour().actions)

        assertFalse(state.showsNotice(hasProjects = false))
        state.start()
        assertFalse(state.showsNotice(hasProjects = true))
    }

    @Test
    fun `starting again after the offer was remembered doesn't remember it twice`() {
        val tour = FakeTour(offered = true)
        val state = TourState(tour.actions)

        state.start()

        assertEquals(0, tour.rememberedOffers)
        assertEquals(TourStep.WELCOME, state.step)
    }

    @Test
    fun `a restart whose ending fails keeps the tour and shows the error`() = runBlocking<Unit> {
        val failure = IllegalStateException("tour folder locked")
        val tour = FakeTour(existing = tourProject, endResult = Result.failure(failure))
        val state = TourState(tour.actions)
        state.start()

        state.restart()

        assertEquals(failure, state.error)
        assertEquals(tourProject, state.project)
        assertEquals(TourStep.EXISTING, state.step)
        assertEquals(0, tour.created)
        state.dismissError()
        assertNull(state.error)
    }

    @Test
    fun `cancelling the end keeps the tour`() {
        val state = TourState(FakeTour().actions)
        state.requestEnd()

        state.cancelEnd()

        assertFalse(state.isEndRequested)
    }

    @Test
    fun `an ending that fails keeps the project and shows the error`() = runBlocking<Unit> {
        val failure = IllegalStateException("tour folder locked")
        val tour = FakeTour(existing = tourProject, endResult = Result.failure(failure))
        val state = TourState(tour.actions)
        state.start()
        state.requestEnd()

        val result = state.confirmEnd()

        assertEquals(failure, result.exceptionOrNull())
        assertEquals(failure, state.error)
        assertEquals(tourProject, state.project)
        assertFalse(state.isEndRequested)
    }

    @Test
    fun `with the tool available the welcome goes straight to creating the sample`() = runBlocking<Unit> {
        val tour = FakeTour(toolAvailable = true)
        val state = TourState(tour.actions)

        state.start()
        state.continueFromWelcome()

        assertEquals(1, tour.created)
        assertEquals(TourStep.OPEN_AGENT, state.step)
        assertEquals(tourProject, state.project)
    }

    @Test
    fun `with the tool missing the tool step comes before creating`() = runBlocking<Unit> {
        val tour = FakeTour(toolAvailable = false)
        val state = TourState(tour.actions)

        state.start()
        state.continueFromWelcome()

        assertEquals(TourStep.TOOL, state.step)
        assertEquals(0, tour.created)
        state.continueFromTool()
        assertEquals(TourStep.OPEN_AGENT, state.step)
    }

    @Test
    fun `cancelling at the welcome writes nothing but the offer`() {
        val tour = FakeTour()
        val state = TourState(tour.actions)

        state.start()
        state.close()

        assertNull(state.step)
        assertEquals(0, tour.created)
        assertEquals(0, tour.ended)
        assertEquals(1, tour.rememberedOffers)
    }

    @Test
    fun `an existing tour offers to restart, which ends it and creates it again`() = runBlocking<Unit> {
        val tour = FakeTour(existing = tourProject)
        val state = TourState(tour.actions)

        state.start()
        assertEquals(TourStep.EXISTING, state.step)
        state.restart()

        assertEquals(1, tour.ended)
        assertEquals(1, tour.created)
        assertEquals(TourStep.OPEN_AGENT, state.step)
    }

    @Test
    fun `a leftover folder without a project is offered as existing`() {
        val state = TourState(FakeTour(leftover = true).actions)

        state.start()

        assertEquals(TourStep.EXISTING, state.step)
        assertNull(state.project)
    }

    @Test
    fun `a failed creation stays on the step with its error and can be retried`() = runBlocking<Unit> {
        val tour = FakeTour(createResult = Result.failure(WorktreeError.TourCreationFailed(listOf("git", "init"), "boom")))
        val state = TourState(tour.actions)

        state.start()
        state.continueFromWelcome()
        assertEquals(TourStep.CREATING, state.step)
        assertTrue(state.error is WorktreeError.TourCreationFailed)

        tour.createResult = Result.success(tourProject)
        state.retry()
        assertEquals(TourStep.OPEN_AGENT, state.step)
        assertNull(state.error)
    }

    @Test
    fun `the notice shows once to people with projects and never after the tour started`() {
        val tour = FakeTour()
        val state = TourState(tour.actions)

        assertFalse(state.showsNotice(hasProjects = false))
        assertTrue(state.showsNotice(hasProjects = true))
        state.start()
        state.close()

        assertFalse(state.showsNotice(hasProjects = true))
        assertFalse(TourState(tour.actions).showsNotice(hasProjects = true))
    }

    @Test
    fun `ending the tour forgets its project`() = runBlocking<Unit> {
        val tour = FakeTour(existing = tourProject)
        val state = TourState(tour.actions)
        state.start()
        state.close()

        state.requestEnd()
        state.confirmEnd()

        assertEquals(1, tour.ended)
        assertNull(state.project)
        assertFalse(state.isEndRequested)
    }
}

@OptIn(ExperimentalTestApi::class)
class TourUiTest {

    private val launcherFiles = FakeLauncherFiles()

    private fun hooks(found: String?) = ClaudeHooksState(
        hooks = ClaudeCodeHooks(FakeFileManager()),
        settingsPath = { "/home/dev/.claude/settings.json" },
        tool = CommandLineTool(
            os = HostOs.MACOS,
            appLauncher = "/Applications/ShogunAi.app/Contents/MacOS/ShogunAi",
            home = "/home/dev",
            pathFolders = { listOf("/usr/bin", "/usr/local/bin") },
            userBinFolders = listOf("/usr/local/bin", "/home/dev/.local/bin"),
            findShogun = { found ?: launcherFiles.links.keys.firstOrNull() },
            files = launcherFiles,
            userPath = object : UserPathEntries {
                override suspend fun contains(folder: String) = false
                override suspend fun add(folder: String) = Unit
                override suspend fun remove(folder: String) = Unit
            },
        ),
        noticeDismissedStore = { true },
        rememberNoticeDismissed = {},
    ).also { runBlocking { it.refresh() } }

    @Test
    fun `the dialog goes through welcome, the tool step and opening the agent`() = runComposeUiTest {
        launcherFiles.writable += "/usr/local/bin"
        launcherFiles.others += "/Applications/ShogunAi.app/Contents/MacOS/ShogunAi"
        val hooks = hooks(found = null)
        val tour = FakeTour(toolAvailable = false)
        val state = TourState(tour.actions).also { it.start() }
        var shown: Project? = null
        setContent {
            CompositionLocalProvider(LocalStrings provides EnglishStrings) {
                MaterialTheme {
                    TourDialog(
                        state = state,
                        hooks = hooks,
                        onShowProject = { shown = it },
                        agentChoices = { project, _ -> Text("Agent choices for ${project.name}") },
                    )
                }
            }
        }

        onNodeWithText(EnglishStrings.tourWelcomeMessage("~/.shogunai/tour")).assertIsDisplayed()
        onNodeWithText(EnglishStrings.tourStart).performClick()
        waitForIdle()
        onNodeWithText(EnglishStrings.tourToolMessage).assertIsDisplayed()
        onNodeWithText(EnglishStrings.tourSkip).assertIsDisplayed()
        onNodeWithText(EnglishStrings.commandLineToolInstall).performClick()
        waitUntil { hooks.toolStatus?.state != CommandLineToolState.MISSING }
        onNodeWithText(EnglishStrings.tourContinue).performClick()
        waitUntil { state.step == TourStep.OPEN_AGENT }

        onNodeWithText("Agent choices for ShogunAi tour").assertIsDisplayed()
        onNodeWithText(EnglishStrings.tourTrustHint).assertIsDisplayed()
        assertEquals(tourProject, shown)
        assertEquals(1, tour.created)
    }

    @Test
    fun `an existing tour offers to open it or restart it`() = runComposeUiTest {
        val state = TourState(FakeTour(existing = tourProject).actions).also { it.start() }
        var shown: Project? = null
        setContent {
            CompositionLocalProvider(LocalStrings provides EnglishStrings) {
                MaterialTheme {
                    TourDialog(state, hooks(found = "/usr/local/bin/shogun"), onShowProject = { shown = it }, agentChoices = { _, _ -> })
                }
            }
        }

        onNodeWithText(EnglishStrings.tourRestart).assertIsDisplayed()
        onNodeWithText(EnglishStrings.tourOpenExisting).performClick()

        assertEquals(tourProject, shown)
        assertNull(state.step)
    }

    @Test
    fun `the notice is one line that starts the tour`() = runComposeUiTest {
        val state = TourState(FakeTour().actions)
        setContent {
            CompositionLocalProvider(LocalStrings provides EnglishStrings) {
                MaterialTheme { Column { TourNotice(state, hasProjects = true) } }
            }
        }

        onNodeWithText(EnglishStrings.tourNoticeMessage).performClick()
        waitForIdle()

        assertTrue(state.step != null)
    }

    @Test
    fun `the collapsed sidebar shows the notice as an icon that starts the tour`() = runComposeUiTest {
        val state = TourState(FakeTour().actions)
        setContent {
            CompositionLocalProvider(LocalStrings provides EnglishStrings) {
                MaterialTheme { Column { TourNotice(state, hasProjects = true, rail = true) } }
            }
        }

        onNodeWithText(EnglishStrings.tourNoticeMessage).assertDoesNotExist()
        onNodeWithContentDescription(EnglishStrings.tourNoticeDetails).performClick()
        waitForIdle()

        assertTrue(state.step != null)
    }

    @Test
    fun `the notice starts the tour and doesn't return once dismissed`() = runComposeUiTest {
        val tour = FakeTour()
        val state = TourState(tour.actions)
        setContent {
            CompositionLocalProvider(LocalStrings provides EnglishStrings) {
                MaterialTheme { Column { TourNotice(state, hasProjects = true) } }
            }
        }

        onNodeWithText(EnglishStrings.tourNoticeMessage).assertIsDisplayed()
        onNodeWithContentDescription(EnglishStrings.claudeHooksNoticeDismiss).performClick()
        waitForIdle()

        onNodeWithText(EnglishStrings.tourNoticeMessage).assertDoesNotExist()
        assertTrue(tour.offered)
        assertFalse(TourState(tour.actions).showsNotice(hasProjects = true))
    }

    @Test
    fun `the empty state offers the tour next to adding a project`() = runComposeUiTest {
        var tours = 0
        setContent {
            CompositionLocalProvider(LocalStrings provides EnglishStrings) {
                MaterialTheme { FirstLaunchState(onAddProject = {}, onTakeTour = { tours++ }) }
            }
        }

        onNodeWithText(EnglishStrings.addProject).assertIsDisplayed()
        onNodeWithText(EnglishStrings.tourTakeTour).performClick()

        assertEquals(1, tours)
    }

    @Test
    fun `the banner opens the agent choices`() = runComposeUiTest {
        val state = TourState(FakeTour(existing = tourProject).actions)
        setContent {
            CompositionLocalProvider(LocalStrings provides EnglishStrings) {
                MaterialTheme {
                    Column { TourBanner(state, tourProject, onEnded = {}, agentChoices = { _, _ -> Text("Agent choices") }) }
                }
            }
        }

        onNodeWithText(EnglishStrings.tourBannerMessage).assertIsDisplayed()
        onNodeWithText(EnglishStrings.tourOpenAgent).performClick()

        onNodeWithText("Agent choices").assertIsDisplayed()
        onNodeWithText(EnglishStrings.tourTrustHint).assertIsDisplayed()
    }

    @Test
    fun `ending from the banner asks first and ends after confirming`() = runComposeUiTest {
        val tour = FakeTour(existing = tourProject)
        val state = TourState(tour.actions)
        var ended = 0
        setContent {
            CompositionLocalProvider(LocalStrings provides EnglishStrings) {
                MaterialTheme { Column { TourBanner(state, tourProject, onEnded = { ended++ }, agentChoices = { _, _ -> }) } }
            }
        }

        onNodeWithText(EnglishStrings.tourEnd).performClick()
        onNodeWithText(EnglishStrings.tourEndMessage("~/.shogunai/tour")).assertIsDisplayed()
        assertEquals(0, tour.ended)
        onAllNodesWithText(EnglishStrings.tourEnd).onLast().performClick()
        waitUntil { ended == 1 }

        assertEquals(1, tour.ended)
        onNodeWithText(EnglishStrings.tourEndTitle).assertDoesNotExist()
    }

    private fun androidx.compose.ui.test.ComposeUiTest.showDialog(state: TourState, hooks: ClaudeHooksState, onShown: (Project) -> Unit = {}) =
        setContent {
            CompositionLocalProvider(LocalStrings provides EnglishStrings) {
                MaterialTheme { TourDialog(state, hooks, onShowProject = onShown, agentChoices = { project, _ -> Text("Agent choices for ${project.name}") }) }
            }
        }

    @Test
    fun `a leftover tour folder without a project only offers to restart`() = runComposeUiTest {
        val state = TourState(FakeTour(leftover = true).actions).also { it.start() }
        showDialog(state, hooks(found = "/usr/local/bin/shogun"))

        onNodeWithText(EnglishStrings.tourRestart).assertIsDisplayed()
        onNodeWithText(EnglishStrings.tourOpenExisting).assertDoesNotExist()
        onNodeWithText(EnglishStrings.cancel).performClick()

        assertNull(state.step)
    }

    @Test
    fun `the tool step offers to skip while the tool's status is unknown`() = runComposeUiTest {
        val unread = ClaudeHooksState(
            hooks = ClaudeCodeHooks(FakeFileManager()),
            settingsPath = { "/home/dev/.claude/settings.json" },
            tool = CommandLineTool(
                os = HostOs.MACOS,
                appLauncher = null,
                home = "/home/dev",
                pathFolders = { emptyList() },
                userBinFolders = emptyList(),
                findShogun = { null },
                files = launcherFiles,
                userPath = object : UserPathEntries {
                    override suspend fun contains(folder: String) = false
                    override suspend fun add(folder: String) = Unit
                    override suspend fun remove(folder: String) = Unit
                },
            ),
            noticeDismissedStore = { true },
            rememberNoticeDismissed = {},
        )
        val state = TourState(FakeTour(toolAvailable = false).actions).also { it.start() }
        showDialog(state, unread)

        onNodeWithText(EnglishStrings.tourStart).performClick()
        waitUntil { state.step == TourStep.TOOL }

        onNodeWithText(EnglishStrings.tourSkip).assertIsDisplayed()
    }

    @Test
    fun `the tool step lets the tour continue with the tool outside the PATH`() = runComposeUiTest {
        val state = TourState(FakeTour(toolAvailable = false).actions).also { it.start() }
        showDialog(state, hooks(found = "/home/dev/bin/shogun"))

        onNodeWithText(EnglishStrings.tourStart).performClick()
        waitUntil { state.step == TourStep.TOOL }

        onNodeWithText(EnglishStrings.tourContinue).assertIsDisplayed()
    }

    @Test
    fun `a failed creation shows its error, retries and can be cancelled`() = runComposeUiTest {
        val tour = FakeTour(createResult = Result.failure(IllegalStateException("disk full")))
        val state = TourState(tour.actions).also { it.start() }
        showDialog(state, hooks(found = "/usr/local/bin/shogun"))

        onNodeWithText(EnglishStrings.tourStart).performClick()
        waitUntil { state.error != null }
        onNodeWithText(EnglishStrings.tourCreating).assertDoesNotExist()
        onNodeWithText(EnglishStrings.tourRetry).performClick()
        waitUntil { tour.created == 2 }
        onNodeWithText(EnglishStrings.cancel).performClick()

        assertNull(state.step)
    }

    @Test
    fun `an error shown in the dialog can be dismissed`() = runComposeUiTest {
        val tour = FakeTour(existing = tourProject, endResult = Result.failure(IllegalStateException("tour folder locked")))
        val state = TourState(tour.actions).also { it.start() }
        showDialog(state, hooks(found = "/usr/local/bin/shogun"))

        onNodeWithText(EnglishStrings.tourRestart).performClick()
        waitUntil { state.error != null }
        onNodeWithContentDescription(EnglishStrings.closeError).performClick()

        assertNull(state.error)
    }

    @Test
    fun `the agent step of a tour that ended in the meantime shows no choices`() = runComposeUiTest {
        val state = TourState(FakeTour().actions)
        state.start()
        runBlocking {
            state.continueFromWelcome()
            state.confirmEnd()
        }
        showDialog(state, hooks(found = "/usr/local/bin/shogun"))

        onNodeWithText(EnglishStrings.tourLater).assertIsDisplayed()
        onNodeWithText("Agent choices for ShogunAi tour").assertDoesNotExist()
    }

    @Test
    fun `the banner leaves errors to the dialog while it is open`() = runComposeUiTest {
        val tour = FakeTour(existing = tourProject, endResult = Result.failure(IllegalStateException("tour folder locked")))
        val state = TourState(tour.actions).also { it.start() }
        runBlocking { state.restart() }
        setContent {
            CompositionLocalProvider(LocalStrings provides EnglishStrings) {
                MaterialTheme { Column { TourBanner(state, tourProject, onEnded = {}, agentChoices = { _, _ -> }) } }
            }
        }

        onNodeWithText("tour folder locked", substring = true).assertDoesNotExist()
    }

    @Test
    fun `the banner shows an ending that failed once the dialog is closed`() = runComposeUiTest {
        val tour = FakeTour(existing = tourProject, endResult = Result.failure(IllegalStateException("tour folder locked")))
        val state = TourState(tour.actions)
        runBlocking { state.confirmEnd() }
        setContent {
            CompositionLocalProvider(LocalStrings provides EnglishStrings) {
                MaterialTheme { Column { TourBanner(state, tourProject, onEnded = {}, agentChoices = { _, _ -> }) } }
            }
        }

        onNodeWithContentDescription(EnglishStrings.closeError).performClick()

        assertNull(state.error)
    }
}
