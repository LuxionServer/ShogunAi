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
import app.luxion.shogunai.domain.model.ProviderToolId
import app.luxion.shogunai.domain.model.ProviderToolProblem
import app.luxion.shogunai.domain.model.RepositoryProviders
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class CheckGradleBuildFolderTest {

    private val home = "/home/dev"
    private val repository = "$home/projects/main-repo"
    private val android = "$repository/android"
    private val server = "$repository/server"
    private val clientJava = "/usr/bin/java"
    private val pathGradle = "/usr/local/bin/gradle"

    private fun build(folder: String, gradle: String = "8.10.2"): Pair<List<String>, Map<String, Map<String, String>>> =
        listOf("$folder/gradlew", "$folder/settings.gradle.kts") to mapOf(
            "$folder/gradle/wrapper/gradle-wrapper.properties" to
                mapOf("distributionUrl" to "https://services.gradle.org/distributions/gradle-$gradle-bin.zip"),
        )

    private suspend fun checks(
        builds: List<Pair<List<String>, Map<String, Map<String, String>>>>,
        properties: Map<String, Map<String, String>> = emptyMap(),
        files: List<String> = emptyList(),
        texts: Map<String, String> = emptyMap(),
        executables: Map<String, String> = mapOf("java" to clientJava, "gradle" to pathGradle),
    ): List<InstallationCheck> {
        val fileManager = FakeFileManager(
            existing = builds.flatMap { it.first } + files,
            properties = builds.fold(properties) { all, build -> all + build.second },
            directories = setOf(repository, server, android),
        )
        texts.forEach { (path, text) -> fileManager.writeText(path, text) }
        val executor = FakeShellCommandExecutor { command ->
            if (command.first() == clientJava) CommandResult(command, 0, "", "openjdk version \"21.0.5\" 2024-10-15") else FakeShellCommandExecutor.failure(command)
        }
        return CheckProviderToolsUseCase(executor, { executables[it] }, fileManager, FakeAndroidSdk(), FakeAppleSimulators(), { null }, HostOs.MACOS, home)(
            "main-repo",
            repository,
            RepositoryProviders(gradle = GradleProvider()),
        )
    }

    private fun InstallationCheck.provider(): InstallationFinding.ProviderTool = finding as InstallationFinding.ProviderTool

    private fun gradle(location: String, version: String = "8.10.2") =
        InstallationFinding.ProviderTool("main-repo", ProviderToolId.GRADLE, location, version = version)

    @Test
    fun `a Gradle build in android is checked with its wrapper and Java`() = runTest {
        val checks = checks(listOf(build(android)))

        assertEquals(listOf(CheckState.PASSED, CheckState.PASSED), checks.map { it.state })
        assertEquals(gradle("$android/gradlew"), checks.first().finding)
        assertEquals(ProviderToolId.GRADLE_JAVA, checks.last().provider().tool)
    }

    @Test
    fun `the nested build's daemon criteria and Android Gradle Plugin are read from its folder`() = runTest {
        val catalog = "[versions]\nagp = \"8.7.0\"\n[plugins]\nandroid-application = { id = \"com.android.application\", version.ref = \"agp\" }"
        val criteria = "$android/gradle/gradle-daemon-jvm.properties"

        val checks = checks(
            listOf(build(android)),
            properties = mapOf(criteria to mapOf("toolchainVersion" to "21")),
            files = listOf(criteria),
            texts = mapOf("$android/gradle/libs.versions.toml" to catalog),
        )

        assertEquals(
            listOf(
                InstallationFinding.ProviderTool("main-repo", ProviderToolId.GRADLE_DAEMON_JAVA, clientJava, version = "21"),
                InstallationFinding.ProviderTool("main-repo", ProviderToolId.ANDROID_GRADLE_PLUGIN_JAVA, null, version = "8.7.0", java = 21),
            ),
            checks.drop(2).map { it.provider() },
        )
    }

    @Test
    fun `a Gradle build at the root wins over one in a subfolder`() = runTest {
        val checks = checks(listOf(build(repository, "8.9"), build(android)))

        assertEquals(gradle("$repository/gradlew", "8.9"), checks.first().finding)
    }

    @Test
    fun `the first nested build in name order is checked`() = runTest {
        val checks = checks(listOf(build(server, "8.9"), build(android)))

        assertEquals(gradle("$android/gradlew"), checks.first().finding)
    }

    @Test
    fun `without a Gradle build the root's wrapper is named as searched`() = runTest {
        val checks = checks(emptyList(), executables = mapOf("java" to clientJava))

        assertEquals(
            InstallationFinding.ProviderTool("main-repo", ProviderToolId.GRADLE, null, ProviderToolProblem.NotFound(listOf("$repository/gradlew"), listOf("gradle"))),
            checks.single().finding,
        )
    }
}
