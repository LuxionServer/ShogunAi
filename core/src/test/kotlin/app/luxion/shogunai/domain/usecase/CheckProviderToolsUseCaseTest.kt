package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeAndroidSdk
import app.luxion.shogunai.domain.FakeAppleSimulators
import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.executor.CommandResult
import app.luxion.shogunai.domain.model.CheckState
import app.luxion.shogunai.domain.model.DockerComposeProvider
import app.luxion.shogunai.domain.model.EmulatorProvider
import app.luxion.shogunai.domain.model.FlutterProvider
import app.luxion.shogunai.domain.model.GradleProvider
import app.luxion.shogunai.domain.model.InstallationCheck
import app.luxion.shogunai.domain.model.InstallationFinding
import app.luxion.shogunai.domain.model.IosSimulatorProvider
import app.luxion.shogunai.domain.model.MavenProvider
import app.luxion.shogunai.domain.model.ProviderToolId
import app.luxion.shogunai.domain.model.ProviderToolProblem
import app.luxion.shogunai.domain.model.RepositoryProviders
import app.luxion.shogunai.domain.model.SimulatorRuntime
import app.luxion.shogunai.domain.model.XcodeProvider
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CheckProviderToolsUseCaseTest {

    private val repository = "/home/dev/projects/main-repo"

    private suspend fun check(
        providers: RepositoryProviders,
        executables: Map<String, String> = emptyMap(),
        files: List<String> = emptyList(),
        androidSdk: FakeAndroidSdk = FakeAndroidSdk(),
        simulators: FakeAppleSimulators = FakeAppleSimulators(),
        run: (List<String>) -> CommandResult = { FakeShellCommandExecutor.success(it) },
        executor: FakeShellCommandExecutor = FakeShellCommandExecutor(run),
    ): List<InstallationCheck> =
        CheckProviderToolsUseCase(executor, { executables[it] }, FakeFileManager(existing = files), androidSdk, simulators)("main-repo", repository, providers)

    private fun List<InstallationCheck>.finding(): InstallationFinding.ProviderTool = single().finding as InstallationFinding.ProviderTool

    @Test
    fun `no providers means no checks`() = runTest {
        assertTrue(check(RepositoryProviders()).isEmpty())
    }

    @Test
    fun `every declared provider gets its tools checked, in order`() = runTest {
        val providers = RepositoryProviders(
            gradle = GradleProvider(),
            emulator = EmulatorProvider(),
            iosSimulator = IosSimulatorProvider(),
            xcode = XcodeProvider(cocoaPods = true),
            maven = MavenProvider(),
            dockerCompose = DockerComposeProvider(),
            flutter = FlutterProvider,
        )

        val checks = check(providers)

        assertEquals(
            listOf("gradle", "maven", "android-sdk", "ios-simulator", "xcode", "cocoapods", "docker-compose", "flutter").map { "provider:main-repo:$it" },
            checks.map { it.finding.id },
        )
    }

    @Test
    fun `the gradle wrapper in the repository passes`() = runTest {
        val checks = check(RepositoryProviders(gradle = GradleProvider()), files = listOf("$repository/gradlew"))

        assertEquals(CheckState.PASSED, checks.single().state)
        assertEquals("$repository/gradlew", checks.finding().location)
    }

    @Test
    fun `gradle on the PATH passes without a wrapper`() = runTest {
        val checks = check(RepositoryProviders(gradle = GradleProvider()), executables = mapOf("gradle" to "/usr/local/bin/gradle"))

        assertEquals("/usr/local/bin/gradle", checks.finding().location)
    }

    @Test
    fun `maven without a wrapper or mvn warns, naming where it looked`() = runTest {
        val checks = check(RepositoryProviders(maven = MavenProvider()))

        assertEquals(CheckState.WARNING, checks.single().state)
        assertEquals(ProviderToolProblem.NotFound(listOf("$repository/mvnw"), listOf("mvn")), checks.finding().problem)
    }

    @Test
    fun `an Android SDK with virtual devices passes, counting them`() = runTest {
        val checks = check(RepositoryProviders(emulator = EmulatorProvider()), androidSdk = FakeAndroidSdk(avds = listOf("Pixel", "Tablet")))

        assertEquals(CheckState.PASSED, checks.single().state)
        assertEquals("/home/dev/Android/sdk", checks.finding().location)
        assertEquals(2, checks.finding().installed)
    }

    @Test
    fun `a missing Android SDK warns, naming where it looked`() = runTest {
        val checks = check(RepositoryProviders(emulator = EmulatorProvider()), androidSdk = FakeAndroidSdk(missing = true))

        assertEquals(CheckState.WARNING, checks.single().state)
        assertEquals(ProviderToolProblem.NotFound(listOf("ANDROID_HOME", "/home/dev/Android/sdk")), checks.finding().problem)
    }

    @Test
    fun `an Android SDK without virtual devices warns`() = runTest {
        val checks = check(RepositoryProviders(emulator = EmulatorProvider()), androidSdk = FakeAndroidSdk(avds = emptyList()))

        assertEquals(ProviderToolProblem.NoAndroidVirtualDevice, checks.finding().problem)
        assertEquals("/home/dev/Android/sdk", checks.finding().location)
    }

    @Test
    fun `iOS simulators pass counting only iOS runtimes`() = runTest {
        val watch = SimulatorRuntime("com.apple.CoreSimulator.SimRuntime.watchOS-11-5", "watchOS 11.5", "11.5", "watchOS", emptyList())
        val simulators = FakeAppleSimulators(installedRuntimes = listOf(FakeAppleSimulators.IOS_18_5, watch))

        val checks = check(RepositoryProviders(iosSimulator = IosSimulatorProvider()), simulators = simulators)

        assertEquals(CheckState.PASSED, checks.single().state)
        assertEquals(1, checks.finding().installed)
    }

    @Test
    fun `no iOS runtime warns`() = runTest {
        val checks = check(RepositoryProviders(iosSimulator = IosSimulatorProvider()), simulators = FakeAppleSimulators(installedRuntimes = emptyList()))

        assertEquals(ProviderToolProblem.NoIosRuntime, checks.finding().problem)
    }

    @Test
    fun `Apple's providers need macOS`() = runTest {
        val checks = check(
            RepositoryProviders(iosSimulator = IosSimulatorProvider(), xcode = XcodeProvider()),
            simulators = FakeAppleSimulators(supported = false),
        )

        assertEquals(listOf(ProviderToolProblem.NeedsMacOs, ProviderToolProblem.NeedsMacOs), checks.map { (it.finding as InstallationFinding.ProviderTool).problem })
        assertTrue(checks.all { it.state == CheckState.WARNING })
    }

    @Test
    fun `Xcode passes when xcodebuild reads its version`() = runTest {
        val executor = FakeShellCommandExecutor { FakeShellCommandExecutor.success(it, "Xcode 26.0") }

        val checks = check(RepositoryProviders(xcode = XcodeProvider()), executables = mapOf("xcodebuild" to "/usr/bin/xcodebuild"), executor = executor)

        assertEquals(CheckState.PASSED, checks.single().state)
        assertEquals(listOf("/usr/bin/xcodebuild", "-version"), executor.executedCommands.single())
    }

    @Test
    fun `only the Command Line Tools make Xcode unusable, with their output`() = runTest {
        val error = "xcode-select: error: tool 'xcodebuild' requires Xcode"

        val checks = check(
            RepositoryProviders(xcode = XcodeProvider()),
            executables = mapOf("xcodebuild" to "/usr/bin/xcodebuild"),
            run = { FakeShellCommandExecutor.failure(it, stderr = "$error\n") },
        )

        assertEquals(ProviderToolProblem.Unusable(error), checks.finding().problem)
        assertEquals("/usr/bin/xcodebuild", checks.finding().location)
    }

    @Test
    fun `a failing tool with no output reports its exit code`() = runTest {
        val checks = check(
            RepositoryProviders(dockerCompose = DockerComposeProvider()),
            executables = mapOf("docker" to "/usr/local/bin/docker"),
            run = { FakeShellCommandExecutor.failure(it, exitCode = 125, stderr = " ") },
        )

        assertEquals(ProviderToolProblem.Unusable("exit code 125"), checks.finding().problem)
    }

    @Test
    fun `a failing tool's standard output is used when it wrote nothing to standard error`() = runTest {
        val checks = check(
            RepositoryProviders(dockerCompose = DockerComposeProvider()),
            executables = mapOf("docker" to "/usr/local/bin/docker"),
            run = { CommandResult(it, 1, "docker: 'compose' is not a docker command.", "") },
        )

        assertEquals(ProviderToolProblem.Unusable("docker: 'compose' is not a docker command."), checks.finding().problem)
    }

    @Test
    fun `docker compose passes when it reads its version`() = runTest {
        val executor = FakeShellCommandExecutor { FakeShellCommandExecutor.success(it, "Docker Compose version v2.39.1") }

        val checks = check(RepositoryProviders(dockerCompose = DockerComposeProvider()), executables = mapOf("docker" to "/usr/local/bin/docker"), executor = executor)

        assertEquals(CheckState.PASSED, checks.single().state)
        assertEquals(listOf("/usr/local/bin/docker", "compose", "version"), executor.executedCommands.single())
    }

    @Test
    fun `missing docker warns without running anything`() = runTest {
        val executor = FakeShellCommandExecutor()

        val checks = check(RepositoryProviders(dockerCompose = DockerComposeProvider()), executor = executor)

        assertEquals(ProviderToolProblem.NotFound(emptyList(), listOf("docker")), checks.finding().problem)
        assertTrue(executor.executedCommands.isEmpty())
    }

    @Test
    fun `CocoaPods is only checked when the Xcode provider declares it`() = runTest {
        val executables = mapOf("xcodebuild" to "/usr/bin/xcodebuild", "pod" to "/opt/homebrew/bin/pod")

        assertEquals(1, check(RepositoryProviders(xcode = XcodeProvider()), executables = executables).size)
        val withPods = check(RepositoryProviders(xcode = XcodeProvider(cocoaPods = true)), executables = executables)
        assertEquals("/opt/homebrew/bin/pod", (withPods.last().finding as InstallationFinding.ProviderTool).location)
    }

    @Test
    fun `flutter is looked for directly, or through fvm when the repository uses it`() = runTest {
        val executables = mapOf("flutter" to "/opt/flutter/bin/flutter", "fvm" to "/opt/homebrew/bin/fvm")

        assertEquals("/opt/flutter/bin/flutter", check(RepositoryProviders(flutter = FlutterProvider), executables = executables).finding().location)
        val withFvm = check(RepositoryProviders(flutter = FlutterProvider), executables = executables, files = listOf("$repository/.fvmrc"))
        assertEquals("/opt/homebrew/bin/fvm", withFvm.finding().location)
    }

    @Test
    fun `a lookup that throws makes that tool unusable with the reason`() = runTest {
        val checks = check(
            RepositoryProviders(xcode = XcodeProvider()),
            executables = mapOf("xcodebuild" to "/usr/bin/xcodebuild"),
            run = { throw IOException("cannot run xcodebuild") },
        )

        assertEquals(ProviderToolProblem.Unusable("cannot run xcodebuild"), checks.finding().problem)
    }

    @Test
    fun `unreadable providers warn with the reason`() {
        val check = CheckProviderToolsUseCase(FakeShellCommandExecutor(), { null }, FakeFileManager(), FakeAndroidSdk(), FakeAppleSimulators())
            .unreadable("main-repo", "malformed")

        assertEquals(CheckState.WARNING, check.state)
        assertEquals(
            InstallationFinding.ProviderTool("main-repo", ProviderToolId.PROVIDERS, null, ProviderToolProblem.ProvidersUnreadable("malformed")),
            check.finding,
        )
    }
}
