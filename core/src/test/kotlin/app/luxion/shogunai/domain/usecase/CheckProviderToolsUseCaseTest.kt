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
import app.luxion.shogunai.domain.model.HostOs
import app.luxion.shogunai.domain.model.InstallationCheck
import app.luxion.shogunai.domain.model.InstallationFinding
import app.luxion.shogunai.domain.model.IosSimulatorProvider
import app.luxion.shogunai.domain.model.MavenProvider
import app.luxion.shogunai.domain.model.ProviderToolId
import app.luxion.shogunai.domain.model.ProviderToolProblem
import app.luxion.shogunai.domain.model.RepositoryProviders
import app.luxion.shogunai.domain.model.SimulatorRuntime
import app.luxion.shogunai.domain.model.XcodeProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
        properties: Map<String, Map<String, String>> = emptyMap(),
        variables: Map<String, String> = emptyMap(),
        os: HostOs = HostOs.MACOS,
    ): List<InstallationCheck> =
        CheckProviderToolsUseCase(executor, { executables[it] }, FakeFileManager(existing = files, properties = properties), androidSdk, simulators, { variables[it] }, os)(
            "main-repo",
            repository,
            providers,
        )

    private fun List<InstallationCheck>.finding(): InstallationFinding.ProviderTool = first().finding as InstallationFinding.ProviderTool

    private fun List<InstallationCheck>.dependent(): InstallationCheck = also { assertEquals(2, size) }.last()

    private fun InstallationCheck.provider(): InstallationFinding.ProviderTool = finding as InstallationFinding.ProviderTool

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

        assertEquals(CheckState.PASSED, checks.first().state)
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

        assertEquals(CheckState.PASSED, checks.first().state)
        assertEquals("2.39.1", checks.finding().version)
        assertEquals(listOf("/usr/local/bin/docker", "compose", "version"), executor.executedCommands.first())
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
        val check = CheckProviderToolsUseCase(FakeShellCommandExecutor(), { null }, FakeFileManager(), FakeAndroidSdk(), FakeAppleSimulators(), { null }, HostOs.MACOS)
            .unreadable("main-repo", "malformed")

        assertEquals(CheckState.WARNING, check.state)
        assertEquals(
            InstallationFinding.ProviderTool("main-repo", ProviderToolId.PROVIDERS, null, ProviderToolProblem.ProvidersUnreadable("malformed")),
            check.finding,
        )
    }

    private val gradleWrapper = "$repository/gradlew"
    private val gradleProperties = "$repository/gradle/wrapper/gradle-wrapper.properties"
    private val mavenWrapper = "$repository/mvnw"
    private val mavenProperties = "$repository/.mvn/wrapper/maven-wrapper.properties"
    private val docker = mapOf("docker" to "/usr/local/bin/docker")

    private fun gradleDistribution(version: String) =
        mapOf(gradleProperties to mapOf("distributionUrl" to "https://services.gradle.org/distributions/gradle-$version-bin.zip"))

    private fun mavenDistribution(version: String) = mapOf(
        mavenProperties to mapOf("distributionUrl" to "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/$version/apache-maven-$version-bin.zip"),
    )

    private fun javaPrints(version: String, others: (List<String>) -> CommandResult = { FakeShellCommandExecutor.success(it) }): (List<String>) -> CommandResult = {
        if (it.getOrNull(1) == "-version") CommandResult(it, 0, "", "openjdk version \"$version\" 2024-10-15\nOpenJDK Runtime Environment") else others(it)
    }

    private suspend fun gradleJava(gradle: String, java: String): InstallationCheck = check(
        RepositoryProviders(gradle = GradleProvider()),
        executables = mapOf("java" to "/usr/bin/java"),
        files = listOf(gradleWrapper),
        properties = gradleDistribution(gradle),
        run = javaPrints(java),
    ).dependent()

    @Test
    fun `the gradle wrapper's version comes from its properties and its java passes`() = runTest {
        val executor = FakeShellCommandExecutor(javaPrints("21.0.5"))

        val checks = check(
            RepositoryProviders(gradle = GradleProvider()),
            executables = mapOf("java" to "/usr/bin/java"),
            files = listOf(gradleWrapper),
            properties = gradleDistribution("8.10.2"),
            executor = executor,
        )

        assertEquals("8.10.2", checks.finding().version)
        val java = checks.dependent()
        assertEquals(CheckState.PASSED, java.state)
        assertEquals(InstallationFinding.ProviderTool("main-repo", ProviderToolId.GRADLE_JAVA, "/usr/bin/java", version = "21"), java.finding)
        assertEquals(listOf(listOf("/usr/bin/java", "-version")), executor.executedCommands)
    }

    @Test
    fun `Gradle 9 needs Java 17`() = runTest {
        val java = gradleJava("9.1.0", "11.0.24")

        assertEquals(CheckState.WARNING, java.state)
        assertEquals(ProviderToolProblem.JavaTooOld(11, 17, "9.1.0"), java.provider().problem)
        assertEquals("/usr/bin/java", java.provider().location)
    }

    @Test
    fun `Java 21 needs Gradle 8_5`() = runTest {
        assertEquals(ProviderToolProblem.GradleTooOld(21, "8.4", "8.5"), gradleJava("8.4", "21.0.5").provider().problem)
    }

    @Test
    fun `a Java newer than the table isn't judged`() = runTest {
        assertEquals(CheckState.PASSED, gradleJava("9.1.0", "27").state)
    }

    @Test
    fun `a Gradle version that isn't a number only needs Java to run`() = runTest {
        assertEquals(CheckState.PASSED, gradleJava("nightly", "11.0.24").state)
    }

    @Test
    fun `a wrapper without properties has no version and only needs Java to run`() = runTest {
        val checks = check(
            RepositoryProviders(gradle = GradleProvider()),
            executables = mapOf("java" to "/usr/bin/java"),
            files = listOf(gradleWrapper),
            run = javaPrints("11.0.24"),
        )

        assertEquals(null, checks.finding().version)
        assertEquals(CheckState.PASSED, checks.dependent().state)
    }

    @Test
    fun `gradle on the PATH prints its version`() = runTest {
        val printed = "\n------------------------------------------------------------\nGradle 8.10.2\n------------------------------------------------------------\n"
        val executor = FakeShellCommandExecutor(javaPrints("21.0.5") { FakeShellCommandExecutor.success(it, printed) })

        val checks = check(
            RepositoryProviders(gradle = GradleProvider()),
            executables = mapOf("gradle" to "/opt/homebrew/bin/gradle", "java" to "/usr/bin/java"),
            executor = executor,
        )

        assertEquals("8.10.2", checks.finding().version)
        assertTrue(executor.executed("/opt/homebrew/bin/gradle", "--version"))
        assertEquals(CheckState.PASSED, checks.dependent().state)
    }

    @Test
    fun `a gradle that can't print its version still passes`() = runTest {
        val failing = check(
            RepositoryProviders(gradle = GradleProvider()),
            executables = mapOf("gradle" to "/opt/homebrew/bin/gradle"),
            run = javaPrints("21") { FakeShellCommandExecutor.failure(it) },
        )
        val throwing = check(
            RepositoryProviders(gradle = GradleProvider()),
            executables = mapOf("gradle" to "/opt/homebrew/bin/gradle"),
            run = javaPrints("21") { throw IOException("cannot run gradle") },
        )

        listOf(failing, throwing).forEach { checks ->
            assertEquals(CheckState.PASSED, checks.first().state)
            assertEquals(null, checks.finding().version)
        }
    }

    @Test
    fun `cancelling while gradle prints its version stops the check`() = runTest {
        assertFailsWith<CancellationException> {
            check(
                RepositoryProviders(gradle = GradleProvider()),
                executables = mapOf("gradle" to "/opt/homebrew/bin/gradle"),
                run = { throw CancellationException("cancelled") },
            )
        }
    }

    @Test
    fun `Maven 4 needs Java 17`() = runTest {
        val checks = check(
            RepositoryProviders(maven = MavenProvider()),
            executables = mapOf("java" to "/usr/bin/java"),
            files = listOf(mavenWrapper),
            properties = mavenDistribution("4.0.0"),
            run = javaPrints("11.0.24"),
        )

        assertEquals("4.0.0", checks.finding().version)
        val java = checks.dependent()
        assertEquals("provider:main-repo:maven-java", java.finding.id)
        assertEquals(ProviderToolProblem.JavaTooOld(11, 17, "4.0.0"), java.provider().problem)
    }

    @Test
    fun `mvn on the PATH prints its version and Java 8's old format is read`() = runTest {
        val run: (List<String>) -> CommandResult = {
            when (it.getOrNull(1)) {
                "-version" -> CommandResult(it, 0, "", "java version \"1.8.0_412\"\nJava(TM) SE Runtime Environment")
                else -> FakeShellCommandExecutor.success(it, "Apache Maven 3.9.9 (8e8579a9e76f7d015ee5ec7bfcdc97d260186937)\nMaven home: /opt/maven")
            }
        }

        val checks = check(RepositoryProviders(maven = MavenProvider()), executables = mapOf("mvn" to "/opt/homebrew/bin/mvn", "java" to "/usr/bin/java"), run = run)

        assertEquals("3.9.9", checks.finding().version)
        assertEquals(CheckState.PASSED, checks.dependent().state)
        assertEquals("8", checks.dependent().provider().version)
    }

    @Test
    fun `older Maven runs on Java 7 and Maven never judges a newer Java`() = runTest {
        val maven = { version: String, java: String ->
            suspend {
                check(
                    RepositoryProviders(maven = MavenProvider()),
                    executables = mapOf("java" to "/usr/bin/java"),
                    files = listOf(mavenWrapper),
                    properties = mavenDistribution(version),
                    run = javaPrints(java),
                ).dependent().state
            }
        }

        assertEquals(CheckState.PASSED, maven("3.8.8", "1.7.0_80")())
        assertEquals(CheckState.PASSED, maven("3.9.9", "25")())
    }

    @Test
    fun `no Java check when Maven isn't found`() = runTest {
        assertEquals(1, check(RepositoryProviders(maven = MavenProvider()), executables = mapOf("java" to "/usr/bin/java")).size)
    }

    @Test
    fun `JAVA_HOME's java is the one checked`() = runTest {
        val executor = FakeShellCommandExecutor(javaPrints("21.0.5"))

        val checks = check(
            RepositoryProviders(maven = MavenProvider()),
            executables = mapOf("java" to "/usr/bin/java"),
            files = listOf(mavenWrapper, "/opt/jdk-21/bin/java"),
            variables = mapOf("JAVA_HOME" to "/opt/jdk-21/"),
            executor = executor,
        )

        assertEquals("/opt/jdk-21/bin/java", checks.dependent().provider().location)
        assertTrue(executor.executed("/opt/jdk-21/bin/java", "-version"))
    }

    @Test
    fun `JAVA_HOME without java warns without falling back to the PATH`() = runTest {
        val executor = FakeShellCommandExecutor()

        val checks = check(
            RepositoryProviders(gradle = GradleProvider()),
            executables = mapOf("java" to "/usr/bin/java"),
            files = listOf(gradleWrapper),
            variables = mapOf("JAVA_HOME" to "/opt/jdk-gone"),
            executor = executor,
        )

        assertEquals(InstallationFinding.ProviderTool("main-repo", ProviderToolId.GRADLE_JAVA, null, ProviderToolProblem.JavaHomeInvalid("/opt/jdk-gone")), checks.dependent().finding)
        assertTrue(executor.executedCommands.isEmpty())
    }

    @Test
    fun `a blank JAVA_HOME uses java on the PATH`() = runTest {
        val checks = check(
            RepositoryProviders(gradle = GradleProvider()),
            executables = mapOf("java" to "/usr/bin/java"),
            files = listOf(gradleWrapper),
            variables = mapOf("JAVA_HOME" to " "),
            run = javaPrints("21"),
        )

        assertEquals("/usr/bin/java", checks.dependent().provider().location)
    }

    @Test
    fun `JAVA_HOME on Windows points to java_exe`() = runTest {
        val executor = FakeShellCommandExecutor(javaPrints("21.0.5"))

        check(
            RepositoryProviders(gradle = GradleProvider()),
            files = listOf(gradleWrapper, "C:\\jdk-21\\bin\\java.exe"),
            variables = mapOf("JAVA_HOME" to "C:\\jdk-21\\"),
            os = HostOs.WINDOWS,
            executor = executor,
        )

        assertTrue(executor.executed("C:\\jdk-21\\bin\\java.exe", "-version"))
    }

    @Test
    fun `macOS's java stub without a JDK doesn't work`() = runTest {
        val stub = "The operation couldn't be completed. Unable to locate a Java Runtime."

        val java = check(
            RepositoryProviders(gradle = GradleProvider()),
            executables = mapOf("java" to "/usr/bin/java"),
            files = listOf(gradleWrapper),
            run = { FakeShellCommandExecutor.failure(it, stderr = "$stub\n") },
        ).dependent()

        assertEquals(ProviderToolProblem.Unusable(stub), java.provider().problem)
        assertEquals("/usr/bin/java", java.provider().location)
    }

    @Test
    fun `java that prints no version doesn't work`() = runTest {
        val java = check(
            RepositoryProviders(gradle = GradleProvider()),
            executables = mapOf("java" to "/usr/bin/java"),
            files = listOf(gradleWrapper),
            run = { FakeShellCommandExecutor.success(it, "unexpected") },
        ).dependent()

        assertEquals(ProviderToolProblem.Unusable("unexpected"), java.provider().problem)
    }

    @Test
    fun `no java warns, naming JAVA_HOME and the PATH`() = runTest {
        val java = check(RepositoryProviders(gradle = GradleProvider()), files = listOf(gradleWrapper)).dependent()

        assertEquals(ProviderToolProblem.NotFound(listOf("JAVA_HOME"), listOf("java")), java.provider().problem)
    }

    private fun composePrints(version: String, info: (List<String>) -> CommandResult = { FakeShellCommandExecutor.success(it, "27.3.1\n") }): (List<String>) -> CommandResult = {
        if (it.getOrNull(1) == "compose") FakeShellCommandExecutor.success(it, version) else info(it)
    }

    @Test
    fun `docker compose older than 2_0 warns and the daemon is still checked`() = runTest {
        val checks = check(
            RepositoryProviders(dockerCompose = DockerComposeProvider()),
            executables = docker,
            run = composePrints("docker-compose version 1.29.2, build 5becea4c"),
        )

        assertEquals(CheckState.WARNING, checks.first().state)
        assertEquals(ProviderToolProblem.TooOld("1.29.2", "2.0"), checks.finding().problem)
        assertEquals(CheckState.PASSED, checks.dependent().state)
    }

    @Test
    fun `a Compose version that can't be read passes`() = runTest {
        val checks = check(RepositoryProviders(dockerCompose = DockerComposeProvider()), executables = docker, run = composePrints("Docker Compose version dev"))

        assertEquals(CheckState.PASSED, checks.first().state)
        assertEquals(null, checks.finding().version)
    }

    @Test
    fun `a running daemon passes, naming its version`() = runTest {
        val executor = FakeShellCommandExecutor(composePrints("Docker Compose version v2.29.1"))

        val daemon = check(RepositoryProviders(dockerCompose = DockerComposeProvider()), executables = docker, executor = executor).dependent()

        assertEquals(InstallationFinding.ProviderTool("main-repo", ProviderToolId.DOCKER_DAEMON, null, version = "27.3.1"), daemon.finding)
        assertTrue(executor.executed("/usr/local/bin/docker", "info", "--format", "{{.ServerVersion}}"))
    }

    @Test
    fun `a running daemon without a version passes`() = runTest {
        val daemon = check(
            RepositoryProviders(dockerCompose = DockerComposeProvider()),
            executables = docker,
            run = composePrints("Docker Compose version v2.29.1") { FakeShellCommandExecutor.success(it, " ") },
        ).dependent()

        assertEquals(CheckState.PASSED, daemon.state)
        assertEquals(null, daemon.provider().version)
    }

    @Test
    fun `a stopped daemon warns with its output and the operating system`() = runTest {
        val error = "Cannot connect to the Docker daemon at unix:///var/run/docker.sock. Is the docker daemon running?"
        val stopped = composePrints("Docker Compose version v2.29.1") { FakeShellCommandExecutor.failure(it, stderr = error) }

        listOf(HostOs.MACOS, HostOs.LINUX).forEach { os ->
            val daemon = check(RepositoryProviders(dockerCompose = DockerComposeProvider()), executables = docker, run = stopped, os = os).dependent()

            assertEquals(CheckState.WARNING, daemon.state)
            assertEquals(ProviderToolProblem.DaemonNotRunning(os, error), daemon.provider().problem)
        }
    }

    @Test
    fun `no daemon check when docker compose fails`() = runTest {
        val checks = check(
            RepositoryProviders(dockerCompose = DockerComposeProvider()),
            executables = docker,
            run = { FakeShellCommandExecutor.failure(it) },
        )

        assertEquals(1, checks.size)
    }
}
