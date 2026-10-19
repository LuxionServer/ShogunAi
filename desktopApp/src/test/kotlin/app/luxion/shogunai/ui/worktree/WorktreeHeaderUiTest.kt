package app.luxion.shogunai.ui.worktree

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import app.luxion.shogunai.domain.model.AgentLaunchConfig
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.i18n.SpanishStrings
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class WorktreeHeaderUiTest {

    private val config = ProjectConfig(
        baseRepositoryPath = "~/projects/main-repo",
        worktreesRoot = "~/projects",
        secretFiles = emptyList(),
        agentLaunchConfig = AgentLaunchConfig(),
    )
    private val projects = listOf(
        Project(id = "api", name = "api", config = config),
        Project(id = "main-repo", name = "main-repo", config = config),
    )

    @Test
    fun `the project filter reports the picked project and all projects`() = runComposeUiTest {
        val picks = mutableListOf<String?>()
        var selectedId by mutableStateOf<String?>(null)
        setContent {
            MaterialTheme {
                ViewProjectFilter(projects, selectedId) { id ->
                    picks += id
                    selectedId = id
                }
            }
        }

        onNodeWithText("All projects").performClick()
        onNodeWithText("api").performClick()
        onNodeWithText("api").performClick()
        onNodeWithText("All projects").performClick()

        assertEquals(listOf<String?>("api", null), picks)
    }

    @Test
    fun `a state group header shows its hint next to its title`() = runComposeUiTest {
        setContent {
            MaterialTheme { StateGroupHeader(StateGroupKind.NEEDS_YOU, count = 2, canCleanUp = false, onCleanUp = {}) }
        }

        onNodeWithText("Needs you").assertIsDisplayed()
        onNodeWithText("uncommitted or unpushed").assertIsDisplayed()
    }

    @Test
    fun `the hint is translated`() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalStrings provides SpanishStrings) {
                MaterialTheme { StateGroupHeader(StateGroupKind.NEEDS_YOU, count = 2, canCleanUp = false, onCleanUp = {}) }
            }
        }

        onNodeWithText("Te necesitan").assertIsDisplayed()
        onNodeWithText("sin commit o sin subir").assertIsDisplayed()
    }

    @Test
    fun `hovering a state group title shows its full criterion`() = runComposeUiTest {
        setContent {
            MaterialTheme { StateGroupHeader(StateGroupKind.INACTIVE, count = 1, canCleanUp = false, onCleanUp = {}) }
        }

        onNodeWithText("Inactive").performMouseInput { moveTo(center) }
        mainClock.advanceTimeBy(2_000)

        onNodeWithText("Clean, no agent and no commit in the last 14 days.").assertIsDisplayed()
    }
}
