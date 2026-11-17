package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeAppleSimulators
import app.luxion.shogunai.domain.FakeProjectRepository
import app.luxion.shogunai.domain.FakeRepositoryProvisioningSource
import app.luxion.shogunai.domain.io.DyldCacheSource
import app.luxion.shogunai.domain.model.DyldCacheFolder
import app.luxion.shogunai.domain.model.DyldCaches
import app.luxion.shogunai.domain.model.IosSimulatorProvider
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.RepositoryProviders
import app.luxion.shogunai.domain.model.RepositoryProvisioning
import app.luxion.shogunai.domain.model.RuntimeRefusal
import app.luxion.shogunai.domain.model.RuntimeUser
import app.luxion.shogunai.domain.model.SimulatorDevice
import app.luxion.shogunai.domain.model.SimulatorRuntime
import app.luxion.shogunai.domain.model.SimulatorRuntimeFailure
import app.luxion.shogunai.domain.model.SimulatorRuntimeImage
import app.luxion.shogunai.domain.model.SimulatorRuntimeProblem
import app.luxion.shogunai.domain.model.SimulatorSettings
import app.luxion.shogunai.domain.model.SimulatorState
import app.luxion.shogunai.domain.model.SimulatorStorage
import app.luxion.shogunai.domain.model.StoredRuntime
import app.luxion.shogunai.domain.model.RuntimeDeletion
import app.luxion.shogunai.domain.model.UnavailableDeletion
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.model.dyldRemovalCommand
import kotlinx.coroutines.test.runTest
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SimulatorStorageUseCasesTest {

    private val simulators = FakeAppleSimulators(installedRuntimes = listOf(runtime("26.5"), runtime("27.0")))
    private var dyld = DyldCacheSource.NONE
    private val projects = FakeProjectRepository()
    private val declared = mutableMapOf<String, RepositoryProvisioning>()
    private val recorded = mutableMapOf<String, Set<String>>()

    private val list get() = ListSimulatorStorageUseCase(simulators, dyld, projects, FakeRepositoryProvisioningSource(declared)) { path, _ -> Result.success(recorded[path].orEmpty()) }
    private val delete get() = DeleteSimulatorRuntimeUseCase(list, simulators)

    private fun install(version: String, sizeBytes: Long? = GB, deletable: Boolean = true, identifier: String = "IMAGE-$version", platform: String = "iOS") {
        simulators.images += SimulatorRuntimeImage(
            identifier = identifier,
            runtimeIdentifier = runtimeId(version, platform),
            version = version,
            build = "B$version",
            sizeBytes = sizeBytes,
            lastUsedAt = Instant.parse("2026-11-14T12:00:00Z"),
            deletable = deletable,
        )
    }

    private fun declare(path: String, name: String, settings: SimulatorSettings = SimulatorSettings(), preferred: String? = null) {
        projects.save(Project(name, name, ProjectConfig(path, "$path-worktrees", emptyList(), simulator = settings)))
        declared[path] = RepositoryProvisioning(providers = RepositoryProviders(iosSimulator = IosSimulatorProvider(runtime = preferred)))
    }

    @Test
    fun `lists every runtime with its size and the total`() = runTest {
        install("26.5", sizeBytes = 8 * GB)
        install("27.0", sizeBytes = 9 * GB)
        install("11.0", sizeBytes = null, platform = "watchOS")

        val storage = list().getOrThrow()

        assertEquals(listOf("iOS 27.0", "iOS 26.5", "watchOS 11.0"), storage.runtimes.map { it.image.name })
        assertEquals(17 * GB, storage.runtimeBytes)
        assertTrue(storage.supported)
    }

    @Test
    fun `no runtime installed lists nothing`() = runTest {
        simulators.installedRuntimes = emptyList()

        val storage = list().getOrThrow()

        assertEquals(emptyList(), storage.runtimes)
        assertEquals(0L, storage.runtimeBytes)
    }

    @Test
    fun `outside macOS nothing is read`() = runTest {
        simulators.supported = false
        install("26.5")

        val storage = list().getOrThrow()

        assertFalse(storage.supported)
        assertEquals(0, simulators.calls)
    }

    @Test
    fun `the newest iOS runtime is the default and the rest have no users`() = runTest {
        install("26.5")
        install("27.0")

        val runtimes = list().getOrThrow().runtimes.associateBy { it.image.version }

        assertEquals(listOf(RuntimeUser.Default), runtimes.getValue("27.0").users)
        assertEquals(RuntimeRefusal.IN_USE, runtimes.getValue("27.0").refusal)
        assertEquals(emptyList(), runtimes.getValue("26.5").users)
        assertTrue(runtimes.getValue("26.5").canDelete)
    }

    @Test
    fun `a worktree's recorded simulator uses its runtime`() = runTest {
        install("26.5")
        declare("/projects/main-repo", "main-repo")
        simulators.addDevice(SimulatorDevice("UDID-1", "ShogunAi · main-repo · TASK-123", SimulatorState.SHUTDOWN, runtimeId("26.5")))
        simulators.addDevice(SimulatorDevice("UDID-2", "ShogunAi · main-repo · TASK-9", SimulatorState.SHUTDOWN, runtimeId("26.5")))
        recorded["/projects/main-repo"] = setOf("UDID-1")

        val runtime = list().getOrThrow().runtimes.single()

        assertEquals(listOf(RuntimeUser.Worktree("ShogunAi · main-repo · TASK-123")), runtime.users)
        assertEquals("used by main-repo · TASK-123", runtime.refusalReason)
    }

    @Test
    fun `a project's setting or preference names its runtime`() = runTest {
        install("26.5")
        install("25.0")
        declare("/projects/main-repo", "main-repo", preferred = "iOS 26.5")
        declare("/projects/other-repo", "other-repo", settings = SimulatorSettings(runtime = "ios 25.0"))

        val runtimes = list().getOrThrow().runtimes.associateBy { it.image.version }

        assertEquals(listOf(RuntimeUser.Project("main-repo")), runtimes.getValue("26.5").users)
        assertEquals(listOf(RuntimeUser.Project("other-repo")), runtimes.getValue("25.0").users)
    }

    @Test
    fun `a recorded simulator without a runtime or unreadable records use nothing`() = runTest {
        install("26.5")
        declare("/projects/main-repo", "main-repo")
        simulators.addDevice(SimulatorDevice("UDID-1", "ShogunAi · main-repo · TASK-123", SimulatorState.SHUTDOWN))
        recorded["/projects/main-repo"] = setOf("UDID-1")
        val failing = ListSimulatorStorageUseCase(simulators, dyld, projects, FakeRepositoryProvisioningSource(declared)) { _, _ ->
            Result.failure(IllegalStateException("not a repository"))
        }

        assertEquals(emptyList(), list().getOrThrow().runtimes.single().users)
        assertEquals(emptyList(), failing().getOrThrow().runtimes.single().users)
    }

    @Test
    fun `without an iOS runtime to create simulators with there is no default`() = runTest {
        simulators.installedRuntimes = emptyList()
        install("26.5")

        assertEquals(emptyList(), list().getOrThrow().runtimes.single().users)
    }

    @Test
    fun `users are labelled for people`() {
        assertEquals("main-repo · TASK-1", RuntimeUser.Worktree("ShogunAi · main-repo · TASK-1").label)
        assertEquals("main-repo", RuntimeUser.Project("main-repo").label)
        assertEquals("default runtime for new simulators", RuntimeUser.Default.label)
    }

    @Test
    fun `unknown sizes count as nothing`() {
        val image = SimulatorRuntimeImage("ID", runtimeId("25.0"), "25.0", "B", null, null, true)
        val device = SimulatorDevice("A", "iPhone", SimulatorState.SHUTDOWN, isAvailable = false)

        assertEquals(null, StoredRuntime(image).refusalReason)
        assertEquals(0L, RuntimeDeletion(StoredRuntime(image), listOf(device)).bytesFreed)
        assertEquals(0L, RuntimeDeletion(StoredRuntime(image), listOf(device)).unavailableBytes)
        assertEquals(0L, UnavailableDeletion(listOf(device)).bytesFreed)
        assertEquals(0L, DyldCaches("B", listOf(DyldCacheFolder("B", "/caches/B", null))).totalBytes)
        assertEquals(0L, SimulatorStorage(runtimes = listOf(StoredRuntime(image)), unavailableDevices = listOf(device)).unavailableBytes)
    }

    @Test
    fun `projects without the provider or unreadable ones use nothing`() = runTest {
        install("26.5")
        projects.save(Project("plain", "plain", ProjectConfig("/projects/plain", "/w", emptyList(), simulator = SimulatorSettings(runtime = "iOS 26.5"))))
        val unreadable = FakeRepositoryProvisioningSource(unreadable = setOf("/projects/plain"))

        val users = ListSimulatorStorageUseCase(simulators, dyld, projects, unreadable) { _, _ -> Result.success(emptySet()) }().getOrThrow().runtimes.single().users

        assertEquals(emptyList(), users)
        assertEquals(emptyList(), list().getOrThrow().runtimes.single().users)
    }

    @Test
    fun `bundled runtimes and runtimes with a booted simulator are refused`() = runTest {
        install("26.5", deletable = false)
        install("25.0")
        simulators.addDevice(SimulatorDevice("U", "iPhone 18", SimulatorState.BOOTED, runtimeId("25.0")))

        val runtimes = list().getOrThrow().runtimes.associateBy { it.image.version }

        assertEquals(RuntimeRefusal.BUNDLED, runtimes.getValue("26.5").refusal)
        assertEquals("it comes with Xcode", runtimes.getValue("26.5").refusalReason)
        assertEquals(RuntimeRefusal.BOOTED, runtimes.getValue("25.0").refusal)
        assertEquals("a simulator on it is booted", runtimes.getValue("25.0").refusalReason)
    }

    @Test
    fun `only the newest duplicate image is the default`() = runTest {
        simulators.installedRuntimes = listOf(runtime("27.0"))
        install("27.0", identifier = "NEW")
        simulators.images += simulators.images.single().copy(identifier = "OLD", version = "26.9")

        val runtimes = list().getOrThrow().runtimes.associateBy { it.image.identifier }

        assertEquals(listOf(RuntimeUser.Default), runtimes.getValue("NEW").users)
        assertTrue(runtimes.getValue("OLD").canDelete)
    }

    @Test
    fun `lists unavailable devices and the dyld caches`() = runTest {
        simulators.addDevice(SimulatorDevice("A", "iPhone 15", SimulatorState.SHUTDOWN, runtimeId("18.5"), isAvailable = false, dataBytes = 2 * GB))
        simulators.addDevice(SimulatorDevice("B", "iPhone 16", SimulatorState.SHUTDOWN, runtimeId("18.5"), isAvailable = false))
        simulators.addDevice(SimulatorDevice("C", "iPhone 18", SimulatorState.SHUTDOWN, runtimeId("27.0")))
        val stale = DyldCacheFolder("26A434", "/caches/26A434", 21 * GB)
        val current = DyldCacheFolder("26B100", "/caches/26B100", 4 * GB)
        dyld = fixedDyld("26B100", listOf(stale, current))

        val storage = list().getOrThrow()

        assertEquals(listOf("A", "B"), storage.unavailableDevices.map { it.udid })
        assertEquals(2 * GB, storage.unavailableBytes)
        assertEquals(DyldCaches("26B100", listOf(stale, current)), storage.dyldCaches)
        assertEquals(listOf(stale), storage.dyldCaches.stale)
        assertEquals(25 * GB, storage.dyldCaches.totalBytes)
        assertEquals("sudo rm -rf /caches/26A434", dyldRemovalCommand(stale))
    }

    @Test
    fun `nothing is stale when the running build is unknown or the only one`() = runTest {
        val folder = DyldCacheFolder("26B100", "/caches/26B100", 4 * GB)

        assertEquals(emptyList(), DyldCaches("26B100", listOf(folder)).stale)
        assertEquals(emptyList(), DyldCaches(null, listOf(folder)).stale)
    }

    @Test
    fun `deletes an unused runtime and reports the space and the devices it leaves`() = runTest {
        install("25.0", sizeBytes = 8 * GB)
        install("27.0")
        simulators.addDevice(SimulatorDevice("A", "iPhone 15", SimulatorState.SHUTDOWN, runtimeId("25.0"), dataBytes = GB))

        val deletion = delete("25.0").getOrThrow()

        assertEquals(listOf("IMAGE-25.0"), simulators.deletedRuntimes)
        assertEquals(8 * GB, deletion.bytesFreed)
        assertEquals(listOf("A"), deletion.unavailableLeft.map { it.udid })
        assertEquals(GB, deletion.unavailableBytes)
    }

    @Test
    fun `a runtime used since it was listed is refused`() = runTest {
        install("25.0")
        install("27.0")
        assertTrue(list().getOrThrow().runtimes.first { it.image.version == "25.0" }.canDelete)
        declare("/projects/main-repo", "main-repo")
        simulators.addDevice(SimulatorDevice("UDID-1", "ShogunAi · main-repo · TASK-123", SimulatorState.SHUTDOWN, runtimeId("25.0")))
        recorded["/projects/main-repo"] = setOf("UDID-1")

        val problem = (delete("IMAGE-25.0").exceptionOrNull() as SimulatorRuntimeFailure).problem

        assertIs<SimulatorRuntimeProblem.Refused>(problem)
        assertEquals("iOS 25.0 can't be deleted: used by main-repo · TASK-123", problem.message)
        assertEquals(emptyList(), simulators.deletedRuntimes)
    }

    @Test
    fun `matches a runtime by identifier, runtime identifier, name or version`() = runTest {
        install("25.0")
        install("27.0")

        assertEquals("IMAGE-25.0", delete.find("IMAGE-25.0").getOrThrow().image.identifier)
        assertEquals("IMAGE-25.0", delete.find(runtimeId("25.0")).getOrThrow().image.identifier)
        assertEquals("IMAGE-25.0", delete.find("ios 25.0").getOrThrow().image.identifier)
        assertEquals("IMAGE-25.0", delete.find(" 25.0 ").getOrThrow().image.identifier)
    }

    @Test
    fun `an unknown or ambiguous runtime deletes nothing`() = runTest {
        install("11.0", identifier = "WATCH", platform = "watchOS")
        install("11.0", identifier = "TV", platform = "tvOS")

        val unknown = (delete("18.5").exceptionOrNull() as SimulatorRuntimeFailure).problem
        val ambiguous = (delete("11.0").exceptionOrNull() as SimulatorRuntimeFailure).problem

        assertEquals("No installed simulator runtime matches \"18.5\"; installed: tvOS 11.0, watchOS 11.0", unknown.message)
        assertEquals("\"11.0\" matches several simulator runtimes; name one of: tvOS 11.0 (TV), watchOS 11.0 (WATCH)", ambiguous.message)
        assertEquals(emptyList(), simulators.deletedRuntimes)
        simulators.images.clear()
        assertEquals(
            "No installed simulator runtime matches \"x\"; no runtime is installed",
            (delete("x").exceptionOrNull() as SimulatorRuntimeFailure).problem.message,
        )
    }

    @Test
    fun `a failing deletion is reported`() = runTest {
        install("25.0")
        install("27.0")
        simulators.deleteRuntimeFailure = IllegalStateException("Permission denied")

        assertEquals("Permission denied", delete("25.0").exceptionOrNull()?.message)
    }

    @Test
    fun `deleting a runtime outside macOS fails`() = runTest {
        simulators.supported = false

        assertIs<WorktreeError.IosSimulatorsNotSupported>(delete("25.0").exceptionOrNull())
        assertIs<WorktreeError.IosSimulatorsNotSupported>(DeleteUnavailableDevicesUseCase(simulators)().exceptionOrNull())
    }

    @Test
    fun `deletes the unavailable devices and reports their size`() = runTest {
        simulators.addDevice(SimulatorDevice("A", "iPhone 15", SimulatorState.SHUTDOWN, runtimeId("18.5"), isAvailable = false, dataBytes = 3 * GB))
        simulators.addDevice(SimulatorDevice("C", "iPhone 18", SimulatorState.SHUTDOWN, runtimeId("27.0")))

        val deletion = DeleteUnavailableDevicesUseCase(simulators)().getOrThrow()

        assertEquals(3 * GB, deletion.bytesFreed)
        assertEquals(listOf("C"), simulators.devicesByUdid.keys.toList())
        assertEquals(1, simulators.unavailableDeletions)
    }

    @Test
    fun `with no unavailable device nothing is deleted`() = runTest {
        val deletion = DeleteUnavailableDevicesUseCase(simulators)().getOrThrow()

        assertEquals(emptyList(), deletion.devices)
        assertEquals(0, simulators.unavailableDeletions)
    }

    private fun fixedDyld(build: String?, folders: List<DyldCacheFolder>) = object : DyldCacheSource {
        override suspend fun folders(): List<DyldCacheFolder> = folders
        override suspend fun currentBuild(): String? = build
    }

    private companion object {
        const val GB = 1_000_000_000L

        fun runtimeId(version: String, platform: String = "iOS") = "com.apple.CoreSimulator.SimRuntime.$platform-${version.replace('.', '-')}"

        fun runtime(version: String) = SimulatorRuntime(runtimeId(version), "iOS $version", version, "iOS", listOf(FakeAppleSimulators.IPHONE_16))
    }
}
