package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeAndroidSdk
import app.luxion.shogunai.domain.FakeAppleSimulators
import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.executor.CommandResult
import app.luxion.shogunai.domain.model.CheckState
import app.luxion.shogunai.domain.model.GradleProvider
import app.luxion.shogunai.domain.model.HostOs
import app.luxion.shogunai.domain.model.InstallationCheck
import app.luxion.shogunai.domain.model.InstallationFinding
import app.luxion.shogunai.domain.model.JavaSource
import app.luxion.shogunai.domain.model.ProviderToolId
import app.luxion.shogunai.domain.model.ProviderToolProblem
import app.luxion.shogunai.domain.model.RepositoryProviders
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CheckGradleBuildJavaTest {

    private val home = "/home/dev"
    private val repository = "$home/projects/main-repo"
    private val criteria = "$repository/gradle/gradle-daemon-jvm.properties"
    private val repositoryProperties = "$repository/gradle.properties"
    private val userProperties = "$home/.gradle/gradle.properties"
    private val catalog = "$repository/gradle/libs.versions.toml"
    private val systemJdks = "/Library/Java/JavaVirtualMachines"
    private val searchedOnMac = listOf(
        "$home/.gradle/jdks",
        "$home/.jdks",
        "$home/.sdkman/candidates/java",
        "$home/.asdf/installs/java",
        systemJdks,
        "$home/Library/Java/JavaVirtualMachines",
    )

    private class Build(
        val gradle: String? = "8.10.2",
        val clientJava: String? = "21.0.5",
        val javas: Map<String, String> = emptyMap(),
        val properties: Map<String, Map<String, String>> = emptyMap(),
        val files: List<String> = emptyList(),
        val texts: Map<String, String> = emptyMap(),
        val variables: Map<String, String> = emptyMap(),
        val os: HostOs = HostOs.MACOS,
        val run: ((List<String>) -> CommandResult)? = null,
    )

    private val executed = mutableListOf<List<String>>()

    private suspend fun checks(build: Build): List<InstallationCheck> {
        val wrapperProperties = "$repository/gradle/wrapper/gradle-wrapper.properties"
        val distribution = build.gradle?.let { mapOf(wrapperProperties to mapOf("distributionUrl" to "https://services.gradle.org/distributions/gradle-$it-bin.zip")) }
        val fileManager = FakeFileManager(existing = listOf("$repository/gradlew") + build.files, properties = build.properties + distribution.orEmpty())
        build.texts.forEach { (path, text) -> fileManager.writeText(path, text) }
        val run = build.run ?: { command ->
            val version = if (command.first() == CLIENT_JAVA) build.clientJava else build.javas[command.first()]
            if (version != null) CommandResult(command, 0, "", "openjdk version \"$version\" 2024-10-15") else FakeShellCommandExecutor.failure(command, stderr = "no java")
        }
        val executor = FakeShellCommandExecutor { executed += it; run(it) }
        val executables = mapOf("java" to CLIENT_JAVA)
        return CheckProviderToolsUseCase(executor, { executables[it] }, fileManager, FakeAndroidSdk(), FakeAppleSimulators(), { build.variables[it] }, build.os, home)(
            "main-repo",
            repository,
            RepositoryProviders(gradle = GradleProvider()),
        )
    }

    private suspend fun buildChecks(build: Build): List<InstallationFinding.ProviderTool> =
        checks(build).drop(2).map { it.finding as InstallationFinding.ProviderTool }

    private suspend fun only(build: Build): InstallationCheck = checks(build).drop(2).single()

    private fun release(home: String, version: String): Pair<String, String> = "$home/release" to "JAVA_VERSION=\"$version\""

    private fun daemon(location: String?, problem: ProviderToolProblem? = null, version: String? = null, provisioned: Boolean = false) =
        InstallationFinding.ProviderTool("main-repo", ProviderToolId.GRADLE_DAEMON_JAVA, location, problem, version = version, provisioned = provisioned)

    private fun criteriaFile(vararg entries: Pair<String, String>): Map<String, Map<String, String>> = mapOf(criteria to mapOf(*entries))

    @Test
    fun `a plain Gradle build only checks Gradle and its Java`() = runTest {
        val checks = checks(Build())

        assertEquals(listOf(ProviderToolId.GRADLE, ProviderToolId.GRADLE_JAVA), checks.map { (it.finding as InstallationFinding.ProviderTool).tool })
    }

    @Test
    fun `daemon JVM criteria take precedence over org gradle java home`() = runTest {
        val jdk21 = "$systemJdks/temurin-21.jdk/Contents/Home"
        val build = Build(
            files = listOf(criteria),
            properties = criteriaFile("toolchainVersion" to "21") + mapOf(repositoryProperties to mapOf("org.gradle.java.home" to "/opt/jdk-17")),
            texts = mapOf(release(jdk21, "21.0.5")),
        )

        val check = only(build)

        assertEquals(CheckState.PASSED, check.state)
        assertEquals(daemon(jdk21, version = "21"), check.finding)
        assertEquals(listOf(listOf(CLIENT_JAVA, "-version")), executed)
    }

    @Test
    fun `Gradle older than 8_8 ignores the daemon JVM criteria`() = runTest {
        val build = Build(gradle = "8.5", files = listOf(criteria), properties = criteriaFile("toolchainVersion" to "21"))

        assertTrue(buildChecks(build).isEmpty())
    }

    @Test
    fun `daemon JVM criteria apply when Gradle's version is unknown`() = runTest {
        val build = Build(gradle = null, clientJava = "17.0.12", files = listOf(criteria), properties = criteriaFile("toolchainVersion" to "21", "toolchainUrl.MAC_OS.AARCH64" to "https://example.com/jdk"))

        assertEquals(daemon(null, version = "21", provisioned = true), only(build).finding)
    }

    @Test
    fun `the client's Java counts as installed for the criteria`() = runTest {
        val build = Build(files = listOf(criteria), properties = criteriaFile("toolchainVersion" to "21"))

        assertEquals(daemon(CLIENT_JAVA, version = "21"), only(build).finding)
    }

    @Test
    fun `a criteria Java that isn't installed warns, naming where it looked`() = runTest {
        val build = Build(clientJava = "17.0.12", files = listOf(criteria), properties = criteriaFile("toolchainVersion" to "21"))

        val check = only(build)

        assertEquals(CheckState.WARNING, check.state)
        assertEquals(daemon(null, ProviderToolProblem.JavaNotInstalled(listOf(21), searchedOnMac), "21"), check.finding)
    }

    @Test
    fun `turned off downloads make a downloadable criteria Java missing`() = runTest {
        val build = Build(
            clientJava = "17.0.12",
            files = listOf(criteria),
            properties = criteriaFile("toolchainVersion" to "21", "toolchainUrl.MAC_OS.AARCH64" to "https://example.com/jdk") +
                mapOf(userProperties to mapOf("org.gradle.java.installations.auto-download" to "false")),
        )

        assertEquals(daemon(null, ProviderToolProblem.JavaNotInstalled(listOf(21), searchedOnMac), "21"), only(build).finding)
    }

    @Test
    fun `unreadable criteria warn and leave the Android Gradle Plugin unjudged`() = runTest {
        val build = Build(
            files = listOf(criteria),
            properties = criteriaFile("toolchainVersion" to "latest"),
            texts = mapOf("$repository/build.gradle.kts" to "plugins { id(\"com.android.application\") version \"8.5.2\" }"),
        )

        assertEquals(listOf(daemon(criteria, ProviderToolProblem.DaemonCriteriaUnreadable(criteria))), buildChecks(build))
    }

    @Test
    fun `a criteria Java too new for the project's Gradle warns`() = runTest {
        val build = Build(gradle = "8.10", files = listOf(criteria), properties = criteriaFile("toolchainVersion" to "25"))

        val problem = ProviderToolProblem.GradleTooOld(25, "8.10", "9.1", JavaSource.DAEMON_CRITERIA)
        assertEquals(daemon(null, problem, "25"), only(build).finding)
    }

    @Test
    fun `org gradle java home pointing at a JDK passes`() = runTest {
        val build = Build(
            properties = mapOf(repositoryProperties to mapOf("org.gradle.java.home" to "/opt/jdk-17")),
            files = listOf("/opt/jdk-17/bin/java"),
            javas = mapOf("/opt/jdk-17/bin/java" to "17.0.12"),
        )

        val check = only(build)

        assertEquals(CheckState.PASSED, check.state)
        assertEquals(daemon("/opt/jdk-17/bin/java", version = "17"), check.finding)
    }

    @Test
    fun `the user's org gradle java home wins over the repository's`() = runTest {
        val build = Build(
            variables = mapOf("GRADLE_USER_HOME" to "/opt/gradle-home"),
            properties = mapOf(
                "/opt/gradle-home/gradle.properties" to mapOf("org.gradle.java.home" to "/opt/jdk-21"),
                repositoryProperties to mapOf("org.gradle.java.home" to "/opt/jdk-17"),
            ),
            files = listOf("/opt/jdk-21/bin/java", "/opt/jdk-17/bin/java"),
            javas = mapOf("/opt/jdk-21/bin/java" to "21.0.5", "/opt/jdk-17/bin/java" to "17.0.12"),
        )

        assertEquals(daemon("/opt/jdk-21/bin/java", version = "21"), only(build).finding)
    }

    @Test
    fun `org gradle java home without java warns, naming the property's file`() = runTest {
        val build = Build(properties = mapOf(repositoryProperties to mapOf("org.gradle.java.home" to "/opt/jdk-gone")))

        assertEquals(daemon(null, ProviderToolProblem.GradleJavaHomeInvalid("/opt/jdk-gone", repositoryProperties)), only(build).finding)
    }

    @Test
    fun `org gradle java home whose java fails warns with its output`() = runTest {
        val build = Build(properties = mapOf(repositoryProperties to mapOf("org.gradle.java.home" to "/opt/jdk-broken")), files = listOf("/opt/jdk-broken/bin/java"))

        assertEquals(daemon("/opt/jdk-broken/bin/java", ProviderToolProblem.Unusable("no java")), only(build).finding)
    }

    @Test
    fun `org gradle java home too old for Gradle 9 warns`() = runTest {
        val build = Build(
            gradle = "9.1.0",
            properties = mapOf(repositoryProperties to mapOf("org.gradle.java.home" to "/opt/jdk-11")),
            files = listOf("/opt/jdk-11/bin/java"),
            javas = mapOf("/opt/jdk-11/bin/java" to "11.0.24"),
        )

        val problem = ProviderToolProblem.JavaTooOld(11, 17, "9.1.0", JavaSource.GRADLE_PROPERTY)
        assertEquals(daemon("/opt/jdk-11/bin/java", problem, "11"), only(build).finding)
    }

    @Test
    fun `org gradle java home on Windows runs java exe`() = runTest {
        val javaExe = "C:\\jdk-17\\bin\\java.exe"
        val build = Build(
            os = HostOs.WINDOWS,
            properties = mapOf(repositoryProperties to mapOf("org.gradle.java.home" to "C:\\jdk-17\\")),
            files = listOf(javaExe),
            javas = mapOf(javaExe to "17.0.12"),
        )

        assertEquals(daemon(javaExe, version = "17"), only(build).finding)
    }

    @Test
    fun `a java that throws makes the build's checks unusable with the reason`() = runTest {
        val build = Build(
            properties = mapOf(repositoryProperties to mapOf("org.gradle.java.home" to "/opt/jdk-17")),
            files = listOf("/opt/jdk-17/bin/java"),
            run = { command -> if (command.first() == CLIENT_JAVA) CommandResult(command, 0, "", "openjdk version \"21\"") else throw IOException("cannot run java") },
        )

        assertEquals(daemon(null, ProviderToolProblem.Unusable("cannot run java")), only(build).finding)
    }

    @Test
    fun `cancellation is not swallowed by the build's checks`() = runTest {
        val build = Build(
            properties = mapOf(repositoryProperties to mapOf("org.gradle.java.home" to "/opt/jdk-17")),
            files = listOf("/opt/jdk-17/bin/java"),
            run = { command -> if (command.first() == CLIENT_JAVA) CommandResult(command, 0, "", "openjdk version \"21\"") else throw CancellationException("cancelled") },
        )

        assertFailsWith<CancellationException> { checks(build) }
    }

    private fun toolchains(problem: ProviderToolProblem? = null, version: String, provisioned: Boolean = false) =
        InstallationFinding.ProviderTool("main-repo", ProviderToolId.GRADLE_TOOLCHAINS, null, problem, version = version, provisioned = provisioned)

    @Test
    fun `installed toolchains pass, naming their versions`() = runTest {
        val build = Build(
            texts = mapOf(
                "$repository/app/build.gradle.kts" to "kotlin { jvmToolchain(17) }",
                release("$home/.sdkman/candidates/java/17.0.12-tem", "17.0.12"),
            ),
        )

        val check = only(build)

        assertEquals(CheckState.PASSED, check.state)
        assertEquals(toolchains(version = "17"), check.finding)
    }

    @Test
    fun `missing toolchains pass when the foojay resolver downloads them`() = runTest {
        val build = Build(
            texts = mapOf(
                "$repository/build.gradle.kts" to "java { toolchain { languageVersion = JavaLanguageVersion.of(23) } }",
                "$repository/settings.gradle.kts" to "plugins { id(\"org.gradle.toolchains.foojay-resolver-convention\") version \"0.8.0\" }",
            ),
        )

        assertEquals(toolchains(version = "23", provisioned = true), only(build).finding)
    }

    @Test
    fun `missing toolchains warn, naming only the missing versions`() = runTest {
        val build = Build(texts = mapOf("$repository/build.gradle.kts" to "jvmToolchain(17)\njvmToolchain(21)"))

        val check = only(build)

        assertEquals(CheckState.WARNING, check.state)
        assertEquals(toolchains(ProviderToolProblem.JavaNotInstalled(listOf(17), searchedOnMac), "17, 21"), check.finding)
    }

    @Test
    fun `the foojay resolver doesn't help when downloads are turned off`() = runTest {
        val build = Build(
            texts = mapOf(
                "$repository/build.gradle.kts" to "jvmToolchain(17)",
                "$repository/settings.gradle" to "plugins { id 'org.gradle.toolchains.foojay-resolver' version '0.8.0' }",
            ),
            properties = mapOf(repositoryProperties to mapOf("org.gradle.java.installations.auto-download" to "false")),
        )

        assertEquals(toolchains(ProviderToolProblem.JavaNotInstalled(listOf(17), searchedOnMac), "17"), only(build).finding)
    }

    private fun agp(version: String, java: Int, problem: ProviderToolProblem? = null) =
        InstallationFinding.ProviderTool("main-repo", ProviderToolId.ANDROID_GRADLE_PLUGIN_JAVA, null, problem, version = version, java = java)

    private val agpCatalog = "[versions]\nagp = \"8.5.2\"\n[plugins]\nandroid-application = { id = \"com.android.application\", version.ref = \"agp\" }"

    @Test
    fun `the Android Gradle Plugin 8 passes on Gradle's Java 17`() = runTest {
        val check = only(Build(clientJava = "17.0.12", texts = mapOf(catalog to agpCatalog)))

        assertEquals(CheckState.PASSED, check.state)
        assertEquals(agp("8.5.2", 17), check.finding)
    }

    @Test
    fun `the Android Gradle Plugin 8 on Java 11 warns, pointing at JAVA_HOME`() = runTest {
        val build = Build(clientJava = "11.0.24", texts = mapOf("$repository/build.gradle" to "classpath 'com.android.tools.build:gradle:8.2.0'"))

        val check = only(build)

        assertEquals(CheckState.WARNING, check.state)
        assertEquals(agp("8.2.0", 11, ProviderToolProblem.AndroidGradlePluginNeedsJava(11, 17, "8.2.0", JavaSource.ENVIRONMENT)), check.finding)
    }

    @Test
    fun `the daemon JVM criteria choose the Android Gradle Plugin's Java`() = runTest {
        val build = Build(
            clientJava = "11.0.24",
            files = listOf(criteria),
            properties = criteriaFile("toolchainVersion" to "21", "toolchainUrl.MAC_OS.AARCH64" to "https://example.com/jdk"),
            texts = mapOf(catalog to agpCatalog.replace("8.5.2", "8.7.0")),
        )

        assertEquals(agp("8.7.0", 21), buildChecks(build).last())
    }

    @Test
    fun `org gradle java home chooses the Android Gradle Plugin's Java`() = runTest {
        val build = Build(
            properties = mapOf(repositoryProperties to mapOf("org.gradle.java.home" to "/opt/jdk-11")),
            files = listOf("/opt/jdk-11/bin/java"),
            javas = mapOf("/opt/jdk-11/bin/java" to "11.0.24"),
            texts = mapOf(catalog to agpCatalog),
        )

        val problem = ProviderToolProblem.AndroidGradlePluginNeedsJava(11, 17, "8.5.2", JavaSource.GRADLE_PROPERTY)
        assertEquals(agp("8.5.2", 11, problem), buildChecks(build).last())
    }

    @Test
    fun `no Android Gradle Plugin check when Gradle's Java is unknown`() = runTest {
        val build = Build(clientJava = null, texts = mapOf(catalog to agpCatalog))

        assertTrue(buildChecks(build).isEmpty())
    }

    @Test
    fun `daemon JVM criteria apply when Gradle's version isn't numeric`() = runTest {
        val build = Build(gradle = "snapshot", files = listOf(criteria), properties = criteriaFile("toolchainVersion" to "21"))

        assertEquals(daemon(CLIENT_JAVA, version = "21"), only(build).finding)
    }

    @Test
    fun `an installed criteria Java too new for Gradle names where it is`() = runTest {
        val jdk25 = "$systemJdks/temurin-25.jdk/Contents/Home"
        val build = Build(gradle = "8.10", files = listOf(criteria), properties = criteriaFile("toolchainVersion" to "25"), texts = mapOf(release(jdk25, "25")))

        assertEquals(daemon(jdk25, ProviderToolProblem.GradleTooOld(25, "8.10", "9.1", JavaSource.DAEMON_CRITERIA), "25"), only(build).finding)
    }

    private companion object {
        const val CLIENT_JAVA = "/usr/bin/java"
    }
}
