package app.luxion.shogunai.ui.shell

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.test.v2.runComposeUiTest
import app.luxion.shogunai.domain.model.MultirepoUnavailability
import app.luxion.shogunai.domain.model.UnavailableMultirepo
import app.luxion.shogunai.ui.i18n.EnglishStrings
import app.luxion.shogunai.ui.i18n.SpanishStrings
import app.luxion.shogunai.ui.multirepo.RemoveMultirepoDialog
import app.luxion.shogunai.ui.multirepo.UnavailableMultirepoView
import app.luxion.shogunai.ui.multirepo.unavailabilityOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class UnavailableMultirepoUiTest {

    private val folder = "/Volumes/External/main-multirepo"
    private val unreachable = UnavailableMultirepo(folder, MultirepoUnavailability.FolderUnreachable)
    private val strings = EnglishStrings

    @Composable
    private fun SidebarWith(rail: Boolean, onSelect: (String) -> Unit = {}, onRemove: (String) -> Unit = {}, onEdit: (String) -> Unit = {}) {
        Sidebar(
            projects = emptyList(),
            selectedProjectId = null,
            isActiveSelected = false,
            isAllSelected = false,
            waitingCount = 0,
            totalWorktreeCount = 0,
            activityOf = { ProjectActivity.NONE },
            rail = rail,
            onSelectActive = {},
            onSelectAll = {},
            onSelectProject = {},
            onEditProject = {},
            onRemoveProject = {},
            onAddProject = {},
            onToggle = {},
            multirepos = listOf(unavailableSidebarFolder(unreachable)),
            selectedMultirepo = folder,
            onSelectMultirepo = onSelect,
            onEditMultirepo = onEdit,
            onRemoveMultirepo = onRemove,
        )
    }

    @Test
    fun `the sidebar marks an unavailable multirepo and offers to remove it instead of its settings`() = runComposeUiTest {
        val selected = mutableListOf<String>()
        val removed = mutableListOf<String>()
        val edited = mutableListOf<String>()
        setContent { MaterialTheme { SidebarWith(rail = false, onSelect = { selected += it }, onRemove = { removed += it }, onEdit = { edited += it }) } }

        onNodeWithTag("multirepo-unavailable").assertExists()
        onNodeWithContentDescription(strings.multirepoUnavailable).assertExists()
        assertTrue(onAllNodesWithTag("multirepo-toggle").fetchSemanticsNodes().isEmpty())
        onNodeWithText("main-multirepo").performClick()
        onNodeWithText("main-multirepo").performMouseInput { rightClick() }
        assertTrue(onAllNodesWithText(strings.multirepoSettingsTitle).fetchSemanticsNodes().isEmpty())
        onNodeWithText(strings.remove).performClick()

        assertEquals(listOf(folder), selected)
        assertEquals(listOf(folder), removed)
        assertTrue(edited.isEmpty())
    }

    @Test
    fun `the rail shows an unavailable multirepo with its name`() = runComposeUiTest {
        val selected = mutableListOf<String>()
        setContent { MaterialTheme { SidebarWith(rail = true, onSelect = { selected += it }) } }

        onNodeWithContentDescription("main-multirepo").performClick()

        assertEquals(listOf(folder), selected)
    }

    @Test
    fun `the view explains why and offers to try again or remove`() = runComposeUiTest {
        var tries = 0
        var removals = 0
        setContent { MaterialTheme { UnavailableMultirepoView(unreachable, onTryAgain = { tries++ }, onRemove = { removals++ }) } }

        onNodeWithText(strings.multirepoFolderUnreachable(folder)).assertExists()
        onNodeWithText("main-multirepo · ${strings.multirepoUnavailable}").assertExists()
        onNodeWithText(strings.tryAgain).performClick()
        onNodeWithText(strings.remove).performClick()

        assertEquals(listOf(1, 1), listOf(tries, removals))
    }

    @Test
    fun `the removal confirmation says what is forgotten and can be confirmed or cancelled`() = runComposeUiTest {
        var confirmed = 0
        var dismissed = 0
        setContent { MaterialTheme { RemoveMultirepoDialog(unreachable, onConfirm = { confirmed++ }, onDismiss = { dismissed++ }) } }

        onNodeWithText(strings.removeMultirepoTitle).assertExists()
        onNodeWithText(strings.removeMultirepoMessage("main-multirepo")).assertExists()
        onNodeWithText(strings.remove).performClick()
        onNodeWithText(strings.cancel).performClick()

        assertEquals(listOf(1, 1), listOf(confirmed, dismissed))
    }

    @Test
    fun `every reason is described in English and Spanish`() {
        listOf(EnglishStrings, SpanishStrings).forEach { language ->
            assertTrue(language.unavailabilityOf(folder, MultirepoUnavailability.FolderUnreachable).contains(folder))
            assertTrue(language.unavailabilityOf(folder, MultirepoUnavailability.DeclarationMissing).contains(".shogunai-multirepo.json"))
            assertTrue(language.unavailabilityOf(folder, MultirepoUnavailability.InvalidDeclaration("unexpected end")).contains("unexpected end"))
            assertTrue(language.removeMultirepoMessage("main-multirepo").contains("main-multirepo"))
            assertTrue(language.removeMultirepoTitle.isNotBlank())
            assertTrue(language.multirepoUnavailable.isNotBlank())
        }
    }
}
