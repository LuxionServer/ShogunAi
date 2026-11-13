package app.luxion.shogunai.ui.machine

import app.luxion.shogunai.domain.model.DeviceLossReason
import app.luxion.shogunai.domain.model.DevicePlatform
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PhysicalDevicesWatchTest {

    private val fixture = PhysicalDevicesFixture()
    private val watch = fixture.watch

    @Test
    fun `nothing is read while no device is recorded and no project declares a mobile provider`() = runTest {
        fixture.shouldRead = false

        watch.refresh()

        assertEquals(0, fixture.reads)
        assertFalse(watch.isActive)
        assertEquals(emptyList(), watch.heldBy(fixture.holderPath))
    }

    @Test
    fun `a refresh follows changes made outside the app`() = runTest {
        watch.refresh()
        assertEquals(1, watch.heldCount)
        assertEquals(1, watch.connectedCount)

        fixture.withoutHolder()
        watch.refresh()

        assertEquals(0, watch.heldCount)
        assertTrue(watch.isActive)
    }

    @Test
    fun `a stopped reading leaves nothing behind`() = runTest {
        watch.refresh()
        fixture.shouldRead = false

        watch.refresh()

        assertNull(watch.state)
    }

    @Test
    fun `the worktree's devices, waits and losses are found by path`() = runTest {
        fixture.state = fixture.state.copy(losses = listOf(fixture.loss(DeviceLossReason.TAKEN)))
        watch.refresh()

        assertEquals(listOf(fixture.pixel.id), watch.heldBy("${fixture.holderPath}/").map { it.device.id })
        assertEquals(listOf(DeviceWaitPlace(DevicePlatform.ANDROID, 1, fixture.ticket)), watch.waitsOf(fixture.waiterPath))
        assertEquals(listOf(fixture.loss(DeviceLossReason.TAKEN)), watch.lossesOf(fixture.waiterPath))
        assertEquals(emptyList(), watch.lossesOf(fixture.holderPath))
        assertEquals(listOf(fixture.ticket), watch.waiting(DevicePlatform.ANDROID))
    }

    @Test
    fun `a loss is no longer shown once the worktree holds a device of that platform`() = runTest {
        fixture.state = fixture.state.copy(losses = listOf(fixture.loss(DeviceLossReason.WITHDRAWN).copy(worktreePath = fixture.holderPath)))
        watch.refresh()

        assertEquals(emptyList(), watch.lossesOf(fixture.holderPath))
    }

    @Test
    fun `release, withdraw and forget wait for a confirmation`() = runTest {
        watch.refresh()
        val held = watch.devices.first()

        watch.request(PendingDeviceAction.Release(held))
        watch.cancel()
        assertNull(watch.pending)
        watch.request(PendingDeviceAction.Release(held))
        watch.confirm()

        assertEquals(listOf(fixture.pixel.id), fixture.released)
        assertNull(watch.devices.first().holder)
        assertNull(watch.busyDevice)
    }

    @Test
    fun `withdrawing a held device asks first and a free one is withdrawn at once`() = runTest {
        watch.refresh()

        watch.withdraw(watch.devices.first())
        assertIs<PendingDeviceAction.Withdraw>(watch.pending)
        assertEquals(emptyList(), fixture.withdrawn)
        watch.confirm()
        watch.withdraw(watch.devices.last())

        assertEquals(listOf(fixture.pixel.id, fixture.a55.id), fixture.withdrawn)
    }

    @Test
    fun `lend, rename and forget go to their actions`() = runTest {
        watch.refresh()
        val a55 = watch.devices.last()

        watch.lend(a55)
        watch.request(PendingDeviceAction.Rename(a55))
        watch.confirm("phone")
        watch.request(PendingDeviceAction.Forget(a55))
        watch.confirm()
        watch.confirm()

        assertEquals(listOf(fixture.a55.id), fixture.lent)
        assertEquals(listOf(fixture.a55.id to "phone"), fixture.renamed)
        assertEquals(listOf(fixture.a55.id), fixture.forgotten)
        assertEquals(1, watch.devices.size)
    }

    @Test
    fun `a failed action is kept until dismissed`() = runTest {
        watch.refresh()

        PhysicalDevicesWatch.NONE.also { none ->
            none.refresh()
            none.lend(watch.devices.last())
            assertTrue(none.error != null)
            none.dismissError()
            assertNull(none.error)
        }
    }
}
