package app.luxion.shogunai.cli

import java.time.Instant
import app.luxion.shogunai.domain.model.ResourceLimits
import app.luxion.shogunai.domain.model.EmulatorState
import app.luxion.shogunai.domain.model.ReleaseRecord
import app.luxion.shogunai.domain.model.DaemonState
import app.luxion.shogunai.domain.FakeMachineCapacity
import app.luxion.shogunai.domain.FakeResourceLimitsRepository
import app.luxion.shogunai.domain.model.CapacityProfile
import app.luxion.shogunai.domain.model.ResourceSettings
import app.luxion.shogunai.domain.FakeAndroidSdk
import app.luxion.shogunai.domain.FakeAppleSimulators
import app.luxion.shogunai.domain.FakeDeviceWaitQueue
import app.luxion.shogunai.domain.FakeProcessLiveness
import app.luxion.shogunai.domain.FakeDerivedDataSource
import app.luxion.shogunai.domain.FakeEnvironmentStateStore
import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.FakeHerdrConfigSource
import app.luxion.shogunai.domain.FakeOrcaClient
import app.luxion.shogunai.domain.FakeGradleDaemonSource
import app.luxion.shogunai.domain.FakeKmpTestWiring
import app.luxion.shogunai.domain.FakePortProbe
import app.luxion.shogunai.domain.FakeProjectRepository
import app.luxion.shogunai.domain.FakeRepositoryProvisioningSource
import app.luxion.shogunai.domain.executor.CommandResult
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.model.EmulatorProvider
import app.luxion.shogunai.domain.model.EnvironmentDeclaration
import app.luxion.shogunai.domain.model.IosSimulatorProvider
import app.luxion.shogunai.domain.model.RepositoryProviders
import app.luxion.shogunai.domain.model.RepositoryProvisioning
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.FakeRepositoryTrust
import app.luxion.shogunai.domain.io.RepositoryTrust
import app.luxion.shogunai.domain.model.RepositoryApprovalSubject
import app.luxion.shogunai.domain.model.ProcessIdentity
import app.luxion.shogunai.domain.model.ResourceKind
import app.luxion.shogunai.domain.model.WaitTicket
import app.luxion.shogunai.domain.model.parseEnvironmentVariable
import app.luxion.shogunai.domain.model.toLocalClockTime
import java.nio.file.Files
import kotlin.io.path.readText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EnvironmentCommandsTest {

    private val resourceLimits = FakeResourceLimitsRepository(ResourceSettings(CapacityProfile.PER_WORKTREE))

    private val base = "/home/dev/projects/main-repo"
    private val linked = "/home/dev/projects/worktrees/TASK-123"
    private val common = "$base/.git"
    private val envFile = Files.createTempFile("claude-env", ".sh")

    private val porcelain = """
        worktree $base
        HEAD 1111111111111111111111111111111111111111
        branch refs/heads/main

        worktree $linked
        HEAD 2222222222222222222222222222222222222222
        branch refs/heads/feature/TASK-123
    """.trimIndent()

    private val androidSdk = FakeAndroidSdk()
    private val appleSimulators = FakeAppleSimulators()
    private val kmpTestWiring = FakeKmpTestWiring()
    private val out = StringBuilder()
    private val err = StringBuilder()
    private val store = FakeEnvironmentStateStore()
    private val waitQueue = FakeDeviceWaitQueue()
    private var clock: Instant = Instant.now()
    private var onPause: () -> Unit = {}

    @AfterTest
    fun cleanUp() {
        Files.deleteIfExists(envFile)
    }

    private val daemons = FakeGradleDaemonSource()
    private val executedCommands = mutableListOf<List<String>>()

    private val executor = object : ShellCommandExecutor {
        override suspend fun execute(command: List<String>, workingDirectory: String?): CommandResult {
            executedCommands += command
            if (command.first() == "kill") return CommandResult(command, 0, "", "")
            val inLinked = workingDirectory?.startsWith(linked) == true
            val stdout = when {
                "--git-dir" in command -> if (inLinked) "$common/worktrees/TASK-123\n$common\n" else "$common\n$common\n"
                "--git-common-dir" in command -> "$common\n"
                command.take(3) == listOf("git", "worktree", "list") -> porcelain
                else -> ""
            }
            val outside = workingDirectory?.startsWith("/home/dev/projects") != true
            return CommandResult(command, if (outside) 128 else 0, stdout, if (outside) "not a git repository" else "")
        }
    }

    private fun declaring(vararg variables: Pair<String, String>) = FakeRepositoryProvisioningSource(
        byRepository = mapOf(
            base to RepositoryProvisioning(
                environment = EnvironmentDeclaration(variables.map { (name, value) -> parseEnvironmentVariable(name, value) }),
            ),
        ),
    )

    private val project = Project("main-repo", "main-repo", ProjectConfig(base, "/home/dev/projects/worktrees", emptyList()))

    private fun cli(
        provisioning: FakeRepositoryProvisioningSource,
        workingDirectory: String = linked,
        input: String = "",
        environment: Map<String, String> = mapOf("CLAUDE_ENV_FILE" to envFile.toString()),
        registered: Boolean = true,
        trust: RepositoryTrust = FakeRepositoryTrust(approvesEverything = true),
    ) = ShogunCli(
        executor = executor,
        fileManager = FakeFileManager(existing = listOf(base, linked)),
        projectRepository = FakeProjectRepository(if (registered) listOf(project) else emptyList()),
        provisioningSource = provisioning,
        daemonSource = daemons,
        gradleUserHome = "/home/dev/.gradle",
        environmentStateStore = store,
        portProbe = FakePortProbe(),
        androidSdk = androidSdk,
        appleSimulators = appleSimulators,
        kmpTestWiring = kmpTestWiring,
        resourceLimits = resourceLimits,
        machineCapacity = FakeMachineCapacity(),
        derivedDataSource = FakeDerivedDataSource(),
        herdrConfig = FakeHerdrConfigSource(),
        orca = FakeOrcaClient(installed = false),
        workingDirectory = workingDirectory,
        out = out,
        err = err,
        readInput = { input },
        canonicalPath = { it.trimEnd('/') },
        environmentVariable = { environment[it] },
        trust = trust,
        deviceWaitQueue = waitQueue,
        processLiveness = FakeProcessLiveness(),
        deviceClock = { clock },
        pauseWhileWaiting = { pause ->
            clock = clock.plus(pause)
            onPause()
        },
    )

    private fun withEmulator() = FakeRepositoryProvisioningSource(
        byRepository = mapOf(base to RepositoryProvisioning(providers = RepositoryProviders(emulator = EmulatorProvider()))),
    )

    private fun withSimulator() = FakeRepositoryProvisioningSource(
        byRepository = mapOf(base to RepositoryProvisioning(providers = RepositoryProviders(iosSimulator = IosSimulatorProvider()))),
    )

    @Test
    fun `simulator start creates and boots the worktree's simulator`() {
        val exitCode = cli(withSimulator()).run(listOf("simulator", "start", "--device", "iPhone 16"))

        assertEquals(0, exitCode, err.toString())
        assertEquals("UDID-1 booted  ShogunAi · main-repo · TASK-123\nexport IOS_SIMULATOR_UDID=UDID-1\n", out.toString())
        assertEquals("ShogunAi · main-repo · TASK-123", appleSimulators.created.single().name)
        assertEquals(listOf("UDID-1"), appleSimulators.shown)
    }

    @Test
    fun `simulator status says how to wire KMP tests and stop shuts it down`() {
        cli(withSimulator()).run(listOf("simulator", "start"))
        out.clear()

        assertEquals(0, cli(withSimulator()).run(listOf("simulator", "status")))
        assertTrue(out.startsWith("UDID-1 booted"), out.toString())
        assertTrue(out.contains("KMP simulator tests don't use this worktree's simulator yet"), out.toString())
        assertTrue(out.contains("shogun simulator gradle-init install"), out.toString())
        out.clear()

        kmpTestWiring.wiredRepositories = setOf(base)
        cli(withSimulator()).run(listOf("simulator", "status"))
        assertTrue(out.contains("KMP simulator tests use this worktree's simulator."), out.toString())
        out.clear()

        assertEquals(0, cli(withSimulator()).run(listOf("simulator", "stop")))
        assertEquals("UDID-1 shutdown  ShogunAi · main-repo · TASK-123\n", out.toString())
    }

    @Test
    fun `simulator status says nothing about KMP outside Kotlin Multiplatform`() {
        kmpTestWiring.nonMultiplatformRepositories = setOf(base)
        cli(withSimulator()).run(listOf("simulator", "start"))
        out.clear()

        assertEquals(0, cli(withSimulator()).run(listOf("simulator", "status")))
        assertEquals("UDID-1 booted  ShogunAi · main-repo · TASK-123\n", out.toString())
    }

    @Test
    fun `simulator commands fail without the provider and never run Apple tools`() {
        val exitCode = cli(declaring("PORT" to "\${port}")).run(listOf("simulator", "start"))

        assertEquals(1, exitCode)
        assertTrue(err.contains("\"providers\": { \"ios-simulator\": {} }"), err.toString())
        assertEquals(0, appleSimulators.calls)
    }

    @Test
    fun `gradle-init installs only after confirmation and uninstalls`() {
        assertEquals(0, cli(declaring(), input = "n\n").run(listOf("simulator", "gradle-init", "install")))
        assertTrue(out.contains("Nothing installed."), out.toString())
        assertEquals(0, kmpTestWiring.installs)

        assertEquals(0, cli(declaring(), input = "y\n").run(listOf("simulator", "gradle-init", "install")))
        assertEquals(1, kmpTestWiring.installs)

        assertEquals(0, cli(declaring()).run(listOf("simulator", "gradle-init", "install", "--yes")))
        assertEquals(2, kmpTestWiring.installs)

        cli(declaring()).run(listOf("simulator", "gradle-init", "uninstall"))
        assertFalse(kmpTestWiring.installed)
    }

    @Test
    fun `session-start tells Claude about its iOS simulator`() {
        store.writeResources("$common/worktrees/TASK-123", mapOf("ios-simulator" to "UDID-1"))
        appleSimulators.addExisting("UDID-1", "ShogunAi · main-repo · TASK-123")
        val exitCode = cli(withSimulator(), input = """{"cwd": "$linked"}""").run(listOf("hook", "session-start"))

        assertEquals(0, exitCode, err.toString())
        assertEquals("export IOS_SIMULATOR_UDID='UDID-1'\n", envFile.readText())
        assertEquals("ShogunAi environment of worktree TASK-123: IOS_SIMULATOR_UDID", out.lines().first())
        assertTrue(out.contains("`shogun simulator start`"), out.toString())
        assertTrue(out.contains("`shogun simulator start --wait` run in the background"), out.toString())
        assertTrue(out.contains("xcodebuild -destination \"id=\$IOS_SIMULATOR_UDID\""), out.toString())
    }

    @Test
    fun `emulator start boots the worktree's emulator and status reports it`() {
        val start = cli(withEmulator()).run(listOf("emulator", "start", "--no-window"))

        assertEquals(0, start, err.toString())
        assertEquals(listOf(FakeAndroidSdk.Started("Medium_Phone_API_35", 5554, noWindow = true)), androidSdk.started)

        assertEquals(0, cli(withEmulator()).run(listOf("emulator", "stop")))
        assertEquals(0, cli(withEmulator()).run(listOf("emulator", "status", linked)))
        assertEquals("emulator-5554 running\nexport ANDROID_SERIAL=emulator-5554\nemulator-5554 stopped\nemulator-5554 stopped\n", out.toString())
    }

    @Test
    fun `emulator status before the first start says not acquired`() {
        assertEquals(0, cli(withEmulator()).run(listOf("emulator", "status")))
        assertEquals("not acquired\n", out.toString())
        assertTrue(androidSdk.started.isEmpty())
    }

    @Test
    fun `emulator status after a release says why and for whom`() {
        store.writeReleases("$common/worktrees/TASK-123", mapOf("emulator" to ReleaseRecord(1_000L, "main-repo · TASK-9", "emulator-5554")))

        assertEquals(0, cli(withEmulator()).run(listOf("emulator", "status")))

        assertEquals("not acquired (released for main-repo · TASK-9 at 1970-01-01T00:00:01Z)\n", out.toString())
    }

    @Test
    fun `emulator status json after a release names the worktree it was released for`() {
        store.writeReleases("$common/worktrees/TASK-123", mapOf("emulator" to ReleaseRecord(1_000L, "main-repo · TASK-9", "emulator-5554")))

        assertEquals(0, cli(withEmulator()).run(listOf("emulator", "status", "--json")))

        val released = jsonOut().getValue("releasedFor") as kotlinx.serialization.json.JsonObject
        assertEquals("main-repo · TASK-9", released.text("worktree"))
        assertEquals("1970-01-01T00:00:01Z", released.text("at"))
    }

    @Test
    fun `simulator status after a release says why and for whom`() {
        store.writeReleases("$common/worktrees/TASK-123", mapOf("ios-simulator" to ReleaseRecord(1_000L, "main-repo · TASK-9", "UDID-1")))

        assertEquals(0, cli(withSimulator()).run(listOf("simulator", "status")))

        assertTrue(out.contains("not acquired (released for main-repo · TASK-9 at 1970-01-01T00:00:01Z)"), out.toString())
    }

    @Test
    fun `emulator start of a running emulator renews its lease and prints the serial to export`() {
        store.writePorts("$common/worktrees/TASK-123", mapOf("emulator" to 5554))
        androidSdk.states["emulator-5554"] = EmulatorState.RUNNING

        assertEquals(0, cli(withEmulator()).run(listOf("emulator", "start")))

        assertEquals("emulator-5554 running\nexport ANDROID_SERIAL=emulator-5554\n", out.toString())
        assertTrue(store.readLeases("$common/worktrees/TASK-123").getValue("emulator") > 0)
        assertTrue(androidSdk.started.isEmpty())
    }

    @Test
    fun `emulator commands fail without the provider and never touch the SDK`() {
        val exitCode = cli(declaring("PORT" to "\${port}")).run(listOf("emulator", "start"))

        assertEquals(1, exitCode)
        assertTrue(err.contains("\"providers\": { \"emulator\": {} }"), err.toString())
        assertEquals(0, androidSdk.locates)
    }

    @Test
    fun `session-start tells Claude how to start the emulator`() {
        store.writePorts("$common/worktrees/TASK-123", mapOf("emulator" to 5554))
        val exitCode = cli(withEmulator(), input = """{"cwd": "$linked"}""").run(listOf("hook", "session-start"))

        assertEquals(0, exitCode, err.toString())
        assertEquals("export ANDROID_SERIAL='emulator-5554'\n", envFile.readText())
        assertEquals("ShogunAi environment of worktree TASK-123: ANDROID_SERIAL", out.lines().first())
        assertFalse(out.contains("emulator-5554"), out.toString())
        assertTrue(out.contains("`shogun emulator start`"), out.toString())
        assertTrue(out.contains("`shogun emulator start --wait` run in the background"), out.toString())
        assertTrue(out.contains("may have been released to another worktree"), out.toString())
        assertTrue(out.contains("never run 'adb kill-server' or 'adb start-server'"), out.toString())
        assertEquals(0, androidSdk.locates)
    }

    @Test
    fun `env prints export lines for the current worktree`() {
        val exitCode = cli(declaring("PORT" to "\${port}", "APP" to "app-\${worktree}"), workingDirectory = "$linked/src").run(listOf("env"))

        assertEquals(0, exitCode, err.toString())
        assertEquals("export PORT='20000'\nexport APP='app-TASK-123'\n", out.toString())
        assertEquals(mapOf("PORT" to 20000), store.readPorts("$common/worktrees/TASK-123"))
    }

    @Test
    fun `env resolves the worktree of a given path in json`() {
        val exitCode = cli(declaring("PORT" to "\${port}"), workingDirectory = base).run(listOf("env", linked, "--format", "json"))

        assertEquals(0, exitCode, err.toString())
        assertEquals("{\"PORT\":\"20000\"}\n", out.toString())
        assertEquals(emptyMap(), store.readPorts(common))
    }

    @Test
    fun `env prints nothing for an empty environment`() {
        assertEquals(0, cli(declaring()).run(listOf("env")))
        assertEquals("", out.toString())
    }

    @Test
    fun `env fails outside a repository`() {
        val exitCode = cli(declaring("PORT" to "\${port}"), workingDirectory = "/tmp/elsewhere").run(listOf("env"))

        assertEquals(1, exitCode)
        assertTrue(err.contains("No Git repository found"), err.toString())
    }

    @Test
    fun `session-start appends exports to CLAUDE_ENV_FILE and prints context`() {
        Files.writeString(envFile, "export OTHER='1'\n")

        val exitCode = cli(declaring("PORT" to "\${port}"), input = """{"cwd": "$linked", "source": "startup"}""").run(listOf("hook", "session-start"))

        assertEquals(0, exitCode, err.toString())
        assertEquals("export OTHER='1'\nexport PORT='20000'\n", envFile.readText())
        assertEquals("ShogunAi environment of worktree TASK-123: PORT", out.lines().first())
        assertFalse(out.contains("20000"), out.toString())
        assertTrue(out.contains("change the branch of the worktree a session starts in"), out.toString())
    }

    @Test
    fun `session-start reports an unwritable CLAUDE_ENV_FILE in one line`() {
        val unwritable = "/home/dev/does-not-exist/claude-env"

        val exitCode = cli(declaring("PORT" to "\${port}"), input = """{"cwd": "$linked"}""", environment = mapOf("CLAUDE_ENV_FILE" to unwritable))
            .run(listOf("hook", "session-start"))

        assertEquals(1, exitCode)
        assertTrue(err.startsWith("shogun: $unwritable"), err.toString())
        assertEquals(1, err.lines().count { it.isNotBlank() })
    }

    @Test
    fun `session-start without CLAUDE_ENV_FILE only prints context`() {
        val exitCode = cli(declaring("PORT" to "\${port}"), input = """{"cwd": "$linked"}""", environment = emptyMap())
            .run(listOf("hook", "session-start"))

        assertEquals(0, exitCode)
        assertEquals("", envFile.readText())
        assertTrue(out.startsWith("ShogunAi environment of worktree TASK-123"))
    }

    @Test
    fun `session-start prints nothing outside a repository`() {
        assertEquals(0, cli(declaring("PORT" to "\${port}"), input = """{"cwd": "/tmp/elsewhere"}""").run(listOf("hook", "session-start")))

        assertEquals("", out.toString())
        assertEquals("", envFile.readText())
    }

    @Test
    fun `session-start without an environment prints the rules and writes no variables`() {
        assertEquals(0, cli(declaring(), input = """{"cwd": "$linked"}""").run(listOf("hook", "session-start")))

        assertFalse(out.contains("ShogunAi environment"), out.toString())
        assertTrue(out.contains("change the branch of the worktree a session starts in"), out.toString())
        assertEquals("", envFile.readText())
    }

    @Test
    fun `session-start states precedence first and points to the guide last`() {
        assertEquals(0, cli(declaring(), input = """{"cwd": "$linked"}""").run(listOf("hook", "session-start")))

        val lines = out.lines().filter { it.isNotBlank() }
        assertTrue(lines.first().contains("take precedence"), out.toString())
        assertTrue(lines.last().contains("shogun guide"), out.toString())
    }

    @Test
    fun `session-start in the main checkout says to create a worktree`() {
        assertEquals(0, cli(declaring(), workingDirectory = base, input = """{"cwd": "$base"}""").run(listOf("hook", "session-start")))

        assertTrue(out.contains("create a worktree with `shogun new <id>`"), out.toString())
        assertFalse(out.contains("change the branch of the worktree a session starts in"), out.toString())
    }

    @Test
    fun `session-start without Herdr has no rule about opening or messaging sessions`() {
        assertEquals(0, cli(declaring(), input = """{"cwd": "$linked"}""").run(listOf("hook", "session-start")))

        assertFalse(out.contains("--herdr"), out.toString())
        assertFalse(out.contains("messages"), out.toString())
    }

    @Test
    fun `session-start in a repository that isn't registered delivers no environment`() {
        val exitCode = cli(declaring("PORT" to "\${port}"), input = """{"cwd": "$linked"}""", registered = false)
            .run(listOf("hook", "session-start"))

        assertEquals(0, exitCode, err.toString())
        assertEquals("", envFile.readText())
        assertFalse(out.contains("PORT"), out.toString())
        assertTrue(out.contains("change the branch of the worktree a session starts in"), out.toString())
        assertTrue(store.portsByAdminDirectory.isEmpty())
        assertEquals(0, store.writes)
    }

    @Test
    fun `session-start names variables awaiting approval without delivering them`() {
        val exitCode = cli(declaring("PORT" to "\${port}"), input = """{"cwd": "$linked"}""", trust = FakeRepositoryTrust())
            .run(listOf("hook", "session-start"))

        assertEquals(0, exitCode, err.toString())
        assertEquals("", envFile.readText())
        assertFalse(out.contains("ShogunAi environment of worktree"), out.toString())
        val awaiting = out.lines().single { it.contains("awaiting the user's approval") }
        assertTrue(awaiting.contains("PORT") && awaiting.contains("shogun trust"), out.toString())
    }

    @Test
    fun `session-start delivers declared variables once approved`() {
        val trust = FakeRepositoryTrust(mapOf(base to RepositoryApprovalSubject(variables = listOf("PORT" to "\${port}"))))

        cli(declaring("PORT" to "\${port}"), input = """{"cwd": "$linked"}""", trust = trust).run(listOf("hook", "session-start"))

        assertEquals("export PORT='20000'\n", envFile.readText())
        assertFalse(out.contains("awaiting"), out.toString())
    }

    @Test
    fun `env leaves out variables awaiting approval and names them on standard error`() {
        val exitCode = cli(declaring("PORT" to "\${port}"), trust = FakeRepositoryTrust()).run(listOf("env"))

        assertEquals(0, exitCode)
        assertEquals("", out.toString())
        assertTrue(err.contains("PORT") && err.contains("shogun trust"), err.toString())
    }

    @Test
    fun `session-start with an unreadable declaration exits 1 without writing`() {
        val provisioning = FakeRepositoryProvisioningSource(unreadable = setOf(base))

        val exitCode = cli(provisioning, input = """{"cwd": "$linked"}""").run(listOf("hook", "session-start"))

        assertEquals(1, exitCode)
        assertTrue(err.contains(".shogunai.json"), err.toString())
        assertFalse(envFile.readText().isNotEmpty())
    }

    private fun jsonOut(): kotlinx.serialization.json.JsonObject {
        val text = out.toString()
        assertTrue(text.endsWith("\n") && text.trimEnd('\n').lines().size == 1, text)
        return kotlinx.serialization.json.Json.parseToJsonElement(text) as kotlinx.serialization.json.JsonObject
    }

    private fun kotlinx.serialization.json.JsonObject.text(key: String) =
        (getValue(key) as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it.isString }?.content

    @Test
    fun `emulator json reports the serial and state`() {
        assertEquals(0, cli(withEmulator()).run(listOf("emulator", "start", "--no-window", "--json")))
        val started = jsonOut()
        assertEquals(setOf("worktree", "serial", "state", "acquired", "acquiredNow", "released", "waited"), started.keys)
        assertEquals(linked, started.text("worktree"))
        assertEquals("emulator-5554", started.text("serial"))
        assertEquals("running", started.text("state"))
        out.clear()

        assertEquals(0, cli(withEmulator()).run(listOf("emulator", "status", "--json")))
        assertEquals("running", jsonOut().text("state"))
        out.clear()

        assertEquals(0, cli(withEmulator()).run(listOf("emulator", "stop", "--json")))
        assertEquals("stopped", jsonOut().text("state"))
    }

    @Test
    fun `emulator status json before any reservation has a null serial and state`() {
        assertEquals(0, cli(withEmulator()).run(listOf("emulator", "status", "--json")))

        val json = jsonOut()
        assertEquals(linked, json.text("worktree"))
        assertEquals(kotlinx.serialization.json.JsonNull, json.getValue("serial"))
        assertEquals(kotlinx.serialization.json.JsonNull, json.getValue("state"))
    }

    @Test
    fun `emulator json without the provider prints nothing on standard output`() {
        val exitCode = cli(declaring("PORT" to "\${port}")).run(listOf("emulator", "status", "--json"))

        assertEquals(1, exitCode)
        assertEquals("", out.toString())
    }

    @Test
    fun `simulator json reports the udid, name and state`() {
        assertEquals(0, cli(withSimulator()).run(listOf("simulator", "start", "--json")))
        val started = jsonOut()
        assertEquals(setOf("worktree", "udid", "name", "state", "acquired", "acquiredNow", "released", "waited"), started.keys)
        assertEquals("UDID-1", started.text("udid"))
        assertEquals("ShogunAi · main-repo · TASK-123", started.text("name"))
        assertEquals("booted", started.text("state"))
        assertFalse(out.contains("KMP"), out.toString())
        out.clear()

        assertEquals(0, cli(withSimulator()).run(listOf("simulator", "stop", "--json")))
        assertEquals("shutdown", jsonOut().text("state"))
    }

    @Test
    fun `simulator status json before any simulator has null fields`() {
        assertEquals(0, cli(withSimulator()).run(listOf("simulator", "status", "--json")))

        assertEquals(kotlinx.serialization.json.JsonNull, jsonOut().getValue("udid"))
    }

    @Test
    fun `simulator gradle-init doesn't accept json`() {
        val exitCode = cli(withSimulator()).run(listOf("simulator", "gradle-init", "install", "--json"))

        assertEquals(2, exitCode)
        assertTrue(err.contains("--json"), err.toString())
    }

    @Test
    fun `session-start before the first emulator start reserves nothing and explains how to get it`() {
        val exitCode = cli(withEmulator(), input = """{"cwd": "$linked"}""").run(listOf("hook", "session-start"))

        assertEquals(0, exitCode, err.toString())
        assertFalse(out.contains("ANDROID_SERIAL="), out.toString())
        assertTrue(out.contains("`shogun emulator start`"), out.toString())
        assertTrue(out.contains("never run 'adb kill-server' or 'adb start-server'"), out.toString())
        assertEquals("", envFile.readText())
        assertTrue(store.portsByAdminDirectory.isEmpty())
    }

    @Test
    fun `session-start before the first simulator start creates nothing`() {
        val exitCode = cli(withSimulator(), input = """{"cwd": "$linked"}""").run(listOf("hook", "session-start"))

        assertEquals(0, exitCode, err.toString())
        assertTrue(out.contains("`shogun simulator start`"), out.toString())
        assertTrue(appleSimulators.created.isEmpty())
        assertTrue(store.resourcesByAdminDirectory.isEmpty())
    }

    @Test
    fun `emulator start applies the limits from the machine settings and reports what it released`() {
        resourceLimits.settings = ResourceSettings(CapacityProfile.CUSTOM, ResourceLimits(emulators = 1, simulators = null, gradleDaemons = null))
        store.writePorts("$common/worktrees/TASK-9", mapOf("emulator" to 5556))

        val exitCode = cli(withEmulator()).run(listOf("emulator", "start"))

        assertEquals(0, exitCode, err.toString())
        assertTrue(err.contains("released the emulator of main-repo · TASK-9 to make room"), err.toString())
        assertEquals(emptyMap(), store.readPorts("$common/worktrees/TASK-9"))
        assertEquals(mapOf("emulator" to 5554), store.readPorts("$common/worktrees/TASK-123"))
    }

    @Test
    fun `emulator start fails with the pool error when every emulator is running`() {
        resourceLimits.settings = ResourceSettings(CapacityProfile.CUSTOM, ResourceLimits(emulators = 1, simulators = null, gradleDaemons = null))
        store.writePorts("$common/worktrees/TASK-9", mapOf("emulator" to 5556))
        store.writeLeases("$common/worktrees/TASK-9", mapOf("emulator" to System.currentTimeMillis()))
        androidSdk.states["emulator-5556"] = EmulatorState.RUNNING

        val exitCode = cli(withEmulator()).run(listOf("emulator", "start"))

        assertEquals(1, exitCode)
        assertEquals("", out.toString())
        assertTrue(err.contains("limit of 1 emulator is reached"), err.toString())
        assertTrue(err.contains("main-repo · TASK-9 (running, lease until"), err.toString())
        assertTrue(androidSdk.started.isEmpty())
    }

    @Test
    fun `pool status shows each pool with its limit and holders`() {
        resourceLimits.settings = ResourceSettings(CapacityProfile.CUSTOM, ResourceLimits(emulators = 2, simulators = 1, gradleDaemons = 4))
        store.writePorts("$common/worktrees/TASK-123", mapOf("emulator" to 5554))
        androidSdk.states["emulator-5554"] = EmulatorState.RUNNING

        val exitCode = cli(withEmulator()).run(listOf("pool", "status"))

        assertEquals(0, exitCode, err.toString())
        assertEquals(
            "emulators 1/2\n  main-repo · TASK-123 running\nsimulators 0/1\ngradle daemons 0/4\n",
            out.toString(),
        )
    }

    @Test
    fun `pool status json lists the pools`() {
        resourceLimits.settings = ResourceSettings(CapacityProfile.CUSTOM, ResourceLimits(emulators = 2, simulators = null, gradleDaemons = 4))
        store.writePorts("$common/worktrees/TASK-123", mapOf("emulator" to 5554))

        assertEquals(0, cli(withEmulator()).run(listOf("pool", "status", "--json")))

        val pools = jsonOut().getValue("pools") as kotlinx.serialization.json.JsonArray
        val emulators = pools.first() as kotlinx.serialization.json.JsonObject
        assertEquals("emulators", emulators.text("kind"))
        assertEquals("2", emulators.getValue("effectiveLimit").toString())
        assertEquals("1", emulators.getValue("inUse").toString())
        assertEquals(1, (emulators.getValue("holders") as kotlinx.serialization.json.JsonArray).size)
    }

    @Test
    fun `pool trim stops the longest idle Gradle daemon over the limit`() {
        resourceLimits.settings = ResourceSettings(CapacityProfile.CUSTOM, ResourceLimits(emulators = null, simulators = null, gradleDaemons = 2))
        daemons.daemons = listOf(
            FakeGradleDaemonSource.gradleDaemon(11, DaemonState.Idle(Instant.parse("2026-01-01T11:00:00Z"))),
            FakeGradleDaemonSource.gradleDaemon(12, DaemonState.Idle(Instant.parse("2026-01-01T10:00:00Z"))),
            FakeGradleDaemonSource.gradleDaemon(13, DaemonState.Idle(Instant.parse("2026-01-01T12:00:00Z"))),
        )

        val exitCode = cli(withEmulator()).run(listOf("pool", "trim"))

        assertEquals(0, exitCode, err.toString())
        assertTrue(out.contains("stopped 12"), out.toString())
        assertTrue(out.contains("gradle daemons 2/2"), out.toString())
        assertTrue(executedCommands.any { it == listOf("kill", "12") })
    }

    @Test
    fun `pool needs an action and accepts only status or trim`() {
        assertEquals(2, cli(withEmulator()).run(listOf("pool")))
        assertEquals(2, cli(withEmulator()).run(listOf("pool", "grow")))
    }

    private fun runningEmulatorOfTask9() {
        resourceLimits.settings = ResourceSettings(CapacityProfile.CUSTOM, ResourceLimits(emulators = 1, simulators = null, gradleDaemons = null))
        store.writePorts("$common/worktrees/TASK-9", mapOf("emulator" to 5556))
        store.writeLeases("$common/worktrees/TASK-9", mapOf("emulator" to clock.toEpochMilli()))
        androidSdk.states["emulator-5556"] = EmulatorState.RUNNING
    }

    @Test
    fun `wait options only apply to start and need positive minutes`() {
        assertEquals(2, cli(withEmulator()).run(listOf("emulator", "status", "--wait")))
        assertTrue(err.contains("--wait and --wait-timeout only apply to 'emulator start'"), err.toString())
        assertEquals(2, cli(withSimulator()).run(listOf("simulator", "stop", "--wait-timeout", "5")))
        assertEquals(2, cli(withSimulator()).run(listOf("simulator", "start", "--wait-timeout", "0")))
        assertEquals(2, cli(withEmulator()).run(listOf("emulator", "start", "--wait-timeout", "soon")))
    }

    @Test
    fun `emulator start with wait queues until the lease expires, showing its place once`() {
        runningEmulatorOfTask9()
        val leaseEnd = clock.plusSeconds(20 * 60).toLocalClockTime()

        val exitCode = cli(withEmulator()).run(listOf("emulator", "start", "--wait"))

        assertEquals(0, exitCode, err.toString())
        val waiting = err.lines().filter { it.startsWith("waiting for") }
        assertEquals(
            listOf(
                "waiting for an emulator: 1st of 1 in the queue, limit 1: main-repo · TASK-9 running, lease until $leaseEnd; " +
                    "next lease expires at $leaseEnd",
            ),
            waiting,
        )
        assertTrue(err.contains("released the emulator of main-repo · TASK-9 to make room"), err.toString())
        assertTrue(err.contains("waited 20 min"), err.toString())
        assertTrue(out.contains("export ANDROID_SERIAL=emulator-5554"), out.toString())
        assertTrue(waitQueue.tickets.isEmpty())
    }

    @Test
    fun `emulator start json reports the seconds waited, and null without wait`() {
        runningEmulatorOfTask9()

        assertEquals(0, cli(withEmulator()).run(listOf("emulator", "start", "--wait", "--json")), err.toString())
        assertEquals("1200", jsonOut().getValue("waited").toString())

        out.clear()
        assertEquals(0, cli(withEmulator()).run(listOf("emulator", "start", "--json")), err.toString())
        assertEquals("null", jsonOut().getValue("waited").toString())
    }

    @Test
    fun `a wait that times out fails with nothing on standard output`() {
        runningEmulatorOfTask9()
        onPause = { store.writeLeases("$common/worktrees/TASK-9", mapOf("emulator" to clock.toEpochMilli())) }

        val exitCode = cli(withEmulator()).run(listOf("emulator", "start", "--wait-timeout", "1", "--json"))

        assertEquals(1, exitCode)
        assertEquals("", out.toString())
        assertTrue(err.contains("Waited 1 min for an emulator and none could be released: main-repo · TASK-9 (running"), err.toString())
        assertTrue(androidSdk.started.isEmpty())
        assertTrue(waitQueue.tickets.isEmpty())
    }

    @Test
    fun `pool status lists the worktrees waiting for each device pool`() {
        resourceLimits.settings = ResourceSettings(CapacityProfile.CUSTOM, ResourceLimits(emulators = 1, simulators = 1, gradleDaemons = 4))
        val since = clock.minusSeconds(120)
        waitQueue.write(
            ResourceKind.EMULATOR,
            listOf(WaitTicket("ticket", "$common/worktrees/TASK-9", "/home/dev/projects/worktrees/TASK-9", ProcessIdentity(1, 1), since.toEpochMilli())),
        )

        assertEquals(0, cli(withEmulator()).run(listOf("pool", "status")), err.toString())
        assertTrue(out.contains("emulators 0/1\n  waiting: main-repo · TASK-9 since ${since.toLocalClockTime()}\nsimulators 0/1\n"), out.toString())

        out.clear()
        assertEquals(0, cli(withEmulator()).run(listOf("pool", "status", "--json")))
        val pools = jsonOut().getValue("pools") as kotlinx.serialization.json.JsonArray
        val waiting = pools.map { ((it as kotlinx.serialization.json.JsonObject).getValue("waiting") as kotlinx.serialization.json.JsonArray).size }
        assertEquals(listOf(1, 0, 0), waiting)
    }
}
