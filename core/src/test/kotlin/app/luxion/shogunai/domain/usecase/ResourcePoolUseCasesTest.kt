package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeAndroidSdk
import app.luxion.shogunai.domain.FakeAppleSimulators
import app.luxion.shogunai.domain.FakeEnvironmentStateStore
import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.FakeGradleDaemonSource
import app.luxion.shogunai.domain.FakePortProbe
import app.luxion.shogunai.domain.FakeRepositoryProvisioningSource
import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.model.EmulatorProvider
import app.luxion.shogunai.domain.model.EmulatorState
import app.luxion.shogunai.domain.model.IosSimulatorProvider
import app.luxion.shogunai.domain.model.PoolHolderState
import app.luxion.shogunai.domain.model.RepositoryProviders
import app.luxion.shogunai.domain.model.RepositoryProvisioning
import app.luxion.shogunai.domain.model.ResourceKind
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.coroutines.test.runTest
import java.time.Instant
import app.luxion.shogunai.domain.io.AndroidSdk
import app.luxion.shogunai.domain.io.AppleSimulators
import app.luxion.shogunai.domain.io.GradleDaemonSource
import app.luxion.shogunai.domain.model.DaemonProcess
import app.luxion.shogunai.domain.model.DaemonState
import app.luxion.shogunai.domain.model.SimulatorDevice
import app.luxion.shogunai.domain.model.SimulatorState
import app.luxion.shogunai.domain.model.Worktree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ResourcePoolUseCasesTest {

    private val repository = "/home/dev/projects/main-repo"
    private val common = "$repository/.git"
    private val otherRepository = "/home/dev/projects/api"
    private val otherCommon = "$otherRepository/.git"
    private val task1 = "/home/dev/projects/worktrees/TASK-1"
    private val task2 = "/home/dev/projects/worktrees/TASK-2"
    private val task3 = "/home/dev/projects/worktrees/TASK-3"
    private val apiTask = "/home/dev/projects/worktrees/API-1"
    private val admin1 = "$common/worktrees/TASK-1"
    private val admin2 = "$common/worktrees/TASK-2"
    private val admin3 = "$common/worktrees/TASK-3"
    private val adminApi = "$otherCommon/worktrees/API-1"

    private val git = object : ShellCommandExecutor {
        override suspend fun execute(command: List<String>, workingDirectory: String?) = FakeShellCommandExecutor.success(
            command,
            when (workingDirectory) {
                task1 -> "$admin1\n$common\n"
                task2 -> "$admin2\n$common\n"
                task3 -> "$admin3\n$common\n"
                apiTask -> "$adminApi\n$otherCommon\n"
                else -> "$common\n$common\n"
            },
        )
    }

    private val source = FakeRepositoryProvisioningSource(
        byRepository = mapOf(
            repository to RepositoryProvisioning(
                providers = RepositoryProviders(emulator = EmulatorProvider(), iosSimulator = IosSimulatorProvider()),
            ),
        ),
    )

    private val files = FakeFileManager().also {
        it.writeText("$admin1/gitdir", "$task1/.git")
        it.writeText("$admin2/gitdir", "$task2/.git")
        it.writeText("$admin3/gitdir", "$task3/.git")
        it.writeText("$adminApi/gitdir", "$apiTask/.git")
    }
    private val store = FakeEnvironmentStateStore()
    private val sdk = FakeAndroidSdk()
    private val harness = PoolHarness(git, source, store, sdk, files = files)

    private fun lastUsed(admin: String, key: String, instant: String) {
        store.writeLastUsed(admin, store.readLastUsed(admin) + (key to Instant.parse(instant).toEpochMilli()))
    }

    private fun lease(admin: String, key: String, instant: Instant = harness.now) {
        store.writeLeases(admin, store.readLeases(admin) + (key to instant.toEpochMilli()))
    }

    @Test
    fun `the emulator pool counts holders in every repository with their worktree and state`() = runTest {
        store.writePorts(admin1, mapOf("emulator" to 5554))
        store.writePorts(adminApi, mapOf("emulator" to 5556))
        sdk.states["emulator-5554"] = EmulatorState.RUNNING

        val usage = harness.readPool.emulators(repository)

        assertEquals(ResourceKind.EMULATOR, usage.kind)
        assertEquals(2, usage.inUse)
        assertEquals(
            mapOf(task1 to PoolHolderState.RUNNING, apiTask to PoolHolderState.STOPPED),
            usage.holders.associate { it.worktreePath.orEmpty() to it.state },
        )
        assertEquals(mapOf(task1 to false, apiTask to true), usage.holders.associate { it.worktreePath.orEmpty() to it.idle })
    }

    @Test
    fun `the emulator limit is capped at 16 even without a limit`() = runTest {
        harness.limit()

        val usage = harness.readPool.emulators(repository)

        assertNull(usage.limit)
        assertEquals(16, usage.effectiveLimit)
    }

    @Test
    fun `a removed worktree's reservation is still a holder and a device without last use is the oldest`() = runTest {
        store.writePorts(admin1, mapOf("emulator" to 5554))
        store.writePorts(admin2, mapOf("emulator" to 5556))
        lastUsed(admin2, "emulator", "2026-01-01T00:00:00Z")
        files.delete("$admin1/gitdir")

        val usage = harness.readPool.emulators(repository)

        assertEquals(setOf(null, task2), usage.holders.map { it.worktreePath }.toSet())
        assertNull(usage.holders.single { it.adminDirectory == admin1 }.lastUsed)
        assertEquals(Instant.parse("2026-01-01T00:00:00Z"), usage.holders.single { it.adminDirectory == admin2 }.lastUsed)
    }

    @Test
    fun `a booting simulator is held and the main checkout can hold a device`() = runTest {
        harness.simulators.addExisting("UDID-B", "ShogunAi · main-repo · main-repo", app.luxion.shogunai.domain.model.SimulatorState.BOOTING)
        store.writeResources(common, mapOf("ios-simulator" to "UDID-B"))

        val usage = harness.readPool.of(ResourceKind.IOS_SIMULATOR, repository)

        assertEquals(listOf(PoolHolderState.BOOTING), usage.holders.map { it.state })
        assertEquals(repository, usage.holders.single().worktreePath)
    }

    @Test
    fun `simulators are held while a UDID is recorded and the pool reads their state`() = runTest {
        harness.simulators.addExisting("UDID-A", "ShogunAi · main-repo · TASK-1", app.luxion.shogunai.domain.model.SimulatorState.BOOTED)
        store.writeResources(admin1, mapOf("ios-simulator" to "UDID-A"))

        val usage = harness.readPool.simulators(repository)

        assertEquals(listOf(PoolHolderState.BOOTED), usage.holders.map { it.state })
        assertFalse(usage.holders.single().idle)
    }

    @Test
    fun `an acquisition that finds room reserves the lowest free pair, skipping busy and taken ports`() = runTest {
        val busy = PoolHarness(git, source, store, sdk, files = files, portProbe = FakePortProbe(busy = setOf(5555)))
        store.writePorts(admin2, mapOf("emulator" to 5558))

        val first = busy.acquire.emulator(repository, task1).getOrThrow()

        assertTrue(first.acquiredNow)
        assertEquals(mapOf("emulator" to 5556), store.readPorts(admin1))
        assertEquals(busy.now.toEpochMilli(), store.readLastUsed(admin1)["emulator"])
    }

    @Test
    fun `acquiring what the worktree already holds only touches its last use`() = runTest {
        harness.acquire.emulator(repository, task1).getOrThrow()
        harness.now = harness.now.plusSeconds(60)

        val again = harness.acquire.emulator(repository, task1).getOrThrow()

        assertFalse(again.acquiredNow)
        assertTrue(again.releases.isEmpty())
        assertEquals(mapOf("emulator" to 5554), store.readPorts(admin1))
        assertEquals(harness.now.toEpochMilli(), store.readLastUsed(admin1)["emulator"])
    }

    @Test
    fun `a full pool releases the least recently used idle emulator`() = runTest {
        harness.limit(emulators = 2)
        store.writePorts(admin1, mapOf("emulator" to 5554))
        store.writePorts(admin2, mapOf("emulator" to 5556))
        lastUsed(admin1, "emulator", "2026-01-05T00:00:00Z")
        lastUsed(admin2, "emulator", "2026-01-04T00:00:00Z")

        val acquisition = harness.acquire.emulator(repository, task3).getOrThrow()

        assertEquals(listOf(task2), acquisition.releases.map { it.worktreePath })
        assertEquals(mapOf("emulator" to 5554), store.readPorts(admin1))
        assertTrue(store.readPorts(admin2).isEmpty())
        assertEquals(mapOf("emulator" to 5558), store.readPorts(admin3))
    }

    @Test
    fun `devices reserved before last use was recorded are released first`() = runTest {
        harness.limit(emulators = 2)
        store.writePorts(admin1, mapOf("emulator" to 5554))
        store.writePorts(admin2, mapOf("emulator" to 5556))
        lastUsed(admin1, "emulator", "2026-01-01T00:00:00Z")

        val acquisition = harness.acquire.emulator(repository, task3).getOrThrow()

        assertEquals(listOf(task2), acquisition.releases.map { it.worktreePath })
    }

    @Test
    fun `running and booting emulators are never released and the error names every holder`() = runTest {
        harness.limit(emulators = 2)
        store.writePorts(adminApi, mapOf("emulator" to 5554))
        store.writePorts(admin2, mapOf("emulator" to 5556))
        lease(adminApi, "emulator")
        sdk.states["emulator-5554"] = EmulatorState.RUNNING
        sdk.states["emulator-5556"] = EmulatorState.BOOTING

        val error = harness.acquire.emulator(repository, task1).exceptionOrNull()

        assertIs<WorktreeError.ResourcePoolFull>(error)
        assertEquals(2, error.limit)
        assertEquals(
            mapOf(apiTask to PoolHolderState.RUNNING, task2 to PoolHolderState.BOOTING),
            error.holders.associate { it.worktreePath.orEmpty() to it.state },
        )
        assertTrue(store.readPorts(admin1).isEmpty())
        assertTrue(sdk.stopped.isEmpty())
    }

    @Test
    fun `lowering the limit stops nothing and the next acquisition finds the pool full`() = runTest {
        store.writePorts(admin1, mapOf("emulator" to 5554))
        store.writePorts(admin2, mapOf("emulator" to 5556))
        lease(admin1, "emulator")
        lease(admin2, "emulator")
        sdk.states["emulator-5554"] = EmulatorState.RUNNING
        sdk.states["emulator-5556"] = EmulatorState.RUNNING
        harness.limit(emulators = 1)

        assertIs<WorktreeError.ResourcePoolFull>(harness.acquire.emulator(repository, task3).exceptionOrNull())
        assertTrue(sdk.stopped.isEmpty())
    }

    @Test
    fun `when every port is busy outside ShogunAi and none is idle acquiring reports no emulator port`() = runTest {
        val busy = PoolHarness(git, source, store, sdk, files = files, portProbe = FakePortProbe(busy = (5554..5585).toSet()))

        assertIs<WorktreeError.NoEmulatorPort>(busy.acquire.emulator(repository, task1).exceptionOrNull())
    }

    @Test
    fun `when the only free port pair belongs to a stopped emulator acquiring releases it and takes its port`() = runTest {
        val busy = PoolHarness(git, source, store, sdk, files = files, portProbe = FakePortProbe(busy = (5556..5585).toSet()))
        store.writePorts(admin1, mapOf("emulator" to 5554))

        val acquisition = busy.acquire.emulator(repository, task2).getOrThrow()

        assertEquals(listOf(task1), acquisition.releases.map { it.worktreePath })
        assertEquals(5554, store.readPorts(admin2)["emulator"])
        assertNull(store.readPorts(admin1)["emulator"])
    }

    @Test
    fun `an acquisition rewrites the declared environment file of the worktree and of the released one`() = runTest {
        val withEnvFile = FakeRepositoryProvisioningSource(
            byRepository = mapOf(
                repository to RepositoryProvisioning(
                    providers = RepositoryProviders(emulator = EmulatorProvider()),
                    environment = app.luxion.shogunai.domain.model.EnvironmentDeclaration(envFile = ".env.local"),
                ),
            ),
        )
        val untracked = object : ShellCommandExecutor {
            override suspend fun execute(command: List<String>, workingDirectory: String?) =
                if (command.getOrNull(1) == "ls-files") {
                    app.luxion.shogunai.domain.executor.CommandResult(command, 1, "", "")
                } else {
                    git.execute(command, workingDirectory)
                }
        }
        val pool = PoolHarness(untracked, withEnvFile, store, sdk, files = files)
        pool.limit(emulators = 1)
        store.writePorts(admin1, mapOf("emulator" to 5554))

        pool.acquire.emulator(repository, task2).getOrThrow()

        assertEquals("ANDROID_SERIAL=emulator-5556\n", files.writtenTexts["$task2/.env.local"])
        assertEquals("", files.writtenTexts["$task1/.env.local"])
    }

    @Test
    fun `preparing a new worktree's environment reserves no port and creates no simulator`() = runTest {
        val pool = PoolHarness(git, source, store, sdk, files = files)

        PrepareWorktreeEnvironmentUseCase(pool.resolve, WriteEnvironmentFileUseCase(git, files, source)).prepare(repository, task1)

        assertTrue(store.portsByAdminDirectory.isEmpty())
        assertTrue(store.resourcesByAdminDirectory.isEmpty())
        assertTrue(pool.simulators.created.isEmpty())
        assertTrue(pool.resolve(repository, task1, reserve = true).getOrThrow().variables.isEmpty())
    }

    private val idleSince10 = app.luxion.shogunai.domain.model.DaemonState.Idle(Instant.parse("2026-01-01T10:00:00Z"))
    private val idleSince11 = app.luxion.shogunai.domain.model.DaemonState.Idle(Instant.parse("2026-01-01T11:00:00Z"))
    private val busy = app.luxion.shogunai.domain.model.DaemonState.Busy

    @Test
    fun `the Gradle pool counts Gradle daemons and not Kotlin daemons`() = runTest {
        harness.daemons.daemons = listOf(
            FakeGradleDaemonSource.gradleDaemon(1, busy),
            FakeGradleDaemonSource.gradleDaemon(2, idleSince10),
            FakeGradleDaemonSource.gradleDaemon(3, idleSince11),
            FakeGradleDaemonSource.kotlinDaemon(4),
            FakeGradleDaemonSource.kotlinDaemon(5),
        )

        val usage = harness.readPool.gradleDaemons(null)

        assertEquals(3, usage.inUse)
        assertEquals(listOf(PoolHolderState.BUSY, PoolHolderState.IDLE, PoolHolderState.IDLE), usage.holders.map { it.state })
    }

    @Test
    fun `trimming stops idle daemons longest idle first until the pool is within its limit`() = runTest {
        harness.limit(gradleDaemons = 2)
        harness.daemons.daemons = listOf(
            FakeGradleDaemonSource.gradleDaemon(1, busy),
            FakeGradleDaemonSource.gradleDaemon(2, idleSince11),
            FakeGradleDaemonSource.gradleDaemon(3, idleSince10),
            FakeGradleDaemonSource.gradleDaemon(4, busy),
        )

        val trim = harness.trim(null).getOrThrow()

        assertEquals(listOf(3L, 2L), trim.stopped.map { it.pid })
        assertEquals(2, trim.remaining)
    }

    @Test
    fun `trimming over the limit stops only as many as needed`() = runTest {
        harness.limit(gradleDaemons = 2)
        harness.daemons.daemons = listOf(
            FakeGradleDaemonSource.gradleDaemon(1, busy),
            FakeGradleDaemonSource.gradleDaemon(2, idleSince11),
            FakeGradleDaemonSource.gradleDaemon(3, idleSince10),
        )

        val trim = harness.trim(null).getOrThrow()

        assertEquals(listOf(3L), trim.stopped.map { it.pid })
    }

    @Test
    fun `trimming leaves busy daemons alone even when over the limit`() = runTest {
        harness.limit(gradleDaemons = 2)
        harness.daemons.daemons = (1L..3L).map { FakeGradleDaemonSource.gradleDaemon(it, busy) }

        val trim = harness.trim(null).getOrThrow()

        assertTrue(trim.stopped.isEmpty())
        assertEquals(3, trim.remaining)
        assertTrue(harness.readPool.gradleDaemons(null).isOverLimit)
    }

    @Test
    fun `a daemon that became busy before the stop is kept`() = runTest {
        harness.limit(gradleDaemons = 1)
        val source = FakeGradleDaemonSource(
            listOf(FakeGradleDaemonSource.gradleDaemon(1, busy), FakeGradleDaemonSource.gradleDaemon(2, idleSince10)),
        )
        val flipping = object : app.luxion.shogunai.domain.io.GradleDaemonSource {
            var calls = 0
            override suspend fun list() = if (calls++ == 0) source.list() else listOf(
                FakeGradleDaemonSource.gradleDaemon(1, busy),
                FakeGradleDaemonSource.gradleDaemon(2, busy),
            )
        }
        val trim = TrimGradleDaemonsUseCase(ListGradleDaemonsUseCase(flipping, files), StopGradleDaemonsUseCase(flipping, git), harness.limits)

        val result = trim(null).getOrThrow()

        assertTrue(result.stopped.isEmpty())
        assertEquals(listOf(2L), result.kept.map { it.pid })
    }

    @Test
    fun `with no limit nothing is trimmed`() = runTest {
        harness.limit()
        harness.daemons.daemons = (1L..5L).map { FakeGradleDaemonSource.gradleDaemon(it, idleSince10) }

        assertTrue(harness.trim(null).getOrThrow().stopped.isEmpty())
    }

    @Test
    fun `a Gradle daemon in an unknown state is held as unknown`() = runTest {
        harness.daemons.daemons = listOf(FakeGradleDaemonSource.gradleDaemon(1, app.luxion.shogunai.domain.model.DaemonState.Unknown))

        val usage = harness.readPool.of(ResourceKind.GRADLE_DAEMON, repository)

        assertEquals(listOf(PoolHolderState.UNKNOWN), usage.holders.map { it.state })
        assertFalse(usage.holders.single().idle)
    }

    private fun pool(
        sdk: AndroidSdk = this.sdk,
        simulators: AppleSimulators = harness.simulators,
        daemons: ListGradleDaemonsUseCase? = null,
    ) = ReadResourcePoolUseCase(harness.resolve, store, files, sdk, simulators, harness.limits, daemons, clock = { harness.now })

    @Test
    fun `an sdk that can't be read leaves every emulator stopped`() = runTest {
        store.writePorts(admin1, mapOf("emulator" to 5554))
        sdk.states["emulator-5554"] = EmulatorState.RUNNING

        val usage = pool(sdk = FakeAndroidSdk(missing = true)).emulators(repository)

        assertEquals(listOf(PoolHolderState.STOPPED), usage.holders.map { it.state })
    }

    @Test
    fun `simulators unsupported or unreadable read as shut down and reservations without one are skipped`() = runTest {
        store.writeResources(admin1, mapOf("ios-simulator" to "UDID-1"))
        store.writePorts(admin2, mapOf("emulator" to 5556))
        harness.simulators.addExisting("UDID-1", "ShogunAi · main-repo · TASK-1", SimulatorState.BOOTED)
        val unreadable = object : AppleSimulators by FakeAppleSimulators() {
            override suspend fun devices(): List<SimulatorDevice> = throw IllegalStateException("simctl failed")
        }

        assertEquals(listOf(PoolHolderState.SHUTDOWN), pool(simulators = FakeAppleSimulators(supported = false)).simulators(repository).holders.map { it.state })
        assertEquals(listOf(PoolHolderState.SHUTDOWN), pool(simulators = unreadable).simulators(repository).holders.map { it.state })
    }

    @Test
    fun `gradle daemons are none without a listing or when it fails, and untracked ones are unknown and unowned`() = runTest {
        val failing = ListGradleDaemonsUseCase(object : GradleDaemonSource by FakeGradleDaemonSource() {
            override suspend fun list(): List<DaemonProcess> = throw IllegalStateException("jps failed")
        }, files)
        harness.daemons.daemons = listOf(FakeGradleDaemonSource.gradleDaemon(1, DaemonState.Untracked, lastBuildDirectory = "/elsewhere"))

        assertTrue(pool().gradleDaemons(null).holders.isEmpty())
        assertTrue(pool(daemons = failing).gradleDaemons(null).holders.isEmpty())
        val untracked = pool(daemons = ListGradleDaemonsUseCase(harness.daemons, files)).gradleDaemons(emptyList()).holders.single()
        assertEquals(PoolHolderState.UNKNOWN, untracked.state)
        assertNull(untracked.worktreePath)
    }

    @Test
    fun `trimming within the limit stops nothing and a failing listing fails`() = runTest {
        harness.limit(gradleDaemons = 5)
        harness.daemons.daemons = listOf(FakeGradleDaemonSource.gradleDaemon(1, idleSince10))
        val failing = object : GradleDaemonSource by FakeGradleDaemonSource() {
            override suspend fun list(): List<DaemonProcess> = throw IllegalStateException("jps failed")
        }

        assertEquals(1, harness.trim(null).getOrThrow().remaining)
        assertTrue(TrimGradleDaemonsUseCase(ListGradleDaemonsUseCase(failing, files), StopGradleDaemonsUseCase(failing, git), harness.limits)(null).isFailure)
    }

    @Test
    fun `a gradle daemon owned by a worktree names it and kotlin daemons don't count for trimming`() = runTest {
        harness.limit(gradleDaemons = 5)
        harness.daemons.daemons = listOf(
            FakeGradleDaemonSource.gradleDaemon(1, idleSince10, lastBuildDirectory = task1),
            FakeGradleDaemonSource.kotlinDaemon(2),
        )
        files.writeText(task1, "")

        val holder = pool(daemons = ListGradleDaemonsUseCase(harness.daemons, files)).gradleDaemons(listOf(Worktree(task1, "feature/TASK-1"))).holders.single()

        assertEquals(task1, holder.worktreePath)
        assertEquals(1, harness.trim(null).getOrThrow().remaining)
    }

    @Test
    fun `the main checkout of a repository at the filesystem root has no worktree path`() = runTest {
        val rootGit = object : ShellCommandExecutor {
            override suspend fun execute(command: List<String>, workingDirectory: String?) = FakeShellCommandExecutor.success(command, "/.git\n/.git\n")
        }
        store.writePorts("/.git", mapOf("emulator" to 5554))

        val usage = PoolHarness(rootGit, source, store, sdk, files = files).readPool.emulators("/")

        assertEquals(listOf(null), usage.holders.map { it.worktreePath })
    }

    @Test
    fun `a device never used is released before a used one whatever their order`() = runTest {
        harness.limit(emulators = 2)
        store.writePorts(admin1, mapOf("emulator" to 5554))
        store.writePorts(admin2, mapOf("emulator" to 5556))
        lastUsed(admin2, "emulator", "2026-01-01T00:00:00Z")

        val acquisition = harness.acquire.emulator(repository, task3).getOrThrow()

        assertEquals(listOf(task1), acquisition.releases.map { it.worktreePath })
    }
}
