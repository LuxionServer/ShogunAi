package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeDeviceWaitQueue
import app.luxion.shogunai.domain.FakeEnvironmentStateStore
import app.luxion.shogunai.domain.FakePhysicalDeviceInventoryStore
import app.luxion.shogunai.domain.FakePhysicalDeviceLeaseStore
import app.luxion.shogunai.domain.FakePhysicalDevices
import app.luxion.shogunai.domain.FakeProcessLiveness
import app.luxion.shogunai.domain.io.PhysicalDevices
import app.luxion.shogunai.domain.model.AgentState
import app.luxion.shogunai.domain.model.DeviceBlock
import app.luxion.shogunai.domain.model.DeviceConnection
import app.luxion.shogunai.domain.model.DeviceLeases
import app.luxion.shogunai.domain.model.DeviceLossReason
import app.luxion.shogunai.domain.model.DevicePlatform
import app.luxion.shogunai.domain.model.DeviceRequest
import app.luxion.shogunai.domain.model.DeviceUsability
import app.luxion.shogunai.domain.model.PendingDevice
import app.luxion.shogunai.domain.model.PhysicalDevice
import app.luxion.shogunai.domain.model.PhysicalDeviceLease
import app.luxion.shogunai.domain.model.PhysicalDeviceListing
import app.luxion.shogunai.domain.model.PhysicalDeviceWait
import app.luxion.shogunai.domain.model.PhysicalDeviceWaitProgress
import app.luxion.shogunai.domain.model.ProcessIdentity
import app.luxion.shogunai.domain.model.RecordedDevice
import app.luxion.shogunai.domain.model.WaitQueueName
import app.luxion.shogunai.domain.model.WaitTicket
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.coroutines.test.runTest
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PhysicalDeviceUseCasesTest {

    private val common = "/home/dev/projects/main-repo/.git"
    private fun worktree(id: String) = "/home/dev/projects/worktrees/$id"
    private fun admin(id: String) = "$common/worktrees/$id"

    private var now: Instant = Instant.parse("2026-01-05T14:00:00Z")
    private val onSleep = mutableListOf<() -> Unit>()
    private val pauses = mutableListOf<Duration>()
    private val devices = FakePhysicalDevices()
    private val inventory = FakePhysicalDeviceInventoryStore()
    private val leases = FakePhysicalDeviceLeaseStore()
    private val queue = FakeDeviceWaitQueue()
    private val liveness = FakeProcessLiveness()
    private val agents = mutableMapOf<String, AgentState>()
    private val ledger = PhysicalDeviceLedger(
        sources = listOf(devices, PhysicalDevices { error("adb failed") }),
        inventory = inventory,
        leases = leases,
        stateStore = FakeEnvironmentStateStore(),
        waitQueue = queue,
        liveness = liveness,
        adminDirectoryOf = { path -> if (path.endsWith("/outside")) error("not a repository") else admin(path.substringAfterLast('/')) },
        leaseDuration = { Duration.ofMinutes(20) },
        agentActivity = { agents[it] },
        clock = { now },
        sleep = { pause ->
            pauses += pause
            now += pause
            onSleep.removeFirstOrNull()?.invoke()
        },
    )
    private val read = ReadPhysicalDevicesUseCase(ledger)
    private val acquire = AcquirePhysicalDeviceUseCase(ledger)
    private val release = ReleasePhysicalDeviceUseCase(ledger)
    private val manage = ManagePhysicalDevicesUseCase(ledger)

    private val pixel = PhysicalDevice(
        DevicePlatform.ANDROID, "3A281FDJH000AB", "3A281FDJH000AB", DeviceConnection.USB,
        name = "Pixel 8", manufacturer = "Google", model = "Pixel 8", marketingName = "Pixel 8", osVersion = "16",
    )
    private val a55 = PhysicalDevice(
        DevicePlatform.ANDROID, "R5CT10ABCDE", "R5CT10ABCDE", DeviceConnection.USB,
        name = "Galaxy A55 5G", manufacturer = "samsung", model = "SM-A556B", marketingName = "Galaxy A55 5G", osVersion = "15",
    )
    private val iphone = PhysicalDevice(
        DevicePlatform.IOS, "00008150-000A1B2C3D4E5F60", "00008150-000A1B2C3D4E5F60", DeviceConnection.USB,
        name = "Test iPhone", manufacturer = "Apple", model = "iPhone18,3", marketingName = "iPhone 17", osVersion = "26.6",
    )

    private fun connect(vararg connected: PhysicalDevice) {
        devices.connected = connected.toList()
    }

    private fun recorded(device: PhysicalDevice, alias: String? = null, lent: Boolean = true, lastSeen: Instant = now) =
        RecordedDevice(device.id, device.platform, alias, device.name, device.manufacturer, device.model, device.marketingName, device.osVersion, lent, lastSeen.toEpochMilli())

    private fun holding(id: String, device: PhysicalDevice, acquiredAgo: Duration, renewedAgo: Duration = acquiredAgo) {
        leases.leases = leases.leases.copy(
            leases = leases.leases.leases + PhysicalDeviceLease(
                device.id, device.platform, admin(id), worktree(id), (now - acquiredAgo).toEpochMilli(), (now - renewedAgo).toEpochMilli(),
            ),
        )
    }

    private fun waiter(id: String, request: DeviceRequest?, queueName: WaitQueueName = WaitQueueName.ANDROID_DEVICE, process: ProcessIdentity = ProcessIdentity(3000, 1)) =
        WaitTicket("ticket-$id", admin(id), worktree(id), process, now.toEpochMilli(), request?.encode()).also {
            queue.tickets[queueName] = queue.tickets[queueName].orEmpty() + it
        }

    private val android = DeviceRequest(DevicePlatform.ANDROID)

    @Test
    fun `a device seen for the first time is recorded as lent with the current time`() = runTest {
        connect(a55)

        val state = read().getOrThrow()

        assertEquals(listOf(recorded(a55)), inventory.devices)
        assertEquals(a55, state.devices.single().connected)
        assertNull(state.devices.single().holder)
    }

    @Test
    fun `a disconnected device stays recorded with when it was last seen`() = runTest {
        connect(a55)
        read()
        val seen = now
        now += Duration.ofDays(3)
        connect()

        val state = read().getOrThrow()

        assertEquals(seen.toEpochMilli(), inventory.devices.single().lastSeen)
        assertNull(state.devices.single().connected)
    }

    @Test
    fun `a device seen again records its new version and nothing is written when nothing changed`() = runTest {
        inventory.devices = listOf(recorded(iphone, alias = "phone"))
        connect(iphone.copy(osVersion = "27.0", name = null, manufacturer = null, model = null, marketingName = null))
        read()
        assertEquals("27.0", inventory.devices.single().osVersion)
        assertEquals("Test iPhone", inventory.devices.single().name)
        assertEquals("phone", inventory.devices.single().alias)
        assertEquals("iPhone 17", inventory.devices.single().marketingName)
        connect(iphone.copy(osVersion = null))
        read()
        assertEquals("27.0", inventory.devices.single().osVersion)
        val writes = inventory.writes

        read()

        assertEquals(writes, inventory.writes)
    }

    @Test
    fun `pending devices are reported and devices listed twice are recorded once`() = runTest {
        val pending = PendingDevice(DevicePlatform.ANDROID, "0A1B2C3D4E", DeviceUsability.UNAUTHORIZED)
        devices.listing = PhysicalDeviceListing(listOf(a55, a55.copy(transport = "192.168.1.20:37000")), listOf(pending))

        val state = read().getOrThrow()

        assertEquals(1, inventory.devices.size)
        assertEquals(listOf(pending), state.pending)
    }

    @Test
    fun `a new device is lent and can be acquired`() = runTest {
        connect(pixel)

        val acquisition = acquire(worktree("TASK-1"), android).getOrThrow()

        assertEquals(pixel.id, acquisition.held.device.id)
        assertEquals(pixel, acquisition.held.connected)
        assertFalse(acquisition.renewed)
        assertNull(acquisition.takenFrom)
        assertNull(acquisition.waitedSeconds)
        assertEquals(now + Duration.ofMinutes(20), acquisition.held.expiresAt)
        assertEquals(admin("TASK-1"), leases.leases.leases.single().adminDirectory)
    }

    @Test
    fun `a withdrawn device can't be acquired and lending it again makes it acquirable`() = runTest {
        connect(a55)
        read()
        manage.rename("R5CT10ABCDE", "a55").getOrThrow()
        val withdrawn = manage.withdraw("a55").getOrThrow()
        assertFalse(withdrawn.lent)

        val error = assertFailsWith<WorktreeError.PhysicalDeviceUnavailable> { acquire(worktree("TASK-1"), DeviceRequest(name = "a55")).getOrThrow() }

        assertEquals(listOf(DeviceBlock.Withdrawn(inventory.devices.single())), error.blocks)
        assertTrue("isn't lent to agents" in error.message)
        assertTrue(manage.lend("a55").getOrThrow().lent)
        assertTrue(acquire(worktree("TASK-1"), DeviceRequest(name = "a55")).isSuccess)
    }

    @Test
    fun `withdrawing a held device releases it and records why`() = runTest {
        connect(a55)
        inventory.devices = listOf(recorded(a55, alias = "a55"))
        holding("TASK-1", a55, Duration.ofMinutes(1))

        manage.withdraw("a55").getOrThrow()

        assertEquals(emptyList(), leases.leases.leases)
        val loss = leases.leases.losses.single()
        assertEquals(DeviceLossReason.WITHDRAWN, loss.reason)
        assertEquals(admin("TASK-1"), loss.adminDirectory)
        assertNull(loss.forWorktree)
        assertEquals(loss, read().getOrThrow().lossOf(admin("TASK-1"), DevicePlatform.ANDROID))
    }

    @Test
    fun `withdrawing a free device records no loss and lending a lent device writes nothing`() = runTest {
        inventory.devices = listOf(recorded(a55))
        manage.withdraw("R5CT10ABCDE").getOrThrow()
        assertEquals(DeviceLeases(), leases.leases)
        manage.lend("R5CT10ABCDE")
        val writes = inventory.writes

        manage.lend("R5CT10ABCDE").getOrThrow()

        assertEquals(writes, inventory.writes)
    }

    @Test
    fun `an alias names the device and can be cleared`() = runTest {
        inventory.devices = listOf(recorded(a55), recorded(pixel))
        connect(a55, pixel)

        assertEquals("a55", manage.rename("R5CT10ABCDE", " a55 ").getOrThrow().alias)
        assertEquals(a55.id, acquire(worktree("TASK-1"), DeviceRequest(name = "A55")).getOrThrow().held.device.id)
        assertNull(manage.rename(a55.id, "").getOrThrow().alias)
    }

    @Test
    fun `an alias already used by another device is refused and nothing changes`() = runTest {
        inventory.devices = listOf(recorded(a55, alias = "a55"), recorded(pixel))

        val error = assertFailsWith<WorktreeError.DuplicateDeviceAlias> { manage.rename("Pixel 8", "A55").getOrThrow() }

        assertEquals(a55.id, error.owner.id)
        assertNull(inventory.devices.last().alias)
        assertEquals("a55", manage.rename("a55", "a55").getOrThrow().alias)
    }

    @Test
    fun `forgetting a device removes it, releases it and it comes back as new`() = runTest {
        inventory.devices = listOf(recorded(a55, alias = "a55", lent = false))
        holding("TASK-1", a55, Duration.ofMinutes(1))

        manage.forget("a55").getOrThrow()

        assertEquals(emptyList(), inventory.devices)
        assertEquals(emptyList(), leases.leases.leases)
        connect(a55)
        read()
        assertEquals(listOf(recorded(a55)), inventory.devices)
    }

    @Test
    fun `forgetting a free device leaves the leases alone`() = runTest {
        inventory.devices = listOf(recorded(a55), recorded(pixel))
        holding("TASK-1", pixel, Duration.ofMinutes(1))

        manage.forget("Galaxy A55 5G").getOrThrow()

        assertEquals(1, leases.leases.leases.size)
    }

    @Test
    fun `an ambiguous or unknown name fails naming the candidates`() = runTest {
        val first = recorded(iphone).copy(name = "iPhone")
        val second = recorded(iphone.copy(identity = "00008150-0000000000000002")).copy(name = "iPhone")
        inventory.devices = listOf(first, second)

        val ambiguous = assertFailsWith<WorktreeError.AmbiguousPhysicalDevice> { manage.forget("iphone").getOrThrow() }
        val missing = assertFailsWith<WorktreeError.PhysicalDeviceNotFound> { manage.forget("pixel9").getOrThrow() }

        assertEquals(listOf(first, second), ambiguous.candidates)
        assertTrue(second.id in ambiguous.message)
        assertTrue("pixel9" in missing.message)
        assertEquals(2, inventory.devices.size)
    }

    @Test
    fun `acquiring by model matches part of the model or marketing name`() = runTest {
        connect(pixel, a55)

        assertEquals(a55.id, acquire(worktree("TASK-1"), DeviceRequest(model = "a55")).getOrThrow().held.device.id)
        assertEquals(pixel.id, acquire(worktree("TASK-2"), DeviceRequest(model = "PIXEL")).getOrThrow().held.device.id)
    }

    @Test
    fun `no platform with devices of both platforms free is a usage error`() = runTest {
        connect(pixel, iphone)

        assertFailsWith<WorktreeError.PhysicalDevicePlatformRequired> { acquire(worktree("TASK-1"), DeviceRequest()).getOrThrow() }
        assertEquals(DevicePlatform.IOS, acquire(worktree("TASK-1"), DeviceRequest(model = "iphone")).getOrThrow().held.lease.platform)
    }

    @Test
    fun `no platform takes the only platform with free devices or the only one recorded`() = runTest {
        inventory.devices = listOf(recorded(pixel))
        connect(iphone)

        assertEquals(DevicePlatform.IOS, acquire(worktree("TASK-1"), DeviceRequest()).getOrThrow().held.lease.platform)
        connect()
        assertFailsWith<WorktreeError.PhysicalDevicePlatformRequired> { acquire(worktree("TASK-2"), DeviceRequest()).getOrThrow() }
        inventory.devices = listOf(recorded(pixel))
        leases.leases = DeviceLeases()
        assertFailsWith<WorktreeError.PhysicalDeviceUnavailable> { acquire(worktree("TASK-2"), DeviceRequest()).getOrThrow() }
    }

    @Test
    fun `a name tries aliases before device names`() = runTest {
        connect(pixel, a55)
        inventory.devices = listOf(recorded(pixel, alias = "Galaxy A55 5G"), recorded(a55))

        assertEquals(pixel.id, acquire(worktree("TASK-1"), DeviceRequest(name = "galaxy a55 5g")).getOrThrow().held.device.id)
    }

    @Test
    fun `acquiring again renews the lease and says it was already held`() = runTest {
        connect(pixel)
        acquire(worktree("TASK-1"), android).getOrThrow()
        now += Duration.ofMinutes(5)

        val again = acquire(worktree("TASK-1"), android).getOrThrow()

        assertTrue(again.renewed)
        assertEquals(now.toEpochMilli(), leases.leases.leases.single().renewedAt)
        assertEquals(now + Duration.ofMinutes(20), again.held.expiresAt)
    }

    @Test
    fun `another device of the same platform must be released first`() = runTest {
        connect(pixel, a55)
        acquire(worktree("TASK-1"), android).getOrThrow()

        val error = assertFailsWith<WorktreeError.PhysicalDeviceAlreadyHeld> { acquire(worktree("TASK-1"), DeviceRequest(model = "a55")).getOrThrow() }

        assertEquals(pixel.id, error.held.device.id)
        assertTrue("release it first" in error.message && "--android" in error.message)
        assertEquals(1, leases.leases.leases.size)
    }

    @Test
    fun `a worktree holds one device of each platform`() = runTest {
        connect(pixel, iphone)

        acquire(worktree("TASK-1"), android).getOrThrow()
        acquire(worktree("TASK-1"), DeviceRequest(DevicePlatform.IOS)).getOrThrow()

        assertEquals(setOf(pixel.id, iphone.id), leases.leases.leases.map { it.deviceId }.toSet())
    }

    @Test
    fun `a working agent keeps its expired device and a done one loses it`() = runTest {
        connect(pixel)
        inventory.devices = listOf(recorded(pixel))
        holding("TASK-1", pixel, Duration.ofMinutes(25))
        agents[worktree("TASK-1")] = AgentState.WORKING

        val error = assertFailsWith<WorktreeError.PhysicalDeviceUnavailable> { acquire(worktree("TASK-2"), android).getOrThrow() }
        assertTrue("agent working" in error.message)
        agents[worktree("TASK-1")] = AgentState.DONE

        val acquisition = acquire(worktree("TASK-2"), android).getOrThrow()

        assertEquals("main-repo · TASK-1", acquisition.takenFrom)
    }

    @Test
    fun `an expired device with no agent session is taken over and its holder records the loss`() = runTest {
        connect(pixel)
        inventory.devices = listOf(recorded(pixel))
        holding("TASK-1", pixel, Duration.ofMinutes(25))

        acquire(worktree("TASK-2"), android).getOrThrow()

        val state = read().getOrThrow()
        assertEquals(admin("TASK-2"), state.heldBy(admin("TASK-2"), DevicePlatform.ANDROID)?.lease?.adminDirectory)
        val loss = state.lossOf(admin("TASK-1"), DevicePlatform.ANDROID)!!
        assertEquals(DeviceLossReason.TAKEN, loss.reason)
        assertEquals("main-repo · TASK-2", loss.forWorktree)
        assertEquals(now.toEpochMilli(), loss.at)
        assertEquals("Pixel 8", loss.deviceName)
    }

    @Test
    fun `the done agent's device is taken before the blocked one's and the oldest first within a step`() = runTest {
        connect(pixel, a55)
        inventory.devices = listOf(recorded(pixel), recorded(a55))
        holding("TASK-1", pixel, Duration.ofMinutes(30))
        holding("TASK-2", a55, Duration.ofMinutes(25))
        agents[worktree("TASK-1")] = AgentState.BLOCKED
        agents[worktree("TASK-2")] = AgentState.IDLE

        assertEquals(a55.id, acquire(worktree("TASK-3"), android).getOrThrow().held.device.id)

        agents[worktree("TASK-3")] = AgentState.IDLE
        holding("TASK-4", pixel, Duration.ZERO)
        leases.leases = leases.leases.copy(leases = leases.leases.leases.filterNot { it.adminDirectory == admin("TASK-4") })
        now += Duration.ofMinutes(25)
        assertEquals(a55.id, acquire(worktree("TASK-5"), android).getOrThrow().held.device.id)
    }

    @Test
    fun `a recorded but unplugged device fails saying when it was last seen`() = runTest {
        val seen = now - Duration.ofDays(3)
        inventory.devices = listOf(recorded(a55, alias = "a55", lastSeen = seen))

        val error = assertFailsWith<WorktreeError.PhysicalDeviceUnavailable> { acquire(worktree("TASK-1"), DeviceRequest(name = "a55")).getOrThrow() }

        assertIs<DeviceBlock.Disconnected>(error.blocks.single())
        assertTrue("isn't connected" in error.message && "connect it" in error.message && "--wait" in error.message, error.message)
        assertEquals(DeviceRequest(DevicePlatform.ANDROID, name = "a55"), error.request)
    }

    @Test
    fun `a held device fails naming its holder, lease and waiters`() = runTest {
        connect(pixel)
        inventory.devices = listOf(recorded(pixel))
        holding("TASK-1", pixel, Duration.ofMinutes(1))
        waiter("TASK-3", android)
        waiter("TASK-4", android)

        val error = assertFailsWith<WorktreeError.PhysicalDeviceUnavailable> { acquire(worktree("TASK-2"), android).getOrThrow() }

        assertTrue("held by main-repo · TASK-1 until" in error.message, error.message)
        assertTrue("2 worktrees are waiting" in error.message, error.message)
        assertEquals(2, error.waiting)
    }

    @Test
    fun `nothing recorded matches fails at once`() = runTest {
        connect(pixel)

        val error = assertFailsWith<WorktreeError.NoRecordedDeviceMatches> { acquire(worktree("TASK-1"), DeviceRequest(model = "pixel 9")).getOrThrow() }

        assertTrue("shogun device list --all" in error.message)
    }

    @Test
    fun `an unusable device fails saying what the user has to do`() = runTest {
        val ipad = iphone.copy(identity = "00008150-0000000000000003", name = "Test iPad", usability = DeviceUsability.DEVELOPER_MODE_OFF)
        connect(ipad)

        val error = assertFailsWith<WorktreeError.PhysicalDeviceUnavailable> { acquire(worktree("TASK-1"), DeviceRequest(DevicePlatform.IOS)).getOrThrow() }

        assertEquals(DeviceBlock.Unusable(inventory.devices.single(), DeviceUsability.DEVELOPER_MODE_OFF), error.blocks.single())
        assertTrue("developer mode must be turned on" in error.message)
    }

    @Test
    fun `outside a repository the acquisition fails`() = runTest {
        assertTrue(acquire("/home/dev/outside", android).isFailure)
    }

    @Test
    fun `a device connected while waiting is acquired`() = runTest {
        inventory.devices = listOf(recorded(a55, alias = "a55"))
        val progress = mutableListOf<PhysicalDeviceWaitProgress>()
        onSleep += { connect(a55) }

        val acquisition = acquire(worktree("TASK-2"), DeviceRequest(name = "a55"), PhysicalDeviceWait(onProgress = { progress += it })).getOrThrow()

        assertEquals(a55.id, acquisition.held.device.id)
        assertEquals(5, acquisition.waitedSeconds)
        assertEquals(listOf(Duration.ofSeconds(5)), pauses)
        assertEquals(1, progress.single().position)
        assertIs<DeviceBlock.Disconnected>(progress.single().blocks.single())
        assertTrue(queue.tickets.isEmpty())
    }

    @Test
    fun `a waiter's ticket carries its request`() = runTest {
        inventory.devices = listOf(recorded(a55, alias = "a55"))
        onSleep += {
            assertEquals("platform=android\nname=a55", queue.tickets.getValue(WaitQueueName.ANDROID_DEVICE).single().request)
            connect(a55)
        }

        acquire(worktree("TASK-2"), DeviceRequest(name = "a55"), PhysicalDeviceWait()).getOrThrow()
    }

    @Test
    fun `a lease expiring while waiting hands the device over`() = runTest {
        connect(pixel)
        inventory.devices = listOf(recorded(pixel))
        holding("TASK-1", pixel, Duration.ofMinutes(17))

        val acquisition = acquire(worktree("TASK-2"), android, PhysicalDeviceWait()).getOrThrow()

        assertEquals("main-repo · TASK-1", acquisition.takenFrom)
        assertEquals(180, acquisition.waitedSeconds)
    }

    @Test
    fun `a free device is acquired at once with a wait of zero seconds`() = runTest {
        connect(pixel)

        assertEquals(0, acquire(worktree("TASK-1"), android, PhysicalDeviceWait()).getOrThrow().waitedSeconds)
    }

    @Test
    fun `a wait for an unknown device fails at once`() = runTest {
        connect(pixel)

        assertFailsWith<WorktreeError.NoRecordedDeviceMatches> { acquire(worktree("TASK-1"), DeviceRequest(name = "pixel9"), PhysicalDeviceWait()).getOrThrow() }
        assertTrue(pauses.isEmpty())
    }

    @Test
    fun `a wait that times out acquires nothing and says why`() = runTest {
        inventory.devices = listOf(recorded(a55, alias = "a55"))

        val error = assertFailsWith<WorktreeError.PhysicalDeviceWaitTimedOut> {
            acquire(worktree("TASK-2"), DeviceRequest(name = "a55"), PhysicalDeviceWait(timeout = Duration.ofSeconds(12))).getOrThrow()
        }

        assertTrue("isn't connected" in error.message)
        assertEquals(DeviceLeases(), leases.leases)
        assertTrue(queue.tickets.isEmpty())
    }

    @Test
    fun `a waiter whose request doesn't match doesn't hold back the ones behind`() = runTest {
        connect(pixel)
        inventory.devices = listOf(recorded(a55, alias = "a55"), recorded(pixel))
        waiter("TASK-2", DeviceRequest(DevicePlatform.ANDROID, name = "a55"))

        val acquisition = acquire(worktree("TASK-3"), android).getOrThrow()

        assertEquals(pixel.id, acquisition.held.device.id)
        assertEquals(1, queue.tickets.getValue(WaitQueueName.ANDROID_DEVICE).size)
    }

    @Test
    fun `an acquisition without waiting doesn't take a device a waiter matches`() = runTest {
        connect(pixel)
        waiter("TASK-2", android)

        val error = assertFailsWith<WorktreeError.PhysicalDeviceUnavailable> { acquire(worktree("TASK-4"), android).getOrThrow() }

        assertTrue("1 worktree is waiting for one" in error.message, error.message)
        assertTrue(error.blocks.isEmpty())
    }

    @Test
    fun `a ticket without a request matches any device`() = runTest {
        connect(pixel)
        waiter("TASK-2", request = null)

        assertTrue(acquire(worktree("TASK-4"), android).isFailure)
    }

    @Test
    fun `a waiter served in turn gets the device ahead of later ones`() = runTest {
        connect(pixel)
        inventory.devices = listOf(recorded(pixel))
        holding("TASK-1", pixel, Duration.ofMinutes(19))
        waiter("TASK-3", android, process = ProcessIdentity(4000, 1))
        onSleep += { liveness.dead += ProcessIdentity(4000, 1) }

        val acquisition = acquire(worktree("TASK-2"), android, PhysicalDeviceWait()).getOrThrow()

        assertEquals(pixel.id, acquisition.held.device.id)
    }

    @Test
    fun `releasing frees the worktree's devices without touching the others`() = runTest {
        inventory.devices = listOf(recorded(pixel), recorded(iphone), recorded(a55))
        holding("TASK-1", pixel, Duration.ofMinutes(1))
        holding("TASK-1", iphone, Duration.ofMinutes(1))
        holding("TASK-2", a55, Duration.ofMinutes(1))

        val android = release(worktree("TASK-1"), DevicePlatform.ANDROID).getOrThrow()
        val rest = release(worktree("TASK-1")).getOrThrow()
        val none = release(worktree("TASK-1")).getOrThrow()

        assertEquals(listOf(pixel.id), android.released.map { it.device.id })
        assertEquals(listOf(iphone.id), rest.released.map { it.device.id })
        assertEquals(emptyList(), none.released)
        assertEquals(listOf(a55.id), leases.leases.leases.map { it.deviceId })
    }

    @Test
    fun `the app can force a device's release`() = runTest {
        inventory.devices = listOf(recorded(pixel))
        holding("TASK-1", pixel, Duration.ofMinutes(1))

        assertEquals(pixel.id, release.force(pixel.id).getOrThrow().released.single().device.id)
        assertEquals(DeviceLeases(), leases.leases)
    }

    @Test
    fun `removing a worktree releases its devices, drops its waits and forgets its losses`() = runTest {
        inventory.devices = listOf(recorded(pixel))
        holding("TASK-1", pixel, Duration.ofMinutes(1))
        waiter("TASK-1", DeviceRequest(DevicePlatform.IOS), WaitQueueName.IOS_DEVICE)
        waiter("TASK-2", android)
        manage.withdraw(pixel.id)
        holding("TASK-1", pixel, Duration.ofMinutes(1))

        release.onWorktreeRemoved(worktree("TASK-1"))

        assertEquals(DeviceLeases(), leases.leases)
        assertEquals(listOf(admin("TASK-2")), queue.tickets.values.flatten().map { it.adminDirectory })
        release.onWorktreeRemoved(worktree("TASK-2"))
        release.onWorktreeRemoved("/home/dev/outside")
        assertTrue(queue.tickets.isEmpty())
    }

    @Test
    fun `a held device that is unplugged stays held and is never given to another worktree`() = runTest {
        inventory.devices = listOf(recorded(pixel))
        holding("TASK-1", pixel, Duration.ofMinutes(30))

        val state = read().getOrThrow()
        val error = assertFailsWith<WorktreeError.PhysicalDeviceUnavailable> { acquire(worktree("TASK-2"), android).getOrThrow() }

        val held = state.heldBy(admin("TASK-1"), DevicePlatform.ANDROID)!!
        assertFalse(held.isConnected)
        assertTrue("disconnected" in error.message)
    }

    @Test
    fun `a held device reconnected over wireless debugging keeps its holder with the new serial`() = runTest {
        inventory.devices = listOf(recorded(pixel))
        holding("TASK-1", pixel, Duration.ofMinutes(1))
        connect(pixel.copy(transport = "adb-3A281FDJH000AB-AbC._adb-tls-connect._tcp", connection = DeviceConnection.NETWORK))

        val held = read().getOrThrow().heldBy(admin("TASK-1"), DevicePlatform.ANDROID)!!

        assertEquals("adb-3A281FDJH000AB-AbC._adb-tls-connect._tcp", held.connected?.transport)
        assertEquals(
            listOf("SHOGUN_ANDROID_DEVICE_SERIAL" to "adb-3A281FDJH000AB-AbC._adb-tls-connect._tcp"),
            ledger.variablesFor(worktree("TASK-1")),
        )
    }

    @Test
    fun `the state reports each worktree's place in line`() = runTest {
        waiter("TASK-2", android)
        waiter("TASK-3", android)

        val state = read().getOrThrow()

        assertEquals(2, state.waitingOf(admin("TASK-3"), DevicePlatform.ANDROID)?.first)
        assertNull(state.waitingOf(admin("TASK-3"), DevicePlatform.IOS))
        assertNull(state.heldBy(admin("TASK-3"), DevicePlatform.ANDROID))
    }

    @Test
    fun `the environment gets the held devices' serial and UDID`() = runTest {
        assertEquals(emptyList(), ledger.variablesFor("/home/dev/outside"))
        inventory.devices = listOf(recorded(pixel), recorded(iphone))
        holding("TASK-1", iphone, Duration.ofMinutes(1))
        holding("TASK-2", pixel, Duration.ofMinutes(1))

        assertEquals(listOf("SHOGUN_IOS_DEVICE_UDID" to iphone.identity), ledger.variablesFor(worktree("TASK-1")))
        assertEquals(0, devices.lists)
        assertEquals(listOf("SHOGUN_ANDROID_DEVICE_SERIAL" to pixel.identity), ledger.variablesFor(worktree("TASK-2")))
        assertEquals(emptyList(), ledger.variablesFor(worktree("TASK-3")))
        assertEquals(emptyList(), ledger.variablesFor("/home/dev/outside"))
    }

    @Test
    fun `a lease for a forgotten device doesn't count`() = runTest {
        connect(pixel)
        holding("TASK-1", a55, Duration.ofMinutes(1))

        assertEquals(pixel.id, acquire(worktree("TASK-1"), android).getOrThrow().held.device.id)
        assertEquals(listOf(pixel.id), leases.leases.leases.map { it.deviceId })
    }

    @Test
    fun `a wait that can't succeed fails at once with its reason`() = runTest {
        connect(pixel, a55)
        acquire(worktree("TASK-1"), android).getOrThrow()

        assertFailsWith<WorktreeError.PhysicalDeviceAlreadyHeld> { acquire(worktree("TASK-1"), DeviceRequest(model = "a55"), PhysicalDeviceWait()).getOrThrow() }
        assertTrue(pauses.isEmpty())
    }

    @Test
    fun `a wait behind a working or unplugged holder polls for connections`() = runTest {
        inventory.devices = listOf(recorded(pixel), recorded(a55))
        connect(pixel)
        holding("TASK-1", pixel, Duration.ofMinutes(1))
        holding("TASK-3", a55, Duration.ofMinutes(1))
        agents[worktree("TASK-1")] = AgentState.WORKING

        assertTrue(acquire(worktree("TASK-2"), android, PhysicalDeviceWait(timeout = Duration.ofSeconds(6))).isFailure)

        assertEquals(listOf(Duration.ofSeconds(5), Duration.ofSeconds(1)), pauses)
    }

    @Test
    fun `withdrawn devices aren't counted when choosing the platform`() = runTest {
        inventory.devices = listOf(recorded(pixel, lent = false))
        connect(pixel, iphone)

        assertEquals(DevicePlatform.IOS, acquire(worktree("TASK-1"), DeviceRequest()).getOrThrow().held.lease.platform)
    }

    @Test
    fun `acquiring keeps the other worktrees' losses and renewing keeps the other leases`() = runTest {
        connect(pixel, a55)
        inventory.devices = listOf(recorded(pixel), recorded(a55))
        holding("TASK-2", a55, Duration.ofMinutes(1))
        leases.leases = leases.leases.copy(
            losses = listOf(
                app.luxion.shogunai.domain.model.DeviceLoss(admin("TASK-2"), "/home/dev/projects/worktrees/TASK", DevicePlatform.ANDROID, a55.id, "A55", 0, DeviceLossReason.TAKEN, "x"),
                app.luxion.shogunai.domain.model.DeviceLoss(admin("TASK-1"), "/home/dev/projects/worktrees/TASK", DevicePlatform.IOS, iphone.id, "iPhone", 0, DeviceLossReason.WITHDRAWN),
                app.luxion.shogunai.domain.model.DeviceLoss(admin("TASK-1"), "/home/dev/projects/worktrees/TASK", DevicePlatform.ANDROID, a55.id, "A55", 0, DeviceLossReason.WITHDRAWN),
            ),
        )

        acquire(worktree("TASK-1"), android).getOrThrow()
        acquire(worktree("TASK-1"), android).getOrThrow()

        assertEquals(2, leases.leases.losses.size)
        assertEquals(2, leases.leases.leases.size)
    }

    @Test
    fun `forcing the release of a lease whose device was forgotten reports nothing`() = runTest {
        holding("TASK-1", pixel, Duration.ofMinutes(1))

        assertEquals(emptyList(), release.force(pixel.id).getOrThrow().released)
        assertEquals(emptyList(), leases.leases.leases)
    }

    @Test
    fun `devices are read for status only while one is held or waited for`() = runTest {
        assertFalse(read.hasRecorded())
        connect(pixel)
        assertNull(read.ifAnyHeld())
        assertEquals(0, devices.lists)
        waiter("TASK-2", android)
        assertEquals(1, read.ifAnyHeld()?.waiting?.getValue(DevicePlatform.ANDROID)?.size)
        queue.tickets.clear()
        holding("TASK-1", pixel, Duration.ofMinutes(1))

        assertEquals(pixel.id, read.ifAnyHeld()?.heldBy(admin("TASK-1"), DevicePlatform.ANDROID)?.device?.id)
        assertTrue(read.hasRecorded())
    }

    @Test
    fun `devices that can't be read for status count as none`() = runTest {
        val broken = object : app.luxion.shogunai.domain.io.PhysicalDeviceLeaseStore {
            override fun read(): DeviceLeases = error("unreadable")
            override fun write(leases: DeviceLeases) = Unit
        }
        val failingInventory = object : app.luxion.shogunai.domain.io.PhysicalDeviceInventoryStore {
            override fun read(): List<RecordedDevice> = emptyList()
            override fun write(devices: List<RecordedDevice>) = error("read-only")
        }
        val ledgerWith = { store: app.luxion.shogunai.domain.io.PhysicalDeviceLeaseStore, inventoryStore: app.luxion.shogunai.domain.io.PhysicalDeviceInventoryStore ->
            ReadPhysicalDevicesUseCase(
                PhysicalDeviceLedger(listOf(devices), inventoryStore, store, FakeEnvironmentStateStore(), queue, liveness, { admin("TASK-1") }, { Duration.ofMinutes(20) }, sleep = {}),
            )
        }
        connect(pixel)
        holding("TASK-1", pixel, Duration.ofMinutes(1))

        assertNull(ledgerWith(broken, inventory).ifAnyHeld())
        assertNull(ledgerWith(leases, failingInventory).ifAnyHeld())
    }

    @Test
    fun `the connected serial of a worktree's Android device is found for Orca`() = runTest {
        assertNull(ledger.connectedAndroidSerial(worktree("TASK-1")))
        inventory.devices = listOf(recorded(pixel), recorded(iphone))
        holding("TASK-2", iphone, Duration.ofMinutes(1))
        assertNull(ledger.connectedAndroidSerial(worktree("TASK-2")))
        holding("TASK-1", pixel, Duration.ofMinutes(1))

        assertNull(ledger.connectedAndroidSerial(worktree("TASK-1")))
        assertNull(ledger.connectedAndroidSerial(worktree("TASK-3")))
        connect(pixel.copy(transport = "192.168.1.20:37000"))
        assertEquals("192.168.1.20:37000", ledger.connectedAndroidSerial(worktree("TASK-1")))
    }
}
