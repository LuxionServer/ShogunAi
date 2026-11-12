package app.luxion.shogunai.ui.machine

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import app.luxion.shogunai.domain.model.DeviceConnection
import app.luxion.shogunai.domain.model.DeviceUsability
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.toLastSeenText
import app.luxion.shogunai.domain.model.toLocalClockTime
import app.luxion.shogunai.ui.i18n.EnglishStrings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class PhysicalDevicesViewUiTest {

    private val fixture = PhysicalDevicesFixture()
    private val project = Project(
        id = "api",
        name = "api",
        config = ProjectConfig(baseRepositoryPath = "/home/dev/projects/api", worktreesRoot = "/home/dev/projects", secretFiles = emptyList()),
    )

    private fun machine() = MachineResources(
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        useCases = fixture.unusedMachineUseCases(),
        projects = { listOf(MachineProject(project, listOf(Worktree(fixture.holderPath, "feature/TASK-1"), Worktree(fixture.waiterPath, "feature/TASK-2")), emptySet())) },
        pathExists = { true },
        physicalDevices = fixture.watch,
    )

    @Test
    fun `the status bar and overview show Devices with how many are connected and held`() {
        runBlocking { fixture.watch.refresh() }
        val machine = machine()

        val item = machineStatusItems(machine.visibleResources, machine.summaries, emptyMap(), EnglishStrings).single()

        assertEquals(MachineResource.PHYSICAL_DEVICES, item.resource)
        assertEquals("1 connected · 1 held", item.summary)
        assertEquals("1", item.figure)
        assertTrue(MachineResource.PHYSICAL_DEVICES in machine.overviewResources)
        assertTrue(MachineResource.PHYSICAL_DEVICES in machine.overviewSummaries)
    }

    @Test
    fun `an empty inventory shows no Devices entry`() {
        fixture.shouldRead = false
        runBlocking { fixture.watch.refresh() }
        val machine = machine()

        assertFalse(MachineResource.PHYSICAL_DEVICES in machine.visibleResources)
        assertFalse(MachineResource.PHYSICAL_DEVICES in machine.overviewResources)
        assertFalse(MachineResource.PHYSICAL_DEVICES in machine.summaries)
    }

    @Test
    fun `the view lists each device with its holder, its last sighting and the waiters, and opens the holder`() = runComposeUiTest {
        runBlocking { fixture.watch.refresh() }
        val opened = mutableListOf<Pair<String, String>>()
        setContent { MaterialTheme { MachineView(MachineResource.PHYSICAL_DEVICES, machine()) { projectId, path -> opened += projectId to path } } }

        onNodeWithText("Pixel 8").assertIsDisplayed()
        onNodeWithText("Lease until ${fixture.expiresAt.toLocalClockTime()}").assertIsDisplayed()
        onNodeWithText("Last seen ${fixture.lastSeen.toLastSeenText()}").assertIsDisplayed()
        onNodeWithText("Waiting for an Android device").assertIsDisplayed()
        onNodeWithText("api · api-TASK-1").performClick()

        assertEquals(listOf("api" to fixture.holderPath), opened)
    }

    @Test
    fun `withdrawing the free device marks it withdrawn`() = runComposeUiTest {
        runBlocking { fixture.watch.refresh() }
        setContent { MaterialTheme { PhysicalDevicesView(machine()) { _, _ -> } } }

        onAllNodesWithText("Withdraw")[1].performClick()
        waitUntil { onAllNodesWithText("Withdrawn").fetchSemanticsNodes().isNotEmpty() }

        assertEquals(listOf(fixture.a55.id), fixture.withdrawn)
        onNodeWithText("Lend").performClick()
        waitUntil { fixture.lent.isNotEmpty() }
    }

    @Test
    fun `withdrawing the held device asks naming its holder`() = runComposeUiTest {
        runBlocking { fixture.watch.refresh() }
        setContent { MaterialTheme { PhysicalDevicesView(machine()) { _, _ -> } } }

        onAllNodesWithText("Withdraw")[0].performClick()
        onNodeWithText("api · api-TASK-1 holds Pixel 8. Withdrawing it releases it, and no agent can acquire it until you lend it again.").assertIsDisplayed()
        onAllNodesWithText("Withdraw")[2].performClick()
        waitUntil { fixture.withdrawn.isNotEmpty() }
    }

    @Test
    fun `release asks naming the holder and the view shows the device free`() = runComposeUiTest {
        runBlocking { fixture.watch.refresh() }
        setContent { MaterialTheme { PhysicalDevicesView(machine()) { _, _ -> } } }

        onNodeWithText("Release").performClick()
        onNodeWithText("api · api-TASK-1 will no longer hold Pixel 8, and the next worktree waiting for one can acquire it. Nothing changes on the device.").assertIsDisplayed()
        onAllNodesWithText("Release")[1].performClick()
        waitUntil { onAllNodesWithText("Free").fetchSemanticsNodes().size == 2 }

        assertEquals(listOf(fixture.pixel.id), fixture.released)
    }

    @Test
    fun `forget is offered only for a disconnected device and asks first`() = runComposeUiTest {
        runBlocking { fixture.watch.refresh() }
        setContent { MaterialTheme { PhysicalDevicesView(machine()) { _, _ -> } } }

        assertEquals(1, onAllNodesWithText("Forget").fetchSemanticsNodes().size)
        onNodeWithText("Forget").performClick()
        onNodeWithText("Forget the device?").assertIsDisplayed()
        onAllNodesWithText("Forget")[1].performClick()
        waitUntil { fixture.forgotten.isNotEmpty() }
    }

    @Test
    fun `rename edits the alias`() = runComposeUiTest {
        runBlocking { fixture.watch.refresh() }
        setContent { MaterialTheme { PhysicalDevicesView(machine()) { _, _ -> } } }

        onAllNodesWithText("Rename")[1].performClick()
        onNodeWithTag("device-alias", useUnmergedTree = true).performTextReplacement("phone")
        onNodeWithText("Save").performClick()
        waitUntil { fixture.renamed.isNotEmpty() }

        assertEquals(listOf(fixture.a55.id to "phone"), fixture.renamed)
    }

    @Test
    fun `a device plugged in while the view is shown appears as connected`() = runComposeUiTest {
        runBlocking { fixture.watch.refresh() }
        setContent { MaterialTheme { PhysicalDevicesView(machine()) { _, _ -> } } }
        onNodeWithText("Last seen ${fixture.lastSeen.toLastSeenText()}").assertIsDisplayed()

        val connected = app.luxion.shogunai.domain.model.PhysicalDevice(
            app.luxion.shogunai.domain.model.DevicePlatform.ANDROID, "R5CT10ABCDE", "192.168.1.20:37000", DeviceConnection.NETWORK,
        )
        fixture.state = fixture.state.copy(devices = fixture.state.devices.map { if (it.device.id == fixture.a55.id) it.copy(connected = connected) else it })
        runBlocking { fixture.watch.refresh() }

        waitUntil { onAllNodesWithText("Network").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(0, onAllNodesWithText("Forget").fetchSemanticsNodes().size)
    }

    @Test
    fun `an unusable device says what to do on it`() = runComposeUiTest {
        val unusable = app.luxion.shogunai.domain.model.PhysicalDevice(
            app.luxion.shogunai.domain.model.DevicePlatform.ANDROID, "R5CT10ABCDE", "R5CT10ABCDE", DeviceConnection.USB, DeviceUsability.UNAUTHORIZED,
        )
        fixture.state = fixture.state.copy(devices = fixture.state.devices.map { if (it.device.id == fixture.a55.id) it.copy(connected = unusable) else it })
        runBlocking { fixture.watch.refresh() }
        setContent { MaterialTheme { PhysicalDevicesView(machine()) { _, _ -> } } }

        onNodeWithText("Allow USB debugging on the device").assertIsDisplayed()
    }

    @Test
    fun `every usability has a label`() {
        assertEquals(
            listOf("Allow USB debugging on the device", "Offline", "Connecting", "Not paired with this Mac", "Turn on developer mode", "Connecting"),
            listOf(DeviceUsability.UNAUTHORIZED, DeviceUsability.OFFLINE, DeviceUsability.CONNECTING, DeviceUsability.UNPAIRED, DeviceUsability.DEVELOPER_MODE_OFF, DeviceUsability.USABLE)
                .map { usabilityLabel(it, EnglishStrings) },
        )
    }
}
