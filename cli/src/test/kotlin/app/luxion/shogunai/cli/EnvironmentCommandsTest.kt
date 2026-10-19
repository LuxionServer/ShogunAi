package app.luxion.shogunai.cli

import app.luxion.shogunai.domain.FakeAndroidSdk
import app.luxion.shogunai.domain.FakeAppleSimulators
import app.luxion.shogunai.domain.FakeDerivedDataSource
import app.luxion.shogunai.domain.FakeEnvironmentStateStore
import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.FakeHerdrConfigSource
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
import app.luxion.shogunai.domain.model.parseEnvironmentVariable
import java.nio.file.Files
import kotlin.io.path.readText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EnvironmentCommandsTest {

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

    @AfterTest
    fun cleanUp() {
        Files.deleteIfExists(envFile)
    }

    private val executor = object : ShellCommandExecutor {
        override suspend fun execute(command: List<String>, workingDirectory: String?): CommandResult {
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

    private fun cli(
        provisioning: FakeRepositoryProvisioningSource,
        workingDirectory: String = linked,
        input: String = "",
        environment: Map<String, String> = mapOf("CLAUDE_ENV_FILE" to envFile.toString()),
    ) = ShogunCli(
        executor = executor,
        fileManager = FakeFileManager(existing = listOf(base, linked)),
        projectRepository = FakeProjectRepository(emptyList()),
        provisioningSource = provisioning,
        daemonSource = FakeGradleDaemonSource(),
        gradleUserHome = "/home/dev/.gradle",
        environmentStateStore = store,
        portProbe = FakePortProbe(),
        androidSdk = androidSdk,
        appleSimulators = appleSimulators,
        kmpTestWiring = kmpTestWiring,
        derivedDataSource = FakeDerivedDataSource(),
        herdrConfig = FakeHerdrConfigSource(),
        workingDirectory = workingDirectory,
        out = out,
        err = err,
        readInput = { input },
        canonicalPath = { it.trimEnd('/') },
        environmentVariable = { environment[it] },
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
        assertEquals("UDID-1 booted  ShogunAi · main-repo · TASK-123\n", out.toString())
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
        val exitCode = cli(withSimulator(), input = """{"cwd": "$linked"}""").run(listOf("hook", "session-start"))

        assertEquals(0, exitCode, err.toString())
        assertEquals("export IOS_SIMULATOR_UDID='UDID-1'\n", envFile.readText())
        assertTrue(out.contains("boot it with 'shogun simulator start'"), out.toString())
        assertTrue(out.contains("xcodebuild -destination \"id=\$IOS_SIMULATOR_UDID\""), out.toString())
    }

    @Test
    fun `emulator start boots the worktree's emulator and status reports it`() {
        val start = cli(withEmulator()).run(listOf("emulator", "start", "--no-window"))

        assertEquals(0, start, err.toString())
        assertEquals(listOf(FakeAndroidSdk.Started("Medium_Phone_API_35", 5554, noWindow = true)), androidSdk.started)

        assertEquals(0, cli(withEmulator()).run(listOf("emulator", "stop")))
        assertEquals(0, cli(withEmulator()).run(listOf("emulator", "status", linked)))
        assertEquals("emulator-5554 running\nemulator-5554 stopped\nemulator-5554 stopped\n", out.toString())
    }

    @Test
    fun `emulator status before any reservation says how to start one`() {
        assertEquals(0, cli(withEmulator()).run(listOf("emulator", "status")))
        assertTrue(out.startsWith("No emulator port reserved"), out.toString())
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
        val exitCode = cli(withEmulator(), input = """{"cwd": "$linked"}""").run(listOf("hook", "session-start"))

        assertEquals(0, exitCode, err.toString())
        assertEquals("export ANDROID_SERIAL='emulator-5554'\n", envFile.readText())
        assertTrue(out.contains("ANDROID_SERIAL=emulator-5554; start it with 'shogun emulator start'"), out.toString())
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
        assertEquals("ShogunAi environment of worktree TASK-123: PORT=20000\n", out.toString())
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
    fun `session-start does nothing without an environment or outside a repository`() {
        assertEquals(0, cli(declaring(), input = """{"cwd": "$linked"}""").run(listOf("hook", "session-start")))
        assertEquals(0, cli(declaring("PORT" to "\${port}"), input = """{"cwd": "/tmp/elsewhere"}""").run(listOf("hook", "session-start")))

        assertEquals("", out.toString())
        assertEquals("", envFile.readText())
    }

    @Test
    fun `session-start with an unreadable declaration exits 1 without writing`() {
        val provisioning = FakeRepositoryProvisioningSource(unreadable = setOf(base))

        val exitCode = cli(provisioning, input = """{"cwd": "$linked"}""").run(listOf("hook", "session-start"))

        assertEquals(1, exitCode)
        assertTrue(err.contains(".shogunai.json"), err.toString())
        assertFalse(envFile.readText().isNotEmpty())
    }
}
