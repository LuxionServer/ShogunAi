package app.luxion.shogunai.ui.shell

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.test.v2.runComposeUiTest
import app.luxion.shogunai.domain.model.MultirepoUnavailability
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.ui.i18n.EnglishStrings
import app.luxion.shogunai.ui.i18n.LocalStrings
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class SidebarUiTest {

    private fun project(name: String) =
        Project(id = name, name = name, config = ProjectConfig("/home/dev/projects/$name", "/home/dev/projects/worktrees", emptyList()))

    private val api = project("api")
    private val mobile = project("mobile")
    private val product = SidebarMultirepo("/home/dev/projects/product", "product", emptyList(), ProjectActivity.NONE, collapsed = false)
    private val lost = SidebarMultirepo(
        "/home/dev/projects/lost", "lost", emptyList(), ProjectActivity.NONE, collapsed = false,
        unavailable = MultirepoUnavailability.FolderUnreachable,
    )
    private val events = mutableListOf<String>()
    private var selected by mutableStateOf("api")

    private fun ComposeUiTest.show(rail: Boolean) = setContent {
        CompositionLocalProvider(LocalStrings provides EnglishStrings) {
            MaterialTheme {
                Sidebar(
                    projects = listOf(api, mobile),
                    selectedProjectId = selected,
                    isActiveSelected = false,
                    isAllSelected = false,
                    waitingCount = 0,
                    totalWorktreeCount = 0,
                    activityOf = { ProjectActivity.NONE },
                    rail = rail,
                    onSelectActive = {},
                    onSelectAll = {},
                    onSelectProject = {
                        events += "select ${it.id}"
                        selected = it.id
                    },
                    onEditProject = {},
                    onRemoveProject = {},
                    onAddProject = {},
                    onToggle = {},
                    multirepos = listOf(product, lost),
                    selectedMultirepo = product.folder,
                    onSelectMultirepo = { events += "open $it" },
                    onEditMultirepo = { events += "edit $it" },
                    onRemoveMultirepo = { events += "remove $it" },
                )
            }
        }
    }

    @Test
    fun `the rail shows each project as a tile that selects it`() = runComposeUiTest {
        show(rail = true)

        onNodeWithText("AP").assertIsDisplayed()
        onNodeWithText("MO").performClick()
        onNodeWithText("AP").performClick()

        assertEquals(listOf("select mobile", "select api"), events)
    }

    @Test
    fun `selecting another project in the list moves the selection to it`() = runComposeUiTest {
        show(rail = false)

        onNodeWithText("mobile").performClick()
        waitForIdle()

        assertEquals("mobile", selected)
    }

    @Test
    fun `the rail shows multirepos as folders, unavailable ones too`() = runComposeUiTest {
        show(rail = true)

        onNodeWithContentDescription("lost").performClick()
        onNodeWithContentDescription("product").performClick()

        assertEquals(listOf("open /home/dev/projects/lost", "open /home/dev/projects/product"), events)
    }

    @Test
    fun `a multirepo's context menu opens its settings`() = runComposeUiTest {
        show(rail = false)

        onNodeWithText("product").performMouseInput { rightClick() }
        onNodeWithText(EnglishStrings.multirepoSettingsTitle).performClick()

        assertEquals(listOf("edit ${product.folder}"), events)
    }

    @Test
    fun `an unavailable multirepo's context menu removes it`() = runComposeUiTest {
        show(rail = false)

        onNodeWithText("lost").performMouseInput { rightClick() }
        onNodeWithText(EnglishStrings.remove).performClick()

        assertEquals(listOf("remove /home/dev/projects/lost"), events)
    }
}
