package app.luxion.shogunai.ui.shell

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class SidebarFooterUiTest {

    private fun gearOpensPreferences(rail: Boolean) = runComposeUiTest {
        var opened = 0
        setContent {
            MaterialTheme {
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
                    onOpenPreferences = { opened++ },
                )
            }
        }

        onNodeWithContentDescription("Preferences").assertIsDisplayed().performClick()
        waitForIdle()

        assertEquals(1, opened)
    }

    @Test
    fun `the gear at the bottom of the sidebar opens Preferences`() = gearOpensPreferences(rail = false)

    @Test
    fun `the gear stays at the bottom of the rail`() = gearOpensPreferences(rail = true)
}
