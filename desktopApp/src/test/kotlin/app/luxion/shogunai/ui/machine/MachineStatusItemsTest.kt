package app.luxion.shogunai.ui.machine

import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.ui.i18n.EnglishStrings
import app.luxion.shogunai.ui.i18n.SpanishStrings
import app.luxion.shogunai.ui.theme.Status
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MachineStatusItemsTest {

    private val gigabyte = 1_073_741_824L

    private fun items(
        resources: Collection<MachineResource>,
        summaries: Map<MachineResource, MachineSummary> = emptyMap(),
        failures: Map<MachineResource, Throwable> = emptyMap(),
    ) = machineStatusItems(resources, summaries, failures, EnglishStrings)

    @Test
    fun `only the resources that apply are shown, with the desktop turn first`() {
        val shown = items(
            listOf(MachineResource.ANDROID_EMULATORS, MachineResource.GRADLE_DAEMONS, MachineResource.DESKTOP_TURN),
        )

        assertEquals(
            listOf(MachineResource.DESKTOP_TURN, MachineResource.GRADLE_DAEMONS, MachineResource.ANDROID_EMULATORS),
            shown.map { it.resource },
        )
    }

    @Test
    fun `nothing applies, nothing is shown`() {
        assertTrue(items(emptyList()).isEmpty())
    }

    @Test
    fun `a pool within its limit shows its usage without attention`() {
        val item = items(
            listOf(MachineResource.ANDROID_EMULATORS),
            mapOf(MachineResource.ANDROID_EMULATORS to MachineSummary(1, poolInUse = 1, poolLimit = 2)),
        ).single()

        assertEquals("Emulators", item.name)
        assertEquals("1/2", item.figure)
        assertNull(item.attention)
    }

    @Test
    fun `a pool over its limit warns and says so in the tooltip`() {
        val item = items(
            listOf(MachineResource.GRADLE_DAEMONS),
            mapOf(MachineResource.GRADLE_DAEMONS to MachineSummary(10, 1_288_490_189L, poolInUse = 9, poolLimit = 3)),
        ).single()

        assertEquals("9/3", item.figure)
        assertEquals(Status.WARNING, item.attention)
        assertEquals("Gradle daemons: 10 · 1.2 GB · 9/3 · over the limit", item.tooltip)
    }

    @Test
    fun `a gradle pool held by running builds says so without warning`() {
        val item = items(
            listOf(MachineResource.GRADLE_DAEMONS),
            mapOf(
                MachineResource.GRADLE_DAEMONS to
                    MachineSummary(10, 1_288_490_189L, poolInUse = 9, poolLimit = 3, poolHeldByBuilds = true),
            ),
        ).single()

        assertEquals("9/3", item.figure)
        assertNull(item.attention)
        assertEquals("Gradle daemons: 10 · 1.2 GB · 9/3 · over the limit: builds running", item.tooltip)
    }

    @Test
    fun `without a limit the figure is the count`() {
        val item = items(
            listOf(MachineResource.IOS_SIMULATORS),
            mapOf(MachineResource.IOS_SIMULATORS to MachineSummary(2)),
        ).single()

        assertEquals("2", item.figure)
    }

    @Test
    fun `DerivedData shows its usage against the quota and warns over it`() {
        val within = items(
            listOf(MachineResource.XCODE_DERIVED_DATA),
            mapOf(MachineResource.XCODE_DERIVED_DATA to MachineSummary(19, bytes = gigabyte, quotaBytes = 20 * gigabyte)),
        ).single()
        val over = items(
            listOf(MachineResource.XCODE_DERIVED_DATA),
            mapOf(MachineResource.XCODE_DERIVED_DATA to MachineSummary(19, bytes = 21 * gigabyte, quotaBytes = 20 * gigabyte)),
        ).single()

        assertEquals("1.0 GB / 20.0 GB", within.figure)
        assertNull(within.attention)
        assertEquals(Status.WARNING, over.attention)
        assertTrue(over.tooltip.endsWith("over the quota"))
    }

    @Test
    fun `a resource not measured yet has no figure and says so`() {
        val calculating = items(
            listOf(MachineResource.XCODE_DERIVED_DATA),
            mapOf(MachineResource.XCODE_DERIVED_DATA to MachineSummary(19, calculating = true)),
        ).single()
        val unread = items(listOf(MachineResource.COMPOSE_PROJECTS)).single()

        assertNull(calculating.figure)
        assertEquals("Xcode DerivedData: not measured yet", calculating.tooltip)
        assertNull(unread.figure)
        assertNull(unread.attention)
    }

    @Test
    fun `a failed read is shown in danger with its reason and the last figure`() {
        val failure = WorktreeError.ProcessListingFailed(listOf("jps", "-l"), 1)
        val item = items(
            listOf(MachineResource.GRADLE_DAEMONS),
            mapOf(MachineResource.GRADLE_DAEMONS to MachineSummary(2, 0)),
            mapOf(MachineResource.GRADLE_DAEMONS to failure),
        ).single()

        assertEquals(Status.DANGER, item.attention)
        assertEquals("2", item.figure)
        assertTrue(item.tooltip.startsWith("Gradle daemons: 2 · 0 B · "))
    }

    @Test
    fun `docker not running is neutral with its reason and the last figure`() {
        val failure = WorktreeError.DockerUnavailable("failed to connect to the docker API at unix:///home/dev/.docker/run/docker.sock")
        val item = items(
            listOf(MachineResource.COMPOSE_PROJECTS),
            mapOf(MachineResource.COMPOSE_PROJECTS to MachineSummary(2)),
            mapOf(MachineResource.COMPOSE_PROJECTS to failure),
        ).single()

        assertNull(item.attention)
        assertEquals("2", item.figure)
        assertEquals("Compose projects: 2 projects · Docker isn't available. Start Docker and try again.", item.tooltip)
    }

    @Test
    fun `a missing android sdk is neutral with a dash and its reason`() {
        val item = items(
            listOf(MachineResource.ANDROID_EMULATORS),
            failures = mapOf(MachineResource.ANDROID_EMULATORS to WorktreeError.AndroidSdkNotFound(listOf("ANDROID_HOME (unset)"))),
        ).single()

        assertNull(item.attention)
        assertNull(item.figure)
        assertTrue(item.tooltip.startsWith("Android emulators: "))
        assertTrue(item.summary.isNotBlank())
    }

    @Test
    fun `only the tools a machine may lack count as missing`() {
        assertTrue(WorktreeError.AndroidSdkNotFound(emptyList()).isMissingTool())
        assertTrue(WorktreeError.DockerUnavailable(null).isMissingTool())
        assertTrue(WorktreeError.IosSimulatorsNotSupported.isMissingTool())
        assertTrue(!WorktreeError.ProcessListingFailed(listOf("jps"), 1).isMissingTool())
        assertTrue(!IllegalStateException("boom").isMissingTool())
    }

    @Test
    fun `a free desktop says nobody holds it`() {
        val item = items(listOf(MachineResource.DESKTOP_TURN), mapOf(MachineResource.DESKTOP_TURN to MachineSummary(0))).single()

        assertEquals("Desktop: Nobody holds the desktop.", item.tooltip)
    }

    @Test
    fun `the desktop turn names the holder and the waiters`() {
        val item = items(
            listOf(MachineResource.DESKTOP_TURN),
            mapOf(MachineResource.DESKTOP_TURN to MachineSummary(2, holder = "TASK-1")),
        ).single()

        assertEquals("TASK-1 · 2 waiting", item.figure)
    }

    @Test
    fun `the status bar strings are translated`() {
        assertNotEquals(EnglishStrings.machineStatusEmulators, SpanishStrings.machineStatusEmulators)
        assertNotEquals(EnglishStrings.machineStatusSimulators, SpanishStrings.machineStatusSimulators)
        assertNotEquals(EnglishStrings.machineStatusDesktop, SpanishStrings.machineStatusDesktop)
        assertNotEquals(EnglishStrings.notMeasuredYet, SpanishStrings.notMeasuredYet)
        assertNotEquals(EnglishStrings.overQuota, SpanishStrings.overQuota)
    }
}
