package app.luxion.shogunai.ui.shell

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
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
import app.luxion.shogunai.domain.usecase.CommandLineToolState
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
            existing = null
            leftover = false
            Result.success(Unit)
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
    fun `with the tool available the welcome goes straight to creating the sample`() = runBlocking {
        val tour = FakeTour(toolAvailable = true)
        val state = TourState(tour.actions)

        state.start()
        state.continueFromWelcome()

        assertEquals(1, tour.created)
        assertEquals(TourStep.OPEN_AGENT, state.step)
        assertEquals(tourProject, state.project)
    }

    @Test
    fun `with the tool missing the tool step comes before creating`() = runBlocking {
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
    fun `an existing tour offers to restart, which ends it and creates it again`() = runBlocking {
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
    fun `a failed creation stays on the step with its error and can be retried`() = runBlocking {
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
    fun `ending the tour forgets its project`() = runBlocking {
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
    fun `the notice starts the tour and doesn't return once dismissed`() = runComposeUiTest {
        val tour = FakeTour()
        val state = TourState(tour.actions)
        setContent {
            CompositionLocalProvider(LocalStrings provides EnglishStrings) {
                MaterialTheme { Column { TourNotice(state, hasProjects = true) } }
            }
        }

        onNodeWithText(EnglishStrings.tourNoticeMessage).assertIsDisplayed()
        onNodeWithText(EnglishStrings.claudeHooksNoticeDismiss).performClick()
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
                MaterialTheme { EmptyState(onAddProject = {}, onTakeTour = { tours++ }) }
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
}
