package app.luxion.shogunai.cli

import app.luxion.shogunai.domain.FakeAndroidSdk
import app.luxion.shogunai.domain.FakeAppleSimulators
import app.luxion.shogunai.domain.FakeDerivedDataSource
import app.luxion.shogunai.domain.FakeDeviceWaitQueue
import app.luxion.shogunai.domain.FakeEnvironmentStateStore
import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.FakeGradleDaemonSource
import app.luxion.shogunai.domain.FakeHerdrConfigSource
import app.luxion.shogunai.domain.FakeKmpTestWiring
import app.luxion.shogunai.domain.FakeMachineCapacity
import app.luxion.shogunai.domain.FakeOrcaClient
import app.luxion.shogunai.domain.FakePhysicalDeviceInventoryStore
import app.luxion.shogunai.domain.FakePhysicalDeviceLeaseStore
import app.luxion.shogunai.domain.FakePhysicalDevices
import app.luxion.shogunai.domain.FakePortProbe
import app.luxion.shogunai.domain.FakeProcessLiveness
import app.luxion.shogunai.domain.FakeProjectRepository
import app.luxion.shogunai.domain.FakeRepositoryProvisioningSource
import app.luxion.shogunai.domain.FakeResourceLimitsRepository
import app.luxion.shogunai.domain.executor.CommandResult
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.model.DeviceConnection
import app.luxion.shogunai.domain.model.DeviceLeases
import app.luxion.shogunai.domain.model.DeviceLoss
import app.luxion.shogunai.domain.model.DeviceLossReason
import app.luxion.shogunai.domain.model.DevicePlatform
import app.luxion.shogunai.domain.model.DeviceUsability
import app.luxion.shogunai.domain.model.OrcaWorktree
import app.luxion.shogunai.domain.model.PendingDevice
import app.luxion.shogunai.domain.model.PhysicalDevice
import app.luxion.shogunai.domain.model.PhysicalDeviceLease
import app.luxion.shogunai.domain.model.PhysicalDeviceListing
import app.luxion.shogunai.domain.model.ProcessIdentity
import app.luxion.shogunai.domain.model.RecordedDevice
import app.luxion.shogunai.domain.model.ResourceSettings
import app.luxion.shogunai.domain.model.WaitQueueName
import app.luxion.shogunai.domain.model.WaitTicket
import app.luxion.shogunai.domain.model.toLocalClockTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeviceCommandsTest {

    private val base = "/home/dev/projects/main-repo"
    private val common = "$base/.git"
    private val task1 = "/home/dev/projects/worktrees/TASK-1"
    private val task2 = "/home/dev/projects/worktrees/TASK-2"
    private val admin1 = "$common/worktrees/TASK-1"
    private val admin2 = "$common/worktrees/TASK-2"

    private val porcelain = """
        worktree $base
        HEAD 1111111111111111111111111111111111111111
        branch refs/heads/main

        worktree $task1
        HEAD 2222222222222222222222222222222222222222
        branch refs/heads/feature/TASK-1

        worktree $task2
        HEAD 3333333333333333333333333333333333333333
        branch refs/heads/feature/TASK-2
    """.trimIndent()

    private val out = StringBuilder()
    private val err = StringBuilder()
    private var now: Instant = Instant.parse("2026-01-05T14:00:00Z")
    private val onPause = mutableListOf<() -> Unit>()
    private val queue = FakeDeviceWaitQueue()
    private val devices = FakePhysicalDevices()
    private val inventory = FakePhysicalDeviceInventoryStore()
    private val leases = FakePhysicalDeviceLeaseStore()
    private val orca = FakeOrcaClient(installed = false)
    private val simulators = FakeAppleSimulators()

    private val pixel = PhysicalDevice(
        DevicePlatform.ANDROID, "3A281FDJH000AB", "3A281FDJH000AB", DeviceConnection.USB,
        name = "Pixel 8", manufacturer = "Google", model = "Pixel 8", marketingName = "Pixel 8", osVersion = "16",
    )
    private val a55 = PhysicalDevice(
        DevicePlatform.ANDROID, "R5CT10ABCDE", "R5CT10ABCDE", DeviceConnection.USB,
        name = "Galaxy A55 5G", manufacturer = "samsung", model = "SM-A556B", marketingName = "Galaxy A55 5G", osVersion = "15",
    )
    private val iphone = PhysicalDevice(
        DevicePlatform.IOS, "00008150-000A1B2C3D4E5F60", "00008150-000A1B2C3D4E5F60", DeviceConnection.NETWORK,
        name = "Test iPhone", manufacturer = "Apple", model = "iPhone18,3", marketingName = "iPhone 17", osVersion = "26.6",
    )

    private val executor = object : ShellCommandExecutor {
        override suspend fun execute(command: List<String>, workingDirectory: String?): CommandResult {
            if (workingDirectory?.startsWith("/home/dev/projects") != true) return CommandResult(command, 128, "", "not a git repository")
            val worktree = listOf(task1, task2).firstOrNull { workingDirectory.startsWith(it) } ?: base
            val admin = when (worktree) {
                task1 -> admin1
                task2 -> admin2
                else -> common
            }
            val stdout = when {
                command.take(3) == listOf("git", "worktree", "list") -> porcelain
                "--show-toplevel" in command -> "$worktree\n"
                "--git-dir" in command -> "$admin\n$common\n"
                "--git-common-dir" in command -> "$common\n"
                else -> ""
            }
            return CommandResult(command, 0, stdout, "")
        }
    }

    private var provisioning = FakeRepositoryProvisioningSource()
    private val environmentStore = FakeEnvironmentStateStore()

    private fun cli(workingDirectory: String = task1, orcaClient: FakeOrcaClient = orca) = ShogunCli(
        executor = executor,
        fileManager = FakeFileManager(existing = listOf(base, task1, task2)),
        projectRepository = FakeProjectRepository(emptyList()),
        provisioningSource = provisioning,
        daemonSource = FakeGradleDaemonSource(),
        gradleUserHome = "/home/dev/.gradle",
        environmentStateStore = environmentStore,
        portProbe = FakePortProbe(),
        androidSdk = FakeAndroidSdk(),
        appleSimulators = simulators,
        kmpTestWiring = FakeKmpTestWiring(),
        resourceLimits = FakeResourceLimitsRepository(ResourceSettings(leaseMinutes = 20)),
        machineCapacity = FakeMachineCapacity(),
        derivedDataSource = FakeDerivedDataSource(),
        herdrConfig = FakeHerdrConfigSource(),
        orca = orcaClient,
        workingDirectory = workingDirectory,
        out = out,
        err = err,
        canonicalPath = { it.trimEnd('/') },
        environment = hostEnvironment(),
        deviceWaitQueue = queue,
        processLiveness = FakeProcessLiveness(),
        deviceClock = { now },
        pauseWhileWaiting = { pause ->
            now += pause
            onPause.removeFirstOrNull()?.invoke()
        },
        physicalDeviceSources = listOf(devices),
        deviceInventory = inventory,
        deviceLeases = leases,
    )

    private fun run(vararg args: String, workingDirectory: String = task1): Int = cli(workingDirectory).run(args.toList())

    private fun json(): JsonObject = Json.parseToJsonElement(out.toString()).jsonObject

    private fun recorded(device: PhysicalDevice, alias: String? = null, lent: Boolean = true, lastSeen: Instant = now) =
        RecordedDevice(device.id, device.platform, alias, device.name, device.manufacturer, device.model, device.marketingName, device.osVersion, lent, lastSeen.toEpochMilli())

    private fun holding(admin: String, worktree: String, device: PhysicalDevice, renewedAgo: Duration = Duration.ofMinutes(1)) {
        leases.leases = leases.leases.copy(
            leases = leases.leases.leases + PhysicalDeviceLease(device.id, device.platform, admin, worktree, (now - renewedAgo).toEpochMilli(), (now - renewedAgo).toEpochMilli()),
        )
    }

    private fun clock(instant: Instant) = instant.toLocalClockTime()

    @Test
    fun `list prints connected devices and all adds the recorded ones`() {
        inventory.devices = listOf(recorded(a55, alias = "a55", lastSeen = now - Duration.ofDays(3)))
        devices.listing = PhysicalDeviceListing(listOf(pixel), listOf(PendingDevice(DevicePlatform.ANDROID, "0A1B2C3D4E", DeviceUsability.UNAUTHORIZED)))
        holding(admin2, task2, pixel)

        assertEquals(0, run("device", "list"), err.toString())
        val connected = out.lines().filter { it.isNotEmpty() }
        assertEquals(2, connected.size, out.toString())
        assertTrue(connected[0].startsWith("Pixel 8 (android:3A281FDJH000AB)") && "USB" in connected[0] && "held by main-repo · TASK-2" in connected[0], connected[0])
        assertTrue("waiting for the user to allow USB debugging" in connected[1])
        out.clear()

        assertEquals(0, run("device", "list", "--all"))

        assertTrue(out.lines().any { it.startsWith("a55 (android:R5CT10ABCDE)") && "disconnected, last seen" in it }, out.toString())
    }

    @Test
    fun `list json prints an array with every field`() {
        inventory.devices = listOf(recorded(a55, alias = "a55", lent = false))
        devices.connected = listOf(pixel)

        assertEquals(0, run("device", "list", "--all", "--json"))

        val entries = Json.parseToJsonElement(out.toString()).jsonArray.map { it.jsonObject }
        assertEquals(listOf("android:R5CT10ABCDE", "android:3A281FDJH000AB"), entries.map { it.getValue("id").jsonPrimitive.content })
        val a55Entry = entries.first()
        assertEquals("a55", a55Entry.getValue("alias").jsonPrimitive.content)
        assertEquals(JsonNull, a55Entry["connection"])
        assertEquals(false, a55Entry.getValue("lent").jsonPrimitive.boolean)
        assertEquals("usb", entries.last().getValue("connection").jsonPrimitive.content)
        assertEquals("3A281FDJH000AB", entries.last().getValue("serial").jsonPrimitive.content)
        assertEquals(JsonNull, entries.last()["holder"])
        assertTrue(entries.first().keys.containsAll(listOf("platform", "manufacturer", "model", "marketingName", "osVersion", "lastSeen")))
    }

    @Test
    fun `an empty list says so`() {
        assertEquals(0, run("device", "list"))
        assertEquals("no physical device connected\n", out.toString())
        out.clear()
        assertEquals(0, run("device", "list", "--all"))
        assertEquals("no physical device recorded\n", out.toString())
    }

    @Test
    fun `acquire prints the device and the line to export`() {
        devices.connected = listOf(pixel)

        assertEquals(0, run("device", "acquire", "--android"), err.toString())

        val lines = out.lines().filter { it.isNotEmpty() }
        assertEquals("acquired Android device Pixel 8, serial 3A281FDJH000AB, USB, lease until ${clock(now + Duration.ofMinutes(20))}", lines[0])
        assertEquals("export SHOGUN_ANDROID_DEVICE_SERIAL=3A281FDJH000AB", lines[1])
        assertEquals("", err.toString())
    }

    @Test
    fun `acquire by model prints the alias and model and the UDID for an iPhone`() {
        inventory.devices = listOf(recorded(iphone, alias = "phone"))
        devices.connected = listOf(iphone)

        assertEquals(0, run("device", "acquire", "--model", "iphone 17"), err.toString())

        assertTrue(out.startsWith("acquired iOS device phone (iPhone 17), UDID 00008150-000A1B2C3D4E5F60, network"), out.toString())
        assertTrue("export SHOGUN_IOS_DEVICE_UDID=00008150-000A1B2C3D4E5F60" in out)
    }

    @Test
    fun `acquiring again says it was already held`() {
        devices.connected = listOf(pixel)
        run("device", "acquire", "--android")
        out.clear()

        assertEquals(0, run("device", "acquire", "--android"))

        assertTrue(out.startsWith("already held Android device Pixel 8"), out.toString())
    }

    @Test
    fun `acquire json reports the device, the wait and who lost it`() {
        devices.connected = listOf(pixel)
        inventory.devices = listOf(recorded(pixel))
        holding(admin2, task2, pixel, renewedAgo = Duration.ofMinutes(25))

        assertEquals(0, run("device", "acquire", "--android", "--wait", "--json"), err.toString())

        val result = json()
        assertEquals("3A281FDJH000AB", result.getValue("android").jsonObject.getValue("serial").jsonPrimitive.content)
        assertEquals(true, result.getValue("android").jsonObject.getValue("connected").jsonPrimitive.boolean)
        assertEquals(JsonNull, result["ios"])
        assertEquals(0, result.getValue("waited").jsonPrimitive.int)
        assertEquals("main-repo · TASK-2", result.getValue("takenFrom").jsonPrimitive.content)
        assertEquals("not-open", result.getValue("orcaView").jsonPrimitive.content)
        assertEquals(1, out.lines().count { it.isNotEmpty() })
    }

    @Test
    fun `acquire json without waiting has a null wait`() {
        devices.connected = listOf(pixel)

        assertEquals(0, run("device", "acquire", "--android", "--json"))

        assertEquals(JsonNull, json()["waited"])
        assertEquals(JsonNull, json()["takenFrom"])
    }

    @Test
    fun `acquire outside a repository fails`() {
        assertEquals(1, run("device", "acquire", "--android", workingDirectory = "/home/dev/elsewhere"))
        assertEquals("", out.toString())
    }

    @Test
    fun `acquire with no platform and both available is a usage error`() {
        devices.connected = listOf(pixel, iphone)

        assertEquals(2, run("device", "acquire"))

        assertTrue("--android, --ios, --name or --model" in err, err.toString())
        assertEquals("", out.toString())
    }

    @Test
    fun `acquire fails naming the holder and pointing to wait`() {
        devices.connected = listOf(pixel)
        inventory.devices = listOf(recorded(pixel))
        holding(admin2, task2, pixel)

        assertEquals(1, run("device", "acquire", "--android"))

        assertTrue("held by main-repo · TASK-2 until ${clock(now + Duration.ofMinutes(19))}" in err && "--wait" in err, err.toString())
        assertEquals("", out.toString())
    }

    @Test
    fun `waiting for an unplugged device prints progress on standard error and acquires it once connected`() {
        inventory.devices = listOf(recorded(a55, alias = "a55"))
        onPause += {}
        onPause += { devices.connected = listOf(a55) }

        assertEquals(0, run("device", "acquire", "--name", "a55", "--wait"), err.toString())

        val progress = err.lines().filter { it.startsWith("waiting for") }
        assertEquals(listOf("waiting for an Android device: 1st of 1 in line; a55 isn't connected; connect it"), progress)
        assertTrue("waited 10 s" in err, err.toString())
        assertTrue(out.startsWith("acquired Android device a55"))
    }

    @Test
    fun `waiting behind a holder and behind other waiters says so`() {
        devices.connected = listOf(pixel)
        inventory.devices = listOf(recorded(pixel))
        holding(admin2, task2, pixel)
        queue.write(WaitQueueName.ANDROID_DEVICE, listOf(WaitTicket("ticket", admin2, task2, ProcessIdentity(3000, 1), now.toEpochMilli(), "platform=android")))

        assertEquals(1, run("device", "acquire", "--android", "--wait-timeout", "1"))

        assertTrue(err.lines().any { it == "waiting for an Android device: 2nd of 2 in line; Pixel 8 held by main-repo · TASK-2 until ${clock(now - Duration.ofSeconds(60) + Duration.ofMinutes(19))}" }, err.toString())
        assertTrue("Waited 1 min" in err, err.toString())
    }

    @Test
    fun `an invalid wait timeout is a usage error`() {
        assertEquals(2, run("device", "acquire", "--android", "--wait-timeout", "0"))
    }

    @Test
    fun `release frees the worktree's devices and says when it held none`() {
        inventory.devices = listOf(recorded(pixel))
        holding(admin1, task1, pixel)

        assertEquals(0, run("device", "release"))
        assertEquals("released Android device Pixel 8\n", out.toString())
        out.clear()
        assertEquals(0, run("device", "release", "--ios"))
        assertEquals("held no physical device\n", out.toString())
        assertEquals(DeviceLeases(), leases.leases)
    }

    @Test
    fun `release json reports each platform`() {
        inventory.devices = listOf(recorded(iphone))
        holding(admin1, task1, iphone)

        assertEquals(0, run("device", "release", "--json"))

        assertEquals("00008150-000A1B2C3D4E5F60", json().getValue("ios").jsonObject.getValue("udid").jsonPrimitive.content)
        assertEquals(JsonNull, json()["android"])
    }

    @Test
    fun `status shows the held device, a waited platform and a lost one`() {
        devices.connected = listOf(pixel)
        inventory.devices = listOf(recorded(pixel), recorded(iphone))
        holding(admin1, task1, pixel)
        queue.write(WaitQueueName.IOS_DEVICE, listOf(WaitTicket("ticket", admin1, task1, ProcessIdentity(3000, 1), now.toEpochMilli(), "platform=ios")))

        assertEquals(0, run("device", "status"))

        assertEquals(
            listOf(
                "android: Pixel 8, serial 3A281FDJH000AB, USB, lease until ${clock(now + Duration.ofMinutes(19))}",
                "ios: waiting, 1st of 1 in line since ${clock(now)}",
            ),
            out.lines().filter { it.isNotEmpty() },
        )
    }

    @Test
    fun `status says why a device was lost and reports a disconnected one`() {
        inventory.devices = listOf(recorded(pixel), recorded(iphone))
        holding(admin1, task1, pixel)
        leases.leases = leases.leases.copy(
            losses = listOf(DeviceLoss(admin1, "/home/dev/projects/worktrees/TASK", DevicePlatform.IOS, iphone.id, "iPhone 17", now.toEpochMilli(), DeviceLossReason.TAKEN, "main-repo · TASK-2")),
        )

        assertEquals(0, run("device", "status"))

        val lines = out.lines().filter { it.isNotEmpty() }
        assertEquals("android: Pixel 8, disconnected, lease until ${clock(now + Duration.ofMinutes(19))}", lines[0])
        assertEquals("ios: none (iPhone 17 was taken for main-repo · TASK-2 at ${clock(now)})", lines[1])
    }

    @Test
    fun `status json has each platform's device, wait and loss`() {
        inventory.devices = listOf(recorded(pixel))
        leases.leases = DeviceLeases(losses = listOf(DeviceLoss(admin1, "/home/dev/projects/worktrees/TASK", DevicePlatform.ANDROID, pixel.id, "Pixel 8", now.toEpochMilli(), DeviceLossReason.WITHDRAWN)))

        assertEquals(0, run("device", "status", "--json"))

        val android = json().getValue("android").jsonObject
        assertEquals(JsonNull, android["device"])
        assertEquals("withdrawn", android.getValue("lost").jsonObject.getValue("reason").jsonPrimitive.content)
        assertEquals(JsonNull, json().getValue("ios").jsonObject["lost"])
    }

    @Test
    fun `lend, withdraw, rename and forget change the inventory`() {
        inventory.devices = listOf(recorded(a55))

        assertEquals(0, run("device", "rename", "R5CT10ABCDE", "a55"))
        assertEquals(0, run("device", "withdraw", "a55"))
        assertEquals(0, run("device", "lend", "a55"))
        assertEquals(0, run("device", "forget", "a55", "--json"))

        val lines = out.lines().filter { it.isNotEmpty() }
        assertEquals("Galaxy A55 5G (android:R5CT10ABCDE) is now called a55", lines[0])
        assertEquals("a55 (android:R5CT10ABCDE) is withdrawn: no agent can acquire it until it's lent again", lines[1])
        assertEquals("a55 (android:R5CT10ABCDE) is lent to agents", lines[2])
        assertEquals("android:R5CT10ABCDE", Json.parseToJsonElement(lines[3]).jsonObject.getValue("id").jsonPrimitive.content)
        assertEquals(emptyList(), inventory.devices)
    }

    @Test
    fun `an ambiguous device fails naming the candidates`() {
        inventory.devices = listOf(
            recorded(iphone).copy(name = "iPhone"),
            recorded(iphone.copy(identity = "00008150-0000000000000002")).copy(name = "iPhone"),
        )

        assertEquals(1, run("device", "forget", "iPhone"))

        assertTrue("ios:00008150-000A1B2C3D4E5F60" in err && "ios:00008150-0000000000000002" in err, err.toString())
        assertEquals("", out.toString())
    }

    @Test
    fun `an acquired Android device is shown in Orca unless the simulator is booted`() {
        val open = FakeOrcaClient(psRows = listOf(OrcaWorktree(task1, createdByOrca = false)))
        val orcaCli = { device: PhysicalDevice ->
            devices.connected = listOf(device)
            ShogunCli(
                executor = executor, fileManager = FakeFileManager(existing = listOf(base, task1, task2)), projectRepository = FakeProjectRepository(emptyList()),
                provisioningSource = FakeRepositoryProvisioningSource(), daemonSource = FakeGradleDaemonSource(), gradleUserHome = "/home/dev/.gradle",
                environmentStateStore = FakeEnvironmentStateStore(), portProbe = FakePortProbe(), androidSdk = FakeAndroidSdk(), appleSimulators = simulators,
                kmpTestWiring = FakeKmpTestWiring(), resourceLimits = FakeResourceLimitsRepository(ResourceSettings()), machineCapacity = FakeMachineCapacity(),
                derivedDataSource = FakeDerivedDataSource(), herdrConfig = FakeHerdrConfigSource(), orca = open, workingDirectory = task1, out = out, err = err,
                canonicalPath = { it.trimEnd('/') }, environment = hostEnvironment(), deviceWaitQueue = queue, processLiveness = FakeProcessLiveness(),
                deviceClock = { now }, physicalDeviceSources = listOf(devices), deviceInventory = inventory, deviceLeases = leases,
            )
        }

        assertEquals(0, orcaCli(pixel).run(listOf("device", "acquire", "--android", "--json")), err.toString())
        assertEquals("shown", json().getValue("orcaView").jsonPrimitive.content)
        assertEquals(listOf("3A281FDJH000AB"), open.attachedEmulators.map { it.second })
        assertTrue("shown Pixel 8 in Orca's emulator view" in err, err.toString())
        out.clear()

        assertEquals(0, orcaCli(iphone).run(listOf("device", "acquire", "--ios", "--json")))
        assertEquals("unsupported", json().getValue("orcaView").jsonPrimitive.content)
        assertTrue("Orca can't show a physical iPhone or iPad" in err, err.toString())
        assertEquals(1, open.attachedEmulators.size)
    }

    @Test
    fun `usage errors name the action and options`() {
        assertEquals(2, run("device"))
        assertEquals(2, run("device", "pair"))
        assertEquals(2, run("device", "list", "extra"))
        assertEquals(2, run("device", "status", "--all"))
        assertEquals(2, run("device", "release", "--model", "a55"))
        assertEquals(2, run("device", "release", "--wait"))
        assertEquals(2, run("device", "status", "--android"))
        assertEquals(2, run("device", "acquire", "--android", "--ios"))
        assertEquals(2, run("device", "acquire", "a", "b"))
        assertEquals(2, run("device", "rename", "a55"))
        assertEquals(2, run("device", "forget"))
        assertEquals(2, run("device", "acquire", "--bogus"))
        assertEquals("", out.toString())
    }

    @Test
    fun `status names the held device and the waiting platform`() {
        inventory.devices = listOf(recorded(pixel), recorded(a55, alias = "a55"))
        devices.connected = listOf(pixel)
        holding(admin1, task1, pixel)
        holding(admin2, task2, a55)
        queue.write(WaitQueueName.IOS_DEVICE, listOf(WaitTicket("ticket", admin2, task2, ProcessIdentity(3000, 1), now.toEpochMilli(), "platform=ios")))

        assertEquals(0, run("status"), err.toString())

        val lines = out.lines()
        assertTrue(lines.single { it.startsWith(task1) }.contains("android-device(Pixel 8)"), out.toString())
        val second = lines.single { it.startsWith(task2) }
        assertTrue("android-device(a55, disconnected)" in second && "ios-device(waiting 1/1)" in second, second)
    }

    @Test
    fun `status json adds each worktree's devices`() {
        inventory.devices = listOf(recorded(pixel))
        devices.connected = listOf(pixel)
        holding(admin1, task1, pixel)
        queue.write(WaitQueueName.ANDROID_DEVICE, listOf(WaitTicket("ticket", admin2, task2, ProcessIdentity(3000, 1), now.toEpochMilli(), "platform=android")))

        assertEquals(0, run("status", "--json"), err.toString())

        val worktrees = json().getValue("worktrees").jsonArray.map { it.jsonObject }.associateBy { it.getValue("path").jsonPrimitive.content }
        val held = worktrees.getValue(task1).getValue("devices").jsonObject
        assertEquals("holder", held.getValue("android").jsonObject.getValue("role").jsonPrimitive.content)
        assertEquals("Pixel 8", held.getValue("android").jsonObject.getValue("model").jsonPrimitive.content)
        assertEquals(JsonNull, held["ios"])
        val waiting = worktrees.getValue(task2).getValue("devices").jsonObject.getValue("android").jsonObject
        assertEquals("waiting", waiting.getValue("role").jsonPrimitive.content)
        assertEquals(1, waiting.getValue("position").jsonPrimitive.int)
        assertEquals(JsonNull, worktrees.getValue(base).getValue("devices").jsonObject["android"])
    }

    @Test
    fun `status doesn't look for devices when none is held or waited for`() {
        devices.connected = listOf(pixel)

        assertEquals(0, run("status"))

        assertEquals(0, devices.lists)
    }

    @Test
    fun `pool status lists the physical devices with holders and waiters`() {
        inventory.devices = listOf(recorded(pixel), recorded(a55, alias = "a55"), recorded(iphone, lent = false))
        devices.connected = listOf(pixel, a55)
        holding(admin1, task1, pixel)
        queue.write(WaitQueueName.ANDROID_DEVICE, listOf(WaitTicket("ticket", admin2, task2, ProcessIdentity(3000, 1), now.toEpochMilli(), "platform=android\nname=pixel 8")))

        assertEquals(0, run("pool", "status", workingDirectory = base), err.toString())

        val lines = out.lines()
        val start = lines.indexOf("Android physical devices: 1 of 2 free")
        assertTrue(start >= 0, out.toString())
        assertEquals("  Pixel 8 held by main-repo · TASK-1 until ${clock(now + Duration.ofMinutes(19))}", lines[start + 1])
        assertEquals("  a55 free", lines[start + 2])
        assertEquals("  1. main-repo · TASK-2 waiting since ${clock(now)}", lines[start + 3])
        assertTrue(lines.none { it.startsWith("iOS physical devices") }, out.toString())
    }

    @Test
    fun `pool status json adds the physical devices`() {
        inventory.devices = listOf(recorded(pixel), recorded(a55))
        holding(admin1, task1, a55)

        assertEquals(0, run("pool", "status", "--json", workingDirectory = base), err.toString())

        val physical = json().getValue("physicalDevices").jsonObject
        assertEquals(2, physical.getValue("android").jsonObject.getValue("devices").jsonArray.size)
        assertEquals(emptyList(), physical.getValue("android").jsonObject.getValue("waiting").jsonArray.toList())
        assertEquals(emptyList(), physical.getValue("ios").jsonObject.getValue("waiting").jsonArray.toList())
        val held = physical.getValue("android").jsonObject.getValue("devices").jsonArray.map { it.jsonObject }.single { it.getValue("id").jsonPrimitive.content == a55.id }
        assertEquals(task1, held.getValue("holder").jsonObject.getValue("worktree").jsonPrimitive.content)
        assertEquals(false, held.getValue("holder").jsonObject.getValue("agentWorking").jsonPrimitive.boolean)
    }

    @Test
    fun `pool status shows a disconnected holder`() {
        inventory.devices = listOf(recorded(pixel))
        holding(admin1, task1, pixel)

        assertEquals(0, run("pool", "status", workingDirectory = base))

        assertTrue(out.lines().contains("  Pixel 8 held by main-repo · TASK-1 until ${clock(now + Duration.ofMinutes(19))}, disconnected"), out.toString())
        assertTrue(out.lines().contains("Android physical devices: 0 of 0 free"), out.toString())
    }

    @Test
    fun `the environment delivers the held device next to the emulator`() {
        inventory.devices = listOf(recorded(pixel))
        devices.connected = listOf(pixel.copy(transport = "192.168.1.20:37000", connection = DeviceConnection.NETWORK))
        holding(admin1, task1, pixel)

        assertEquals(0, run("env"), err.toString())

        assertTrue("export SHOGUN_ANDROID_DEVICE_SERIAL='192.168.1.20:37000'" in out, out.toString())
    }

    @Test
    fun `release, status and acquire take a worktree path`() {
        devices.connected = listOf(pixel)

        assertEquals(0, run("device", "acquire", task2, "--android", workingDirectory = base), err.toString())
        assertEquals(admin2, leases.leases.leases.single().adminDirectory)
        out.clear()
        assertEquals(0, run("device", "status", task2, workingDirectory = base))
        assertTrue(out.startsWith("android: Pixel 8"), out.toString())
        out.clear()
        assertEquals(0, run("device", "release", task2, workingDirectory = base))
        assertEquals("released Android device Pixel 8\n", out.toString())
    }

    @Test
    fun `acquiring again a disconnected held device reports nothing about Orca`() {
        inventory.devices = listOf(recorded(pixel))
        holding(admin1, task1, pixel)

        assertEquals(0, run("device", "acquire", "--android", "--json"), err.toString())

        assertEquals(JsonNull, json()["orcaView"])
        assertEquals(true, json().getValue("renewed").jsonPrimitive.boolean)
    }

    @Test
    fun `the Orca step is skipped while the simulator is booted and reports a failure`() {
        provisioning = FakeRepositoryProvisioningSource(
            byRepository = mapOf(
                base to app.luxion.shogunai.domain.model.RepositoryProvisioning(
                    providers = app.luxion.shogunai.domain.model.RepositoryProviders(iosSimulator = app.luxion.shogunai.domain.model.IosSimulatorProvider()),
                ),
            ),
        )
        environmentStore.resourcesByAdminDirectory[admin1] = mapOf("ios-simulator" to "SIM-1")
        simulators.addExisting("SIM-1", "ShogunAi · main-repo · TASK-1", app.luxion.shogunai.domain.model.SimulatorState.BOOTED)
        devices.connected = listOf(pixel)
        val listed = listOf(OrcaWorktree(task1, createdByOrca = false))

        assertEquals(0, cli(orcaClient = FakeOrcaClient(psRows = listed)).run(listOf("device", "acquire", "--android", "--json")), err.toString())
        assertEquals("skipped", json().getValue("orcaView").jsonPrimitive.content)
        assertTrue("the worktree's simulator is booted" in err, err.toString())
        out.clear()
        environmentStore.resourcesByAdminDirectory.remove(admin1)

        val failing = FakeOrcaClient(psRows = listed, attachFailure = app.luxion.shogunai.domain.model.WorktreeError.OrcaCommandFailed(listOf("orca"), "no device"))
        assertEquals(0, cli(orcaClient = failing).run(listOf("device", "acquire", "--android")))
        assertTrue("couldn't show Pixel 8 in Orca: no device" in err, err.toString())
        assertTrue(out.startsWith("already held"), out.toString())
    }

    @Test
    fun `name and model only apply to acquire`() {
        assertEquals(2, run("device", "status", "--name", "a55"))
    }
}
