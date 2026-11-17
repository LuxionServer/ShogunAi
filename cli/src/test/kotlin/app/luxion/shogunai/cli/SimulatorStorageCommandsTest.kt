package app.luxion.shogunai.cli

import app.luxion.shogunai.domain.FakeAndroidSdk
import app.luxion.shogunai.domain.FakeAppleSimulators
import app.luxion.shogunai.domain.FakeDerivedDataSource
import app.luxion.shogunai.domain.FakeEnvironmentStateStore
import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.FakeGradleDaemonSource
import app.luxion.shogunai.domain.FakeHerdrConfigSource
import app.luxion.shogunai.domain.FakeKmpTestWiring
import app.luxion.shogunai.domain.FakeMachineCapacity
import app.luxion.shogunai.domain.FakeOrcaClient
import app.luxion.shogunai.domain.FakePortProbe
import app.luxion.shogunai.domain.FakeProjectRepository
import app.luxion.shogunai.domain.FakeRepositoryProvisioningSource
import app.luxion.shogunai.domain.FakeResourceLimitsRepository
import app.luxion.shogunai.domain.ScriptedShellCommandExecutor
import app.luxion.shogunai.domain.executor.CommandResult
import app.luxion.shogunai.domain.io.DyldCacheSource
import app.luxion.shogunai.domain.model.DyldCacheFolder
import app.luxion.shogunai.domain.model.SimulatorDevice
import app.luxion.shogunai.domain.model.SimulatorRuntime
import app.luxion.shogunai.domain.model.SimulatorRuntimeImage
import app.luxion.shogunai.domain.model.SimulatorState
import app.luxion.shogunai.engine.ShogunEngine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SimulatorStorageCommandsTest {

    private val gigabyte = 1_073_741_824L
    private val home = "/home/dev"
    private val out = StringBuilder()
    private val err = StringBuilder()
    private val executor = object : ScriptedShellCommandExecutor() {
        override suspend fun respond(command: List<String>, workingDirectory: String?): CommandResult = CommandResult(command, 0, "", "")
    }
    private val simulators = FakeAppleSimulators(installedRuntimes = listOf(runtime("27.0")))
    private var dyld: DyldCacheSource = DyldCacheSource.NONE

    private fun install(version: String, sizeBytes: Long?, build: String = "B$version", lastUsed: String? = "2026-11-14T12:00:00Z") {
        simulators.images += SimulatorRuntimeImage(
            "IMAGE-$version",
            runtimeId(version),
            version,
            build,
            sizeBytes,
            lastUsed?.let(Instant::parse),
            deletable = true,
        )
    }

    private fun cli() = ShogunCli(
        engine = ShogunEngine(
            cliEnginePorts(
                executor = executor,
                fileManager = FakeFileManager(),
                projectRepository = FakeProjectRepository(),
                provisioningSource = FakeRepositoryProvisioningSource(),
                gradleDaemons = FakeGradleDaemonSource(),
                gradleUserHome = "$home/.gradle",
                environmentStateStore = FakeEnvironmentStateStore(),
                portProbe = FakePortProbe(),
                androidSdk = FakeAndroidSdk(),
                appleSimulators = simulators,
                kmpTestWiring = FakeKmpTestWiring(),
                resourceLimits = FakeResourceLimitsRepository(),
                machineCapacity = FakeMachineCapacity(),
                derivedData = FakeDerivedDataSource(),
                herdrConfig = FakeHerdrConfigSource(),
                orca = FakeOrcaClient(installed = false),
                workingDirectory = home,
                environment = hostEnvironment(),
                dyldCaches = dyld,
            ),
        ),
        workingDirectory = home,
        out = out,
        err = err,
        canonicalPath = { it.trimEnd('/') },
        environment = hostEnvironment(),
    )

    private fun json(): JsonObject = Json.parseToJsonElement(out.toString()).jsonObject

    @Test
    fun `storage lists runtimes, unavailable devices and dyld caches`() {
        install("27.0", 8 * gigabyte)
        install("25.0", null, build = "", lastUsed = null)
        simulators.addDevice(SimulatorDevice("UDID-A", "iPhone 15", SimulatorState.SHUTDOWN, runtimeId("18.5"), isAvailable = false, dataBytes = gigabyte))
        dyld = fixedDyld(
            "26B100",
            DyldCacheFolder("26A434", "/caches/26A434", 2 * gigabyte),
            DyldCacheFolder("26B100", "/caches/26B100", null),
        )

        val exitCode = cli().run(listOf("simulator", "storage"))

        assertEquals(0, exitCode, err.toString())
        assertEquals(
            """
            Simulator runtimes, 8.0 GB:
              iOS 27.0 (B27.0)  8.0 GB  last used 2026-11-14  kept: used by default runtime for new simulators
              iOS 25.0 (unknown build)  size unknown  last used never  unused: shogun simulator runtime delete IMAGE-25.0
            1 simulator device is unavailable, 1.0 GB: delete them with 'shogun simulator clean-unavailable --yes'
            Simulator dyld caches, 2.0 GB:
              26A434  2.0 GB  from another macOS build, remove it yourself: sudo rm -rf /caches/26A434
              26B100  size unknown

            """.trimIndent(),
            out.toString(),
        )
    }

    @Test
    fun `storage with nothing installed says so`() {
        cli().run(listOf("simulator", "storage"))

        assertEquals("No simulator runtime is installed.\nNo simulator device is unavailable.\n", out.toString())
    }

    @Test
    fun `storage outside macOS says it doesn't apply`() {
        simulators.supported = false

        assertEquals(0, cli().run(listOf("simulator", "storage")))
        assertEquals("$NOT_SUPPORTED\n", out.toString())
        out.clear()
        cli().run(listOf("simulator", "storage", "--json"))
        assertEquals(false, json().getValue("supported").jsonPrimitive.boolean)
    }

    @Test
    fun `storage json gives every runtime, device and cache folder`() {
        install("27.0", 8 * gigabyte)
        install("25.0", gigabyte, lastUsed = null)
        simulators.addDevice(SimulatorDevice("UDID-A", "iPhone 15", SimulatorState.SHUTDOWN, runtimeId("18.5"), isAvailable = false, dataBytes = gigabyte))
        dyld = fixedDyld("26B100", DyldCacheFolder("26A434", "/caches/26A434", 2 * gigabyte))

        cli().run(listOf("simulator", "storage", "--json"))

        val runtimes = json().getValue("runtimes").jsonArray.map { it.jsonObject }
        assertEquals(listOf("IMAGE-27.0", "IMAGE-25.0"), runtimes.map { it.getValue("identifier").jsonPrimitive.content })
        assertEquals(listOf(false, true), runtimes.map { it.getValue("canDelete").jsonPrimitive.boolean })
        assertEquals(listOf("2026-11-14T12:00:00Z", "null"), runtimes.map { it.getValue("lastUsedAt").toString().trim('"') })
        assertEquals("default runtime for new simulators", runtimes.first().getValue("users").jsonArray.single().jsonPrimitive.content)
        assertEquals(9 * gigabyte, json().getValue("runtimeBytes").jsonPrimitive.long)
        assertEquals("UDID-A", json().getValue("unavailableDevices").jsonArray.single().jsonObject.getValue("udid").jsonPrimitive.content)
        val folder = json().getValue("dyldCaches").jsonObject.getValue("folders").jsonArray.single().jsonObject
        assertEquals(true, folder.getValue("stale").jsonPrimitive.boolean)
        assertEquals("sudo rm -rf /caches/26A434", folder.getValue("removalCommand").jsonPrimitive.content)
    }

    @Test
    fun `runtime delete without yes only says what it would delete`() {
        install("27.0", 8 * gigabyte)
        install("25.0", gigabyte)

        assertEquals(0, cli().run(listOf("simulator", "runtime", "delete", "25.0")))

        assertEquals("Would delete iOS 25.0 (B25.0), 1.0 GB.\nRun again with --yes to delete it.\n", out.toString())
        assertEquals(emptyList(), simulators.deletedRuntimes)
    }

    @Test
    fun `runtime delete preview of a kept runtime says why`() {
        install("27.0", 8 * gigabyte)

        cli().run(listOf("simulator", "runtime", "delete", "27.0"))

        assertTrue(out.contains("It can't be deleted now: used by default runtime for new simulators."), out.toString())
        out.clear()
        cli().run(listOf("simulator", "runtime", "delete", "27.0", "--json"))
        assertEquals(false, json().getValue("deleted").jsonPrimitive.boolean)
        assertEquals("IMAGE-27.0", json().getValue("runtime").jsonObject.getValue("identifier").jsonPrimitive.content)
    }

    @Test
    fun `runtime delete with yes deletes it and points to the devices it leaves`() {
        install("27.0", 8 * gigabyte)
        install("25.0", gigabyte)
        simulators.addDevice(SimulatorDevice("UDID-A", "iPhone 15", SimulatorState.SHUTDOWN, runtimeId("25.0"), dataBytes = gigabyte))

        assertEquals(0, cli().run(listOf("simulator", "runtime", "delete", "iOS 25.0", "--yes")))

        assertEquals(listOf("IMAGE-25.0"), simulators.deletedRuntimes)
        assertEquals(
            "Deleted iOS 25.0 (B25.0), freed 1.0 GB.\n" +
                "1 simulator device is now unavailable (1.0 GB); delete them with 'shogun simulator clean-unavailable --yes'.\n",
            out.toString(),
        )
    }

    @Test
    fun `runtime delete json reports the space freed`() {
        install("27.0", 8 * gigabyte)
        install("25.0", gigabyte)

        cli().run(listOf("simulator", "runtime", "delete", "25.0", "-y", "--json"))

        assertEquals(true, json().getValue("deleted").jsonPrimitive.boolean)
        assertEquals(gigabyte, json().getValue("bytesFreed").jsonPrimitive.long)
        assertEquals(0, json().getValue("unavailableDevices").jsonArray.size)
    }

    @Test
    fun `a deletion that leaves no device says only the space freed`() {
        install("27.0", 8 * gigabyte)
        install("25.0", gigabyte)

        cli().run(listOf("simulator", "runtime", "delete", "IMAGE-25.0", "--yes"))

        assertEquals("Deleted iOS 25.0 (B25.0), freed 1.0 GB.\n", out.toString())
    }

    @Test
    fun `a refused or unknown runtime fails with the reason`() {
        install("27.0", 8 * gigabyte)

        assertEquals(1, cli().run(listOf("simulator", "runtime", "delete", "27.0", "--yes")))
        assertEquals("shogun: iOS 27.0 can't be deleted: used by default runtime for new simulators\n", err.toString())
        err.clear()
        assertEquals(1, cli().run(listOf("simulator", "runtime", "delete", "18.5", "--yes")))
        assertEquals("shogun: No installed simulator runtime matches \"18.5\"; installed: iOS 27.0\n", err.toString())
        assertEquals(emptyList(), simulators.deletedRuntimes)
    }

    @Test
    fun `clean-unavailable without yes lists the devices`() {
        simulators.addDevice(SimulatorDevice("UDID-A", "iPhone 15", SimulatorState.SHUTDOWN, runtimeId("18.5"), isAvailable = false, dataBytes = gigabyte))
        simulators.addDevice(SimulatorDevice("UDID-B", "iPhone 16", SimulatorState.SHUTDOWN, runtimeId("18.5"), isAvailable = false))

        assertEquals(0, cli().run(listOf("simulator", "clean-unavailable")))

        assertEquals(
            "2 simulator devices are unavailable, 1.0 GB:\n  iPhone 15 UDID-A  1.0 GB\n  iPhone 16 UDID-B  size unknown\nRun again with --yes to delete them.\n",
            out.toString(),
        )
        assertEquals(0, simulators.unavailableDeletions)
    }

    @Test
    fun `clean-unavailable with yes deletes them`() {
        simulators.addDevice(SimulatorDevice("UDID-A", "iPhone 15", SimulatorState.SHUTDOWN, runtimeId("18.5"), isAvailable = false, dataBytes = gigabyte))

        cli().run(listOf("simulator", "clean-unavailable", "--yes"))

        assertEquals("Deleted 1 unavailable simulator device, freed 1.0 GB.\n", out.toString())
        assertEquals(1, simulators.unavailableDeletions)
    }

    @Test
    fun `clean-unavailable counts several devices`() {
        simulators.addDevice(SimulatorDevice("UDID-A", "iPhone 15", SimulatorState.SHUTDOWN, runtimeId("18.5"), isAvailable = false, dataBytes = gigabyte))
        simulators.addDevice(SimulatorDevice("UDID-B", "iPhone 16", SimulatorState.SHUTDOWN, runtimeId("18.5"), isAvailable = false, dataBytes = gigabyte))

        cli().run(listOf("simulator", "clean-unavailable", "--yes"))

        assertEquals("Deleted 2 unavailable simulator devices, freed 2.0 GB.\n", out.toString())
    }

    @Test
    fun `clean-unavailable json previews and deletes`() {
        simulators.addDevice(SimulatorDevice("UDID-A", "iPhone 15", SimulatorState.SHUTDOWN, runtimeId("18.5"), isAvailable = false, dataBytes = gigabyte))

        cli().run(listOf("simulator", "clean-unavailable", "--json"))
        assertEquals(false, json().getValue("deleted").jsonPrimitive.boolean)
        assertEquals(gigabyte, json().getValue("bytes").jsonPrimitive.long)
        out.clear()
        cli().run(listOf("simulator", "clean-unavailable", "--yes", "--json"))
        assertEquals(true, json().getValue("deleted").jsonPrimitive.boolean)
    }

    @Test
    fun `clean-unavailable with nothing unavailable says so`() {
        cli().run(listOf("simulator", "clean-unavailable"))
        cli().run(listOf("simulator", "clean-unavailable", "--yes"))

        assertEquals("No simulator device is unavailable.\nNo simulator device is unavailable.\n", out.toString())
    }

    @Test
    fun `clean-unavailable outside macOS says it doesn't apply`() {
        simulators.supported = false

        cli().run(listOf("simulator", "clean-unavailable"))
        cli().run(listOf("simulator", "clean-unavailable", "--json"))

        assertEquals("$NOT_SUPPORTED\n{\"supported\":false}\n", out.toString())
    }

    @Test
    fun `parses the storage commands`() {
        assertEquals(SimulatorStorageCommand(SimulatorStorageAction.STORAGE, json = true), parseSimulatorStorage(listOf("storage")).withJson())
        assertEquals(
            SimulatorStorageCommand(SimulatorStorageAction.RUNTIME_DELETE, runtime = "26.5", confirmed = true),
            parseSimulatorStorage(listOf("runtime", "delete", "26.5", "--yes")),
        )
        assertEquals(SimulatorStorageCommand(SimulatorStorageAction.CLEAN_UNAVAILABLE, confirmed = true), parseSimulatorStorage(listOf("clean-unavailable", "-y")))
    }

    @Test
    fun `rejects malformed storage commands`() {
        assertEquals("unknown option '--yes' for 'simulator storage'", usage("storage", "--yes"))
        assertEquals("'simulator clean-unavailable' takes no arguments, got 'x'", usage("clean-unavailable", "x"))
        assertEquals("'simulator runtime' needs the action delete", usage("runtime", "remove"))
        assertEquals("'simulator runtime delete' needs the runtime to delete, as shown by 'shogun simulator storage'", usage("runtime", "delete"))
        assertEquals("'simulator runtime delete' takes a single runtime, got '26.5' and '27.0'", usage("runtime", "delete", "26.5", "27.0"))
        assertEquals("unknown option '--all' for 'simulator runtime delete'", usage("runtime", "delete", "--all"))
    }

    @Test
    fun `cleanup never touches runtimes or unavailable devices`() {
        install("25.0", gigabyte)
        simulators.addDevice(SimulatorDevice("UDID-A", "iPhone 15", SimulatorState.SHUTDOWN, runtimeId("18.5"), isAvailable = false))

        cli().run(listOf("clean", "--yes", "--delete-simulators", "--delete-derived-data"))

        assertEquals(emptyList(), simulators.deletedRuntimes)
        assertEquals(0, simulators.unavailableDeletions)
    }

    private fun usage(vararg arguments: String): String? =
        runCatching { parseSimulatorStorage(arguments.toList()) }.exceptionOrNull()?.message

    private fun fixedDyld(build: String, vararg folders: DyldCacheFolder) = object : DyldCacheSource {
        override suspend fun folders(): List<DyldCacheFolder> = folders.toList()
        override suspend fun currentBuild(): String = build
    }

    private fun runtimeId(version: String) = "com.apple.CoreSimulator.SimRuntime.iOS-${version.replace('.', '-')}"

    private fun runtime(version: String) = SimulatorRuntime(runtimeId(version), "iOS $version", version, "iOS", listOf(FakeAppleSimulators.IPHONE_16))
}
