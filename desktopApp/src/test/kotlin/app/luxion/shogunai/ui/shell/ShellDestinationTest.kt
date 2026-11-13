package app.luxion.shogunai.ui.shell

import app.luxion.shogunai.ui.machine.MachineResource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShellDestinationTest {

    private val folder = "/home/dev/projects/product"
    private val views = listOf(
        ShellDestination.Active,
        ShellDestination.All(),
        ShellDestination.All(projectFilter = "api"),
        ShellDestination.MachineOverview,
        ShellDestination.Machine(MachineResource.IOS_SIMULATORS),
        ShellDestination.MultirepoView(folder),
    )
    private val forms = listOf(
        ShellDestination.ProjectSettings("api"),
        ShellDestination.AddProject(),
        ShellDestination.MultirepoSettings(folder),
    )

    @Test
    fun `every view is restored from the key it is saved with`() {
        views.forEach { assertEquals(it, ShellDestination.fromStorageKey(it.storageKey())) }
    }

    @Test
    fun `forms are not saved`() {
        forms.forEach {
            assertTrue(it.isForm)
            assertNull(it.storageKey())
        }
        views.forEach { assertFalse(it.isForm) }
    }

    @Test
    fun `only the views of a project and its settings reference it`() {
        assertEquals(
            listOf(null, null, "api", null, null, null, "api", null, null),
            (views + forms).map { it.referencedProjectId },
        )
    }

    @Test
    fun `only a multirepo's view and settings reference it`() {
        assertEquals(
            listOf(null, null, null, null, null, folder, null, null, folder),
            (views + forms).map { it.referencedMultirepo },
        )
    }

    @Test
    fun `keys without a name, unknown resources and unknown keys restore nothing`() {
        listOf(null, "project:", "multirepo:", "machine:NOT_A_RESOURCE", "settings:api").forEach {
            assertNull(ShellDestination.fromStorageKey(it))
        }
    }
}
